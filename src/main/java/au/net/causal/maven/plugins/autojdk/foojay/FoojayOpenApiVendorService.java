package au.net.causal.maven.plugins.autojdk.foojay;

import au.net.causal.maven.plugins.autojdk.VendorService;
import au.net.causal.maven.plugins.autojdk.VendorServiceException;
import au.net.causal.maven.plugins.autojdk.foojay.openapi.handler.ApiException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FoojayOpenApiVendorService implements VendorService
{
    private final FoojayClient foojayClient;

    public FoojayOpenApiVendorService(FoojayClient foojayClient)
    {
        this.foojayClient = Objects.requireNonNull(foojayClient);
    }

    @Override
    public List<String> getAllVendors()
    throws VendorServiceException
    {
        try
        {
            List<? extends JdkDistribution> vendors = foojayClient.getDistributions(false, false, null);
            return vendors.stream()
                          .map(JdkDistribution::getApiParameter)
                          .sorted() //To have a consistent order
                          .collect(Collectors.toUnmodifiableList());
        }
        catch (ApiException e)
        {
            throw new VendorServiceException(e);
        }
    }
}
