package au.net.causal.maven.plugins.autojdk;

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
     * Whether to match normal releases (GA) or early access releases (EA) when purging.
     */
    @Parameter(property = "autojdk.purge.releaseType", defaultValue = "GA", required = true)
    private ReleaseType purgeJdkReleaseType;

    /**
     * Also remove any cached archives of JDKs that are removed that may be stored on the local system, such as in the local Maven repository.
     */
    @Parameter(property = "autojdk.purge.caches", defaultValue = "true", required = true)
    private boolean purgeCaches;

    private static final String PURGE_ALL_VENDOR = "all";

    @Override
    public void execute()
    throws MojoExecutionException, MojoFailureException
    {
        super.execute();

        String vendorPurgeFilter;
        if (PURGE_ALL_VENDOR.equals(purgeJdkVendor))
            vendorPurgeFilter = null;
        else
            vendorPurgeFilter = purgeJdkVendor;

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
                                                                  platformTools.getCurrentArchitecture(),
                                                                  platformTools.getCurrentOperatingSystem(),
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
