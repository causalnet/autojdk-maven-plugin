package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.codehaus.plexus.util.StringUtils;

import java.util.ArrayList;
import java.util.List;


public enum JdkVersionExpander
{
    /**
     * Keep full versions as their full versions only, do not expand version numbers at all.
     * e.g. '17.0.2-36' remains only at '17.0.2-36'
     *
     */
    KEEP
    {
        @Override
        public List<? extends ArtifactVersion> expandVersions(ArtifactVersion version)
        {
            return List.of(version);
        }
    },

    /**
     * Expands every version number out to its major version and its full version.
     * e.g. '17.0.2' becomes '17' and '17.0.2'
     */
    MAJOR_AND_FULL
    {
        @Override
        public List<? extends ArtifactVersion> expandVersions(ArtifactVersion version)
        {
            List<ArtifactVersion> results = new ArrayList<>(2);
            ArtifactVersion majorOnly = new DefaultArtifactVersion(String.valueOf(version.getMajorVersion()));
            results.add(version);
            if (!version.equals(majorOnly))
                results.add(majorOnly);

            return results;
        }
    },

    /**
     * Converts a single specific JDK version number into all the version numbers that it supports.
     * e.g. '17.0.2-36' supports '17.0.2-36', '17.0.2', '17.0' and '17'
     */
    EXPAND_ALL
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
    };

    public abstract List<? extends ArtifactVersion> expandVersions(ArtifactVersion version);
}
