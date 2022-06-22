package au.net.causal.maven.plugins.autojdk;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class JdkInstaller
{
    private final ArchiveStreamFactory archiveStreamFactory = ArchiveStreamFactory.DEFAULT;

    public void installJdkArchive(Path jdkArchive)
    throws IOException
    {
        //TODO scan for top-level directories that can be skipped
        //   e.g. vendorjdk/bin, vendorjdk/lib

        try (ArchiveInputStream is = archiveStreamFactory.createArchiveInputStream(Files.newInputStream(jdkArchive)))
        {
            ArchiveEntry ae;
            while ((ae = is.getNextEntry()) != null)
            {
                System.out.println(ae.getName());
            }
        }
        catch (ArchiveException e)
        {
            throw new IOException(e);
        }
    }

}
