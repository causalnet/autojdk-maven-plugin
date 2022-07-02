package au.net.causal.maven.plugins.autojdk;

import com.google.common.base.StandardSystemProperty;
import io.foojay.api.discoclient.DiscoClient;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.ToolchainManager;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mojo(name="prepare", defaultPhase = LifecyclePhase.VALIDATE)
public class PrepareMojo extends AbstractMojo
{
    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private ToolchainManager toolchainManager;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true)
    protected List<RemoteRepository> remoteRepositories;

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/autojdk-download", required = true)
    protected File downloadDirectory;

    private boolean downloadDirectorySetUp;

    @Parameter(property = "autojdk.jdk.version")
    private String requiredJdkVersion;

    @Parameter(property = "autojdk.jdk.vendor")
    private String requiredJdkVendor;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        Map<String, String> toolchainJdkRequirements = readToolchainsJdkRequirements();
        if (requiredJdkVersion == null)
            requiredJdkVersion = toolchainJdkRequirements.get("version");
        if (requiredJdkVendor == null)
            requiredJdkVendor = toolchainJdkRequirements.get("vendor");

        if (requiredJdkVersion == null)
            throw new MojoExecutionException("No required JDK version configured.  Either configure directly on the plugin or add toolchains plugin with appropriate configuration.");
        if (requiredJdkVendor == null)
            requiredJdkVendor = "zulu"; //TODO find a better way to handle this, ok for now for testing

        Path userHome = Path.of(StandardSystemProperty.USER_HOME.value());
        Path m2Home = userHome.resolve(".m2");
        Path autojdkHome = m2Home.resolve("autojdk");
        Path autoJdkInstallationDirectory = autojdkHome.resolve("jdks");

        PlatformTools platformTools = new PlatformTools();

        DiscoClient discoClient = DiscoClientSingleton.discoClient();
        FileDownloader fileDownloader = new SimpleFileDownloader(this::tempDownloadDirectory);

        AutoJdkInstalledJdkSystem localJdkResolver = new AutoJdkInstalledJdkSystem(autoJdkInstallationDirectory);

        VendorConfiguration vendorConfiguration = new VendorConfiguration(discoClient);
        List<JdkArchiveRepository<?>> jdkArchiveRepositories = List.of(
                new MavenArtifactJdkArchiveRepository(repositorySystem, repoSession, remoteRepositories, "au.net.causal.autojdk.jdk", vendorConfiguration),
                new FoojayJdkRepository(discoClient, repositorySystem, repoSession, fileDownloader, "au.net.causal.autojdk.jdk")
        );
        AutoJdk autoJdk = new AutoJdk(localJdkResolver, localJdkResolver, jdkArchiveRepositories, StandardVersionTranslationScheme.MAJOR_AND_FULL);




        try
        {
            //Ensure we have an available JDK to work with
            JdkSearchRequest jdkSearchRequest =  new JdkSearchRequest(VersionRange.createFromVersionSpec(requiredJdkVersion),
                                                                      platformTools.getCurrentArchitecture(),
                                                                      platformTools.getCurrentOperatingSystem(),
                                                                      requiredJdkVendor);
            LocalJdk localJdk = autoJdk.prepareJdk(jdkSearchRequest);

            getLog().info("Prepared local JDK: " + localJdk.getJdkDirectory());

            List<? extends ToolchainModel> jdkToolchains = autoJdk.generateToolchainsFromLocalJdks();
            getLog().info(jdkToolchains.size() + " toolchains made from local JDKs");
            for (ToolchainModel jdkToolchain : jdkToolchains)
            {
                getLog().info("Registered JDK toolchain: " + jdkToolchain.getProvides());
            }
            Map<String, List<ToolchainModel>> toolchains = new HashMap<>();
            toolchains.put("jdk", new ArrayList<>(jdkToolchains));
            session.getRequest().setToolchains(toolchains);
        }
        catch (InvalidVersionSpecificationException e)
        {
            throw new MojoExecutionException("Invalid JDK version/range: " + requiredJdkVersion, e);
        }
        catch (JdkNotFoundException e)
        {
            throw new MojoExecutionException("Could not find or install JDK '" + requiredJdkVersion + "'", e);
        }
        catch (LocalJdkResolutionException e)
        {
            throw new MojoExecutionException("Failed to generate toolchains from local JDKs: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("I/O error preparing JDK: " + e.getMessage(), e);
        }
    }

    private synchronized Path tempDownloadDirectory()
    throws IOException
    {
        if (!downloadDirectorySetUp)
        {
            //Don't use default download directory if there's no project
            if (project == null)
                downloadDirectory = new File(StandardSystemProperty.JAVA_IO_TMPDIR.value());

            //Ensure all directories are created
            Files.createDirectories(downloadDirectory.toPath());

            downloadDirectorySetUp = true;
        }

        return downloadDirectory.toPath();
    }

    private Map<String, String> readToolchainsJdkRequirements()
    {
        Map<String, String> requirements = new LinkedHashMap<>();

        Plugin toolchainsPlugin = project.getBuild().getPluginsAsMap().get("org.apache.maven.plugins:maven-toolchains-plugin");

        //Should only have one execution, but if there's more than one just combine everything
        //If there's multiple executions with different requirements I'd consider the build broken
        //so if we break there as well it's not a huge deal
        if (toolchainsPlugin != null && toolchainsPlugin.getExecutions() != null)
        {
            for (PluginExecution toolchainsPluginExecution : toolchainsPlugin.getExecutions())
            {
                Object configuration = toolchainsPluginExecution.getConfiguration();
                if (configuration instanceof Xpp3Dom)
                {
                    Xpp3Dom xppConfig = (Xpp3Dom) configuration;

                    Xpp3Dom toolchainsConfig = xppConfig.getChild("toolchains");
                    if (toolchainsConfig != null)
                    {
                        Xpp3Dom jdkConfig = toolchainsConfig.getChild("jdk");
                        if (jdkConfig != null)
                        {
                            for (Xpp3Dom requirementConfig : jdkConfig.getChildren())
                            {
                                String requirementName = requirementConfig.getName();
                                String requirementValue = requirementConfig.getValue();
                                if (StringUtils.isNotEmpty(requirementName) && StringUtils.isNotEmpty(requirementValue))
                                    requirements.put(requirementName, requirementValue);
                            }
                        }
                    }
                }
            }
        }

        return requirements;
    }
}
