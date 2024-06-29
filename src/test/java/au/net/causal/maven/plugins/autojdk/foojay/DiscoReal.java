package au.net.causal.maven.plugins.autojdk.foojay;

import au.net.causal.maven.plugins.autojdk.Platform;
import au.net.causal.maven.plugins.autojdk.PlatformTools;
import au.net.causal.maven.plugins.autojdk.foojay.openapi.handler.ApiException;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.ArchiveType;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.ReleaseStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

class DiscoReal
{
    private static final Logger log = LoggerFactory.getLogger(DiscoReal.class);

    @Disabled
    @Test
    void test()
    throws Exception
    {
        FoojayClient client = new FoojayClient();

        List<? extends JdkPackage> results = client.getJdkPackages(null, 6, null, null, List.of(Architecture.X64), null, List.of(ArchiveType.ZIP, ArchiveType.TAR_GZ), List.of(OperatingSystem.WINDOWS, OperatingSystem.LINUX), null, null, List.of(ReleaseStatus.GA, ReleaseStatus.EA), null, null,
                                                                   Latest.ALL_OF_VERSION, null, true, null, null, null);



        for (Iterator<? extends JdkPackage> i = results.iterator(); i.hasNext();)
        {
            JdkPackage p = i.next();

            //Detect mismatch and filter out - is probably Graal
            if (p.getMajorVersion() != null && p.getJdkVersion() != null && !p.getMajorVersion().equals(p.getJdkVersion()))
            {
                System.out.println("*** removing mismatch: " + p);
                i.remove();
            }
        }

        System.out.println();
        for (JdkPackage result : results)
        {
            System.out.println(result);
        }
    }

    @Disabled
    @Test
    void test2()
    throws Exception
    {
        FoojayClient client = new FoojayClient();

        List<? extends MajorVersion> result = client.getAllMajorVersions();

        System.out.println(result);
    }

    @Disabled
    @Test
    void test3()
    throws Exception
    {
        FoojayClient client = new FoojayClient();

        List<? extends JdkDistribution> result = client.getDistributions(false, false, null);

        for (JdkDistribution r : result)
        {
            System.out.println(r);
        }

        //Wipe out extra properties
        result.forEach(it -> it.getOtherProperties().clear());

        System.out.println(client.getApiClient().getObjectMapper().writeValueAsString(result));
    }

    //Use this one to generate a well-known platform list
    @Test
    @Disabled
    void generateWellKnownPlatforms()
    throws ApiException
    {
        FoojayClient foojayClient = new FoojayClient();
        PlatformTools platformTools = new PlatformTools();

        Set<OperatingSystem> oses = EnumSet.noneOf(OperatingSystem.class);
        Set<Architecture> arcs = EnumSet.noneOf(Architecture.class);
        Set<Platform> platforms = new LinkedHashSet<>();

        int[] versions = IntStream.rangeClosed(8, 19).toArray();

        for (int version : versions)
        {
            List<? extends JdkPackage> pkgResult = foojayClient.getJdkPackages(null, version, null, null, null, null, null, null, null, null, List.of(ReleaseStatus.EA, ReleaseStatus.GA), null, null, Latest.ALL_OF_VERSION, null, true, null, null, null);

            pkgResult.sort(Comparator.comparing(JdkPackage::getDistribution));

            for (JdkPackage r : pkgResult)
            {
                //log.info(r.getDistributionName() + ": " + r);
                oses.add(platformTools.canonicalOperatingSystem(r.getOperatingSystem()));
                arcs.add(platformTools.canonicalArchitecture(r.getArchitecture()));
                platforms.add(new Platform(platformTools.canonicalOperatingSystem(r.getOperatingSystem()),
                        platformTools.canonicalArchitecture(r.getArchitecture())));
            }
        }

        log.info("Os: " + oses);
        log.info("Arcs: " + arcs);

        log.info("Platforms: " + platforms);
        log.info("(" + platforms.size() + ")");

        platforms.stream().sorted(Comparator.comparing(Platform::toString)).forEachOrdered(p -> log.info(p.toString()));

    }
}
