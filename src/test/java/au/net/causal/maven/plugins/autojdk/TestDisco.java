package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.Bitness;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.PackageType;
import eu.hansolo.jdktools.versioning.VersionNumber;
import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.pkg.Distribution;
import io.foojay.api.discoclient.pkg.Pkg;
import io.foojay.api.discoclient.util.Helper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;

/**
 * Test Foojay disco client but use Wiremock playback to avoid smashing their server in our tests.
 * When run with {@link AbstractDiscoTestCase#WIREMOCK_RECORD} as false (the default) it will use a previously recorded session and avoid
 * hitting their servers at all.  Turning it to true will instead hit the real server and also record responses that can be played back later.
 * Recording may be required when changing test code since different services may be hit.
 */
class TestDisco extends AbstractDiscoTestCase
{
    /**
     * Tests reading distributions.
     */
    @Test
    void testGetDistributions()
    {
        DiscoClient discoClient = new DiscoClient();
        List<Distribution> distributions = discoClient.getDistributions();
        distributions.forEach(System.out::println);

        //Do it twice to check that wiremock is mocking multi-calls properly
        distributions = discoClient.getDistributions();
        distributions.forEach(System.out::println);
    }

    /**
     * Get distributions from the disco client locally, ensuring its funny async initialization is completed.
     */
    @Test
    void testInitialization()
    {
        DiscoClient discoClient = new DiscoClient();

        //Wait for client to be initialized - this call awaits distributions to be initialized
        //If you don't do this you get a race condition where if you don't wait for the async stuff the return map is empty
        DiscoClient.getDistributionFromText("");

        //Now pull in
        Map<String, Distribution> distros = discoClient.getDistros();
        System.out.println(distros);
        assertThat(distros).isNotEmpty();
    }

    /**
     * Read latest available Java 17 versions from all vendors for Windows 64-bit.
     */
    @Test
    void testGetPackagesAvailable()
    {
        DiscoClient discoClient = new DiscoClient();

        List<Pkg> pkgResult = discoClient.getPkgs(null, new VersionNumber(17), Latest.AVAILABLE, OperatingSystem.WINDOWS, null, Architecture.AMD64, Bitness.BIT_64, null, PackageType.JDK, null, true, null, null, null, null, null);
        for (Pkg r : pkgResult)
        {
            System.out.println(r.getDistributionName() + ": ");
            System.out.println(r);
        }
    }

    /**
     * Read all available Java 17 versions from all vendors for Windows 64-bit.
     */
    @Test
    void testGetPackagesAll()
    {
        DiscoClient discoClient = new DiscoClient();

        List<Pkg> pkgResult = discoClient.getPkgs(null, new VersionNumber(17), Latest.ALL_OF_VERSION, OperatingSystem.WINDOWS, null, Architecture.AMD64, Bitness.BIT_64, null, PackageType.JDK, null, true, null, null, null, null, null);
        assertThat(pkgResult).isNotEmpty();
        for (Pkg r : pkgResult)
        {
            System.out.println(r.getDistributionName() + ": ");
            System.out.println(r);
        }

        System.out.println("--------------------------------------------------------");

        pkgResult.stream().filter(pkg -> pkg.getDistributionName().equals("ORACLE_OPEN_JDK")).forEach(pkg ->
        {
            System.out.println(pkg.getDistributionName() + ": ");
            System.out.println(pkg);
        });
    }

    @Test
    void testPreloadWithoutNetwork() throws ExecutionException, InterruptedException
    {
        //Could use these (local only) for groupId/artifactId generation for known JDKs
        Map<String, Distribution> dists = Helper.preloadDistributions().get();
        for (Map.Entry<String, Distribution> entry : dists.entrySet())
        {
            System.out.println(entry.getKey() + ":");
            System.out.println(entry.getValue());
        }
    }

    @Test
    void currentOs()
    {
        OperatingSystem os = eu.hansolo.jdktools.util.Helper.getOperatingSystem();
        System.out.println("Current OS: " + os);
        Architecture arch = eu.hansolo.jdktools.util.Helper.getArchitecture();
        System.out.println("Arch: " + arch);
    }
}
