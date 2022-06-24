package au.net.causal.maven.plugins.autojdk;

public class JdkNotFoundException extends Exception
{
    public JdkNotFoundException()
    {
    }

    public JdkNotFoundException(String message)
    {
        super(message);
    }

    public JdkNotFoundException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public JdkNotFoundException(Throwable cause)
    {
        super(cause);
    }
}
