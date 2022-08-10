package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.toolchain.RequirementMatcherFactory;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Entrypoint to AutoJDK functionality.
 */
public class AutoJdk
{
    private static final Logger log = LoggerFactory.getLogger(AutoJdk.class);

    private static final PlatformTools platformTools = new PlatformTools();

    private final LocalJdkResolver localJdkResolver;
    private final JdkInstallationTarget jdkInstallationTarget;
    private final List<JdkArchiveRepository<?>> jdkArchiveRepositories;
    private final VersionTranslationScheme versionTranslationScheme;
    private final AutoJdkConfiguration autoJdkConfiguration;
    private final JdkSearchUpdateChecker jdkSearchUpdateChecker;
    private final Clock clock;

    public AutoJdk(LocalJdkResolver localJdkResolver, JdkInstallationTarget jdkInstallationTarget,
                   Collection<? extends JdkArchiveRepository<?>> jdkArchiveRepositories, VersionTranslationScheme versionTranslationScheme,
                   AutoJdkConfiguration autoJdkConfiguration,
                   JdkSearchUpdateChecker jdkSearchUpdateChecker,
                   Clock clock)
    {
        this.localJdkResolver = Objects.requireNonNull(localJdkResolver);
        this.jdkInstallationTarget = Objects.requireNonNull(jdkInstallationTarget);
        this.jdkArchiveRepositories = List.copyOf(jdkArchiveRepositories);
        this.versionTranslationScheme = Objects.requireNonNull(versionTranslationScheme);
        this.autoJdkConfiguration = Objects.requireNonNull(autoJdkConfiguration);
        this.jdkSearchUpdateChecker = Objects.requireNonNull(jdkSearchUpdateChecker);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<? extends ToolchainModel> generateToolchainsFromLocalJdks(ReleaseType releaseType)
    throws LocalJdkResolutionException
    {
        List<ToolchainModel> toolchains = new ArrayList<>();

        List<LocalJdk> localJdks = new ArrayList<>(localJdkResolver.getInstalledJdks(releaseType));
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
        searchRequest = translateSearchRequestForVersionTranslationScheme(searchRequest);
        JdkArchiveRepository<?> compositeRepository = new CompositeJdkArchiveRepository(CompositeJdkArchiveRepository.SearchType.EXHAUSTIVE,
                                                                                        SearchErrorLoggingJdkArchiveRepository.wrapRepositories(jdkArchiveRepositories));
        try
        {
            return compositeRepository.search(searchRequest);
        }
        catch (JdkRepositoryException e)
        {
            //Each individual repo is wrapped to log/supress errors, so really we should never get here
            log.warn("Failed to search repository for JDK: " + e.getMessage());
            log.debug("Failed to search repository for JDK: " + e.getMessage(), e);
            return List.of();
        }
    }

    private boolean updateCheckRequiredForJdkSearch(JdkSearchRequest searchRequest)
    throws JdkSearchUpdateCheckException
    {
        Duration checkPolicy = autoJdkConfiguration.getJdkUpdatePolicyDuration();
        if (checkPolicy == null) //null -> never check
            return false;

        Instant lastCheckTime = jdkSearchUpdateChecker.getLastCheckTime(searchRequest);
        if (lastCheckTime == null) //If never checked before, then we'll need to check no matter what the current time is
            return true;

        Instant now = Instant.now(clock);

        return lastCheckTime.plus(checkPolicy).isBefore(now);
    }

    public LocalJdk prepareJdk(JdkSearchRequest searchRequest)
    throws LocalJdkResolutionException, JdkNotFoundException, JdkSearchUpdateCheckException, IOException
    {
        searchRequest = translateSearchRequestForVersionTranslationScheme(searchRequest);

        //First scan all existing local JDKs and use one of those if there is a match
        LocalJdk localJdk = findMatchingLocalJdk(searchRequest);

        //Check if a download check is required
        if (localJdk == null || updateCheckRequiredForJdkSearch(searchRequest))
        {
            if (localJdk != null)
                log.info("Found local JDK " + localJdk.getJdkDirectory() + ", checking if there is a more recent version available...");
            else
                log.info("No matching local JDK found, searching for one available to download...");

            //Query remote repos and download if the local does not match
            //TODO should we check all repos for up-to-date checks and pick the best?
            CompositeJdkArchiveRepository compositeRepository = new CompositeJdkArchiveRepository(CompositeJdkArchiveRepository.SearchType.EXHAUSTIVE,
                                                                                                  SearchErrorLoggingJdkArchiveRepository.wrapRepositories(jdkArchiveRepositories));
            try
            {
                //Download a JDK archive
                JdkArchive downloadedJdk = attemptDownloadJdkFromRemoteRepository(searchRequest, compositeRepository, localJdk);

                //If we get here, the search/check worked, so update the up-to-date metadata
                jdkSearchUpdateChecker.saveLastCheckTime(searchRequest, Instant.now(clock));

                if (downloadedJdk != null)
                {
                    //Extract/install it locally
                    LocalJdkMetadata downloadedJdkMetadata = new LocalJdkMetadata(
                            downloadedJdk.getArtifact().getVendor(),
                            downloadedJdk.getArtifact().getVersion().toString(),
                            downloadedJdk.getArtifact().getReleaseType(),
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
                //We already have error wrapping on the repo list, so shouldn't really get here
                log.warn("Failed to search repository for JDK: " + e.getMessage());
                log.debug("Failed to search repository for JDK: " + e.getMessage(), e);
            }
        }

        //Might have found a local JDK but up-to-date check didn't get any better options
        if (localJdk != null)
            return localJdk;

        //If we get here we couldn't download or install a JDK
        throw new JdkNotFoundException("Could not find suitable JDK");
    }

    private <A extends JdkArtifact > JdkArchive attemptDownloadJdkFromRemoteRepository(JdkSearchRequest searchRequest, JdkArchiveRepository < A > repository, LocalJdk bestMatchingLocalJdk)
    throws JdkRepositoryException
    {
        Collection<? extends A> searchResults = repository.search(searchRequest);
        A selectedJdk = searchResults.stream()
                                     .max(jdkComparator()) //Pick the latest available JDK version
                                     .orElse(null);
        if (selectedJdk == null)
            return null;

        //If the best matching local JDK matches the best remote one, just use that and don't download it again
        if (bestMatchingLocalJdk != null && remoteJdkMatchesLocalJdk(selectedJdk, bestMatchingLocalJdk))
            return null;

        log.info("Installing JDK from " + selectedJdk);
        return repository.resolveArchive(selectedJdk);
    }

    private boolean remoteJdkMatchesLocalJdk(JdkArtifact remoteJdk, LocalJdk localJdk)
    {
        return Objects.equals(remoteJdk.getVersion(), localJdk.getVersion()) &&
               Objects.equals(remoteJdk.getOperatingSystem(), localJdk.getOperatingSystem()) &&
               Objects.equals(platformTools.canonicalArchitecture(remoteJdk.getArchitecture()), platformTools.canonicalArchitecture(localJdk.getArchitecture())) &&
               Objects.equals(remoteJdk.getVendor(), localJdk.getVendor()) &&
               Objects.equals(remoteJdk.getReleaseType(), localJdk.getReleaseType());
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
        Collection<? extends LocalJdk> jdks = localJdkResolver.getInstalledJdks(searchRequest.getReleaseType());

        //Find highest versioned match
        return jdks.stream()
                   .filter(jdk -> localJdkMatches(jdk, searchRequest))
                   .max(localJdkComparator())
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

    public int deleteLocalJdks(JdkSearchRequest searchRequest)
    throws LocalJdkResolutionException, IOException, JdkRepositoryException
    {
        Collection<? extends LocalJdk> jdks = localJdkResolver.getInstalledJdks(searchRequest.getReleaseType());

        List<? extends LocalJdk> matchingLocalJdks = jdks.stream()
                                                         .filter(jdk -> localJdkMatches(jdk, searchRequest))
                                                         .collect(Collectors.toList());
        for (LocalJdk localJdk : matchingLocalJdks)
        {
            deleteLocalJdk(localJdk);
        }

        return matchingLocalJdks.size();
    }

    public void deleteLocalJdk(LocalJdk localJdk)
    throws IOException, JdkRepositoryException
    {
        log.info("Deleting local JDK: " + localJdk.getJdkDirectory());
        jdkInstallationTarget.deleteJdk(localJdk.getJdkDirectory());

        for (JdkArchiveRepository<?> jdkArchiveRepository : jdkArchiveRepositories)
        {
            Collection<? extends JdkArchive> purgedArchives = jdkArchiveRepository.purgeCache(new JdkPurgeCacheRequest(localJdk.getVersion(), localJdk.getArchitecture(), localJdk.getOperatingSystem(), localJdk.getVendor(), localJdk.getReleaseType()));
            for (JdkArchive purgedArchive : purgedArchives)
            {
                log.info("Deleted cache file: " + purgedArchive.getFile());
            }
        }
    }
}
