package au.net.causal.maven.plugins.autojdk.foojay;

import au.net.causal.maven.plugins.autojdk.foojay.openapi.DefaultApi;
import au.net.causal.maven.plugins.autojdk.foojay.openapi.handler.ApiClient;
import au.net.causal.maven.plugins.autojdk.foojay.openapi.handler.ApiException;
import com.fasterxml.jackson.databind.module.SimpleModule;
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

    public FoojayClient(ApiClient apiClient)
    {
        this.apiClient = Objects.requireNonNull(apiClient);
        this.api = new DefaultApi(apiClient);
    }

    public FoojayClient()
    {
        this(createDefaultApiClient());
    }

    private static ApiClient createDefaultApiClient()
    {
        ApiClient apiClient = new ApiClient();
        apiClient.setObjectMapper(apiClient.getObjectMapper().registerModule(new SimpleModule()
                .addDeserializer(OperatingSystem.class, new ApiEnumDeserializer<>(OperatingSystem::fromText))
                .addDeserializer(Architecture.class, new ApiEnumDeserializer<>(Architecture::fromText))
                .addDeserializer(ArchiveType.class, new ApiEnumDeserializer<>(ArchiveType::fromText))
                .addDeserializer(ReleaseStatus.class, new ApiEnumDeserializer<>(ReleaseStatus::fromText))
        ));
        //apiClient.setObjectMapper(apiClient.getObjectMapper().registerModule(new SimpleModule().addDeserializer(Enum.class, new ApiEnumDeserializer())));
        apiClient.updateBaseUri("https://api.foojay.io");

        return apiClient;
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
