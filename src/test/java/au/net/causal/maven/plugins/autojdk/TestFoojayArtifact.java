package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TestFoojayArtifact extends AbstractDiscoTestCase
{
    @Nested
    class MavenSafeVersionFromPkgVersion
    {
        @Test
        void test()
        {
            ArtifactVersion version = FoojayArtifact.mavenSafeVersionFromPkgVersion("17.0.3-7");

            assertThat(version.getMajorVersion()).isEqualTo(17);
            assertThat(version.getMinorVersion()).isEqualTo(0);
            assertThat(version.getIncrementalVersion()).isEqualTo(3);
            assertThat(version.getBuildNumber()).isEqualTo(7);
        }

        @Test
        void test2()
        {
            ArtifactVersion version = FoojayArtifact.mavenSafeVersionFromPkgVersion("11.0.9.1-1");

            assertThat(version.getMajorVersion()).isEqualTo(11);
            assertThat(version.getMinorVersion()).isEqualTo(0);
            assertThat(version.getIncrementalVersion()).isEqualTo(9);
            assertThat(version.getQualifier()).isEqualTo("1-1");
        }
    }

}
