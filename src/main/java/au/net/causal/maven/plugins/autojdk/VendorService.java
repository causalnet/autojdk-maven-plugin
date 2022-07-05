package au.net.causal.maven.plugins.autojdk;

import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.pkg.Distribution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Allows querying of all known vendors.
 */
public class VendorService
{
    private final DiscoClient discoClient;
    private final AutoJdkConfiguration autoJdkConfiguration;

    public VendorService(DiscoClient discoClient, AutoJdkConfiguration autoJdkConfiguration)
    {
        this.discoClient = Objects.requireNonNull(discoClient);
        this.autoJdkConfiguration = Objects.requireNonNull(autoJdkConfiguration);
    }

    /**
     * @return an ordered list of all known JDK vendors used to search for available JDKs.  Entries earlier in the list are preferred to later ones.
     */
    public List<String> getAllVendors()
    {

        List<String> vendors = new ArrayList<>(autoJdkConfiguration.getVendors());

        int wildcardIndex = vendors.indexOf(AutoJdkConfiguration.WILDCARD_VENDOR); //First index of the wildcard entry
        vendors.removeIf(AutoJdkConfiguration.WILDCARD_VENDOR::equals); //Use removeIf() to remove _all_ wildcard entries, just in case user specified more than once

        //Replace wildcard entry with all remaining vendors
        if (wildcardIndex >= 0)
        {
            List<String> allKnownVendors = discoClient.getDistros().values()
                                                                   .stream()
                                                                   .sorted(Comparator.comparing(Distribution::getApiString)) //from a map, so use sorting for consistency
                                                                   .map(Distribution::getApiString)
                                                                   .collect(Collectors.toList());
            allKnownVendors.removeAll(vendors);  //Don't re-add vendors already in the list
            vendors.addAll(wildcardIndex, allKnownVendors);
        }

        return vendors;
    }

}
