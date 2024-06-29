package au.net.causal.maven.plugins.autojdk.foojay;

import au.net.causal.maven.plugins.autojdk.FileDownloader;
import au.net.causal.maven.plugins.autojdk.JdkArchive;
import au.net.causal.maven.plugins.autojdk.JdkArchiveRepository;
import au.net.causal.maven.plugins.autojdk.JdkRepositoryException;
import au.net.causal.maven.plugins.autojdk.JdkSearchRequest;
import au.net.causal.maven.plugins.autojdk.VersionTools;
import au.net.causal.maven.plugins.autojdk.foojay.openapi.handler.ApiException;
import eu.hansolo.jdktools.ArchiveType;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.LibCType;
import eu.hansolo.jdktools.ReleaseStatus;
import eu.hansolo.jdktools.util.OutputFormat;
import eu.hansolo.jdktools.versioning.VersionNumber;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.toolchain.RequirementMatcherFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class FoojayOpenApiJdkRepository implements JdkArchiveRepository<FoojayOpenApiArtifact>
{
    private final FoojayClient foojayClient;
    private final FileDownloader fileDownloader;

    public FoojayOpenApiJdkRepository(FoojayClient foojayClient, FileDownloader fileDownloader)
    {
        this.foojayClient = Objects.requireNonNull(foojayClient);
        this.fileDownloader = Objects.requireNonNull(fileDownloader);
    }

    /**
     * @return a list containing the specified value if it is not null, or null if the specified value is null.
     */
    private static <T> List<T> singleItemList(T itemOrNull)
    {
        if (itemOrNull == null)
            return null;
        else
            return List.of(itemOrNull);
    }

    private Set<String> vendorSynonyms(String vendorName)
    throws ApiException
    {
        if (vendorName == null)
            return null;

        Set<String> vendorAllowSet = new HashSet<>();
        List<? extends JdkDistribution> distributions = foojayClient.getDistributions(false, true, null);
        for (JdkDistribution distribution : distributions)
        {
            if (vendorName.equals(distribution.getApiParameter()) || distribution.getSynonyms().contains(vendorName))
            {
                vendorAllowSet.add(distribution.getApiParameter());
                vendorAllowSet.addAll(distribution.getSynonyms());
            }
        }
        return vendorAllowSet;
    }

    @Override
    public Collection<? extends FoojayOpenApiArtifact> search(JdkSearchRequest searchRequest)
    throws JdkRepositoryException
    {
        List<VersionNumberAndLatest> foojaySearch = versionRangeToSearchNumbers(searchRequest.getVersionRange());
        foojaySearch.sort(Comparator.comparing(VersionNumberAndLatest::getVersionNumber).reversed()); //Start with latest version and work our way down

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

        try
        {
            //If there is a vendor restriction in the search, need to grab them and their synonyms
            Set<String> vendorAndSynonyms = vendorSynonyms(searchRequest.getVendor());

            for (VersionNumberAndLatest vlCriteria : foojaySearch)
            {
                String versionString = vlCriteria.getVersionNumber().toString(OutputFormat.REDUCED_COMPRESSED, true, true);
                List<? extends JdkPackage> searchResults = foojayClient.getJdkPackages(
                        versionString,
                        null,
                        singleItemList(searchRequest.getVendor()),
                        singleItemList(searchRequest.getVendor()),
                        singleItemList(searchRequest.getArchitecture()),
                        null,
                        List.of(ArchiveType.ZIP, ArchiveType.TAR_GZ),
                        singleItemList(searchRequest.getOperatingSystem()),
                        null,
                        null,
                        releaseStatuses,
                        null,
                        null,
                        vlCriteria.getLatest(),
                        null,
                        true,
                        null,
                        null,
                        null);

                List<FoojayOpenApiArtifact> results = searchResults.stream()
                                                                   //Unknown vendor/distribution turn into wildcard search for some strange reason so also filter client-side just in case
                                                                   .filter(pkg -> vendorAndSynonyms == null || vendorAndSynonyms.contains(pkg.getDistribution()))
                                                                   .filter(pkg -> pkgMatchesVersionRange(pkg, searchRequest.getVersionRange()))
                                                                   .filter(pkg -> pkgMatchesLibCType(pkg, searchRequest.getOperatingSystem() == null ? null
                                                                                                                                                     : searchRequest.getOperatingSystem()
                                                                                                                                                                    .getLibCType()))
                                                                   //Exclude GraalVM builds, their versioning is wonky
                                                                   .filter(pkg -> !isGraalMismatchedVersioning(pkg))
                                                                   .map(FoojayOpenApiArtifact::new)
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
        catch (ApiException e)
        {
            throw new JdkRepositoryException(e);
        }
    }

    private boolean isGraalMismatchedVersioning(JdkPackage p)
    {
        //GraalVM JDK versioning is just broken sometimes.
        //Sometimes the major version and the JDK version do not match - so detect this
        return p.getMajorVersion() != null && p.getJdkVersion() != null && !p.getMajorVersion().equals(p.getJdkVersion());
    }

    private boolean pkgMatchesVersionRange(JdkPackage pkg, VersionRange searchVersionRange)
    {
        FoojayOpenApiArtifact artifactForPackage = new FoojayOpenApiArtifact(pkg);
        String javaVersionString = artifactForPackage.getVersion().toString(); //Version number translation happens in FoojayOpenApiArtifact
        return RequirementMatcherFactory.createVersionMatcher(javaVersionString).matches(searchVersionRange.toString());
    }

    /**
     * Noticed that sometimes search results contain libc's that don't match the operating system's.  a JDK using musl on Ubuntu which is glibc won't run so
     * these results need to be filtered out.
     */
    private boolean pkgMatchesLibCType(JdkPackage pkg, LibCType requiredLibCType)
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
     *
     * @throws JdkRepositoryException if an error occurs.
     */
    protected List<VersionNumberAndLatest> versionRangeToSearchNumbers(VersionRange versionRange)
    throws JdkRepositoryException
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
        List<? extends MajorVersion> availableMajorVersions = readMajorVersions();
        availableMajorVersions.sort(Comparator.comparing(MajorVersion::getMajorVersion));
        for (MajorVersion majorVersion : availableMajorVersions)
        {
            if (lowestBound == null || lowestBound.isSmallerOrEqualThan(new VersionNumber(majorVersion.getMajorVersion())))
            {
                //If upper is not unbounded, trim off everything greater than the highest upper bound
                if (highestBound == null || highestBound.isLargerOrEqualThan(new VersionNumber(majorVersion.getMajorVersion())))
                    searchNumberCriteria.add(new VersionNumberAndLatest(new VersionNumber(majorVersion.getMajorVersion()), Latest.ALL_OF_VERSION));
            }
        }

        return searchNumberCriteria;
    }

    private List<? extends MajorVersion> readMajorVersions()
    throws JdkRepositoryException
    {
        try
        {
            return foojayClient.getAllMajorVersions();
        }
        catch (ApiException e)
        {
            throw new JdkRepositoryException("Error reading major versions: " + e, e);
        }
    }

    @Override
    public JdkArchive<FoojayOpenApiArtifact> resolveArchive(FoojayOpenApiArtifact jdkArtifact)
    throws JdkRepositoryException
    {
        //If we get here, could not find in the local repo so download it and save to local repo
        if (jdkArtifact.getJdkPackage().getLinks() == null || jdkArtifact.getJdkPackage().getLinks().getPkgDownloadRedirect() == null)
            throw new JdkRepositoryException("Download information not found for Foojay package " + jdkArtifact.getJdkPackage().getId() + ":" + jdkArtifact.getJdkPackage().getJavaVersion());

        URI downloadUri = jdkArtifact.getJdkPackage().getLinks().getPkgDownloadRedirect();

        //Download into local repository

        //First download to temp file
        try
        {
            FileDownloader.Download download = fileDownloader.downloadFile(downloadUri.toURL());
            return new JdkArchive<>(jdkArtifact, download.getFile());
        }
        catch (MalformedURLException e)
        {
            throw new JdkRepositoryException("Invalid JDK download URL: " + downloadUri + " - " + e, e);
        }
        catch (IOException e)
        {
            throw new JdkRepositoryException("Failed to download JDK: " + e.getMessage(), e);
        }
    }

    @Override
    public void purgeResolvedArchive(JdkArchive<FoojayOpenApiArtifact> archive)
    throws JdkRepositoryException
    {
        //The JDK archive will just be a temporary download
        try
        {
            Files.deleteIfExists(archive.getFile());
        }
        catch (IOException e)
        {
            throw new JdkRepositoryException("Error deleting downloaded archive " + archive.getFile() + ": " + e.getMessage(), e);
        }
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
