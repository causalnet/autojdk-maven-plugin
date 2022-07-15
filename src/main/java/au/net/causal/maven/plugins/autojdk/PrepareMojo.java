package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.toolchain.model.ToolchainModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mojo(name="prepare", defaultPhase = LifecyclePhase.VALIDATE)
public class PrepareMojo extends AbstractAutoJdkMojo
{
    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Override
    protected void executeImpl() throws MojoExecutionException, MojoFailureException
    {
        try
        {
            //Ensure we have an available JDK to work with
            JdkSearchRequest jdkSearchRequest =  new JdkSearchRequest(getRequiredJdkVersionRange(),
                                                                      platformTools.getCurrentArchitecture(),
                                                                      platformTools.getCurrentOperatingSystem(),
                                                                      getRequiredJdkVendor());
            LocalJdk localJdk = autoJdk().prepareJdk(jdkSearchRequest);

            getLog().info("Prepared local JDK: " + localJdk.getJdkDirectory());

            List<? extends ToolchainModel> jdkToolchains = autoJdk().generateToolchainsFromLocalJdks();
            getLog().info(jdkToolchains.size() + " toolchains made from local JDKs");
            for (ToolchainModel jdkToolchain : jdkToolchains)
            {
                getLog().info("Registered JDK toolchain: " + jdkToolchain.getProvides());
            }
            Map<String, List<ToolchainModel>> toolchains = new HashMap<>();
            toolchains.put("jdk", new ArrayList<>(jdkToolchains));
            session.getRequest().setToolchains(toolchains);
        }
        catch (JdkNotFoundException e)
        {
            throw new MojoExecutionException("Could not find or install JDK '" + getRequiredJdkVersionRange() + "'", e);
        }
        catch (LocalJdkResolutionException e)
        {
            throw new MojoExecutionException("Failed to generate toolchains from local JDKs: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("I/O error preparing JDK: " + e, e);
        }
    }
}
