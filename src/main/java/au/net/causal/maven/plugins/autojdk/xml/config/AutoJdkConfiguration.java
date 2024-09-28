package au.net.causal.maven.plugins.autojdk.xml.config;

import au.net.causal.maven.plugins.autojdk.AutoJdkXmlManager;
import au.net.causal.maven.plugins.autojdk.ExceptionalSupplier;
import au.net.causal.maven.plugins.autojdk.FileDownloader;
import au.net.causal.maven.plugins.autojdk.HttpClientFileDownloader;
import au.net.causal.maven.plugins.autojdk.JdkArchiveRepository;
import au.net.causal.maven.plugins.autojdk.LocalRepositoryCachingRepository;
import au.net.causal.maven.plugins.autojdk.MavenArtifactJdkArchiveRepository;
import au.net.causal.maven.plugins.autojdk.MavenDownloadProgressAdapter;
import au.net.causal.maven.plugins.autojdk.MavenJdkProxySelector;
import au.net.causal.maven.plugins.autojdk.UserConfiguredVendorService;
import au.net.causal.maven.plugins.autojdk.VendorService;
import au.net.causal.maven.plugins.autojdk.config.ActivationProcessor;
import au.net.causal.maven.plugins.autojdk.config.AutoJdkConfigurationException;
import au.net.causal.maven.plugins.autojdk.config.CombinableConfiguration;
import au.net.causal.maven.plugins.autojdk.foojay.FoojayClient;
import au.net.causal.maven.plugins.autojdk.foojay.FoojayOpenApiJdkRepository;
import au.net.causal.maven.plugins.autojdk.foojay.OfflineDistributionsVendorService;
import au.net.causal.maven.plugins.autojdk.foojay.openapi.handler.ApiClient;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * User-global configuration for AutoJDK.
 */
@XmlRootElement(name = "autojdk-configuration")
@XmlType(propOrder={})
public class AutoJdkConfiguration implements CombinableConfiguration<AutoJdkConfiguration>
{
    /**
     * Special vendor name that can be used to substitute any other known vendor that is not already in the list.
     */
    public static final String WILDCARD_VENDOR = "*";

    /**
     * Default vendor preference order.  These were selected to provide maximum compatibility.
     */
    public static final List<String> DEFAULT_VENDORS = List.of(
            "zulu", //Zulu seems to always work and is present all the way back to JDK 6
            "liberica", //Next ones are pretty solid too but not available on all platforms
            "corretto",
            "oracle_open_jdk",
            "temurin",
            WILDCARD_VENDOR //Still allow everything else if the above ones aren't available for a particular platform/JDK version combination
    );

    /**
     * Default JDK update policy duration of 1 day.
     */
    public static final JdkUpdatePolicySpec DEFAULT_JDK_UPDATE_POLICY = new JdkUpdatePolicySpec(new JdkUpdatePolicy.EveryDuration(DatatypeFactory.newDefaultInstance().newDuration(true, DatatypeConstants.FIELD_UNDEFINED, DatatypeConstants.FIELD_UNDEFINED, 1, DatatypeConstants.FIELD_UNDEFINED, DatatypeConstants.FIELD_UNDEFINED, DatatypeConstants.FIELD_UNDEFINED)));

    /**
     * Default JDK repositories are just the foojay repository.
     */
    public static final List<JdkRepository> DEFAULT_JDK_REPOSITORIES = List.of(
            new FoojayDiscoRepository(new FoojayDiscoRepository.LocalRepositoryCache("au.net.causal.autojdk.jdk"))
    );

    private static final Logger log = LoggerFactory.getLogger(AutoJdkConfiguration.class);

    //Method instead of variable since extension exclusion element is mutable
    public static List<ExtensionExclusion> defaultExtensionExclusions()
    {
        return List.of(new ExtensionExclusion("(,8)", "[8,9)"));
    }

    private Activation activation;
    private final List<String> includes = new ArrayList<>();
    private final List<String> vendors = new ArrayList<>();
    private final List<ExtensionExclusion> extensionExclusions = new ArrayList<>();
    private JdkUpdatePolicySpec jdkUpdatePolicy;
    private final List<JdkRepository> jdkRepositories = new ArrayList<>();

