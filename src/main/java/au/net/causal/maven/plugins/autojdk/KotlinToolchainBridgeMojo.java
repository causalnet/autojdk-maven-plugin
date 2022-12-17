package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configures Kotlin plugin if it is in the current project with a JDK home from toolchains.
 * If using with AutoJDK, ensure the 'prepare' goal is run before this one so that toolchains are set up first.
 */
@Mojo(name="kotlin-toolchain-bridge", defaultPhase = LifecyclePhase.INITIALIZE)
public class KotlinToolchainBridgeMojo extends AbstractMojo
{
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession mavenSession;

    @Component
    private ToolchainManager toolchainManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        //Look up Java home from toolchain
        Toolchain toolchain = getToolchain();
        if (toolchain != null)
        {
            Path jdkHome = findJdkHome(toolchain);

            //Simply set the property the Kotlin plugin uses by default
            project.getProperties().setProperty("kotlin.compiler.jdkHome", jdkHome.toAbsolutePath().toString());
            getLog().info("Configured Kotlin plugin JDK home to: " + jdkHome.toAbsolutePath());
        }
    }

    protected Path findJdkHome(Toolchain jdkToolchain)
    {
        String javaExe = jdkToolchain.findTool("java");
        if (javaExe != null)
        {
            Path javaExePath = Path.of(javaExe);
            Path binDirectory = javaExePath.getParent();
            Path javaHome = binDirectory.getParent();
            if (Files.isDirectory(javaHome))
                return javaHome;
        }

        return null;
    }

    protected Toolchain getToolchain()
    {
        return toolchainManager.getToolchainFromBuildContext("jdk", mavenSession);
    }
}
