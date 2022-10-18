package au.net.causal.maven.plugins.autojdk.foojay;

import au.net.causal.maven.plugins.autojdk.foojay.openapi.handler.ApiClient;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.recording.RecordSpecBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.StubImport.*;

/**
 * Superclass of foojay client test cases that use WireMock to avoid having to do real network requests.
 * <p>
 *
 * Test Foojay client but use Wiremock playback to avoid smashing their server in our tests.
 * When run with {@link #wireMockMode} as {@link WireMockMode#PLAYBACK} (the default) it will use a previously recorded session and avoid
 * hitting their servers at all.  Turning it to {@link WireMockMode#RECORD} will instead hit the real server and also record responses that can be played back later.
 * {@link WireMockMode#REAL_SERVICE} will just hit the real service without recording, useful for ensuring the real service hasn't changed since the last recording.
 * Recording may be required when changing test code since different services may be hit.
 */
@WireMockTest
class AbstractApiClientMockTestCase
{
    /**
     * Controls whether
     */
    static final WireMockMode wireMockMode = WireMockMode.PLAYBACK;

    protected static ApiClient apiClient = new FoojayClient().getApiClient(); //Make a FoojayClient just so we can get the default configured API client

    private static final String defaultFoojayApiUrl = FoojayClient.createDefaultApiClient().getBaseUri();

    @BeforeAll
    static void setUpDisco(WireMockRuntimeInfo wireMockRuntimeInfo)
    {
        //Proxy mode with HTTPS is hard to set up so just do explicit URLs for now
        //apiClient.setHttpClientBuilder(HttpClient.newBuilder().proxy(ProxySelector.of(new InetSocketAddress("localhost", wireMockRuntimeInfo.getHttpPort()))));

        if (wireMockMode != WireMockMode.REAL_SERVICE)
            apiClient.updateBaseUri(wireMockRuntimeInfo.getHttpBaseUrl());
    }

    @BeforeEach
    void setUpWireMock(WireMockRuntimeInfo wireMockRuntimeInfo)
    {
        if (wireMockMode == WireMockMode.RECORD)
        {
            wireMockRuntimeInfo.getWireMock().importStubMappings(stubImport().ignoreExisting());
            wireMockRuntimeInfo.getWireMock().startStubRecording(
                    new RecordSpecBuilder().forTarget(defaultFoojayApiUrl)
                                           .ignoreRepeatRequests()
                                           .onlyRequestsMatching(anyRequestedFor(urlMatching(".*/disco/.*"))));
        }

        //Record from api.foojay.io which is the real server
        if (wireMockMode == WireMockMode.RECORD)
            stubFor(get(urlMatching("/.*")).willReturn(aResponse().proxiedFrom(defaultFoojayApiUrl)));
    }

    @AfterEach
    void finishWireMock(WireMockRuntimeInfo wireMockRuntimeInfo)
    {
        if (wireMockMode == WireMockMode.RECORD)
            wireMockRuntimeInfo.getWireMock().stopStubRecording();
    }

    protected static enum WireMockMode
    {
        /**
         * Play back previously recorded responses.
         */
        PLAYBACK,

        /**
         * Perform requests to real service and record requests and responses.
         */
        RECORD,

        /**
         * Perform requests to real service without recording responses.
         */
        REAL_SERVICE
    }
}