    public static AutoJdkConfiguration defaultAutoJdkConfiguration()
    {
        return new AutoJdkConfiguration(null, Collections.emptyList(), DEFAULT_VENDORS, defaultExtensionExclusions(), DEFAULT_JDK_UPDATE_POLICY, DEFAULT_JDK_REPOSITORIES);
    }

    public AutoJdkConfiguration()
    {
        this(null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, Collections.emptyList());
    }

    public AutoJdkConfiguration(Activation activation, List<String> includes, List<String> vendors, List<ExtensionExclusion> extensionExclusions, JdkUpdatePolicySpec jdkUpdatePolicy, List<JdkRepository> jdkRepositories)
    {
        this.activation = activation;
        this.includes.addAll(includes);
        this.vendors.addAll(vendors);
        this.extensionExclusions.addAll(extensionExclusions);
        this.jdkUpdatePolicy = jdkUpdatePolicy;
        this.jdkRepositories.addAll(jdkRepositories);
    }

    /**
     * List of additional configuration files to import.
     */
    @XmlElementWrapper
    @XmlElement(name = "include")
    @Override
    public List<String> getIncludes()
    {
        return includes;
    }

    public void setIncludes(List<String> includes)
    {
        this.includes.clear();
        this.includes.addAll(includes);
    }

    @Override
    public Activation getActivation()
    {
        return activation;
    }

    public void setActivation(Activation activation)
    {
        this.activation = activation;
    }

    /**
     * List of JDK vendors that will be used/preferred in the order they are specified.
     * The special entry {@value WILDCARD_VENDOR} will match all vendors and can be used in the last place to allow any other vendors with matching JDKs should there be no previous matches.
     */
    @XmlElementWrapper
    @XmlElement(name = "vendor")
    public List<String> getVendors()
    {
        return vendors;
    }

    public void setVendors(List<String> vendors)
    {
        this.vendors.clear();
        this.vendors.addAll(vendors);
    }

    @XmlElementWrapper(name = "extension-exclusions")
    @XmlElement(name = "extension-exclusion")
    public List<ExtensionExclusion> getExtensionExclusions()
    {
        return extensionExclusions;
    }

    public void setExtensionExclusions(List<ExtensionExclusion> extensionExclusions)
    {
        this.extensionExclusions.clear();
        this.extensionExclusions.addAll(extensionExclusions);
    }

    @XmlElement(name = "jdk-update-policy")
    public JdkUpdatePolicySpec getJdkUpdatePolicy()
    {
        return jdkUpdatePolicy;
    }

    public void setJdkUpdatePolicy(JdkUpdatePolicySpec jdkUpdatePolicy)
    {
        this.jdkUpdatePolicy = jdkUpdatePolicy;
    }

    @XmlElementWrapper(name = "jdk-repositories")
    @XmlElements({
            @XmlElement(name = "foojay-disco", type = FoojayDiscoRepository.class),
            @XmlElement(name = "maven-repository", type = JdkMavenRepository.class)
    })
    public List<JdkRepository> getJdkRepositories()
    {
        return jdkRepositories;
    }

    public void setJdkRepositories(List<JdkRepository> jdkRepositories)
    {
        this.jdkRepositories.clear();
        this.jdkRepositories.addAll(jdkRepositories);
    }

    /**
     * Combine this configuration with another configuration, preferring other's configuration if there are conflicts.
     *
     * @param other the other configuration to combine with.
     *
     * @return the combined configuration.
     */
    public AutoJdkConfiguration combinedWith(AutoJdkConfiguration other)
    {
        AutoJdkConfiguration combined = new AutoJdkConfiguration();

        combined.setVendors(other.getVendors());
        if (combined.getVendors().isEmpty())
            combined.setVendors(this.getVendors());

        combined.setExtensionExclusions(other.getExtensionExclusions());
        if (combined.getExtensionExclusions().isEmpty())
            combined.setExtensionExclusions(this.getExtensionExclusions());

        combined.setJdkUpdatePolicy(other.getJdkUpdatePolicy());
        if (combined.getJdkUpdatePolicy() == null)
            combined.setJdkUpdatePolicy(this.getJdkUpdatePolicy());

        combined.setJdkRepositories(other.getJdkRepositories());
        if (combined.getJdkRepositories().isEmpty())
            combined.setJdkRepositories(this.getJdkRepositories());

        return combined;
    }

