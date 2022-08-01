package au.net.causal.maven.plugins.autojdk;

import com.google.common.net.HttpHeaders;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResumableFileDownloader extends SimpleFileDownloader
{
    private final Log log;

    public ResumableFileDownloader(ExceptionalSupplier<Path, IOException> tempDirectorySupplier, ProxySelector proxySelector)
    {
        this(tempDirectorySupplier, new SystemStreamLog(), proxySelector);
    }

    public ResumableFileDownloader(Path tempDirectory, ProxySelector proxySelector)
    {
        this(tempDirectory, new SystemStreamLog(), proxySelector);
    }

    public ResumableFileDownloader(ExceptionalSupplier<Path, IOException> tempDirectorySupplier, Log log, ProxySelector proxySelector)
    {
        super(tempDirectorySupplier, proxySelector);
        this.log = log;
    }

    public ResumableFileDownloader(Path tempDirectory, Log log, ProxySelector proxySelector)
    {
        super(tempDirectory, proxySelector);
        this.log = log;
    }

    @Override
    protected void saveUrlToFileFromUrlConnection(URL url, Path tempFile, URLConnection con)
    throws IOException
    {
        long downloadSize = con.getContentLengthLong();
        boolean supportsResume = "bytes".equals(con.getHeaderField(HttpHeaders.ACCEPT_RANGES));

        //Resumable downloads not supported, just use default behaviour
        if (!supportsResume || downloadSize < 0L)
        {
            super.saveUrlToFileFromUrlConnection(url, tempFile, con);
            return;
        }

        //TODO maybe some connection timeout settings too on the URL connection, (plus the one we recreate down there)

        Proxy proxy = getProxySelector().selectProxy(url);

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
                        log.warn("Download was cut short (" + totalBytesSaved + " of " +
                                 downloadSize + " saved), attempting to resume download");

                        //Even though we are in a try-with-resources block, close before we try to make a new connection
                        downloadIs.close();

                        //Prepare the next request that will be used in the next iteration of the do-while loop
                        if (proxy == null)
                            con = url.openConnection();
                        else
                        {
                            con = url.openConnection(proxy);

                            Authenticator proxyAuthenticator = getProxySelector().proxyAuthenticator(url);
                            if (proxyAuthenticator != null && con instanceof HttpURLConnection)
                                ((HttpURLConnection)con).setAuthenticator(proxyAuthenticator);
                        }

                        con.setRequestProperty(HttpHeaders.RANGE, "bytes=" + totalBytesSaved + "-");

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

