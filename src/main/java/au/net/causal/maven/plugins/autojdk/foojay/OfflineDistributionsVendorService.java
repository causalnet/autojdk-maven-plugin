package au.net.causal.maven.plugins.autojdk.foojay;

import au.net.causal.maven.plugins.autojdk.VendorService;
import au.net.causal.maven.plugins.autojdk.VendorServiceException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Loads distributions only from a classpath resource without using the network.
 */
public class OfflineDistributionsVendorService implements VendorService
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<String> getAllVendors()
    throws VendorServiceException
    {
        URL resource = OfflineDistributionsVendorService.class.getResource("distributions.json");
        if (resource == null)
            throw new VendorServiceException("Missing classpath resource: distributions.json");

        try
        {
            List<JdkDistribution> distributions = objectMapper.readValue(resource, new TypeReference<>(){});
            return distributions.stream().map(JdkDistribution::getApiParameter).collect(Collectors.toList());
        }
        catch (IOException e)
        {
            throw new VendorServiceException("Error reading distributions.json: " + e, e);
        }
    }
}
