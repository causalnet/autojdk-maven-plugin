package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import javax.xml.datatype.XMLGregorianCalendar;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * XML metadata for up-to-date checks for JDK searches.
 */
@XmlRootElement(name = "autojdk-search-update-checks")
public class JdkSearchUpToDateMetadata
{
    private final List<Search> searches = new ArrayList<>();

    @XmlElement(name = "search")
    public List<Search> getSearches()
    {
        return searches;
    }

    public void setSearches(List<Search> searches)
    {
        this.searches.clear();
        this.searches.addAll(searches);
    }

    public static class Search
    {
        private String versionRange;
        private Architecture architecture;
        private OperatingSystem operatingSystem;
        private String vendor;
        private ReleaseType releaseType;
        private XMLGregorianCalendar lastUpdated;

        public Search()
        {
        }

        public Search(String versionRange, Architecture architecture, OperatingSystem operatingSystem, String vendor, ReleaseType releaseType, XMLGregorianCalendar lastUpdated)
        {
            this.versionRange = versionRange;
            this.architecture = architecture;
            this.operatingSystem = operatingSystem;
            this.vendor = vendor;
            this.releaseType = releaseType;
            this.lastUpdated = lastUpdated;
        }

        public String getVersionRange()
        {
            return versionRange;
        }

        public void setVersionRange(String versionRange)
        {
            this.versionRange = versionRange;
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

        public String getVendor()
        {
            return vendor;
        }

        public void setVendor(String vendor)
        {
            this.vendor = vendor;
        }

        public ReleaseType getReleaseType()
        {
            return releaseType;
        }

        public void setReleaseType(ReleaseType releaseType)
        {
            this.releaseType = releaseType;
        }

        public XMLGregorianCalendar getLastUpdated()
        {
            return lastUpdated;
        }

        public void setLastUpdated(XMLGregorianCalendar lastUpdated)
        {
            this.lastUpdated = lastUpdated;
        }

        @Override
        public String toString()
        {
            return new StringJoiner(", ", Search.class.getSimpleName() + "[", "]")
                    .add("versionRange='" + versionRange + "'")
                    .add("architecture=" + architecture)
                    .add("operatingSystem=" + operatingSystem)
                    .add("vendor='" + vendor + "'")
                    .add("releaseType=" + releaseType)
                    .add("lastUpdated=" + lastUpdated)
                    .toString();
        }
    }
}
