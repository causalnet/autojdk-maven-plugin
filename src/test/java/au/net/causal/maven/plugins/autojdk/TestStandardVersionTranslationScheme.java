package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TestStandardVersionTranslationScheme
{
    @Nested
    class Unmodified
    {
        private final StandardVersionTranslationScheme scheme = StandardVersionTranslationScheme.UNMODIFIED;

        @Test
        void expand()
        {
            ArtifactVersion jdkVersion = new DefaultArtifactVersion("17.0.2");
            List<ArtifactVersion> results = List.copyOf(scheme.expandJdkVersionForRegistration(jdkVersion));
            assertThat(results).containsExactlyInAnyOrder(new DefaultArtifactVersion("17.0.2"));
        }

        @Test
        void translate() throws InvalidVersionSpecificationException
        {
            VersionRange range = VersionRange.createFromVersionSpec("17");
            VersionRange result = scheme.translateProjectRequiredJdkVersionToSearchCriteria(range);
            assertThat(result).isEqualTo(VersionRange.createFromVersionSpec("17"));
        }
    }

    @Nested
    class MajorAndFull
    {
        private final StandardVersionTranslationScheme scheme = StandardVersionTranslationScheme.MAJOR_AND_FULL;

        @Test
        void expand()
        {
            ArtifactVersion jdkVersion = new DefaultArtifactVersion("17.0.2");
            List<ArtifactVersion> results = List.copyOf(scheme.expandJdkVersionForRegistration(jdkVersion));
            assertThat(results).containsExactlyInAnyOrder(
                    new DefaultArtifactVersion("17"),
                    new DefaultArtifactVersion("17.0.2")
            );
        }

        @Test
        void translateMajor() throws InvalidVersionSpecificationException
        {
            VersionRange range = VersionRange.createFromVersionSpec("17");
            VersionRange result = scheme.translateProjectRequiredJdkVersionToSearchCriteria(range);
            assertThat(result).isEqualTo(VersionRange.createFromVersionSpec("[17,18)"));
        }

        @Test
        void translateNonMajor() throws InvalidVersionSpecificationException
        {
            VersionRange range = VersionRange.createFromVersionSpec("17.0.2");
            VersionRange result = scheme.translateProjectRequiredJdkVersionToSearchCriteria(range);
            assertThat(result).isEqualTo(VersionRange.createFromVersionSpec("17.0.2"));
        }
    }
}
