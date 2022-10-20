package au.net.causal.maven.plugins.autojdk;

import java.util.Collection;

/**
 * A JDK archive repository that caches downloaded artifacts on the filesystem and has the capability of storing
 * cached downloads from other repositories which are reused later for searches.
 */
public interface CachingJdkArchiveRepository<A extends JdkArtifact> extends JdkArchiveRepository<A>
{
    /**
     * Saves a downloaded JDK archive into the local filesystem cache of this repository.
     *
     * @param archive the archive to save.
     *
     * @return the archive saved to the cache.
     *
     * @throws JdkRepositoryException if an error occurs.
     */
    public JdkArchive<A> saveToCache(JdkArchive<?> archive)
    throws JdkRepositoryException;

    /**
     * Deletes locally cached files (metadata, archives) for any JDK matching the specified search criteria.
     *
     * @param jdkMatchSearchRequest JDKs matching this search request will have their local data purged.
     *
     * @return a list of JDK archives that had their locally cached data purged.
     *
     * @throws JdkRepositoryException if an error occurs.
     */
    //TODO change purge cache request
    public Collection<? extends JdkArchive<A>> purgeCache(JdkSearchRequest jdkMatchSearchRequest)
    throws JdkRepositoryException;
}
