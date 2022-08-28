package au.net.causal.maven.plugins.autojdk.foojay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JdkDistributionsResponse
{
    private List<JdkDistribution> result;

    public List<JdkDistribution> getResult()
    {
        return result;
    }

    public void setResult(List<JdkDistribution> result)
    {
        this.result = result;
    }
}
