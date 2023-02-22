package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Detects the JDK version by looking at the 'source', 'target' and 'release' configuration options of the Maven compiler plugin configured on a project.
 * The highest of these values will be returned from this detector.  Version numbers of the form '1.x' for older Java versions will also be handled
 * appropriately and translated to a match for major version number 'x'.
 */
public class CompilerPluginJavaDetector extends JavaVersionDetector
{
    @Override
    public VersionRange detectJavaVersion(ProjectContext project)
    throws VersionDetectionException
    {
        Plugin plugin = project.getProject().getPlugin("org.apache.maven.plugins:maven-compiler-plugin");
        if (plugin == null)
            return null;

        //config may be null if there is no configuration defined in the POM
        Xpp3Dom compilerConfig = project.readPluginConfiguration(plugin);

        SortedSet<Integer> javaVersions = new TreeSet<>();
        readJavaVersionFromCompilerPlugin(project, compilerConfig, "source", "maven.compiler.source", javaVersions);
        readJavaVersionFromCompilerPlugin(project, compilerConfig, "target", "maven.compiler.target", javaVersions);
        readJavaVersionFromCompilerPlugin(project, compilerConfig, "release", "maven.compiler.release", javaVersions);

        //Pick the highest needed Java version
        if (javaVersions.isEmpty())
            return null;
        else
        {
            int bestMajorVersion = javaVersions.last();
            return majorVersionToRange(bestMajorVersion);
        }
    }

    /**
     * Reads a version from a configuration value of the compiler plugin into a collection.
     *
     * @param configuration the compiler plugin configuration.
     * @param configurationKey the configuration property name to read.
     *
     * @return the read compiler version.
     */
    private Integer getJavaVersionFromCompilerPluginConfiguration(Xpp3Dom configuration, String configurationKey)
    {
        Xpp3Dom element = configuration.getChild(configurationKey);
        if (element == null)
            return null;

        String version = element.getValue().trim();
        if (StringUtils.isEmpty(version))
            return null;

        return attemptParseMajorJdkVersion(version);
    }

    /**
     * Reads a version from a configuration value of the compiler plugin into a collection.
     *
     * @param project the project being read.
     * @param configuration the compiler plugin configuration.
     * @param configurationKey the configuration property name to read.
     * @param fallbackProperty the system property used as a fallback if the value is not explicitly defined.
     * @param versions a collection of versions that will receive the additional version that was read, if it could be read.  Otherwise this collection is untouched.
     */
    private void readJavaVersionFromCompilerPlugin(ProjectContext project, Xpp3Dom configuration, String configurationKey, String fallbackProperty, Collection<? super Integer> versions)
    {
        if (configuration != null)
        {
            Integer configVersion = getJavaVersionFromCompilerPluginConfiguration(configuration, configurationKey);
            if (configVersion != null)
            {
                versions.add(configVersion);
                return;
            }
        }

        //Try to use property fallback
        try
        {
            String fallbackValue = project.evaluateProjectProperty(fallbackProperty);
            if (fallbackValue == null)
                return;

            String version = fallbackValue.trim();
            if (StringUtils.isEmpty(version))
                return;

            Integer majorVersion = attemptParseMajorJdkVersion(version);
            if (majorVersion != null)
                versions.add(majorVersion);
        }
        catch (MavenExecutionException e)
        {
            //Failed to read property, so give up
        }
    }
}
