package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.xml.metadata.MavenJdkArtifactMetadata;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Wrapper for another JDK archive repository that caches artifacts in the local Maven repository under
 * a specified groupId.
 *
 * @param <A> types of JDK artifacts of the underlying repository.
 */
public class LocalRepositoryCachingRepository<A extends JdkArtifact>
implements JdkArchiveRepository<A>
{
    private final String groupId;
    private final JdkArchiveRepository<A> repository;
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;
    private final ExceptionalSupplier<Path, IOException> tempDirectorySupplier;
    private final AutoJdkXmlManager xmlManager;

    /**
     * Creates a local caching repository.
     *
     * @param groupId the group ID to store cached artifacts under in the local repository.
     * @param repository the wrapped JDK archive repository.
     * @param repositorySystem Maven repository system.
     * @param repositorySystemSession Maven repository system session.
     * @param tempDirectorySupplier supplier for a temporary directory.
     * @param xmlManager JAXB XML manager.
     */
    public LocalRepositoryCachingRepository(String groupId, JdkArchiveRepository<A> repository,
                                            RepositorySystem repositorySystem,
                                            RepositorySystemSession repositorySystemSession,
                                            ExceptionalSupplier<Path, IOException> tempDirectorySupplier,
                                            AutoJdkXmlManager xmlManager)
    {
        this.groupId = Objects.requireNonNull(groupId);
        this.repository = Objects.requireNonNull(repository);
        this.repositorySystem = Objects.requireNonNull(repositorySystem);
        this.repositorySystemSession = Objects.requireNonNull(repositorySystemSession);
        this.tempDirectorySupplier = Objects.requireNonNull(tempDirectorySupplier);
        this.xmlManager = Objects.requireNonNull(xmlManager);
    }

    @Override
    public Collection<? extends A> search(JdkSearchRequest searchRequest)
    throws JdkRepositoryException
    {
        return repository.search(searchRequest);
    }

    private Artifact resolveArtifactFromLocalRepository(A jdkArtifact)
    {
        Artifact mavenArtifact = mavenArtifactForJdkArtifact(jdkArtifact);
        ArtifactRequest lookupRequest = new ArtifactRequest(mavenArtifact, Collections.emptyList() /*no remotes, local only*/, null);
        try
        {
            ArtifactResult lookupResult = repositorySystem.resolveArtifact(repositorySystemSession, lookupRequest);

            //If we get here, archive was already found in the local repo
            return lookupResult.getArtifact();
        }
        catch (ArtifactResolutionException e)
        {
            //Not found in local repo, that's fine, move on to downloading it
            return mavenArtifact;
        }
    }

    @Override
    public JdkArchive<A> resolveArchive(A jdkArtifact)
    throws JdkRepositoryException
    {
        //First check if we don't already have a cached version in the local repo, if we do just return that
        Artifact mavenArtifact = resolveArtifactFromLocalRepository(jdkArtifact);
        if (mavenArtifact.getFile() != null)
            return new JdkArchive<>(jdkArtifact, mavenArtifact.getFile().toPath());

        //Resolve using real repository
        JdkArchive<A> archive = repository.resolveArchive(jdkArtifact);

        //Cache the archive in the local repo
        InstallRequest request = new InstallRequest();
        mavenArtifact = mavenArtifact.setFile(archive.getFile().toFile());
        request.setArtifacts(Collections.singletonList(mavenArtifact));
        try
        {
            repositorySystem.install(repositorySystemSession, request);

            //Re-resolve to local repo so file of artifact gets filled in
            mavenArtifact = mavenArtifact.setFile(null);
            ArtifactRequest reresolveRequest = new ArtifactRequest(mavenArtifact, Collections.emptyList(), null);
            ArtifactResult reresolveResult = repositorySystem.resolveArtifact(repositorySystemSession, reresolveRequest);
            File jdkArchiveInLocalRepo = reresolveResult.getArtifact().getFile();

            //Also upload metadata
            Path tempMetadataFile = tempDirectorySupplier.get().resolve(jdkArchiveInLocalRepo.toPath().getFileName().toString() + "." + MavenArtifactJdkArchiveRepository.AUTOJDK_METADATA_EXTENSION);
            try
            {
                generateJdkArtifactMetadataFile(new MavenJdkArtifactMetadata(Collections.singleton(archive.getArtifact().getArchiveType()), archive.getArtifact().getReleaseType()), tempMetadataFile);
                Artifact metadataArtifact = autoJdkMetadataArtifactForArchive(mavenArtifact);
                metadataArtifact = metadataArtifact.setFile(tempMetadataFile.toFile());
                InstallRequest metadataInstallRequest = new InstallRequest();
                metadataInstallRequest.setArtifacts(Collections.singleton(metadataArtifact));
                repositorySystem.install(repositorySystemSession, metadataInstallRequest);
            }
            finally
            {
                //Cleanup metadata file once it is installed to the local repo (or failed)
                Files.deleteIfExists(tempMetadataFile);
            }

            //Original file can be deleted now that it's saved in local repo
            repository.cleanUpAfterArchiveUse(archive);

            return new JdkArchive<>(jdkArtifact, jdkArchiveInLocalRepo.toPath());
        }
        catch (IOException | InstallationException | ArtifactResolutionException | AutoJdkXmlManager.XmlWriteException e)
        {
            throw new JdkRepositoryException("Failed to install JDK archive artifact to local repo: " + e.getMessage(), e);
        }
    }

    protected Artifact mavenArtifactForJdkArtifact(JdkArtifact jdkArtifact)
    {
        return new MavenJdkArtifact(groupId, jdkArtifact).getArtifact();
    }

    private Artifact autoJdkMetadataArtifactForArchive(Artifact archiveArtifact)
    {
        return new DefaultArtifact(archiveArtifact.getGroupId(), archiveArtifact.getArtifactId(), archiveArtifact.getClassifier(),
                MavenArtifactJdkArchiveRepository.AUTOJDK_METADATA_EXTENSION, archiveArtifact.getVersion());
    }

    private void generateJdkArtifactMetadataFile(MavenJdkArtifactMetadata metadata, Path file)
    throws AutoJdkXmlManager.XmlWriteException
    {
        xmlManager.writeFile(metadata, file);
    }

    @Override
    public void cleanUpAfterArchiveUse(JdkArchive<A> archive)
    throws JdkRepositoryException
    {
        //Do nothing, once artifact is installed in local repo we keep it
    }

    private void purgeFromLocalRepo(JdkArchive<A> archive)
    throws JdkRepositoryException
    {
        //There is no local repo API for deletion, so just delete from filesystem
        //This is what maven dependency plugin's purge does as well
        Path archiveFileInLocalRepo = archive.getFile();
        try
        {
            Files.deleteIfExists(archiveFileInLocalRepo);
        }
        catch (IOException e)
        {
            throw new JdkRepositoryException("Failed to delete local repository archive " + archiveFileInLocalRepo + ": " + e.getMessage(), e);
        }

        //Also delete metadata if it exists
        Artifact mavenArtifact = mavenArtifactForJdkArtifact(archive.getArtifact());
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

    @Override
    public Collection<? extends JdkArchive<A>> purge(JdkSearchRequest jdkMatchSearchRequest)
    throws JdkRepositoryException
    {
        Collection<? extends JdkArchive<A>> purgedFromUnderlyingRepo = repository.purge(jdkMatchSearchRequest);

        Collection<? extends A> cacheSearchResults = search(jdkMatchSearchRequest);
        List<JdkArchive<A>> purgedArchives = new ArrayList<>(cacheSearchResults.size() + purgedFromUnderlyingRepo.size());
        purgedArchives.addAll(purgedFromUnderlyingRepo);

        for (A cacheSearchResult : cacheSearchResults)
        {
            JdkArchive<A> localRepositoryArchive = resolveArchive(cacheSearchResult);
            purgeFromLocalRepo(localRepositoryArchive);
            purgedArchives.add(localRepositoryArchive);
        }

        return purgedArchives;
    }

    @Override
    public String toString()
    {
        return new StringJoiner(", ", LocalRepositoryCachingRepository.class.getSimpleName() + "[", "]")
                .add("repository=" + repository)
                .add("groupId=" + groupId)
                .toString();
    }
}
