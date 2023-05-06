package au.net.causal.maven.plugins.autojdk.config;

public class AutoJdkConfigurationException extends Exception
{
    public AutoJdkConfigurationException()
    {
    }

    public AutoJdkConfigurationException(String message)
    {
        super(message);
    }

    public AutoJdkConfigurationException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public AutoJdkConfigurationException(Throwable cause)
    {
        super(cause);
    }
}
