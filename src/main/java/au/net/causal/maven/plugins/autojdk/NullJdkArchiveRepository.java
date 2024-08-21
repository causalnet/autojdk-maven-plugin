package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.ArtifactVersion;

import java.util.Collection;
import java.util.List;

/**
 * A JDK archive repository that never finds any results.
 */
public class NullJdkArchiveRepository implements JdkArchiveRepository<NullJdkArchiveRepository.NullJdkArtifact>
{
    @Override
    public Collection<? extends NullJdkArtifact> search(JdkSearchRequest searchRequest)
    throws JdkRepositoryException
    {
        return List.of();
    }

    @Override
    public JdkArchive<NullJdkArtifact> resolveArchive(NullJdkArtifact jdkArtifact)
    throws JdkRepositoryException
    {
        throw new JdkRepositoryException("Cannot resolve archives from null repositories.");
    }

    @Override
    public void cleanUpAfterArchiveUse(JdkArchive<NullJdkArtifact> archive) throws JdkRepositoryException
    {
        throw new JdkRepositoryException("Cannot clean up archives from null repositories.");
    }

    @Override
    public Collection<? extends JdkArchive<NullJdkArtifact>> purge(JdkSearchRequest jdkMatchSearchRequest)
    throws JdkRepositoryException
    {
        return List.of();
    }

    /**
     * Should never be used.  Here to give a valid type parameter to the null repository, but never actually
     * created because there are never any search results from null repositories.
     */
    public static final class NullJdkArtifact implements JdkArtifact
    {
        private NullJdkArtifact()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getVendor()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ArtifactVersion getVersion()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Architecture getArchitecture()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperatingSystem getOperatingSystem()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ArchiveType getArchiveType()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReleaseType getReleaseType()
        {
            throw new UnsupportedOperationException();
        }
    }
}
