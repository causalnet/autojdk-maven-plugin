package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.AutoJdkConfiguration.ExtensionExclusion;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Collection;
import java.util.List;

public class ExtensionExclusionProcessor
{
    private final Collection<? extends ExtensionExclusion> exclusions;

    public ExtensionExclusionProcessor(Collection<? extends ExtensionExclusion> exclusions)
    {
        this.exclusions = List.copyOf(exclusions);
    }

    public ExtensionExclusion checkExclusions(VersionRange requiredVersion)
    throws ExclusionProcessorException
    {
        for (ExtensionExclusion exclusion : exclusions)
        {
            try
            {
                VersionRange exclusionVersionRange = VersionRange.createFromVersionSpec(exclusion.getVersion());

                //If the combined version range can match anything (is not empty) then apply this exclusion
                if (exclusionVersionMatchesRequirement(exclusionVersionRange, requiredVersion))
                    return exclusion;
            }
            catch (InvalidVersionSpecificationException e)
            {
                throw new ExclusionProcessorException("Invalid exclusion version '" + exclusion.getVersion() + "'.", e);
            }
        }

        //Nothing matched
        return null;
    }

    protected boolean exclusionVersionMatchesRequirement(VersionRange exclusionVersion, VersionRange requiredVersion)
    {
        VersionRange exclusionAndRequiredVersion = requiredVersion.restrict(exclusionVersion);

        //No restrictions mean no matches - even a recommended version has the wildcard restriction
        if (exclusionAndRequiredVersion.getRestrictions().isEmpty())
            return false;

        //If required version has a recommended version, it will only be carried to the result if it matches the range of the result
        if (requiredVersion.getRecommendedVersion() != null && !requiredVersion.getRecommendedVersion().equals(exclusionAndRequiredVersion.getRecommendedVersion()))
            return false;

        //Similar the other way around - anything not matching indicates a mismatch on the whole VersionRange
        if (exclusionVersion.getRecommendedVersion() != null && !exclusionVersion.getRecommendedVersion().equals(exclusionAndRequiredVersion.getRecommendedVersion()))
            return false;

        return true;
    }

    public static class ExclusionProcessorException extends Exception
    {
        public ExclusionProcessorException()
        {
        }

        public ExclusionProcessorException(String message)
        {
            super(message);
        }

        public ExclusionProcessorException(String message, Throwable cause)
        {
            super(message, cause);
        }

        public ExclusionProcessorException(Throwable cause)
        {
            super(cause);
        }
    }
}
