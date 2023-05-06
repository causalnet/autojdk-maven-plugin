package au.net.causal.maven.plugins.autojdk.config;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "sample-config")
@XmlType(propOrder={})
public class SampleConfig implements CombinableConfiguration<SampleConfig>
{
    private final List<String> includes = new ArrayList<>();
    private Activation activation;

    private List<String> dataItems;

    @XmlElementWrapper
    @XmlElement(name = "include")
    @Override
    public List<String> getIncludes()
    {
        return includes;
    }

    @XmlElement
    @Override
    public Activation getActivation()
    {
        return activation;
    }

    public void setActivation(Activation activation)
    {
        this.activation = activation;
    }

    @XmlElementWrapper
    @XmlElement(name = "dataItem")
    public List<String> getDataItems()
    {
        return dataItems;
    }

    public void setDataItems(List<String> dataItems)
    {
        this.dataItems = dataItems;
    }

    @Override
    public SampleConfig combinedWith(SampleConfig other)
    {
        SampleConfig combo = new SampleConfig();

        //Combine all elements, preferring us
        if (this.getDataItems() != null)
            combo.setDataItems(new ArrayList<>(this.getDataItems()));
        else if (other.getDataItems() != null)
            combo.setDataItems(new ArrayList<>(other.getDataItems()));

        return combo;
    }
}
