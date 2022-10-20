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

    public void purgeResolvedArchive(JdkArchive<A> archive)
    throws JdkRepositoryException;
}
