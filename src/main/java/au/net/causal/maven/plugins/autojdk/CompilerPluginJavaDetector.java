package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

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
