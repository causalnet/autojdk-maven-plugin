package au.net.causal.maven.plugins.autojdk;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prints out support for different host platforms (by operating system and architecture) and latest JDK versions for the current project's configured JDK.
 */
@Mojo(name="platform-support")
public class ShowPlatformSupportMojo extends AbstractAutoJdkMojo
{
    @Override
    protected void executeImpl() throws MojoExecutionException, MojoFailureException
    {
        JdkSearchRequest jdkSearchRequest =  new JdkSearchRequest(getRequiredJdkVersionRange(),
                                                                  null,
                                                                  null,
                                                                  getRequiredJdkVendor(),
                                                                  getJdkReleaseType());

        Collection<? extends JdkArtifact> results = autoJdk().findArtifactsInAllRepositories(jdkSearchRequest);

        //Organize into platforms
        Multimap<Platform, JdkArtifact> resultMap = ArrayListMultimap.create();

        for (JdkArtifact result : results)
        {
            Platform platform = new Platform(result.getOperatingSystem(), platformTools.canonicalArchitecture(result.getArchitecture()));
            resultMap.put(platform, result);
        }

        List<Platform> platforms = resultMap.keySet().stream().sorted(Comparator.comparing(Platform::toString)).collect(Collectors.toUnmodifiableList());
        for (Platform platform : platforms)
        {
            Collection<JdkArtifact> resultsForPlatform = resultMap.get(platform);

            Map<String, List<JdkArtifact>> resultsForPlatformByVendor = resultsForPlatform.stream().collect(Collectors.groupingBy(JdkArtifact::getVendor));

            List<String> sortedVendors = resultsForPlatformByVendor.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toUnmodifiableList());
            StringBuilder buf = new StringBuilder();
            for (String vendor : sortedVendors)
            {
                List<JdkArtifact> vendorResults = resultsForPlatformByVendor.get(vendor);
                JdkArtifact bestVendorResult = vendorResults.stream().max(autoJdk().jdkComparator()).get();
                long distinctVersions = vendorResults.stream().map(JdkArtifact::getVersion).distinct().count();

                if (buf.length() > 0)
                    buf.append(", ");

                buf.append(vendor);
                buf.append("(").append(distinctVersions).append("):");
                buf.append(bestVendorResult.getVersion());
            }

            getLog().info(platform + " - " + buf);
        }
    }
}
