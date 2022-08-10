package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.ArtifactVersion;

import java.util.Objects;

public class JdkPurgeCacheRequest
{
    private final ArtifactVersion version;
    private final Architecture architecture;
    private final OperatingSystem operatingSystem;
    private final String vendor;
    private final ReleaseType releaseType;

    public JdkPurgeCacheRequest(ArtifactVersion version, Architecture architecture, OperatingSystem operatingSystem, String vendor, ReleaseType releaseType)
    {
        this.version = Objects.requireNonNull(version);
        this.architecture = Objects.requireNonNull(architecture);
        this.operatingSystem = Objects.requireNonNull(operatingSystem);
        this.vendor = Objects.requireNonNull(vendor);
        this.releaseType = Objects.requireNonNull(releaseType);
    }

    public ArtifactVersion getVersion()
    {
        return version;
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

    public JdkArtifact toJdkArtifact(ArchiveType archiveType)
    {
        return new JdkPurgeRequestJdkArtifact(this, archiveType);
    }

    protected static class JdkPurgeRequestJdkArtifact implements JdkArtifact
    {
        private final JdkPurgeCacheRequest purgeRequest;
        private final ArchiveType archiveType;

        public JdkPurgeRequestJdkArtifact(JdkPurgeCacheRequest purgeRequest, ArchiveType archiveType)
        {
            this.purgeRequest = Objects.requireNonNull(purgeRequest);
            this.archiveType = Objects.requireNonNull(archiveType);
        }

        @Override
        public String getVendor()
        {
            return purgeRequest.getVendor();
        }

        @Override
        public ArtifactVersion getVersion()
        {
            return purgeRequest.getVersion();
        }

        @Override
        public Architecture getArchitecture()
        {
            return purgeRequest.getArchitecture();
        }

        @Override
        public OperatingSystem getOperatingSystem()
        {
            return purgeRequest.getOperatingSystem();
        }

        @Override
        public ArchiveType getArchiveType()
        {
            return archiveType;
        }

        @Override
        public ReleaseType getReleaseType()
        {
            return purgeRequest.getReleaseType();
        }
    }
}
