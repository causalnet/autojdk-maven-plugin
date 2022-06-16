package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.MavenJdkArtifact.OperatingSystemAndArchitecture;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestMavenJdkArtifact
{
    @Nested
    class MakeClassifier
    {
        @Test
        void windowsX64()
        {
            String classifier = MavenJdkArtifact.makeClassifier(OperatingSystem.WINDOWS, Architecture.X64);
            assertThat(classifier).isEqualTo("windows-x64");
        }

        @Test
        void linuxX64()
        {
            String classifier = MavenJdkArtifact.makeClassifier(OperatingSystem.LINUX, Architecture.X64);
            assertThat(classifier).isEqualTo("linux-x64");
        }

        @Test
        void linuxAarch64()
        {
            String classifier = MavenJdkArtifact.makeClassifier(OperatingSystem.LINUX, Architecture.AARCH64);
            assertThat(classifier).isEqualTo("linux-aarch64");
        }
    }

    @Nested
    class ParseClassifier
    {
        @Test
        void windowsX64()
        {
            OperatingSystemAndArchitecture result = MavenJdkArtifact.parseClassifier("windows-x64");
            assertThat(result.getOperatingSystem()).isEqualTo(OperatingSystem.WINDOWS);
            assertThat(result.getArchitecture()).isEqualTo(Architecture.X64);
        }

        @Test
        void linuxX64()
        {
            OperatingSystemAndArchitecture result = MavenJdkArtifact.parseClassifier("linux-x64");
            assertThat(result.getOperatingSystem()).isEqualTo(OperatingSystem.LINUX);
            assertThat(result.getArchitecture()).isEqualTo(Architecture.X64);
        }

        @Test
        void linuxAArch64()
        {
            OperatingSystemAndArchitecture result = MavenJdkArtifact.parseClassifier("linux-aarch64");
            assertThat(result.getOperatingSystem()).isEqualTo(OperatingSystem.LINUX);
            assertThat(result.getArchitecture()).isEqualTo(Architecture.AARCH64);
        }
    }
}
