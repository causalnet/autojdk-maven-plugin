package au.net.causal.maven.plugins.autojdk;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class MavenArtifactJdkArchiveRepository implements JdkArchiveRepository<MavenJdkArtifact>
{
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;
    private final List<RemoteRepository> remoteRepositories;

    public MavenArtifactJdkArchiveRepository(RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession,
                                             List<RemoteRepository> remoteRepositories)
    {
        this.repositorySystem = Objects.requireNonNull(repositorySystem);
        this.repositorySystemSession = Objects.requireNonNull(repositorySystemSession);
        this.remoteRepositories = List.copyOf(remoteRepositories);
    }

    @Override
    public Collection<? extends MavenJdkArtifact> search(JdkSearchRequest searchRequest)
    throws JdkRepositoryException
    {
        

        //TODO
        return null;
    }

    @Override
    public JdkArchive resolveArchive(MavenJdkArtifact jdkArtifact) throws JdkRepositoryException
    {
        ArtifactRequest request = new ArtifactRequest(jdkArtifact.getArtifact(), remoteRepositories, null);
        try
        {
            ArtifactResult result = repositorySystem.resolveArtifact(repositorySystemSession, request);
            return new JdkArchive(jdkArtifact, result.getArtifact().getFile());
        }
        catch (ArtifactResolutionException e)
        {
            //Could not find artifact
            throw new JdkRepositoryException("Failed to resolve JDK archive: " + e.getMessage(), e);
        }
    }
}
