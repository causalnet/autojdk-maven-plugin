package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "autojdk-injector")
public class AutoJdkInjectorExtension extends AbstractMavenLifecycleParticipant
{
    private static final Logger log = LoggerFactory.getLogger(AutoJdkInjectorExtension.class);

    private static final String AUTOJDK_PLUGIN_GROUP_ID = "au.net.causal.maven.plugins";
    private static final String AUTOJDK_PLUGIN_ARTIFACT_ID = "autojdk-maven-plugin";

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException
    {
        AutoJdkExtensionProperties extensionProperties = AutoJdkExtensionProperties.fromMavenSession(session);

        for (MavenProject project : session.getProjects())
        {
            processProject(project, extensionProperties);
        }
    }

    private void processProject(MavenProject project, AutoJdkExtensionProperties extensionProperties)
    throws MavenExecutionException
    {
        Plugin autoJdkPlugin = project.getPlugin(AUTOJDK_PLUGIN_GROUP_ID + ":" + AUTOJDK_PLUGIN_ARTIFACT_ID);
        Plugin toolchainsPlugin = project.getPlugin("org.apache.maven.plugins:maven-toolchains-plugin");

        String requiredJavaVersion = calculateRequiredJavaVersion(project, extensionProperties);
        if (requiredJavaVersion == null)
            return;

        //TODO handle what happens if the requiredJavaVersion is too low
        //   e.g. version 1.4 which no repository would reasonably have
        //   in this case might want to just pick the lowest available version or something

        //Inject toolchains
        if (toolchainsPlugin == null)
            injectToolchainsPlugin(project, requiredJavaVersion, extensionProperties);
        if (autoJdkPlugin == null)
            injectAutoJdkPlugin(project);
    }

    private void injectToolchainsPlugin(MavenProject project, String requiredJavaVersion, AutoJdkExtensionProperties extensionProperties)
    {
        log.info("AutoJDK extension injecting toolchains plugin with required Java version '" + requiredJavaVersion + "' into project " + project.getArtifactId());

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
        versionElement.setValue(requiredJavaVersion);
        jdkElement.addChild(versionElement);
        if (extensionProperties.getJdkVendor() != null)
        {
            Xpp3Dom vendorElement = new Xpp3Dom("vendor");
            vendorElement.setValue(extensionProperties.getJdkVendor());
            jdkElement.addChild(vendorElement);
        }
        toolchainsElement.addChild(jdkElement);
        pluginConfiguration.addChild(toolchainsElement);

        execution.setConfiguration(xmlToConfiguration(pluginConfiguration, project));

        project.getBuild().addPlugin(plugin);
        project.getBuild().getPluginsAsMap().put(plugin.getKey(), plugin);
    }

    /**
     * Converts a required major version into a Maven version range.  e.g. 17 -> [17, 18)
     */
    private String majorVersionToRange(int majorVersion)
    {
        return "[" + majorVersion + "," + (majorVersion + 1) + ")";
    }

    private void injectAutoJdkPlugin(MavenProject project)
    throws MavenExecutionException
    {
        String autoJdkPluginVersion = lookupAutoJdkPluginVersion();
        log.info("AutoJDK extension injecting AutoJDK plugin " + autoJdkPluginVersion + " into project " + project.getArtifactId());

        Plugin plugin = new Plugin();
        plugin.setGroupId(AUTOJDK_PLUGIN_GROUP_ID);
        plugin.setArtifactId(AUTOJDK_PLUGIN_ARTIFACT_ID);
        plugin.setVersion(autoJdkPluginVersion);

        PluginExecution execution = new PluginExecution();
        execution.setGoals(new ArrayList<>(Collections.singletonList("prepare"))); //Wrapped in ArrayList so later anyone using getter can modify list
        plugin.addExecution(execution);

        //AutoJDK plugin needs to come before toolchains plugin so inject it at the top of the list
        project.getBuild().getPlugins().add(0, plugin);
        project.getBuild().getPluginsAsMap().put(plugin.getKey(), plugin);
    }

