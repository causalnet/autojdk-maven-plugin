package au.net.causal.maven.plugins.autojdk;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TestVendorService
{
    private static final Logger log = LoggerFactory.getLogger(TestVendorService.class);

    @Test
    void allVendors()
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(List.of(AutoJdkConfiguration.WILDCARD_VENDOR));
        VendorService vendorService = new VendorService(DiscoClientSingleton.discoClient(), config);

        List<String> vendors = vendorService.getAllVendors();

        log.debug("Vendors: " + vendors);

        //Ensure we have a few vendors in the list (source is from classpath, so might change a bit when dependencies change)
        assertThat(vendors).hasSizeGreaterThan(10)
                           .doesNotHaveDuplicates()
                           .doesNotContain(AutoJdkConfiguration.WILDCARD_VENDOR);
    }

    @Test
    void somePreferred()
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(List.of(
                "zulu",
                "liberica",
                AutoJdkConfiguration.WILDCARD_VENDOR
        ));
        VendorService vendorService = new VendorService(DiscoClientSingleton.discoClient(), config);

        List<String> vendors = vendorService.getAllVendors();

        log.debug("Vendors: " + vendors);

        //Ensure we have a few vendors in the list (source is from classpath, so might change a bit when dependencies change)
        assertThat(vendors).hasSizeGreaterThan(10)
                           .doesNotHaveDuplicates()
                           .doesNotContain(AutoJdkConfiguration.WILDCARD_VENDOR)

        //Check that preferred vendors are at the top of the list
                           .startsWith("zulu", "liberica");
    }

    @Test
    void extraVendorsNotInDisco()
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(List.of(
                "zulu",
                "galah",
                "liberica",
                AutoJdkConfiguration.WILDCARD_VENDOR
        ));
        VendorService vendorService = new VendorService(DiscoClientSingleton.discoClient(), config);

        List<String> vendors = vendorService.getAllVendors();

        log.debug("Vendors: " + vendors);

        //Ensure we have a few vendors in the list (source is from classpath, so might change a bit when dependencies change)
        assertThat(vendors).hasSizeGreaterThan(10)
                           .doesNotHaveDuplicates()
                           .doesNotContain(AutoJdkConfiguration.WILDCARD_VENDOR)

                           //Check that preferred vendors are at the top of the list
                           .startsWith("zulu", "galah", "liberica");
    }

    @Test
    void noWildcard()
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(List.of(
                "zulu",
                "liberica"
        ));
        VendorService vendorService = new VendorService(DiscoClientSingleton.discoClient(), config);

        List<String> vendors = vendorService.getAllVendors();

        log.debug("Vendors: " + vendors);

        //Should only have exactly what we specified
        assertThat(vendors).containsExactly("zulu", "liberica");
    }

    /**
     * Only the first wildcard is expanded, the rest are ignored.
     */
    @Test
    void multipleWildcards()
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(List.of(
                "zulu",
                AutoJdkConfiguration.WILDCARD_VENDOR,
                AutoJdkConfiguration.WILDCARD_VENDOR
        ));
        VendorService vendorService = new VendorService(DiscoClientSingleton.discoClient(), config);

        List<String> vendors = vendorService.getAllVendors();

        log.debug("Vendors: " + vendors);

        //Ensure we have a few vendors in the list (source is from classpath, so might change a bit when dependencies change)
        assertThat(vendors).hasSizeGreaterThan(10)
                           .doesNotHaveDuplicates()
                           .doesNotContain(AutoJdkConfiguration.WILDCARD_VENDOR)

                           //Check that preferred vendors are at the top of the list
                           .startsWith("zulu");
    }

    /**
     * Putting wildcard before a specific vendor means prefer every other vendor.
     */
    @Test
    void wildcardNotAtEnd()
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(List.of(
                AutoJdkConfiguration.WILDCARD_VENDOR,
                "liberica"
        ));
        VendorService vendorService = new VendorService(DiscoClientSingleton.discoClient(), config);

        List<String> vendors = vendorService.getAllVendors();

        log.debug("Vendors: " + vendors);

        //Ensure we have a few vendors in the list (source is from classpath, so might change a bit when dependencies change)
        assertThat(vendors).hasSizeGreaterThan(10)
                           .doesNotHaveDuplicates()
                           .doesNotContain(AutoJdkConfiguration.WILDCARD_VENDOR)

                           //Check that preferred vendors are at the top of the list
                           .endsWith("liberica");
    }

    /**
     * Sanity check to make sure all the default vendors actually exist.
     */
    @Test
    void ensureAllDefaultVendorsAreRealVendors()
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(List.of(AutoJdkConfiguration.WILDCARD_VENDOR));
        VendorService vendorService = new VendorService(DiscoClientSingleton.discoClient(), config);

        List<String> knownVendors = vendorService.getAllVendors();

        List<String> defaultVendorsWithoutWildcard = new ArrayList<>(AutoJdkConfiguration.DEFAULT_VENDORS);
        defaultVendorsWithoutWildcard.remove(AutoJdkConfiguration.WILDCARD_VENDOR);

        assertThat(knownVendors).containsAll(defaultVendorsWithoutWildcard);
    }
}
