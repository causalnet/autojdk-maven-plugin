package au.net.causal.maven.plugins.autojdk;

import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.pkg.Distribution;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class VendorConfiguration
{
    private final DiscoClient discoClient;

    public VendorConfiguration(DiscoClient discoClient)
    {
        this.discoClient = Objects.requireNonNull(discoClient);
    }

    public List<String> getAllVendors()
    {
        return discoClient.getDistros().values().stream().map(Distribution::getApiString).collect(Collectors.toUnmodifiableList());
    }

}
