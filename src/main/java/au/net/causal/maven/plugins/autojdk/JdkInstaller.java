package au.net.causal.maven.plugins.autojdk;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.util.ArchiveEntryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class JdkInstaller
{
    private static final Logger log = LoggerFactory.getLogger(JdkInstaller.class);

    private final ArchiveStreamFactory archiveStreamFactory = ArchiveStreamFactory.DEFAULT;
    private final CompressorStreamFactory compressorStreamFactory = CompressorStreamFactory.getSingleton();

    /**
     * All these tools must be found under the JDK directory, optionally with a '.exe' suffix for Windows.
     * Directory separators normalized to '/'.
     */
    private static final Collection<String> knownJdkTools = List.of("bin/java", "bin/javac");

    private final Path jdksInstallationDirectory;

    public JdkInstaller(Path jdksInstallationDirectory)
    {
        this.jdksInstallationDirectory = Objects.requireNonNull(jdksInstallationDirectory);
    }

    public Path installJdkArchive(Path jdkArchive, String name)
    throws IOException
    {
        String topLevelDir = detectTopLevelDirectory(jdkArchive);
        if (topLevelDir == null)
        {
            log.warn("Could not detect top-level JDK directory in " + jdkArchive);
            topLevelDir = "";
        }

        Path jdkExtractionDir = jdksInstallationDirectory.resolve(name);
        if (Files.exists(jdkExtractionDir))
            throw new FileAlreadyExistsException(jdkExtractionDir.toString());

        extractArchive(jdkArchive, topLevelDir, jdkExtractionDir);

        return jdkExtractionDir;
    }

    /**
     * Find the top-most directory in an archive that contains all the known JDK tools.
     *
     * @param jdkArchive the JDK archive to scan.
     *
     * @return the name of the directory that contains 'the JDK' with all the required tools.
     *
     * @throws IOException if an error occurs scanning the archive.
     */
    @VisibleForTesting
    String detectTopLevelDirectory(Path jdkArchive)
    throws IOException
    {
        //Find the shortest path to bin/java[.exe] and bin/javac[.exe]

        Set<String> filesInArchive = new LinkedHashSet<>();
        Set<String> directoriesInArchive = new LinkedHashSet<>(); //All will end with '/'

        //Always allow the root
        directoriesInArchive.add("");

        //Read all directories in archive
        try (ArchiveInputStream is = detectAndReadArchiveFile(jdkArchive))
        {
            ArchiveEntry entry;
            while ((entry = is.getNextEntry()) != null)
            {
                String fileName = FilenameUtils.separatorsToUnix(entry.getName());

                if (entry.isDirectory())
                {
                    if (!fileName.endsWith("/"))
                        fileName = fileName + "/";

                    directoriesInArchive.add(fileName);
                }
                else
                    filesInArchive.add(fileName);
            }
        }
        catch (ArchiveException e)
        {
            throw new IOException(e);
        }

        Set<String> directoriesWithAllTheTools = new LinkedHashSet<>();
        for (String directory : directoriesInArchive)
        {
            int toolsFound = 0;
            for (String knownJdkTool : knownJdkTools)
            {
                String expectedFileNameUnderDirectory = directory + knownJdkTool;
                if (filesInArchive.contains(expectedFileNameUnderDirectory))
                    toolsFound++;
                else if (filesInArchive.contains(expectedFileNameUnderDirectory + ".exe")) //For windows
                    toolsFound++;
            }

            if (toolsFound == knownJdkTools.size())
                directoriesWithAllTheTools.add(directory);
        }

        //Might have multiple directories, so let's pick the most top-level one
        String bestDirectory = null;
        for (String directory : directoriesWithAllTheTools)
        {
            if (bestDirectory == null || StringUtils.countMatches(directory, '/') < StringUtils.countMatches(bestDirectory, '/'))
                bestDirectory = directory;
        }

        return bestDirectory;
    }

    private void extractArchive(Path jdkArchive, String baseDirectoryInArchive, Path outputDirectory)
    throws IOException
    {
        try (ArchiveInputStream is = detectAndReadArchiveFile(jdkArchive))
        {
            ArchiveEntry entry;
            while ((entry = is.getNextEntry()) != null)
            {
                extractFile(entry, is, baseDirectoryInArchive, outputDirectory);
            }
        }
        catch (ArchiveException e)
        {
            throw new IOException("Error reading archive: " + jdkArchive + ": " + e.getMessage(), e);
        }
    }

    private void extractFile(ArchiveEntry entry, InputStream entryData, String baseDirectoryInArchive, Path baseOutputDirectory)
    throws IOException
    {
        //Do not extract this entry if it doesn't start with the base directory inside the archive
        if (!entry.getName().startsWith(baseDirectoryInArchive))
            return;

        //Remove leading '/' if it exists and normalize separators to system
        String relativeEntryName = entry.getName().substring(baseDirectoryInArchive.length());
        relativeEntryName = FilenameUtils.separatorsToUnix(relativeEntryName);
        if (relativeEntryName.startsWith("/"))
            relativeEntryName = relativeEntryName.substring(1);

        relativeEntryName = FilenameUtils.separatorsToSystem(relativeEntryName);

        //Generate a target path
        Path targetPath = baseOutputDirectory.resolve(relativeEntryName);

        //Extract file to this path
        extractEntryToTargetPath(entry, entryData, targetPath);
    }

    private void extractEntryToTargetPath(ArchiveEntry entry, InputStream entryData, Path targetPath)
    throws IOException
    {
        if (entry.isDirectory())
            Files.createDirectories(targetPath);
        else
        {
            //Might not have explicit directory entries, so make sure parent exists before extracting
            Files.createDirectories(targetPath.getParent());

            //Copy the data
            try (OutputStream targetOs = Files.newOutputStream(targetPath))
            {
                entryData.transferTo(targetOs);
            }

            //File modified date
            if (entry.getLastModifiedDate() != null)
                Files.setLastModifiedTime(targetPath, FileTime.from(entry.getLastModifiedDate().toInstant()));

            //Unix permissions
            if (!entry.isDirectory())
            {
                Integer unixMode = readUnixModeForArchiveEntry(entry);
                if (unixMode != null)
                {
                    try
                    {
                        ArchiveEntryUtils.chmod(targetPath.toFile(), unixMode); //Same thing what Maven/Plexus archiver does
                    }
                    catch (ArchiverException e)
                    {
                        //Prefer checked exception
                        throw new IOException(e);
                    }

                }
            }
        }
    }

    protected Integer readUnixModeForArchiveEntry(ArchiveEntry entry)
    {
        if (entry instanceof TarArchiveEntry)
        {
            TarArchiveEntry tarEntry = (TarArchiveEntry)entry;
            if (tarEntry.getMode() != 0)
                return tarEntry.getMode();
            else
                return null;
        }
        else if (entry instanceof ZipArchiveEntry)
        {
            ZipArchiveEntry zipEntry = (ZipArchiveEntry)entry;
            if (zipEntry.getUnixMode() != 0)
                return zipEntry.getUnixMode();
            else
                return null;
        }

        //Couldn't read mode if we get here
        return null;
    }

    /**
     * Opens an archive file, detects its format, and opens an archive input stream to read its contents.
     *
     * @param archiveFile the archive file to open.
     *
     * @return the opened archive stream ready to read its entries.
     *
     * @throws IOException if an I/O error occurs reading the file.
     * @throws ArchiveException if an error occurs understanding the archive format.
     */
    private ArchiveInputStream detectAndReadArchiveFile(Path archiveFile)
    throws IOException, ArchiveException
    {
        String compressor;

        InputStream is = new BufferedInputStream(Files.newInputStream(archiveFile));
        try
        {

            //Check if there is a compressor (the .gz in a tar.gz) and decompress if we can
            //Might not need a compressor
            compressor = CompressorStreamFactory.detect(is);
        }
        catch (CompressorException e)
        {
            //No compressor found for the stream, just treat as an archive directly
            compressor = null;
        }
        catch (Exception e)
        {
            //Another worse exception, bail out
            try
            {
                is.close();
            }
            catch (IOException ex)
            {
                e.addSuppressed(ex);
            }
            throw e;
        }

        try
        {
            if (compressor != null)
                is = new BufferedInputStream(compressorStreamFactory.createCompressorInputStream(compressor, is)); //Need another buffered because mark() is required for archive stream
        }
        catch (CompressorException e)
        {
            throw new IOException("Could not decompress file " + archiveFile + ": " + e.getMessage(), e);
        }

        return archiveStreamFactory.createArchiveInputStream(is);
    }
}
