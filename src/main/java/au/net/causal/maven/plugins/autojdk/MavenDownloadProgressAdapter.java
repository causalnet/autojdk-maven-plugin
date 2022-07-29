package au.net.causal.maven.plugins.autojdk;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class MavenDownloadProgressAdapter implements FileDownloader.DownloadProgressListener
{
    private final RepositorySystemSession repoSession;

    private final Map<URL, TransferResource> resourceMap = new ConcurrentHashMap<>();

    public MavenDownloadProgressAdapter(RepositorySystemSession repoSession)
    {
        this.repoSession = Objects.requireNonNull(repoSession);
    }

    private TransferResource transferResource(FileDownloader.DownloadEvent event)
    {
        //Attempt lookup from cache first
        return resourceMap.computeIfAbsent(event.getDownloadUrl(), url -> createTransferResource(event));
    }

    private TransferResource createTransferResource(FileDownloader.DownloadEvent event)
    {
        try
        {
            URI uri = event.getDownloadUrl().toURI();
            URI parentUri = uri.resolve(".");
            String childFile = parentUri.relativize(uri).toString();
            TransferResource res = new TransferResource(null, parentUri.toString(), childFile, null, null);

            if (event.getDownloadSize() >= 0L)
                res.setContentLength(event.getDownloadSize());

            return res;
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void downloadStarted(FileDownloader.DownloadStartedEvent event)
    {
        TransferResource res = transferResource(event);
        TransferEvent.Builder b = new TransferEvent.Builder(repoSession, res).setRequestType(TransferEvent.RequestType.GET);

        try
        {
            repoSession.getTransferListener().transferInitiated(b.setType(TransferEvent.EventType.INITIATED).build());
            repoSession.getTransferListener().transferStarted(b.setType(TransferEvent.EventType.STARTED).build());
        }
        catch (TransferCancelledException e)
        {
            //Just ignore for now
        }
    }

    @Override
    public void downloadProgress(FileDownloader.DownloadProgressEvent event)
    {
        TransferResource res = transferResource(event);
        TransferEvent.Builder b = new TransferEvent.Builder(repoSession, res).setRequestType(TransferEvent.RequestType.GET);

        b = b.addTransferredBytes(event.getBytesDownloaded());

        try
        {
            repoSession.getTransferListener().transferProgressed(b.setType(TransferEvent.EventType.PROGRESSED).build());
        }
        catch (TransferCancelledException e)
        {
            //Just ignore for now
        }
    }

    @Override
    public void downloadCompleted(FileDownloader.DownloadCompletedEvent event)
    {
        TransferResource res = transferResource(event);
        TransferEvent.Builder b = new TransferEvent.Builder(repoSession, res).setRequestType(TransferEvent.RequestType.GET);

        b = b.addTransferredBytes(event.getDownloadSize());

        repoSession.getTransferListener().transferSucceeded(b.setType(TransferEvent.EventType.SUCCEEDED).build());

        //Last event, so remove from the map
        resourceMap.remove(event.getDownloadUrl());
    }

    @Override
    public void downloadFailed(FileDownloader.DownloadFailedEvent event)
    {
        TransferResource res = transferResource(event);
        TransferEvent.Builder b = new TransferEvent.Builder(repoSession, res).setRequestType(TransferEvent.RequestType.GET);
        b = b.setException(event.getError());
        repoSession.getTransferListener().transferFailed(b.setType(TransferEvent.EventType.FAILED).build());

        //Last event, so remove from the map
        resourceMap.remove(event.getDownloadUrl());
    }
}
