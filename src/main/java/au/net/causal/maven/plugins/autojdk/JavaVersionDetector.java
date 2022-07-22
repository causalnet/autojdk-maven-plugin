package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * For a given Maven project, detects the Java version that should be used.
 */
public abstract class JavaVersionDetector
{
    /**
     * Detect the Java version to be used for a Maven project.
     *
     * @param project the Maven project and associated data.
     *
     * @return the detected Java version if detected, or null if it could not be determined.
     *
     * @throws VersionDetectionException if an error occurs during detection.  Should only be thrown if there is a condition serious enough to fail the build - return null
     *              if the version simply cannot be detected by the detector.
     */
    public abstract VersionRange detectJavaVersion(ProjectContext project)
    throws VersionDetectionException;

    /**
     * Utility method to turn a major Java version into a version range that matches the entire major version.
     *
     * @param majorVersion the JDK major version number.  e.g. 17 for Java 17.
     *
     * @return a version range suitable for matching this version.
     */
    public static VersionRange majorVersionToRange(int majorVersion)
    {
        try
        {
            return VersionRange.createFromVersionSpec("[" + majorVersion + "," + (majorVersion + 1) + ")");
        }
        catch (InvalidVersionSpecificationException e)
        {
            //Should not happen since we are constructing from an integer
            throw new RuntimeException(e);
        }
    }

    /**
     * Attempts to parse a major JDK version number from a string.  Special handling is performed for old JDK versions prefixed by '1.', so '1.6' will return
     * the number 6.
     * <p>
     *
     * Only integers and numbers in the form of '1.x' will be parsed.  Other version numbers that have one or more '.' or other characters will not be parsed and will
     * return null.
     *
     * @param version the version string to parse.
     *
     * @return the JDK major version number if it could be parsed, or null if the version string was not understood.
     */
    public static Integer attemptParseMajorJdkVersion(String version)
    {
        if (version == null)
            return null;

        version = version.trim();

        if (version.isEmpty())
            return null;

        //Adjust 1.x -> x
        if (version.startsWith("1."))
            version = version.substring("1.".length());

        //Must be a number, if not don't use
        try
        {
            return Integer.parseInt(version);
        }
        catch (NumberFormatException e)
        {
            //Ignore versions that don't parse into a number
            return null;
        }
    }

    /**
     * Thrown when an error occurs during Java version detection.
     */
    public static class VersionDetectionException extends Exception
    {
        public VersionDetectionException()
        {
        }

        public VersionDetectionException(String message)
        {
            super(message);
        }

        public VersionDetectionException(String message, Throwable cause)
        {
            super(message, cause);
        }

        public VersionDetectionException(Throwable cause)
        {
            super(cause);
        }
    }
}
