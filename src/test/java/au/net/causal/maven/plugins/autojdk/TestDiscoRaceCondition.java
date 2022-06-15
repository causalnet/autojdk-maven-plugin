package au.net.causal.maven.plugins.autojdk;

import com.google.common.util.concurrent.MoreExecutors;
import io.foojay.api.discoclient.DiscoClient;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Demonstrates a race condition with initializing DiscoClients.
 * Workaround is to make sure we only construct one as a singleton.
 */
class TestDiscoRaceCondition extends AbstractDiscoTestCase
{
    private static final Logger log = LoggerFactory.getLogger(TestDiscoRaceCondition.class);

    private DiscoClient discoClient()
    {
        //return new DiscoClient();
        return DiscoClientSingleton.discoClient();
    }

    @Test
    void testInitialization()
    {
        ExecutorService executor = Executors.newFixedThreadPool(64);

        //First one just to get the real size
        int expectedSize = discoClient().getDistros().keySet().size();

        AtomicInteger mismatchCounter = new AtomicInteger();

        for (int i = 0; i < 500000; i++)
        {
            executor.submit(() ->
            {
                DiscoClient client = discoClient();
                int size = client.getDistros().keySet().size();
                if (size != expectedSize)
                {
                    log.info("Distro count mismatch: " + size + " / " + expectedSize);
                    mismatchCounter.incrementAndGet();
                }
            });
        }

        MoreExecutors.shutdownAndAwaitTermination(executor, 10, TimeUnit.MINUTES);

        assertThat(mismatchCounter).describedAs("Race condition detected - all calls should return distros of same size").hasValue(0);
    }
}
