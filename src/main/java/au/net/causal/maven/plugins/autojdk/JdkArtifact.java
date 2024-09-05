package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.xml.metadata.ArchiveType;
import au.net.causal.maven.plugins.autojdk.xml.metadata.ReleaseType;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.ArtifactVersion;

public interface JdkArtifact
{
    //TODO consider implmenting architecture/OS ourselves to have no jdktools dependencies

    public String getVendor();
    public ArtifactVersion getVersion();
    public Architecture getArchitecture();
    public OperatingSystem getOperatingSystem();
    public ArchiveType getArchiveType();
    public ReleaseType getReleaseType();
}
