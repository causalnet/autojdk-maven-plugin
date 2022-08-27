package au.net.causal.maven.plugins.autojdk.foojay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MajorVersionsResponse
{
    private List<MajorVersion> result;

    public List<MajorVersion> getResult()
    {
        return result;
    }

    public void setResult(List<MajorVersion> result)
    {
        this.result = result;
    }
}
