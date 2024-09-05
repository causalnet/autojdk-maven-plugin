package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.xml.metadata.ArchiveType;
import au.net.causal.maven.plugins.autojdk.xml.metadata.MavenJdkArtifactMetadata;
import au.net.causal.maven.plugins.autojdk.xml.metadata.ReleaseType;
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
    throws Exception {
        MavenJdkArtifactMetadata metadata = new MavenJdkArtifactMetadata(EnumSet.of(ArchiveType.ZIP, ArchiveType.TAR_GZ), ReleaseType.GA);

        String result;
        try (StringWriter writer = new StringWriter()) {
            JAXB.marshal(metadata, writer);
            result = writer.toString();
        }

        log.debug(result);
        assertThat(result).contains("<archiveType>ZIP</archiveType>")
                          .contains("<archiveType>TAR_GZ</archiveType>");
    }

    @Test
    void testDeserializeGaReleaseType()
    {
        String xml = "<jdk xmlns='https://autojdk.causal.net.au/metadata/1.0'><archiveType>ZIP</archiveType><releaseType>GA</releaseType></jdk>";
        MavenJdkArtifactMetadata result;
        try (StringReader reader = new StringReader(xml))
        {
            result = JAXB.unmarshal(reader, MavenJdkArtifactMetadata.class);
        }

        assertThat(result.getArchiveTypes()).containsExactly(ArchiveType.ZIP);
        assertThat(result.getReleaseType()).isEqualTo(ReleaseType.GA);
    }

    @Test
    void testDeserializeEaReleaseType()
    {
        String xml = "<jdk xmlns='https://autojdk.causal.net.au/metadata/1.0'><archiveType>ZIP</archiveType><releaseType>EA</releaseType></jdk>";
        MavenJdkArtifactMetadata result;
        try (StringReader reader = new StringReader(xml))
        {
            result = JAXB.unmarshal(reader, MavenJdkArtifactMetadata.class);
        }

        assertThat(result.getArchiveTypes()).containsExactly(ArchiveType.ZIP);
        assertThat(result.getReleaseType()).isEqualTo(ReleaseType.EA);
    }

    @Test
    void testDeserializeDefaultReleaseType()
    {
        String xml = "<jdk xmlns='https://autojdk.causal.net.au/metadata/1.0'><archiveType>ZIP</archiveType></jdk>";
        MavenJdkArtifactMetadata result;
        try (StringReader reader = new StringReader(xml))
        {
            result = JAXB.unmarshal(reader, MavenJdkArtifactMetadata.class);
        }

        assertThat(result.getArchiveTypes()).containsExactly(ArchiveType.ZIP);
        assertThat(result.getReleaseType()).isEqualTo(ReleaseType.GA); //Should default to GA if element is not present
    }
}
