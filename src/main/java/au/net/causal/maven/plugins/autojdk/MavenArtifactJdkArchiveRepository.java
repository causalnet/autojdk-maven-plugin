package au.net.causal.maven.plugins.autojdk;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MavenArtifactJdkArchiveRepository implements CachingJdkArchiveRepository<MavenJdkArtifact>
{
    private static final Logger log = LoggerFactory.getLogger(MavenArtifactJdkArchiveRepository.class);

    private static final String AUTOJDK_METADATA_EXTENSION = "autojdk-metadata.xml";

    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;
    private final String mavenArtifactGroupId;

    private final List<RemoteRepository> remoteRepositories;
    private final VendorService vendorService;
    private final AutoJdkXmlManager xmlManager;

    private final ExceptionalSupplier<Path, IOException> tempDirectorySupplier;

    public MavenArtifactJdkArchiveRepository(RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession,
                                             Collection<? extends RemoteRepository> remoteRepositories, String mavenArtifactGroupId,
                                             VendorService vendorService,
                                             AutoJdkXmlManager xmlManager,
                                             ExceptionalSupplier<Path, IOException> tempDirectorySupplier)
    {
        this.repositorySystem = Objects.requireNonNull(repositorySystem);
        this.repositorySystemSession = Objects.requireNonNull(repositorySystemSession);
        this.mavenArtifactGroupId = Objects.requireNonNull(mavenArtifactGroupId);

        this.remoteRepositories = List.copyOf(remoteRepositories);
        this.vendorService = Objects.requireNonNull(vendorService);
        this.xmlManager = Objects.requireNonNull(xmlManager);
        this.tempDirectorySupplier = Objects.requireNonNull(tempDirectorySupplier);
    }

    @Override
    public Collection<? extends MavenJdkArtifact> search(JdkSearchRequest searchRequest)
    throws JdkRepositoryException
    {
        return search(searchRequest, remoteRepositories);
    }

    private Collection<? extends MavenJdkArtifact> search(JdkSearchRequest searchRequest, List<RemoteRepository> remoteRepositoriesToSearch)
    throws JdkRepositoryException
    {
        //If os and arch is not specified, use all platforms as the search
        List<? extends Platform> searchPlatforms;
        if (searchRequest.getOperatingSystem() == null && searchRequest.getArchitecture() == null)
            searchPlatforms = PlatformTools.WELL_KNOWN_PLATFORMS;
        else if (searchRequest.getOperatingSystem() == null)
        {
            searchPlatforms = PlatformTools.WELL_KNOWN_PLATFORMS.stream()
                    .filter(p -> p.getArchitecture() == searchRequest.getArchitecture())
                    .collect(Collectors.toList());
        }
        else if (searchRequest.getArchitecture() == null)
        {
            searchPlatforms = PlatformTools.WELL_KNOWN_PLATFORMS.stream()
                    .filter(p -> p.getOperatingSystem() == searchRequest.getOperatingSystem())
                    .collect(Collectors.toList());
        }
        else
            searchPlatforms = List.of(new Platform(searchRequest.getOperatingSystem(), searchRequest.getArchitecture()));

        List<String> artifactIdsToSearch;

        //Do we know the artifact ID?
        if (searchRequest.getVendor() == null)
        {
            //If not, need to search all known vendors
            try
            {
                artifactIdsToSearch = vendorService.getAllVendors()
                                                   .stream()
                                                   .map(MavenJdkArtifact::vendorToArtifactId)
                                                   .collect(Collectors.toUnmodifiableList());
            }
            catch (VendorServiceException e)
            {
                throw new JdkRepositoryException("Error retrieving vendor list: " + e.getMessage(), e);
            }
        }
        else
            artifactIdsToSearch = Collections.singletonList(MavenJdkArtifact.vendorToArtifactId(searchRequest.getVendor()));

        //Find known artifacts without versions

        //Resolve all known versions
        List<MavenJdkArtifact> matchingArtifacts = new ArrayList<>();
        for (String artifactIdToSearch : artifactIdsToSearch)
        {
            //Only need groupId/artifactId, it searches all extensions / classifiers
            //TODO or does it???
            Artifact searchArtifact = new DefaultArtifact(mavenArtifactGroupId, artifactIdToSearch, null, searchRequest.getVersionRange().toString());
            VersionRangeRequest versionSearchRequest = new VersionRangeRequest(searchArtifact, remoteRepositoriesToSearch, null);

            try
            {
                VersionRangeResult versionSearchResult = repositorySystem.resolveVersionRange(repositorySystemSession, versionSearchRequest);
                for (Exception warningException : versionSearchResult.getExceptions())
                {
                    log.debug("Problem performing version search: " + warningException.getMessage(), warningException);
                }
                List<Version> foundVersions = versionSearchResult.getVersions();

                for (Platform searchPlatform : searchPlatforms)
                {
                    for (Version foundVersion : foundVersions)
                    {
                        String classifier = MavenJdkArtifact.makeClassifier(searchPlatform.getOperatingSystem(), searchPlatform.getArchitecture());
                        Artifact searchArtifactWithClassifier = new DefaultArtifact(searchArtifact.getGroupId(), searchArtifact.getArtifactId(), classifier, searchArtifact.getExtension(), foundVersion.toString());
                        MavenJdkArtifactMetadata mavenMetadata = readMavenJdkArtifactMetadata(searchArtifactWithClassifier, remoteRepositoriesToSearch);

                        //Only include result release type (EA/GA) matches search request (if specified)
                        if (searchRequest.getReleaseType() == null || searchRequest.getReleaseType().equals(mavenMetadata.getReleaseType()))
                        {
                            for (ArchiveType curArchiveType : mavenMetadata.getArchiveTypes())
                            {
                                MavenJdkArtifact curMavenJdkArtifact = new MavenJdkArtifact(mavenArtifactGroupId, artifactIdToSearch, foundVersion.toString(),
                                        searchPlatform.getArchitecture(), searchPlatform.getOperatingSystem(), curArchiveType);
                                matchingArtifacts.add(curMavenJdkArtifact);
                            }
                        }
                    }
                }
            }
            catch (VersionRangeResolutionException e)
            {
                throw new JdkRepositoryException("Error performing version search in Maven repository: " + e.getMessage(), e);
            }
        }

        return matchingArtifacts;
    }

    private MavenJdkArtifactMetadata readMavenJdkArtifactMetadata(Artifact archiveArtifact, List<RemoteRepository> remoteRepositoriesToSearch)
    {
        Artifact metadataArtifact = autoJdkMetadataArtifactForArchive(archiveArtifact);
        ArtifactRequest metadataRequest = new ArtifactRequest(metadataArtifact, remoteRepositoriesToSearch, null);
        try
        {
            ArtifactResult metadataResult = repositorySystem.resolveArtifact(repositorySystemSession, metadataRequest);
            return xmlManager.parseFile(metadataResult.getArtifact().getFile(), MavenJdkArtifactMetadata.class);
        }
        catch (ArtifactResolutionException e)
        {
            //Could not find metadata, so just ignore this search result
            //TODO probably should turn this down because it's reasonable that Maven found artifacts for different OS/architecture
            log.warn("Could not find JDK metadata for " + metadataArtifact, e);

            return new MavenJdkArtifactMetadata(); //No archive types is like an empty result
        }
        catch (AutoJdkXmlManager.XmlParseException e)
        {
            log.warn("Error parsing JDK metadata for " + metadataArtifact, e);

            return new MavenJdkArtifactMetadata(); //No archive types is like an empty result
        }
    }

    @Override
    public JdkArchive<MavenJdkArtifact> resolveArchive(MavenJdkArtifact jdkArtifact) throws JdkRepositoryException
    {
        return resolveArchive(jdkArtifact, remoteRepositories);
    }

    private JdkArchive<MavenJdkArtifact> resolveArchive(MavenJdkArtifact jdkArtifact, List<RemoteRepository> remoteRepositoriesToUse) throws JdkRepositoryException
    {
        ArtifactRequest request = new ArtifactRequest(jdkArtifact.getArtifact(), remoteRepositoriesToUse, null);
        try
        {
            ArtifactResult result = repositorySystem.resolveArtifact(repositorySystemSession, request);
            return new JdkArchive<>(jdkArtifact, result.getArtifact().getFile().toPath());
        }
        catch (ArtifactResolutionException e)
        {
            //Could not find artifact
            throw new JdkRepositoryException("Failed to resolve JDK archive: " + e.getMessage(), e);
        }
    }

    protected Artifact mavenArtifactForJdkArtifact(JdkArtifact jdkArtifact)
    {
        return new MavenJdkArtifact(mavenArtifactGroupId, jdkArtifact).getArtifact();
    }

    @Override
    public JdkArchive<MavenJdkArtifact> saveToCache(JdkArchive<?> archive)
    throws JdkRepositoryException
    {
        //First check if we don't already have a cached version in the local repo, if we do just return that
        Artifact mavenArtifact = mavenArtifactForJdkArtifact(archive.getArtifact());
        ArtifactRequest lookupRequest = new ArtifactRequest(mavenArtifact, Collections.emptyList() /*no remotes, local only*/, null);
        try
        {
            ArtifactResult lookupResult = repositorySystem.resolveArtifact(repositorySystemSession, lookupRequest);

            //If we get here, archive was already found in the local repo
            MavenJdkArtifact jdkArtifact = new MavenJdkArtifact(lookupResult.getArtifact());
            return new JdkArchive<>(jdkArtifact, lookupResult.getArtifact().getFile().toPath());
        }
        catch (ArtifactResolutionException e)
        {
            //Not found in local repo, that's fine, move on to downloading it
        }

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
            Path tempMetadataFile = tempDirectorySupplier.get().resolve(jdkArchiveInLocalRepo.toPath().getFileName().toString() + "." + AUTOJDK_METADATA_EXTENSION);
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

            MavenJdkArtifact jdkArtifact = new MavenJdkArtifact(reresolveResult.getArtifact());

            return new JdkArchive<>(jdkArtifact, jdkArchiveInLocalRepo.toPath());
        }
        catch (IOException | InstallationException | ArtifactResolutionException | AutoJdkXmlManager.XmlWriteException e)
        {
            throw new JdkRepositoryException("Failed to install JDK archive artifact to local repo: " + e.getMessage(), e);
        }
    }

    private void generateJdkArtifactMetadataFile(MavenJdkArtifactMetadata metadata, Path file)
    throws AutoJdkXmlManager.XmlWriteException
    {
        xmlManager.writeFile(metadata, file);
    }

    private Artifact autoJdkMetadataArtifactForArchive(Artifact archiveArtifact)
    {
        return new DefaultArtifact(archiveArtifact.getGroupId(), archiveArtifact.getArtifactId(), archiveArtifact.getClassifier(),
                                   AUTOJDK_METADATA_EXTENSION, archiveArtifact.getVersion());
    }

    @Override
    public void purgeResolvedArchive(JdkArchive<MavenJdkArtifact> archive)
    throws JdkRepositoryException
    {
        Path archiveFileInLocalRepo = archive.getFile();

        //There is no local repo API for deletion, so just delete from filesystem
        //This is what maven dependency plugin's purge does as well
        try
        {
            Files.deleteIfExists(archiveFileInLocalRepo);
        }
        catch (IOException e)
        {
            throw new JdkRepositoryException("Failed to delete local repository archive " + archiveFileInLocalRepo + ": " + e.getMessage(), e);
        }

        //Also delete metadata if it exists
        Artifact metadataArtifact = autoJdkMetadataArtifactForArchive(archive.getArtifact().getArtifact());
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
    public Collection<? extends JdkArchive<MavenJdkArtifact>> purgeCache(JdkSearchRequest jdkMatchSearchRequest)
    throws JdkRepositoryException
    {
        //Search only local repos, no remotes, since we're only looking in the local repo cache
        List<RemoteRepository> noRemoteRepositories = List.of();

        Collection<? extends MavenJdkArtifact> cacheSearchResults = search(jdkMatchSearchRequest, noRemoteRepositories);
        List<JdkArchive<MavenJdkArtifact>> purgedArchives = new ArrayList<>(cacheSearchResults.size());

        for (MavenJdkArtifact cacheSearchResult : cacheSearchResults)
        {
            JdkArchive<MavenJdkArtifact> localRepositoryArchive = resolveArchive(cacheSearchResult, noRemoteRepositories);
            purgeResolvedArchive(localRepositoryArchive);
            purgedArchives.add(localRepositoryArchive);
        }

        return purgedArchives;
    }
}
