package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.util.List;

/**
 * Removes JDKs previously installed by autojdk from the local system.
 */
@Mojo(name="purge-jdks")
public class PurgeJdksMojo extends AbstractAutoJdkMojo
{
    @Parameter(property = "autojdk.purge.version", required = true)
    private String purgeJdkVersion;

    @Parameter(property = "autojdk.purge.vendor", required = true)
    private String purgeJdkVendor;

    @Parameter(property = "autojdk.purge.releaseType", defaultValue = "GA", required = true)
    private ReleaseType purgeJdkReleaseType;

    private static final String PURGE_ALL_VENDOR = "all";

    @Override
    protected void executeImpl() throws MojoExecutionException, MojoFailureException
    {
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
            int numJdksDeleted = autoJdk().deleteLocalJdks(jdkSearchRequest);
            if (numJdksDeleted > 0)
                getLog().info(numJdksDeleted + " JDK(s) deleted");
            else
                getLog().warn("No JDKs matching criteria were found to be deleted.");

        }
        catch (LocalJdkResolutionException | IOException e)
        {
            throw new MojoExecutionException("Error deleting local JDKs: " + e, e);
        }
    }
}
