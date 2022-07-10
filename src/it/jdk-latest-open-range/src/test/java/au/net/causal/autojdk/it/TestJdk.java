package au.net.causal.autojdk.it;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class TestJdk
{
    @Test
    void javaHomeIsFromAutoJdkInstallationDirectory()
    {
        Path javaHomeDirectory = Path.of(System.getProperty("java.home"));

        assertThat(javaHomeDirectory).isDirectory();

        //Expect AutoJDK to install here
        //though .m2 might be from target/it/userhome and not our real .m2 because of integration test setup
        assertThat(javaHomeDirectory.getParent()).endsWith(Path.of(".m2", "autojdk", "jdks"));
    }

    @Test
    void jvmVersion()
    {
        String javaVersion = ManagementFactory.getRuntimeMXBean().getSpecVersion();
        double specVersionNumber = Double.parseDouble(javaVersion);

        //Should pick latest Java version available, which might change as time goes on
        //As of writing, 18 is the latest release version of Java so check it is at least that
        assertThat(specVersionNumber).isGreaterThanOrEqualTo(18);
    }
}
