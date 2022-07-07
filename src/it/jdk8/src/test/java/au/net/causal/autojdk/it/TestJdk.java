package au.net.causal.autojdk.it;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.*;

class TestJdk
{
    @Test
    void javaHomeIsFromAutoJdkInstallationDirectory()
    {
        Path javaHomeDirectory = Paths.get(System.getProperty("java.home"));

        assertThat(javaHomeDirectory).isDirectory();

        //Some versions of JDK have a jre subdirectory which is what the java home is, but some not
        if (javaHomeDirectory.endsWith(Paths.get("jre")))
            javaHomeDirectory = javaHomeDirectory.getParent();

        //Expect AutoJDK to install here
        //though .m2 might be from target/it/userhome and not our real .m2 because of integration test setup
        assertThat(javaHomeDirectory.getParent()).endsWith(Paths.get(".m2", "autojdk", "jdks"));
    }

    @Test
    void jvmVersion()
    {
        assertThat(ManagementFactory.getRuntimeMXBean().getSpecVersion()).isEqualTo("1.8");
    }
}
