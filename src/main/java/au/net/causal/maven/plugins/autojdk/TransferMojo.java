package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;

/**
 * Play around with giving Maven file download events from custom sources and observing the download progress display in the console.
 */
@Mojo(name = "transfer")
public class TransferMojo extends AbstractMojo
{
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        TransferEvent.Builder b = new TransferEvent.Builder(repoSession, new TransferResource("galah", "http://galah.galah.galah", "galah.txt", null, null).setContentLength(1_000_000L))
                .setRequestType(TransferEvent.RequestType.GET);

        TransferEvent.Builder b2 = new TransferEvent.Builder(repoSession, new TransferResource("galah", "http://galah.galah.galah", "cockatoo.txt", null, null).setContentLength(2_000_000L))
                .setRequestType(TransferEvent.RequestType.GET);

        try
        {
            repoSession.getTransferListener().transferInitiated(b.setType(TransferEvent.EventType.INITIATED).build());
            repoSession.getTransferListener().transferInitiated(b2.setType(TransferEvent.EventType.INITIATED).build());
            Thread.sleep(10L);
            repoSession.getTransferListener().transferStarted(b.setType(TransferEvent.EventType.STARTED).build());
            repoSession.getTransferListener().transferStarted(b2.setType(TransferEvent.EventType.STARTED).build());
            Thread.sleep(1000L);

            for (int i = 0; i < 10; i++)
            {
                repoSession.getTransferListener().transferProgressed(b.setType(TransferEvent.EventType.PROGRESSED).addTransferredBytes(100_000L).build());
                repoSession.getTransferListener().transferProgressed(b2.setType(TransferEvent.EventType.PROGRESSED).addTransferredBytes(200_000L).build());
                Thread.sleep(500L);
            }

            repoSession.getTransferListener().transferSucceeded(b.setType(TransferEvent.EventType.SUCCEEDED).build());
            repoSession.getTransferListener().transferSucceeded(b2.setType(TransferEvent.EventType.SUCCEEDED).build());
        }
        catch (InterruptedException e)
        {
            throw new MojoExecutionException("Interrupted.", e);
        }
        catch (TransferCancelledException e)
        {
            throw new MojoExecutionException("Transfer cancelled.", e);
        }
    }
}
