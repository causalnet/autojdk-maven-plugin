package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.foojay.openapi.DefaultApi;
import au.net.causal.maven.plugins.autojdk.foojay.openapi.handler.ApiClient;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DiscoReal2
{
    @Test
    void test()
    throws Exception
    {
        var apiClient = new ApiClient();
        apiClient.updateBaseUri("https://api.foojay.io");
        var foojay = new DefaultApi(apiClient);
        var results = foojay.getJDKPackagesV3("17", null, null, null, List.of(Architecture.X64.getApiString()), null, null, List.of(OperatingSystem.WINDOWS.getApiString()), null, null, null, null, null, null, null, true, null, null, null);


        //Convert
        var typedResult = apiClient.getObjectMapper().convertValue(results, JdkPackagesResponse.class);

        //String s = apiClient.getObjectMapper().writeValueAsString(results);



        System.out.println(results);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JdkPackagesResponse
    {
        public List<Pkg> result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Pkg
    {
        public String id;
        public String distribution;
        public String archiveType;
        public String jdkVersion;
        public String distributionVersion;

        @JsonAnyGetter @JsonAnySetter
        public Map<String, Object> otherProperties = new LinkedHashMap<>();
    }
}
