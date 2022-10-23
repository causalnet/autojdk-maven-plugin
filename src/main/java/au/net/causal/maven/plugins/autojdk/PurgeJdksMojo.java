package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;

/**
 * Removes JDKs previously installed by AutoJDK from the local system.
 */
@Mojo(name="purge-jdks", requiresProject = false)
public class PurgeJdksMojo extends AbstractAutoJdkMojo
{
    /**
     * The version range or explicit version of the JDK(s) to remove.  e.g. "17.0.1", "[17, 18)"
     */
    @Parameter(property = "autojdk.purge.version", required = true)
    private String purgeJdkVersion;

    /**
     * The vendor of the JDK to remove.  Use a value of 'all' to match all vendors.
     */
    @Parameter(property = "autojdk.purge.vendor", required = true)
    private String purgeJdkVendor;

    /**
     * The system architecture of the JDK to remove.  Use a value of 'all' to match all architectures.  Defaults to the current system architecture.
     * e.g. 'x64', 'aarch64'
     */
    @Parameter(property = "autojdk.purge.architecture")
    private String purgeJdkArchitecture;

    /**
     * The operating system of the JDK to remove.  Use a value of 'all' to match all operating systems.  Defaults to the current operating system.
     * e.g. 'windows', 'linux'
     */
    @Parameter(property = "autojdk.purge.os")
    private String purgeJdkOperatingSystem;

    /**
     * Whether to match normal releases (GA) or early access releases (EA) when purging.
     */
    @Parameter(property = "autojdk.purge.releaseType", defaultValue = "GA", required = true)
    private ReleaseType purgeJdkReleaseType;

    /**
     * Also remove any cached archives of JDKs that are removed that may be stored on the local system, such as in the local Maven repository.
     */
    @Parameter(property = "autojdk.purge.caches", defaultValue = "true", required = true)
    private boolean purgeCaches;

    /**
     * Wildcard value that specifies to select all vendors, architectures or operating systems.
     */
    private static final String PURGE_ALL_VALUE = "all";

    @Override
    public void execute()
    throws MojoExecutionException, MojoFailureException
    {
        super.execute();

        String vendorPurgeFilter;
        if (PURGE_ALL_VALUE.equals(purgeJdkVendor))
            vendorPurgeFilter = null;
        else
            vendorPurgeFilter = purgeJdkVendor;

        Architecture architecturePurgeFilter;
        if (purgeJdkArchitecture == null)
            architecturePurgeFilter = platformTools.getCurrentArchitecture();
        else if (PURGE_ALL_VALUE.equals(purgeJdkArchitecture))
            architecturePurgeFilter = null;
        else
            architecturePurgeFilter = Architecture.fromText(purgeJdkArchitecture);

        OperatingSystem osPurgeFilter;
        if (purgeJdkOperatingSystem == null)
            osPurgeFilter = platformTools.getCurrentOperatingSystem();
        else if (PURGE_ALL_VALUE.equals(purgeJdkOperatingSystem))
            osPurgeFilter = null;
        else
            osPurgeFilter = OperatingSystem.fromText(purgeJdkOperatingSystem);

        VersionRange purgeJdkVersionRange;
        try
        {
            purgeJdkVersionRange = VersionRange.createFromVersionSpec(purgeJdkVersion);
        }
        catch (InvalidVersionSpecificationException e)
        {
            throw new MojoExecutionException("Invalid JDK version/range: " + purgeJdkVersion, e);
        }

        JdkSearchRequest jdkSearchRequest =  new JdkSearchRequest(purgeJdkVersionRange,
                                                                  architecturePurgeFilter,
                                                                  osPurgeFilter,
                                                                  vendorPurgeFilter,
                                                                  purgeJdkReleaseType);
        try
        {
            int numJdksDeleted = autoJdk().deleteLocalJdks(jdkSearchRequest, purgeCaches);
            if (numJdksDeleted > 0)
                getLog().info(numJdksDeleted + " JDK(s) deleted");
            else
                getLog().warn("No JDKs matching criteria were found to be deleted.");

        }
        catch (LocalJdkResolutionException | IOException | JdkRepositoryException e)
        {
            throw new MojoExecutionException("Error deleting local JDKs: " + e, e);
        }
    }
}
