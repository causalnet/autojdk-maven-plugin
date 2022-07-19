package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.AutoJdkConfiguration.ExtensionExclusion;
import jakarta.xml.bind.JAXB;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.*;

class TestAutoJdkConfiguration
{
    private static final Logger log = LoggerFactory.getLogger(TestAutoJdkConfiguration.class);

    @Test
    void testSerialization()
    throws IOException
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration();
        config.getVendors().add("zulu");
        config.getVendors().add("*");

        config.getExtensionExclusions().add(new ExtensionExclusion("(,8)", "[8,9)"));

        try (StringWriter out = new StringWriter())
        {
            JAXB.marshal(config, out);
            log.debug(out.toString());
            assertThat(out.toString()).contains("<vendor>zulu</vendor>", "<vendor>*</vendor>");
        }
    }

    @Test
    void testDeserialization()
    {
        String xml = "<autojdk-configuration><vendors>" +
                     "    <vendor>zulu</vendor>" +
                     "    <vendor>*</vendor>" +
                     "</vendors></autojdk-configuration>";

        try (StringReader in = new StringReader(xml))
        {
            AutoJdkConfiguration result = JAXB.unmarshal(in, AutoJdkConfiguration.class);
            log.debug(result.getVendors().toString());

            assertThat(result.getVendors()).containsExactly("zulu", "*");
        }
    }

    //TODO think about defaults when the file is present but elements are not
}
