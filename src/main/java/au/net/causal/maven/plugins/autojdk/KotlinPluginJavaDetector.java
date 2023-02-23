package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.plexus.util.xml.Xpp3Dom;

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
        readJavaVersionFromPlugin(project, kotlinConfig, "jvmTarget", "kotlin.compiler.jvmTarget", javaVersions);

        //Also read from executions
        Xpp3Dom compileExecutionConfig = project.readExecutionConfiguration("org.jetbrains.kotlin", "kotlin-maven-plugin", "compile");
        readJavaVersionFromPlugin(project, compileExecutionConfig, "jvmTarget", "kotlin.compiler.jvmTarget", javaVersions);
        Xpp3Dom testCompileExecutionConfig = project.readExecutionConfiguration("org.jetbrains.kotlin", "kotlin-maven-plugin", "test-compile");
        readJavaVersionFromPlugin(project, testCompileExecutionConfig, "jvmTarget", "kotlin.compiler.jvmTarget", javaVersions);

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
