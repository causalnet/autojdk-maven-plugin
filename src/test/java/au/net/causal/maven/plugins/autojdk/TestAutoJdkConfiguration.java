package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.AutoJdkConfiguration.ExtensionExclusion;
import jakarta.xml.bind.JAXB;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

class TestAutoJdkConfiguration
{
    private static final Logger log = LoggerFactory.getLogger(TestAutoJdkConfiguration.class);
    private static final DatatypeFactory datatypeFactory = DatatypeFactory.newDefaultInstance();

    @Test
    void testSerialization()
    throws IOException
    {
        AutoJdkConfiguration config = new AutoJdkConfiguration();
        config.getVendors().add("zulu");
        config.getVendors().add("*");

        config.getExtensionExclusions().add(new ExtensionExclusion("(,8)", "[8,9)"));

        config.setJdkUpdatePolicy(new AutoJdkConfiguration.JdkUpdatePolicySpec(new JdkUpdatePolicy.EveryDuration(datatypeFactory.newDuration("PT30M"))));
        //config.setJdkUpdatePolicy(new AutoJdkConfiguration.JdkUpdatePolicySpec(new JdkUpdatePolicy.Always()));

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

    @Test
    void updatePolicyCheckDays()
    {
        JdkUpdatePolicy updatePolicy = new JdkUpdatePolicy.EveryDuration(datatypeFactory.newDuration("P5D"));

        Instant now = LocalDateTime.of(2020, 2, 1, 0, 0).toInstant(ZoneOffset.UTC);
        Instant sixDaysAgo = now.minus(6, ChronoUnit.DAYS);
        Instant fourDaysAgo = now.minus(4, ChronoUnit.DAYS);

        boolean sixDaysCheck = updatePolicy.isUpdateCheckRequired(sixDaysAgo, now);
        boolean fourDaysCheck = updatePolicy.isUpdateCheckRequired(fourDaysAgo, now);
        boolean noPreviousCheck = updatePolicy.isUpdateCheckRequired(null, now);

        assertThat(sixDaysCheck).describedAs("check required for 6 days ago").isTrue();
        assertThat(fourDaysCheck).describedAs("check required for 4 days ago").isFalse();
        assertThat(noPreviousCheck).describedAs("check required for no previous check").isTrue();
    }

    @Test
    void updatePolicyCheckMonths()
    {
        JdkUpdatePolicy updatePolicy = new JdkUpdatePolicy.EveryDuration(datatypeFactory.newDuration("P2M"));

        Instant now = LocalDateTime.of(2020, 2, 1, 0, 0).toInstant(ZoneOffset.UTC);
        Instant oneMonthAgo = LocalDateTime.of(2020, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);
        Instant threeMonthsAgo = LocalDateTime.of(2019, 11, 1, 0, 0).toInstant(ZoneOffset.UTC);

        boolean oneMonthCheck = updatePolicy.isUpdateCheckRequired(oneMonthAgo, now);
        boolean threeMonthsCheck = updatePolicy.isUpdateCheckRequired(threeMonthsAgo, now);
        boolean noPreviousCheck = updatePolicy.isUpdateCheckRequired(null, now);

        assertThat(oneMonthCheck).describedAs("check required for 1 month ago").isFalse();
        assertThat(threeMonthsCheck).describedAs("check required for 3 months ago").isTrue();
        assertThat(noPreviousCheck).describedAs("check required for no previous check").isTrue();
    }

    @Test
    void updatePolicyCheckMinutesSeconds()
    {
        JdkUpdatePolicy updatePolicy = new JdkUpdatePolicy.EveryDuration(datatypeFactory.newDuration("PT2M30S"));

        Instant now = LocalDateTime.of(2020, 2, 1, 12, 0, 0).toInstant(ZoneOffset.UTC);
        Instant twoMinutesAgo = now.minus(2, ChronoUnit.MINUTES);
        Instant threeMinutesAgo = now.minus(3, ChronoUnit.MINUTES);

        boolean twoMinuteCheck = updatePolicy.isUpdateCheckRequired(twoMinutesAgo, now);
        boolean threeMinuteCheck = updatePolicy.isUpdateCheckRequired(threeMinutesAgo, now);
        boolean noPreviousCheck = updatePolicy.isUpdateCheckRequired(null, now);

        assertThat(twoMinuteCheck).describedAs("check required for 2 minutes ago").isFalse();
        assertThat(threeMinuteCheck).describedAs("check required for 3 minutes ago").isTrue();
        assertThat(noPreviousCheck).describedAs("check required for no previous check").isTrue();
    }

    @Test
    void updatePolicyAlways()
    {
        JdkUpdatePolicy updatePolicy = new JdkUpdatePolicy.Always();

        Instant now = LocalDateTime.of(2020, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);

        boolean previousTimeCheck = updatePolicy.isUpdateCheckRequired(now.minusSeconds(10), now);
        boolean noPreviousTimeCheck = updatePolicy.isUpdateCheckRequired(null, now);

        assertThat(previousTimeCheck).describedAs("check required for previous time").isTrue();
        assertThat(noPreviousTimeCheck).describedAs("check required for no previous time").isTrue();
    }

    @Test
    void updatePolicyNever()
    {
        JdkUpdatePolicy updatePolicy = new JdkUpdatePolicy.Never();

        Instant now = LocalDateTime.of(2020, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);

        boolean previousTimeCheck = updatePolicy.isUpdateCheckRequired(now.minusSeconds(10), now);
        boolean noPreviousTimeCheck = updatePolicy.isUpdateCheckRequired(null, now);

        assertThat(previousTimeCheck).describedAs("check required for previous time").isFalse();
        assertThat(noPreviousTimeCheck).describedAs("check required for no previous time").isFalse();
    }

    //TODO think about defaults when the file is present but elements are not
}
