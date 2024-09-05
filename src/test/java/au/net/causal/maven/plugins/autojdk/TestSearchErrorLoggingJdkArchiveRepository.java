package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.xml.metadata.ArchiveType;
import au.net.causal.maven.plugins.autojdk.xml.metadata.ReleaseType;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestSearchErrorLoggingJdkArchiveRepository
{
    @Mock
    private JdkArchiveRepository<SimpleJdkArtifact> repo;

    @Mock
    private JdkArchiveRepository<SimpleJdkArtifact> repo2;

    @Test
    void searchResultsPassThrough()
    throws Exception
    {
        SimpleJdkArtifact aSearchResult = new SimpleJdkArtifact("zulu", "17.0", ArchiveType.TAR_GZ);
        when(repo.search(any())).thenAnswer(a -> List.of(aSearchResult));

        SearchErrorLoggingJdkArchiveRepository<SimpleJdkArtifact> r = new SearchErrorLoggingJdkArchiveRepository<>(repo);

        JdkSearchRequest request = new JdkSearchRequest(VersionRange.createFromVersionSpec("[17, 18)"), Architecture.X64, OperatingSystem.LINUX, null, ReleaseType.GA);
        Collection<? extends SimpleJdkArtifact> searchResults = r.search(request);

        assertThat(searchResults).singleElement().isEqualTo(aSearchResult);
    }

    @Test
    void errorsAreSuppressed()
    throws Exception
    {
        when(repo.search(any())).thenThrow(JdkRepositoryException.class);

        SearchErrorLoggingJdkArchiveRepository<SimpleJdkArtifact> r = new SearchErrorLoggingJdkArchiveRepository<>(repo);

        JdkSearchRequest request = new JdkSearchRequest(VersionRange.createFromVersionSpec("[17, 18)"), Architecture.X64, OperatingSystem.LINUX, null, ReleaseType.GA);
        Collection<? extends SimpleJdkArtifact> searchResults = r.search(request);

        //Should not fail, error should be logged and empty list returned
        assertThat(searchResults).isEmpty();
    }

    @Test
    void wrapList()
    throws Exception
    {
        List<? extends SearchErrorLoggingJdkArchiveRepository<?>> repos = SearchErrorLoggingJdkArchiveRepository.wrapRepositories(List.of(repo, repo2));

        when(repo.search(any())).thenThrow(JdkRepositoryException.class);
        CompositeJdkArchiveRepository.WrappedJdkArtifact<SimpleJdkArtifact> aSearchResult =
                new CompositeJdkArchiveRepository.WrappedJdkArtifact<>(repo2, new SimpleJdkArtifact("zulu", "17.0", ArchiveType.TAR_GZ));
        when(repo2.search(any())).thenAnswer(a -> List.of(aSearchResult));

        CompositeJdkArchiveRepository compRepo = new CompositeJdkArchiveRepository(CompositeJdkArchiveRepository.SearchType.EXHAUSTIVE, repos);

        JdkSearchRequest request = new JdkSearchRequest(VersionRange.createFromVersionSpec("[17, 18)"), Architecture.X64, OperatingSystem.LINUX, null, ReleaseType.GA);
        Collection<? extends CompositeJdkArchiveRepository.WrappedJdkArtifact<?>> searchResults = compRepo.search(request);

        //Both repos called, first repo failed but second one gave results
        //First repo error will be logged but suppressed
        assertThat(searchResults).hasSize(1);
        assertThat(searchResults).singleElement().satisfies(searchResult ->
        {
            assertThat(searchResult.getWrappedArtifact()).isEqualTo(aSearchResult);
        });

        verify(repo).search(any());
        verify(repo2).search(any());
    }
}