    public static AutoJdkConfiguration fromFile(Path file, AutoJdkXmlManager xmlManager, ActivationProcessor activationProcessor, MavenSession session)
    throws AutoJdkXmlManager.XmlParseException, AutoJdkConfigurationException
    {
        return fromFile(file, xmlManager, activationProcessor, session, defaultAutoJdkConfiguration());
    }

    private static AutoJdkConfiguration fromFile(Path file, AutoJdkXmlManager xmlManager, ActivationProcessor activationProcessor, MavenSession session, AutoJdkConfiguration baseConfig)
    throws AutoJdkXmlManager.XmlParseException, AutoJdkConfigurationException
    {
        //If no config file is present just use the default settings
        if (Files.notExists(file))
            return baseConfig;

        AutoJdkConfiguration configFromFile = xmlManager.parseFile(file, AutoJdkConfiguration.class);

        //Start with base
        var fullConfig = baseConfig;

        //Check if we should process this file at all
        if (configFromFile.getActivation() == null || activationProcessor.isActive(configFromFile.getActivation(), session))
        {
            List<Path> includeFiles = configFromFile.resolveIncludeFiles(file);

            //Resolve/apply imports
            for (Path includeFile : includeFiles) {
                //Ignore files that are specified as import but do not exist
                if (Files.exists(includeFile)) {
                    AutoJdkConfiguration includeConfig = fromFile(includeFile, xmlManager, activationProcessor, session, new AutoJdkConfiguration());

                    if (includeConfig.getActivation() == null || activationProcessor.isActive(includeConfig.getActivation(), session))
                        fullConfig = fullConfig.combinedWith(includeConfig);
                }
            }

            fullConfig = fullConfig.combinedWith(configFromFile);
        }

        //Imports are now resolved so remove them
        fullConfig.setIncludes(List.of());

        return fullConfig;
    }

    private List<Path> resolveIncludeFiles(Path baseConfigFile)
    {
        return getIncludes().stream()
                .map(baseConfigFile::resolveSibling)
                .collect(Collectors.toList());
    }

    @XmlType(propOrder={})
    public static class ExtensionExclusion
    {
        private String version;
        private String substitution;

        public ExtensionExclusion()
        {
        }

        public ExtensionExclusion(String version, String substitution)
        {
            this.version = version;
            this.substitution = substitution;
        }

        public String getVersion()
        {
            return version;
        }

        public void setVersion(String version)
        {
            this.version = version;
        }

        public String getSubstitution()
        {
            return substitution;
        }

