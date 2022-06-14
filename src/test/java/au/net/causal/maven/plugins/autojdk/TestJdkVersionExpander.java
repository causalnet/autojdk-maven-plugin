package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TestJdkVersionExpander
{
    private final JdkVersionExpander expander = new JdkVersionExpander();

    @Test
    void withQualifier()
    {
        List<? extends ArtifactVersion> results = expander.expandVersions(new DefaultArtifactVersion("17.0.2-20-ga"));

        assertThat(new ArrayList<ArtifactVersion>(results)).containsExactly(
                new DefaultArtifactVersion("17.0.2-20-ga"),
                new DefaultArtifactVersion("17.0.2"),
                new DefaultArtifactVersion("17.0"),
                new DefaultArtifactVersion("17")
        );
    }

    @Test
    void withBuildNumber()
    {
        List<? extends ArtifactVersion> results = expander.expandVersions(new DefaultArtifactVersion("17.0.2-20"));

        assertThat(new ArrayList<ArtifactVersion>(results)).containsExactly(
                new DefaultArtifactVersion("17.0.2-20"),
                new DefaultArtifactVersion("17.0.2"),
                new DefaultArtifactVersion("17.0"),
                new DefaultArtifactVersion("17")
        );
    }

    @Test
    void withoutQualifierOrBuildNumber()
    {
        List<? extends ArtifactVersion> results = expander.expandVersions(new DefaultArtifactVersion("17.0.2"));

        assertThat(new ArrayList<ArtifactVersion>(results)).containsExactly(
                new DefaultArtifactVersion("17.0.2"),
                new DefaultArtifactVersion("17.0"),
                new DefaultArtifactVersion("17")
        );
    }
}
