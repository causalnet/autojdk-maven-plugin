package au.net.causal.maven.plugins.autojdk.xml.metadata;

import au.net.causal.maven.plugins.autojdk.ArchiveType;
import au.net.causal.maven.plugins.autojdk.ReleaseType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@XmlRootElement(name = "jdk")
public class MavenJdkArtifactMetadata
{
    private final Set<ArchiveType> archiveTypes;
    private ReleaseType releaseType;

    public MavenJdkArtifactMetadata()
    {
        this(Collections.emptySet(), ReleaseType.GA); //Default to GA if not present
    }

    public MavenJdkArtifactMetadata(Collection<ArchiveType> archiveTypes, ReleaseType releaseType)
    {
        this.archiveTypes = new LinkedHashSet<>(archiveTypes);
        this.releaseType = releaseType;
    }

    @XmlElement(name = "archiveType")
    public Set<ArchiveType> getArchiveTypes()
    {
        return archiveTypes;
    }

    public void setArchiveTypes(Set<ArchiveType> archiveTypes)
    {
        this.archiveTypes.clear();
        this.archiveTypes.addAll(archiveTypes);
    }

    public ReleaseType getReleaseType()
    {
        return releaseType;
    }

    public void setReleaseType(ReleaseType releaseType)
    {
        this.releaseType = releaseType;
    }
}
