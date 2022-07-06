package au.net.causal.maven.plugins.autojdk;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User-global configuration for AutoJDK.
 */
@XmlRootElement(name = "autojdk-configuration")
public class AutoJdkConfiguration
{
    /**
     * Special vendor name that can be used to substitute any other known vendor that is not already in the list.
     */
    public static final String WILDCARD_VENDOR = "*";

    /**
     * Default vendor preference order.  These were selected to provide maximum compatibility.
     */
    static final List<String> DEFAULT_VENDORS = List.of(
            "zulu", //Zulu seems to always work and is present all the way back to JDK 6
            "liberica", //Next ones are pretty solid too but not available on all platforms
            "corretto",
            "oracle_open_jdk",
            "temurin",
            WILDCARD_VENDOR //Still allow everything else if the above ones aren't available for a particular platform/JDK version combination
    );

    private final List<String> vendors = new ArrayList<>();

    public static AutoJdkConfiguration defaultAutoJdkConfiguration()
    {
        return new AutoJdkConfiguration(DEFAULT_VENDORS);
    }

    public AutoJdkConfiguration()
    {
        this(Collections.emptyList());
    }

    public AutoJdkConfiguration(List<String> vendors)
    {
        this.vendors.addAll(vendors);
    }

    /**
     * List of JDK vendors that will be used/preferred in the order they are specified.
     * The special entry {@value WILDCARD_VENDOR} will match all vendors and can be used in the last place to allow any other vendors with matching JDKs should there be no previous matches.
     */
    @XmlElementWrapper
    @XmlElement(name = "vendor")
    public List<String> getVendors()
    {
        return vendors;
    }

    public void setVendors(List<String> vendors)
    {
        this.vendors.clear();
        this.vendors.addAll(vendors);
    }
}
