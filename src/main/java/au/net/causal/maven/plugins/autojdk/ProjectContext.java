package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;

public class ProjectContext
{
    private static final Logger log = LoggerFactory.getLogger(ProjectContext.class);

    private final MavenProject project;
    private final MavenSession session;
    private final AutoJdkExtensionProperties autoJdkExtensionProperties;

    public ProjectContext(MavenProject project, MavenSession session,
                          AutoJdkExtensionProperties autoJdkExtensionProperties)
    {
        this.project = Objects.requireNonNull(project);
        this.session = Objects.requireNonNull(session);
        this.autoJdkExtensionProperties = Objects.requireNonNull(autoJdkExtensionProperties);
    }

    public MavenProject getProject()
    {
        return project;
    }

    public MavenSession getSession()
    {
        return session;
    }

    public AutoJdkExtensionProperties getAutoJdkExtensionProperties()
    {
        return autoJdkExtensionProperties;
    }

    public Xpp3Dom readPluginConfiguration(Plugin plugin)
    {
        try
        {
            return configurationToXml(plugin.getConfiguration());
        }
        catch (IOException | XmlPullParserException e)
        {
            log.warn("Failed to parse configuration XML for " + plugin.getGroupId() + ":" + plugin.getArtifactId() + ": " + e.getMessage(), e);
            return null;
        }
    }

    public Xpp3Dom readPluginConfiguration(String pluginGroupId, String pluginArtifactId)
    {
        Plugin plugin = project.getPlugin(pluginGroupId + ":" + pluginArtifactId);
        if (plugin == null)
            return null;

        return readPluginConfiguration(plugin);
    }

    public Xpp3Dom readExecutionConfiguration(String pluginGroupId, String pluginArtifactId, String executionName)
    {
        Plugin plugin = project.getPlugin(pluginGroupId + ":" + pluginArtifactId);
        if (plugin == null)
            return null;

        PluginExecution execution = plugin.getExecutionsAsMap().get(executionName);
        if (execution == null)
            return null;
        try
        {
            return configurationToXml(execution.getConfiguration());
        }
        catch (IOException | XmlPullParserException e)
        {
            log.warn("Failed to parse configuration XML for execution " + pluginGroupId + ":" + pluginArtifactId + ":" + executionName + ": " + e.getMessage(), e);
            return null;
        }
    }

    private Xpp3Dom configurationToXml(Object configuration)
    throws IOException, XmlPullParserException
    {
        if (configuration == null)
            return null;
        if (configuration instanceof Xpp3Dom)
            return (Xpp3Dom)configuration;

        //When run as an extension when the extension's classloader is isolated, the Xpp3Dom class from the project
        //might have a different classloader to the extension's version of Xpp3Dom
        //So just serialize to string and then deserialize back to Xpp3Dom from our classloader
        //TODO these classloader hacks are hacky - and won't work with any plugin dependencies - need to find a better way than this
        String xmlString = configuration.toString();
        try (StringReader xmlReader = new StringReader(xmlString))
        {
            return Xpp3DomBuilder.build(xmlReader);
        }
    }

    public String evaluateProjectProperty(String propertyName)
    throws MavenExecutionException
    {
        PluginParameterExpressionEvaluator evaluator = new PluginParameterExpressionEvaluator(session, new MojoExecution(null));
        try
        {
            String propertyValue = (String)evaluator.evaluate("${" + propertyName + "}", String.class);
            if (propertyValue != null && propertyValue.trim().isEmpty())
                return null;

            return propertyValue;
        }
        catch (ExpressionEvaluationException e)
        {
            throw new MavenExecutionException("Error reading property '" + propertyName + "': " + e, e);
        }
    }
}
