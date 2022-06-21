package au.net.causal.maven.plugins.autojdk;

import jakarta.xml.bind.JAXB;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class TestMavenJdkArtifactMetadata
{
    private static final Logger log = LoggerFactory.getLogger(TestMavenJdkArtifactMetadata.class);

    @Test
    void testSerialize()
    throws Exception
    {
        MavenJdkArtifactMetadata metadata = new MavenJdkArtifactMetadata(EnumSet.of(ArchiveType.ZIP, ArchiveType.TAR_GZ));

        String result;
        try (StringWriter writer = new StringWriter())
        {
            JAXB.marshal(metadata, writer);
            result = writer.toString();
        }

        log.debug(result);
        assertThat(result).contains("<archiveType>ZIP</archiveType>");
        assertThat(result).contains("<archiveType>TAR_GZ</archiveType>");
    }

    @Test
    void testDeserialize()
    {
        String xml = "<jdk><archiveType>ZIP</archiveType></jdk>";
        MavenJdkArtifactMetadata result;
        try (StringReader reader = new StringReader(xml))
        {
            result = JAXB.unmarshal(reader, MavenJdkArtifactMetadata.class);
        }

        assertThat(result.getArchiveTypes()).containsExactly(ArchiveType.ZIP);
    }
}
