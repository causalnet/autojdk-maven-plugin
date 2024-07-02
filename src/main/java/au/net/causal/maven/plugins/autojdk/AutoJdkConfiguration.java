package au.net.causal.maven.plugins.autojdk;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * User-global configuration for AutoJDK.
 */
@XmlRootElement(name = "autojdk-configuration")
@XmlType(propOrder={})
public class AutoJdkConfiguration
{
    /**
     * Special vendor name that can be used to substitute any other known vendor that is not already in the list.
     */
    public static final String WILDCARD_VENDOR = "*";

    /**
     * Default vendor preference order.  These were selected to provide maximum compatibility.
     */
    static final List<String> DEFAULT_VENDORS = List.of(
            "zulu", //Zulu seems to always work and is present all the way back to JDK 6
            "liberica", //Next ones are pretty solid too but not available on all platforms
            "corretto",
            "oracle_open_jdk",
            "temurin",
            WILDCARD_VENDOR //Still allow everything else if the above ones aren't available for a particular platform/JDK version combination
    );

    /**
     * Default JDK update policy duration of 1 day.
     */
    static final JdkUpdatePolicySpec DEFAULT_JDK_UPDATE_POLICY = new JdkUpdatePolicySpec(new JdkUpdatePolicy.EveryDuration(DatatypeFactory.newDefaultInstance().newDuration(true, DatatypeConstants.FIELD_UNDEFINED, DatatypeConstants.FIELD_UNDEFINED, 1, DatatypeConstants.FIELD_UNDEFINED, DatatypeConstants.FIELD_UNDEFINED, DatatypeConstants.FIELD_UNDEFINED)));

    //Method instead of variable since extension exclusion element is mutable
    static List<ExtensionExclusion> defaultExtensionExclusions()
    {
        return List.of(new ExtensionExclusion("(,8)", "[8,9)"));
    }

    private final List<String> imports = new ArrayList<>();
    private final List<String> vendors = new ArrayList<>();
    private final List<ExtensionExclusion> extensionExclusions = new ArrayList<>();
    private JdkUpdatePolicySpec jdkUpdatePolicy;

    public static AutoJdkConfiguration defaultAutoJdkConfiguration()
    {
        return new AutoJdkConfiguration(Collections.emptyList(), DEFAULT_VENDORS, defaultExtensionExclusions(), DEFAULT_JDK_UPDATE_POLICY);
    }

