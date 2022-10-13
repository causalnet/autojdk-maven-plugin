package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.foojay.FoojayClient;
import au.net.causal.maven.plugins.autojdk.foojay.FoojayOpenApiJdkRepository;
import au.net.causal.maven.plugins.autojdk.foojay.FoojayOpenApiVendorService;
import au.net.causal.maven.plugins.autojdk.foojay.OfflineDistributionsVendorService;
import au.net.causal.maven.plugins.autojdk.foojay.openapi.handler.ApiClient;
import com.google.common.base.StandardSystemProperty;
import jakarta.xml.bind.JAXBException;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.datatype.DatatypeFactory;
import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractAutoJdkMojo extends AbstractMojo
{
    private static final String VERSION_TRANSLATION_SCHEME_AUTO = "auto";
    private static final String UPDATE_POLICY_NEVER = "never";
    private static final String UPDATE_POLICY_ALWAYS = "always";

    static final String PROPERTY_JDK_VENDOR = "autojdk.jdk.vendor";
    static final String PROPERTY_JDK_VERSION = "autojdk.jdk.version";
    static final String PROPERTY_JDK_RELEASE_TYPE = "autojdk.jdk.releaseType";
    static final String PROPERTY_AUTOJDK_CONFIGURATION_FILE = "autojdk.config.file";

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true)
    protected List<RemoteRepository> remoteRepositories;

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;

    @Parameter(defaultValue = "${project.build.directory}/autojdk-download", required = true)
    protected File downloadDirectory;

    private boolean downloadDirectorySetUp;

    @Parameter(property = PROPERTY_JDK_VERSION)
    private String requiredJdkVersion;

    private VersionRange requiredJdkVersionRange;

    @Parameter(property = PROPERTY_JDK_VENDOR)
    private String requiredJdkVendor;

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

    private AutoJdk autoJdk;
    protected final PlatformTools platformTools = new PlatformTools();

    protected abstract void executeImpl()
    throws MojoExecutionException, MojoFailureException;

    protected VersionRange getRequiredJdkVersionRange()
    {
        return requiredJdkVersionRange;
    }

    protected AutoJdk autoJdk()
    {
        return autoJdk;
    }

    protected String getRequiredJdkVendor()
    {
        return requiredJdkVendor;
    }

    protected ReleaseType getJdkReleaseType()
    {
        return jdkReleaseType;
    }

    @Override
    public final void execute()
    throws MojoExecutionException, MojoFailureException
    {
        Map<String, String> toolchainJdkRequirements = readToolchainsJdkRequirements();
        if (requiredJdkVersion == null)
            requiredJdkVersion = toolchainJdkRequirements.get("version");
        if (requiredJdkVendor == null)
            requiredJdkVendor = toolchainJdkRequirements.get("vendor");

        if (requiredJdkVersion == null)
            throw new MojoExecutionException("No required JDK version configured.  Either configure directly on the plugin or add toolchains plugin with appropriate configuration.");

        try
        {
            requiredJdkVersionRange = VersionRange.createFromVersionSpec(requiredJdkVersion);
        }
        catch (InvalidVersionSpecificationException e)
        {
            throw new MojoExecutionException("Invalid JDK version/range: " + requiredJdkVersion, e);
        }

        AutoJdkHome autojdkHome = AutoJdkHome.defaultHome();
        if (autoJdkConfigurationFile == null)
            autoJdkConfigurationFile = autojdkHome.getAutoJdkConfigurationFile().toFile();

        boolean offlineMode = session.isOffline();

        FileDownloader fileDownloader = new SimpleFileDownloader(this::tempDownloadDirectory, new MavenProxySelector(repoSession));
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

        AutoJdkInstalledJdkSystem localJdkResolver = new AutoJdkInstalledJdkSystem(autojdkHome.getLocalJdksDirectory(), xmlManager);

        AutoJdkConfiguration autoJdkConfiguration;
        try
        {
            autoJdkConfiguration = AutoJdkConfiguration.fromFile(autoJdkConfigurationFile.toPath(), xmlManager);
        }
        catch (AutoJdkXmlManager.XmlParseException e)
        {
            throw new MojoExecutionException("Error reading " + autojdkHome.getAutoJdkConfigurationFile() + ": " + e.getMessage(), e);
        }
        configureAutoJdkUpdatePolicy(autoJdkConfiguration);

        FoojayClient foojayClient = null;
        if (!offlineMode)
        {
            ApiClient apiClient = FoojayClient.createDefaultApiClient();
            HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();


            //TODO

            System.err.println("Warning: hack that accepts all SSL in AbstractAutoJdkMojo");

            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        public void checkClientTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }
                        public void checkServerTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };

            try
            {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new SecureRandom());
                httpClientBuilder.sslContext(sslContext);
            }
            catch (NoSuchAlgorithmException | KeyManagementException e)
            {
                throw new RuntimeException(e);
            }

            //TODO

            //Proxy configuration
            MavenJdkProxySelector proxySelector = new MavenJdkProxySelector(repoSession);
            httpClientBuilder.proxy(proxySelector).authenticator(proxySelector.authenticator());

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
        jdkArchiveRepositories.add(new MavenArtifactJdkArchiveRepository(repositorySystem, repoSession, remoteRepositories, "au.net.causal.autojdk.jdk", userConfiguredVendorService, xmlManager));
        if (!offlineMode)
            jdkArchiveRepositories.add(new FoojayOpenApiJdkRepository(foojayClient, repositorySystem, repoSession, fileDownloader, "au.net.causal.autojdk.jdk", xmlManager));

        VersionTranslationScheme versionTranslationScheme = getVersionTranslationScheme();
        Clock clock = Clock.systemDefaultZone();
        JdkSearchUpdateChecker jdkSearchUpdateChecker = new MetadataFileJdkSearchUpdateChecker(autojdkHome.getAutoJdkSearchUpToDateCheckMetadataFile(), xmlManager);

        autoJdk = new AutoJdk(localJdkResolver, localJdkResolver, jdkArchiveRepositories, versionTranslationScheme, autoJdkConfiguration, jdkSearchUpdateChecker, clock);

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

    private Map<String, String> readToolchainsJdkRequirements()
    {
        Map<String, String> requirements = new LinkedHashMap<>();

        Plugin toolchainsPlugin = project.getBuild().getPluginsAsMap().get("org.apache.maven.plugins:maven-toolchains-plugin");

        //Should only have one execution, but if there's more than one just combine everything
        //If there's multiple executions with different requirements I'd consider the build broken
        //so if we break there as well it's not a huge deal
        if (toolchainsPlugin != null && toolchainsPlugin.getExecutions() != null)
        {
            for (PluginExecution toolchainsPluginExecution : toolchainsPlugin.getExecutions())
            {
                Object configuration = toolchainsPluginExecution.getConfiguration();
                if (configuration instanceof Xpp3Dom)
                {
                    Xpp3Dom xppConfig = (Xpp3Dom) configuration;

                    Xpp3Dom toolchainsConfig = xppConfig.getChild("toolchains");
                    if (toolchainsConfig != null)
                    {
                        Xpp3Dom jdkConfig = toolchainsConfig.getChild("jdk");
                        if (jdkConfig != null)
                        {
                            for (Xpp3Dom requirementConfig : jdkConfig.getChildren())
                            {
                                String requirementName = requirementConfig.getName();
                                String requirementValue = requirementConfig.getValue();
                                if (StringUtils.isNotEmpty(requirementName) && StringUtils.isNotEmpty(requirementValue))
                                    requirements.put(requirementName, requirementValue);
                            }
                        }
                    }
                }
            }
        }

        return requirements;
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
            VersionTranslationScheme selectedScheme = detectVersionTranslationSchemeFromRequiredJdkVersion();
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

    /**
     * When the required JDK version (possibly read from toolchains) is a simple integer value, e.g. '17' then use
     * {@link StandardVersionTranslationScheme#MAJOR_AND_FULL} translation, otherwise use
     * {@link StandardVersionTranslationScheme#UNMODIFIED} translation.  This will allow toolchains configurations
     * with java version set at e.g. '17' to just work even with JDK 17.0.2.
     *
     * @see StandardVersionTranslationScheme
     */
    private VersionTranslationScheme detectVersionTranslationSchemeFromRequiredJdkVersion()
    {
        //If the required format is a simple integer...
        if (isPositiveInteger(requiredJdkVersion))
            return StandardVersionTranslationScheme.MAJOR_AND_FULL;
        else
            return StandardVersionTranslationScheme.UNMODIFIED;
    }

    /**
     * @return true if {@code s} is a parseable integer with value greater than zero, false otherwise.
     */
    private static boolean isPositiveInteger(String s)
    {
        try
        {
            int n = Integer.parseInt(s);
            return n > 0;
        }
        catch (NumberFormatException e)
        {
            return false;
        }
    }
}
