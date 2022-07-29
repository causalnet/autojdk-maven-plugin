package au.net.causal.maven.plugins.autojdk;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestMavenDownloadProgressAdapter
{
    @Mock
    private RepositorySystemSession repoSession;

    @Mock
    private TransferListener transferListener;

    @BeforeEach
    private void setUpSession()
    {
        when(repoSession.getTransferListener()).thenReturn(transferListener);
    }

    @Test
    void resourceUrlsAreFormedCorrectly()
    throws Exception
    {
        MavenDownloadProgressAdapter adapter = new MavenDownloadProgressAdapter(repoSession);

        URL url = new URL("http://galah.galah.galah/thepath/sub/file.txt");
        adapter.downloadStarted(new FileDownloader.DownloadStartedEvent(url, 1024L));

        ArgumentCaptor<TransferEvent> initiatedEventCaptor = ArgumentCaptor.forClass(TransferEvent.class);
        ArgumentCaptor<TransferEvent> startedEventCaptor = ArgumentCaptor.forClass(TransferEvent.class);
        verify(transferListener).transferInitiated(initiatedEventCaptor.capture());
        verify(transferListener).transferStarted(startedEventCaptor.capture());

        assertThat(initiatedEventCaptor.getValue().getResource().getRepositoryUrl()).isEqualTo("http://galah.galah.galah/thepath/sub/");
        assertThat(initiatedEventCaptor.getValue().getResource().getResourceName()).isEqualTo("file.txt");
        assertThat(startedEventCaptor.getValue().getResource().getRepositoryUrl()).isEqualTo("http://galah.galah.galah/thepath/sub/");
        assertThat(startedEventCaptor.getValue().getResource().getResourceName()).isEqualTo("file.txt");

        //Ensure both events have the same resource instance - this is important or Maven displays multiple downloads instead of a single one
        assertThat(initiatedEventCaptor.getValue().getResource()).isSameAs(startedEventCaptor.getValue().getResource());
    }
}
