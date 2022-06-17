package au.net.causal.maven.plugins.autojdk;

import com.google.common.annotations.VisibleForTesting;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.util.Objects;
import java.util.regex.Pattern;

public class MavenJdkArtifact implements JdkArtifact
{
    private final Artifact artifact;

    /**
     * Creates a Maven JDK artifact directly from a Maven artifact.
     *
     * @param artifact the Maven artifact.
     */
    public MavenJdkArtifact(Artifact artifact)
    {
        this.artifact = Objects.requireNonNull(artifact);
    }

    /**
     * Creates a Maven JDK artifact from JDK artifact details, creating the underlying Maven artifact object.
     *
     * @param groupId the group ID for the Maven artifact.
     * @param vendor JDK vendor.
     * @param version JDK version.
     * @param architecture JDK architecture.
     * @param operatingSystem JDK operating system.
     * @param archiveType JDK archive type.
     */
    public MavenJdkArtifact(String groupId, String vendor, String version, Architecture architecture, OperatingSystem operatingSystem, ArchiveType archiveType)
    {
        this(new DefaultArtifact(groupId, vendorToArtifactId(vendor), makeClassifier(operatingSystem, architecture), archiveType.getFileExtension(), version));
    }

    /**
     * Creates a Maven JDK artifact from details of another JDK artifact, creating the underlying Maven artifact object.
     *
     * @param groupId the group ID for the Maven artifact.
     * @param jdkDetails JDK details to use.  This may be any JDK artifact implementation, not necessarily a
     *                   Maven JDK artifact.
     */
    public MavenJdkArtifact(String groupId, JdkArtifact jdkDetails)
    {
        this(groupId, jdkDetails.getVendor(), jdkDetails.getVersion(), jdkDetails.getArchitecture(),
             jdkDetails.getOperatingSystem(), jdkDetails.getArchiveType());
    }

    @Override
    public String getVendor()
    {
        return artifact.getArtifactId();
    }

    @Override
    public String getVersion()
    {
        return artifact.getVersion();
    }

    @Override
    public Architecture getArchitecture()
    {
        return parseClassifier(artifact.getClassifier()).getArchitecture();
    }

    @Override
    public OperatingSystem getOperatingSystem()
    {
        return parseClassifier(artifact.getClassifier()).getOperatingSystem();
    }

    @Override
    public ArchiveType getArchiveType()
    {
        return ArchiveType.forFileExtension(artifact.getExtension());
    }

    /**
     * @return the Maven artifact used for the JDK artifact.
     */
    public Artifact getArtifact()
    {
        return artifact;
    }

    /**
     * Generates a classifier for the given JDK operating system and architecture.
     */
    @VisibleForTesting
    static String makeClassifier(OperatingSystem operatingSystem, Architecture architecture)
    {
        return operatingSystem.getApiString() + "-" + architecture.getApiString();
    }

    /**
     * Parse operating system and architecture from a classifier.
     * Classifier is in the form [operating system]-[architecture].
     *
     * @param classifier the classifier to parse.
     *
     * @return the operating system and architecture that was parsed.  Never null, but might have null elements if
     *          the classifier or portions of it could not be parsed.
     */
    @VisibleForTesting
    static OperatingSystemAndArchitecture parseClassifier(String classifier)
    {
        OperatingSystem operatingSystem = null;
        Architecture architecture = null;

        if (classifier != null && !classifier.isEmpty())
        {
            String[] tokens = classifier.split(Pattern.quote("-"), 2);
            if (tokens.length > 0)
                operatingSystem = OperatingSystem.fromText(tokens[0]);
            if (tokens.length > 1)
                architecture = Architecture.fromText(tokens[1]);
        }

        return new OperatingSystemAndArchitecture(operatingSystem, architecture);
    }

    public static String vendorToArtifactId(String vendor)
    {
        return vendor;
    }

    /**
     * Find the canonical architecture given a possible synonym.  Some architectures might have synonyms, but it can be
     * useful to always use the same architecture for all its synonyms.
     *
     * @param architecture architecture to canonicalize.
     *
     * @return the canonical architecture which is equal to {@code architecture} or has it as a synonym.
     */
    private static Architecture canonicalArchitecture(Architecture architecture)
    {
        for (Architecture a : Architecture.values())
        {
            if (a == architecture || a.getSynonyms().contains(architecture))
                return a;
        }

        //Not found - should never happen
        return architecture;
    }

    /**
     * JDK operating system and architecture pair.
     */
    public static class OperatingSystemAndArchitecture
    {
        private final OperatingSystem operatingSystem;
        private final Architecture architecture;

        public OperatingSystemAndArchitecture(OperatingSystem operatingSystem, Architecture architecture)
        {
            this.operatingSystem = operatingSystem;
            this.architecture = architecture;
        }

        public OperatingSystem getOperatingSystem()
        {
            return operatingSystem;
        }

        public Architecture getArchitecture()
        {
            return architecture;
        }
    }
}
