package au.net.causal.maven.plugins.autojdk;

import com.google.common.net.HttpHeaders;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResumableFileDownloader extends SimpleFileDownloader
{
    private final Log log;

    public ResumableFileDownloader(ExceptionalSupplier<Path, IOException> tempDirectorySupplier)
    {
        this(tempDirectorySupplier, new SystemStreamLog());
    }

    public ResumableFileDownloader(Path tempDirectory)
    {
        this(tempDirectory, new SystemStreamLog());
    }

    public ResumableFileDownloader(ExceptionalSupplier<Path, IOException> tempDirectorySupplier, Log log)
    {
        super(tempDirectorySupplier);
        this.log = log;
    }

    public ResumableFileDownloader(Path tempDirectory, Log log)
    {
        super(tempDirectory);
        this.log = log;
    }

    @Override
    protected void saveUrlToFileFromUrlConnection(URL url, Path tempFile, URLConnection con)
    throws IOException
    {
        long downloadSize = con.getContentLengthLong();

        //Resumable downloads not supported, just use default behaviour
        if (downloadSize < 0L)
            super.saveUrlToTempFile(url, tempFile);

        //TODO maybe some connection timeout settings too on the URL connection, (plus the one we recreate down there)

        boolean supportsResume = "bytes".equals(con.getHeaderField(HttpHeaders.ACCEPT_RANGES));
        long totalBytesSaved = 0L;

        try (OutputStream saveOs = Files.newOutputStream(tempFile))
        {
            do
            {
                try (InputStream downloadIs = con.getInputStream())
                {
                    //Copy as much content as we can to the file from the download stream
                    long curBytesSaved = downloadIs.transferTo(saveOs);
                    totalBytesSaved += curBytesSaved;

                    if (curBytesSaved > 0L && totalBytesSaved < downloadSize)
                    {
                        if (!supportsResume)
                        {
                            throw new IOException("File download incomplete (" + totalBytesSaved + " of " +
                                                  downloadSize + ") and server does not support resuming downloads.");
                        }

                        log.warn("Download was cut short (" + totalBytesSaved + " of " +
                                 downloadSize + " saved), attempting to resume download");

                        //Even though we are in a try-with-resources block, close before we try to make a new connection
                        downloadIs.close();

                        con = url.openConnection();
                        con.setRequestProperty("Range", "bytes=" + totalBytesSaved + "-");

                        long expectedRangeRequestContentLength = downloadSize - totalBytesSaved;

                        //File length changed after resume, maybe the file changed?
                        if (con.getContentLengthLong() != expectedRangeRequestContentLength)
                            throw new IOException("File length changed on the server after resume, maybe the file changed?");
                    }
                }

            }
            while (totalBytesSaved < downloadSize);
        }
    }
}

