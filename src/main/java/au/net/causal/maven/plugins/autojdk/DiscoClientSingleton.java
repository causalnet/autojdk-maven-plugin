package au.net.causal.maven.plugins.autojdk;

import io.foojay.api.discoclient.DiscoClient;
import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.concurrent.LazyInitializer;

/**
 * Singleton lazy-instantiator of disco client.
 */
public final class DiscoClientSingleton
{
    private static final DiscoClientInitializer discoClientInitializer = new DiscoClientInitializer();

    /**
     * Private constructor to prevent instantiation.
     */
    private DiscoClientSingleton()
    {
    }

    /**
     * @return the singleton disco client.
     */
    public static DiscoClient discoClient()
    {
        try
        {
            return discoClientInitializer.get();
        }
        catch (ConcurrentException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static class DiscoClientInitializer extends LazyInitializer<DiscoClient>
    {
        @Override
        protected DiscoClient initialize()
        {
            DiscoClient discoClient = new DiscoClient();

            //This forces the DISTRIBUTIONS field to be populated, waits for it to finish and sets initialized to true
            DiscoClient.getDistributionFromText("");

            //If after this point no more disco client constructors are called it should be safe

            return discoClient;
        }
    }
}
