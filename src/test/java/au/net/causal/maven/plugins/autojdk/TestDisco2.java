package au.net.causal.maven.plugins.autojdk;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.recording.RecordSpecBuilder;
import com.google.common.io.Resources;
import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.PropertyManager;
import io.foojay.api.discoclient.pkg.Distribution;
import io.foojay.api.discoclient.util.Constants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.StubImport.*;

@WireMockTest
class TestDisco2
{
    /**
     * Set this to true to record from the real system so we can play back later.
     */
    private static final boolean WIREMOCK_RECORD = false;

    private static final Properties originalProperties = new Properties();

    @BeforeAll
    private static void setUpDisco(WireMockRuntimeInfo wireMockRuntimeInfo)
    {
        Properties properties = PropertyManager.INSTANCE.getProperties();
        originalProperties.putAll(properties);

        properties.setProperty(Constants.PROPERTY_KEY_DISTRIBUTION_JSON_URL, wireMockRuntimeInfo.getHttpBaseUrl() + "/distributions.json");
        properties.setProperty(Constants.PROPERTY_KEY_DISCO_URL, wireMockRuntimeInfo.getHttpBaseUrl());

        if (WIREMOCK_RECORD)
        {
            wireMockRuntimeInfo.getWireMock().importStubMappings(stubImport().deleteAllExistingStubsNotInImport());
            wireMockRuntimeInfo.getWireMock().startStubRecording(new RecordSpecBuilder().forTarget("https://api.foojay.io").ignoreRepeatRequests().onlyRequestsMatching(anyRequestedFor(urlMatching(".*/disco/.*"))));
        }
    }

    @AfterAll
    private static void restoreDisco(WireMockRuntimeInfo wireMockRuntimeInfo)
    {
        Properties properties = PropertyManager.INSTANCE.getProperties();
        properties.putAll(originalProperties);
        originalProperties.clear();

        if (WIREMOCK_RECORD)
            wireMockRuntimeInfo.getWireMock().stopStubRecording();
    }

    @Test
    void test()
    throws Exception
    {
        stubFor(get(urlMatching("/distributions.json")).willReturn(aResponse().withBody(Resources.toByteArray(DiscoClient.class.getResource(Constants.DISTRIBUTION_JSON)))));
        if (WIREMOCK_RECORD)
            stubFor(get(urlMatching("/.*")).willReturn(aResponse().proxiedFrom("https://api.foojay.io")));

        DiscoClient discoClient = new DiscoClient();
        List<Distribution> distributions = discoClient.getDistributions();
        distributions.forEach(System.out::println);

        distributions = discoClient.getDistributions();
        distributions.forEach(System.out::println);
    }
}
