package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

public class KotlinPluginJavaDetector extends JavaVersionDetector
{
    @Override
    public VersionRange detectJavaVersion(ProjectContext project)
    throws VersionDetectionException
    {
        Xpp3Dom kotlinConfig = project.readPluginConfiguration("org.jetbrains.kotlin", "kotlin-maven-plugin");
        SortedSet<Integer> javaVersions = new TreeSet<>();

        //Main plugin config
        readJavaVersionFromKotlinPluginConfiguration(kotlinConfig, "jvmTarget", javaVersions);

        //Also read from executions
        Xpp3Dom compileExecutionConfig = project.readExecutionConfiguration("org.jetbrains.kotlin", "kotlin-maven-plugin", "compile");
        readJavaVersionFromKotlinPluginConfiguration(compileExecutionConfig, "jvmTarget", javaVersions);
        Xpp3Dom testCompileExecutionConfig = project.readExecutionConfiguration("org.jetbrains.kotlin", "kotlin-maven-plugin", "test-compile");
        readJavaVersionFromKotlinPluginConfiguration(testCompileExecutionConfig, "jvmTarget", javaVersions);

        //Also check kotlin.compiler.jvmTarget property but only if we couldn't read version directly
        if (javaVersions.isEmpty())
        {
            try
            {
                String jvmTarget = project.evaluateProjectProperty("kotlin.compiler.jvmTarget");
                if (jvmTarget != null)
                    javaVersions.add(attemptParseMajorJdkVersion(jvmTarget));
            }
            catch (MavenExecutionException e)
            {
                throw new VersionDetectionException(e);
            }
        }

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
     * Reads a version from a configuration value of the kotlin plugin into a collection.
     *
     * @param configuration the compiler plugin configuration.
     * @param configurationKey the configuration property name to read.
     * @param versions a collection of versions that will receive the additional version that was read, if it could be read.  Otherwise this collection is untouched.
     */
    private void readJavaVersionFromKotlinPluginConfiguration(Xpp3Dom configuration, String configurationKey, Collection<? super Integer> versions)
    {
        if (configuration == null)
            return;

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
