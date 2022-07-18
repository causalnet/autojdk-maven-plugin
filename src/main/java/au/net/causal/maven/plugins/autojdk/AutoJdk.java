package au.net.causal.maven.plugins.autojdk;

import com.google.common.annotations.VisibleForTesting;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.toolchain.RequirementMatcherFactory;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Entrypoint to AutoJDK functionality.
 */
public class AutoJdk
{
    private static final Logger log = LoggerFactory.getLogger(AutoJdk.class);

    private final LocalJdkResolver localJdkResolver;
    private final JdkInstallationTarget jdkInstallationTarget;
    private final List<JdkArchiveRepository<?>> jdkArchiveRepositories;
    private final VersionTranslationScheme versionTranslationScheme;
    private final AutoJdkConfiguration autoJdkConfiguration;

    public AutoJdk(LocalJdkResolver localJdkResolver, JdkInstallationTarget jdkInstallationTarget,
                   Collection<? extends JdkArchiveRepository<?>> jdkArchiveRepositories, VersionTranslationScheme versionTranslationScheme,
                   AutoJdkConfiguration autoJdkConfiguration)
    {
        this.localJdkResolver = Objects.requireNonNull(localJdkResolver);
        this.jdkInstallationTarget = Objects.requireNonNull(jdkInstallationTarget);
        this.jdkArchiveRepositories = List.copyOf(jdkArchiveRepositories);
        this.versionTranslationScheme = Objects.requireNonNull(versionTranslationScheme);
        this.autoJdkConfiguration = Objects.requireNonNull(autoJdkConfiguration);
    }

    public List<? extends ToolchainModel> generateToolchainsFromLocalJdks()
    throws LocalJdkResolutionException
    {
        List<ToolchainModel> toolchains = new ArrayList<>();

        List<LocalJdk> localJdks = new ArrayList<>(localJdkResolver.getInstalledJdks());
        localJdks.sort(localJdkComparator().reversed());

        for (LocalJdk jdk : localJdks)
        {
            for (ArtifactVersion jdkVersion : versionTranslationScheme.expandJdkVersionForRegistration(jdk.getVersion()))
            {
                toolchains.add(localJdkToToolchainModel(jdk, jdkVersion));
            }
        }
        return toolchains;
    }

    private ToolchainModel localJdkToToolchainModel(LocalJdk jdk, ArtifactVersion jdkVersionToRegister)
    {
        ToolchainModel tcm = new ToolchainModel();
        tcm.setType("jdk");
        tcm.addProvide("version", jdkVersionToRegister.toString());
        tcm.addProvide("vendor", jdk.getVendor());
        Xpp3Dom conf = new Xpp3Dom("configuration");
        Xpp3Dom jdkHomeElement = new Xpp3Dom("jdkHome");
        jdkHomeElement.setValue(jdk.getJdkDirectory().toAbsolutePath().toString());
        conf.addChild(jdkHomeElement);
        tcm.setConfiguration(conf);

        return tcm;
    }

    /**
     * Translate the search request according to the version translation scheme.
     * This can help situations where the version criteria from the search request came from a project spec that doesn't quite follow Maven's versioning rules
     * but worked with pure toolchains because users defined JDKs with only major version numbers
     */
    private JdkSearchRequest translateSearchRequestForVersionTranslationScheme(JdkSearchRequest searchRequest)
    {
        VersionRange translatedVersionRange = versionTranslationScheme.translateProjectRequiredJdkVersionToSearchCriteria(searchRequest.getVersionRange());
        return searchRequest.withVersionRange(translatedVersionRange);
    }

    public Collection<? extends JdkArtifact> findArtifactsInAllRepositories(JdkSearchRequest searchRequest)
    {
        List<JdkArtifact> results = new ArrayList<>();
        for (JdkArchiveRepository<?> jdkArchiveRepository : jdkArchiveRepositories)
        {
            try
            {
                Collection<? extends JdkArtifact> repositoryResults = jdkArchiveRepository.search(searchRequest);
                results.addAll(repositoryResults);
            }
            catch (JdkRepositoryException e)
            {
                log.warn("Failed to search repository for JDK: " + e.getMessage());
                log.debug("Failed to search repository for JDK: " + e.getMessage(), e);
            }
        }

        return results;
    }

