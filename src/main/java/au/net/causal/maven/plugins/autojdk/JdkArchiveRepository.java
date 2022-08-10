package au.net.causal.maven.plugins.autojdk;

import java.util.Collection;

/**
 * Search for JDKs and resolve/download archives.
 */
public interface JdkArchiveRepository<A extends JdkArtifact>
{
    public Collection<? extends A> search(JdkSearchRequest searchRequest)
    throws JdkRepositoryException;

    public JdkArchive resolveArchive(A jdkArtifact)
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
    public Collection<? extends JdkArchive> purgeCache(JdkPurgeCacheRequest jdkMatchSearchRequest)
    throws JdkRepositoryException;
}
