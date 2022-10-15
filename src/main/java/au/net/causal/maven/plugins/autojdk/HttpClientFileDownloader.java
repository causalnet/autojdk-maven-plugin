package au.net.causal.maven.plugins.autojdk;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HttpHeaders;
import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class HttpClientFileDownloader implements FileDownloader
{
    private final ExceptionalSupplier<Path, IOException> tempDirectorySupplier;
    private final HttpClient.Builder httpClientBuilder;

    private final List<DownloadProgressListener> downloadProgressListeners = new CopyOnWriteArrayList<>();

    public HttpClientFileDownloader(ExceptionalSupplier<Path, IOException> tempDirectorySupplier, HttpClient.Builder httpClientBuilder)
    {
        Objects.requireNonNull(tempDirectorySupplier, "tempDirectorySupplier == null");
        Objects.requireNonNull(httpClientBuilder, "httpClientBuilder == null");
        this.tempDirectorySupplier = tempDirectorySupplier;
        this.httpClientBuilder = httpClientBuilder;
    }

    public HttpClientFileDownloader(ExceptionalSupplier<Path, IOException> tempDirectorySupplier)
    {
        this(tempDirectorySupplier, HttpClient.newBuilder());
    }

    public HttpClientFileDownloader(Path tempDirectory, HttpClient.Builder httpClientBuilder)
    {
        this(() -> tempDirectory, httpClientBuilder);
    }

    public HttpClientFileDownloader(Path tempDirectory)
    {
        this(() -> tempDirectory, HttpClient.newBuilder());
    }

    @Override
    public void addDownloadProgressListener(DownloadProgressListener listener)
    {
        downloadProgressListeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void removeDownloadProgressListener(DownloadProgressListener listener)
    {
        downloadProgressListeners.remove(Objects.requireNonNull(listener));
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
        saveUrlToFileUsingHttpClient(url, tempFile, httpClientBuilder.build());
    }

    protected void saveUrlToFileUsingHttpClient(URL url, Path tempFile, HttpClient httpClient)
    throws IOException
    {
        HttpResponse<InputStream> response;
        try
        {
            response = httpClient.send(HttpRequest.newBuilder().uri(url.toURI()).build(), HttpResponse.BodyHandlers.ofInputStream());
        }
        catch (URISyntaxException e)
        {
            throw new IOException(e);
        }
        catch (InterruptedException e)
        {
            InterruptedIOException ex = new InterruptedIOException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }

        long expectedSize = response.headers().firstValueAsLong(HttpHeaders.CONTENT_LENGTH).orElse(-1L);
        DownloadStartedEvent startEvent = new DownloadStartedEvent(url, expectedSize);
        downloadProgressListeners.forEach(listener -> listener.downloadStarted(startEvent));

        //Check response
        int responseStatus = response.statusCode();
        if (responseStatus == 404) //Special case for 404 - not found
            throw new FileNotFoundException(url.toExternalForm());
        if (responseStatus / 100 != 2)
            throw new IOException("HTTP error: " + responseStatus);

        try (InputStream is = response.body())
        {
            //This is faster but no progress
            //long numBytesCopied = Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);

            long numBytesCopied;
            try (OutputStream os = Files.newOutputStream(tempFile))
            {
                numBytesCopied = copy(is, os, startEvent);
            }

            //If we have a valid expected file size, check it
            if (expectedSize >= 0L && expectedSize != numBytesCopied)
                throw new TruncatedDownloadException(url, expectedSize, numBytesCopied);
        }
        catch (IOException e)
        {
            DownloadFailedEvent failedEvent = new DownloadFailedEvent(url, expectedSize, e);
            downloadProgressListeners.forEach(listener -> listener.downloadFailed(failedEvent));
            throw e;
        }

        //Download completed
        DownloadCompletedEvent completedEvent = new DownloadCompletedEvent(url, expectedSize);
        downloadProgressListeners.forEach(listener -> listener.downloadCompleted(completedEvent));
    }

    private long copy(InputStream is, OutputStream os, DownloadStartedEvent startEvent)
    throws IOException
    {
        long numBytesCopied = 0L;
        byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
        int read;
        while ((read = is.read(buffer, 0, buffer.length)) >= 0)
        {
            os.write(buffer, 0, read);
            numBytesCopied += read;

            DownloadProgressEvent progressEvent = new DownloadProgressEvent(startEvent.getDownloadUrl(), startEvent.getDownloadSize(), numBytesCopied);
            downloadProgressListeners.forEach(listener -> listener.downloadProgress(progressEvent));
        }
        return numBytesCopied;
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
