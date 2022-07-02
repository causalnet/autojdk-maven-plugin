package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "autojdk-injector")
public class AutoJdkInjectorExtension extends AbstractMavenLifecycleParticipant
{
    private static final Logger log = LoggerFactory.getLogger(AutoJdkInjectorExtension.class);

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException
    {
        //TODO only current project or all?
        processProject(session.getCurrentProject());
    }

    private void processProject(MavenProject project)
    {
        Plugin autoJdkPlugin = project.getPlugin("au.net.causal.maven.plugins:autojdk-maven-plugin");
        Plugin toolchainsPlugin = project.getPlugin("org.apache.maven.plugins:maven-toolchains-plugin");

        Integer requiredJavaVersion = calculateRequiredJavaVersion(project);
        if (requiredJavaVersion == null)
            return;

        //Inject toolchains
        if (toolchainsPlugin == null)
            injectToolchainsPlugin(project, requiredJavaVersion);
        if (autoJdkPlugin == null)
            injectAutoJdkPlugin(project);
    }

    private void injectToolchainsPlugin(MavenProject project, int requiredJavaVersion)
    {
        log.info("AutoJDK extension injecting toolchains plugin with required Java version '" + requiredJavaVersion + "'");

        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-toolchains-plugin");
        plugin.setVersion("3.0.0"); //TODO non-hardcode version

        PluginExecution execution = new PluginExecution();
        execution.setGoals(new ArrayList<>(Collections.singletonList("toolchain")));
        plugin.addExecution(execution);

        Xpp3Dom pluginConfiguration = new Xpp3Dom("configuration");
        Xpp3Dom toolchainsElement = new Xpp3Dom("toolchains");
        Xpp3Dom jdkElement = new Xpp3Dom("jdk");
        Xpp3Dom versionElement = new Xpp3Dom("version");
        versionElement.setValue(String.valueOf(requiredJavaVersion)); //TODO maybe better to use a range for this one
        jdkElement.addChild(versionElement);
        toolchainsElement.addChild(jdkElement);
        pluginConfiguration.addChild(toolchainsElement);

        execution.setConfiguration(xmlToConfiguration(pluginConfiguration, project));

        project.getBuild().addPlugin(plugin);
        project.getBuild().getPluginsAsMap().put(plugin.getKey(), plugin);
    }

    private void injectAutoJdkPlugin(MavenProject project)
    {
        log.info("AutoJDK extension injecting AutoJDK plugin");

        Plugin plugin = new Plugin();
        plugin.setGroupId("au.net.causal.maven.plugins");
        plugin.setArtifactId("autojdk-maven-plugin");
        plugin.setVersion("1.0-SNAPSHOT"); //TODO non-hardcode version

        PluginExecution execution = new PluginExecution();
        execution.setGoals(new ArrayList<>(Collections.singletonList("prepare")));
        plugin.addExecution(execution);

        //AutoJDK plugin needs to come before toolchains plugin so inject it at the top of the list
        project.getBuild().getPlugins().add(0, plugin);
        project.getBuild().getPluginsAsMap().put(plugin.getKey(), plugin);
    }

    /**
     * @return the calculated version of Java this project needs, or null if it couldn't be detected or isn't a Java project.
     */
    private Integer calculateRequiredJavaVersion(MavenProject project)
    {
        //Look at:
        //- compiler plugin configuration
        //- well known java version properties
        //- enforcer plugin configuration

        Plugin compilerPlugin = project.getPlugin("org.apache.maven.plugins:maven-compiler-plugin");
        if (compilerPlugin == null)
            return null;

        SortedSet<Integer> javaVersions = new TreeSet<>();
        readJavaVersionFromCompilerPluginConfiguration(compilerPlugin.getConfiguration(), "source", javaVersions);
        readJavaVersionFromCompilerPluginConfiguration(compilerPlugin.getConfiguration(), "target", javaVersions);
        readJavaVersionFromCompilerPluginConfiguration(compilerPlugin.getConfiguration(), "release", javaVersions);

        //Pick the highest needed Java version
        if (javaVersions.isEmpty())
            return null;
        else
            return javaVersions.last();
    }

    private Xpp3Dom configurationToXml(Object configuration)
    {
        if (configuration == null)
            return null;
        if (configuration instanceof Xpp3Dom)
            return (Xpp3Dom)configuration;

        //When run as an extension when the extension's classloader is isolated, the Xpp3Dom class from the project
        //might have a different classloader to the extension's version of Xpp3Dom
        String xmlString = configuration.toString();
        try (StringReader xmlReader = new StringReader(xmlString))
        {
            return Xpp3DomBuilder.build(xmlReader);
        }
        catch (IOException | XmlPullParserException e)
        {
            log.warn("Failed to parse configuration XML: " + e.getMessage(), e);
            return null;
        }
    }

    private Object xmlToConfiguration(Xpp3Dom xml, MavenProject project)
    {
        try
        {
            Class<?> xpp3DomBuilderClass = Class.forName(Xpp3DomBuilder.class.getName(), true, project.getClass().getClassLoader());
            if (Xpp3DomBuilder.class == xpp3DomBuilderClass)
                return xml;

            //Special handling for the Maven extension classloader issue with Xpp3Dom and plexus-utils
            //Use when Xpp3Dom from plexus-utils from the project is not the same as our one
            //So we have to use reflective workarounds
            String xmlString = xml.toString();

            Method buildMethod = xpp3DomBuilderClass.getMethod("build", Reader.class);

            try (StringReader reader = new StringReader(xmlString))
            {
                return buildMethod.invoke(null, reader);
            }
        }
        catch (ReflectiveOperationException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void readJavaVersionFromCompilerPluginConfiguration(Object configuration, String configurationKey, Collection<? super Integer> versions)
    {
        Xpp3Dom configurationDom = configurationToXml(configuration);
        if (configurationDom == null)
            return;

        Xpp3Dom element = configurationDom.getChild(configurationKey);
        if (element == null)
            return;

        String version = element.getValue().trim();
        if (StringUtils.isEmpty(version))
            return;

        //Adjust 1.x -> x
        if (version.startsWith("1."))
            version = version.substring("1.".length());

        //Must be a number, if not don't use
        try
        {
            versions.add(Integer.parseInt(version));
        }
        catch (NumberFormatException e)
        {
            //Ignore versions that don't parse into a number
        }
    }
}
