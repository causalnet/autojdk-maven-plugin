package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;

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
        readJavaVersionFromPlugin(project, compilerConfig, "source", "maven.compiler.source", javaVersions);
        readJavaVersionFromPlugin(project, compilerConfig, "target", "maven.compiler.target", javaVersions);
        readJavaVersionFromPlugin(project, compilerConfig, "release", "maven.compiler.release", javaVersions);

        //Pick the highest needed Java version
        if (javaVersions.isEmpty())
            return null;
        else
        {
            int bestMajorVersion = javaVersions.last();
            return majorVersionToRange(bestMajorVersion);
        }
    }
}
