package au.net.causal.maven.plugins.autojdk.foojay;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import eu.hansolo.jdktools.Api;
import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.ArchiveType;
import eu.hansolo.jdktools.LibCType;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.ReleaseStatus;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class JdkPackage
{
    private String id;
    private String javaVersion;
    private String distribution;
    private Architecture architecture;
    private OperatingSystem operatingSystem;
    private ArchiveType archiveType;
    private ReleaseStatus releaseStatus;
    private Links links;
    private Integer majorVersion;
    private Integer jdkVersion;
    private @JsonProperty("lib_c_type") LibCType libCType;

    @JsonAnyGetter
    @JsonAnySetter
    private final Map<String, Object> otherProperties = new LinkedHashMap<>();

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getJavaVersion()
    {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion)
    {
        this.javaVersion = javaVersion;
    }

    public String getDistribution()
    {
        return distribution;
    }

    public void setDistribution(String distribution)
    {
        this.distribution = distribution;
    }

    public Architecture getArchitecture()
    {
        return architecture;
    }

    public void setArchitecture(Architecture architecture)
    {
        this.architecture = architecture;
    }

    public OperatingSystem getOperatingSystem()
    {
        return operatingSystem;
    }

    public void setOperatingSystem(OperatingSystem operatingSystem)
    {
        this.operatingSystem = operatingSystem;
    }

    public ArchiveType getArchiveType()
    {
        return archiveType;
    }

    public void setArchiveType(ArchiveType archiveType)
    {
        this.archiveType = archiveType;
    }

    public ReleaseStatus getReleaseStatus()
    {
        return releaseStatus;
    }

    public void setReleaseStatus(ReleaseStatus releaseStatus)
    {
        this.releaseStatus = releaseStatus;
    }

    public Links getLinks()
    {
        return links;
    }

    public void setLinks(Links links)
    {
        this.links = links;
    }

    public Integer getMajorVersion()
    {
        return majorVersion;
    }

    public void setMajorVersion(Integer majorVersion)
    {
        this.majorVersion = majorVersion;
    }

    public Integer getJdkVersion()
    {
        return jdkVersion;
    }

    public void setJdkVersion(Integer jdkVersion)
    {
        this.jdkVersion = jdkVersion;
    }

    public LibCType getLibCType()
    {
        return libCType;
    }

    public void setLibCType(LibCType libCType)
    {
        this.libCType = libCType;
    }

    //Useful for debugging
    public Map<String, Object> getOtherProperties()
    {
        return otherProperties;
    }

    @Override
    public String toString()
    {
        return new StringJoiner(", ", JdkPackage.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("javaVersion='" + javaVersion + "'")
                .add("jdkVersion=" + jdkVersion)
                .add("majorVersion=" + majorVersion)
                .add("distribution='" + distribution + "'")
                .add("architecture=" + apiString(architecture))
                .add("operatingSystem=" + apiString(operatingSystem) )
                .add("archiveType=" + apiString(archiveType))
                .add("releaseStatus=" + apiString(releaseStatus))
                .add("libCType=" + apiString(libCType))
                .add("links=" + links)
                .add("otherProperties=" + otherProperties)
                .toString();
    }

    private String apiString(Api api)
    {
        return api == null ? null : api.getApiString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Links
    {
        private URI pkgInfoUri;
        private URI pkgDownloadRedirect;

        public URI getPkgInfoUri()
        {
            return pkgInfoUri;
        }

        public void setPkgInfoUri(URI pkgInfoUri)
        {
            this.pkgInfoUri = pkgInfoUri;
        }

        public URI getPkgDownloadRedirect()
        {
            return pkgDownloadRedirect;
        }

        public void setPkgDownloadRedirect(URI pkgDownloadRedirect)
        {
            this.pkgDownloadRedirect = pkgDownloadRedirect;
        }

        @Override
        public String toString()
        {
            return new StringJoiner(", ", Links.class.getSimpleName() + "[", "]")
                    .add("pkgInfoUri=" + pkgInfoUri)
                    .add("pkgDownloadRedirect=" + pkgDownloadRedirect)
                    .toString();
        }
    }
}
