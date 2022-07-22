package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.VersionRange;
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
        Xpp3Dom compilerConfig = project.readPluginConfiguration("org.apache.maven.plugins", "maven-compiler-plugin");
        if (compilerConfig == null)
            return null;

        SortedSet<Integer> javaVersions = new TreeSet<>();
        readJavaVersionFromCompilerPluginConfiguration(compilerConfig, "source", javaVersions);
        readJavaVersionFromCompilerPluginConfiguration(compilerConfig, "target", javaVersions);
        readJavaVersionFromCompilerPluginConfiguration(compilerConfig, "release", javaVersions);

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
     * @param versions a collection of versions that will receive the additional version that was read, if it could be read.  Otherwise this collection is untouched.
     */
    private void readJavaVersionFromCompilerPluginConfiguration(Xpp3Dom configuration, String configurationKey, Collection<? super Integer> versions)
    {
        Xpp3Dom element = configuration.getChild(configurationKey);
        if (element == null)
            return;

        String version = element.getValue().trim();
        if (StringUtils.isEmpty(version))
            return;

        Integer majorVersion = attemptParseMajorJdkVersion(version);
        if (majorVersion != null)
            versions.add(majorVersion);
    }
}
