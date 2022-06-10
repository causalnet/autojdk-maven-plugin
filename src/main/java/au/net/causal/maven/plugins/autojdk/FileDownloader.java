package au.net.causal.maven.plugins.autojdk;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Downloads a file from a remote URL to a temporary storage space on the local system.
 */
public interface FileDownloader
{
    /**
     * Downloads a file to a temporary storage space on the local system.  Use try-with-resources on the resulting download to ensure the temporary 
     * file is cleaned up after use.
     * 
     * @param url the URL to download.
     *            
     * @return the temporary downloaded file.
     * 
     * @throws IOException if an error occurs.
     */
    public Download downloadFile(URL url)
    throws IOException;
    
    /**
     * A file that was downloaded to a temporary directory on the filesystem.
     */
    public static class Download implements Closeable
    {
        private final URL url;
        private final Path file;

        /**
         * Creates a temporary download.
         * 
         * @param url the original URL that was downloaded from.
         * @param file the temporary file that the download was saved to.
         */
        public Download(URL url, Path file)
        {
            Objects.requireNonNull(url, "url == null");
            Objects.requireNonNull(file, "file == null");

            this.url = url;
            this.file = file;
        }

        /**
         * @return the original URL that was downloaded from.
         */
        public URL getUrl()
        {
            return url;
        }

        /**
         * @return the temporary file that the download was saved to.
         */
        public Path getFile()
        {
            return file;
        }

        /**
         * Removes the temporary download from the filesystem.
         * 
         * @throws IOException if an error occurs.
         */
        @Override
        public void close() 
        throws IOException
        {
            Files.deleteIfExists(file);
        }
    }
}
