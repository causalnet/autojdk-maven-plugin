package au.net.causal.maven.plugins.autojdk;

import io.foojay.api.discoclient.util.Helper;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Loads Foojay distributions only from a classpath resource without using the network.
 */
public class OfflineFoojayVendorService implements VendorService
{
    @Override
    public List<String> getAllVendors()
    throws VendorServiceException
    {
        try
        {
            //This Foojay/distroclient API only loads from a resource on the classpath, no network
            return DiscoClientVendorService.foojayDistributionsToVendorList(Helper.preloadDistributions().get().values());
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new VendorServiceException(e);
        }
    }
}
