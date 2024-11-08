package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.xml.metadata.ArchiveType;
import au.net.causal.maven.plugins.autojdk.xml.metadata.ReleaseType;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.Objects;

/**
 * Simple JDK artifact implementation for testing.
 */
class SimpleJdkArtifact implements JdkArtifact
{
    private final String vendor;
    private final ArtifactVersion version;
    private final Architecture architecture;
    private final OperatingSystem operatingSystem;
    private final ArchiveType archiveType;
    private final ReleaseType releaseType;

    public SimpleJdkArtifact(String vendor, ArtifactVersion version, Architecture architecture, OperatingSystem operatingSystem, ArchiveType archiveType, ReleaseType releaseType)
    {
        this.vendor = vendor;
        this.version = version;
        this.architecture = architecture;
        this.operatingSystem = operatingSystem;
        this.archiveType = archiveType;
        this.releaseType = releaseType;
    }

    /**
     * Simpler constructor with useful defaults for testing.
     */
    public SimpleJdkArtifact(String vendor, String version, ArchiveType archiveType)
    {
        this(vendor, new DefaultArtifactVersion(version), Architecture.X64, OperatingSystem.LINUX, archiveType, ReleaseType.GA);
    }

    @Override
    public String getVendor()
    {
        return vendor;
    }

    @Override
    public ArtifactVersion getVersion()
    {
        return version;
    }

    @Override
    public Architecture getArchitecture()
    {
        return architecture;
    }

    @Override
    public OperatingSystem getOperatingSystem()
    {
        return operatingSystem;
    }

    @Override
    public ArchiveType getArchiveType()
    {
        return archiveType;
    }

    @Override
    public ReleaseType getReleaseType()
    {
        return releaseType;
    }

    @Override
    public String toString()
    {
        return getVendor() + ":" + getVersion() + ":" + getReleaseType() + ":" + getArchitecture().getApiString() + ":" + getOperatingSystem().getApiString() + ":" + getArchiveType();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof SimpleJdkArtifact)) return false;
        SimpleJdkArtifact that = (SimpleJdkArtifact) o;
        return getVendor().equals(that.getVendor()) && getVersion().equals(
                that.getVersion()) && getArchitecture() == that.getArchitecture() && getOperatingSystem() == that.getOperatingSystem() && getArchiveType() == that.getArchiveType() && getReleaseType() == that.getReleaseType();
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getVendor(), getVersion(), getArchitecture(), getOperatingSystem(), getArchiveType(), getReleaseType());
    }
}
