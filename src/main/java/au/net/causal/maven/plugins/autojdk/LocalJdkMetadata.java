package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.Objects;

@XmlRootElement(name = "local-jdk-metadata")
public class LocalJdkMetadata
{
    private String vendor;
    private String version;
    private Architecture architecture;
    private OperatingSystem operatingSystem;

    public LocalJdkMetadata()
    {
    }

    public LocalJdkMetadata(String vendor, String version, Architecture architecture, OperatingSystem operatingSystem)
    {
        this.vendor = Objects.requireNonNull(vendor);
        this.version = Objects.requireNonNull(version);
        this.architecture = Objects.requireNonNull(architecture);
        this.operatingSystem = Objects.requireNonNull(operatingSystem);
    }

    public String getVendor()
    {
        return vendor;
    }

    public void setVendor(String vendor)
    {
        this.vendor = vendor;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public Architecture getArchitecture()
    {
        return architecture;
    }

    public void setArchitecture(Architecture architecture)
    {
        this.architecture = architecture;
    }

    public OperatingSystem getOperatingSystem()
    {
        return operatingSystem;
    }

    public void setOperatingSystem(OperatingSystem operatingSystem)
    {
        this.operatingSystem = operatingSystem;
    }
}
