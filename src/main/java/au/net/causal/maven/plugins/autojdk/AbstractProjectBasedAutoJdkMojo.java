package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractProjectBasedAutoJdkMojo extends AbstractAutoJdkMojo
{
    @Parameter(property = PROPERTY_JDK_VERSION)
    private String requiredJdkVersion;

    private VersionRange requiredJdkVersionRange;

    @Parameter(property = PROPERTY_JDK_VENDOR)
    private String requiredJdkVendor;

    protected VersionRange getRequiredJdkVersionRange()
    {
        return requiredJdkVersionRange;
    }

    protected String getRequiredJdkVendor()
    {
        return requiredJdkVendor;
    }

    private Map<String, String> readToolchainsJdkRequirements()
    {
        Map<String, String> requirements = new LinkedHashMap<>();

        Plugin toolchainsPlugin = project.getBuild().getPluginsAsMap().get("org.apache.maven.plugins:maven-toolchains-plugin");

        //Should only have one execution, but if there's more than one just combine everything
        //If there's multiple executions with different requirements I'd consider the build broken
        //so if we break there as well it's not a huge deal
        if (toolchainsPlugin != null && toolchainsPlugin.getExecutions() != null)
        {
            for (PluginExecution toolchainsPluginExecution : toolchainsPlugin.getExecutions())
            {
                Object configuration = toolchainsPluginExecution.getConfiguration();
                if (configuration instanceof Xpp3Dom)
                {
                    Xpp3Dom xppConfig = (Xpp3Dom) configuration;

                    Xpp3Dom toolchainsConfig = xppConfig.getChild("toolchains");
                    if (toolchainsConfig != null)
                    {
                        Xpp3Dom jdkConfig = toolchainsConfig.getChild("jdk");
                        if (jdkConfig != null)
                        {
                            for (Xpp3Dom requirementConfig : jdkConfig.getChildren())
                            {
                                String requirementName = requirementConfig.getName();
                                String requirementValue = requirementConfig.getValue();
                                if (StringUtils.isNotEmpty(requirementName) && StringUtils.isNotEmpty(requirementValue))
                                    requirements.put(requirementName, requirementValue);
                            }
                        }
                    }
                }
            }
        }

        return requirements;
    }

    @Override
    protected void executeImpl()
    throws MojoExecutionException, MojoFailureException
    {
        Map<String, String> toolchainJdkRequirements = readToolchainsJdkRequirements();
        if (requiredJdkVersion == null)
            requiredJdkVersion = toolchainJdkRequirements.get("version");
        if (requiredJdkVendor == null)
            requiredJdkVendor = toolchainJdkRequirements.get("vendor");

        if (requiredJdkVersion == null)
            throw new MojoExecutionException("No required JDK version configured.  Either configure directly on the plugin or add toolchains plugin with appropriate configuration.");

        try
        {
            requiredJdkVersionRange = VersionRange.createFromVersionSpec(requiredJdkVersion);
        }
        catch (InvalidVersionSpecificationException e)
        {
            throw new MojoExecutionException("Invalid JDK version/range: " + requiredJdkVersion, e);
        }

        super.executeImpl();
    }

    /**
     * When the required JDK version (possibly read from toolchains) is a simple integer value, e.g. '17' then use
     * {@link StandardVersionTranslationScheme#MAJOR_AND_FULL} translation, otherwise use
     * {@link StandardVersionTranslationScheme#UNMODIFIED} translation.  This will allow toolchains configurations
     * with java version set at e.g. '17' to just work even with JDK 17.0.2.
     *
     * @see StandardVersionTranslationScheme
     */
    @Override
    protected VersionTranslationScheme detectVersionTranslationScheme()
    {
        //If the required format is a simple integer...
        if (isPositiveInteger(requiredJdkVersion))
            return StandardVersionTranslationScheme.MAJOR_AND_FULL;
        else
            return StandardVersionTranslationScheme.UNMODIFIED;
    }

    /**
     * @return true if {@code s} is a parseable integer with value greater than zero, false otherwise.
     */
    private static boolean isPositiveInteger(String s)
    {
        try
        {
            int n = Integer.parseInt(s);
            return n > 0;
        }
        catch (NumberFormatException e)
        {
            return false;
        }
    }
}
