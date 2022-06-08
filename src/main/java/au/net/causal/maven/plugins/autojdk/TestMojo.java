package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;

@Mojo(name="test", defaultPhase = LifecyclePhase.INITIALIZE)
public class TestMojo extends AbstractMojo
{
    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private ToolchainManager toolchainManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        //Just simulating what a consumer of a toolchain would do
        Toolchain toolchain = toolchainManager.getToolchainFromBuildContext("jdk", session);
        getLog().info("Toolchain: " + toolchain);
    }
}
