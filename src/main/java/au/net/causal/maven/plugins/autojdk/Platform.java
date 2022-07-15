package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;

import java.util.Objects;

/**
 * A platform is the combination of an operating system and an architecture.
 */
public class Platform
{
    private final OperatingSystem operatingSystem;
    private final Architecture architecture;

    public Platform(OperatingSystem operatingSystem, Architecture architecture)
    {
        this.operatingSystem = Objects.requireNonNull(operatingSystem);
        this.architecture = Objects.requireNonNull(architecture);
    }

    public OperatingSystem getOperatingSystem()
    {
        return operatingSystem;
    }

    public Architecture getArchitecture()
    {
        return architecture;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof Platform)) return false;
        Platform platform = (Platform) o;
        return operatingSystem == platform.operatingSystem && architecture == platform.architecture;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(operatingSystem, architecture);
    }

    @Override
    public String toString()
    {
        return operatingSystem.getApiString() + "-" + architecture.getApiString();
    }
}
