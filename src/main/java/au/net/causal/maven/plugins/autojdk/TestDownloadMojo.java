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

    @Parameter(property = "autojdk.version")
    private String jdkVersion;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        //Calculate version if not specified
        if (jdkVersion == null)
            jdkVersion = "17";
        //TODO actually calculate from project metadata or whatever

        JdkSearchRequest searchRequest;
        try
        {
            searchRequest = new JdkSearchRequest(VersionRange.createFromVersionSpec(jdkVersion), Architecture.X64, OperatingSystem.WINDOWS, "zulu");
        }
        catch (InvalidVersionSpecificationException e)
        {
            throw new MojoExecutionException("Invalid version: " + jdkVersion, e);
        }

        //runSearch(searchRequest, foojayJdkRepository());
        runSearch(searchRequest, mavenJdkRepository());
    }

    private FoojayJdkRepository foojayJdkRepository()
    {
        FileUtils.mkdir(project.getBuild().getDirectory());
        Path buildDirectory = Path.of(project.getBuild().getDirectory());
        FileDownloader downloader = new SimpleFileDownloader(buildDirectory);
        return new FoojayJdkRepository(DiscoClientSingleton.discoClient(), repositorySystem, repoSession, downloader, "au.net.causal.autojdk.jdk");
    }

    private MavenArtifactJdkArchiveRepository mavenJdkRepository()
    {
        return new MavenArtifactJdkArchiveRepository(repositorySystem, repoSession, List.of(),
                                                     "au.net.causal.autojdk.jdk",
                                                     new VendorConfiguration(DiscoClientSingleton.discoClient()));
    }

    private <A extends JdkArtifact> void runSearch(JdkSearchRequest searchRequest, JdkArchiveRepository<A> jdkRepo)
    throws MojoExecutionException
    {
        try
        {
            Collection<? extends A> results = jdkRepo.search(searchRequest);

            getLog().info("Result count: " + results.size());

            for (A result : results)
            {
                getLog().info(result.getVendor() + ":" + result.getVersion() + ":" +
                              result.getArchiveType().getFileExtension() +
                              " (os=" + result.getOperatingSystem().getApiString() +
                              ", arch=" + result.getArchitecture().getApiString() + ")");
            }

            JdkArchive resolved = jdkRepo.resolveArchive(results.iterator().next());
            getLog().info("Resolved: " + resolved.getFile());
        }
        catch (JdkRepositoryException e)
        {
            throw new MojoExecutionException("Error searching: " + e, e);
        }
    }
}
