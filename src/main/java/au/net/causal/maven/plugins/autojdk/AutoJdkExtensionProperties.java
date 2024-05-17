package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AutoJDK properties that may be set on the command line by the user through system properties.
 */
public class AutoJdkExtensionProperties
{
    private final String jdkVendor;
    private final String jdkVersion;
    private final Path autoJdkConfigFile;
    private final Boolean skip;

    public AutoJdkExtensionProperties(String jdkVendor, String jdkVersion, Path autoJdkConfigFile, Boolean skip)
    {
        this.jdkVendor = jdkVendor;
        this.jdkVersion = jdkVersion;
        this.autoJdkConfigFile = autoJdkConfigFile;
        this.skip = skip;
    }

    /**
     * Reads system properties from the Maven session.
     */
    public static AutoJdkExtensionProperties fromMavenSession(MavenSession session)
    throws MavenExecutionException
    {
        PluginParameterExpressionEvaluator evaluator = new PluginParameterExpressionEvaluator(session, new MojoExecution(null));
        try
        {
            String vendor = (String) evaluator.evaluate("${" + PrepareMojo.PROPERTY_JDK_VENDOR + "}", String.class);
            String version = (String) evaluator.evaluate("${" + PrepareMojo.PROPERTY_JDK_VERSION + "}", String.class);
            String autoJdkConfigFile = (String) evaluator.evaluate("${" + PrepareMojo.PROPERTY_AUTOJDK_CONFIGURATION_FILE + "}", File.class); //TODO check this
            boolean skip = Boolean.parseBoolean((String) evaluator.evaluate("${" + PrepareMojo.PROPERTY_AUTOJDK_SKIP + "}", Boolean.class));
            return new AutoJdkExtensionProperties(vendor, version, autoJdkConfigFile == null ? null : Paths.get(autoJdkConfigFile), skip);
        }
        catch (ExpressionEvaluationException e)
        {
            throw new MavenExecutionException("Error reading properties: " + e, e);
        }
    }

    /**
     * @return JDK vendor specified on command line.  -D with {@value PrepareMojo#PROPERTY_JDK_VENDOR}
     */
    public String getJdkVendor()
    {
        return jdkVendor;
    }

    /**
     * @return JDK version specified on command line.  -D with {@value PrepareMojo#PROPERTY_JDK_VERSION}
     */
    public String getJdkVersion()
    {
        return jdkVersion;
    }

    /**
     * @return the user-specified AutoJDK configuration file.  -D with {@value PrepareMojo#PROPERTY_AUTOJDK_CONFIGURATION_FILE}
     */
    public Path getAutoJdkConfigFile()
    {
        return autoJdkConfigFile;
    }
}
