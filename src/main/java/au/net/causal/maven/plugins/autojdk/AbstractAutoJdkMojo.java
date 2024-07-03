package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.config.ActivationProcessor;
import au.net.causal.maven.plugins.autojdk.config.AutoJdkConfigurationException;
import au.net.causal.maven.plugins.autojdk.foojay.FoojayClient;
import au.net.causal.maven.plugins.autojdk.foojay.FoojayOpenApiJdkRepository;
import au.net.causal.maven.plugins.autojdk.foojay.FoojayOpenApiVendorService;
import au.net.causal.maven.plugins.autojdk.foojay.OfflineDistributionsVendorService;
import au.net.causal.maven.plugins.autojdk.foojay.openapi.handler.ApiClient;
import com.google.common.base.StandardSystemProperty;
import jakarta.xml.bind.JAXBException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.profile.activation.FileProfileActivator;
import org.apache.maven.model.profile.activation.JdkVersionProfileActivator;
import org.apache.maven.model.profile.activation.OperatingSystemProfileActivator;
import org.apache.maven.model.profile.activation.PropertyProfileActivator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import javax.xml.datatype.DatatypeFactory;
import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAutoJdkMojo extends AbstractMojo
{
    private static final String VERSION_TRANSLATION_SCHEME_AUTO = "auto";
    private static final String UPDATE_POLICY_NEVER = "never";
    private static final String UPDATE_POLICY_ALWAYS = "always";

    static final String PROPERTY_JDK_VENDOR = "autojdk.jdk.vendor";
    static final String PROPERTY_JDK_VERSION = "autojdk.jdk.version";
    static final String PROPERTY_JDK_RELEASE_TYPE = "autojdk.jdk.releaseType";
    static final String PROPERTY_AUTOJDK_CONFIGURATION_FILE = "autojdk.config.file";
    static final String PROPERTY_AUTOJDK_SKIP = "autojdk.skip";

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private FileProfileActivator fileProfileActivator;

    @Component
    private OperatingSystemProfileActivator operatingSystemProfileActivator;

    @Component
    private PropertyProfileActivator propertyProfileActivator;

    @Component
    private JdkVersionProfileActivator jdkVersionProfileActivator;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true)
    protected List<RemoteRepository> remoteRepositories;

    @Parameter( defaultValue = "${plugin}", required = true, readonly = true )
    private PluginDescriptor pluginDescriptor;

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;

    @Parameter(defaultValue = "${project.build.directory}/autojdk-download", required = true)
    protected File downloadDirectory;

    private boolean downloadDirectorySetUp;

    @Parameter(property = PROPERTY_JDK_RELEASE_TYPE, defaultValue = "GA", required = true)
    private ReleaseType jdkReleaseType;

    @Parameter(property = PROPERTY_AUTOJDK_CONFIGURATION_FILE)
    private File autoJdkConfigurationFile;

    @Parameter(property = "autojdk.versionTranslationScheme", required = true, defaultValue = VERSION_TRANSLATION_SCHEME_AUTO)
    private String versionTranslationScheme;

    /**
     * How AutoJDK checks for updates, specified in a duration format.  e.g. PT12H for checking every 12 hours.
     * If specified, this overrides the configuration a user has in their AutoJDK configuration file.
     * If not specified, the configuration file setting or a default is used.  To always check, specify a value of 'always'.
     * To never check, specify a value of 'never'.
     */
    @Parameter(property = "autojdk.update.policy")
    private String updatePolicy;

    /**
     * If true, allow downloads to come from http:// protocol as well as https://.  By default, http downloads are not allowed.
     */
    @Parameter(property="autojdk.download.allowHttp", defaultValue = "false")
    private boolean allowHttpJdkDownloads;

    /**
     * If true, skip execution of autojdk plugin.
     */
    @Parameter(property=PROPERTY_AUTOJDK_SKIP, defaultValue = "false")
    protected boolean skip;

    private AutoJdk autoJdk;
    protected final PlatformTools platformTools = new PlatformTools();

    protected AutoJdk autoJdk()
    {
        return autoJdk;
    }

    protected ReleaseType getJdkReleaseType()
    {
        return jdkReleaseType;
    }

    protected void executeImpl()
    throws MojoExecutionException, MojoFailureException
    {
        AutoJdkHome autojdkHome = AutoJdkHome.defaultHome();
        if (autoJdkConfigurationFile == null)
            autoJdkConfigurationFile = autojdkHome.getAutoJdkConfigurationFile().toFile();

        boolean offlineMode = session.isOffline();

        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();

        //Proxy + other builder configuration
        MavenJdkProxySelector proxySelector = new MavenJdkProxySelector(repoSession);
        httpClientBuilder.proxy(proxySelector).authenticator(proxySelector.authenticator());
        if (allowHttpJdkDownloads)
            httpClientBuilder.followRedirects(HttpClient.Redirect.ALWAYS);
        else
            httpClientBuilder.followRedirects(HttpClient.Redirect.NORMAL); //Allow redirect, except from HTTPS URLs to HTTP URLs.

        FileDownloader fileDownloader = new HttpClientFileDownloader(this::tempDownloadDirectory, httpClientBuilder);
        fileDownloader.addDownloadProgressListener(new MavenDownloadProgressAdapter(repoSession));

        AutoJdkXmlManager xmlManager;
        try
        {
            xmlManager = new AutoJdkXmlManager();
        }
        catch (JAXBException e)
        {
            throw new MojoExecutionException("Error initializing JAXB: " + e, e);
        }

        String autoJdkPluginVersion = pluginDescriptor.getVersion();
        ActivationProcessor activationProcessor = new ActivationProcessor(
                fileProfileActivator, operatingSystemProfileActivator, propertyProfileActivator, jdkVersionProfileActivator, autoJdkPluginVersion
        );

        AutoJdkInstalledJdkSystem localJdkResolver = new AutoJdkInstalledJdkSystem(autojdkHome.getLocalJdksDirectory(), xmlManager);

        AutoJdkConfiguration autoJdkConfiguration;
        try
        {
            autoJdkConfiguration = AutoJdkConfiguration.fromFile(autoJdkConfigurationFile.toPath(), xmlManager, activationProcessor, session);
        }
        catch (AutoJdkXmlManager.XmlParseException | AutoJdkConfigurationException e)
        {
            throw new MojoExecutionException("Error reading " + autojdkHome.getAutoJdkConfigurationFile() + ": " + e.getMessage(), e);
        }
        configureAutoJdkUpdatePolicy(autoJdkConfiguration);

        FoojayClient foojayClient = null;
        if (!offlineMode)
        {
            ApiClient apiClient = FoojayClient.createDefaultApiClient();
            apiClient.setHttpClientBuilder(httpClientBuilder);
            foojayClient = new FoojayClient(apiClient);
        }

        VendorService allVendorService;
        if (offlineMode)
            allVendorService = new OfflineDistributionsVendorService();
        else
            allVendorService = new FoojayOpenApiVendorService(foojayClient);

        VendorService userConfiguredVendorService = new UserConfiguredVendorService(allVendorService, autoJdkConfiguration);
        List<JdkArchiveRepository<?>> jdkArchiveRepositories = new ArrayList<>();
        jdkArchiveRepositories.add(new MavenArtifactJdkArchiveRepository(repositorySystem, repoSession, remoteRepositories, "au.net.causal.autojdk.jdk", userConfiguredVendorService, xmlManager, this::tempDownloadDirectory));
        if (!offlineMode)
            jdkArchiveRepositories.add(new FoojayOpenApiJdkRepository(foojayClient, fileDownloader));

        VersionTranslationScheme versionTranslationScheme = getVersionTranslationScheme();
        Clock clock = Clock.systemDefaultZone();
        JdkSearchUpdateChecker jdkSearchUpdateChecker = new MetadataFileJdkSearchUpdateChecker(autojdkHome.getAutoJdkSearchUpToDateCheckMetadataFile(), xmlManager);

        autoJdk = new AutoJdk(localJdkResolver, localJdkResolver, jdkArchiveRepositories, versionTranslationScheme, autoJdkConfiguration, jdkSearchUpdateChecker, clock);
    }

    @Override
    public final void execute()
    throws MojoExecutionException, MojoFailureException
    {
        if (skip)
        {
            getLog().info("AutoJDK plugin execution skipped due to configuration.");
            return;
        }

        executeImpl();
    }

    private void configureAutoJdkUpdatePolicy(AutoJdkConfiguration configuration)
    throws MojoExecutionException
    {
        //Not specified, don't override anything
        if (updatePolicy == null)
            return;

        //Replace if specific values
        if (UPDATE_POLICY_NEVER.equalsIgnoreCase(updatePolicy))
        {
            configuration.setJdkUpdatePolicy(new AutoJdkConfiguration.JdkUpdatePolicySpec(new JdkUpdatePolicy.Never()));
            return;
        }
        if (UPDATE_POLICY_ALWAYS.equalsIgnoreCase(updatePolicy))
        {
            configuration.setJdkUpdatePolicy(new AutoJdkConfiguration.JdkUpdatePolicySpec(new JdkUpdatePolicy.Always()));
            return;
        }

        try
        {
            configuration.setJdkUpdatePolicy(new AutoJdkConfiguration.JdkUpdatePolicySpec(new JdkUpdatePolicy.EveryDuration(DatatypeFactory.newDefaultInstance().newDuration(updatePolicy))));
        }
        catch (IllegalArgumentException e)
        {
            throw new MojoExecutionException("Error parsing updatePolicy value '" + updatePolicy + "'.", e);
        }
    }

    private synchronized Path tempDownloadDirectory()
    throws IOException
    {
        if (!downloadDirectorySetUp)
        {
            //Don't use default download directory if there's no project
            if (project == null)
                downloadDirectory = new File(StandardSystemProperty.JAVA_IO_TMPDIR.value());

            //Ensure all directories are created
            Files.createDirectories(downloadDirectory.toPath());

            downloadDirectorySetUp = true;
        }

        return downloadDirectory.toPath();
    }

    /**
     * Determines/calculates the version translation scheme that should be used from the version translation scheme configured directly or, if set to automatic mode,
     * detects which should be used based on the version.
     *
     * @return version translation scheme, never null.
     *
     * @throws MojoExecutionException if the configured version translation scheme is unrecognized.
     */
    protected VersionTranslationScheme getVersionTranslationScheme()
    throws MojoExecutionException
    {
        if (VERSION_TRANSLATION_SCHEME_AUTO.equalsIgnoreCase(versionTranslationScheme))
        {
            VersionTranslationScheme selectedScheme = detectVersionTranslationScheme();
            if (selectedScheme == null)
                selectedScheme = StandardVersionTranslationScheme.UNMODIFIED;

            return selectedScheme;
        }
        else
        {
            //One of the standard schemes
            for (StandardVersionTranslationScheme cur : StandardVersionTranslationScheme.values())
            {
                if (cur.name().equalsIgnoreCase(versionTranslationScheme))
                    return cur;
            }
        }

        //If we get here it's a value we don't recognize
        throw new MojoExecutionException("Unknown version translation scheme: " + versionTranslationScheme);
    }

    protected VersionTranslationScheme detectVersionTranslationScheme()
    {
        //No project, so we can't
        return null;
    }
}
