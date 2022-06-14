package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.codehaus.plexus.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a single specific JDK version number into all the version numbers that it supports.
 * e.g. '17.0.2.0-36' supports '17.0.2.0-36', '17.0.2.0', '17.0.2', '17.0' and '17'
 */
public class JdkVersionExpander
{
    public List<? extends ArtifactVersion> expandVersions(ArtifactVersion version)
    {
        List<ArtifactVersion> results = new ArrayList<>(4);
        if (StringUtils.isNotEmpty(version.getQualifier()))
            results.add(new DefaultArtifactVersion(version.getMajorVersion() + "." + version.getMinorVersion() + "." + version.getIncrementalVersion() + "-" + version.getQualifier()));
        else if (version.getBuildNumber() != 0)
            results.add(new DefaultArtifactVersion(version.getMajorVersion() + "." + version.getMinorVersion() + "." + version.getIncrementalVersion() + "-" + version.getBuildNumber()));

        results.add(new DefaultArtifactVersion(version.getMajorVersion() + "." + version.getMinorVersion() + "." + version.getIncrementalVersion()));
        results.add(new DefaultArtifactVersion(version.getMajorVersion() + "." + version.getMinorVersion()));
        results.add(new DefaultArtifactVersion(String.valueOf(version.getMajorVersion())));

        return results;
    }
}
