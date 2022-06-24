package au.net.causal.maven.plugins.autojdk;

import com.google.common.base.StandardSystemProperty;
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

@Mojo(name="installjdk", defaultPhase = LifecyclePhase.VALIDATE)
public class InstallMojo extends AbstractMojo
{
    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    private final PlatformTools platformTools = new PlatformTools();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        Path userHome = Path.of(StandardSystemProperty.USER_HOME.value());
        Path m2Home = userHome.resolve(".m2");
        Path autojdkHome = m2Home.resolve("autojdk");
        Path autoJdkInstallationDirectory = autojdkHome.resolve("jdks");

        AutoJdkInstalledJdkSystem localJdkResolver = new AutoJdkInstalledJdkSystem(autoJdkInstallationDirectory);

        //TODO this local repo/download stuff needs to be integrated into AutoJdk.prepareJdk()
        List<RemoteRepository> remoteRepositories = List.of(); //Don't try to resolve from remotes for now
        VendorConfiguration vendorConfiguration = new VendorConfiguration(DiscoClientSingleton.discoClient());
        MavenArtifactJdkArchiveRepository jdkArchiveRepository = new MavenArtifactJdkArchiveRepository(repositorySystem, repoSession, remoteRepositories, "au.net.causal.autojdk.jdk", vendorConfiguration);

        JdkSearchRequest jdkSearchRequest;
        try
        {
            //TODO lots of stuff still hardcoded, still mostly for testing at this stage
            jdkSearchRequest =  new JdkSearchRequest(VersionRange.createFromVersionSpec("17"), platformTools.getCurrentArchitecture(), platformTools.getCurrentOperatingSystem(), "zulu");

            Collection<? extends MavenJdkArtifact> results = jdkArchiveRepository.search(jdkSearchRequest);
            if (results.isEmpty())
                throw new MojoExecutionException("No result found for JDK search");

            MavenJdkArtifact jdkToInstall = results.iterator().next();
            JdkArchive jdkArchiveToInstall = jdkArchiveRepository.resolveArchive(jdkToInstall);

            AutoJdkInstalledJdkSystem.LocalJdkMetadata jdkMetadata = new AutoJdkInstalledJdkSystem.LocalJdkMetadata();
            jdkMetadata.setArchitecture(jdkToInstall.getArchitecture());
            jdkMetadata.setOperatingSystem(jdkToInstall.getOperatingSystem());
            jdkMetadata.setVendor(jdkToInstall.getVendor());
            jdkMetadata.setVersion(jdkToInstall.getVersion());

            localJdkResolver.installJdkFromArchive(jdkArchiveToInstall.getFile().toPath(), jdkMetadata);
        }
        catch (InvalidVersionSpecificationException e)
        {
            throw new MojoExecutionException("Invalid version to search for: " + e.getMessage(), e);
        }
        catch (JdkRepositoryException e)
        {
            throw new MojoExecutionException("Failed to search JDK repository: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Failed to install new JDK: " + e.getMessage(), e);
        }
    }
}
