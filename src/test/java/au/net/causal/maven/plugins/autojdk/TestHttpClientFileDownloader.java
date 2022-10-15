package au.net.causal.maven.plugins.autojdk;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.google.common.net.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@WireMockTest
class TestHttpClientFileDownloader
{
    private HttpClientFileDownloader downloader;

    @BeforeEach
    void setUpDownloader(@TempDir Path tempDir)
    {
        downloader = new HttpClientFileDownloader(tempDir);
    }

    @Test
    void successfulDownloadWithoutContentLength(WireMockRuntimeInfo wmRuntimeInfo)
    throws IOException
    {
        stubFor(get("/myfile").willReturn(ok("file content")));

        try (FileDownloader.Download download = downloader.downloadFile(new URL(wmRuntimeInfo.getHttpBaseUrl() + "/myfile")))
        {
            assertThat(download.getUrl()).hasPath("/myfile");
            assertThat(download.getFile()).hasContent("file content");
        }
    }

    @Test
    void successfulDownloadWithContentLength(WireMockRuntimeInfo wmRuntimeInfo)
    throws IOException
    {
        stubFor(get("/myfile").willReturn(ok("file content")
                                      .withHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf("file content".length()))));

        try (FileDownloader.Download download = downloader.downloadFile(new URL(wmRuntimeInfo.getHttpBaseUrl() + "/myfile")))
        {
            assertThat(download.getUrl()).hasPath("/myfile");
            assertThat(download.getFile()).hasContent("file content");
        }
    }

    @Test
    void truncatedDownloadIsDetected(WireMockRuntimeInfo wmRuntimeInfo)
    {
        stubFor(get("/myfile").willReturn(ok("file content")
                                      .withHeader(HttpHeaders.CONTENT_LENGTH, "1000"))); //content length is 1000, but actual content is much shorter, so truncated

        TruncatedDownloadException truncationError = catchThrowableOfType(() ->
        {
            try (FileDownloader.Download ignored = downloader.downloadFile(new URL(wmRuntimeInfo.getHttpBaseUrl() + "/myfile")))
            {
            }
        }, TruncatedDownloadException.class);
        assertThat(truncationError.getUrl()).hasPath("/myfile");
        assertThat(truncationError.getExpectedLength()).isEqualTo(1000L);
        assertThat(truncationError.getActualLength()).isEqualTo("file content".length());
    }

    @Test
    void missingFileProducesFileNotFoundException(WireMockRuntimeInfo wmRuntimeInfo)
    {
        stubFor(get("/myfile").willReturn(notFound()));

        assertThatIOException().isThrownBy(() ->
        {
            try (FileDownloader.Download ignored = downloader.downloadFile(new URL(wmRuntimeInfo.getHttpBaseUrl() + "/myfile")))
            {
            }
        }).isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void downloadErrorProducesError(WireMockRuntimeInfo wmRuntimeInfo)
    {
        stubFor(get("/myfile").willReturn(serverError()));

        assertThatIOException().isThrownBy(() ->
        {
            try (FileDownloader.Download ignored = downloader.downloadFile(new URL(wmRuntimeInfo.getHttpBaseUrl() + "/myfile")))
            {
            }
        }).isNotInstanceOfAny(FileNotFoundException.class, TruncatedDownloadException.class);
    }
}
