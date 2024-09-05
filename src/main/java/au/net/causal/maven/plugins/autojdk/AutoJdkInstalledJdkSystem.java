package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.xml.metadata.LocalJdkMetadata;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.codehaus.plexus.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
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
public class AutoJdkInstalledJdkSystem implements LocalJdkResolver, JdkInstallationTarget
{
    private static final Logger log = LoggerFactory.getLogger(AutoJdkInstalledJdkSystem.class);

    private final Path autoJdkInstallationDirectory;
    private final JdkInstaller jdkInstaller;
    private final AutoJdkXmlManager xmlManager;

    public AutoJdkInstalledJdkSystem(Path autoJdkInstallationDirectory, AutoJdkXmlManager xmlManager)
    {
        this.autoJdkInstallationDirectory = Objects.requireNonNull(autoJdkInstallationDirectory);
        this.xmlManager = Objects.requireNonNull(xmlManager);
        this.jdkInstaller = new JdkInstaller(autoJdkInstallationDirectory);
    }

    /**
     * Extracts a JDK from an archive, installing it into the autojdk installation directory and generating an appropriate metadata file.
     *
     * @param jdkArchive an archive containing the JDK to install.
     * @param metadata metadata for the JDK.
     *
     * @return the local directory where the JDK was extracted and installed.
     *
     * @throws IOException if an error occurs during the installation.
     */
    @Override
    public Path installJdkFromArchive(Path jdkArchive, LocalJdkMetadata metadata)
    throws IOException
    {
        //Extract the JDK
        Path jdkExtractionDir = jdkInstaller.installJdkArchive(jdkArchive, defaultJdkNameForMetadata(metadata));
        Path jdkMetadataFile = metadataFileForJdkInstallationDirectory(jdkExtractionDir);

        //Generate the metadata file
        try
        {
            xmlManager.writeFile(metadata, jdkMetadataFile);
        }
        catch (AutoJdkXmlManager.XmlWriteException e)
        {
            throw new IOException("Error writing JDK metadata file " + jdkMetadataFile + ": " + e.getMessage(), e);
        }

        return jdkExtractionDir;
    }

    @Override
    public void deleteJdk(Path jdkDirectory) throws IOException
    {
        //Ensure everything exists
        if (Files.notExists(jdkDirectory))
            throw new NoSuchFileException(jdkDirectory.toString());
        if (!Files.isDirectory(jdkDirectory))
            throw new NotDirectoryException(jdkDirectory.toString());

        Path jdkMetadataFile = metadataFileForJdkInstallationDirectory(jdkDirectory);
        if (Files.notExists(jdkMetadataFile))
            throw new IOException("Missing metadata file for JDK: " + jdkDirectory, new NoSuchFileException(jdkMetadataFile.toString()));

        //Perform deletion of metadata and JDK directory
        Files.delete(jdkMetadataFile);
        FileUtils.deleteDirectory(jdkDirectory.toFile());
    }

    private Path metadataFileForJdkInstallationDirectory(Path jdkDirectory)
    {
        return jdkDirectory.resolveSibling(jdkDirectory.getFileName().toString() + ".xml");
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
    public Collection<? extends AutoJdkInstallation> getInstalledJdks(ReleaseType releaseType)
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
                    LocalJdkMetadata metadata = xmlManager.parseFile(metadataXmlFile, LocalJdkMetadata.class);
                    Path jdkDirectory = metadataXmlFile.resolveSibling(FileUtils.removeExtension(metadataXmlFile.getFileName().toString()));
                    if (!Files.isDirectory(jdkDirectory))
                        log.warn("JDK directory " + jdkDirectory + " does not exist for metadata file " + metadataXmlFile);

                    if (releaseType == null || releaseType.equals(metadata.getReleaseType()))
                        jdks.add(new AutoJdkInstallation(jdkDirectory, metadata));
                }
                catch (AutoJdkXmlManager.XmlParseException e)
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

        @Override
        public ReleaseType getReleaseType()
        {
            return metadata.getReleaseType();
        }
    }

}
