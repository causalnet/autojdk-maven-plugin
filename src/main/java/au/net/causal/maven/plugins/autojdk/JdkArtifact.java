package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;

import java.util.Objects;

public interface JdkArtifact
{
    //TODO consider implmenting architecture/OS ourselves to have no jdktools dependencies

    public String getVendor();
    public String getVersion();
    public Architecture getArchitecture();
    public OperatingSystem getOperatingSystem();
    public ArchiveType getArchiveType();
}
