package au.net.causal.maven.plugins.autojdk.foojay;

import au.net.causal.maven.plugins.autojdk.foojay.openapi.DefaultApi;
import au.net.causal.maven.plugins.autojdk.foojay.openapi.handler.ApiClient;
import au.net.causal.maven.plugins.autojdk.foojay.openapi.handler.ApiException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.annotations.VisibleForTesting;
import eu.hansolo.jdktools.Api;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.ArchiveType;
import eu.hansolo.jdktools.FPU;
import eu.hansolo.jdktools.Latest;
import eu.hansolo.jdktools.LibCType;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.ReleaseStatus;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Type-safety wrapper around the generated Foojay OpenAPI client.  Request parameters are made type-safe by using existing enums where possible, and raw
 * Object responses are converted into proper structured response objects.
 */
public class FoojayClient
{
    private final ApiClient apiClient;
    private final DefaultApi api;

    private final boolean useMajorVersionsFallbackOnly = true;

    public FoojayClient(ApiClient apiClient)
    {
        this.apiClient = Objects.requireNonNull(apiClient);
        this.api = new DefaultApi(apiClient);
    }

    public FoojayClient()
    {
        this(createDefaultApiClient());
    }

    @VisibleForTesting
    DefaultApi getApi()
    {
        return api;
    }

    @VisibleForTesting
    ApiClient getApiClient()
    {
        return apiClient;
    }

    public static ApiClient createDefaultApiClient()
    {
        ApiClient apiClient = new ApiClient();
        ObjectMapper om = apiClient.getObjectMapper().registerModule(new SimpleModule()
                        .addDeserializer(OperatingSystem.class, new ApiEnumDeserializer<>(OperatingSystem::fromText))
                        .addDeserializer(Architecture.class, new ApiEnumDeserializer<>(Architecture::fromText))
                        .addDeserializer(ArchiveType.class, new ApiEnumDeserializer<>(ArchiveType::fromText))
                        .addDeserializer(ReleaseStatus.class, new ApiEnumDeserializer<>(ReleaseStatus::fromText))
                        .addDeserializer(LibCType.class, new ApiEnumDeserializer<>(LibCType::fromText)));
        om = enableObjectMapperJsonFeatures(om, JsonReadFeature.ALLOW_TRAILING_COMMA);
        apiClient.setObjectMapper(om);

        apiClient.updateBaseUri("https://api.foojay.io");

        return apiClient;
    }

    private static ObjectMapper enableObjectMapperJsonFeatures(ObjectMapper objectMapper, JsonReadFeature... features)
    {
        if (objectMapper instanceof JsonMapper)
        {
            JsonMapper jsonMapper = (JsonMapper)objectMapper;
            JsonMapper.Builder m = jsonMapper.rebuild();
            for (JsonReadFeature feature : features)
            {
                m = m.enable(feature);
            }
            return m.build();
        }
        else //Not a JSON object mapper, so JsonReadFeatures won't work anyway
            return objectMapper;
    }

    public List<? extends JdkDistribution> getDistributions(
            Boolean includeVersions,
            Boolean includeSynonyms,
            List<String> discoveryScopeId
    )
    throws ApiException
    {
        Object rawResponse = api.getDistributionsV3(includeVersions, includeSynonyms, discoveryScopeId);

        JdkDistributionsResponse typedResponse = apiClient.getObjectMapper().convertValue(rawResponse, JdkDistributionsResponse.class);

        if (typedResponse.getResult() == null)
            return List.of();
        else
            return typedResponse.getResult();
    }

    private List<? extends MajorVersion> getAllMajorVersionsUsingFallback()
    throws ApiException
    {
        List<? extends JdkDistribution> results = getDistributions(true, false, null);
        return results.stream()
                .flatMap(d -> d.getVersions().stream())
                .filter(this::isValidJavaVersionFromDistribution)
                .map(v -> FoojayOpenApiArtifact.mavenSafeVersionFromPkgVersion(v).getMajorVersion())
                .distinct()
                .sorted()
                .map(this::createMajorVersion)
                .collect(Collectors.toList());
    }

    /**
     * Some distributions have weird / bugged version numbers such as "2264" so filter those out.
     *
     * @param v version string.
     *
     * @return true if the version string should be included.
     */
    private boolean isValidJavaVersionFromDistribution(String v)
    {
        //Invalid version numbers are parsable integers that are huge (> 1000)

        //Attempt to parse
        try
        {
            int vint = Integer.parseInt(v);

            //Only allow full version numbers < 1000, otherwise likely they are bad ones (e.g. 1264)
            return vint < 1000;
        }
        catch (NumberFormatException e)
        {
            //Not a full integer, so it can pass the filter
            return true;
        }
    }

    private MajorVersion createMajorVersion(int majorVersionNumber)
    {
        MajorVersion mv = new MajorVersion();
        mv.setMajorVersion(majorVersionNumber);
        return mv;
    }

    public List<? extends MajorVersion> getAllMajorVersions()
    throws ApiException
    {
        if (useMajorVersionsFallbackOnly)
            return getAllMajorVersionsUsingFallback();

        Object rawResponse;

        //Disabled for now since getAllMajorVersionsV3 misses some minor versions
        try
        {
            rawResponse = api.getAllMajorVersionsV3(null, null, null, false, null, null, null);
        }
        catch (ApiException e)
        {
            if (e.getCause() instanceof JsonParseException)
            {
                //Foojay service has issues with getAllMajorVersions at times, fallback to parsing major versions from distributions
                return getAllMajorVersionsUsingFallback();
            }
            throw e;
        }


        MajorVersionsResponse typedResponse = apiClient.getObjectMapper().convertValue(rawResponse, MajorVersionsResponse.class);

        if (typedResponse.getResult() == null)
            return List.of();
        else
            return typedResponse.getResult();
    }

    public List<? extends JdkPackage> getJdkPackages(
            String version,
            Integer jdkVersion,
            List<String> distro,
            List<String> distribution,
            Collection<Architecture> architecture,
            Collection<FPU> fpu,
            Collection<ArchiveType> archiveType,
            Collection<OperatingSystem> operatingSystem,
            Collection<LibCType> libcType,
            Collection<LibCType> libCType,
            Collection<ReleaseStatus> releaseStatus,
            Boolean javafxBundled,
            Boolean withJavafxIfAvailable,
            Latest latest,
            Boolean signatureAvailable,
            Boolean freeToUseInProduction,
            String tckTested,
            String aqavitCertified,
            String match
    )
    throws ApiException
    {
        Object rawResponse = api.getJDKPackagesV3(
                version,
                jdkVersion,
                distro,
                distribution,
                apiValuesToString(architecture),
                apiValuesToString(fpu),
                apiValuesToString(archiveType),
                apiValuesToString(operatingSystem),
                apiValuesToString(libcType),
                apiValuesToString(libCType),
                apiValuesToString(releaseStatus),
                javafxBundled,
                withJavafxIfAvailable,
                latest == null ? null : latest.getApiString(),
                signatureAvailable,
                freeToUseInProduction,
                tckTested,
                aqavitCertified,
                match
        );

        JdkPackagesResponse typedResponse = apiClient.getObjectMapper().convertValue(rawResponse, JdkPackagesResponse.class);

        if (typedResponse.getResult() == null)
            return List.of();
        else
            return typedResponse.getResult();
    }

    private <T extends Api> List<String> apiValuesToString(Collection<T> apiValues)
    {
        if (apiValues == null)
            return null;

        return apiValues.stream()
                        .map(Api::getApiString)
                        .collect(Collectors.toUnmodifiableList());
    }
}
