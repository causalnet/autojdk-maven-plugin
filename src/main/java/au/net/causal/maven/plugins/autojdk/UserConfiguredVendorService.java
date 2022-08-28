package au.net.causal.maven.plugins.autojdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Allows querying of all known vendors that uses the AutoJDK configuration to handle user preference selection and ordering.
 */
public class UserConfiguredVendorService implements VendorService
{
    private final VendorService vendorSource;
    private final AutoJdkConfiguration autoJdkConfiguration;

    public UserConfiguredVendorService(VendorService vendorSource, AutoJdkConfiguration autoJdkConfiguration)
    {
        this.vendorSource = Objects.requireNonNull(vendorSource);
        this.autoJdkConfiguration = Objects.requireNonNull(autoJdkConfiguration);
    }

    /**
     * @return an ordered list of all known JDK vendors used to search for available JDKs.  Entries earlier in the list are preferred to later ones.
     */
    @Override
    public List<String> getAllVendors()
    throws VendorServiceException
    {
        List<String> vendors = new ArrayList<>(autoJdkConfiguration.getVendors());

        int wildcardIndex = vendors.indexOf(AutoJdkConfiguration.WILDCARD_VENDOR); //First index of the wildcard entry
        vendors.removeIf(AutoJdkConfiguration.WILDCARD_VENDOR::equals); //Use removeIf() to remove _all_ wildcard entries, just in case user specified more than once

        //Replace wildcard entry with all remaining vendors
        if (wildcardIndex >= 0)
        {
            List<String> allKnownVendors = new ArrayList<>(vendorSource.getAllVendors());
            allKnownVendors.removeAll(vendors);  //Don't re-add vendors already in the list
            vendors.addAll(wildcardIndex, allKnownVendors);
        }

        return vendors;
    }
}
