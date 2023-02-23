package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.Collection;

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
    protected static Integer attemptParseMajorJdkVersion(String version)
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
     * Reads a version from a configuration value of a plugin into a collection.
     *
     * @param configuration the plugin configuration.
     * @param configurationKey the configuration property name to read.
     *
     * @return the read compiler version.
     */
    protected static Integer getJavaVersionFromPluginConfiguration(Xpp3Dom configuration, String configurationKey)
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
     * Reads a version from a configuration value of a plugin into a collection.
     *
     * @param project the project being read.
     * @param configuration the plugin configuration.
     * @param configurationKey the configuration property name to read.
     * @param fallbackProperty the system property used as a fallback if the value is not explicitly defined in configuration.
     * @param versions a collection of versions that will receive the additional version that was read, if it could be read.  Otherwise this collection is untouched.
     */
    protected static void readJavaVersionFromPlugin(ProjectContext project, Xpp3Dom configuration, String configurationKey, String fallbackProperty, Collection<? super Integer> versions)
    {
        if (configuration != null)
        {
            Integer configVersion = getJavaVersionFromPluginConfiguration(configuration, configurationKey);
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
