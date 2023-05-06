package au.net.causal.maven.plugins.autojdk.config;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.glassfish.jaxb.core.v2.model.core.ClassInfo;
import org.glassfish.jaxb.core.v2.model.core.PropertyInfo;
import org.glassfish.jaxb.runtime.api.JAXBRIContext;
import org.glassfish.jaxb.runtime.v2.model.runtime.RuntimeClassInfo;
import org.glassfish.jaxb.runtime.v2.model.runtime.RuntimeNonElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Arrays;

class TestCombinableConfiguration
{
    private JAXBContext jaxbContext;

    @BeforeEach
    void setUp()
    throws JAXBException
    {
        jaxbContext = JAXBContext.newInstance(SampleConfig.class);
    }

    @Test
    void test()
    throws JAXBException
    {
        SampleConfig config = new SampleConfig();
        config.getIncludes().add("galah.txt");
        config.setDataItems(Arrays.asList("one", "two"));


        StringWriter w = new StringWriter();

        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(config, w);

        System.out.println(w);
    }

    @Test
    void test2()
    throws Exception
    {
        RuntimeNonElement info = ((JAXBRIContext)jaxbContext).getRuntimeTypeInfoSet().getClassInfo(SampleConfig.class);
        System.out.println(info);

        ClassInfo<?, ?> classInfo = (ClassInfo<?, ?>)info;

        var props = classInfo.getProperties();
        for (PropertyInfo<?, ?> prop : props)
        {
            System.out.println(prop.getName() + ": " + prop.kind());
        }
    }
}
