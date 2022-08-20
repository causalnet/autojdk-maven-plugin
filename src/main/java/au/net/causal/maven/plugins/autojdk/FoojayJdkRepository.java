package au.net.causal.maven.plugins.autojdk;

import com.google.common.collect.ImmutableList;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.LibCType;
import eu.hansolo.jdktools.PackageType;
import eu.hansolo.jdktools.ReleaseStatus;
import eu.hansolo.jdktools.versioning.VersionNumber;
import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.pkg.Distribution;
import io.foojay.api.discoclient.pkg.MajorVersion;
import io.foojay.api.discoclient.pkg.Pkg;
import io.foojay.api.discoclient.pkg.Scope;
import io.foojay.api.discoclient.util.PkgInfo;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.toolchain.RequirementMatcherFactory;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
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

public class FoojayJdkRepository extends LocalMavenRepositoryCachedJdkArchiveRepository<FoojayArtifact>
{
    private final DiscoClient discoClient;
    private final FileDownloader fileDownloader;
    private final AutoJdkXmlManager xmlManager;

    public FoojayJdkRepository(DiscoClient discoClient, RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession,
                               FileDownloader fileDownloader, String mavenArtifactGroupId,
                               AutoJdkXmlManager xmlManager)
    {
        super(repositorySystem, repositorySystemSession, mavenArtifactGroupId);
        this.discoClient = Objects.requireNonNull(discoClient);
        this.fileDownloader = Objects.requireNonNull(fileDownloader);
        this.xmlManager = Objects.requireNonNull(xmlManager);
    }

