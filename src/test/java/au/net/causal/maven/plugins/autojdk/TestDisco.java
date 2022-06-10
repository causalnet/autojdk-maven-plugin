package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.Bitness;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.PackageType;
import eu.hansolo.jdktools.ReleaseStatus;
import eu.hansolo.jdktools.versioning.Semver;
import eu.hansolo.jdktools.versioning.VersionNumber;
import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.pkg.Distribution;
import io.foojay.api.discoclient.pkg.Pkg;
import io.foojay.api.discoclient.util.Helper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

class TestDisco
{
    @Test
    void test()
    throws Exception
    {
        DiscoClient discoClient = new DiscoClient();

        /*
        List<Distribution> distributions = discoClient.getDistributions();
        distributions.forEach(System.out::println);

        System.out.println("---------------------------------------");

        Set<Distribution> dists = discoClient.getDistributionsForSemver(new Semver(new VersionNumber(17), ReleaseStatus.GA));
        dists.forEach(System.out::println);


         */
        System.out.println("---------------------------------------");

        //List<Pkg> pkgResult = discoClient.getPkgs(null, new VersionNumber(17), null, OperatingSystem.WINDOWS, null, Architecture.AMD64, Bitness.BIT_64, null, PackageType.JDK, null, true, null, null, null, null, null);
        List<Pkg> pkgResult = discoClient.getPkgs(null, new VersionNumber(17), Latest.AVAILABLE, OperatingSystem.WINDOWS, null, Architecture.AMD64, Bitness.BIT_64, null, PackageType.JDK, null, true, null, null, null, null, null);
        //List<Pkg> pkgResult = discoClient.getPkgs(null, new VersionNumber(17), Latest.ALL_OF_VERSION, OperatingSystem.WINDOWS, null, Architecture.AMD64, Bitness.BIT_64, null, PackageType.JDK, null, true, null, null, null, null, null);
        System.out.println(pkgResult);


        for (Pkg r : pkgResult)
        {
            System.out.println(r.getDistributionName() + ": ");
            //System.out.println(discoClient.getPkgDirectDownloadUri(r.getId()));
            System.out.println(r);


        }
    }

    @Test
    void testInitialization() throws InterruptedException
    {
        DiscoClient discoClient = new DiscoClient();

        //Wait for client to be initialized
        while (!discoClient.isInitialzed())
        {
            Thread.sleep(500L);
        }
        System.out.println("Now initialized");

        Map<String, Distribution> distros = discoClient.getDistros();
        System.out.println(distros);
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
