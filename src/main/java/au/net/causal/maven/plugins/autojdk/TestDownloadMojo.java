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
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.nio.file.Path;
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

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        //runFromFoojay();
        runFromLocalRepo();
    }

    private void runFromFoojay()
    throws MojoExecutionException
    {
        FileUtils.mkdir(project.getBuild().getDirectory());
        Path buildDirectory = Path.of(project.getBuild().getDirectory());
        FileDownloader downloader = new SimpleFileDownloader(buildDirectory);
        FoojayJdkRepository jdkRepo = new FoojayJdkRepository(DiscoClientSingleton.discoClient(), repositorySystem, repoSession, downloader, "au.net.causal.autojdk.jdk");

        try
        {
            JdkSearchRequest request = new JdkSearchRequest(VersionRange.createFromVersionSpec("17"), Architecture.X64, OperatingSystem.WINDOWS, "zulu");
            Collection<? extends FoojayArtifact> results = jdkRepo.search(request);

            System.out.println("Result count: " + results.size());

            for (FoojayArtifact result : results)
            {
                System.out.println(result.getFoojayPkg());
            }

            JdkArchive resolved = jdkRepo.resolveArchive(results.iterator().next());
            System.out.println("Resolved: " + resolved.getFile());
        }
        catch (InvalidVersionSpecificationException | JdkRepositoryException e)
        {
            throw new MojoExecutionException("Error searching: " + e, e);
        }
    }

    private void runFromLocalRepo()
    throws MojoExecutionException
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

            if (!results.isEmpty())
            {
                JdkArchive resolved = jdkRepo.resolveArchive(results.iterator().next());
                System.out.println("Resolved: " + resolved.getFile());
            }
        }
        catch (InvalidVersionSpecificationException | JdkRepositoryException e)
        {
            throw new MojoExecutionException("Error searching: " + e, e);
        }
    }
}
