package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

public abstract class JavaVersionDetector
{
    public abstract VersionRange detectJavaVersion(ProjectContext project)
    throws VersionDetectionException;

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

    public static class VersionDetectionException extends Exception
    {
        public VersionDetectionException() {
        }

        public VersionDetectionException(String message) {
            super(message);
        }

        public VersionDetectionException(String message, Throwable cause) {
            super(message, cause);
        }

        public VersionDetectionException(Throwable cause) {
            super(cause);
        }
    }
}
