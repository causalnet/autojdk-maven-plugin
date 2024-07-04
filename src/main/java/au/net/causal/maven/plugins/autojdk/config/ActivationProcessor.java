package au.net.causal.maven.plugins.autojdk.config;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.ActivationFile;
import org.apache.maven.model.ActivationOS;
import org.apache.maven.model.ActivationProperty;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.profile.DefaultProfileActivationContext;
import org.apache.maven.model.profile.activation.FileProfileActivator;
import org.apache.maven.model.profile.activation.JdkVersionProfileActivator;
import org.apache.maven.model.profile.activation.OperatingSystemProfileActivator;
import org.apache.maven.model.profile.activation.ProfileActivator;
import org.apache.maven.model.profile.activation.PropertyProfileActivator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Performs the check to see if a configuration should be activated based on its activation value.
 */
public class ActivationProcessor
{
    private static final Logger log = LoggerFactory.getLogger(ActivationProcessor.class);

    private final FileProfileActivator fileProfileActivator;
    private final OperatingSystemProfileActivator operatingSystemProfileActivator;
    private final PropertyProfileActivator propertyProfileActivator;
    private final JdkVersionProfileActivator jdkVersionProfileActivator;
    private final AutoJdkVersionActivator autoJdkVersionActivator;

    public ActivationProcessor(FileProfileActivator fileProfileActivator, OperatingSystemProfileActivator operatingSystemProfileActivator,
                               PropertyProfileActivator propertyProfileActivator, JdkVersionProfileActivator jdkVersionProfileActivator,
                               String autoJdkPluginVersion)
    {
        this.fileProfileActivator = fileProfileActivator;
        this.operatingSystemProfileActivator = operatingSystemProfileActivator;
        this.propertyProfileActivator = propertyProfileActivator;
        this.jdkVersionProfileActivator = jdkVersionProfileActivator;
        this.autoJdkVersionActivator = new AutoJdkVersionActivator(autoJdkPluginVersion);
    }

    public boolean isActive(Activation activation, MavenSession session)
    throws AutoJdkConfigurationException
    {
        //No activation restrictions means it is always active
        if (activation == null)
            return true;

        boolean active = true;

        if (activation.getFile() != null)
            active = active && isActive(activation.getFile(), session);
        if (activation.getOs() != null)
            active = active && isActive(activation.getOs(), session);
        if (activation.getProperty() != null)
            active = active && isActive(activation.getProperty(), session);
        if (activation.getHostJdk() != null)
            active = active && isActiveHostJdk(activation.getHostJdk(), session);
        if (activation.getAutoJdkVersion() != null)
            active = active && isActiveAutoJdkVersion(activation.getAutoJdkVersion(), session);

        return active;
    }

    protected boolean isActive(Activation.FileActivation fileActivation, MavenSession session)
    throws AutoJdkConfigurationException
    {
        //Generate a fake profile to re-use Maven's classes to check
        Profile profile = new Profile();
        org.apache.maven.model.Activation mavenActivation = new org.apache.maven.model.Activation();
        mavenActivation.setFile(fileActivation.getFileActivationType().toMavenActivationFile());
        profile.setActivation(mavenActivation);

        return checkIsActiveUsingMavenProfileActivator(fileProfileActivator, profile, session);
    }

    protected boolean isActive(Activation.OperatingSystem osActivation, MavenSession session)
    throws AutoJdkConfigurationException
    {
        //Generate a fake profile to re-use Maven 's classes to check
        Profile profile = new Profile();
        org.apache.maven.model.Activation mavenActivation = new org.apache.maven.model.Activation();

        ActivationOS mavenActivationOs = new ActivationOS();
        mavenActivationOs.setArch(osActivation.getArch());
        mavenActivationOs.setFamily(osActivation.getFamily());
        mavenActivationOs.setName(osActivation.getName());
        mavenActivationOs.setVersion(osActivation.getVersion());
        mavenActivation.setOs(mavenActivationOs);
        profile.setActivation(mavenActivation);

        return checkIsActiveUsingMavenProfileActivator(operatingSystemProfileActivator, profile, session);
    }

