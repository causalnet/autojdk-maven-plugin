package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.ArtifactVersion;
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
import java.util.Collections;
import java.util.List;
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

    @Mock(strictness = Mock.Strictness.LENIENT)
    private CachingJdkArchiveRepository<JdkArtifact> cachingJdkArchiveRepository;

    @Mock
    private JdkSearchUpdateChecker jdkSearchUpdateChecker;

    @Mock
    private Clock clock;

    @Nested
    class Caching
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
        void setUpMockCachingRepository()
        throws JdkRepositoryException
        {
            when(cachingJdkArchiveRepository.saveToCache(any())).thenAnswer(invocation ->
            {
                JdkArchive<?> arc = invocation.getArgument(0);
                JdkArtifact a = arc.getArtifact();
                Path cachedFile = repositoryDir.resolve(arc.getFile().getFileName());
                Files.copy(arc.getFile(), cachedFile);
                return new JdkArchive<>(new CachedJdkArtifact(a), cachedFile);
            });
        }

        @BeforeEach
        void setUpAutoJdk()
        {
            autoJdk = new AutoJdk(localJdkResolver, jdkInstallationTarget,
                              List.of(jdkArchiveRepository, cachingJdkArchiveRepository),
                              StandardVersionTranslationScheme.UNMODIFIED,
                              AutoJdkConfiguration.defaultAutoJdkConfiguration(), jdkSearchUpdateChecker, clock);
        }

        /**
         * Saves a non-cached archive to a caching repository.
         */
        @Test
        void saveToCacheFromNormalRepository()
        throws Exception
        {
            JdkArtifact artifact = new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.TAR_GZ);
            Path archiveFile = Files.createTempFile(downloadDir, "artifact", ".tar.gz");
            JdkArchive<JdkArtifact> originalArchive = new JdkArchive<>(artifact, archiveFile);

            //Save to cache
            JdkArchive<?> cachedArchive = autoJdk.saveToCache(originalArchive, jdkArchiveRepository);

            //Original archive is in downloads dir, but after caching it is now in the repository
            assertThat(originalArchive.getFile()).hasParent(downloadDir);
            assertThat(cachedArchive.getFile()).hasParent(repositoryDir);

            //Check that the artifact has the same values but the cached one is cached
            assertThat(cachedArchive.getArtifact()).isInstanceOf(CachedJdkArtifact.class);
            CachedJdkArtifact cachedArtifact = (CachedJdkArtifact)cachedArchive.getArtifact();
            assertThat(cachedArtifact.getOriginalArtifact()).isSameAs(artifact);
        }

        /**
         * Saving an artifact that is already from a caching repository should perform no additional caching.
         */
        @Test
        void saveToCacheFromCachingRepository()
        throws Exception
        {
            JdkArtifact artifact = new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.TAR_GZ);
            Path archiveFile = Files.createTempFile(downloadDir, "artifact", ".tar.gz"); //in downloads, but source will be from caching repository
            JdkArchive<JdkArtifact> originalArchive = new JdkArchive<>(artifact, archiveFile);

            //Save to cache
            JdkArchive<?> cachedArchive = autoJdk.saveToCache(originalArchive, cachingJdkArchiveRepository);

            //Verify archive wasn't actually cached again
            verifyNoInteractions(cachingJdkArchiveRepository);

            //Check that the artifact returned is the original one (no extra caching)
            assertThat(cachedArchive).isSameAs(originalArchive);
        }

        @Test
        void compositeUncachedOriginalArtifact()
        throws Exception
        {
            CompositeJdkArchiveRepository compositeRepository = new CompositeJdkArchiveRepository(CompositeJdkArchiveRepository.SearchType.EXHAUSTIVE,
                                                                                                  List.of(jdkArchiveRepository));

            JdkArtifact artifact = new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.TAR_GZ);
            Path archiveFile = Files.createTempFile(downloadDir, "artifact", ".tar.gz");

            CompositeJdkArchiveRepository.WrappedJdkArtifact<JdkArtifact> wrappedArtifact = new CompositeJdkArchiveRepository.WrappedJdkArtifact<>(jdkArchiveRepository, artifact);
            JdkArchive<CompositeJdkArchiveRepository.WrappedJdkArtifact<?>> originalArchive = new JdkArchive<>(wrappedArtifact, archiveFile);

            //Save to cache
            JdkArchive<?> cachedArchive = autoJdk.saveToCache(originalArchive, compositeRepository);

            //Original archive is in downloads dir, but after caching it is now in the repository
            assertThat(originalArchive.getFile()).hasParent(downloadDir);
            assertThat(cachedArchive.getFile()).hasParent(repositoryDir);

            //Check that the artifact has the same values but the cached one is cached
            assertThat(cachedArchive.getArtifact()).isInstanceOf(CachedJdkArtifact.class);
            CachedJdkArtifact cachedArtifact = (CachedJdkArtifact)cachedArchive.getArtifact();
            assertThat(cachedArtifact.getOriginalArtifact()).isSameAs(artifact);
        }

        @Test
        void compositeCachedOriginalArtifact()
        throws Exception
        {
            CompositeJdkArchiveRepository compositeRepository = new CompositeJdkArchiveRepository(CompositeJdkArchiveRepository.SearchType.EXHAUSTIVE,
                                                                                                  List.of(cachingJdkArchiveRepository));

            JdkArtifact artifact = new SimpleJdkArtifact("zulu", "7.0.2", ArchiveType.TAR_GZ);
            Path archiveFile = Files.createTempFile(downloadDir, "artifact", ".tar.gz");

            CompositeJdkArchiveRepository.WrappedJdkArtifact<JdkArtifact> wrappedArtifact = new CompositeJdkArchiveRepository.WrappedJdkArtifact<>(cachingJdkArchiveRepository, artifact);
            JdkArchive<CompositeJdkArchiveRepository.WrappedJdkArtifact<?>> originalArchive = new JdkArchive<>(wrappedArtifact, archiveFile);

            //Save to cache
            JdkArchive<?> cachedArchive = autoJdk.saveToCache(originalArchive, compositeRepository);

            //Verify archive wasn't actually cached again
            verifyNoInteractions(cachingJdkArchiveRepository);

            //Check that the artifact returned is the original one (no extra caching)
            //Might have some unwrapping but the coordinates of both artifacts should be the same
            assertThat(cachedArchive.getFile()).isEqualTo(originalArchive.getFile());
            assertThat(cachedArchive.getArtifact()).isEqualTo(originalArchive.getArtifact().getWrappedArtifact());
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
                                          new AutoJdkConfiguration(Collections.emptyList(),
                                               List.of(
                                                  "galahjdk", //galahjdk is preferred
                                                  AutoJdkConfiguration.WILDCARD_VENDOR, //all unknowns
                                                  "zzzcockatoojdk"), //cockatoo jdk is preferred even less than unknowns
                                               List.of(), AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY), jdkSearchUpdateChecker, clock);

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
