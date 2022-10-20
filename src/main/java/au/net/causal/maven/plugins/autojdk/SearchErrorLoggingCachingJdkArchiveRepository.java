package au.net.causal.maven.plugins.autojdk;

import java.util.Collection;
import java.util.StringJoiner;

public class SearchErrorLoggingCachingJdkArchiveRepository<A extends JdkArtifact>
extends SearchErrorLoggingJdkArchiveRepository<A>
implements CachingJdkArchiveRepository<A>
{
    private final CachingJdkArchiveRepository<A> repository;

    public SearchErrorLoggingCachingJdkArchiveRepository(CachingJdkArchiveRepository<A> repository)
    {
        super(repository);
        this.repository = repository;
    }

    @Override
    public JdkArchive<A> saveToCache(JdkArchive<?> archive)
    throws JdkRepositoryException
    {
        return repository.saveToCache(archive);
    }

    @Override
    public Collection<? extends JdkArchive<A>> purgeCache(JdkSearchRequest jdkMatchSearchRequest)
    throws JdkRepositoryException
    {
        return repository.purgeCache(jdkMatchSearchRequest);
    }

    @Override
    public String toString()
    {
        return new StringJoiner(", ", SearchErrorLoggingCachingJdkArchiveRepository.class.getSimpleName() + "[", "]")
                .add("repository=" + repository)
                .toString();
    }
}
