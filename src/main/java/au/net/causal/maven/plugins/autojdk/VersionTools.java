package au.net.causal.maven.plugins.autojdk;

import com.google.common.annotations.VisibleForTesting;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * Utility methods for dealing with versions.
 */
public final class VersionTools
{
    /**
     * Private constructor to prevent instantiation.
     */
    private VersionTools()
    {
    }

    /**
     * Returns whether a version specifies only a major version and nothing else.  e.g. "17" -> true, "17.0" -> false.
     *
     * @param version the version to check.
     *
     * @return true if the version only has a major version in it and nothing else, false otherwise.
     */
    public static boolean isMajorVersionOnly(ArtifactVersion version)
    {
        ArtifactVersion majorOnly = new DefaultArtifactVersion(String.valueOf(version.getMajorVersion()));
        return version.toString().equals(majorOnly.toString());

        //Do not use the following - it can not tell difference between "8.0" and "8" - Maven internal version comparison is seriously convoluted!
        //return version.equals(majorOnly);
    }
}
