package au.net.causal.maven.plugins.autojdk.foojay;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

class TestOfflineDistributions
{
    private final OfflineDistributionsVendorService offlineDistributionsVendorService = new OfflineDistributionsVendorService();

    @Disabled
    @Test
    void diffOfflineAndLiveVendors()
    throws Exception
    {
        FoojayClient client = new FoojayClient();

        //Read existing vendors from offline resource
        List<String> existingVendors = offlineDistributionsVendorService.getAllVendors();

        //Read vendors from live service
        List<? extends JdkDistribution> result = client.getDistributions(false, false, null);
        List<String> liveVendors = result.stream().map(JdkDistribution::getApiParameter).collect(Collectors.toList());

        //Compare
        List<String> noLongerInLive = new ArrayList<>(existingVendors);
        noLongerInLive.removeAll(liveVendors);
        Collections.sort(noLongerInLive);

        List<String> newInLive = new ArrayList<>(liveVendors);
        newInLive.removeAll(existingVendors);
        Collections.sort(newInLive);

        if (!noLongerInLive.isEmpty())
            System.out.println("No longer in live: " + noLongerInLive);
        if (!newInLive.isEmpty())
            System.out.println("New in live (" + newInLive.size() + "): " + newInLive);

        if (!noLongerInLive.isEmpty() || !newInLive.isEmpty())
        {
            //Remove extra stuff we don't need and sort
            result.forEach(it -> it.getOtherProperties().clear());
            result.sort(Comparator.comparing(JdkDistribution::getApiParameter));

            //Print out so we can copy+paste into our own distributions.json
            System.out.println("------- New suggested distributions.json -------");
            System.out.println(client.getApiClient().getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
            System.out.println("------------------------------------------------");
        }
    }
}
