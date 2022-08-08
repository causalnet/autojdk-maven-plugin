package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Removes JDKs previously installed by autojdk from the local system.
 */
@Mojo(name="purge-jdks")
public class PurgeJdksMojo extends AbstractAutoJdkMojo
{
    @Parameter(property = "autojdk.purge.version", required = true)
    private String purgeJdkVersion;

    @Parameter(property = "autojdk.purge.vendor")
    private String purgeJdkVendor;

    @Parameter(property = "autojdk.purge.releaseType", defaultValue = "GA", required = true)
    private ReleaseType purgeJdkReleaseType;

    @Override
    protected void executeImpl() throws MojoExecutionException, MojoFailureException
    {
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
                                                                  purgeJdkVendor,
                                                                  purgeJdkReleaseType);
        try
        {
            autoJdk().deleteLocalJdks(jdkSearchRequest);
        }
        catch (LocalJdkResolutionException e)
        {
            throw new MojoExecutionException("Error deleting local JDKs: " + e, e);
        }
    }
}
