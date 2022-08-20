package au.net.causal.autojdk.it;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class TestJdk
{
    private static final String expectedJdkVersion = System.getProperty("expected.jdk.version", "17");

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
        assertThat(ManagementFactory.getRuntimeMXBean().getSpecVersion()).isEqualTo(expectedJdkVersion);
    }
}