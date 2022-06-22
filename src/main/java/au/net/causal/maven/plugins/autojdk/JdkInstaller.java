package au.net.causal.maven.plugins.autojdk;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.plexus.archiver.Archiver;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JdkInstaller
{
    private final ArchiveStreamFactory archiveStreamFactory = ArchiveStreamFactory.DEFAULT;

    /**
     * All these tools must be found under the JDK directory, optionally with a '.exe' suffix for Windows.
     * Directory separators normalized to '/'.
     */
    private final Collection<String> knownJdkTools = List.of("bin/java", "bin/javac");

    public void installJdkArchive(Path jdkArchive)
    throws IOException
    {
        String topLevelDir = detectTopLevelDirectory(jdkArchive);

        System.out.println(topLevelDir);

        //TODO extractArchive(jdkArchive, topLevelDir, outputDir);
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
    private String detectTopLevelDirectory(Path jdkArchive)
    throws IOException
    {
        //Find the shortest path to bin/java[.exe] and bin/javac[.exe]

        Set<String> filesInArchive = new LinkedHashSet<>();
        Set<String> directoriesInArchive = new LinkedHashSet<>(); //All will end with '/'

        //Read all directories in archive
        try (ArchiveInputStream is = archiveStreamFactory.createArchiveInputStream(new BufferedInputStream(Files.newInputStream(jdkArchive))))
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
}
