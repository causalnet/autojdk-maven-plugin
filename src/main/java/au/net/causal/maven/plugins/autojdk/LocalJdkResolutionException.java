package au.net.causal.maven.plugins.autojdk;

public class LocalJdkResolutionException extends Exception
{
    public LocalJdkResolutionException()
    {
    }

    public LocalJdkResolutionException(String message)
    {
        super(message);
    }

    public LocalJdkResolutionException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public LocalJdkResolutionException(Throwable cause)
    {
        super(cause);
    }
}
