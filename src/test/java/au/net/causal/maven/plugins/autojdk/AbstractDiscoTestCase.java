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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.StubImport.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Superclass of discovery client test cases that use WireMock to avoid having to do real network requests.
 * <p>
 *
 * Test Foojay disco client but use Wiremock playback to avoid smashing their server in our tests.
 * When run with {@link #WIREMOCK_RECORD} as false (the default) it will use a previously recorded session and avoid
 * hitting their servers at all.  Turning it to true will instead hit the real server and also record responses that can be played back later.
 * Recording may be required when changing test code since different services may be hit.
 */
@WireMockTest
abstract class AbstractDiscoTestCase
{
    private static final Logger log = LoggerFactory.getLogger(AbstractDiscoTestCase.class);

    /**
     * Set this to true to record from the real system so we can play back later.
     */
    static final boolean WIREMOCK_RECORD = false;

    private static final Properties originalProperties = new Properties();

    @BeforeAll
    static void setUpDisco(WireMockRuntimeInfo wireMockRuntimeInfo)
    {
        //Ensure user home exists - while it's not critical it does there is an ugly error message if it doesn't exist
        Path homeDirectory = Path.of(Constants.HOME_FOLDER);
        if (Files.notExists(homeDirectory))
        {
            try
            {
                Files.createDirectories(homeDirectory);
            }
            catch (IOException e)
            {
                log.warn("Failed to create DiscoClient home directory: " + e, e);
            }
        }

        Properties properties = PropertyManager.INSTANCE.getProperties();
        originalProperties.putAll(properties);

        properties.setProperty(Constants.PROPERTY_KEY_DISTRIBUTION_JSON_URL, wireMockRuntimeInfo.getHttpBaseUrl() + "/distributions.json");
        properties.setProperty(Constants.PROPERTY_KEY_DISCO_URL, wireMockRuntimeInfo.getHttpBaseUrl());
    }

    @AfterAll
    static void restoreDisco(WireMockRuntimeInfo wireMockRuntimeInfo)
    {
        Properties properties = PropertyManager.INSTANCE.getProperties();
        properties.putAll(originalProperties);
        originalProperties.clear();
    }

    @BeforeEach
    void setUpWireMock(WireMockRuntimeInfo wireMockRuntimeInfo)
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

        //Record from api.foojay.io which is the real server
        if (WIREMOCK_RECORD)
            stubFor(get(urlMatching("/.*")).willReturn(aResponse().proxiedFrom("https://api.foojay.io")));

        //When getting remote distributions, use our own distributions resource
        //Normally this comes from a github page https://github.com/foojayio/distributions/raw/main/distributions.json
        //If there is a need to update this, download the file in a browser and paste content into test-distributions.json in the test resources directory
        //Getting wiremock to properly handle multiple URL hosts with redirects (which the github one does) is just too painful
        stubFor(get(urlMatching(".*/distributions.json")).willReturn(aResponse().withBody(Resources.toByteArray(AbstractDiscoTestCase.class.getResource("/test-distributions.json")))));

        //Preload distributions into distro client
        //It's static and racey and we don't want this to kick off after tests have finished in a background thread after
        //wiremock is torn down - it can result in random "Request was not matched" warnings

        //Wait for client to be initialized - this call awaits distributions to be initialized and read from the network (actually wiremock), statically
        //When it is done, no more disco client constructors should be called to avoid the race condition
        DiscoClient discoClient = DiscoClientSingleton.discoClient();

        assertThat(discoClient.isInitialzed()).isTrue();
    }

    @AfterEach
    void finishWireMock(WireMockRuntimeInfo wireMockRuntimeInfo)
    {
        if (WIREMOCK_RECORD)
            wireMockRuntimeInfo.getWireMock().stopStubRecording();
    }
}
