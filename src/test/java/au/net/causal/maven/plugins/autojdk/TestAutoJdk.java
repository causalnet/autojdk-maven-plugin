package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Mock
    private JdkSearchUpdateChecker jdkSearchUpdateChecker;

    @Mock
    private Clock clock;

    @Nested
    class Full
    {
        @TempDir
        private Path tempDir;

        private Path repositoryDir;
        private Path downloadDir;

        private AutoJdk autoJdk;

        @BeforeEach
        void setUpDirs()
        throws IOException
        {
            repositoryDir = tempDir.resolve("repository");
            downloadDir = tempDir.resolve("download");
            Files.createDirectories(repositoryDir);
            Files.createDirectories(downloadDir);
        }

        @BeforeEach
        void setUpAutoJdk()
        {
            autoJdk = new AutoJdk(localJdkResolver, jdkInstallationTarget,
                              List.of(jdkArchiveRepository),
                              StandardVersionTranslationScheme.UNMODIFIED,
                              AutoJdkConfiguration.defaultAutoJdkConfiguration(), jdkSearchUpdateChecker, clock);
        }

        @Test
        void localJdkAlreadyExistsAndNoRemoteRepositories()
        throws Exception
        {
            LocalJdk jdk = new AutoJdkInstalledJdkSystem.AutoJdkInstallation(tempDir.resolve("myjdk"), new LocalJdkMetadata(
                    "zulu", "17.0.0", ReleaseType.GA, Architecture.X86_64, OperatingSystem.LINUX
            ));
            when(localJdkResolver.getInstalledJdks(eq(ReleaseType.GA))).thenAnswer(inv -> List.of(jdk));

            JdkSearchRequest request = new JdkSearchRequest(
                    VersionRange.createFromVersionSpec("[17, 18)"),
                    Architecture.X86_64,
                    OperatingSystem.LINUX,
                    null,
                    ReleaseType.GA);

            LocalJdk result = autoJdk.prepareJdk(request);

            assertThat(result).isEqualTo(jdk);
        }

        @Test
        void noJdkFound()
        throws Exception
        {
            when(localJdkResolver.getInstalledJdks(eq(ReleaseType.GA))).thenAnswer(inv -> List.of());

            JdkSearchRequest request = new JdkSearchRequest(
                    VersionRange.createFromVersionSpec("[17, 18)"),
                    Architecture.X86_64,
                    OperatingSystem.LINUX,
                    null,
                    ReleaseType.GA);

            assertThatExceptionOfType(JdkNotFoundException.class).isThrownBy(() -> autoJdk.prepareJdk(request));
        }

        /**
         * An existing system with JDK 17.0.0 searches for updates and finds JDK 17.0.1 available, installs the new version and uses it.
         */
        @Test
        void updateToMoreRecentJdk()
        throws Exception
        {
            AtomicBoolean newJdkInstalled = new AtomicBoolean(false);

            LocalJdk jdk = new AutoJdkInstalledJdkSystem.AutoJdkInstallation(tempDir.resolve("myjdk"), new LocalJdkMetadata(
                    "zulu", "17.0.0", ReleaseType.GA, Architecture.X86_64, OperatingSystem.LINUX
            ));

            //New JDK that was downloaded and eventually is installed
            LocalJdk newJdk = new AutoJdkInstalledJdkSystem.AutoJdkInstallation(tempDir.resolve("myjdk"), new LocalJdkMetadata(
                    "zulu", "17.0.1", ReleaseType.GA, Architecture.X86_64, OperatingSystem.LINUX
            ));
            SimpleJdkArtifact remoteJdk = new SimpleJdkArtifact("zulu", "17.0.1", ArchiveType.TAR_GZ);

            //Once the remote JDK has been downloaded, this is where it is downloaded to
            JdkArchive<SimpleJdkArtifact> downloadedJdkArchive = new JdkArchive<>(remoteJdk, tempDir.resolve("downloaded-jdk.tar.gz"));

            when(jdkInstallationTarget.installJdkFromArchive(eq(downloadedJdkArchive.getFile()), any())).then(inv ->
            {
                newJdkInstalled.set(true);
                return tempDir.resolve("myjdknew");
            });
            when(localJdkResolver.getInstalledJdks(eq(ReleaseType.GA))).thenAnswer(inv ->
            {
                if (newJdkInstalled.get())
                    return List.of(jdk, newJdk);
                else
                    return List.of(jdk);
            });

            JdkSearchRequest request = new JdkSearchRequest(
                    VersionRange.createFromVersionSpec("[17, 18)"),
                    Architecture.X86_64,
                    OperatingSystem.LINUX,
                    null,
                    ReleaseType.GA);

            when(jdkArchiveRepository.search(any())).thenAnswer(inv -> List.of(remoteJdk));
            when(jdkArchiveRepository.resolveArchive(eq(remoteJdk))).thenAnswer(inv -> downloadedJdkArchive);

            LocalJdk result = autoJdk.prepareJdk(request);

            verify(jdkInstallationTarget).installJdkFromArchive(eq(downloadedJdkArchive.getFile()), any());

            assertThat(result).isEqualTo(newJdk);
        }
    }

    @Nested
    class TestJdkComparator
    {
        @Test
        void versionSorting()
        {
            AutoJdk autoJdk = new AutoJdk(localJdkResolver, jdkInstallationTarget, Collections.singleton(jdkArchiveRepository), StandardVersionTranslationScheme.UNMODIFIED,
                                          AutoJdkConfiguration.defaultAutoJdkConfiguration(), jdkSearchUpdateChecker, clock);

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
                                          AutoJdkConfiguration.defaultAutoJdkConfiguration(), jdkSearchUpdateChecker, clock);


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
                                          AutoJdkConfiguration.defaultAutoJdkConfiguration(), jdkSearchUpdateChecker, clock);

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
                                          new AutoJdkConfiguration(null, Collections.emptyList(),
                                               List.of(
                                                  "galahjdk", //galahjdk is preferred
                                                  AutoJdkConfiguration.WILDCARD_VENDOR, //all unknowns
                                                  "zzzcockatoojdk"), //cockatoo jdk is preferred even less than unknowns
                                               List.of(), AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY, AutoJdkConfiguration.DEFAULT_JDK_REPOSITORIES), jdkSearchUpdateChecker, clock);

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
                                          AutoJdkConfiguration.defaultAutoJdkConfiguration(), jdkSearchUpdateChecker, clock);


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

    private static class CachedJdkArtifact extends SimpleJdkArtifact
    {
        private JdkArtifact original;

        public CachedJdkArtifact(JdkArtifact original)
        {
            super(original.getVendor(), original.getVersion(), original.getArchitecture(), original.getOperatingSystem(), original.getArchiveType(), original.getReleaseType());
            this.original = original;
        }

        public JdkArtifact getOriginalArtifact()
        {
            return original;
        }
    }

}
