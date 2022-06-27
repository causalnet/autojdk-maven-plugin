package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TestVersionTools
{
    @Test
    void major()
    {
        ArtifactVersion version = new DefaultArtifactVersion("8");
        boolean isMajor = VersionTools.isMajorVersionOnly(version);
        assertThat(isMajor).isTrue();
    }

    @Test
    void majorMinor()
    {
        ArtifactVersion version = new DefaultArtifactVersion("8.1");
        boolean isMajor = VersionTools.isMajorVersionOnly(version);
        assertThat(isMajor).isFalse();
    }

    @Test
    void majorMinorWithZero()
    {
        ArtifactVersion version = new DefaultArtifactVersion("8.0");
        boolean isMajor = VersionTools.isMajorVersionOnly(version);
        assertThat(isMajor).isFalse();
    }

    @Test
    void majorMinorIncrement()
    {
        ArtifactVersion version = new DefaultArtifactVersion("8.1.2");
        boolean isMajor = VersionTools.isMajorVersionOnly(version);
        assertThat(isMajor).isFalse();
    }

    @Test
    void majorQualifier()
    {
        ArtifactVersion version = new DefaultArtifactVersion("8-beta");
        boolean isMajor = VersionTools.isMajorVersionOnly(version);
        assertThat(isMajor).isFalse();
    }

    @Test
    void majorBuildNumber()
    {
        ArtifactVersion version = new DefaultArtifactVersion("8-123");
        boolean isMajor = VersionTools.isMajorVersionOnly(version);
        assertThat(isMajor).isFalse();
    }
}
