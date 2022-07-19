package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TestAutoJdk
{
    private static final Logger log = LoggerFactory.getLogger(TestAutoJdk.class);

    @Mock
    private LocalJdkResolver localJdkResolver;

    @Mock
    private JdkInstallationTarget jdkInstallationTarget;

    @Mock
    private JdkArchiveRepository<JdkArtifact> jdkArchiveRepository;

    @Nested
    class TestJdkComparator
    {
        @Test
        void versionSorting()
        {
            AutoJdk autoJdk = new AutoJdk(localJdkResolver, jdkInstallationTarget, Collections.singleton(jdkArchiveRepository), StandardVersionTranslationScheme.UNMODIFIED,
                                          AutoJdkConfiguration.defaultAutoJdkConfiguration());

            List<JdkArtifact> artifacts = Arrays.asList(
                    new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("zulu", "7.0.3", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("zulu", "6.0.0", ArchiveType.TAR_GZ)
            );

            artifacts.sort(autoJdk.jdkComparator());

            log.debug("Results:\n" + artifacts.stream().map(JdkArtifact::toString).collect(Collectors.joining("\n")));

            assertThat(artifacts).map(JdkArtifact::getVersion).map(ArtifactVersion::toString).containsExactly(
                "6.0.0",
                "7.0.2",
                "7.0.3"
            );
        }

        @Test
        void archiveTypeSorting()
        {
            AutoJdk autoJdk = new AutoJdk(localJdkResolver, jdkInstallationTarget, Collections.singleton(jdkArchiveRepository), StandardVersionTranslationScheme.UNMODIFIED,
                                          AutoJdkConfiguration.defaultAutoJdkConfiguration());


            List<JdkArtifact> artifacts = Arrays.asList(
                    new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.ZIP)
            );

            artifacts.sort(autoJdk.jdkComparator());

            log.debug("Results:\n" + artifacts.stream().map(JdkArtifact::toString).collect(Collectors.joining("\n")));

            assertThat(artifacts).map(JdkArtifact::getArchiveType).containsExactly(
                    ArchiveType.ZIP,
                    ArchiveType.TAR_GZ
            );
        }

        @Test
        void preferredVendorSortingDefault()
        {
            AutoJdk autoJdk = new AutoJdk(localJdkResolver, jdkInstallationTarget, Collections.singleton(jdkArchiveRepository), StandardVersionTranslationScheme.UNMODIFIED,
                                          AutoJdkConfiguration.defaultAutoJdkConfiguration());

            List<JdkArtifact> artifacts = Arrays.asList(
                    new SimpleJdkArtifact("galahjdk", "7.0.2", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("temurin", "7.0.2", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("zzzcockatoojdk", "7.0.2", ArchiveType.TAR_GZ)
            );

            artifacts.sort(autoJdk.jdkComparator());

            log.debug("Results:\n" + artifacts.stream().map(JdkArtifact::toString).collect(Collectors.joining("\n")));

            assertThat(artifacts).map(JdkArtifact::getVendor).containsExactly(
                    "galahjdk", //Unknown, so original order
                    "zzzcockatoojdk", //Unknown, so original order
                    "temurin", //Less preferred
                    "zulu" //Most preferred
            );
        }

        @Test
        void preferredVendorSortingSpecified()
        {
            AutoJdk autoJdk = new AutoJdk(localJdkResolver, jdkInstallationTarget, Collections.singleton(jdkArchiveRepository), StandardVersionTranslationScheme.UNMODIFIED,
                                          new AutoJdkConfiguration(List.of(
                                                  "galahjdk", //galahjdk is preferred
                                                  AutoJdkConfiguration.WILDCARD_VENDOR, //all unknowns
                                                  "zzzcockatoojdk"), //cockatoo jdk is preferred even less than unknowns
                                               List.of()));

            List<JdkArtifact> artifacts = Arrays.asList(
                    new SimpleJdkArtifact("galahjdk", "7.0.2", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("temurin", "7.0.2", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("zzzcockatoojdk", "7.0.2", ArchiveType.TAR_GZ)
            );

            artifacts.sort(autoJdk.jdkComparator());

            log.debug("Results:\n" + artifacts.stream().map(JdkArtifact::toString).collect(Collectors.joining("\n")));

            assertThat(artifacts).map(JdkArtifact::getVendor).containsExactly(
                    "zzzcockatoojdk", //Preferred less than unknowns
                    "temurin", //Unknown, original order
                    "zulu", //Unknown, original order
                    "galahjdk" //Most preferred
            );
        }

        @Test
        void combinedSorting()
        {
            AutoJdk autoJdk = new AutoJdk(localJdkResolver, jdkInstallationTarget, Collections.singleton(jdkArchiveRepository), StandardVersionTranslationScheme.UNMODIFIED,
                                          AutoJdkConfiguration.defaultAutoJdkConfiguration());


            List<JdkArtifact> artifacts = Arrays.asList(
                    new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("zulu", "7.0.0", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.ZIP),
                    new SimpleJdkArtifact("anotherjdk", "7.0.2", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("temurin", "7.0.3", ArchiveType.TAR_GZ),
                    new SimpleJdkArtifact("temurin", "7.0.2", ArchiveType.TAR_GZ)

            );

            artifacts.sort(autoJdk.jdkComparator());

            log.debug("Results:\n" + artifacts.stream().map(JdkArtifact::toString).collect(Collectors.joining("\n")));

            assertThat(artifacts).containsExactly(
                new SimpleJdkArtifact("anotherjdk", "7.0.2", ArchiveType.TAR_GZ), //unknown JDK, least preferable
                new SimpleJdkArtifact("temurin", "7.0.2", ArchiveType.TAR_GZ), //known JDK but lower than vendor zulu
                new SimpleJdkArtifact("temurin", "7.0.3", ArchiveType.TAR_GZ),
                new SimpleJdkArtifact("zulu", "7.0.0", ArchiveType.TAR_GZ), //preferenced vendor, lowest version
                new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.ZIP), //preferenced vendor, zip less preferable to tar.gz
                new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.TAR_GZ) //most preferable - preferred vendor, highest version, .tar.gz
            );
        }
    }
}