    public LocalJdk prepareJdk(JdkSearchRequest searchRequest)
    throws LocalJdkResolutionException, JdkNotFoundException, IOException
    {
        searchRequest = translateSearchRequestForVersionTranslationScheme(searchRequest);

        //First scan all existing local JDKs and use one of those if there is a match
        LocalJdk localJdk = findMatchingLocalJdk(searchRequest);

        if (localJdk != null)
            return localJdk;

        //If no local JDK found we need to download one from a remote repository
        for (JdkArchiveRepository<?> jdkArchiveRepository : jdkArchiveRepositories)
        {
            try
            {
                //Download a JDK archive
                JdkArchive downloadedJdk = attemptDownloadJdkFromRemoteRepository(searchRequest, jdkArchiveRepository);
                if (downloadedJdk != null)
                {
                    //Extract/install it locally
                    LocalJdkMetadata downloadedJdkMetadata = new LocalJdkMetadata(
                            downloadedJdk.getArtifact().getVendor(),
                            downloadedJdk.getArtifact().getVersion().toString(),
                            downloadedJdk.getArtifact().getArchitecture(),
                            downloadedJdk.getArtifact().getOperatingSystem()
                    );

                    Path newJdkInstallDirectory = jdkInstallationTarget.installJdkFromArchive(downloadedJdk.getFile().toPath(), downloadedJdkMetadata);

                    log.info("Installed new JDK to: " + newJdkInstallDirectory);

                    //Rescan - should find it now
                    localJdk = findMatchingLocalJdk(searchRequest);

                    if (localJdk == null)
                        throw new JdkNotFoundException("Could not find JDK locally even after it downloaded and installed");

                    return localJdk;
                }
            }
            catch (JdkRepositoryException e)
            {
                log.warn("Failed to search repository for JDK: " + e.getMessage());
                log.debug("Failed to search repository for JDK: " + e.getMessage(), e);
            }
        }

        //If we get here we couldn't download or install a JDK
        throw new JdkNotFoundException("Could not find suitable JDK");
    }

    private <A extends JdkArtifact > JdkArchive attemptDownloadJdkFromRemoteRepository(JdkSearchRequest searchRequest, JdkArchiveRepository < A > repository)
    throws JdkRepositoryException
    {
        Collection<? extends A> searchResults = repository.search(searchRequest);
        A selectedJdk = searchResults.stream()
                                     .max(jdkComparator()) //Pick the latest available JDK version
                                     .orElse(null);
        if (selectedJdk == null)
            return null;

        log.info("Installing JDK from " + selectedJdk);
        return repository.resolveArchive(selectedJdk);
    }

    /**
     * @return a comparator that sorts JDKs by preferred vendor, version and archive type, preferred JDKs last.
     */
    public Comparator<JdkArtifact> jdkComparator()
    {
        //Sort by preferred vendor first (preferred last), then by version (highest last) then archive type (.tar.gz last)
        return Comparator.comparing(JdkArtifact::getVendor, new KnownValueComparator<>(autoJdkConfiguration.getVendors(), AutoJdkConfiguration.WILDCARD_VENDOR).reversed())
                         .thenComparing(JdkArtifact::getVersion)
                         //max should prefer .tar.gz because on unix platforms this archive format has executable
                         //permissions in it
                         .thenComparing(JdkArtifact::getArchiveType);
    }

    /**
     * @return a comparator that sorts JDKs by preferred vendor and version, preferred JDKs last.
     */
    public Comparator<LocalJdk> localJdkComparator()
    {
        //Sort by preferred vendor first (preferred last), then by version (highest last)
        return Comparator.comparing(LocalJdk::getVendor, new KnownValueComparator<>(autoJdkConfiguration.getVendors(), AutoJdkConfiguration.WILDCARD_VENDOR).reversed())
                         .thenComparing(LocalJdk::getVersion);
    }

    protected LocalJdk findMatchingLocalJdk(JdkSearchRequest searchRequest)
    throws LocalJdkResolutionException
    {
        Collection<? extends LocalJdk> jdks = localJdkResolver.getInstalledJdks();

        //Find highest versioned match
        return jdks.stream()
                   .filter(jdk -> localJdkMatches(jdk, searchRequest))
                   .max(Comparator.comparing(LocalJdk::getVersion))
                   .orElse(null);
    }

    private boolean localJdkMatches(LocalJdk jdk, JdkSearchRequest searchRequest)
    {
        //Reject if architecture does not match
        if (!searchRequest.getArchitecture().equals(jdk.getArchitecture()) &&
            !searchRequest.getArchitecture().getSynonyms().contains(jdk.getArchitecture()))
        {
            return false;
        }

        //Reject if operating system does not match
        if (!searchRequest.getOperatingSystem().equals(jdk.getOperatingSystem()) &&
            !searchRequest.getOperatingSystem().getSynonyms().contains(jdk.getOperatingSystem()))
        {
            return false;
        }

        //If vendor was specified in search request, reject JDK if vendor does not match
        if (searchRequest.getVendor() != null && !searchRequest.getVendor().equals(jdk.getVendor()))
            return false;

        //Version comparison
        if (!localJdkVersionMatches(jdk.getVersion(), searchRequest.getVersionRange()))
            return false;

        //If we get here it matches
        return true;
    }

    //Needs to have the same logic as toolchains
    private boolean localJdkVersionMatches(ArtifactVersion jdkVersion, VersionRange searchVersion)
    {
        //This logic needs to emulate the logic of how we expand local JDKs into toolchains.xml definitions
        //The match logic of this loop must be the same as what toolchains does
        //See DefaultToolchain.matchesRequirements()
        //and RequirementMatcherFactory.VersionMatcher.matches()
        return RequirementMatcherFactory.createVersionMatcher(jdkVersion.toString()).matches(searchVersion.toString());
    }
}
