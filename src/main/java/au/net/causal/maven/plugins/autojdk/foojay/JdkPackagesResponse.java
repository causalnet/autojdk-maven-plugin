package au.net.causal.maven.plugins.autojdk.foojay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JdkPackagesResponse
{
    private List<JdkPackage> result;

    public List<JdkPackage> getResult()
    {
        return result;
    }

    public void setResult(List<JdkPackage> result)
    {
        this.result = result;
    }
}
