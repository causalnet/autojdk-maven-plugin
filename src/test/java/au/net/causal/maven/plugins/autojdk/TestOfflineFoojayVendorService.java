package au.net.causal.maven.plugins.autojdk;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TestOfflineFoojayVendorService
{
    private static final Logger log = LoggerFactory.getLogger(TestOfflineFoojayVendorService.class);

    private final VendorService vendorService = new OfflineFoojayVendorService();

    @Test
    void readAllVendors()
    {
        List<String> vendors = vendorService.getAllVendors();

        log.debug("All vendors: " + vendors);

        //This can change between Foojay releases, just make sure there's a few in there and has some known ones
        assertThat(vendors).hasSizeGreaterThan(10)
                           .contains("zulu", "liberica", "corretto");
    }
}
