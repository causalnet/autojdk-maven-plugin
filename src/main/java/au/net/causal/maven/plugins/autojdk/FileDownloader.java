package au.net.causal.maven.plugins.autojdk;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.StringJoiner;

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
     * @throws TruncatedDownloadException if the file was not completely downloaded.
     * @throws IOException if an error occurs.
     */
    public Download downloadFile(URL url)
    throws IOException;

    public void addDownloadProgressListener(DownloadProgressListener listener);
    public void removeDownloadProgressListener(DownloadProgressListener listener);

    public static interface DownloadProgressListener
    {
        public void downloadStarted(DownloadStartedEvent event);
        public void downloadProgress(DownloadProgressEvent event);
        public void downloadCompleted(DownloadCompletedEvent event);
        public void downloadFailed(DownloadFailedEvent event);
    }

    public static abstract class DownloadEvent
    {
        private final URL downloadUrl;
        private final long downloadSize;

        protected DownloadEvent(URL downloadUrl, long downloadSize)
        {
            this.downloadUrl = Objects.requireNonNull(downloadUrl);
            this.downloadSize = downloadSize;
        }

        public URL getDownloadUrl()
        {
            return downloadUrl;
        }

        /**
         * @return the known size of the download, or -1 if the size is unknown.
         */
        public long getDownloadSize()
        {
            return downloadSize;
        }
    }

    public static class DownloadStartedEvent extends DownloadEvent
    {
        public DownloadStartedEvent(URL downloadUrl, long downloadSize)
        {
            super(downloadUrl, downloadSize);
        }
    }

    public static class DownloadProgressEvent extends DownloadEvent
    {
        private final long bytesDownloaded;

        public DownloadProgressEvent(URL downloadUrl, long downloadSize, long bytesDownloaded)
        {
            super(downloadUrl, downloadSize);
            this.bytesDownloaded = bytesDownloaded;
        }

        /**
         * @return the number of bytes downloaded so far.
         */
        public long getBytesDownloaded()
        {
            return bytesDownloaded;
        }
    }

    public static class DownloadCompletedEvent extends DownloadEvent
    {
        public DownloadCompletedEvent(URL downloadUrl, long downloadSize)
        {
            super(downloadUrl, downloadSize);
        }
    }

    public static class DownloadFailedEvent extends DownloadEvent
    {
        private final IOException error;

        public DownloadFailedEvent(URL downloadUrl, long downloadSize, IOException error)
        {
            super(downloadUrl, downloadSize);
            this.error = Objects.requireNonNull(error);
        }

        public IOException getError()
        {
            return error;
        }
    }

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

        @Override
        public String toString()
        {
            return new StringJoiner(", ", Download.class.getSimpleName() + "[", "]")
                    .add("url=" + url)
                    .add("file=" + file)
                    .toString();
        }
    }
}
