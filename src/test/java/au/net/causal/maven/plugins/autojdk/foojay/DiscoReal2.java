package au.net.causal.maven.plugins.autojdk.foojay;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.ArchiveType;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.ReleaseStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

class DiscoReal2
{
    @Disabled
    @Test
    void test()
    throws Exception
    {
        FoojayClient client = new FoojayClient();

        List<? extends JdkPackage> results = client.getJdkPackages(null, 11, null, null, List.of(Architecture.X64), null, List.of(ArchiveType.ZIP, ArchiveType.TAR_GZ), List.of(OperatingSystem.WINDOWS), null, null, List.of(ReleaseStatus.GA, ReleaseStatus.EA), null, null,
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

        List<? extends MajorVersion> result = client.getAllMajorVersions(null, null, null, null, null, null, false);

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
}
