package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.ArtifactVersion;

import java.nio.file.Path;

public interface LocalJdk
{
    public String getVendor();
    public ArtifactVersion getVersion();
    public OperatingSystem getOperatingSystem();
    public Architecture getArchitecture();
    public Path getJdkDirectory();
}
