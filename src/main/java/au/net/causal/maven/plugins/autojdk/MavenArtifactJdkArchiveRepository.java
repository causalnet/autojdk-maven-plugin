package au.net.causal.maven.plugins.autojdk;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.version.Version;

import java.util.*;
import java.util.stream.Collectors;

public class MavenArtifactJdkArchiveRepository implements JdkArchiveRepository<MavenJdkArtifact>
{
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

        //TODO we might have to be a bit tricky with version searching here - we need "11" to find "11.0.2", etc.

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
            Artifact searchArtifact = new DefaultArtifact(mavenArtifactGroupId, artifactIdToSearch, null, "[0,)");
            //Artifact searchArtifact = new DefaultArtifact("org.apache.maven", "maven-archiver", null, null);
            VersionRangeRequest versionSearchRequest = new VersionRangeRequest(searchArtifact, remoteRepositories, null);

            try
            {
                VersionRangeResult versionSearchResult = repositorySystem.resolveVersionRange(repositorySystemSession, versionSearchRequest);
                List<Version> foundVersions = versionSearchResult.getVersions();

                for (Version foundVersion : foundVersions)
                {
                    for (ArchiveType curArchiveType : ArchiveType.values())
                    {
                        MavenJdkArtifact curMavenJdkArtifact = new MavenJdkArtifact(mavenArtifactGroupId, artifactIdToSearch, foundVersion.toString(), searchRequest.getArchitecture(), searchRequest.getOperatingSystem(), curArchiveType);

                        //For each found version, check if the artifact/extension/classifier exists
                        VersionRequest versionRequest = new VersionRequest(curMavenJdkArtifact.getArtifact(), remoteRepositories, null);
                        VersionResult versionResult = repositorySystem.resolveVersion(repositorySystemSession, versionRequest);

                        //TODO what happens if a version of this one is not found?

                        //If we get here we found it?
                        matchingArtifacts.add(curMavenJdkArtifact);
                    }
                }
            }
            catch (VersionRangeResolutionException | VersionResolutionException e)
            {
                throw new JdkRepositoryException("Error performing version search in Maven repository: " + e.getMessage(), e);
            }
        }

        return matchingArtifacts;
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
