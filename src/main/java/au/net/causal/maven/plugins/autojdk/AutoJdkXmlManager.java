package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.xml.config.AutoJdkConfiguration;
import au.net.causal.maven.plugins.autojdk.xml.metadata.JdkSearchUpToDateMetadata;
import au.net.causal.maven.plugins.autojdk.xml.metadata.LocalJdkMetadata;
import au.net.causal.maven.plugins.autojdk.xml.metadata.MavenJdkArtifactMetadata;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.nio.file.Path;

/**
 * Wraps any XML / JAXB related reading / writing.
 */
public class AutoJdkXmlManager
{
    //Add all XML config classes - used to initialize JAXB
    private static final Class<?>[] xmlConfigClasses = new Class<?>[] {
            AutoJdkConfiguration.class,
            MavenJdkArtifactMetadata.class,
            LocalJdkMetadata.class,
            JdkSearchUpToDateMetadata.class
    };

    private final JAXBContext jaxbContext;

    public AutoJdkXmlManager()
    throws JAXBException
    {

        this(JAXBContext.newInstance(xmlConfigClasses));
    }

    public AutoJdkXmlManager(JAXBContext jaxbContext)
    {
        this.jaxbContext = jaxbContext;
    }

    /**
     * Parses an XML configuration file.
     *
     * @param file the XML file to parse.
     * @param type the type defining the XML format to read.
     *
     * @return the parsed XML data.
     * @param <T> the type defining the XML format to read.
     *
     * @throws XmlParseException if an error occurs reading the file.
     */
    public <T> T parseFile(Path file, Class<T> type)
    throws XmlParseException
    {
        return parseFile(file.toFile(), type);
    }

    /**
     * Parses an XML configuration file.
     *
     * @param file the XML file to parse.
     * @param type the type defining the XML format to read.
     *
     * @return the parsed XML data.
     * @param <T> the type defining the XML format to read.
     *
     * @throws XmlParseException if an error occurs reading the file.
     */
    public <T> T parseFile(File file, Class<T> type)
    throws XmlParseException
    {
        try
        {
            return jaxbContext.createUnmarshaller().unmarshal(new StreamSource(file), type).getValue();
        }
        catch (JAXBException e)
        {
            throw new XmlParseException("Error parsing " + file + ": " + e.getMessage(), e);
        }

    }

    /**
     * Writes an XML file given an object containing data to be serialized.
     *
     * @param data data that will be serialized to the file.
     * @param file the file to write.
     *
     * @throws XmlWriteException if an error occurs writing the file.
     */
    public void writeFile(Object data, Path file)
    throws XmlWriteException
    {
        try
        {
            jaxbContext.createMarshaller().marshal(data, file.toFile());
        }
        catch (JAXBException e)
        {
            throw new XmlWriteException("Error writing XML file " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Thrown when an error occurs parsing or validating an XML file.
     */
    public static class XmlParseException extends Exception
    {
        public XmlParseException()
        {
            super();
        }

        public XmlParseException(String message)
        {
            super(message);
        }

        public XmlParseException(String message, Throwable cause)
        {
            super(message, cause);
        }

        public XmlParseException(Throwable cause)
        {
            super(cause);
        }
    }

    /**
     * Thrown when an error occurs writing or generating an XML file.
     */
    public static class XmlWriteException extends Exception
    {
        public XmlWriteException()
        {
        }

        public XmlWriteException(String message)
        {
            super(message);
        }

        public XmlWriteException(String message, Throwable cause)
        {
            super(message, cause);
        }

        public XmlWriteException(Throwable cause)
        {
            super(cause);
        }
    }
}
