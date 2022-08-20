package au.net.causal.maven.plugins.autojdk;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MavenArtifactJdkArchiveRepository extends LocalMavenRepositoryCachedJdkArchiveRepository<MavenJdkArtifact>
{
    private static final Logger log = LoggerFactory.getLogger(MavenArtifactJdkArchiveRepository.class);

    private final List<RemoteRepository> remoteRepositories;
    private final VendorService vendorService;
    private final AutoJdkXmlManager xmlManager;

    public MavenArtifactJdkArchiveRepository(RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession,
                                             List<RemoteRepository> remoteRepositories, String mavenArtifactGroupId,
                                             VendorService vendorService,
                                             AutoJdkXmlManager xmlManager)
    {
        super(repositorySystem, repositorySystemSession, mavenArtifactGroupId);
        this.remoteRepositories = List.copyOf(remoteRepositories);
        this.vendorService = Objects.requireNonNull(vendorService);
        this.xmlManager = Objects.requireNonNull(xmlManager);
    }

    @Override
    public Collection<? extends MavenJdkArtifact> search(JdkSearchRequest searchRequest)
    throws JdkRepositoryException
    {
        //Cannot search without known architecture / os, so bail out if this happens
        if (searchRequest.getOperatingSystem() == null || searchRequest.getArchitecture() == null)
            return Collections.emptyList(); //TODO a kludge until we can figure out something better

        List<String> artifactIdsToSearch;

        //Do we know the artifact ID?
        if (searchRequest.getVendor() == null)
        {
            //If not, need to search all known vendors
            artifactIdsToSearch = vendorService.getAllVendors()
                                               .stream()
                                               .map(MavenJdkArtifact::vendorToArtifactId)
                                               .collect(Collectors.toUnmodifiableList());
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
            Artifact searchArtifact = new DefaultArtifact(getMavenArtifactGroupId(), artifactIdToSearch, null, searchRequest.getVersionRange().toString());
            VersionRangeRequest versionSearchRequest = new VersionRangeRequest(searchArtifact, remoteRepositories, null);

            try
            {
                VersionRangeResult versionSearchResult = getRepositorySystem().resolveVersionRange(getRepositorySystemSession(), versionSearchRequest);
                for (Exception warningException : versionSearchResult.getExceptions())
                {
                    log.debug("Problem performing version search: " + warningException.getMessage(), warningException);
                }
                List<Version> foundVersions = versionSearchResult.getVersions();

                for (Version foundVersion : foundVersions)
                {
                    String classifier = MavenJdkArtifact.makeClassifier(searchRequest.getOperatingSystem(), searchRequest.getArchitecture());
                    Artifact searchArtifactWithClassifier = new DefaultArtifact(searchArtifact.getGroupId(), searchArtifact.getArtifactId(), classifier, searchArtifact.getExtension(), foundVersion.toString());
                    MavenJdkArtifactMetadata mavenMetadata = readMavenJdkArtifactMetadata(searchArtifactWithClassifier);

                    //Only include result release type (EA/GA) matches search request (if specified)
                    if (searchRequest.getReleaseType() == null || searchRequest.getReleaseType().equals(mavenMetadata.getReleaseType()))
                    {
                        for (ArchiveType curArchiveType : mavenMetadata.getArchiveTypes())
                        {
                            MavenJdkArtifact curMavenJdkArtifact = new MavenJdkArtifact(getMavenArtifactGroupId(), artifactIdToSearch, foundVersion.toString(),
                                                                                        searchRequest.getArchitecture(), searchRequest.getOperatingSystem(), curArchiveType);
                            matchingArtifacts.add(curMavenJdkArtifact);
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

    private MavenJdkArtifactMetadata readMavenJdkArtifactMetadata(Artifact archiveArtifact)
    {
        Artifact metadataArtifact = autoJdkMetadataArtifactForArchive(archiveArtifact);
        ArtifactRequest metadataRequest = new ArtifactRequest(metadataArtifact, remoteRepositories, null);
        try
        {
            ArtifactResult metadataResult = getRepositorySystem().resolveArtifact(getRepositorySystemSession(), metadataRequest);
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
    public JdkArchive resolveArchive(MavenJdkArtifact jdkArtifact) throws JdkRepositoryException
    {
        ArtifactRequest request = new ArtifactRequest(jdkArtifact.getArtifact(), remoteRepositories, null);
        try
        {
            ArtifactResult result = getRepositorySystem().resolveArtifact(getRepositorySystemSession(), request);
            return new JdkArchive(jdkArtifact, result.getArtifact().getFile());
        }
        catch (ArtifactResolutionException e)
        {
            //Could not find artifact
            throw new JdkRepositoryException("Failed to resolve JDK archive: " + e.getMessage(), e);
        }
    }
}
