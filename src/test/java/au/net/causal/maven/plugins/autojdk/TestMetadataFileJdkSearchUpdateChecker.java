package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import jakarta.xml.bind.JAXBException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

class TestMetadataFileJdkSearchUpdateChecker
{
    private static final Logger log = LoggerFactory.getLogger(TestMetadataFileJdkSearchUpdateChecker.class);

    private MetadataFileJdkSearchUpdateChecker checker;
    private Path metadataFile;

    @BeforeEach
    void setUp(@TempDir Path tempDir)
    throws JAXBException
    {
        metadataFile = tempDir.resolve("metadata.xml");
        checker = new MetadataFileJdkSearchUpdateChecker(metadataFile, new AutoJdkXmlManager());
    }

    @Test
    void lastCheckTimeWhenNoMetadataFileExists()
    throws Exception
    {
        assertThat(metadataFile).doesNotExist();

        Instant lastCheckTime = checker.getLastCheckTime(new JdkSearchRequest(
                                            VersionRange.createFromVersionSpec("[17, 18)"),
                                            null, null, null, ReleaseType.GA));
        assertThat(lastCheckTime).isNull();
    }

    @Test
    void lastCheckIsPersistedAndRetrieved()
    throws Exception
    {
        JdkSearchRequest searchRequest = new JdkSearchRequest(
                                                VersionRange.createFromVersionSpec("[17, 18)"),
                                                null, null, null, ReleaseType.GA);
        Instant checkTime = LocalDateTime.of(2020, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);

        //Save the check time
        checker.saveLastCheckTime(searchRequest, checkTime);

        //Debugging - print file contents
        log.debug(Files.readString(metadataFile));

        //Validate we can retrieve the time back out
        Instant retrievedTime = checker.getLastCheckTime(searchRequest);

        assertThat(retrievedTime).isEqualTo(checkTime);
    }

    @Test
    void multipleSearchesHaveTheirOwnTimes()
    throws Exception
    {
        JdkSearchRequest searchRequest1 = new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[17, 18)"),
                null, null, null, ReleaseType.GA);
        JdkSearchRequest searchRequest2 = new JdkSearchRequest(
                VersionRange.createFromVersionSpec("[17, 18)"),
                Architecture.X64, OperatingSystem.LINUX, "zulu", ReleaseType.GA);
        Instant checkTime1 = LocalDateTime.of(2020, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);
        Instant checkTime2 = LocalDateTime.of(2021, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);

        //Save the check times
        checker.saveLastCheckTime(searchRequest1, checkTime1);
        checker.saveLastCheckTime(searchRequest2, checkTime2);

        //Debugging - print file contents
        log.debug(Files.readString(metadataFile));

        //Validate we can retrieve the time back out
        Instant retrievedTime1 = checker.getLastCheckTime(searchRequest1);
        Instant retrievedTime2 = checker.getLastCheckTime(searchRequest2);

        assertThat(retrievedTime1).isEqualTo(checkTime1);
        assertThat(retrievedTime2).isEqualTo(checkTime2);
    }
}
