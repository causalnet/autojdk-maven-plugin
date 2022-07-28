package au.net.causal.maven.plugins.autojdk;

import com.google.common.annotations.VisibleForTesting;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.ReleaseStatus;
import io.foojay.api.discoclient.pkg.Pkg;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

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
        return foojayPkg.getDistribution().getApiString();
    }

    @Override
    public ArtifactVersion getVersion()
    {
        return mavenSafeVersionFromPkgVersion(foojayPkg.getJavaVersion().toString());
    }

    /**
     * Translate '+' to '-' because Maven really doesn't deal with '+' in version numbers well.
     */
    @VisibleForTesting
    static ArtifactVersion mavenSafeVersionFromPkgVersion(String pkgVersion)
    {
        //Maven cannot handle '+' in versions well
        pkgVersion = pkgVersion.replace('+', '-');

        String curVersion = pkgVersion;

        //Keep translating the nth last '.' into '-' until it parses into a proper Maven version
        int nthLastDotIndex = pkgVersion.length();
        ArtifactVersion v;
        do
        {
            v = new DefaultArtifactVersion(curVersion);
            if (v.getMajorVersion() != 0) //It parsed OK
                return v;

            nthLastDotIndex = pkgVersion.lastIndexOf('.', nthLastDotIndex);
            if (nthLastDotIndex >= 0)
                curVersion = pkgVersion.substring(0, nthLastDotIndex) + "-" + pkgVersion.substring(nthLastDotIndex + 1);
        }
        while (v.getMajorVersion() == 0 && nthLastDotIndex >= 0);

        //If we get here it just simply failed to parse, so give up and just use original
        return new DefaultArtifactVersion(pkgVersion);
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

    @Override
    public ReleaseType getReleaseType()
    {
        if (foojayPkg.getReleaseStatus() == ReleaseStatus.EA)
            return ReleaseType.EA;
        else
            return ReleaseType.GA;
    }

    protected Pkg getFoojayPkg()
    {
        return foojayPkg;
    }

    @Override
    public String toString()
    {
        return "Foojay repository download:" + getFoojayPkg().toString();
    }
}
