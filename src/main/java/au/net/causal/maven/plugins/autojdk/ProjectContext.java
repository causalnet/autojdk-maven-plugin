package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
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
    private final AutoJdkExtensionProperties autoJdkExtensionProperties;

    public ProjectContext(MavenProject project, AutoJdkExtensionProperties autoJdkExtensionProperties)
    {
        this.project = Objects.requireNonNull(project);
        this.autoJdkExtensionProperties = Objects.requireNonNull(autoJdkExtensionProperties);
    }

    public MavenProject getProject()
    {
        return project;
    }

    public AutoJdkExtensionProperties getAutoJdkExtensionProperties()
    {
        return autoJdkExtensionProperties;
    }

    public Xpp3Dom readPluginConfiguration(String pluginGroupId, String pluginArtifactId)
    {
        Plugin plugin = project.getPlugin(pluginGroupId + ":" + pluginArtifactId);
        if (plugin == null)
            return null;

        try
        {
            return configurationToXml(plugin.getConfiguration());
        }
        catch (IOException | XmlPullParserException e)
        {
            log.warn("Failed to parse configuration XML for " + pluginGroupId + ":" + pluginArtifactId + ": " + e.getMessage(), e);
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
}
