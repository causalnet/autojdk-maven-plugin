package au.net.causal.maven.plugins.autojdk.foojay;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import eu.hansolo.jdktools.Api;
import eu.hansolo.jdktools.ReleaseStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MajorVersion
{
    private Integer majorVersion;
    private Boolean maintained;
    private Boolean earlyAccessOnly;
    private ReleaseStatus releaseStatus;

    @JsonAnySetter
    private final Map<String, Object> otherProperties = new LinkedHashMap<>();

    public Integer getMajorVersion()
    {
        return majorVersion;
    }

    public void setMajorVersion(Integer majorVersion)
    {
        this.majorVersion = majorVersion;
    }

    public Boolean getMaintained()
    {
        return maintained;
    }

    public void setMaintained(Boolean maintained)
    {
        this.maintained = maintained;
    }

    public Boolean getEarlyAccessOnly()
    {
        return earlyAccessOnly;
    }

    public void setEarlyAccessOnly(Boolean earlyAccessOnly)
    {
        this.earlyAccessOnly = earlyAccessOnly;
    }

    public ReleaseStatus getReleaseStatus()
    {
        return releaseStatus;
    }

    public void setReleaseStatus(ReleaseStatus releaseStatus)
    {
        this.releaseStatus = releaseStatus;
    }

    @JsonAnyGetter
    public Map<String, Object> getOtherProperties()
    {
        return otherProperties;
    }

    private String apiString(Api api)
    {
        return api == null ? null : api.getApiString();
    }

    @Override
    public String toString()
    {
        return new StringJoiner(", ", MajorVersion.class.getSimpleName() + "[", "]")
                .add("majorVersion=" + majorVersion)
                .add("maintained=" + maintained)
                .add("earlyAccessOnly=" + earlyAccessOnly)
                .add("releaseStatus=" + apiString(releaseStatus))
                .add("otherProperties=" + otherProperties)
                .toString();
    }
}
