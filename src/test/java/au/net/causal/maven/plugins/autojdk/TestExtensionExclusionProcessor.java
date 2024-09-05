package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.xml.config.AutoJdkConfiguration.ExtensionExclusion;
import au.net.causal.maven.plugins.autojdk.ExtensionExclusionProcessor.ExclusionProcessorException;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TestExtensionExclusionProcessor
{
    @Nested
    class Processor
    {
        @Test
        void singleExclusionNoMatch()
        throws InvalidVersionSpecificationException, ExclusionProcessorException
        {
            ExtensionExclusionProcessor processor = new ExtensionExclusionProcessor(List.of(
                    new ExtensionExclusion("(,8)", "[8, 9)")
            ));

            VersionRange required = VersionRange.createFromVersionSpec("[8, 9)");
            ExtensionExclusion result = processor.checkExclusions(required);

            assertThat(result).isNull();
        }

        @Test
        void singleExclusionMatch()
        throws InvalidVersionSpecificationException, ExclusionProcessorException
        {
            ExtensionExclusionProcessor processor = new ExtensionExclusionProcessor(List.of(
                    new ExtensionExclusion("(,8)", "[8, 9)")
            ));

            VersionRange required = VersionRange.createFromVersionSpec("[6, 7)");
            ExtensionExclusion result = processor.checkExclusions(required);

            assertThat(result).isNotNull();
            assertThat(result.getSubstitution()).isEqualTo("[8, 9)");
        }
    }

    @Nested
    class Matches
    {
        private final ExtensionExclusionProcessor processor = new ExtensionExclusionProcessor(Collections.emptyList());

        @Test
        void twoRangesNoMatch()
        throws InvalidVersionSpecificationException
        {
            VersionRange required = VersionRange.createFromVersionSpec("[8,9)"); //version 8-9
            VersionRange exclusion = VersionRange.createFromVersionSpec("(,8)"); //anything less than version 8

            boolean result = processor.exclusionVersionMatchesRequirement(exclusion, required);

            assertThat(result).isFalse();
        }

        @Test
        void twoRangesMatch()
        throws InvalidVersionSpecificationException
        {
            VersionRange required = VersionRange.createFromVersionSpec("[7,8)"); //version 7-8
            VersionRange exclusion = VersionRange.createFromVersionSpec("(,8)"); //anything less than version 8

            boolean result = processor.exclusionVersionMatchesRequirement(exclusion, required);

            assertThat(result).isTrue();
        }

        @Test
        void twoOverlappingRangesMatch()
        throws InvalidVersionSpecificationException
        {
            VersionRange required = VersionRange.createFromVersionSpec("[7,9)"); //version 7-9
            VersionRange exclusion = VersionRange.createFromVersionSpec("(,8)"); //anything less than version 8

            boolean result = processor.exclusionVersionMatchesRequirement(exclusion, required);

            assertThat(result).isTrue();
        }

        @Test
        void requiredRecommendedVersionMatch()
        throws InvalidVersionSpecificationException
        {
            VersionRange required = VersionRange.createFromVersionSpec("7.0.0");
            VersionRange exclusion = VersionRange.createFromVersionSpec("(,8)"); //anything less than version 8

            boolean result = processor.exclusionVersionMatchesRequirement(exclusion, required);

            assertThat(result).isTrue();
        }

        @Test
        void requiredRecommendedVersionNoMatchOnEdge()
        throws InvalidVersionSpecificationException
        {
            VersionRange required = VersionRange.createFromVersionSpec("8.0.0");
            VersionRange exclusion = VersionRange.createFromVersionSpec("(,8)"); //anything less than version 8

            boolean result = processor.exclusionVersionMatchesRequirement(exclusion, required);

            assertThat(result).isFalse();
        }

        @Test
        void requiredRecommendedVersionNoMatch()
        throws InvalidVersionSpecificationException
        {
            VersionRange required = VersionRange.createFromVersionSpec("9.0.0");
            VersionRange exclusion = VersionRange.createFromVersionSpec("(,8)"); //anything less than version 8

            boolean result = processor.exclusionVersionMatchesRequirement(exclusion, required);

            assertThat(result).isFalse();
        }

        //Can't imagine why someone would set up an exclusion that is not a range, but make sure everything is consistent

        @Test
        void exclusionVersionRecommendedVersionMatch()
        throws InvalidVersionSpecificationException
        {
            VersionRange required = VersionRange.createFromVersionSpec("[7, 8)"); //version 7
            VersionRange exclusion = VersionRange.createFromVersionSpec("7.0.0");

            boolean result = processor.exclusionVersionMatchesRequirement(exclusion, required);

            assertThat(result).isTrue();
        }

        @Test
        void exclusionVersionRecommendedVersionNoMatch()
        throws InvalidVersionSpecificationException
        {
            VersionRange required = VersionRange.createFromVersionSpec("[7, 8)"); //version 7
            VersionRange exclusion = VersionRange.createFromVersionSpec("8.0.0");

            boolean result = processor.exclusionVersionMatchesRequirement(exclusion, required);

            assertThat(result).isFalse();
        }

        @Test
        void allRecommendedVersionsMatch()
        throws InvalidVersionSpecificationException
        {
            VersionRange required = VersionRange.createFromVersionSpec("8.0.0");
            VersionRange exclusion = VersionRange.createFromVersionSpec("8.0.0");

            boolean result = processor.exclusionVersionMatchesRequirement(exclusion, required);

            assertThat(result).isTrue();
        }

        @Test
        void allRecommendedVersionsNoMatch()
        throws InvalidVersionSpecificationException
        {
            VersionRange required = VersionRange.createFromVersionSpec("8.0.0");
            VersionRange exclusion = VersionRange.createFromVersionSpec("9.0.0");

            boolean result = processor.exclusionVersionMatchesRequirement(exclusion, required);

            assertThat(result).isFalse();
        }
    }
}
