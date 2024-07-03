package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.foojay.OfflineDistributionsVendorService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TestUserConfiguredVendorService
{
    private static final Logger log = LoggerFactory.getLogger(TestUserConfiguredVendorService.class);

    /**
     * Vendor service that retrieves all known vendors.  The user configured vendor service wrapper performs ordering and filtering which is what we are testing.
     */
    private final VendorService sourceVendorService = new OfflineDistributionsVendorService();

    @Test
    void allVendors()
    throws VendorServiceException
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(null, List.of(), List.of(AutoJdkConfiguration.WILDCARD_VENDOR), List.of(), AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY);
        VendorService vendorService = new UserConfiguredVendorService(sourceVendorService, config);

        List<String> vendors = vendorService.getAllVendors();

        log.debug("Vendors: " + vendors);

        //Ensure we have a few vendors in the list (source is from classpath, so might change a bit when dependencies change)
        assertThat(vendors).hasSizeGreaterThan(10)
                           .doesNotHaveDuplicates()
                           .doesNotContain(AutoJdkConfiguration.WILDCARD_VENDOR);
    }

    @Test
    void somePreferred()
    throws VendorServiceException
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(null, List.of(), List.of(
                "zulu",
                "liberica",
                AutoJdkConfiguration.WILDCARD_VENDOR
        ), List.of(), AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY);
        VendorService vendorService = new UserConfiguredVendorService(sourceVendorService, config);

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
    throws VendorServiceException
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(null, List.of(), List.of(
                "zulu",
                "galah",
                "liberica",
                AutoJdkConfiguration.WILDCARD_VENDOR
        ), List.of(), AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY);
        VendorService vendorService = new UserConfiguredVendorService(sourceVendorService, config);

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
    throws VendorServiceException
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(null, List.of(), List.of(
                "zulu",
                "liberica"
        ), List.of(), AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY);
        VendorService vendorService = new UserConfiguredVendorService(sourceVendorService, config);

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
    throws VendorServiceException
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(null, List.of(), List.of(
                "zulu",
                AutoJdkConfiguration.WILDCARD_VENDOR,
                AutoJdkConfiguration.WILDCARD_VENDOR
        ), List.of(), AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY);
        VendorService vendorService = new UserConfiguredVendorService(sourceVendorService, config);

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
    throws VendorServiceException
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(null, List.of(), List.of(
                AutoJdkConfiguration.WILDCARD_VENDOR,
                "liberica"
        ), List.of(), AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY);
        VendorService vendorService = new UserConfiguredVendorService(sourceVendorService, config);

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
    throws VendorServiceException
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration(null, List.of(), List.of(AutoJdkConfiguration.WILDCARD_VENDOR), List.of(), AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY);
        VendorService vendorService = new UserConfiguredVendorService(sourceVendorService, config);

        List<String> knownVendors = vendorService.getAllVendors();

        List<String> defaultVendorsWithoutWildcard = new ArrayList<>(AutoJdkConfiguration.DEFAULT_VENDORS);
        defaultVendorsWithoutWildcard.remove(AutoJdkConfiguration.WILDCARD_VENDOR);

        assertThat(knownVendors).containsAll(defaultVendorsWithoutWildcard);
    }
}
