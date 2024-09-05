package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.xml.metadata.ReleaseType;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Objects;

public class JdkSearchRequest
{
    private final VersionRange versionRange;
    private final Architecture architecture;
    private final OperatingSystem operatingSystem;
    private final String vendor;
    private final ReleaseType releaseType;

    public JdkSearchRequest(VersionRange versionRange, Architecture architecture, OperatingSystem operatingSystem, String vendor, ReleaseType releaseType)
    {
        this.versionRange = Objects.requireNonNull(versionRange);
        this.architecture = architecture;
        this.operatingSystem = operatingSystem;
        this.vendor = vendor;
        this.releaseType = releaseType;
    }

    public VersionRange getVersionRange()
    {
        return versionRange;
    }

    public Architecture getArchitecture()
    {
        return architecture;
    }

    public OperatingSystem getOperatingSystem()
    {
        return operatingSystem;
    }

    public String getVendor()
    {
        return vendor;
    }

    public ReleaseType getReleaseType()
    {
        return releaseType;
    }

    public JdkSearchRequest withVersionRange(VersionRange versionRange)
    {
        return new JdkSearchRequest(versionRange, getArchitecture(), getOperatingSystem(), getVendor(), getReleaseType());
    }
}
