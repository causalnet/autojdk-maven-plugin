package au.net.causal.maven.plugins.autojdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class TestAutoJdkConfigurationImporting
{
    @TempDir Path tempDir;

    @Test
    void emptyFile()
    throws Exception
    {
        String xml =
            "<autojdk-configuration>" +
            "</autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager());

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
            "<autojdk-configuration>" +
            "    <vendors>" +
            "        <vendor>zulu</vendor>" +
            "        <vendor>*</vendor>" +
            "    </vendors>" +
            "</autojdk-configuration>";
        Path configFile = tempDir.resolve("config.xml");
        Files.writeString(configFile, xml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(configFile, new AutoJdkXmlManager());

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
            "<autojdk-configuration>" +
            "    <imports>" +
            "        <import>sub1.xml</import>" +
            "    </imports>" +
            "</autojdk-configuration>";
        Path baseConfigFile = tempDir.resolve("config.xml");
        Files.writeString(baseConfigFile, baseXml);

        String subXml =
            "<autojdk-configuration>" +
            "    <vendors>" +
            "        <vendor>zulu</vendor>" +
            "        <vendor>*</vendor>" +
            "    </vendors>" +
            "</autojdk-configuration>";
        Path subConfigFile = tempDir.resolve("sub1.xml");
        Files.writeString(subConfigFile, subXml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(baseConfigFile, new AutoJdkXmlManager());

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
            "<autojdk-configuration>" +
            "    <imports>" +
            "        <import>sub1.xml</import>" +
            "    </imports>" +
            "    <vendors>" +
            "        <vendor>temurin</vendor>" +
            "        <vendor>zulu</vendor>" +
            "        <vendor>*</vendor>" +
            "    </vendors>" +
            "</autojdk-configuration>";
        Path baseConfigFile = tempDir.resolve("config.xml");
        Files.writeString(baseConfigFile, baseXml);

        String subXml =
                "<autojdk-configuration>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path subConfigFile = tempDir.resolve("sub1.xml");
        Files.writeString(subConfigFile, subXml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(baseConfigFile, new AutoJdkXmlManager());

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
                "<autojdk-configuration>" +
                "    <imports>" +
                "        <import>sub1.xml</import>" +
                "        <import>sub2.xml</import>" +
                "    </imports>" +
                "</autojdk-configuration>";
        Path baseConfigFile = tempDir.resolve("config.xml");
        Files.writeString(baseConfigFile, baseXml);

        String sub1Xml =
                "<autojdk-configuration>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path sub1ConfigFile = tempDir.resolve("sub1.xml");
        Files.writeString(sub1ConfigFile, sub1Xml);

        String sub2Xml =
                "<autojdk-configuration>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>temurin</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path sub2ConfigFile = tempDir.resolve("sub2.xml");
        Files.writeString(sub2ConfigFile, sub2Xml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(baseConfigFile, new AutoJdkXmlManager());

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
                "<autojdk-configuration>" +
                "    <imports>" +
                "        <import>sub1.xml</import>" +
                "        <import>missing.xml</import>" +
                "    </imports>" +
                "</autojdk-configuration>";
        Path baseConfigFile = tempDir.resolve("config.xml");
        Files.writeString(baseConfigFile, baseXml);

        String sub1Xml =
                "<autojdk-configuration>" +
                "    <vendors>" +
                "        <vendor>zulu</vendor>" +
                "        <vendor>*</vendor>" +
                "    </vendors>" +
                "</autojdk-configuration>";
        Path sub1ConfigFile = tempDir.resolve("sub1.xml");
        Files.writeString(sub1ConfigFile, sub1Xml);

        AutoJdkConfiguration configuration = AutoJdkConfiguration.fromFile(baseConfigFile, new AutoJdkXmlManager());

        //Check that vendors are imported from sub2 - later imports clobber earlier ones
        assertThat(configuration.getVendors()).containsExactly("zulu", AutoJdkConfiguration.WILDCARD_VENDOR);
        assertThat(configuration.getJdkUpdatePolicy().getValue()).isEqualTo(AutoJdkConfiguration.DEFAULT_JDK_UPDATE_POLICY.getValue());
        assertThat(configuration.getExtensionExclusions()).isEqualTo(AutoJdkConfiguration.defaultExtensionExclusions());
    }
}