    @Override
    public Collection<? extends FoojayArtifact> search(JdkSearchRequest searchRequest)
    throws JdkRepositoryException
    {
        List<VersionNumberAndLatest> foojaySearch = versionRangeToSearchNumbers(searchRequest.getVersionRange());
        foojaySearch.sort(Comparator.comparing(VersionNumberAndLatest::getVersionNumber).reversed()); //Start with latest version and work our way down

        //If a vendor was specified, attempt to resolve
        List<Distribution> searchDistributions;
        if (searchRequest.getVendor() == null)
            searchDistributions = null;
        else
        {
            Distribution distribution = resolveSearchDistributionFromVendor(searchRequest.getVendor());

            //If the distribution could not be found, just bail out of the search with no results
            //Foojay API treats an unknown distribution as a wildcard and it would match everything which is not what we want
            //when the user selects a specific vendor
            if (distribution == null)
                return Collections.emptyList();

            searchDistributions = Collections.singletonList(distribution);
        }

        List<ReleaseStatus> releaseStatuses;
        if (searchRequest.getReleaseType() == null)
            releaseStatuses = null;
        else
        {
            switch (searchRequest.getReleaseType())
            {
                case GA:
                    releaseStatuses = Collections.singletonList(ReleaseStatus.GA);
                    break;
                case EA:
                    releaseStatuses = Collections.singletonList(ReleaseStatus.EA);
                    break;
                default:
                    throw new Error("Unknown release type: " + searchRequest.getReleaseType());
            }
        }
        for (VersionNumberAndLatest vlCriteria : foojaySearch)
        {
            List<Pkg> searchResults = discoClient.getPkgs(
                                                    searchDistributions,
                                                    vlCriteria.getVersionNumber(),
                                                    vlCriteria.getLatest(),
                                                    searchRequest.getOperatingSystem(),
                                                    null,
                                                    searchRequest.getArchitecture(),
                                                    searchRequest.getArchitecture() == null ? null : searchRequest.getArchitecture().getBitness(),
                                                    null, /* archive type we want is both .zip and .tar.gz but can't specify both with this Java API */
                                                    PackageType.JDK,
                                                    null,
                                                    true,
                                                    releaseStatuses,
                                                    null,
                                                    null,
                                                    ImmutableList.of(Scope.DIRECTLY_DOWNLOADABLE, Scope.BUILD_OF_OPEN_JDK, Scope.FREE_TO_USE_IN_PRODUCTION),
                                                    null);

            List<FoojayArtifact> results =  searchResults.stream()
                                                         .filter(pkg -> pkgMatchesVersionRange(pkg, searchRequest.getVersionRange()))
                                                         .filter(pkg -> pkgMatchesLibCType(pkg, searchRequest.getOperatingSystem() == null ? null : searchRequest.getOperatingSystem().getLibCType()))
                                                         //Exclude GraalVM builds, their versioning is wonky
                                                         .filter(pkg -> !pkg.getDistribution().getScopes().contains(Scope.BUILD_OF_GRAALVM))
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
     * Given a vendor, return the matching distribution or returns null if none was found.
     *
     * @param vendor the vendor to resolve.
     *
     * @return a known distribution, or null.
     */
    private Distribution resolveSearchDistributionFromVendor(String vendor)
    {
        //Do not use DiscoClient.getDistributionFromText() since it only uses classpath resource
        //and we want to match onto live up-to-date remote data
        return discoClient.getDistributions()
                          .stream()
                          .filter(distribution -> distribution.getFromText(vendor) != null)
                          .findFirst()
                          .orElse(null);
    }

    private boolean pkgMatchesVersionRange(Pkg pkg, VersionRange searchVersionRange)
    {
        FoojayArtifact artifactForPackage = new FoojayArtifact(pkg);
        String javaVersionString = artifactForPackage.getVersion().toString(); //Version number translation happens in FoojayArtifact
        return RequirementMatcherFactory.createVersionMatcher(javaVersionString).matches(searchVersionRange.toString());
    }

    /**
     * Noticed that sometimes search results contain libc's that don't match the operating system's.  a JDK using musl on Ubuntu which is glibc won't run so
     * these results need to be filtered out.
     */
    private boolean pkgMatchesLibCType(Pkg pkg, LibCType requiredLibCType)
    {
        //If there is no libc type just assume match since we can't check
        if (requiredLibCType == null || pkg.getLibCType() == null)
            return true;

        return pkg.getLibCType() == requiredLibCType;
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
            Latest latest = Latest.ALL_OF_VERSION; //Get back all versions for the major version
            //there are other options for only retrieving latest of the major version, but for now let's get back everything and Maven logic choose

            //With the foojay API if you pass more than a major version into search it is ignored for some reason...
            //So let's just use major version numbers for searching JDKs in there
            //and filter results on our side
            return Collections.singletonList(new VersionNumberAndLatest(new VersionNumber(versionRange.getRecommendedVersion().getMajorVersion()), latest));
        }

        //TODO if we know we are searching for latest of major version (which will be a lot of the time)
        //   e.g. [17, 18) we could optimize to use Latest.AVAILABLE and get back less stuff, considering we will be picking the highest versions anyway
        //Version range has restrictions / exclusions
        //Use major version of the first lower bound for the first search, then search everything if that fails (but it's slow!)
        List<VersionNumber> lowerBounds = new ArrayList<>();
        List<VersionNumber> upperBounds = new ArrayList<>();
        boolean unboundedUpper = false;
        for (Restriction restriction : versionRange.getRestrictions())
        {
            if (restriction.getLowerBound() != null)
                lowerBounds.add(new VersionNumber(restriction.getLowerBound().getMajorVersion()));
            if (restriction.getUpperBound() != null)
            {
                //If upper bound is only the major version number and exclusive, then include the previous major version
                if (!restriction.isUpperBoundInclusive() && VersionTools.isMajorVersionOnly(restriction.getUpperBound()))
                    upperBounds.add(new VersionNumber(restriction.getUpperBound().getMajorVersion() - 1));
                else
                    upperBounds.add(new VersionNumber(restriction.getUpperBound().getMajorVersion()));
            }
            else
                unboundedUpper = true;
        }
        Collections.sort(lowerBounds);
        Collections.sort(upperBounds);

        List<VersionNumberAndLatest> searchNumberCriteria = new ArrayList<>();
        VersionNumber lowestBound = null;
        if (!lowerBounds.isEmpty())
            lowestBound = lowerBounds.get(0);
        VersionNumber highestBound = null;
        if (!unboundedUpper && !upperBounds.isEmpty())
            highestBound = upperBounds.get(upperBounds.size() - 1);

        //Now expand for all major versions above what we started with
        List<MajorVersion> availableMajorVersions = new ArrayList<>(discoClient.getAllMajorVersions());
        availableMajorVersions.sort(Comparator.comparing(MajorVersion::getAsInt));
        for (MajorVersion majorVersion : availableMajorVersions)
        {
            if (lowestBound == null || lowestBound.isSmallerOrEqualThan(majorVersion.getVersionNumber()))
            {
                //If upper is not unbounded, trim off everything greater than the highest upper bound
                if (highestBound == null || highestBound.isLargerOrEqualThan(majorVersion.getVersionNumber()))
                    searchNumberCriteria.add(new VersionNumberAndLatest(majorVersion.getVersionNumber(), Latest.ALL_OF_VERSION));
            }
        }

        return searchNumberCriteria;
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
            ArtifactResult lookupResult = getRepositorySystem().resolveArtifact(getRepositorySystemSession(), lookupRequest);

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
                getRepositorySystem().install(getRepositorySystemSession(), request);

                //Re-resolve to local repo so file of artifact gets filled in
                mavenArtifact = mavenArtifact.setFile(null);
                ArtifactRequest reresolveRequest = new ArtifactRequest(mavenArtifact, Collections.emptyList(), null);
                ArtifactResult reresolveResult = getRepositorySystem().resolveArtifact(getRepositorySystemSession(), reresolveRequest);
                File jdkArchiveInLocalRepo = reresolveResult.getArtifact().getFile();

                //Also upload metadata
                Path metadataFile = downloadedFile.resolveSibling(downloadedFile.getFileName().toString() + "." + MavenArtifactJdkArchiveRepository.AUTOJDK_METADATA_EXTENSION);
                generateJdkArtifactMetadataFile(new MavenJdkArtifactMetadata(Collections.singleton(jdkArtifact.getArchiveType()), jdkArtifact.getReleaseType()), metadataFile);
                Artifact metadataArtifact = autoJdkMetadataArtifactForArchive(mavenArtifact);
                metadataArtifact = metadataArtifact.setFile(metadataFile.toFile());

                InstallRequest metadataInstallRequest = new InstallRequest();
                metadataInstallRequest.setArtifacts(Collections.singleton(metadataArtifact));
                getRepositorySystem().install(getRepositorySystemSession(), metadataInstallRequest);

                return new JdkArchive(jdkArtifact, jdkArchiveInLocalRepo);
            }
            catch (InstallationException | ArtifactResolutionException | AutoJdkXmlManager.XmlWriteException e)
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
    throws AutoJdkXmlManager.XmlWriteException
    {
        xmlManager.writeFile(metadata, file);
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
