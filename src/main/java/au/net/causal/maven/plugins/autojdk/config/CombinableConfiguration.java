package au.net.causal.maven.plugins.autojdk.config;

import au.net.causal.maven.plugins.autojdk.xml.config.Activation;

import java.util.List;

/**
 * Base configuration that can be combined / overlayed with other files.
 *
 * @param <C> the actual configuration type.  Will be
 */
public interface CombinableConfiguration<C extends CombinableConfiguration<C>>
{
    /**
     * Additional files that should be included when loading this configuration.  File paths are relative to this file.  Included files are overlayed over this file in order
     * specified here.
     */
    public List<String> getIncludes();

    /**
     * Controls whether this configuration will be processed based on some conditions.  If not specified, this file will always be considered activated.
     */
    public Activation getActivation();

    /**
     * Combines this configuration with another of the same type.  The resulting configuration be the result of the target configuration overlayed with this configuration -
     * where data from this configuration 'wins'.
     *
     * @param other the other configuration to combine with.
     *
     * @return the new, combined configuration.
     */
    public C combinedWith(C other);
}
