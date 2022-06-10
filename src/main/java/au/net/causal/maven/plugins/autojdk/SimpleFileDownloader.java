package au.net.causal.maven.plugins.autojdk;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public class SimpleFileDownloader implements FileDownloader
{
    private final ExceptionalSupplier<Path, IOException> tempDirectorySupplier;
    
    public SimpleFileDownloader(ExceptionalSupplier<Path, IOException> tempDirectorySupplier)
    {
        Objects.requireNonNull(tempDirectorySupplier, "tempDirectorySupplier == null");
        this.tempDirectorySupplier = tempDirectorySupplier;
    }

    public SimpleFileDownloader(Path tempDirectory)
    {
        this(() -> tempDirectory);
    }
    
    @Override
    public Download downloadFile(URL url) 
    throws IOException
    {
        Objects.requireNonNull(url, "url == null");

        //If the URL is already a file just return it
        try
        {
            URI uri = url.toURI();
            if ("file".equals(uri.getScheme()))
                return new NonTemporaryDownload(url, Paths.get(uri));
        }
        catch (URISyntaxException e)
        {
            throw new IOException(e);
        }
        
        //Save to temp file if it is remote
        return new Download(url, saveUrlToTempFile(url));
    }

    private Path saveUrlToTempFile(URL url)
    throws IOException
    {
        Path tempDirectory = tempDirectorySupplier.get();
        Path tempFile = Files.createTempFile(tempDirectory, "download", ".tmp");
        saveUrlToTempFile(url, tempFile);
        return tempFile;
    }

    protected void saveUrlToTempFile(URL url, Path tempFile)
    throws IOException
    {
        URLConnection con = url.openConnection();
        saveUrlToFileFromUrlConnection(url, tempFile, con);
    }

    protected void saveUrlToFileFromUrlConnection(URL url, Path tempFile, URLConnection con)
    throws IOException
    {
        long expectedSize = con.getContentLengthLong();
        try (InputStream is = con.getInputStream())
        {
            //context.getLog().info("Saving " + apkFileUrl + " to file " + tempFile.toAbsolutePath().toString() + " (" + size + " bytes)...");
            long numBytesCopied = Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);

            //If we have a valid expected file size, check it
            if (expectedSize >= 0L && expectedSize != numBytesCopied)
                throw new IOException("Download of " + url.toExternalForm() + " truncated - expected " + expectedSize + "bytes but downloaded only " + numBytesCopied + " bytes");
        }
    }

    /**
     * A non-temporary download that does not delete the original file.
     */
    @VisibleForTesting
    static class NonTemporaryDownload extends Download
    {
        public NonTemporaryDownload(URL url, Path file)
        {
            super(url, file);
        }

        @Override
        public void close() throws IOException
        {
            //Do nothing - file is not a temp file
        }
    }
}
