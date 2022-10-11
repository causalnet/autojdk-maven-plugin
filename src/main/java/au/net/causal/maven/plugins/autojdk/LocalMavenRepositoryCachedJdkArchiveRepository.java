package au.net.causal.maven.plugins.autojdk;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public abstract class LocalMavenRepositoryCachedJdkArchiveRepository<A extends JdkArtifact> implements JdkArchiveRepository<A>
{
    public static final String AUTOJDK_METADATA_EXTENSION = "autojdk-metadata.xml";

    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;
    private final String mavenArtifactGroupId;

    protected LocalMavenRepositoryCachedJdkArchiveRepository(RepositorySystem repositorySystem,
                                                             RepositorySystemSession repositorySystemSession,
                                                             String mavenArtifactGroupId)
    {
        this.repositorySystem = Objects.requireNonNull(repositorySystem);
        this.repositorySystemSession = Objects.requireNonNull(repositorySystemSession);
        this.mavenArtifactGroupId = mavenArtifactGroupId;
    }

    protected Artifact mavenArtifactForJdkArtifact(JdkArtifact jdkArtifact)
    {
        return new MavenJdkArtifact(mavenArtifactGroupId, jdkArtifact).getArtifact();
    }

    protected RepositorySystem getRepositorySystem()
    {
        return repositorySystem;
    }

    protected RepositorySystemSession getRepositorySystemSession()
    {
        return repositorySystemSession;
    }

    protected String getMavenArtifactGroupId()
    {
        return mavenArtifactGroupId;
    }

    protected Artifact autoJdkMetadataArtifactForArchive(Artifact archiveArtifact)
    {
        return new DefaultArtifact(archiveArtifact.getGroupId(), archiveArtifact.getArtifactId(), archiveArtifact.getClassifier(),
                    AUTOJDK_METADATA_EXTENSION, archiveArtifact.getVersion());
    }

    @Override
    public Collection<? extends JdkArchive> purgeCache(JdkPurgeCacheRequest jdkMatchSearchRequest)
            throws JdkRepositoryException
    {
        List<JdkArchive> archivesPurged = new ArrayList<>();

        //Purse all archive types that match the rest of the criteria
        for (ArchiveType archiveType : ArchiveType.values())
        {
            JdkArtifact jdkArtifact = jdkMatchSearchRequest.toJdkArtifact(archiveType);
            Artifact mavenArtifact = mavenArtifactForJdkArtifact(jdkArtifact);
            LocalArtifactRequest localRequest = new LocalArtifactRequest(mavenArtifact, null, null);
            LocalArtifactResult localResult = repositorySystemSession.getLocalRepositoryManager().find(repositorySystemSession, localRequest);
            if (localResult.isAvailable())
            {
                try
                {
                    Files.deleteIfExists(localResult.getFile().toPath());
                    archivesPurged.add(new JdkArchive(jdkArtifact, localResult.getFile()));
                }
                catch (IOException e)
                {
                    throw new JdkRepositoryException("Failed to delete local repository archive " + localResult.getFile() + ": " + e.getMessage(), e);
                }
            }

            //Also metadata if it exists
            Artifact metadataArtifact = autoJdkMetadataArtifactForArchive(mavenArtifact);
            LocalArtifactRequest localMetadataRequest = new LocalArtifactRequest(metadataArtifact, null, null);
            LocalArtifactResult localMetadataResult = repositorySystemSession.getLocalRepositoryManager().find(repositorySystemSession, localMetadataRequest);
            if (localMetadataResult.isAvailable())
            {
                try
                {
                    Files.deleteIfExists(localMetadataResult.getFile().toPath());
                }
                catch (IOException e)
                {
                    throw new JdkRepositoryException("Failed to delete local repository metadata " + localMetadataResult.getFile() + ": " + e.getMessage(), e);
                }
            }
        }

        return archivesPurged;
    }
}
