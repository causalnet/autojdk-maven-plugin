package au.net.causal.maven.plugins.autojdk.config;

/**
 * Controls how a configuration will be processed based on some conditions.  If more than one condition is specified, all conditions must match for activation to occur.
 */
public class Activation
{
    private String hostJdk;
    private OperatingSystem os;
    private Property property;
    private FileActivation file;
    private String autoJdkVersion;

    /**
     * Activation will occur when a matching host JDK (the JDK running Maven itself) matches this version.  This can be a version number of Maven version range.
     *
     * @see org.apache.maven.model.Activation#getJdk()
     */
    public String getHostJdk()
    {
        return hostJdk;
    }

    public void setHostJdk(String hostJdk)
    {
        this.hostJdk = hostJdk;
    }

    /**
     * Activation will occur when the current operating system matches.
     */
    public OperatingSystem getOs()
    {
        return os;
    }

    public void setOs(OperatingSystem os)
    {
        this.os = os;
    }

    /**
     * Activation will occur when this property exists and, if specified, its value matches.
     */
    public Property getProperty()
    {
        return property;
    }

    public void setProperty(Property property)
    {
        this.property = property;
    }

    /**
     * Activation occurs based on the existence of a file.
     */
    public FileActivation getFile()
    {
        return file;
    }

    public void setFile(FileActivation file)
    {
        this.file = file;
    }

    /**
     * Activation occurs when running with specific versions of the AutoJDK plugin.  This can be either an exact version or a version range.
     */
    public String getAutoJdkVersion()
    {
        return autoJdkVersion;
    }

    public void setAutoJdkVersion(String autoJdkVersion)
    {
        this.autoJdkVersion = autoJdkVersion;
    }

    /**
     * Operating system attributes used for matching.
     */
    public static class OperatingSystem
    {
        private String name;
        private String family;
        private String arch;
        private String version;

        /**
         * The name of the operating system to be used to activate the profile. This must be an exact match of the ${os.name} Java property, such as Windows XP.
         */
        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        /**
         * The general family of the OS to be used to activate the profile, such as windows or unix.
         */
        public String getFamily()
        {
            return family;
        }

        public void setFamily(String family)
        {
            this.family = family;
        }

        /**
         * The architecture of the operating system to be used to activate the profile.  This is an exact match of ${os.arch}.
         */
        public String getArch()
        {
            return arch;
        }

        public void setArch(String arch)
        {
            this.arch = arch;
        }

        /**
         * The version of the operating system to be used to activate the profile.  This is an exact match of ${os.version}.
         */
        public String getVersion()
        {
            return version;
        }

        public void setVersion(String version)
        {
            this.version = version;
        }
    }

    /**
     * Matches a property.
     */
    public static class Property
    {
        private String name;
        private String value;

        /**
         * The name of the property that must exist for activation.
         */
        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        /**
         * The required value of the property.  If specified, the property must have this exact value for activation to occur.
         */
        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

    /**
     * Activation occurs whether a file exists or is missing.
     */
    public static class FileActivation
    {
        private String missing;
        private String exists;

        /**
         * The name of the file that must be missing for activation.
         */
        public String getMissing()
        {
            return missing;
        }

        public void setMissing(String missing)
        {
            this.missing = missing;
        }

        /**
         * The name of the file that must exist for activation.
         */
        public String getExists()
        {
            return exists;
        }

        public void setExists(String exists)
        {
            this.exists = exists;
        }
    }
}
