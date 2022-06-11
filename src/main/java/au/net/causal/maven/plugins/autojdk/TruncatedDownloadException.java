package au.net.causal.maven.plugins.autojdk;

import java.io.IOException;
import java.net.URL;

/**
 * Thrown when attempting to download a file results in content that is less than the expected length.
 */
public class TruncatedDownloadException extends IOException
{
    private final URL url;
    private final long expectedLength;
    private final long actualLength;

    public TruncatedDownloadException(URL url, long expectedLength, long actualLength)
    {
        this(url, expectedLength, actualLength,
             "Download of " + url.toExternalForm() + " truncated - expected " + expectedLength + " bytes but downloaded only " + actualLength + " bytes");
    }

    public TruncatedDownloadException(URL url, long expectedLength, long actualLength, String message)
    {
        this(url, expectedLength, actualLength, message, null);
    }

    public TruncatedDownloadException(URL url, long expectedLength, long actualLength, String message, Throwable cause)
    {
        super(message, cause);
        this.url = url;
        this.expectedLength = expectedLength;
        this.actualLength = actualLength;
    }

    /**
     * @return the URL of the file that was downloaded.
     */
    public URL getUrl()
    {
        return url;
    }

    /**
     * @return the expected length the file was meant to have.
     */
    public long getExpectedLength()
    {
        return expectedLength;
    }

    /**
     * @return the actual number of bytes of the file downloaded.
     */
    public long getActualLength()
    {
        return actualLength;
    }
}