    public AutoJdkConfiguration()
    {
        this(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
    }

    public AutoJdkConfiguration(List<String> imports, List<String> vendors, List<ExtensionExclusion> extensionExclusions, JdkUpdatePolicySpec jdkUpdatePolicy)
    {
        this.imports.addAll(imports);
        this.vendors.addAll(vendors);
        this.extensionExclusions.addAll(extensionExclusions);
        this.jdkUpdatePolicy = jdkUpdatePolicy;
    }

    /**
     * List of additional configuration files to import.
     */
    @XmlElementWrapper
    @XmlElement(name = "import")
    public List<String> getImports()
    {
        return imports;
    }

    public void setImports(List<String> imports)
    {
        this.imports.clear();
        this.imports.addAll(imports);
    }

    /**
     * List of JDK vendors that will be used/preferred in the order they are specified.
     * The special entry {@value WILDCARD_VENDOR} will match all vendors and can be used in the last place to allow any other vendors with matching JDKs should there be no previous matches.
     */
    @XmlElementWrapper
    @XmlElement(name = "vendor")
    public List<String> getVendors()
    {
        return vendors;
    }

    public void setVendors(List<String> vendors)
    {
        this.vendors.clear();
        this.vendors.addAll(vendors);
    }

    @XmlElementWrapper(name = "extension-exclusions")
    @XmlElement(name = "extension-exclusion")
    public List<ExtensionExclusion> getExtensionExclusions()
    {
        return extensionExclusions;
    }

    public void setExtensionExclusions(List<ExtensionExclusion> extensionExclusions)
    {
        this.extensionExclusions.clear();
        this.extensionExclusions.addAll(extensionExclusions);
    }

    @XmlElement(name = "jdk-update-policy")
    public JdkUpdatePolicySpec getJdkUpdatePolicy()
    {
        return jdkUpdatePolicy;
    }

    public void setJdkUpdatePolicy(JdkUpdatePolicySpec jdkUpdatePolicy)
    {
        this.jdkUpdatePolicy = jdkUpdatePolicy;
    }

    /**
     * Combine this configuration with another configuration, preferring other's configuration if there are conflicts.
     *
     * @param other the other configuration to combine with.
     *
     * @return the combined configuration.
     */
    public AutoJdkConfiguration combinedWith(AutoJdkConfiguration other)
    {
        AutoJdkConfiguration combined = new AutoJdkConfiguration();

        combined.setVendors(other.getVendors());
        if (combined.getVendors().isEmpty())
            combined.setVendors(this.getVendors());

        combined.setExtensionExclusions(other.getExtensionExclusions());
        if (combined.getExtensionExclusions().isEmpty())
            combined.setExtensionExclusions(this.getExtensionExclusions());

        combined.setJdkUpdatePolicy(other.getJdkUpdatePolicy());
        if (combined.getJdkUpdatePolicy() == null)
            combined.setJdkUpdatePolicy(this.getJdkUpdatePolicy());

        return combined;
    }

    public static AutoJdkConfiguration fromFile(Path file, AutoJdkXmlManager xmlManager)
    throws AutoJdkXmlManager.XmlParseException
    {
        //If no config file is present just use the default settings
        if (Files.notExists(file))
            return AutoJdkConfiguration.defaultAutoJdkConfiguration();

        AutoJdkConfiguration configFromFile = xmlManager.parseFile(file, AutoJdkConfiguration.class);
        List<Path> importFiles = configFromFile.resolveImportFiles(file.getParent());

        //Start with defaults
        var fullConfig = defaultAutoJdkConfiguration();

        //Resolve/apply imports
        for (Path importFile : importFiles)
        {
            //Ignore files that are specified as import but do not exist
            if (Files.exists(importFile))
            {
                AutoJdkConfiguration importConfig = fromFile(importFile, xmlManager);
                //TODO conditionals and namespace checking
                fullConfig = fullConfig.combinedWith(importConfig);
            }
        }

        fullConfig = fullConfig.combinedWith(configFromFile);

        //Imports are now resolved so remove them
        fullConfig.setImports(List.of());

        return fullConfig;
    }

    private List<Path> resolveImportFiles(Path base)
    {
        return getImports().stream()
                .map(base::resolve)
                .collect(Collectors.toList());
    }

    @XmlType(propOrder={})
    public static class ExtensionExclusion
    {
        private String version;
        private String substitution;

        public ExtensionExclusion()
        {
        }

        public ExtensionExclusion(String version, String substitution)
        {
            this.version = version;
            this.substitution = substitution;
        }

        public String getVersion()
        {
            return version;
        }

        public void setVersion(String version)
        {
            this.version = version;
        }

        public String getSubstitution()
        {
            return substitution;
        }

        public void setSubstitution(String substitution)
        {
            this.substitution = substitution;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof ExtensionExclusion)) return false;
            ExtensionExclusion that = (ExtensionExclusion) o;
            return Objects.equals(getVersion(), that.getVersion()) && Objects.equals(getSubstitution(), that.getSubstitution());
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(getVersion(), getSubstitution());
        }
    }

    /**
     * Holds a choice of update policy.
     */
    public static class JdkUpdatePolicySpec
    {
        private JdkUpdatePolicy value;

        public JdkUpdatePolicySpec()
        {
        }

        public JdkUpdatePolicySpec(JdkUpdatePolicy jdkUpdatePolicy)
        {
            this.value = jdkUpdatePolicy;
        }

        /**
         * Which update policy was specified.
         */
        @XmlElements({
                @XmlElement(name = "never", type = JdkUpdatePolicy.Never.class, required = true),
                @XmlElement(name = "always", type = JdkUpdatePolicy.Always.class, required = true),
                @XmlElement(name = "every", type = JdkUpdatePolicy.EveryDuration.class, required = true)
        })
        public JdkUpdatePolicy getValue()
        {
            return value;
        }

        public void setValue(JdkUpdatePolicy value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof JdkUpdatePolicySpec)) return false;
            JdkUpdatePolicySpec that = (JdkUpdatePolicySpec) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(value);
        }
    }
}
