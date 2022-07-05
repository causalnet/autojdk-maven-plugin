package au.net.causal.maven.plugins.autojdk;

import com.google.common.base.StandardSystemProperty;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
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

        List<RemoteRepository> remoteRepositories = List.of(); //Don't try to resolve from remote Maven repos for now
        VendorService vendorService = new VendorService(DiscoClientSingleton.discoClient(), AutoJdkConfiguration.defaultAutoJdkConfiguration());
        MavenArtifactJdkArchiveRepository jdkArchiveRepository = new MavenArtifactJdkArchiveRepository(repositorySystem, repoSession, remoteRepositories, "au.net.causal.autojdk.jdk",
                                                                                                       vendorService);

        AutoJdkInstalledJdkSystem localJdkResolver = new AutoJdkInstalledJdkSystem(autoJdkInstallationDirectory);
        List<JdkArchiveRepository<?>> jdkArchiveRepositories = List.of(jdkArchiveRepository);
        AutoJdk autoJdk = new AutoJdk(localJdkResolver, localJdkResolver, jdkArchiveRepositories, StandardVersionTranslationScheme.MAJOR_AND_FULL);

        JdkSearchRequest jdkSearchRequest;
        try
        {
            //TODO lots of stuff still hardcoded, still mostly for testing at this stage
            //jdkSearchRequest =  new JdkSearchRequest(VersionRange.createFromVersionSpec("[17, 18)"), platformTools.getCurrentArchitecture(), platformTools.getCurrentOperatingSystem(), "zulu");
            jdkSearchRequest =  new JdkSearchRequest(VersionRange.createFromVersionSpec("17"), platformTools.getCurrentArchitecture(), platformTools.getCurrentOperatingSystem(), "zulu");

            LocalJdk localJdk = autoJdk.prepareJdk(jdkSearchRequest);

            getLog().info("JDK prepared: " + localJdk.getJdkDirectory());

            /*
            Collection<? extends MavenJdkArtifact> results = jdkArchiveRepository.search(jdkSearchRequest);
            if (results.isEmpty())
                throw new MojoExecutionException("No result found for JDK search");

            MavenJdkArtifact jdkToInstall = results.iterator().next();
            JdkArchive jdkArchiveToInstall = jdkArchiveRepository.resolveArchive(jdkToInstall);

            LocalJdkMetadata jdkMetadata = new LocalJdkMetadata();
            jdkMetadata.setArchitecture(jdkToInstall.getArchitecture());
            jdkMetadata.setOperatingSystem(jdkToInstall.getOperatingSystem());
            jdkMetadata.setVendor(jdkToInstall.getVendor());
            jdkMetadata.setVersion(jdkToInstall.getVersion());

            localJdkResolver.installJdkFromArchive(jdkArchiveToInstall.getFile().toPath(), jdkMetadata);
             */
        }
        catch (JdkNotFoundException e)
        {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        catch (InvalidVersionSpecificationException e)
        {
            throw new MojoExecutionException("Invalid version to search for: " + e.getMessage(), e);
        }
        catch (LocalJdkResolutionException e)
        {
            throw new MojoExecutionException("Failed to search JDK repositories: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Failed to install new JDK: " + e.getMessage(), e);
        }
    }
}
