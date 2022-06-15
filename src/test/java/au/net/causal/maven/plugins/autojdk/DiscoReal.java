package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.Bitness;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.PackageType;
import eu.hansolo.jdktools.versioning.VersionNumber;
import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.pkg.Pkg;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

/**
 * For experimenting with the real disco service during development.  Don't run these tests normally.
 */
class DiscoReal
{
    private static final Logger log = LoggerFactory.getLogger(DiscoReal.class);

    //@Test
    void test()
    {
        DiscoClient discoClient = DiscoClientSingleton.discoClient();

        List<Pkg> pkgResult = discoClient.getPkgs(null, new VersionNumber(17), Latest.ALL_OF_VERSION, OperatingSystem.WINDOWS, null, Architecture.AMD64, Bitness.BIT_64, null, PackageType.JDK, null, true, null, null, null, null, null);

        pkgResult.sort(Comparator.comparing(Pkg::getDistributionName));

        for (Pkg r : pkgResult)
        {
            log.info(r.getDistributionName() + ": " + r);
        }
    }
}
