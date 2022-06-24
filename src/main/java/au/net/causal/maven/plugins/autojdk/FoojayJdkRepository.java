package au.net.causal.maven.plugins.autojdk;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.PackageType;
import eu.hansolo.jdktools.versioning.VersionNumber;
import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.pkg.Distribution;
import io.foojay.api.discoclient.pkg.MajorVersion;
import io.foojay.api.discoclient.pkg.Pkg;
import io.foojay.api.discoclient.pkg.Scope;
import io.foojay.api.discoclient.util.PkgInfo;
import jakarta.xml.bind.JAXB;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.toolchain.RequirementMatcherFactory;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class FoojayJdkRepository implements JdkArchiveRepository<FoojayArtifact>
{
    private final DiscoClient discoClient;
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;
    private final FileDownloader fileDownloader;

    private final String mavenArtifactGroupId;

    private final JdkVersionExpander versionExpander = JdkVersionExpander.EXPAND_ALL;

    public FoojayJdkRepository(DiscoClient discoClient, RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession,
                               FileDownloader fileDownloader, String mavenArtifactGroupId)
    {
        this.discoClient = Objects.requireNonNull(discoClient);
        this.repositorySystem = Objects.requireNonNull(repositorySystem);
        this.repositorySystemSession = Objects.requireNonNull(repositorySystemSession);
        this.fileDownloader = Objects.requireNonNull(fileDownloader);
        this.mavenArtifactGroupId = Objects.requireNonNull(mavenArtifactGroupId);
    }

    @Override
    public Collection<? extends FoojayArtifact> search(JdkSearchRequest searchRequest)
    throws JdkRepositoryException
    {
        List<VersionNumberAndLatest> foojaySearch = versionRangeToSearchNumbers(searchRequest.getVersionRange());

        //If a vendor was specified, attempt to resolve
        List<Distribution> searchDistributions;
        if (searchRequest.getVendor() == null)
            searchDistributions = null;
        else
            searchDistributions = Collections.singletonList(resolveSearchDistributionFromVendor(searchRequest.getVendor()));

        for (VersionNumberAndLatest vlCriteria : foojaySearch)
        {
            List<Pkg> searchResults = discoClient.getPkgs(
                                                    searchDistributions,
                                                    vlCriteria.getVersionNumber(),
                                                    vlCriteria.getLatest(),
                                                    searchRequest.getOperatingSystem(),
                                                    null,
                                                    searchRequest.getArchitecture(),
                                                    searchRequest.getArchitecture().getBitness(),
                                                    null, /* archive type we want is both .zip and .tar.gz but can't specify both with this Java API */
                                                    PackageType.JDK,
                                                    null,
                                                    true,
                                                    null,
                                                    null,
                                                    null,
                                                    ImmutableList.of(Scope.DIRECTLY_DOWNLOADABLE, Scope.BUILD_OF_OPEN_JDK, Scope.FREE_TO_USE_IN_PRODUCTION),
                                                    null);

            List<FoojayArtifact> results =  searchResults.stream()
                                                         .filter(pkg -> pkgMatchesVersionRange(pkg, searchRequest.getVersionRange()))
                                                         .map(FoojayArtifact::new)
                                                         .filter(artifact -> artifact.getArchiveType() != null) //Any not-understood archive type is discarded
                                                         .collect(Collectors.toList());

            //If we have at least one result after filtering, use that
            //Otherwise go to the next iteration / search number which might get more results
            if (!results.isEmpty())
                return results;
        }

        //If we get here no searches found anything
        return Collections.emptyList();
    }

    /**
     * Given a vendor, return the matching distribution or generate a new one suitable for using with the
     * search API.
     *
     * @param vendor the vendor to resolve.
     *
     * @return a known distribution, or a generated one if the distribution could not be resolved.
     */
    private Distribution resolveSearchDistributionFromVendor(String vendor)
    {
        //Do not use DiscoClient.getDistributionFromText() since it only uses classpath resource
        //and we want to match onto live up-to-date remote data
        return discoClient.getDistributions()
                          .stream()
                          .filter(distribution -> distribution.getFromText(vendor) != null)
                          .findFirst()
                          .orElse(new Distribution(vendor, vendor, vendor));
    }

    private boolean pkgMatchesVersionRange(Pkg pkg, VersionRange versionRange)
    {
        //TODO

        FoojayArtifact artifactForPackage = new FoojayArtifact(pkg);
        String javaVersionString = artifactForPackage.getVersion(); //Version number translation happens in FoojayArtifact
        ArtifactVersion javaVersionAsArtifactVersion = new DefaultArtifactVersion(javaVersionString);
        List<? extends ArtifactVersion> javaVersionsExpanded = versionExpander.expandVersions(javaVersionAsArtifactVersion);

        for (ArtifactVersion curJavaVersion : javaVersionsExpanded)
        {
            boolean matched = RequirementMatcherFactory.createVersionMatcher(curJavaVersion.toString()).matches(versionRange.toString());
            if (matched)
                return true;
        }

        //Nothing matched
        return false;
    }

    /**
     * Converts a version range from Maven into one or more version number search criteria that can be searched for in Foojay.
     *
     * @param versionRange version range to convert.
     *
     * @return a list of version numbers that can be searched for.  Null elements may be returned (which in Foojay means no restriction by version number).
     */
    protected List<VersionNumberAndLatest> versionRangeToSearchNumbers(VersionRange versionRange)
    {
        //Recommended version is just a version number and no restrictions, e.g. "17.0.2"
        //If there is a recommended version and not a range in the range, use that
        if (versionRange.getRecommendedVersion() != null)
        {
            //If the recommended version is just a major version, then we can do a latest=available search to only get back the latest JDKs of this major version
            Latest latest;
            if (isArtifactVersionMajorOnly(versionRange.getRecommendedVersion()))
                latest = Latest.AVAILABLE; //Latest for the major version only
            else
                latest = Latest.ALL_OF_VERSION; //Get back all versions for the major version

            //With the foojay API if you pass more than a major version into search it is ignored for some reason...
            //So let's just use major version numbers for searching JDKs in there
            //and filter results on our side
            return Collections.singletonList(new VersionNumberAndLatest(new VersionNumber(versionRange.getRecommendedVersion().getMajorVersion()), latest));
        }

        //Version range has restrictions / exclusions
        //Use major version of the first lower bound for the first search, then search everything if that fails (but it's slow!)
        List<VersionNumber> lowerBounds = new ArrayList<>();
        for (Restriction restriction : versionRange.getRestrictions())
        {
            if (restriction.getLowerBound() != null)
                lowerBounds.add(new VersionNumber(restriction.getLowerBound().getMajorVersion()));
        }
        Collections.sort(lowerBounds);

        List<VersionNumberAndLatest> searchNumberCriteria = new ArrayList<>();
        VersionNumber lowestBound = null;
        if (!lowerBounds.isEmpty())
        {
            lowestBound = lowerBounds.get(0);
            searchNumberCriteria.add(new VersionNumberAndLatest(lowestBound, Latest.ALL_OF_VERSION)); //The lowest lower bound major version found
        }

        //Now expand for all major versions above what we started with
        List<MajorVersion> availableMajorVersions = new ArrayList<>(discoClient.getAllMajorVersions());
        availableMajorVersions.sort(Comparator.comparing(MajorVersion::getAsInt));
        for (MajorVersion majorVersion : availableMajorVersions)
        {
            if (lowestBound == null || lowestBound.isSmallerThan(majorVersion.getVersionNumber()))
                searchNumberCriteria.add(new VersionNumberAndLatest(majorVersion.getVersionNumber(), Latest.ALL_OF_VERSION));
        }

        return searchNumberCriteria;
    }

    @VisibleForTesting
    static boolean isArtifactVersionMajorOnly(ArtifactVersion artifactVersion)
    {
        return String.valueOf(artifactVersion.getMajorVersion()).equals(artifactVersion.toString());
    }

    @Override
    public JdkArchive resolveArchive(FoojayArtifact jdkArtifact)
    throws JdkRepositoryException
    {
        //First check if we don't already have a cached version in the local repo
        Artifact mavenArtifact = mavenArtifactForJdkArtifact(jdkArtifact);
        ArtifactRequest lookupRequest = new ArtifactRequest(mavenArtifact, Collections.emptyList() /*no remotes, local only*/, null);
        try
        {
            ArtifactResult lookupResult = repositorySystem.resolveArtifact(repositorySystemSession, lookupRequest);

            //If we get here, archive was found in the local repo so just return that
            return new JdkArchive(jdkArtifact, lookupResult.getArtifact().getFile());
        }
        catch (ArtifactResolutionException e)
        {
            //Not found in local repo, that's fine, move on to downloading it
        }

        //If we get here, could not find in the local repo so download it and save to local repo
        PkgInfo pkgInfo = discoClient.getPkgInfoByPkgId(jdkArtifact.getFoojayPkg().getId(), jdkArtifact.getFoojayPkg().getJavaVersion());
        if (pkgInfo == null || pkgInfo.getDirectDownloadUri() == null) //Not found
            throw new JdkRepositoryException("Download information not found for Foojay package " + jdkArtifact.getFoojayPkg().getId() + ":" + jdkArtifact.getFoojayPkg().getJavaVersion());

        //Download into local repository

        //First download to temp file
        try (FileDownloader.Download download = fileDownloader.downloadFile(new URL(pkgInfo.getDirectDownloadUri())))
        {
            //Once file is downloaded, install to local repo
            Path downloadedFile = download.getFile();

            //download's close() in try-with-resources will handle deleting the temp file

            InstallRequest request = new InstallRequest();
            mavenArtifact = mavenArtifact.setFile(downloadedFile.toFile());
            request.setArtifacts(Collections.singletonList(mavenArtifact));
            try
            {
                repositorySystem.install(repositorySystemSession, request);

                //Re-resolve to local repo so file of artifact gets filled in
                mavenArtifact = mavenArtifact.setFile(null);
                ArtifactRequest reresolveRequest = new ArtifactRequest(mavenArtifact, Collections.emptyList(), null);
                ArtifactResult reresolveResult = repositorySystem.resolveArtifact(repositorySystemSession, reresolveRequest);
                File jdkArchiveInLocalRepo = reresolveResult.getArtifact().getFile();

                //Also upload metadata
                Path metadataFile = downloadedFile.resolveSibling(downloadedFile.getFileName().toString() + "." + MavenArtifactJdkArchiveRepository.AUTOJDK_METADATA_EXTENSION);
                generateJdkArtifactMetadataFile(new MavenJdkArtifactMetadata(Collections.singleton(jdkArtifact.getArchiveType())), metadataFile);
                Artifact metadataArtifact = new DefaultArtifact(mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(), mavenArtifact.getClassifier(), MavenArtifactJdkArchiveRepository.AUTOJDK_METADATA_EXTENSION, mavenArtifact.getVersion());
                metadataArtifact = metadataArtifact.setFile(metadataFile.toFile());

                InstallRequest metadataInstallRequest = new InstallRequest();
                metadataInstallRequest.setArtifacts(Collections.singleton(metadataArtifact));
                repositorySystem.install(repositorySystemSession, metadataInstallRequest);

                return new JdkArchive(jdkArtifact, jdkArchiveInLocalRepo);
            }
            catch (InstallationException | ArtifactResolutionException e)
            {
                throw new JdkRepositoryException("Failed to install JDK archive artifact to local repo: " + e.getMessage(), e);
            }
        }
        catch (MalformedURLException e)
        {
            throw new JdkRepositoryException("Invalid JDK download URL: " + pkgInfo.getDirectDownloadUri() + " - " + e, e);
        }
        catch (IOException e)
        {
            throw new JdkRepositoryException("Failed to download JDK: " + e.getMessage(), e);
        }
    }

    private void generateJdkArtifactMetadataFile(MavenJdkArtifactMetadata metadata, Path file)
    {
        JAXB.marshal(metadata, file.toFile());
    }

    protected Artifact mavenArtifactForJdkArtifact(JdkArtifact jdkArtifact)
    {
        return new MavenJdkArtifact(mavenArtifactGroupId, jdkArtifact).getArtifact();
    }

    protected static class VersionNumberAndLatest
    {
        private final VersionNumber versionNumber;
        private final Latest latest;

        public VersionNumberAndLatest(VersionNumber versionNumber, Latest latest)
        {
            this.versionNumber = versionNumber;
            this.latest = latest;
        }

        public VersionNumber getVersionNumber()
        {
            return versionNumber;
        }

        public Latest getLatest()
        {
            return latest;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof VersionNumberAndLatest)) return false;
            VersionNumberAndLatest that = (VersionNumberAndLatest) o;
            return Objects.equals(getVersionNumber(), that.getVersionNumber()) && getLatest() == that.getLatest();
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(getVersionNumber(), getLatest());
        }

        @Override
        public String toString()
        {
            return new StringJoiner(", ", "[", "]")
                    .add("versionNumber=" + versionNumber)
                    .add("latest=" + (latest == null ? null : latest.name()))
                    .toString();
        }
    }
}
