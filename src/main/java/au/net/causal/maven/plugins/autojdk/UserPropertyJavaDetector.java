package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * Reads the JDK version from an AutoJDK property set by the user on the command line.
 *
 * @see AutoJdkExtensionProperties#getJdkVersion()
 */
public class UserPropertyJavaDetector extends JavaVersionDetector
{
    @Override
    public VersionRange detectJavaVersion(ProjectContext project)
    throws VersionDetectionException
    {
        AutoJdkExtensionProperties extensionProperties = project.getAutoJdkExtensionProperties();

        if (extensionProperties.getJdkVersion() == null)
            return null;

        //If Java version explicitly specified on command line, use that

        //If this is an integer, convert to range
        Integer majorVersion = attemptParseMajorJdkVersion(extensionProperties.getJdkVersion());
        if (majorVersion != null)
            return majorVersionToRange(majorVersion);
        else
        {
            try
            {
                return VersionRange.createFromVersionSpec(extensionProperties.getJdkVersion());
            }
            catch (InvalidVersionSpecificationException e)
            {
                //If the user set the version property to an invalid version, don't ignore it  - fail the build
                throw new VersionDetectionException("Invalid version range '" + extensionProperties.getJdkVersion() + "' specified as JDK version.", e);
            }
        }
    }
}
