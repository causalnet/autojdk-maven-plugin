package au.net.causal.maven.plugins.autojdk;

public class JdkRepositoryException extends Exception
{
    public JdkRepositoryException()
    {
    }

    public JdkRepositoryException(String message)
    {
        super(message);
    }

    public JdkRepositoryException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public JdkRepositoryException(Throwable cause)
    {
        super(cause);
    }
}
