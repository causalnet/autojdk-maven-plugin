package au.net.causal.maven.plugins.autojdk.foojay;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class JdkDistribution
{
    private String name;
    private String apiParameter;
    private final List<String> synonyms = new ArrayList<>();
    private final List<String> versions = new ArrayList<>();

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

    public List<String> getSynonyms()
    {
        return synonyms;
    }

    public void setSynonyms(List<String> synonyms)
    {
        this.synonyms.clear();
        this.synonyms.addAll(synonyms);
    }

    public List<String> getVersions()
    {
        return versions;
    }

    public void setVersions(List<String> versions)
    {
        this.versions.clear();
        this.versions.addAll(versions);
    }

    @JsonAnyGetter
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
                .add("synonyms=" + synonyms)
                .add("otherProperties=" + otherProperties)
                .toString();
    }
}
