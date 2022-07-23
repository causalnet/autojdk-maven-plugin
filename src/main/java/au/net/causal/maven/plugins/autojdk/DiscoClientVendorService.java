package au.net.causal.maven.plugins.autojdk;

import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.pkg.Distribution;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reads all vendors using the Foojay disco client.  This reads vendors from an online resource.
 */
public class DiscoClientVendorService implements VendorService
{
    private final DiscoClient discoClient;

    public DiscoClientVendorService(DiscoClient discoClient)
    {
        this.discoClient = Objects.requireNonNull(discoClient);
    }

    @Override
    public List<String> getAllVendors()
    {
        return foojayDistributionsToVendorList(discoClient.getDistros().values());
    }

    /**
     * Turns Foojay distributions into a default-sorted list of vendor names.
     *
     * @param distributions Foojay distributions.
     *
     * @return a list of vendor names.
     */
    static List<String> foojayDistributionsToVendorList(Collection<? extends Distribution> distributions)
    {
        return distributions.stream()
                            .sorted(Comparator.comparing(Distribution::getApiString)) //from a map, so use sorting for consistency
                            .map(Distribution::getApiString)
                            .collect(Collectors.toList());
    }
}
