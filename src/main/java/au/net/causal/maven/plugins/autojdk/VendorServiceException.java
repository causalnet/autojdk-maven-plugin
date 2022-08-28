package au.net.causal.maven.plugins.autojdk;

public class VendorServiceException extends Exception
{
    public VendorServiceException()
    {
    }

    public VendorServiceException(String message)
    {
        super(message);
    }

    public VendorServiceException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public VendorServiceException(Throwable cause)
    {
        super(cause);
    }
}
