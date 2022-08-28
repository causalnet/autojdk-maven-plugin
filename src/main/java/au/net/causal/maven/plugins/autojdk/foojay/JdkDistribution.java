package au.net.causal.maven.plugins.autojdk.foojay;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class JdkDistribution
{
    private String name;
    private String apiParameter;

    @JsonAnyGetter
    @JsonAnySetter
    private final Map<String, Object> otherProperties = new LinkedHashMap<>();

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getApiParameter()
    {
        return apiParameter;
    }

    public void setApiParameter(String apiParameter)
    {
        this.apiParameter = apiParameter;
    }

    public Map<String, Object> getOtherProperties()
    {
        return otherProperties;
    }

    @Override
    public String toString()
    {
        return new StringJoiner(", ", JdkDistribution.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("apiParameter='" + apiParameter + "'")
                .add("otherProperties=" + otherProperties)
                .toString();
    }
}
