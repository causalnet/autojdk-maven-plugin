import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import java.nio.file.Path

class TestJdk {
    @Test
    fun javaHomeIsFromAutoJdkInstallationDirectory() {
        val javaHomeDirectory = Path.of(System.getProperty("java.home"))
        assertThat(javaHomeDirectory).isDirectory

        //Expect AutoJDK to install here
        //though .m2 might be from target/it/userhome and not our real .m2 because of integration test setup
        assertThat(javaHomeDirectory.parent).endsWith(Path.of(".m2", "autojdk", "jdks"))
    }

    @Test
    fun jvmVersion() {
        assertThat(ManagementFactory.getRuntimeMXBean().specVersion).isEqualTo("18")
    }

    @Test
    fun usingJava18OnlyCode() {
        //charset() is JDK18+ so this tests whether the Kotlin compiler is using Java 18 from autojdk instead of the default
        //If autojdk is not configuring Kotlin compiler with Java 18 then compilation will fail here if the host JDK is < 18
        assertThat(System.out.charset()).isNotNull
    }
}
