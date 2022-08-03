package au.net.causal.maven.plugins.autojdk;

/**
 * Thrown when something goes wrong when reading update check metadata for a JDK search.
 */
public class JdkSearchUpdateCheckException extends Exception
{
    public JdkSearchUpdateCheckException()
    {
    }

    public JdkSearchUpdateCheckException(String message)
    {
        super(message);
    }

    public JdkSearchUpdateCheckException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public JdkSearchUpdateCheckException(Throwable cause)
    {
        super(cause);
    }
}
