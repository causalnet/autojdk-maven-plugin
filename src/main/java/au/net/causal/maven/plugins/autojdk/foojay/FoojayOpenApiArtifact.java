package au.net.causal.maven.plugins.autojdk.foojay;

import au.net.causal.maven.plugins.autojdk.ArchiveType;
import au.net.causal.maven.plugins.autojdk.JdkArtifact;
import au.net.causal.maven.plugins.autojdk.ReleaseType;
import com.google.common.annotations.VisibleForTesting;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.ReleaseStatus;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.Objects;

public class FoojayOpenApiArtifact implements JdkArtifact
{
    private final JdkPackage jdkPackage;

    public FoojayOpenApiArtifact(JdkPackage jdkPackage)
    {
        this.jdkPackage = Objects.requireNonNull(jdkPackage);
    }

    public JdkPackage getJdkPackage()
    {
        return jdkPackage;
    }

    @Override
    public String getVendor()
    {
        return getJdkPackage().getDistribution();
    }

    @Override
    public ArtifactVersion getVersion()
    {
        return mavenSafeVersionFromPkgVersion(getJdkPackage().getJavaVersion());
    }

    /**
     * Translate '+' to '-' because Maven really doesn't deal with '+' in version numbers well.
     * TODO duplicated in FoojayArtifact
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
        return getJdkPackage().getArchitecture();
    }

    @Override
    public OperatingSystem getOperatingSystem()
    {
        return getJdkPackage().getOperatingSystem();
    }

    @Override
    public ArchiveType getArchiveType()
    {
        switch (getJdkPackage().getArchiveType())
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
        if (getJdkPackage().getReleaseStatus() == ReleaseStatus.EA)
            return ReleaseType.EA;
        else
            return ReleaseType.GA;
    }

    @Override
    public String toString()
    {
        return "Foojay OpenAPI repository download:" + getJdkPackage().toString();
    }
}
