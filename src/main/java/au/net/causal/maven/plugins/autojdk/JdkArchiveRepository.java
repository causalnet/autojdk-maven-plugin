package au.net.causal.maven.plugins.autojdk;

import java.util.Collection;

/**
 * Search for JDKs and resolve/download archives.
 */
public interface JdkArchiveRepository<A extends JdkArtifact>
{
    public Collection<? extends A> search(JdkSearchRequest searchRequest)
    throws JdkRepositoryException;

    public JdkArchive<A> resolveArchive(A jdkArtifact)
    throws JdkRepositoryException;

    /**
     * Called once a resolved archive has been used.  If the archive was a temporary file it will be deleted.
     *
     * @param archive the archive to clean up.
     *
     * @throws JdkRepositoryException if an error occurs.
     */
    public void cleanUpAfterArchiveUse(JdkArchive<A> archive)
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
    public Collection<? extends JdkArchive<A>> purge(JdkSearchRequest jdkMatchSearchRequest)
    throws JdkRepositoryException;
}
