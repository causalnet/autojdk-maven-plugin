package au.net.causal.maven.plugins.autojdk.xml.config;

import au.net.causal.maven.plugins.autojdk.AutoJdkXmlManager;
import au.net.causal.maven.plugins.autojdk.config.ActivationProcessor;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.path.DefaultPathTranslator;
import org.apache.maven.model.path.ProfileActivationFilePathInterpolator;
import org.apache.maven.model.profile.activation.FileProfileActivator;
import org.apache.maven.model.profile.activation.JdkVersionProfileActivator;
import org.apache.maven.model.profile.activation.OperatingSystemProfileActivator;
import org.apache.maven.model.profile.activation.PropertyProfileActivator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TestAutoJdkConfigurationImporting
{
    @TempDir Path tempDir;

    @Test
    void emptyFile()
    throws Exception
    {
        String xml =
            "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
            "</autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession());

        //Defaults should be preferred
        assertThat(configuration.getVendors()).isEqualTo(AutoJdkConfiguration.DEFAULT_VENDORS);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void simpleFileNoImports()
    throws Exception
    {
        String xml =
            "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
            "    <vendors>" +
            "        <vendor>zulu</vendor>" +
            "        <vendor>*</vendor>" +
            "    </vendors>" +
            "</autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession());

        //All defaults except for vendors
        assertThat(configuration.getVendors()).containsExactly("zulu", AutoJdkConfiguration.WILDCARD_VENDOR);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void importSingleFile()
    throws Exception
    {
        String baseXml =
            "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
            "    <includes>" +
            "        <include>sub1.xml</include>" +
            "    </includes>" +
            "</autojdk-configuration>";
        Path baseConfigFile = tempDir.resolve("config.xml");
        Files.writeString(baseConfigFile, baseXml);

        String subXml =
            "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
            "    <vendors>" +
            "        <vendor>zulu</vendor>" +
            "        <vendor>*</vendor>" +
            "    </vendors>" +
            "</autojdk-configuration>";
        Path subConfigFile = tempDir.resolve("sub1.xml");
        Files.writeString(subConfigFile, subXml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(baseConfigFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession());

        //Check that vendors are imported from sub1
        assertThat(configuration.getVendors()).containsExactly("zulu", AutoJdkConfiguration.WILDCARD_VENDOR);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void importSingleFileWithOverrides()
    throws Exception
    {
        String baseXml =
            "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
            "    <includes>" +
            "        <include>sub1.xml</include>" +
            "    </includes>" +
            "    <vendors>" +
            "        <vendor>temurin</vendor>" +
            "        <vendor>zulu</vendor>" +
            "        <vendor>*</vendor>" +
            "    </vendors>" +
            "</autojdk-configuration>";
        Path baseConfigFile = tempDir.resolve("config.xml");
        Files.writeString(baseConfigFile, baseXml);

        String subXml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path subConfigFile = tempDir.resolve("sub1.xml");
        Files.writeString(subConfigFile, subXml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(baseConfigFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession());

        //Check that vendors are imported from sub1
        assertThat(configuration.getVendors()).containsExactly("temurin", "zulu", AutoJdkConfiguration.WILDCARD_VENDOR);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void importMultipleFiles()
    throws Exception
    {
        String baseXml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <includes>" +
                "        <include>sub1.xml</include>" +
                "        <include>sub2.xml</include>" +
                "    </includes>" +
                "</autojdk-configuration>";
        Path baseConfigFile = tempDir.resolve("config.xml");
        Files.writeString(baseConfigFile, baseXml);

        String sub1Xml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path sub1ConfigFile = tempDir.resolve("sub1.xml");
        Files.writeString(sub1ConfigFile, sub1Xml);

        String sub2Xml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>temurin</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path sub2ConfigFile = tempDir.resolve("sub2.xml");
        Files.writeString(sub2ConfigFile, sub2Xml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(baseConfigFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession());

        //Check that vendors are imported from sub2 - later imports clobber earlier ones
        assertThat(configuration.getVendors()).containsExactly("zulu", "temurin", AutoJdkConfiguration.WILDCARD_VENDOR);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }

    @Test
    void missingImportsAreIgnored()
    throws Exception
    {
        String baseXml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <includes>" +
                "        <include>sub1.xml</include>" +
                "        <include>missing.xml</include>" +
                "    </includes>" +
                "</autojdk-configuration>";
        Path baseConfigFile = tempDir.resolve("config.xml");
        Files.writeString(baseConfigFile, baseXml);

        String sub1Xml =
                "<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path sub1ConfigFile = tempDir.resolve("sub1.xml");
        Files.writeString(sub1ConfigFile, sub1Xml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(baseConfigFile, new AutoJdkXmlManager(), createActivationProcessor(), createMavenSession());

        //Check that vendors are imported from sub2 - later imports clobber earlier ones
        assertThat(configuration.getVendors()).containsExactly("zulu", AutoJdkConfiguration.WILDCARD_VENDOR);
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
        @SuppressWarnings("deprecation") MavenSession session = new MavenSession(null, new DefaultMavenExecutionRequest(), null, List.of());
        return session;
    }
}
