package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.util.Collection;
import java.util.List;

@Mojo(name="testdownload", defaultPhase = LifecyclePhase.INITIALIZE)
public class TestDownloadMojo extends AbstractMojo
{
    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true)
    protected List<RemoteRepository> remoteRepos;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        MavenArtifactJdkArchiveRepository jdkRepo = new MavenArtifactJdkArchiveRepository(repositorySystem, repoSession, List.of(), "au.net.causal.autojdk.jdk", new VendorConfiguration(DiscoClientSingleton.discoClient()));

        try
        {
            JdkSearchRequest request = new JdkSearchRequest(VersionRange.createFromVersionSpec("17"), Architecture.X64, OperatingSystem.WINDOWS, "zulu");
            Collection<? extends MavenJdkArtifact> results = jdkRepo.search(request);

            System.out.println("Result count: " + results.size());

            for (MavenJdkArtifact result : results)
            {
                System.out.println(result.getArtifact());
            }
        }
        catch (InvalidVersionSpecificationException | JdkRepositoryException e)
        {
            throw new MojoExecutionException("Error searching: " + e, e);
        }
    }
}
