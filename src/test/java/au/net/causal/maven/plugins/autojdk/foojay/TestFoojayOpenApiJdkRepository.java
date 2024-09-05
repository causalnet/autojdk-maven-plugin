package au.net.causal.maven.plugins.autojdk.foojay;

import au.net.causal.maven.plugins.autojdk.FileDownloader;
import au.net.causal.maven.plugins.autojdk.JdkArchive;
import au.net.causal.maven.plugins.autojdk.JdkSearchRequest;
import au.net.causal.maven.plugins.autojdk.xml.metadata.ReleaseType;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.versioning.VersionNumber;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestFoojayOpenApiJdkRepository extends AbstractApiClientMockTestCase
{
    private static final Logger log = LoggerFactory.getLogger(TestFoojayOpenApiJdkRepository.class);

    private FoojayOpenApiJdkRepository jdkRepository;

    @Mock
    private FileDownloader fileDownloader;

    @BeforeEach
    void setUp()
    {
        FoojayClient foojayClient = new FoojayClient(apiClient);
        jdkRepository = new FoojayOpenApiJdkRepository(foojayClient, fileDownloader);
    }

    @Test
    void testMajorMinorRange()
    throws Exception
    {
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[17.0.2, 17.0.3)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null,
                ReleaseType.GA));
        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("17.0.2"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayOpenApiArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void testMajorRange()
    throws Exception
    {
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                                                                        VersionRange.createFromVersionSpec("[17,18)"),
                                                                        Architecture.AMD64,
                                                                        OperatingSystem.WINDOWS,
                                                                        null,
                                                                        ReleaseType.GA));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("17"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayOpenApiArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void testExplicitVersion()
    throws Exception
    {
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("17.0.1"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null,
                ReleaseType.GA));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().equals("17.0.1"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayOpenApiArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    /**
     * Search for an older JDK.
     */
    @Test
    void testOldOne()
    throws Exception
    {
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[7,8)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null,
                ReleaseType.GA));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("7."))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayOpenApiArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void searchWithVendor()
    throws Exception
    {
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[17.0.2, 17.0.3)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                "zulu",
                ReleaseType.GA));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("17.0.2"))
                .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                .allMatch(r -> r.getVendor().equalsIgnoreCase("zulu"))
                .map(FoojayOpenApiArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void searchWithVendorSynonym()
    throws Exception
    {
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[17.0.2, 17.0.3)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                "zulucore",
                ReleaseType.GA));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("17.0.2"))
                .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                .allMatch(r -> r.getVendor().equalsIgnoreCase("zulu")) //canonical name, not the same as input vendor
                .map(FoojayOpenApiArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void searchWithEaReleaseType()
    throws Exception
    {
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[18, 19)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                "zulu",
                ReleaseType.EA));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().getMajorVersion() == 18)
                           .allMatch(r -> r.getVersion().getQualifier().startsWith("ea-"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .allMatch(r -> r.getVendor().equalsIgnoreCase("zulu")) //canonical name, not the same as input vendor
                           .map(FoojayOpenApiArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    /**
     * Noticed that searching for latest Java 18 can give the musl result for linuxes that don't specify this despite other glibc distributions being available so
     * there is a priority for results that have matching libc's.
     */
    @Test
    void java18LinuxRequiresMatchingOperatingSystemAndLibCType()
    throws Exception
    {
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[18,19)"),
                Architecture.AMD64,
                OperatingSystem.LINUX,
                null,
                ReleaseType.GA));

        List<FoojayOpenApiArtifact> resultList = new ArrayList<>(results);
        resultList.sort(Comparator.<FoojayOpenApiArtifact, String>comparing(x -> x.getJdkPackage().getDistribution())
                                  .thenComparing(x -> x.getJdkPackage().getJavaVersion())
        );

        resultList.forEach(r -> log.debug(r.toString()));

        assertThat(resultList).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(resultList).allMatch(r -> r.getVersion().toString().startsWith("18"))
                              .allMatch(r -> r.getOperatingSystem() == OperatingSystem.LINUX)
                              .allMatch(r -> r.getJdkPackage().getLibCType() == OperatingSystem.LINUX.getLibCType()) //which is GLibC
                              .map(FoojayOpenApiArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void testDownload(@TempDir Path tempDir)
    throws Exception
    {
        Path theUploadedFile = tempDir.resolve("jdk.zip");

        when(fileDownloader.downloadFile(any())).thenAnswer(invocation -> new FileDownloader.Download(
                invocation.getArgument(0, URL.class),
                theUploadedFile
        ));

        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                                                        VersionRange.createFromVersionSpec("[17.0.2,17.0.3)"),
                                                        Architecture.AMD64,
                                                        OperatingSystem.WINDOWS,
                                                        "zulu",
                                                        ReleaseType.GA));

        //There are 2 results, one with JavaFX and one without - so let's pick one for consistency
        FoojayOpenApiArtifact result = results.stream()
                                       .filter(a -> Boolean.FALSE.equals(a.getJdkPackage().getJavafxBundled()))
                                       .findFirst()
                                       .orElseThrow();

        JdkArchive<FoojayOpenApiArtifact> download = jdkRepository.resolveArchive(result);

        assertThat(download.getFile()).isEqualTo(theUploadedFile);
        assertThat(download.getArtifact().getVendor()).isEqualToIgnoringCase("zulu");
        assertThat(download.getArtifact().getVersion().toString()).startsWith("17.0.2");

        verify(fileDownloader).downloadFile(any());
    }

    /**
     * Searching with version criteria that requires multiple steps.
     */
    @Test
    void testComplexSearchWithVersionCriteria()
    throws Exception
    {
        //Java 16, not 17, and anything 18+ up to 19 but there is no 16.0.9, so it should move on to 17 which get filtered out from the spec, then it should finally settle on 18
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[16.0.9,17)[18,19)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null,
                ReleaseType.GA));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("18"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayOpenApiArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    /**
     * Multiple major JDK versions covered by a single bounded range.
     */
    @Test
    void testSearchWithBoundContainingMultipleMajorVersionNumbers()
    throws Exception
    {
        //Java 16 or 17
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[16, 18)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null,
                ReleaseType.GA));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        //Since Java 17 is the latest available it should just return Java 17 results and not the 16 ones
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("17"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayOpenApiArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void testSearchWithUnknownVendor()
    throws Exception
    {
        //Without special handling, doing a lower-level Discoclient search with unknown vendor will actually
        //return results for all vendors, so there is a special case that does not perform a search when the vendor is unknown
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                                                                VersionRange.createFromVersionSpec("[17, 18)"),
                                                                Architecture.AMD64,
                                                                OperatingSystem.WINDOWS,
                                                                "unknown-vendor",
                                                                ReleaseType.GA));
        assertThat(results).isEmpty();
    }

    @Test
    void versionRangeToSearchNumbersRecommendedMajorOnly()
    throws Exception
    {
        VersionRange searchCriteria = VersionRange.createFromVersionSpec("9");
        List<FoojayOpenApiJdkRepository.VersionNumberAndLatest> result = jdkRepository.versionRangeToSearchNumbers(searchCriteria);

        assertThat(result).singleElement().isEqualTo(new FoojayOpenApiJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("9"), Latest.ALL_OF_VERSION //Find latest available JDK 9
        ));
    }

    @Test
    void versionRangeToSearchNumbersRecommendedFullVersion()
    throws Exception
    {
        VersionRange searchCriteria = VersionRange.createFromVersionSpec("11.1.0");
        List<FoojayOpenApiJdkRepository.VersionNumberAndLatest> result = jdkRepository.versionRangeToSearchNumbers(searchCriteria);

        assertThat(result).singleElement().isEqualTo(new FoojayOpenApiJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("11"), Latest.ALL_OF_VERSION //Find all JDK 11s but post-process-filtering will be applied later on
        ));
    }

    @Test
    void versionRangeToSearchNumbersRangeCriteriaWithSimpleLowerBound()
    throws Exception
    {
        VersionRange searchCriteria = VersionRange.createFromVersionSpec("[11,12)");
        List<FoojayOpenApiJdkRepository.VersionNumberAndLatest> result = jdkRepository.versionRangeToSearchNumbers(searchCriteria);

        //Should only search Java 11
        assertThat(result).singleElement().isEqualTo(
                new FoojayOpenApiJdkRepository.VersionNumberAndLatest(VersionNumber.fromText("11"), Latest.ALL_OF_VERSION));
    }

    @Test
    void versionRangeToSearchNumbersRangeCriteriaWithMultipleLowerBounds()
    throws Exception
    {
        VersionRange searchCriteria = VersionRange.createFromVersionSpec("[16,17),[18,18.0.2)");
        List<FoojayOpenApiJdkRepository.VersionNumberAndLatest> result = jdkRepository.versionRangeToSearchNumbers(searchCriteria);

        //Will have searches for each known major version from the lowest min bound onwards
        assertThat(result).hasSize(3); //16-18

        assertThat(result).element(0).isEqualTo(new FoojayOpenApiJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("16"), Latest.ALL_OF_VERSION
        ));
        assertThat(result).element(1).isEqualTo(new FoojayOpenApiJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("17"), Latest.ALL_OF_VERSION
        ));
        assertThat(result).element(2).isEqualTo(new FoojayOpenApiJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("18"), Latest.ALL_OF_VERSION
        ));
        //Might have more, but this amount of checking is enough
    }

    @Test
    void versionRangeToSearchNumbersRangeCriteriaWithBoundCoveringMultipleMajorVersions()
    throws Exception
    {
        VersionRange searchCriteria = VersionRange.createFromVersionSpec("[16,18)");
        List<FoojayOpenApiJdkRepository.VersionNumberAndLatest> result = jdkRepository.versionRangeToSearchNumbers(searchCriteria);

        //Will have searches for each known major version from the lowest min bound onwards
        assertThat(result).hasSize(2); //16-17

        assertThat(result).element(0).isEqualTo(new FoojayOpenApiJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("16"), Latest.ALL_OF_VERSION
        ));
        assertThat(result).element(1).isEqualTo(new FoojayOpenApiJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("17"), Latest.ALL_OF_VERSION
        ));
        //Might have more, but this amount of checking is enough
    }

    @Test
    void allJava11VersionsAreReallyMavenSafe()
    throws Exception
    {
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[11,12)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null,
                ReleaseType.GA));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("11"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayOpenApiArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);

        //Check that every version is parseable and has a parseable major version number
        //If parsing is off, everything ends up in the qualifier which messes up version ordering, major version registration of JDKs in toolchains, etc.
        for (FoojayOpenApiArtifact result : results)
        {
            assertThat(result.getVersion().getMajorVersion()).isEqualTo(11);
        }
    }

    @Test
    void allJava17VersionsAreReallyMavenSafe()
    throws Exception
    {
        Collection<? extends FoojayOpenApiArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[17,18)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null,
                ReleaseType.GA));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("17"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayOpenApiArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);

        //Check that every version is parseable and has a parseable major version number
        //If parsing is off, everything ends up in the qualifier which messes up version ordering, major version registration of JDKs in toolchains, etc.
        for (FoojayOpenApiArtifact result : results)
        {
            assertThat(result.getVersion().getMajorVersion()).isEqualTo(17);
        }
    }
}
