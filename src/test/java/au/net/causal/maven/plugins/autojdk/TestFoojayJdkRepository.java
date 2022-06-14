package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.versioning.VersionNumber;
import io.foojay.api.discoclient.DiscoClient;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TestFoojayJdkRepository extends AbstractDiscoTestCase
{
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
        DiscoClient discoClient = new DiscoClient();
        jdkRepository = new FoojayJdkRepository(discoClient, repositorySystem, repositorySystemSession, fileDownloader);
    }

    /**
     * Non-latest results require more extensive client-side filtering, so test that.
     */
    @Test
    void testNonLatest()
    throws Exception
    {
        Collection<? extends FoojayArtifact> results = jdkRepository.search(new JdkSearchRequest(
                                                                        VersionRange.createFromVersionSpec("17.0.2"),
                                                                        Architecture.AMD64,
                                                                        OperatingSystem.WINDOWS,
                                                                        null));

        results.forEach(System.out::println);

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().startsWith("17.0.2"))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
    }

    /**
     * Latest JDKs for a given major version.
     */
    @Test
    void testLatest()
    throws Exception
    {
        Collection<? extends FoojayArtifact> results = jdkRepository.search(new JdkSearchRequest(
                                                                        VersionRange.createFromVersionSpec("17"),
                                                                        Architecture.AMD64,
                                                                        OperatingSystem.WINDOWS,
                                                                        null));

        results.forEach(System.out::println);

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().startsWith("17."))
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
                VersionRange.createFromVersionSpec("7"),
                Architecture.AMD64,
                OperatingSystem.WINDOWS,
                null));

        results.forEach(System.out::println);

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().startsWith("7."))
                           .allMatch(r -> r.getOperatingSystem() == OperatingSystem.WINDOWS)
                           .map(FoojayArtifact::getArchitecture).containsAnyOf(Architecture.AMD64, Architecture.X86_64, Architecture.X64);
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

        results.forEach(System.out::println);

        assertThat(results).isNotEmpty();

        //Check that all results have an appropriate java version, arch, etc.
        assertThat(results).allMatch(r -> r.getVersion().startsWith("18"))
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
                VersionNumber.fromText("9"), Latest.AVAILABLE //Find latest available JDK 9
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

    @Nested
    class IsArtifactMajorOnly
    {
        @Test
        void onlyMajor()
        {
            boolean result = FoojayJdkRepository.isArtifactVersionMajorOnly(new DefaultArtifactVersion("1"));
            assertThat(result).isTrue();
        }

        @Test
        void inotOnlyMajor()
        {
            boolean result = FoojayJdkRepository.isArtifactVersionMajorOnly(new DefaultArtifactVersion("1.0"));
            assertThat(result).isFalse();
        }
    }

}
