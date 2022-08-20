package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.Bitness;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.PackageType;
import eu.hansolo.jdktools.ReleaseStatus;
import eu.hansolo.jdktools.versioning.VersionNumber;
import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.pkg.Pkg;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * For experimenting with the real disco service during development.  Don't run these tests normally.
 */
class DiscoReal
{
    private static final Logger log = LoggerFactory.getLogger(DiscoReal.class);

    @Test
    @Disabled
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

    //Use this one to generate a well-known platform list
    @Test
    @Disabled
    void generateWellKnownPlatforms()
    {
        DiscoClient discoClient = DiscoClientSingleton.discoClient();
        PlatformTools platformTools = new PlatformTools();

        Set<OperatingSystem> oses = EnumSet.noneOf(OperatingSystem.class);
        Set<Architecture> arcs = EnumSet.noneOf(Architecture.class);
        Set<Platform> platforms = new LinkedHashSet<>();

        List<VersionNumber> versions = IntStream.rangeClosed(8, 19).mapToObj(VersionNumber::new).collect(Collectors.toList());

        for (VersionNumber version : versions)
        {
            List<Pkg> pkgResult = discoClient.getPkgs(null, version, Latest.ALL_OF_VERSION, null /* os */, null, null /*arc */, null /*bitness */, null, PackageType.JDK, null, true, List.of(ReleaseStatus.EA, ReleaseStatus.GA), null, null, null, null);

            pkgResult.sort(Comparator.comparing(Pkg::getDistributionName));

            for (Pkg r : pkgResult)
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
