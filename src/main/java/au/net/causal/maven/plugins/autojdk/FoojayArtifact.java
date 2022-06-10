package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import io.foojay.api.discoclient.pkg.Pkg;

import java.util.Objects;

public class FoojayArtifact implements JdkArtifact
{
    private final Pkg foojayPkg;

    public FoojayArtifact(Pkg foojayPkg)
    {
        this.foojayPkg = Objects.requireNonNull(foojayPkg);
    }

    @Override
    public String getVendor()
    {
        return foojayPkg.getDistributionName();
    }

    @Override
    public String getVersion()
    {
        return foojayPkg.getJavaVersion().toString();
    }

    @Override
    public Architecture getArchitecture()
    {
        return foojayPkg.getArchitecture();
    }

    @Override
    public OperatingSystem getOperatingSystem()
    {
        return foojayPkg.getOperatingSystem();
    }

    @Override
    public ArchiveType getArchiveType()
    {
        switch (foojayPkg.getArchiveType())
        {
            case ZIP:
                return ArchiveType.ZIP;
            case TAR_GZ:
                return ArchiveType.TAR_GZ;
            default:
                return null;
        }
    }

    protected Pkg getFoojayPkg()
    {
        return foojayPkg;
    }
}