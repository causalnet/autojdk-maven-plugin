package au.net.causal.maven.plugins.autojdk;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestCompositeJdkArchiveRepository
{
    @Mock
    private JdkArchiveRepository<SimpleJdkArtifact> repo1;

    @Mock
    private JdkArchiveRepository<SimpleJdkArtifact> repo2;

    @Mock
    private JdkArchiveRepository<SimpleJdkArtifact> repo3;

    @TempDir
    private File tempDir;

    @Test
    void emptySearchesHitAllReposUsingFirstSuccessSearchType()
    throws Exception
    {
        when(repo1.search(any())).thenReturn(List.of());
        when(repo2.search(any())).thenReturn(List.of());
        when(repo3.search(any())).thenReturn(List.of());

        CompositeJdkArchiveRepository mainRepo = new CompositeJdkArchiveRepository(CompositeJdkArchiveRepository.SearchType.FIRST_SUCCESS,
                                                                                   List.of(repo1, repo2, repo3));

        JdkSearchRequest searchRequest = new JdkSearchRequest(VersionRange.createFromVersionSpec("[17, 18)"), Architecture.X64, OperatingSystem.LINUX, null, ReleaseType.GA);
        Collection<? extends CompositeJdkArchiveRepository.WrappedJdkArtifact<?>> results = mainRepo.search(searchRequest);
        assertThat(results).isEmpty();

        //Ensure each repository was searched
        verify(repo1).search(any());
        verify(repo2).search(any());
        verify(repo3).search(any());
    }

    @Test
    void emptySearchesHitAllReposUsingExhaustiveSearchType()
    throws Exception
    {
        when(repo1.search(any())).thenReturn(List.of());
        when(repo2.search(any())).thenReturn(List.of());
        when(repo3.search(any())).thenReturn(List.of());

        CompositeJdkArchiveRepository mainRepo = new CompositeJdkArchiveRepository(CompositeJdkArchiveRepository.SearchType.EXHAUSTIVE,
                                                                                   List.of(repo1, repo2, repo3));

        JdkSearchRequest searchRequest = new JdkSearchRequest(VersionRange.createFromVersionSpec("[17, 18)"), Architecture.X64, OperatingSystem.LINUX, null, ReleaseType.GA);
        Collection<? extends CompositeJdkArchiveRepository.WrappedJdkArtifact<?>> results = mainRepo.search(searchRequest);
        assertThat(results).isEmpty();

        //Ensure each repository was searched
        verify(repo1).search(any());
        verify(repo2).search(any());
        verify(repo3).search(any());
    }

    @Test
    void firstSuccessSearchTypeStopsWhenFindingResults()
    throws Exception
    {
        when(repo1.search(any())).thenReturn(List.of());
        when(repo2.search(any())).thenAnswer(i -> List.of(new SimpleJdkArtifact("zulu", "17.0", ArchiveType.TAR_GZ)));

        CompositeJdkArchiveRepository mainRepo = new CompositeJdkArchiveRepository(CompositeJdkArchiveRepository.SearchType.FIRST_SUCCESS,
                                                                                   List.of(repo1, repo2, repo3));

        JdkSearchRequest searchRequest = new JdkSearchRequest(VersionRange.createFromVersionSpec("[17, 18)"), Architecture.X64, OperatingSystem.LINUX, null, ReleaseType.GA);
        Collection<? extends CompositeJdkArchiveRepository.WrappedJdkArtifact<?>> results = mainRepo.search(searchRequest);
        assertThat(results).singleElement().satisfies(r ->
        {
            assertThat(r.getSourceRepository()).isSameAs(repo2);
            assertThat(r.getVendor()).isEqualTo("zulu");
            assertThat(r.getVersion()).isEqualTo(new DefaultArtifactVersion("17.0"));
            assertThat(r.getArchiveType()).isEqualTo(ArchiveType.TAR_GZ);
        });

        //Ensure each repository was searched
        verify(repo1).search(any());
        verify(repo2).search(any());
        verify(repo3, never()).search(any());
    }

    @Test
    void exhaustiveSearchTypeDoesNotStopWhenFindingResults()
    throws Exception
    {
        when(repo1.search(any())).thenReturn(List.of());
        when(repo2.search(any())).thenAnswer(i -> List.of(new SimpleJdkArtifact("zulu", "17.0", ArchiveType.TAR_GZ)));
        when(repo3.search(any())).thenAnswer(i -> List.of(new SimpleJdkArtifact("zulu", "17.1", ArchiveType.TAR_GZ)));

        CompositeJdkArchiveRepository mainRepo = new CompositeJdkArchiveRepository(CompositeJdkArchiveRepository.SearchType.EXHAUSTIVE,
                                                                                   List.of(repo1, repo2, repo3));

        JdkSearchRequest searchRequest = new JdkSearchRequest(VersionRange.createFromVersionSpec("[17, 18)"), Architecture.X64, OperatingSystem.LINUX, null, ReleaseType.GA);
        Collection<? extends CompositeJdkArchiveRepository.WrappedJdkArtifact<?>> results = mainRepo.search(searchRequest);
        assertThat(results).hasSize(2);
        assertThat(results).first().satisfies(r ->
        {
            assertThat(r.getSourceRepository()).isSameAs(repo2);
            assertThat(r.getVendor()).isEqualTo("zulu");
            assertThat(r.getVersion()).isEqualTo(new DefaultArtifactVersion("17.0"));
            assertThat(r.getArchiveType()).isEqualTo(ArchiveType.TAR_GZ);
        });
        assertThat(results).last().satisfies(r ->
        {
            assertThat(r.getSourceRepository()).isSameAs(repo3);
            assertThat(r.getVendor()).isEqualTo("zulu");
            assertThat(r.getVersion()).isEqualTo(new DefaultArtifactVersion("17.1"));
            assertThat(r.getArchiveType()).isEqualTo(ArchiveType.TAR_GZ);
        });

        //Ensure each repository was searched
        verify(repo1).search(any());
        verify(repo2).search(any());
        verify(repo3).search(any());
    }

    @Test
    void resolveArchiveResolvesAgainstCorrectRepoForSearchResults()
    throws Exception
    {
        SimpleJdkArtifact artifact = new SimpleJdkArtifact("zulu", "17.0", ArchiveType.TAR_GZ);
        when(repo1.search(any())).thenReturn(List.of());
        when(repo2.search(any())).thenAnswer(i -> List.of(artifact));
        when(repo3.search(any())).thenReturn(List.of());
        when(repo2.resolveArchive(any())).thenReturn(new JdkArchive(artifact, tempDir));

        CompositeJdkArchiveRepository mainRepo = new CompositeJdkArchiveRepository(CompositeJdkArchiveRepository.SearchType.EXHAUSTIVE,
                                                                                   List.of(repo1, repo2, repo3));

        JdkSearchRequest searchRequest = new JdkSearchRequest(VersionRange.createFromVersionSpec("[17, 18)"), Architecture.X64, OperatingSystem.LINUX, null, ReleaseType.GA);
        Collection<? extends CompositeJdkArchiveRepository.WrappedJdkArtifact<?>> results = mainRepo.search(searchRequest);

        CompositeJdkArchiveRepository.WrappedJdkArtifact<?> result = Iterables.getOnlyElement(results);

        JdkArchive archive = mainRepo.resolveArchive(result);
        assertThat(archive.getArtifact()).isEqualTo(artifact);
        assertThat(archive.getFile()).isEqualTo(tempDir);

        verify(repo1, never()).resolveArchive(any());
        verify(repo2).resolveArchive(any());
        verify(repo3, never()).resolveArchive(any());
    }
}
