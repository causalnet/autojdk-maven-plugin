package au.net.causal.maven.plugins.autojdk;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.recording.RecordSpecBuilder;
import com.google.common.io.Resources;
import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.PropertyManager;
import io.foojay.api.discoclient.util.Constants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.StubImport.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Superclass of discovery client test cases that use WireMock to avoid having to do real network requests.
 *
 * Test Foojay disco client but use Wiremock playback to avoid smashing their server in our tests.
 * When run with {@link #WIREMOCK_RECORD} as false (the default) it will use a previously recorded session and avoid
 * hitting their servers at all.  Turning it to true will instead hit the real server and also record responses that can be played back later.
 * Recording may be required when changing test code since different services may be hit.
 */
@WireMockTest
abstract class AbstractDiscoTestCase
{
    /**
     * Set this to true to record from the real system so we can play back later.
     */
    static final boolean WIREMOCK_RECORD = false;

    private static final Properties originalProperties = new Properties();

    @BeforeAll
    private static void setUpDisco(WireMockRuntimeInfo wireMockRuntimeInfo)
    {
        Properties properties = PropertyManager.INSTANCE.getProperties();
        originalProperties.putAll(properties);

        properties.setProperty(Constants.PROPERTY_KEY_DISTRIBUTION_JSON_URL, wireMockRuntimeInfo.getHttpBaseUrl() + "/distributions.json");
        properties.setProperty(Constants.PROPERTY_KEY_DISCO_URL, wireMockRuntimeInfo.getHttpBaseUrl());
    }

    @AfterAll
    private static void restoreDisco(WireMockRuntimeInfo wireMockRuntimeInfo)
    {
        Properties properties = PropertyManager.INSTANCE.getProperties();
        properties.putAll(originalProperties);
        originalProperties.clear();
    }

    @BeforeEach
    private void setUpWireMock(WireMockRuntimeInfo wireMockRuntimeInfo)
    throws IOException
    {
        if (WIREMOCK_RECORD)
        {
            wireMockRuntimeInfo.getWireMock().importStubMappings(stubImport());
            wireMockRuntimeInfo.getWireMock().startStubRecording(
                    new RecordSpecBuilder().forTarget("https://api.foojay.io")
                                           .ignoreRepeatRequests()
                                           .onlyRequestsMatching(anyRequestedFor(urlMatching(".*/disco/.*"))));
        }

        //When getting remote distributions, just use Foojay's local resource
        //Normally this comes from a github page
        stubFor(get(urlMatching("/distributions.json")).willReturn(aResponse().withBody(Resources.toByteArray(DiscoClient.class.getResource(Constants.DISTRIBUTION_JSON)))));

        //Record from api.foojay.io which is the real server
        if (WIREMOCK_RECORD)
            stubFor(get(urlMatching("/.*")).willReturn(aResponse().proxiedFrom("https://api.foojay.io")));

        //Preload distributions into distro client
        //It's static and racey and we don't want this to kick off after tests have finished in a background thread after
        //wiremock is torn down - it can result in random "Request was not matched" warnings

        //Wait for client to be initialized - this call awaits distributions to be initialized and read from the network (actually wiremock), statically
        //When it is done, no more disco client constructors should be called to avoid the race condition
        DiscoClient discoClient = DiscoClientSingleton.discoClient();

        assertThat(discoClient.isInitialzed()).isTrue();
    }

    @AfterEach
    private void finishWireMock(WireMockRuntimeInfo wireMockRuntimeInfo)
    {
        if (WIREMOCK_RECORD)
            wireMockRuntimeInfo.getWireMock().stopStubRecording();
    }
}
