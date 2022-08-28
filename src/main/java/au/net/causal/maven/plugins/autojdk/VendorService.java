package au.net.causal.maven.plugins.autojdk;

import java.util.List;

public interface VendorService
{
    List<String> getAllVendors()
    throws VendorServiceException;
}
