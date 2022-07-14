package au.net.causal.maven.plugins.autojdk;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import io.foojay.api.discoclient.DiscoClient;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static au.net.causal.maven.plugins.autojdk.PrepareMojo.*;

//TODO a lot of duplicated code, refactor shared code into abstract superclass

@Mojo(name="platform-support")
public class ShowPlatformSupportMojo extends AbstractMojo
{
    private static final String VERSION_TRANSLATION_SCHEME_AUTO = "auto";

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true)
    protected List<RemoteRepository> remoteRepositories;

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/autojdk-download", required = true)
    protected File downloadDirectory;

    private boolean downloadDirectorySetUp;

    @Parameter(property = "autojdk.jdk.version")
    private String requiredJdkVersion;

    @Parameter(property = PROPERTY_JDK_VENDOR)
    private String requiredJdkVendor;

    @Parameter(property = "autojdk.versionTranslationScheme", required = true, defaultValue = VERSION_TRANSLATION_SCHEME_AUTO)
    private String versionTranslationScheme;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        Map<String, String> toolchainJdkRequirements = readToolchainsJdkRequirements();
        if (requiredJdkVersion == null)
            requiredJdkVersion = toolchainJdkRequirements.get("version");
        if (requiredJdkVendor == null)
            requiredJdkVendor = toolchainJdkRequirements.get("vendor");

        if (requiredJdkVersion == null)
            throw new MojoExecutionException("No required JDK version configured.  Either configure directly on the plugin or add toolchains plugin with appropriate configuration.");

        Path userHome = Path.of(StandardSystemProperty.USER_HOME.value());
        Path m2Home = userHome.resolve(".m2");
        Path autojdkHome = m2Home.resolve("autojdk");
        Path autoJdkInstallationDirectory = autojdkHome.resolve("jdks");

        PlatformTools platformTools = new PlatformTools();

        DiscoClient discoClient = DiscoClientSingleton.discoClient();
        FileDownloader fileDownloader = new SimpleFileDownloader(this::tempDownloadDirectory);

        AutoJdkInstalledJdkSystem localJdkResolver = new AutoJdkInstalledJdkSystem(autoJdkInstallationDirectory);

        AutoJdkConfiguration autoJdkConfiguration = AutoJdkConfiguration.defaultAutoJdkConfiguration();
        VendorService vendorService = new VendorService(discoClient, autoJdkConfiguration);
        List<JdkArchiveRepository<?>> jdkArchiveRepositories = List.of(
                new MavenArtifactJdkArchiveRepository(repositorySystem, repoSession, remoteRepositories, "au.net.causal.autojdk.jdk", vendorService),
                new FoojayJdkRepository(discoClient, repositorySystem, repoSession, fileDownloader, "au.net.causal.autojdk.jdk")
        );
        VersionTranslationScheme versionTranslationScheme = getVersionTranslationScheme();
        AutoJdk autoJdk = new AutoJdk(localJdkResolver, localJdkResolver, jdkArchiveRepositories, versionTranslationScheme, autoJdkConfiguration);

        try
        {
            //Ensure we have an available JDK to work with
            JdkSearchRequest jdkSearchRequest =  new JdkSearchRequest(VersionRange.createFromVersionSpec(requiredJdkVersion),
                                                                      null,
                                                                      null,
                                                                      requiredJdkVendor);

            Collection<? extends JdkArtifact> results = autoJdk.findArtifactsInAllRepositories(jdkSearchRequest);

            //Organize into platforms
            Multimap<Platform, JdkArtifact> resultMap = ArrayListMultimap.create();

            for (JdkArtifact result : results)
            {
                Platform platform = new Platform(result.getOperatingSystem(), platformTools.canonicalArchitecture(result.getArchitecture()));
                resultMap.put(platform, result);
            }

            List<Platform> platforms = resultMap.keySet().stream().sorted(Comparator.comparing(Platform::toString)).collect(Collectors.toUnmodifiableList());
            for (Platform platform : platforms)
            {
                Collection<JdkArtifact> resultsForPlatform = resultMap.get(platform);

                Map<String, List<JdkArtifact>> resultsForPlatformByVendor = resultsForPlatform.stream().collect(Collectors.groupingBy(JdkArtifact::getVendor));

                List<String> sortedVendors = resultsForPlatformByVendor.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toUnmodifiableList());
                StringBuilder buf = new StringBuilder();
                for (String vendor : sortedVendors)
                {
                    List<JdkArtifact> vendorResults = resultsForPlatformByVendor.get(vendor);
                    JdkArtifact bestVendorResult = vendorResults.stream().max(autoJdk.jdkComparator()).get();
                    long distinctVersions = vendorResults.stream().map(JdkArtifact::getVersion).distinct().count();

                    if (buf.length() > 0)
                        buf.append(", ");

                    buf.append(vendor);
                    buf.append("(").append(distinctVersions).append("):");
                    buf.append(bestVendorResult.getVersion());
                }

                getLog().info(platform + " - " + buf);
            }
        }
        catch (InvalidVersionSpecificationException e)
        {
            throw new MojoExecutionException("Invalid JDK version/range: " + requiredJdkVersion, e);
        }
    }

    private static class Platform
    {
        private final OperatingSystem operatingSystem;
        private final Architecture architecture;

        public Platform(OperatingSystem operatingSystem, Architecture architecture)
        {
            this.operatingSystem = Objects.requireNonNull(operatingSystem);
            this.architecture = Objects.requireNonNull(architecture);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof Platform)) return false;
            Platform platform = (Platform) o;
            return operatingSystem == platform.operatingSystem && architecture == platform.architecture;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(operatingSystem, architecture);
        }

        @Override
        public String toString()
        {
            return operatingSystem.getApiString() + "-" + architecture.getApiString();
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
