package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.versioning.VersionNumber;
import io.foojay.api.discoclient.DiscoClient;
import org.apache.maven.artifact.versioning.VersionRange;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestFoojayJdkRepository extends AbstractDiscoTestCase
{
    private static final Logger log = LoggerFactory.getLogger(TestFoojayJdkRepository.class);

    private static final String JDK_GROUP_ID = "au.net.causal.autojdk.jdk";

    private FoojayJdkRepository jdkRepository;

    @Mock
    private RepositorySystem repositorySystem;

    @Mock
    private RepositorySystemSession repositorySystemSession;

    @Mock
    private FileDownloader fileDownloader;

    @BeforeEach
    private void setUp()
    {
        DiscoClient discoClient = DiscoClientSingleton.discoClient();
        jdkRepository = new FoojayJdkRepository(discoClient, repositorySystem, repositorySystemSession, fileDownloader, JDK_GROUP_ID);
    }

    @Test
    void testMajorMinorRange()
    throws Exception
    {
        Collection<? extends FoojayArtifact> results = jdkRepository.search(new JdkSearchRequest(
                                                                        VersionRange.createFromVersionSpec("[17.0.2, 17.0.3)"),
                                                                        Architecture.AMD64,
                                                                        OperatingSystem.WINDOWS,
                                                                        null));
        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("17.0.2"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void testMajorRange()
    throws Exception
    {
        Collection<? extends FoojayArtifact> results = jdkRepository.search(new JdkSearchRequest(
                                                                        VersionRange.createFromVersionSpec("[17,18)"),
                                                                        Architecture.AMD64,
                                                                        OperatingSystem.WINDOWS,
                                                                        null));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("17"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void testExplicitVersion()
    throws Exception
    {
        Collection<? extends FoojayArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("17.0.1"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().equals("17.0.1"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    /**
     * Search for an older JDK.
     */
    @Test
    void testOldOne()
    throws Exception
    {
        Collection<? extends FoojayArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[7,8)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("7."))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void searchWithVendor()
    throws Exception
    {
        Collection<? extends FoojayArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[17.0.2, 17.0.3)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                "zulu"));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("17.0.2"))
                .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                .allMatch(r -> r.getVendor().equalsIgnoreCase("zulu"))
                .map(FoojayArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void searchWithVendorSynonym()
    throws Exception
    {
        Collection<? extends FoojayArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[17.0.2, 17.0.3)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                "zulucore"));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("17.0.2"))
                .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                .allMatch(r -> r.getVendor().equalsIgnoreCase("zulu")) //canonical name, not the same as input vendor
                .map(FoojayArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void testDownload(@TempDir Path tempDir)
    throws Exception
    {
        Path theUploadedFile = tempDir.resolve("jdk.zip");

        //Never find anything in the local repo on first resolve attempt, but after it is added to the local repo
        //we can then find it on the 2nd resolve attempt
        when(repositorySystem.resolveArtifact(any(), any()))
                //First lookup will fail
                .thenThrow(ArtifactResolutionException.class)
                //Second lookup done once file is uploaded
                .thenAnswer(inv -> {
                    ArtifactRequest req = inv.getArgument(1, ArtifactRequest.class);
                    return new ArtifactResult(req).setArtifact(req.getArtifact().setFile(theUploadedFile.toFile()));
                });

        when(fileDownloader.downloadFile(any())).thenAnswer(invocation -> new FileDownloader.Download(
                invocation.getArgument(0, URL.class),
                theUploadedFile
        ));

        Collection<? extends FoojayArtifact> results = jdkRepository.search(new JdkSearchRequest(
                                                        VersionRange.createFromVersionSpec("[17.0.2,17.0.3)"),
                                                        Architecture.AMD64,
                                                        OperatingSystem.WINDOWS,
                                                        "zulu"));

        //There are 2 results, one with JavaFX and one without - so let's pick one for consistency
        FoojayArtifact result = results.stream()
                                       .filter(a -> Boolean.FALSE.equals(a.getFoojayPkg().isJavaFXBundled()))
                                       .findFirst()
                                       .orElseThrow();

        JdkArchive download = jdkRepository.resolveArchive(result);

        assertThat(download.getFile()).isEqualTo(theUploadedFile.toFile());
        assertThat(download.getArtifact().getVendor()).isEqualToIgnoringCase("zulu");
        assertThat(download.getArtifact().getVersion().toString()).startsWith("17.0.2");

        verify(repositorySystem, times(2)).resolveArtifact(any(), any());
        verify(fileDownloader).downloadFile(any());

        //Verify the downloaded file was actually installed to the local repo as well as the metadata
        List<File> installedFiles = new ArrayList<>();
        verify(repositorySystem, times(2)).install(any(), argThat(ir ->
        {
            assertThat(ir.getArtifacts()).hasSize(1);
            Artifact artifact = ir.getArtifacts().iterator().next();
            if (!installedFiles.contains(artifact.getFile())) //Workaround for mockito weirdness
                installedFiles.add(artifact.getFile());
            assertThat(artifact.getGroupId()).isEqualTo(JDK_GROUP_ID);
            assertThat(artifact.getArtifactId()).isEqualTo("zulu");
            assertThat(artifact.getVersion()).startsWith("17.0.2"); //Depending on search result, might have suffix
            return true;
        }));
        assertThat(installedFiles).hasSize(2);
        assertThat(installedFiles).first().isEqualTo(theUploadedFile.toFile());
        assertThat(installedFiles).last().isEqualTo(theUploadedFile.resolveSibling(theUploadedFile.getFileName() + "." + MavenArtifactJdkArchiveRepository.AUTOJDK_METADATA_EXTENSION).toFile());
    }

    /**
     * Searching with version criteria that requires multiple steps.
     */
    @Test
    void testComplexSearchWithVersionCriteria()
    throws Exception
    {
        //Java 16, not 17, and anything 18+ but there is no 16.0.9, so it should move on to 17 which get filtered out from the spec, then it should finally settle on 18
        Collection<? extends FoojayArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[16.0.9,17)[18,)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("18"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    @Test
    void versionRangeToSearchNumbersRecommendedMajorOnly()
    throws Exception
    {
        VersionRange searchCriteria = VersionRange.createFromVersionSpec("9");
        List<FoojayJdkRepository.VersionNumberAndLatest> result = jdkRepository.versionRangeToSearchNumbers(searchCriteria);

        assertThat(result).singleElement().isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("9"), Latest.ALL_OF_VERSION //Find latest available JDK 9
        ));
    }

    @Test
    void versionRangeToSearchNumbersRecommendedFullVersion()
    throws Exception
    {
        VersionRange searchCriteria = VersionRange.createFromVersionSpec("11.1.0");
        List<FoojayJdkRepository.VersionNumberAndLatest> result = jdkRepository.versionRangeToSearchNumbers(searchCriteria);

        assertThat(result).singleElement().isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("11"), Latest.ALL_OF_VERSION //Find all JDK 11s but post-process-filtering will be applied later on
        ));
    }

    @Test
    void versionRangeToSearchNumbersRangeCriteriaWithSimpleLowerBound()
    throws Exception
    {
        VersionRange searchCriteria = VersionRange.createFromVersionSpec("[11,12)");
        List<FoojayJdkRepository.VersionNumberAndLatest> result = jdkRepository.versionRangeToSearchNumbers(searchCriteria);

        //Will have searches for each known major version from the lowest min bound onwards
        assertThat(result).size().isGreaterThanOrEqualTo(8); //11-18 at least, but probably more as time goes on

        assertThat(result).element(0).isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("11"), Latest.ALL_OF_VERSION
        ));
        assertThat(result).element(1).isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("12"), Latest.ALL_OF_VERSION
        ));
        assertThat(result).element(2).isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("13"), Latest.ALL_OF_VERSION
        ));
        assertThat(result).element(3).isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("14"), Latest.ALL_OF_VERSION
        ));
        assertThat(result).element(4).isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("15"), Latest.ALL_OF_VERSION
        ));
        assertThat(result).element(5).isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("16"), Latest.ALL_OF_VERSION
        ));
        assertThat(result).element(6).isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("17"), Latest.ALL_OF_VERSION
        ));
        assertThat(result).element(7).isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("18"), Latest.ALL_OF_VERSION
        ));
        //Might have more, but this amount of checking is enough
    }

    @Test
    void versionRangeToSearchNumbersRangeCriteriaWithMultipleLowerBounds()
    throws Exception
    {
        VersionRange searchCriteria = VersionRange.createFromVersionSpec("[16,17),[18,18.0.2)");
        List<FoojayJdkRepository.VersionNumberAndLatest> result = jdkRepository.versionRangeToSearchNumbers(searchCriteria);

        //Will have searches for each known major version from the lowest min bound onwards
        assertThat(result).size().isGreaterThanOrEqualTo(3); //16-18 at least, but probably more as time goes on

        assertThat(result).element(0).isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("16"), Latest.ALL_OF_VERSION
        ));
        assertThat(result).element(1).isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("17"), Latest.ALL_OF_VERSION
        ));
        assertThat(result).element(2).isEqualTo(new FoojayJdkRepository.VersionNumberAndLatest(
                VersionNumber.fromText("18"), Latest.ALL_OF_VERSION
        ));
        //Might have more, but this amount of checking is enough
    }

    @Test
    void allJava11VersionsAreReallyMavenSafe()
    throws Exception
    {
        Collection<? extends FoojayArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[11,12)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("11"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);

        //Check that every version is parseable and has a parseable major version number
        //If parsing is off, everything ends up in the qualifier which messes up version ordering, major version registration of JDKs in toolchains, etc.
        for (FoojayArtifact result : results)
        {
            assertThat(result.getVersion().getMajorVersion()).isEqualTo(11);
        }
    }

    @Test
    void allJava17VersionsAreReallyMavenSafe()
    throws Exception
    {
        Collection<? extends FoojayArtifact> results = jdkRepository.search(new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[17,18)"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null));

        results.forEach(r -> log.debug(r.toString()));

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().toString().startsWith("17"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);

        //Check that every version is parseable and has a parseable major version number
        //If parsing is off, everything ends up in the qualifier which messes up version ordering, major version registration of JDKs in toolchains, etc.
        for (FoojayArtifact result : results)
        {
            assertThat(result.getVersion().getMajorVersion()).isEqualTo(17);
        }
    }
}
