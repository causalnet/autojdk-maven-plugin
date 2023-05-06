package au.net.causal.maven.plugins.autojdk.config;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.path.DefaultPathTranslator;
import org.apache.maven.model.path.ProfileActivationFilePathInterpolator;
import org.apache.maven.model.profile.activation.FileProfileActivator;
import org.apache.maven.model.profile.activation.JdkVersionProfileActivator;
import org.apache.maven.model.profile.activation.OperatingSystemProfileActivator;
import org.apache.maven.model.profile.activation.PropertyProfileActivator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.Os;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestActivationProcessor
{
    private final ActivationProcessor activationProcessor = createActivationProcessor();

    private static ActivationProcessor createActivationProcessor()
    {
        FileProfileActivator fileProfileActivator = new FileProfileActivator();
        ProfileActivationFilePathInterpolator profileActivationFilePathInterpolator = new ProfileActivationFilePathInterpolator();
        profileActivationFilePathInterpolator.setPathTranslator(new DefaultPathTranslator());
        fileProfileActivator.setProfileActivationFilePathInterpolator(profileActivationFilePathInterpolator);
        return new ActivationProcessor(fileProfileActivator, new OperatingSystemProfileActivator(), new PropertyProfileActivator(), new JdkVersionProfileActivator(), "1.0-SNAPSHOT");
    }

    private static MavenSession createMavenSession(MavenExecutionRequest request)
    {
        @SuppressWarnings("deprecation") MavenSession session = new MavenSession(null, request, null, List.of());
        return session;
    }

    @Test
    void noActivationRestrictions()
    throws AutoJdkConfigurationException
    {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        MavenSession session = createMavenSession(request);

        Activation activation = new Activation();

        boolean active = activationProcessor.isActive(activation, session);

        assertThat(active).isTrue();
    }

    @Nested
    class File
    {
        @Test
        void existingFileByAbsoluteName(@TempDir Path dir)
        throws AutoJdkConfigurationException, IOException
        {
            Path theFile = dir.resolve("myfile.txt");
            Files.createFile(theFile);

            MavenSession session = createMavenSession(new DefaultMavenExecutionRequest());
            Activation activation = new Activation();
            Activation.FileActivation fileActivation = new Activation.FileActivation();
            fileActivation.setExists(theFile.toAbsolutePath().toString());
            activation.setFile(fileActivation);

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isTrue();
        }

        @Test
        void notExistingFileByAbsoluteName(@TempDir Path dir)
        throws AutoJdkConfigurationException
        {
            Path theFile = dir.resolve("myfile.txt");
            assertThat(theFile).doesNotExist();

            MavenSession session = createMavenSession(new DefaultMavenExecutionRequest());
            Activation activation = new Activation();
            Activation.FileActivation fileActivation = new Activation.FileActivation();
            fileActivation.setExists(theFile.toAbsolutePath().toString());
            activation.setFile(fileActivation);

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isFalse();
        }

        @Test
        void missingFileByAbsoluteName(@TempDir Path dir)
        throws AutoJdkConfigurationException
        {
            Path theFile = dir.resolve("myfile.txt");
            assertThat(theFile).doesNotExist();

            MavenSession session = createMavenSession(new DefaultMavenExecutionRequest());
            Activation activation = new Activation();
            Activation.FileActivation fileActivation = new Activation.FileActivation();
            fileActivation.setMissing(theFile.toAbsolutePath().toString());
            activation.setFile(fileActivation);

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isTrue();
        }

        @Test
        void notMissingFileByAbsoluteName(@TempDir Path dir)
        throws AutoJdkConfigurationException, IOException
        {
            Path theFile = dir.resolve("myfile.txt");
            Files.createFile(theFile);

            MavenSession session = createMavenSession(new DefaultMavenExecutionRequest());
            Activation activation = new Activation();
            Activation.FileActivation fileActivation = new Activation.FileActivation();
            fileActivation.setMissing(theFile.toAbsolutePath().toString());
            activation.setFile(fileActivation);

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isFalse();
        }

        @Test
        void existingFileByInterpolatedName(@TempDir Path dir)
        throws AutoJdkConfigurationException, IOException
        {
            Path projectFile = dir.resolve("pom.xml");
            Path theFile = dir.resolve("myfile.txt");
            Files.createFile(theFile);

            DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
            Properties userProperties = new Properties();
            userProperties.setProperty("fileNameFromProperty", "myfile");
            request.setUserProperties(userProperties);
            MavenSession session = createMavenSession(request);
            MavenProject mavenProject = new MavenProject();
            mavenProject.setFile(projectFile.toFile());
            session.setCurrentProject(mavenProject);
            Activation activation = new Activation();
            Activation.FileActivation fileActivation = new Activation.FileActivation();
            fileActivation.setExists("${fileNameFromProperty}.txt");
            activation.setFile(fileActivation);

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isTrue();
        }
    }

    @Nested
    class HostJdk
    {
        private MavenSession createMavenSessionWithJavaVersion(String javaVersion)
        {
            MavenExecutionRequest request = new DefaultMavenExecutionRequest();
            Properties systemProperties = new Properties();
            systemProperties.setProperty("java.version", javaVersion);
            request.setSystemProperties(systemProperties);
            return createMavenSession(request);
        }

        @Test
        void matchingValue()
        throws AutoJdkConfigurationException
        {
            MavenSession session = createMavenSessionWithJavaVersion("11.0.1");
            Activation activation = new Activation();
            activation.setHostJdk("11");

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isTrue();
        }

        @Test
        void nonMatchingValue()
        throws AutoJdkConfigurationException
        {
            MavenSession session = createMavenSessionWithJavaVersion("20.0.1");

            Activation activation = new Activation();
            activation.setHostJdk("11");

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isFalse();
        }

        @Test
        void matchingRange()
        throws AutoJdkConfigurationException
        {
            MavenSession session = createMavenSessionWithJavaVersion("11.0.1");

            Activation activation = new Activation();
            activation.setHostJdk("[11, 18)");

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isTrue();
        }

        @Test
        void nonMatchingRange()
        throws AutoJdkConfigurationException
        {
            MavenSession session = createMavenSessionWithJavaVersion("18.0");

            Activation activation = new Activation();
            activation.setHostJdk("[11, 18)");

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isFalse();
        }
    }

    @Nested
    class Property
    {
        private MavenSession createMavenSessionWithProperty(String propertyName, String propertyValue)
        {
            MavenExecutionRequest request = new DefaultMavenExecutionRequest();
            Properties properties = new Properties();
            properties.setProperty(propertyName, propertyValue);
            request.setUserProperties(properties);
            return createMavenSession(request);
        }

        @Test
        void matchingValue()
        throws AutoJdkConfigurationException
        {
            MavenSession session = createMavenSessionWithProperty("myProperty", "value1");
            Activation activation = new Activation();
            Activation.Property activationProperty = new Activation.Property();
            activationProperty.setName("myProperty");
            activationProperty.setValue("value1");
            activation.setProperty(activationProperty);

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isTrue();
        }

        @Test
        void notMatchingValue()
        throws AutoJdkConfigurationException
        {
            MavenSession session = createMavenSessionWithProperty("myProperty", "value1");
            Activation activation = new Activation();
            Activation.Property activationProperty = new Activation.Property();
            activationProperty.setName("myProperty");
            activationProperty.setValue("value2");
            activation.setProperty(activationProperty);

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isFalse();
        }

        @Test
        void missingProperty()
        throws AutoJdkConfigurationException
        {
            MavenSession session = createMavenSessionWithProperty("something", "else");
            Activation activation = new Activation();
            Activation.Property activationProperty = new Activation.Property();
            activationProperty.setName("myProperty");
            activationProperty.setValue("value1");
            activation.setProperty(activationProperty);

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isFalse();
        }

        @Test
        void notValueMatch()
        throws AutoJdkConfigurationException
        {
            MavenSession session = createMavenSessionWithProperty("myProperty", "value1");
            Activation activation = new Activation();
            Activation.Property activationProperty = new Activation.Property();
            activationProperty.setName("myProperty");
            activationProperty.setValue("!value1");
            activation.setProperty(activationProperty);

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isFalse();
        }

        @Test
        void notValueNotMatch()
        throws AutoJdkConfigurationException
        {
            MavenSession session = createMavenSessionWithProperty("myProperty", "value2");
            Activation activation = new Activation();
            Activation.Property activationProperty = new Activation.Property();
            activationProperty.setName("myProperty");
            activationProperty.setValue("!value1");
            activation.setProperty(activationProperty);

            boolean active = activationProcessor.isActive(activation, session);

            assertThat(active).isTrue();
        }
    }

    @Nested
    class OperatingSystem
    {
        @Test
        void matchingFamily()
        throws Exception
        {
            try (MockedStatic<Os> os = Mockito.mockStatic(Os.class, withSettings().mockMaker(MockMakers.INLINE)))
            {
                os.when(() -> Os.isFamily(eq("galah-os"))).thenReturn(true);

                MavenSession session = createMavenSession(new DefaultMavenExecutionRequest());

                Activation activation = new Activation();
                Activation.OperatingSystem activationOs = new Activation.OperatingSystem();
                activationOs.setFamily("galah-os");
                activation.setOs(activationOs);

                boolean active = activationProcessor.isActive(activation, session);

                assertThat(active).isTrue();
            }
        }

        @Test
        void notMatchingFamily()
        throws Exception
        {
            try (MockedStatic<Os> os = Mockito.mockStatic(Os.class, withSettings().mockMaker(MockMakers.INLINE)))
            {
                os.when(() -> Os.isFamily("galah-os")).thenReturn(false);

                MavenSession session = createMavenSession(new DefaultMavenExecutionRequest());

                Activation activation = new Activation();
                Activation.OperatingSystem activationOs = new Activation.OperatingSystem();
                activationOs.setFamily("galah-os");
                activation.setOs(activationOs);

                boolean active = activationProcessor.isActive(activation, session);

                assertThat(active).isFalse();
            }
        }

        @Test
        void matchingArch()
        throws Exception
        {
            try (MockedStatic<Os> os = Mockito.mockStatic(Os.class, withSettings().mockMaker(MockMakers.INLINE)))
            {
                os.when(() -> Os.isArch(eq("amd64"))).thenReturn(true);

                MavenSession session = createMavenSession(new DefaultMavenExecutionRequest());

                Activation activation = new Activation();
                Activation.OperatingSystem activationOs = new Activation.OperatingSystem();
                activationOs.setArch("amd64");
                activation.setOs(activationOs);

                boolean active = activationProcessor.isActive(activation, session);

                assertThat(active).isTrue();
            }
        }

        @Test
        void notMatchingArch()
        throws Exception
        {
            try (MockedStatic<Os> os = Mockito.mockStatic(Os.class, withSettings().mockMaker(MockMakers.INLINE)))
            {
                os.when(() -> Os.isArch(eq("amd64"))).thenReturn(false);

                MavenSession session = createMavenSession(new DefaultMavenExecutionRequest());

                Activation activation = new Activation();
                Activation.OperatingSystem activationOs = new Activation.OperatingSystem();
                activationOs.setArch("amd64");
                activation.setOs(activationOs);

                boolean active = activationProcessor.isActive(activation, session);

                assertThat(active).isFalse();
            }
        }
    }
}
