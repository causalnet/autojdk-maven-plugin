@XmlSchema(
        namespace = "https://autojdk.causal.net.au/metadata/1.0",
        elementFormDefault = XmlNsForm.QUALIFIED
)
@XmlSchemaTypes({
        @XmlSchemaType(type = Architecture.class, name = "string"),
        @XmlSchemaType(type = OperatingSystem.class, name = "string")
})
package au.net.causal.maven.plugins.autojdk.xml.metadata;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlSchemaTypes;
