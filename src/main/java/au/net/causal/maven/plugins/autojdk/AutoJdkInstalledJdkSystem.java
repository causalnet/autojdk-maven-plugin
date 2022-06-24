package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import jakarta.xml.bind.DataBindingException;
import jakarta.xml.bind.JAXB;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.codehaus.plexus.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Finds JDKs that AutoJDK has installed into its dedicated installation directory.
 * <p>
 *
 * Each JDK is installed in a subdirectory of the base directory with an associated XML metadata file with the same name as the directory.
 */
public class AutoJdkInstalledJdkSystem implements LocalJdkResolver
{
    private static final Logger log = LoggerFactory.getLogger(AutoJdkInstalledJdkSystem.class);

    private final Path autoJdkInstallationDirectory;
    private final JdkInstaller jdkInstaller;

    public AutoJdkInstalledJdkSystem(Path autoJdkInstallationDirectory)
    {
        this.autoJdkInstallationDirectory = Objects.requireNonNull(autoJdkInstallationDirectory);
        this.jdkInstaller = new JdkInstaller(autoJdkInstallationDirectory);
    }

    /**
     * Extracts a JDK from an archive, installing it into the autojdk installation directory and generating an appropriate metadata file.
     *
     * @param jdkArchive an archive containing the JDK to install.
     * @param metadata metadata for the JDK.
     *
     * @throws IOException if an error occurs during the installation.
     */
    public void installJdkFromArchive(Path jdkArchive, LocalJdkMetadata metadata)
    throws IOException
    {
        //Extract the JDK
        Path jdkExtractionDir = jdkInstaller.installJdkArchive(jdkArchive, defaultJdkNameForMetadata(metadata));
        Path jdkMetadataFile = jdkExtractionDir.resolveSibling(jdkExtractionDir.getFileName().toString() + ".xml");

        //Generate the metadata file
        try
        {
            JAXB.marshal(metadata, jdkMetadataFile.toFile());
        }
        catch (DataBindingException e)
        {
            throw new IOException("Error writing JDK metadata file " + jdkMetadataFile + ": " + e.getMessage(), e);
        }
    }

    /**
     * Generates a name of the directory for a JDK given its metadata.
     *
     * @param metadata JDK metadata.
     *
     * @return the directory name.
     */
    protected String defaultJdkNameForMetadata(LocalJdkMetadata metadata)
    {
        return metadata.getVendor() + "-" + metadata.getVersion() + "-" + metadata.getOperatingSystem().getApiString() + "-" + metadata.getArchitecture().getApiString();
    }

    @Override
    public Collection<? extends AutoJdkInstallation> getInstalledJdks()
    throws LocalJdkResolutionException
    {
        //If there's no JDK base directory, there are not JDKs
        if (Files.notExists(autoJdkInstallationDirectory))
            return Collections.emptyList();

        List<AutoJdkInstallation> jdks = new ArrayList<>();

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(autoJdkInstallationDirectory, "*.xml"))
        {
            for (Path metadataXmlFile : dirStream)
            {
                try
                {
                    LocalJdkMetadata metadata = JAXB.unmarshal(metadataXmlFile.toFile(), LocalJdkMetadata.class);
                    Path jdkDirectory = metadataXmlFile.resolveSibling(FileUtils.removeExtension(metadataXmlFile.getFileName().toString()));
                    if (!Files.isDirectory(jdkDirectory))
                        log.warn("JDK directory " + jdkDirectory + " does not exist for metadata file " + metadataXmlFile);

                    jdks.add(new AutoJdkInstallation(jdkDirectory, metadata));
                }
                catch (DataBindingException e)
                {
                    log.warn("Failed to read local JDK metadata file " + metadataXmlFile + ": " + e.getMessage());
                    log.debug("Failed to read local JDK metadata file " + metadataXmlFile + ": " + e.getMessage(), e);
                }
            }
        }
        catch (IOException e)
        {
            throw new LocalJdkResolutionException(e);
        }

        return jdks;
    }

    public static class AutoJdkInstallation implements LocalJdk
    {
        private final Path jdkDirectory;
        private final LocalJdkMetadata metadata;

        public AutoJdkInstallation(Path jdkDirectory, LocalJdkMetadata metadata)
        {
            this.jdkDirectory = Objects.requireNonNull(jdkDirectory);
            this.metadata = Objects.requireNonNull(metadata);
        }

        @Override
        public String getVendor()
        {
            return metadata.getVendor();
        }

        @Override
        public ArtifactVersion getVersion()
        {
            return new DefaultArtifactVersion(metadata.getVersion());
        }

        @Override
        public OperatingSystem getOperatingSystem()
        {
            return metadata.getOperatingSystem();
        }

        @Override
        public Architecture getArchitecture()
        {
            return metadata.getArchitecture();
        }

        @Override
        public Path getJdkDirectory()
        {
            return jdkDirectory;
        }
    }

    @XmlRootElement(name = "local-jdk-metadata")
    public static class LocalJdkMetadata
    {
        private String vendor;
        private String version;
        private Architecture architecture;
        private OperatingSystem operatingSystem;

        public String getVendor()
        {
            return vendor;
        }

        public void setVendor(String vendor)
        {
            this.vendor = vendor;
        }

        public String getVersion()
        {
            return version;
        }

        public void setVersion(String version)
        {
            this.version = version;
        }

        public Architecture getArchitecture()
        {
            return architecture;
        }

        public void setArchitecture(Architecture architecture)
        {
            this.architecture = architecture;
        }

        public OperatingSystem getOperatingSystem()
        {
            return operatingSystem;
        }

        public void setOperatingSystem(OperatingSystem operatingSystem)
        {
            this.operatingSystem = operatingSystem;
        }
    }
}
