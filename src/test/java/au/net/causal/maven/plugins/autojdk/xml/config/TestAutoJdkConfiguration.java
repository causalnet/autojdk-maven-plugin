package au.net.causal.maven.plugins.autojdk.xml.config;

import au.net.causal.maven.plugins.autojdk.AutoJdkXmlManager;
import au.net.causal.maven.plugins.autojdk.xml.config.AutoJdkConfiguration.ExtensionExclusion;
import au.net.causal.maven.plugins.autojdk.config.ActivationProcessor;
import jakarta.xml.bind.JAXB;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.path.DefaultPathTranslator;
import org.apache.maven.model.path.ProfileActivationFilePathInterpolator;
import org.apache.maven.model.profile.activation.FileProfileActivator;
import org.apache.maven.model.profile.activation.JdkVersionProfileActivator;
import org.apache.maven.model.profile.activation.OperatingSystemProfileActivator;
import org.apache.maven.model.profile.activation.PropertyProfileActivator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;

//TODO namespaces in XML schema and support for them
//    specifically ignoring unknown namespaces when doing imports
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
        String xml = "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'><vendors>" +
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

    @Test
    void fillInDefaults(@TempDir Path tempDir)
    throws Exception
    {
        String xml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'><vendors>" +
                "    <vendor>zulu</vendor>" +
                "    <vendor>*</vendor>" +
                "</vendors></autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession());

        assertThat(configuration.getVendors()).containsExactly("zulu", AutoJdkConfiguration.WILDCARD_VENDOR);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void cascade()
    {
        AutoJdkConfiguration c1 = new AutoJdkConfiguration();
        c1.setVendors(List.of("zulu", "temurin", "*"));
        c1.setExtensionExclusions(List.of(new ExtensionExclusion("(,8)", "[8, 9)")));

        AutoJdkConfiguration c2 = new AutoJdkConfiguration();
        c2.setVendors(List.of("zulu", "*"));
        c2.setJdkUpdatePolicy(new AutoJdkConfiguration.JdkUpdatePolicySpec(new JdkUpdatePolicy.Always()));

        AutoJdkConfiguration combined = c1.combinedWith(c2);

        assertThat(combined.getVendors()).containsExactly("zulu", "*"); //prefer c2
        assertThat(combined.getJdkUpdatePolicy()).isEqualTo(new AutoJdkConfiguration.JdkUpdatePolicySpec(new JdkUpdatePolicy.Always())); //only defined in c2
        assertThat(combined.getExtensionExclusions()).isEqualTo(List.of(new ExtensionExclusion("(,8)", "[8, 9)"))); //only defined in c1
    }

    @Test
    void activationBySystemPropertySuccess(@TempDir Path tempDir)
    throws Exception
    {
        String xml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <activation>" +
                "        <property>" +
                "            <name>galahProperty</name>" +
                "            <value>myValue</value>" +
                "        </property>" +
                "    </activation>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        DefaultMavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        Properties systemProperties = new Properties();
        systemProperties.setProperty("galahProperty", "myValue");
        executionRequest.setSystemProperties(systemProperties);
        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession(executionRequest));

        assertThat(configuration.getVendors()).containsExactly("zulu", AutoJdkConfiguration.WILDCARD_VENDOR);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void activationBySystemPropertyNotIncluded(@TempDir Path tempDir)
    throws Exception
    {
        String xml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <activation>" +
                "        <property>" +
                "            <name>galahProperty</name>" +
                "            <value>myValue</value>" +
                "        </property>" +
                "    </activation>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        DefaultMavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        Properties systemProperties = new Properties();
        systemProperties.setProperty("galahProperty", "someOtherValue");
        executionRequest.setSystemProperties(systemProperties);
        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession(executionRequest));

        //Everything should be at defaults because config file was not activated
        assertThat(configuration.getVendors()).isEqualTo(AutoJdkConfiguration.DEFAULT_VENDORS);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void activationByFileExistSuccess(@TempDir Path tempDir)
    throws Exception
    {
        //Write the file we are looking for
        Path galahFile = tempDir.resolve("galahfile.txt");
        Files.createFile(galahFile);

        String xml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <activation>" +
                "        <file>" +
                "            <exists>" + galahFile.toAbsolutePath() + "</exists>" +
                "        </file>" +
                "    </activation>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        DefaultMavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        Properties systemProperties = new Properties();
        systemProperties.setProperty("galahProperty", "myValue");
        executionRequest.setSystemProperties(systemProperties);
        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession(executionRequest));

        assertThat(configuration.getVendors()).containsExactly("zulu", AutoJdkConfiguration.WILDCARD_VENDOR);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void activationByFileExistNotIncluded(@TempDir Path tempDir)
    throws Exception
    {
        String xml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <activation>" +
                "        <file>" +
                "            <exists>idonotexist.txt</exists>" +
                "        </file>" +
                "    </activation>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        DefaultMavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        Properties systemProperties = new Properties();
        systemProperties.setProperty("galahProperty", "myValue");
        executionRequest.setSystemProperties(systemProperties);
        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession(executionRequest));

        //Everything should be at defaults because config file was not activated
        assertThat(configuration.getVendors()).isEqualTo(AutoJdkConfiguration.DEFAULT_VENDORS);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void activationByAutoJdkVersionSuccess(@TempDir Path tempDir)
    throws Exception
    {
        String xml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <activation>" +
                "        <autojdk-version>[,100)</autojdk-version>" +
                "    </activation>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession());

        assertThat(configuration.getVendors()).containsExactly("zulu", AutoJdkConfiguration.WILDCARD_VENDOR);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void activationByAutoJdkVersionNotIncluded(@TempDir Path tempDir)
    throws Exception
    {
        String xml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <activation>" +
                "        <autojdk-version>[100,)</autojdk-version>" +
                "    </activation>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession());

        //Everything should be at defaults because config file was not activated
        assertThat(configuration.getVendors()).isEqualTo(AutoJdkConfiguration.DEFAULT_VENDORS);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void activationByHostJdkVersionSuccess(@TempDir Path tempDir)
    throws Exception
    {
        String xml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <activation>" +
                "        <host-jdk>[17,)</host-jdk>" +
                "    </activation>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        DefaultMavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        Properties systemProperties = new Properties();
        systemProperties.setProperty("java.version", "17.0.0");
        executionRequest.setSystemProperties(systemProperties);
        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession(executionRequest));

        assertThat(configuration.getVendors()).containsExactly("zulu", AutoJdkConfiguration.WILDCARD_VENDOR);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void activationByHostJdkVersionNotIncluded(@TempDir Path tempDir)
    throws Exception
    {
        String xml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <activation>" +
                "        <host-jdk>[17,)</host-jdk>" +
                "    </activation>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        DefaultMavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        Properties systemProperties = new Properties();
        systemProperties.setProperty("java.version", "11.0.0");
        executionRequest.setSystemProperties(systemProperties);
        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession(executionRequest));

        //Everything should be at defaults because config file was not activated
        assertThat(configuration.getVendors()).isEqualTo(AutoJdkConfiguration.DEFAULT_VENDORS);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    private static ActivationProcessor createActivationProcessor()
    {
        FileProfileActivator fileProfileActivator = new FileProfileActivator();
        ProfileActivationFilePathInterpolator profileActivationFilePathInterpolator = new ProfileActivationFilePathInterpolator();
        profileActivationFilePathInterpolator.setPathTranslator(new DefaultPathTranslator());
        fileProfileActivator.setProfileActivationFilePathInterpolator(profileActivationFilePathInterpolator);
        return new ActivationProcessor(fileProfileActivator, new OperatingSystemProfileActivator(), new PropertyProfileActivator(), new JdkVersionProfileActivator(), "1.0-SNAPSHOT");
    }

    private static MavenSession createMavenSession()
    {
        return createMavenSession(new DefaultMavenExecutionRequest());
    }

    private static MavenSession createMavenSession(MavenExecutionRequest executionRequest)
    {
        @SuppressWarnings("deprecation") MavenSession session = new MavenSession(null, executionRequest, null, List.of());
        return session;
    }
}