    protected boolean isActive(Activation.Property propertyActivation, MavenSession session)
    throws AutoJdkConfigurationException
    {
        //Generate a fake profile to re-use Maven 's classes to check
        Profile profile = new Profile();
        org.apache.maven.model.Activation mavenActivation = new org.apache.maven.model.Activation();

        ActivationProperty mavenActivationProperty = new ActivationProperty();
        mavenActivationProperty.setName(propertyActivation.getName());
        mavenActivationProperty.setValue(propertyActivation.getValue());
        mavenActivation.setProperty(mavenActivationProperty);
        profile.setActivation(mavenActivation);

        return checkIsActiveUsingMavenProfileActivator(propertyProfileActivator, profile, session);
    }

    protected boolean isActiveHostJdk(String jdkActivation, MavenSession session)
    throws AutoJdkConfigurationException
    {
        //Generate a fake profile to re-use Maven 's classes to check
        Profile profile = new Profile();
        org.apache.maven.model.Activation mavenActivation = new org.apache.maven.model.Activation();
        mavenActivation.setJdk(jdkActivation);
        profile.setActivation(mavenActivation);

        return checkIsActiveUsingMavenProfileActivator(jdkVersionProfileActivator, profile, session);
    }

    protected boolean isActiveAutoJdkVersion(String autoJdkVersion, MavenSession session)
    throws AutoJdkConfigurationException
    {
        return autoJdkVersionActivator.isActive(autoJdkVersion);
    }

    private boolean checkIsActiveUsingMavenProfileActivator(ProfileActivator activator, Profile profile, MavenSession session)
    throws AutoJdkConfigurationException
    {
        SimpleModelProblemCollector col = new SimpleModelProblemCollector();
        DefaultProfileActivationContext context = new DefaultProfileActivationContext();

        if (session.getCurrentProject() != null)
        {
            context.setProjectProperties(session.getCurrentProject().getProperties());
            context.setProjectDirectory(session.getCurrentProject().getBasedir());
        }
        context.setSystemProperties(session.getSystemProperties());
        context.setUserProperties(session.getUserProperties());

        boolean active = activator.isActive(profile, context, col);

        col.processProblems();

        return active;
    }

    private static class SimpleModelProblemCollector implements ModelProblemCollector
    {
        private final List<ModelProblemCollectorRequest> problems = new ArrayList<>();

        @Override
        public void add(ModelProblemCollectorRequest req)
        {
            problems.add(req);
        }

        public List<ModelProblemCollectorRequest> getProblems()
        {
            return problems;
        }

        public void processProblems()
        throws AutoJdkConfigurationException
        {
            if (!getProblems().isEmpty())
            {
                for (ModelProblemCollectorRequest problem : getProblems())
                {
                    if (problem.getSeverity() == ModelProblem.Severity.WARNING)
                        log.warn("Warning processing autojdk configuration: " + problem.getMessage(), problem.getException());
                    else
                        log.error("Error processing autojdk configuration: " + problem.getMessage(), problem.getException());
                }

                for (ModelProblemCollectorRequest problem : getProblems())
                {
                    if (problem.getSeverity() != ModelProblem.Severity.WARNING)
                        throw new AutoJdkConfigurationException("Error processing autojdk configuration: " + problem.getMessage(), problem.getException());
                }
            }
        }
    }

    private static class AutoJdkVersionActivator
    {
        private final String autoJdkPluginVersion;

        public AutoJdkVersionActivator(String autoJdkPluginVersion)
        {
            this.autoJdkPluginVersion = autoJdkPluginVersion;
        }

        public boolean isActive(String expectedVersion)
        throws AutoJdkConfigurationException
        {
            //If for whatever reason the plugin's version could not be determined then just bail out
            if (autoJdkPluginVersion == null)
                return false;

            //Bit of a hack to reuse all the version parsing logic from Maven itself
            //Use JDK version profile activator but supply a fake context where system property 'java.version' is actually AutoJDK's version instead of the OS version
            JdkVersionProfileActivator jdkVersionProfileActivator = new JdkVersionProfileActivator();

            Profile fakeProfile = new Profile();
            org.apache.maven.model.Activation fakeActivation = new org.apache.maven.model.Activation();
            fakeActivation.setJdk(expectedVersion);
            fakeProfile.setActivation(fakeActivation);

            DefaultProfileActivationContext fakeContext = new DefaultProfileActivationContext();
            fakeContext.setSystemProperties(Map.of("java.version", autoJdkPluginVersion));

            SimpleModelProblemCollector col = new SimpleModelProblemCollector();

            boolean active = jdkVersionProfileActivator.isActive(fakeProfile, fakeContext, col);

            col.processProblems();

            return active;
        }
    }
}
