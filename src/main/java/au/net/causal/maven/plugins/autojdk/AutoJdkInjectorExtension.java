package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.AutoJdkConfiguration.ExtensionExclusion;
import au.net.causal.maven.plugins.autojdk.ExtensionExclusionProcessor.ExclusionProcessorException;
import au.net.causal.maven.plugins.autojdk.config.ActivationProcessor;
import au.net.causal.maven.plugins.autojdk.config.AutoJdkConfigurationException;
import jakarta.xml.bind.JAXBException;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.path.DefaultPathTranslator;
import org.apache.maven.model.path.ProfileActivationFilePathInterpolator;
import org.apache.maven.model.profile.activation.FileProfileActivator;
import org.apache.maven.model.profile.activation.JdkVersionProfileActivator;
import org.apache.maven.model.profile.activation.OperatingSystemProfileActivator;
import org.apache.maven.model.profile.activation.PropertyProfileActivator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutoJdkInjectorExtension extends AbstractMavenLifecycleParticipant
{
    private static final Logger log = LoggerFactory.getLogger(AutoJdkInjectorExtension.class);

    private static final String AUTOJDK_PLUGIN_GROUP_ID = "au.net.causal.maven.plugins";
    private static final String AUTOJDK_PLUGIN_ARTIFACT_ID = "autojdk-maven-plugin";

    /**
     * Ordered list of version detectors that are tried in order to determine the version of the JDK to use in order to inject AutoJDK and toolchains.
     * First one that successfully reads a version wins and no subsequent ones will be attempted.
     */
    private final List<? extends JavaVersionDetector> javaVersionDetectors = List.of(
            new UserPropertyJavaDetector(),
            new CompilerPluginJavaDetector(),
            new KotlinPluginJavaDetector()
    );

    /**
     * Plexus component that delegates to our outer class but handles the nuances/weirdness of running both in a ext/lib or extension with isolated classloader.
     * The outer class will always be called with an isolated classloader and full dependencies loaded thanks to this loader.
     */
    @Component(role = AbstractMavenLifecycleParticipant.class, hint = "autojdk-injector")
    public static class Loader extends DependencyLoaderMavenLifecycleParticipant
    {
        public Loader()
        {
            super(new ExtensionSpec(AUTOJDK_PLUGIN_GROUP_ID, AUTOJDK_PLUGIN_ARTIFACT_ID), AutoJdkInjectorExtension.class);
        }
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException
    {
        AutoJdkExtensionProperties extensionProperties = AutoJdkExtensionProperties.fromMavenSession(session);

        AutoJdkHome autojdkHome = AutoJdkHome.defaultHome();
        AutoJdkConfiguration autoJdkConfiguration;
        try
        {
            Path autoJdkConfigFile = extensionProperties.getAutoJdkConfigFile();
            if (autoJdkConfigFile == null)
                autoJdkConfigFile = autojdkHome.getAutoJdkConfigurationFile();

            log.debug("AutoJDK config file: " + autoJdkConfigFile.toAbsolutePath());

            FileProfileActivator fileProfileActivator = new FileProfileActivator();
            if (classExists("org.apache.maven.model.path.ProfileActivationFilePathInterpolator")) //Needed for Maven < 3.8
            {
                ProfileActivationFilePathInterpolator profileActivationFilePathInterpolator = new ProfileActivationFilePathInterpolator();
                profileActivationFilePathInterpolator.setPathTranslator(new DefaultPathTranslator());
                fileProfileActivator.setProfileActivationFilePathInterpolator(profileActivationFilePathInterpolator);
            }
            ActivationProcessor activationProcessor = new ActivationProcessor(fileProfileActivator, new OperatingSystemProfileActivator(), new PropertyProfileActivator(), new JdkVersionProfileActivator(), lookupAutoJdkPluginVersion());

            AutoJdkXmlManager xmlManager = new AutoJdkXmlManager();
            autoJdkConfiguration = AutoJdkConfiguration.fromFile(autoJdkConfigFile, xmlManager, activationProcessor, session);
        }
        catch (AutoJdkXmlManager.XmlParseException | JAXBException | AutoJdkConfigurationException e)
        {
            throw new MavenExecutionException("Error reading " + autojdkHome.getAutoJdkConfigurationFile() + ": " + e.getMessage(), e);
        }

        for (MavenProject project : session.getProjects())
        {
            processProject(project, session, extensionProperties, autoJdkConfiguration);
        }
    }

    private static boolean classExists(String className)
    {
        try
        {
            Class.forName(className, false, AutoJdkInjectorExtension.class.getClassLoader());
            return true;
        }
        catch (ClassNotFoundException e)
        {
            return false;
        }
    }

    private void processProject(MavenProject project, MavenSession session,
                                AutoJdkExtensionProperties extensionProperties,
                                AutoJdkConfiguration autoJdkConfiguration)
    throws MavenExecutionException
    {
        Plugin autoJdkPlugin = project.getPlugin(AUTOJDK_PLUGIN_GROUP_ID + ":" + AUTOJDK_PLUGIN_ARTIFACT_ID);
        Plugin toolchainsPlugin = project.getPlugin("org.apache.maven.plugins:maven-toolchains-plugin");
        Plugin kotlinPlugin = project.getPlugin("org.jetbrains.kotlin:kotlin-maven-plugin");
        boolean kotlinSupportRequired = kotlinPlugin != null;

        VersionRange requiredJavaVersion = calculateRequiredJavaVersionRange(project, session, extensionProperties);
        if (requiredJavaVersion == null)
            return;

        //Substitution processing
        ExtensionExclusionProcessor exclusionProcessor = new ExtensionExclusionProcessor(autoJdkConfiguration.getExtensionExclusions());
        try
        {
            ExtensionExclusion matchedExclusion = exclusionProcessor.checkExclusions(requiredJavaVersion);
            if (matchedExclusion != null)
            {
                if (matchedExclusion.getSubstitution() != null)
                {
                    log.info("AutoJDK extension substituting Java version " + matchedExclusion.getSubstitution() + " for requirement " + requiredJavaVersion + " due to exclusion configuration");
                    try
                    {
                        requiredJavaVersion = VersionRange.createFromVersionSpec(matchedExclusion.getSubstitution());
                    }
                    catch (InvalidVersionSpecificationException e)
                    {
                        throw new MavenExecutionException("Invalid substitution version '" + matchedExclusion.getSubstitution() + "' in AutoJDK configuration.", e);
                    }
                }
                else //No substitution, that means bail out and don't inject anything in this project
                {
                    log.info("AutoJDK extension ignoring required Java version " + requiredJavaVersion + " because it is configured to be excluded.");
                    return;
                }

            }
        }
        catch (ExclusionProcessorException e)
        {
            throw new MavenExecutionException(e.getMessage(), e);
        }

        //Inject toolchains, autojdk plugin
        if (toolchainsPlugin == null)
            injectToolchainsPlugin(project, requiredJavaVersion, extensionProperties);
        if (autoJdkPlugin == null)
            injectAutoJdkPlugin(project, kotlinSupportRequired);
    }

    private void injectToolchainsPlugin(MavenProject project, VersionRange requiredJavaVersion, AutoJdkExtensionProperties extensionProperties)
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
        versionElement.setValue(requiredJavaVersion.toString());
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

    private void injectAutoJdkPlugin(MavenProject project, boolean kotlinSupportRequired)
    throws MavenExecutionException
    {
        String autoJdkPluginVersion = lookupAutoJdkPluginVersion();

        log.info("AutoJDK extension injecting AutoJDK plugin " + autoJdkPluginVersion + " into project " + project.getArtifactId());

        Plugin plugin = new Plugin();
        plugin.setGroupId(AUTOJDK_PLUGIN_GROUP_ID);
        plugin.setArtifactId(AUTOJDK_PLUGIN_ARTIFACT_ID);
        plugin.setVersion(autoJdkPluginVersion);

        PluginExecution execution = new PluginExecution();
        List<String> goals = new ArrayList<>();
        goals.add("prepare");
        if (kotlinSupportRequired)
            goals.add("kotlin-toolchain-bridge");

        execution.setGoals(goals);
        plugin.addExecution(execution);

        //AutoJDK plugin needs to come before toolchains plugin so inject it at the top of the list
        project.getBuild().getPlugins().add(0, plugin);
        project.getBuild().getPluginsAsMap().put(plugin.getKey(), plugin);
    }

    /**
     * Tried each registered Java version detector to find the JDK version to use until one succeeds.  The first one that is successful is used.
     *
     * @param project the Maven project being processed.
     * @param extensionProperties AutoJDK extension properties that the user may have configured on the command line.
     *
     * @return the calculated version of Java this project needs, or null if it couldn't be detected or isn't a Java project.  This will be a version range.
     *
     * @throws MavenExecutionException if an error occurs creating or processing the Java version.
     */
    private VersionRange calculateRequiredJavaVersionRange(MavenProject project,
                                                           MavenSession session,
                                                           AutoJdkExtensionProperties extensionProperties)
    throws MavenExecutionException
    {
        //Look at:
        //- compiler plugin configuration
        //- well known java version properties
        //- enforcer plugin configuration
        //TODO maybe make this extensible?

        ProjectContext projectContext = new ProjectContext(project, session, extensionProperties);
        for (JavaVersionDetector javaVersionDetector : javaVersionDetectors)
        {
            try
            {
                VersionRange detectedJavaVersion = javaVersionDetector.detectJavaVersion(projectContext);
                if (detectedJavaVersion != null)
                    return detectedJavaVersion;
            }
            catch (JavaVersionDetector.VersionDetectionException e)
            {
                throw new MavenExecutionException(e.getMessage(), e);
            }
        }

        return null;
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
        try
        {
            return PluginMetadataTools.lookupArtifactVersion(AUTOJDK_PLUGIN_GROUP_ID, AUTOJDK_PLUGIN_ARTIFACT_ID, AutoJdkInjectorExtension.class);
        }
        catch (PluginMetadataTools.ArtifactMetadataException e)
        {
            throw new MavenExecutionException(e.getMessage(), e);
        }
    }
}
