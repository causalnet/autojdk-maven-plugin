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
class TestResumableFileDownloader
{
    private ResumableFileDownloader downloader;

    @BeforeEach
    void setUpDownloader(@TempDir Path tempDir)
    {
        downloader = new ResumableFileDownloader(tempDir, new SimpleFileDownloader.NoProxySelector());
    }

    @Test
    void successfulDownloadWithoutResumeSupportWithoutContentLength(WireMockRuntimeInfo wmRuntimeInfo)
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
    void successfulDownloadWithoutResumeSupportWithContentLength(WireMockRuntimeInfo wmRuntimeInfo)
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
    void successfulDownloadSingleAttempt(WireMockRuntimeInfo wmRuntimeInfo)
    throws IOException
    {
        stubFor(get("/myfile").willReturn(ok("file content")
                                      .withHeader(HttpHeaders.ACCEPT_RANGES, "bytes")));

        try (FileDownloader.Download download = downloader.downloadFile(new URL(wmRuntimeInfo.getHttpBaseUrl() + "/myfile")))
        {
            assertThat(download.getUrl()).hasPath("/myfile");
            assertThat(download.getFile()).hasContent("file content");
        }
    }

    @Test
    void truncatedDownloadWithResume(WireMockRuntimeInfo wmRuntimeInfo)
    throws IOException
    {
        String messagePart1 = "Part 1\n";
        String messagePart2 = "Part 2\n";
        String fullMessage = messagePart1 + messagePart2;

        stubFor(get("/myfile").willReturn(ok(messagePart1)
                                      .withHeader(HttpHeaders.ACCEPT_RANGES, "bytes") //for resumable downloads
                                      .withHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(fullMessage.length())))); //full length, but only part 1 will actually be received
        stubFor(get("/myfile").withHeader(HttpHeaders.RANGE, equalTo("bytes=" + messagePart1.length() + "-"))
                              .willReturn(ok(messagePart2)
                                      .withHeader(HttpHeaders.ACCEPT_RANGES, "bytes") //for resumable downloads
                                      .withHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(messagePart2.length())))); //remaining download part 2

        try (FileDownloader.Download download = downloader.downloadFile(new URL(wmRuntimeInfo.getHttpBaseUrl() + "/myfile")))
        {
            assertThat(download.getUrl()).hasPath("/myfile");
            assertThat(download.getFile()).hasContent(fullMessage);
        }
    }

    @Test
    void truncatedDownloadIsDetectedWhenResumeIsNotAvailable(WireMockRuntimeInfo wmRuntimeInfo)
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