        public void setSubstitution(String substitution)
        {
            this.substitution = substitution;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof ExtensionExclusion)) return false;
            ExtensionExclusion that = (ExtensionExclusion) o;
            return Objects.equals(getVersion(), that.getVersion()) && Objects.equals(getSubstitution(), that.getSubstitution());
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(getVersion(), getSubstitution());
        }
    }

    public interface JdkRepository
    {
        public abstract JdkArchiveRepository<?> createJdkArchiveRepository(
                RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession,
                boolean offlineMode, boolean allowHttpJdkDownloads,
                ExceptionalSupplier<Path, IOException> tempDownloadDirectory,
                AutoJdkXmlManager xmlManager,
                AutoJdkConfiguration autoJdkConfiguration,
                Log log
        );
    }

    @XmlType(propOrder={})
    public static class FoojayDiscoRepository implements JdkRepository
    {
        private LocalRepositoryCache localRepositoryCache;

        public FoojayDiscoRepository()
        {
            this(null);
        }

        public FoojayDiscoRepository(LocalRepositoryCache localRepositoryCache)
        {
            this.localRepositoryCache = localRepositoryCache;
        }

        @XmlElement(name = "local-repository-cache")
        public LocalRepositoryCache getLocalRepositoryCache()
        {
            return localRepositoryCache;
        }

        public void setLocalRepositoryCache(LocalRepositoryCache localRepositoryCache)
        {
            this.localRepositoryCache = localRepositoryCache;
        }

        @Override
        public JdkArchiveRepository<?> createJdkArchiveRepository(
                RepositorySystem repositorySystem,
                RepositorySystemSession repositorySystemSession,
                boolean offlineMode, boolean allowHttpJdkDownloads,
                ExceptionalSupplier<Path, IOException> tempDownloadDirectory,
                AutoJdkXmlManager xmlManager,
                AutoJdkConfiguration autoJdkConfiguration,
                Log log
        )
        {
            if (offlineMode)
            {
                //If local-repo caching is enabled it's still possible to resolve from the local repo even when offline
                if (getLocalRepositoryCache() != null && getLocalRepositoryCache().getJdkGroupId() != null) {
                    //Make a local-only Maven artifact repository for offline mode so we can still search it
                    List<RemoteRepository> noRemoteRepositories = List.of();
                    VendorService offlineVendorService = new OfflineDistributionsVendorService();
                    MavenArtifactJdkArchiveRepository localOnlyMavenArchiveRepository = new MavenArtifactJdkArchiveRepository(
                            repositorySystem, repositorySystemSession,
                            noRemoteRepositories, getLocalRepositoryCache().getJdkGroupId(),
                            offlineVendorService, xmlManager);
                    return new LocalRepositoryCachingRepository<>(
                            getLocalRepositoryCache().getJdkGroupId(),
                            localOnlyMavenArchiveRepository,
                            repositorySystem, repositorySystemSession, tempDownloadDirectory,xmlManager);
                }
                //No caching and not online, can't use this repo at all
                else
                    return null;
            }

            //Not offline if we get here

            HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();

            //Proxy + other builder configuration
            MavenJdkProxySelector proxySelector = new MavenJdkProxySelector(repositorySystemSession);
            httpClientBuilder.proxy(proxySelector).authenticator(proxySelector.authenticator());
            if (allowHttpJdkDownloads)
                httpClientBuilder.followRedirects(HttpClient.Redirect.ALWAYS);
            else
                httpClientBuilder.followRedirects(HttpClient.Redirect.NORMAL); //Allow redirect, except from HTTPS URLs to HTTP URLs.


            ApiClient apiClient = FoojayClient.createDefaultApiClient();
            apiClient.setHttpClientBuilder(httpClientBuilder);
            FoojayClient foojayClient = new FoojayClient(apiClient);

            FileDownloader fileDownloader = new HttpClientFileDownloader(tempDownloadDirectory, httpClientBuilder);
            fileDownloader.addDownloadProgressListener(new MavenDownloadProgressAdapter(repositorySystemSession));

            JdkArchiveRepository<?> repository = new FoojayOpenApiJdkRepository(foojayClient, fileDownloader);

            //If local repo cache is enabled, wrap foojay repo with a caching layer
            if (getLocalRepositoryCache() != null && getLocalRepositoryCache().getJdkGroupId() != null)
            {
                repository = new LocalRepositoryCachingRepository<>(
                        getLocalRepositoryCache().getJdkGroupId(),
                        repository,
                        repositorySystem, repositorySystemSession, tempDownloadDirectory,xmlManager);
            }

            return repository;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof FoojayDiscoRepository)) return false;
            FoojayDiscoRepository that = (FoojayDiscoRepository) o;
            return Objects.equals(localRepositoryCache, that.localRepositoryCache);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(localRepositoryCache);
        }

        public static class LocalRepositoryCache
        {
            private String jdkGroupId;

            public LocalRepositoryCache()
            {
                this(null);
            }

            public LocalRepositoryCache(String jdkGroupId)
            {
                this.jdkGroupId = jdkGroupId;
            }

            @XmlElement(name = "jdk-group-id")
            public String getJdkGroupId()
            {
                return jdkGroupId;
            }

            public void setJdkGroupId(String jdkGroupId)
            {
                this.jdkGroupId = jdkGroupId;
            }

            @Override
            public boolean equals(Object o)
            {
                if (this == o) return true;
                if (!(o instanceof LocalRepositoryCache)) return false;
                LocalRepositoryCache that = (LocalRepositoryCache) o;
                return Objects.equals(jdkGroupId, that.jdkGroupId);
            }

            @Override
            public int hashCode()
            {
                return Objects.hashCode(jdkGroupId);
            }
        }
    }

    /**
     * A Maven repository that contains downloadable JDKs and metadata.
     */
    @XmlType(propOrder={})
    public static class JdkMavenRepository implements JdkRepository
    {
        private String id;
        private String name;
        private String url;
        private String jdkGroupId;

        public String getId()
        {
            return id;
        }

        public void setId(String id)
        {
            this.id = id;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getUrl()
        {
            return url;
        }

        public void setUrl(String url)
        {
            this.url = url;
        }

        @XmlElement(name = "jdk-group-id")
        public String getJdkGroupId()
        {
            return jdkGroupId;
        }

        public void setJdkGroupId(String jdkGroupId)
        {
            this.jdkGroupId = jdkGroupId;
        }

        @Override
        public JdkArchiveRepository<?> createJdkArchiveRepository(
                RepositorySystem repositorySystem,
                RepositorySystemSession repositorySystemSession,
                boolean offlineMode,
                boolean allowHttpJdkDownloads,
                ExceptionalSupplier<Path, IOException> tempDownloadDirectory,
                AutoJdkXmlManager xmlManager,
                AutoJdkConfiguration autoJdkConfiguration,
                Log log)
        {
            if (getJdkGroupId() == null)
            {
                log.warn("Ignoring autojdk configured jdk repository " + getId() + ", no jdk-group-id specified");
                return null;
            }
            else if (getUrl() == null)
            {
                log.warn("Ignoring autojdk configured jdk repository " + getId() + ", no repository url specified");
                return null;
            }

            RemoteRepository remoteRepo = jdkRemoteRepository(repositorySystemSession);

            VendorService allVendorService = new OfflineDistributionsVendorService();
            //allVendorService = new FoojayOpenApiVendorService(foojayClient); //TODO we'll miss out on any new vendors

            VendorService userConfiguredVendorService = new UserConfiguredVendorService(allVendorService, autoJdkConfiguration);

            return new MavenArtifactJdkArchiveRepository(
                    repositorySystem, repositorySystemSession, List.of(remoteRepo),
                    getJdkGroupId(), userConfiguredVendorService,
                    xmlManager);
        }

        private RemoteRepository jdkRemoteRepository(RepositorySystemSession repoSession)
        {
            MavenArtifactRepository repo = new MavenArtifactRepository(
                    getId(), getUrl(), new DefaultRepositoryLayout(),
                    new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE),
                    new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE)
            );

            RemoteRepository rRepo = RepositoryUtils.toRepo(repo);

            Proxy proxy = repoSession.getProxySelector().getProxy(rRepo);
            if (proxy != null)
                rRepo = new RemoteRepository.Builder(rRepo).setProxy(proxy).build();

            Authentication auth = repoSession.getAuthenticationSelector().getAuthentication(rRepo);
            if (auth != null)
                rRepo = new RemoteRepository.Builder(rRepo).setAuthentication(auth).build();

            return rRepo;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof JdkMavenRepository)) return false;
            JdkMavenRepository that = (JdkMavenRepository) o;
            return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(url, that.url) && Objects.equals(jdkGroupId, that.jdkGroupId);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(id, name, url, jdkGroupId);
        }
    }

    /**
     * Holds a choice of update policy.
     */
    public static class JdkUpdatePolicySpec
    {
        private JdkUpdatePolicy value;

        public JdkUpdatePolicySpec()
        {
        }

        public JdkUpdatePolicySpec(JdkUpdatePolicy jdkUpdatePolicy)
        {
            this.value = jdkUpdatePolicy;
        }

        /**
         * Which update policy was specified.
         */
        @XmlElements({
                @XmlElement(name = "never", type = JdkUpdatePolicy.Never.class, required = true),
                @XmlElement(name = "always", type = JdkUpdatePolicy.Always.class, required = true),
                @XmlElement(name = "every", type = JdkUpdatePolicy.EveryDuration.class, required = true)
        })
        public JdkUpdatePolicy getValue()
        {
            return value;
        }

        public void setValue(JdkUpdatePolicy value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof JdkUpdatePolicySpec)) return false;
            JdkUpdatePolicySpec that = (JdkUpdatePolicySpec) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(value);
        }
    }
}