    /**
     * @return the calculated version of Java this project needs, or null if it couldn't be detected or isn't a Java project.
     */
    private String calculateRequiredJavaVersion(MavenProject project, AutoJdkExtensionProperties extensionProperties)
    {
        //Look at:
        //- compiler plugin configuration
        //- well known java version properties
        //- enforcer plugin configuration
        //TODO maybe make this extensible?

        //If explicitly specified on command line, use that
        if (extensionProperties.getJdkVersion() != null)
        {
            //If this is an integer, convert to range
            Integer majorVersion = attemptParseMajorJdkVersion(extensionProperties.getJdkVersion());
            if (majorVersion != null)
                return majorVersionToRange(majorVersion);
            else
                return extensionProperties.getJdkVersion();
        }

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
        {
            int bestMajorVersion = javaVersions.last();
            return majorVersionToRange(bestMajorVersion);
        }
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

        Integer majorVersion = attemptParseMajorJdkVersion(version);
        if (majorVersion != null)
            versions.add(majorVersion);
    }

    private Integer attemptParseMajorJdkVersion(String version)
    {
        if (version == null)
            return null;

        version = version.trim();

        if (version.isEmpty())
            return null;

        //Adjust 1.x -> x
        if (version.startsWith("1."))
            version = version.substring("1.".length());

        //Must be a number, if not don't use
        try
        {
            return Integer.parseInt(version);
        }
        catch (NumberFormatException e)
        {
            //Ignore versions that don't parse into a number
            return null;
        }
    }

    /**
     * Read plugin version from resource on classpath.  We can't use the Maven lookup mechanism (inject PluginDescriptor ${plugin})
     * because we are an extension, but there should only be one version of the plugin on our classpath so this
     * should work.
     *
     * @return the version of the AutoJDK plugin this extension exists in.
     *
     * @throws MavenExecutionException if an error occurs reading the plugin version.
     */
    private String lookupAutoJdkPluginVersion()
    throws MavenExecutionException
    {
        URL autoJdkArtifactMetadataPropertiesResource =
                AutoJdkInjectorExtension.class.getResource("/META-INF/maven/" + AUTOJDK_PLUGIN_GROUP_ID + "/" + AUTOJDK_PLUGIN_ARTIFACT_ID + "/pom.properties");
        if (autoJdkArtifactMetadataPropertiesResource == null)
            throw new MavenExecutionException("Could not find plugin metadata resource.", (Throwable)null);

        Properties autoJdkArtifactMetadataProperties = new Properties();
        try (InputStream autoJdkArtifactMetadataPropertiesIs = autoJdkArtifactMetadataPropertiesResource.openStream())
        {
            autoJdkArtifactMetadataProperties.load(autoJdkArtifactMetadataPropertiesIs);
        }
        catch (IOException e)
        {
            throw new MavenExecutionException("Error reading plugin metadata resource: " + e.getMessage(), e);
        }

        String version = autoJdkArtifactMetadataProperties.getProperty("version");
        if (version == null)
            throw new MavenExecutionException("No version property in plugin metadata.", (Throwable)null);

        return version;
    }

    /**
     * AutoJDK properties that may be set on the command line by the user through system properties.
     */
    private static class AutoJdkExtensionProperties
    {
        private final String jdkVendor;
        private final String jdkVersion;

        public AutoJdkExtensionProperties(String jdkVendor, String jdkVersion)
        {
            this.jdkVendor = jdkVendor;
            this.jdkVersion = jdkVersion;
        }

        /**
         * Reads system properties from the Maven session.
         */
        public static AutoJdkExtensionProperties fromMavenSession(MavenSession session)
        throws MavenExecutionException
        {
            PluginParameterExpressionEvaluator evaluator = new PluginParameterExpressionEvaluator(session, new MojoExecution(null));
            try
            {
                String vendor = (String)evaluator.evaluate("${" + PrepareMojo.PROPERTY_JDK_VENDOR + "}", String.class);
                String version = (String)evaluator.evaluate("${" + PrepareMojo.PROPERTY_JDK_VERSION + "}", String.class);
                return new AutoJdkExtensionProperties(vendor, version);
            }
            catch (ExpressionEvaluationException e)
            {
                throw new MavenExecutionException("Error reading properties: " + e, e);
            }
        }

        /**
         * @return JDK vendor specified on command line.  -D with {@value PrepareMojo#PROPERTY_JDK_VENDOR}
         */
        public String getJdkVendor()
        {
            return jdkVendor;
        }

        /**
         * @return JDK version specified on command line.  -D with {@value PrepareMojo#PROPERTY_JDK_VERSION}
         */
        public String getJdkVersion()
        {
            return jdkVersion;
        }
    }
}
