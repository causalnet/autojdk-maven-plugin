package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Standard version translation schemes that can be referenced by name.
 */
public enum StandardVersionTranslationScheme implements VersionTranslationScheme
{
    /**
     * JDK project requirements are treated as-is without modification.  A requirement of "17" would mean explicitly Java 17.0.0 and only that version.
     */
    UNMODIFIED
    {
        @Override
        public Collection<? extends ArtifactVersion> expandJdkVersionForRegistration(ArtifactVersion actualJdkVersion)
        {
            return Collections.singleton(actualJdkVersion);
        }

        @Override
        public VersionRange translateProjectRequiredJdkVersionToSearchCriteria(VersionRange projectRequiredJdkVersion)
        {
            return projectRequiredJdkVersion;
        }
    },

    /**
     * JDK project requirements that have only major version numbers are handled loosely so they can work as would a proper range specification.
     * For example, "17" would work as "[17, 18)".
     */
    MAJOR_AND_FULL
    {
        @Override
        public Collection<? extends ArtifactVersion> expandJdkVersionForRegistration(ArtifactVersion actualJdkVersion)
        {
            List<ArtifactVersion> results = new ArrayList<>(2);
            ArtifactVersion majorOnly = new DefaultArtifactVersion(String.valueOf(actualJdkVersion.getMajorVersion()));
            results.add(actualJdkVersion);
            if (!actualJdkVersion.equals(majorOnly))
                results.add(majorOnly);

            return results;
        }

        @Override
        public VersionRange translateProjectRequiredJdkVersionToSearchCriteria(VersionRange projectRequiredJdkVersion)
        {
            //Do we have a requirement with only a recommended major version?
            if (projectRequiredJdkVersion.getRecommendedVersion() != null && !projectRequiredJdkVersion.hasRestrictions() &&
                VersionTools.isMajorVersionOnly(projectRequiredJdkVersion.getRecommendedVersion()))
            {
                int majorVersion = projectRequiredJdkVersion.getRecommendedVersion().getMajorVersion();
                int nextMajorVersion = majorVersion + 1;
                String criteria = "[" + majorVersion + "," + nextMajorVersion + ")";

                try
                {
                    return VersionRange.createFromVersionSpec(criteria);
                }
                catch (InvalidVersionSpecificationException e)
                {
                    //Should not happen we are constructing the string where only variables are integers
                    throw new RuntimeException("Failed to create version range from spec " + criteria + ": " + e, e);
                }
            }
            else //Anything not just a major version we'll just leave as-is
                return projectRequiredJdkVersion;
        }
    };
}
