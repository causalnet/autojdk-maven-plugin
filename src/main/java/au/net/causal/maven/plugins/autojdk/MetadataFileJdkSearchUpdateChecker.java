package au.net.causal.maven.plugins.autojdk;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Objects;

/**
 * JDK search update check metadata store that uses an XML file that saves times against searches.
 * <p>
 *
 * Files are written safely - they are written to a temporary file and then renamed atomically.  This is so half-written
 * files are never read.  If two saves are attempted at exactly the same time, one check time might be lost, but that
 * is acceptable since the only consequence is that another search will be reattempted when it shouldn't be.
 *
 * @see JdkSearchUpToDateMetadata
 */
public class MetadataFileJdkSearchUpdateChecker implements JdkSearchUpdateChecker
{
    private final Path metadataFile;
    private final AutoJdkXmlManager xmlManager;
    private final DatatypeFactory datatypeFactory = DatatypeFactory.newDefaultInstance();

    /**
     * Creates an update checker.
     *
     * @param metadataFile the XML metadata file to use for storing times.  Will be created if it does not exist.
     * @param xmlManager XML manager for parsing XML files.
     */
    public MetadataFileJdkSearchUpdateChecker(Path metadataFile, AutoJdkXmlManager xmlManager)
    {
        this.metadataFile = Objects.requireNonNull(metadataFile);
        this.xmlManager = Objects.requireNonNull(xmlManager);
    }

    @Override
    public Instant getLastCheckTime(JdkSearchRequest searchRequest)
    throws JdkSearchUpdateCheckException
    {
        //Read the metadata file
        JdkSearchUpToDateMetadata metadata = readMetadataFile();

        //Find matching element(s) and return max time
        return metadata.getSearches().stream()
                                     .filter(s -> metadataSearchMatchesJdkSearch(s, searchRequest))
                                     .map(JdkSearchUpToDateMetadata.Search::getLastUpdated)
                                     .filter(Objects::nonNull) //May be null if data malformed
                                     .map(cal -> cal.toGregorianCalendar().toInstant())
                                     .max(Comparator.naturalOrder())
                                     .orElse(null);
    }

    @Override
    public void saveLastCheckTime(JdkSearchRequest searchRequest, Instant checkTime)
    throws JdkSearchUpdateCheckException
    {
        //Read metadata file
        JdkSearchUpToDateMetadata metadata = readMetadataFile();

        //Remove existing elements that match search request
        metadata.getSearches().removeIf(s -> metadataSearchMatchesJdkSearch(s, searchRequest));

        //Add a new search with our metadata
        XMLGregorianCalendar checkTimeCal = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar.from(checkTime.atZone(ZoneOffset.UTC)));
        metadata.getSearches().add(new JdkSearchUpToDateMetadata.Search(
                searchRequest.getVersionRange().toString(),
                searchRequest.getArchitecture(),
                searchRequest.getOperatingSystem(),
                searchRequest.getVendor(),
                searchRequest.getReleaseType(),
                checkTimeCal
        ));

        //Save file
        saveMetadataFile(metadata);
    }

    private boolean metadataSearchMatchesJdkSearch(JdkSearchUpToDateMetadata.Search metadataSearch, JdkSearchRequest searchRequest)
    {
        return Objects.equals(metadataSearch.getVendor(), searchRequest.getVendor()) &&
               Objects.equals(metadataSearch.getArchitecture(), searchRequest.getArchitecture()) &&
               Objects.equals(metadataSearch.getOperatingSystem(), searchRequest.getOperatingSystem()) &&
               Objects.equals(metadataSearch.getReleaseType(), searchRequest.getReleaseType()) &&
               Objects.equals(metadataSearch.getVersionRange(), searchRequest.getVersionRange().toString());
    }

    private JdkSearchUpToDateMetadata readMetadataFile()
    throws JdkSearchUpdateCheckException
    {
        //If file does not exist, return empty result
        if (Files.notExists(metadataFile))
            return new JdkSearchUpToDateMetadata();

        //Otherwise read the file and return it
        try
        {
            return xmlManager.parseFile(metadataFile, JdkSearchUpToDateMetadata.class);
        }
        catch (AutoJdkXmlManager.XmlParseException e)
        {
            throw new JdkSearchUpdateCheckException(e);
        }
    }

    private void saveMetadataFile(JdkSearchUpToDateMetadata metadata)
    throws JdkSearchUpdateCheckException
    {
        try
        {
            //If parent directories do not exist, create
            Path parentDir = metadataFile.getParent();
            Files.createDirectories(parentDir);

            //Save it safely - first write to temp file then rename
            Path tempFile = Files.createTempFile(parentDir, "autojdk-search-metadata", ".xml");
            xmlManager.writeFile(metadata, tempFile);

            Files.move(tempFile, metadataFile, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException | AutoJdkXmlManager.XmlWriteException e)
        {
            throw new JdkSearchUpdateCheckException(e);
        }
    }
}
