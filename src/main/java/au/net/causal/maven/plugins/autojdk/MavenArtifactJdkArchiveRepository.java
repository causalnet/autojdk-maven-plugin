package au.net.causal.maven.plugins.autojdk;

import jakarta.xml.bind.JAXB;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class MavenArtifactJdkArchiveRepository implements JdkArchiveRepository<MavenJdkArtifact>
{
    private static final Logger log = LoggerFactory.getLogger(MavenArtifactJdkArchiveRepository.class);

    static final String AUTOJDK_METADATA_EXTENSION = "autojdk-metadata.xml";

    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;
    private final List<RemoteRepository> remoteRepositories;
    private final String mavenArtifactGroupId;
    private final VendorConfiguration vendorConfiguration;

    public MavenArtifactJdkArchiveRepository(RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession,
                                             List<RemoteRepository> remoteRepositories, String mavenArtifactGroupId,
                                             VendorConfiguration vendorConfiguration)
    {
        this.repositorySystem = Objects.requireNonNull(repositorySystem);
        this.repositorySystemSession = Objects.requireNonNull(repositorySystemSession);
        this.remoteRepositories = List.copyOf(remoteRepositories);
        this.mavenArtifactGroupId = Objects.requireNonNull(mavenArtifactGroupId);
        this.vendorConfiguration = Objects.requireNonNull(vendorConfiguration);
    }

    @Override
    public Collection<? extends MavenJdkArtifact> search(JdkSearchRequest searchRequest)
    throws JdkRepositoryException
    {
        List<String> artifactIdsToSearch;

        //Do we know the artifact ID?
        if (searchRequest.getVendor() == null)
        {
            //If not, need to search all known vendors
            artifactIdsToSearch = vendorConfiguration.getAllVendors()
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
            Artifact searchArtifact = new DefaultArtifact(mavenArtifactGroupId, artifactIdToSearch, null, searchRequest.getVersionRange().toString());
            VersionRangeRequest versionSearchRequest = new VersionRangeRequest(searchArtifact, remoteRepositories, null);

            try
            {
                VersionRangeResult versionSearchResult = repositorySystem.resolveVersionRange(repositorySystemSession, versionSearchRequest);
                for (Exception warningException : versionSearchResult.getExceptions())
                {
                    log.debug("Problem performing version search: " + warningException.getMessage(), warningException);
                }
                List<Version> foundVersions = versionSearchResult.getVersions();

                for (Version foundVersion : foundVersions)
                {
                    String classifier = MavenJdkArtifact.makeClassifier(searchRequest.getOperatingSystem(), searchRequest.getArchitecture());
                    MavenJdkArtifactMetadata mavenMetadata = readMavenJdkArtifactMetadata(artifactIdToSearch, classifier, foundVersion);

                    for (ArchiveType curArchiveType : mavenMetadata.getArchiveTypes())
                    {
                        MavenJdkArtifact curMavenJdkArtifact = new MavenJdkArtifact(mavenArtifactGroupId, artifactIdToSearch, foundVersion.toString(), searchRequest.getArchitecture(), searchRequest.getOperatingSystem(), curArchiveType);
                        matchingArtifacts.add(curMavenJdkArtifact);
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

    private MavenJdkArtifactMetadata readMavenJdkArtifactMetadata(String artifactId, String classifier, Version version)
    {

        Artifact metadataArtifact = new DefaultArtifact(mavenArtifactGroupId, artifactId, classifier, AUTOJDK_METADATA_EXTENSION, version.toString());
        ArtifactRequest metadataRequest = new ArtifactRequest(metadataArtifact, remoteRepositories, null);
        try
        {
            ArtifactResult metadataResult = repositorySystem.resolveArtifact(repositorySystemSession, metadataRequest);

            //TODO better management of JAXB context
            return JAXB.unmarshal(metadataResult.getArtifact().getFile(), MavenJdkArtifactMetadata.class);
        }
        catch (ArtifactResolutionException e)
        {
            //Could not find metadata, so just ignore this search result
            //TODO probably should turn this down because it's reasonable that Maven found artifacts for different OS/architecture
            log.warn("Could not find JDK metadata for " + mavenArtifactGroupId + ":" + artifactId + ":" + classifier + ":" + version, e);

            return new MavenJdkArtifactMetadata();
        }
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
