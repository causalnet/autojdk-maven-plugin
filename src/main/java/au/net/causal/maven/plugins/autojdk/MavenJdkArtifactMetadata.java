package au.net.causal.maven.plugins.autojdk;

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

    public MavenJdkArtifactMetadata()
    {
        this(Collections.emptySet());
    }

    public MavenJdkArtifactMetadata(Collection<ArchiveType> archiveTypes)
    {
        this.archiveTypes = new LinkedHashSet<>(archiveTypes);
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
}
