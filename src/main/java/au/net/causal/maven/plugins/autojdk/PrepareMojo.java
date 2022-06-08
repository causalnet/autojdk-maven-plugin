package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.toolchain.ToolchainManager;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mojo(name="prepare", defaultPhase = LifecyclePhase.VALIDATE)
public class PrepareMojo extends AbstractMojo
{
    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private ToolchainManager toolchainManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        getLog().info("This is my plugin " + session.getRequest().getToolchains());

        if (session.getRequest().getToolchains().isEmpty())
        {
            getLog().info("Adding custom toolchain because there are none");

            //Let's see if we can inject a toolchain from a plugin
            ToolchainModel tcm = new ToolchainModel();
            tcm.setType("jdk");
            tcm.addProvide("version", "18");
            tcm.addProvide("vendor", "openjdk");
            Xpp3Dom conf = new Xpp3Dom("configuration");
            Xpp3Dom child = new Xpp3Dom("jdkHome");
            child.setValue("C:\\Program Files\\OpenJDK\\jdk-18.0.1.1");
            conf.addChild(child);
            tcm.setConfiguration(conf);

            Map<String, List<ToolchainModel>> toolchains = new HashMap<>();
            toolchains.put("jdk", new ArrayList<>(List.of(tcm)));
            session.getRequest().setToolchains(toolchains);
        }
        else
            System.out.println(session.getRequest().getToolchains().get("jdk").get(0).getConfiguration().getClass());
        //TODO

    }
}
