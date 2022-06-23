package au.net.causal.maven.plugins.autojdk;

import com.google.common.base.StandardSystemProperty;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class TestJdkInstaller
{
    private JdkInstaller installer;

    @TempDir
    private Path tempDir;

    private Path jdksInstallationDirectory;

    @BeforeEach
    private void setUpJdkInstaller()
    throws IOException
    {
        jdksInstallationDirectory = Files.createTempDirectory(tempDir, "autojdk-test-jdks");
        installer = new JdkInstaller(jdksInstallationDirectory);
    }

    @Test
    @Disabled("Still uses real paths, manual run for now")
    void test()
    throws IOException
    {
        //TODO need a better way of picking a filename - this is only temporary
        Path archiveFile = Path.of(StandardSystemProperty.USER_HOME.value(), ".m2", "repository", "au", "net", "causal", "autojdk", "jdk", "zulu", "17.0.3-7", "zulu-17.0.3-7-windows-x64.zip");
        //Path archiveFile = Path.of(StandardSystemProperty.USER_HOME.value(), ".m2", "repository", "au", "net", "causal", "autojdk", "jdk", "zulu", "17.0.3-7", "zulu-17.0.3-7-linux-x64.tar.gz");
        Path jdkDir = installer.installJdkArchive(archiveFile, "myjdk");

        System.out.println("Extracted JDK to: " + jdkDir);

        //TODO tests to verify output
    }

    private Path generateZipArchiveWithEmptyEntries(String... entryPaths)
    throws IOException, ArchiveException
    {
        Path archiveFile = Files.createTempFile(tempDir, "autojdktest", ".zip");
        try (ArchiveOutputStream os = ArchiveStreamFactory.DEFAULT.createArchiveOutputStream(ArchiveStreamFactory.ZIP, Files.newOutputStream(archiveFile)))
        {
            for (String entryPath : entryPaths)
            {
                ArchiveEntry entry = new ZipArchiveEntry(entryPath);
                os.putArchiveEntry(entry);
                os.closeArchiveEntry();
            }
            os.finish();
        }

        return archiveFile;
    }

    private Path generateTarGzArchiveWithEmptyEntries(String... entryPaths)
    throws IOException, ArchiveException, CompressorException
    {
        Path archiveFile = Files.createTempFile(tempDir, "autojdktest", ".tar.gz");
        try (ArchiveOutputStream os = ArchiveStreamFactory.DEFAULT.createArchiveOutputStream(ArchiveStreamFactory.TAR,
                                         CompressorStreamFactory.getSingleton().createCompressorOutputStream(CompressorStreamFactory.GZIP,
                                            Files.newOutputStream(archiveFile))))
        {
            for (String entryPath : entryPaths)
            {
                ArchiveEntry entry = new TarArchiveEntry(entryPath);
                os.putArchiveEntry(entry);
                os.closeArchiveEntry();
            }
            os.finish();
        }

        return archiveFile;
    }

    @Nested
    class DetectTopLevelDirectory
    {
        @Test
        void everythingInTopLevelZipWindows()
        throws IOException, ArchiveException
        {
            Path archiveFile = generateZipArchiveWithEmptyEntries(
                    "readme.txt", "jre/", "jre/something.txt", //other stuff
                    "bin/", "bin/java.exe", "bin/javaw.exe", "bin/javac.exe" //the tools
            );
            String topLevel = installer.detectTopLevelDirectory(archiveFile);
            assertThat(topLevel).isEqualTo("");
        }

        @Test
        void everythingInTopLevelZipLinux()
        throws IOException, ArchiveException
        {
            Path archiveFile = generateZipArchiveWithEmptyEntries(
                    "readme.txt", "jre/", "jre/something.txt", //other stuff
                    "bin/", "bin/java", "bin/javaw", "bin/javac" //the tools
            );
            String topLevel = installer.detectTopLevelDirectory(archiveFile);
            assertThat(topLevel).isEqualTo("");
        }

        @Test
        void everythingInTopLevelTarGzWindows()
        throws IOException, ArchiveException, CompressorException
        {
            Path archiveFile = generateTarGzArchiveWithEmptyEntries(
                    "readme.txt", "jre/", "jre/something.txt", //other stuff
                    "bin/", "bin/java.exe", "bin/javaw.exe", "bin/javac.exe" //the tools
            );
            String topLevel = installer.detectTopLevelDirectory(archiveFile);
            assertThat(topLevel).isEqualTo("");
        }

        @Test
        void everythingInTopLevelTarGzLinux()
        throws IOException, ArchiveException, CompressorException
        {
            Path archiveFile = generateTarGzArchiveWithEmptyEntries(
                    "readme.txt", "jre/", "jre/something.txt", //other stuff
                    "bin/", "bin/java", "bin/javaw", "bin/javac" //the tools
            );
            String topLevel = installer.detectTopLevelDirectory(archiveFile);
            assertThat(topLevel).isEqualTo("");
        }

        @Test
        void oneDirectoryDownZipWindows()
        throws IOException, ArchiveException
        {
            Path archiveFile = generateZipArchiveWithEmptyEntries(
                    "hello.txt", "doc/", "doc/readme.html", //other stuff at top level
                    "myjdk/", "myjdk/readme.txt", "myjdk/jre/", "myjdk/jre/something.txt", //other stuff
                    "myjdk/bin/", "myjdk/bin/java.exe", "myjdk/bin/javaw.exe", "myjdk/bin/javac.exe" //the tools
            );
            String topLevel = installer.detectTopLevelDirectory(archiveFile);
            assertThat(topLevel).isEqualTo("myjdk/");
        }

        @Test
        void oneDirectoryDownZipLinux()
        throws IOException, ArchiveException
        {
            Path archiveFile = generateZipArchiveWithEmptyEntries(
                    "hello.txt", "doc/", "doc/readme.html", //other stuff at top level
                    "myjdk/", "myjdk/readme.txt", "myjdk/jre/", "myjdk/jre/something.txt", //other stuff
                    "myjdk/bin/", "myjdk/bin/java", "myjdk/bin/javaw", "myjdk/bin/javac" //the tools
            );
            String topLevel = installer.detectTopLevelDirectory(archiveFile);
            assertThat(topLevel).isEqualTo("myjdk/");
        }

        @Test
        void oneDirectoryDownTarGzWindows()
        throws IOException, ArchiveException, CompressorException
        {
            Path archiveFile = generateTarGzArchiveWithEmptyEntries(
                    "hello.txt", "doc/", "doc/readme.html", //other stuff at top level
                    "myjdk/", "myjdk/readme.txt", "myjdk/jre/", "myjdk/jre/something.txt", //other stuff
                    "myjdk/bin/", "myjdk/bin/java.exe", "myjdk/bin/javaw.exe", "myjdk/bin/javac.exe" //the tools
            );
            String topLevel = installer.detectTopLevelDirectory(archiveFile);
            assertThat(topLevel).isEqualTo("myjdk/");
        }

        @Test
        void oneDirectoryDownTarGzLinux()
        throws IOException, ArchiveException, CompressorException
        {
            Path archiveFile = generateTarGzArchiveWithEmptyEntries(
                    "hello.txt", "doc/", "doc/readme.html", //other stuff at top level
                    "myjdk/", "myjdk/readme.txt", "myjdk/jre/", "myjdk/jre/something.txt", //other stuff
                    "myjdk/bin/", "myjdk/bin/java", "myjdk/bin/javaw", "myjdk/bin/javac" //the tools
            );
            String topLevel = installer.detectTopLevelDirectory(archiveFile);
            assertThat(topLevel).isEqualTo("myjdk/");
        }

        @Test
        void multipleCandidatesZipWindows()
        throws IOException, ArchiveException
        {
            Path archiveFile = generateZipArchiveWithEmptyEntries(
                    "hello.txt", "doc/", "doc/readme.html", //other stuff at top level

                    //jre - should not be chosen because it is not the shortest path to all the tools
                    //(normally wouldn't have javac but whatever - we are testing finding shortest path)
                    "myjdk/", "myjdk/jre/", "myjdk/jre/bin/", "myjdk/jre/bin/java.exe", "myjdk/jre/bin/javac.exe",

                    //The shorter paths that should be chosen
                    "myjdk/bin/", "myjdk/bin/java.exe", "myjdk/bin/javaw.exe", "myjdk/bin/javac.exe"
            );
            String topLevel = installer.detectTopLevelDirectory(archiveFile);
            assertThat(topLevel).isEqualTo("myjdk/");
        }
    }
}
