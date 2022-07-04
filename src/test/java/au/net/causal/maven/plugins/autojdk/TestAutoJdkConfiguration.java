package au.net.causal.maven.plugins.autojdk;

import jakarta.xml.bind.JAXB;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

class TestAutoJdkConfiguration
{
    @Test
    void test()
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration();
        config.getVendors().add("zulu");
        config.getVendors().add("*");

        StringWriter out = new StringWriter();
        JAXB.marshal(config, out);

        System.out.println(out);
    }
}
