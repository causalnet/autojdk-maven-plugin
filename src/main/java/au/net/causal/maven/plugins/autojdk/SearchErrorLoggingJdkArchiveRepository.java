package au.net.causal.maven.plugins.autojdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Errors that are thrown from JDK searches are logged to a logger, not thrown, and an empty result set is returned instead.
 * Archive resolution is unchanged from its delegate.
 *
 * @param <A> JDK artifact type.
 */
public class SearchErrorLoggingJdkArchiveRepository<A extends JdkArtifact> implements JdkArchiveRepository<A>
{
    private static final Logger log = LoggerFactory.getLogger(SearchErrorLoggingJdkArchiveRepository.class);

    private final JdkArchiveRepository<A> repository;

    /**
     * Wraps each element in a list of JDK archive repositories with a search-error-logging repository wrapper.
     *
     * @param repositories repositories to wrap.
     *
     * @return a list of repositories that are wrapped.
     */
    public static List<? extends SearchErrorLoggingJdkArchiveRepository<?>> wrapRepositories(List<? extends JdkArchiveRepository<?>> repositories)
    {
        return repositories.stream()
                           .map(SearchErrorLoggingJdkArchiveRepository::wrapRepository)
                           .collect(Collectors.toUnmodifiableList());
    }

    private static SearchErrorLoggingJdkArchiveRepository<?> wrapRepository(JdkArchiveRepository<?> repository)
    {
        return new SearchErrorLoggingJdkArchiveRepository<>(repository);
    }

    /**
     * Creates an error logging archive repository from an existing repository.
     *
     * @param repository the repository.
     */
    public SearchErrorLoggingJdkArchiveRepository(JdkArchiveRepository<A> repository)
    {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Collection<? extends A> search(JdkSearchRequest searchRequest) throws JdkRepositoryException
    {
        try
        {
            return repository.search(searchRequest);
        }
        catch (JdkRepositoryException e)
        {
            logSearchError(e);
            return Collections.emptyList();
        }
    }

    protected void logSearchError(JdkRepositoryException e)
    {
        log.warn("Failed to search repository " + repository + " for JDK: " + e.getMessage());
        log.debug("Failed to search repository " + repository + " for JDK: " + e.getMessage(), e);
    }

    @Override
    public JdkArchive<A> resolveArchive(A jdkArtifact) throws JdkRepositoryException
    {
        return repository.resolveArchive(jdkArtifact);
    }

    @Override
    public void cleanUpAfterArchiveUse(JdkArchive<A> archive) throws JdkRepositoryException
    {
        repository.cleanUpAfterArchiveUse(archive);
    }

    @Override
    public Collection<? extends JdkArchive<A>> purge(JdkSearchRequest jdkMatchSearchRequest) throws JdkRepositoryException
    {
        return repository.purge(jdkMatchSearchRequest);
    }

    @Override
    public String toString()
    {
        return new StringJoiner(", ", SearchErrorLoggingJdkArchiveRepository.class.getSimpleName() + "[", "]")
                .add("repository=" + repository)
                .toString();
    }
}
