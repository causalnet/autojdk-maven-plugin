package au.net.causal.maven.plugins.autojdk;

import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.util.Constants;
import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.concurrent.LazyInitializer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Singleton lazy-instantiator of disco client.
 */
public final class DiscoClientSingleton
{
    private static final DiscoClientInitializer discoClientInitializer = new DiscoClientInitializer();

    /**
     * Private constructor to prevent instantiation.
     */
    private DiscoClientSingleton()
    {
    }

    //If we get desperate enough we can do some reflection hacks to install a custom properties that doesn't store but closes the output stream
    /*
    public static void configureDiscoHacks()
    {
        try
        {
            Properties replacementProperties = new NoStoreProperties();
            replacementProperties.putAll(PropertyManager.INSTANCE.getProperties());
            Field propertyManagerPropertiesField = PropertyManager.class.getDeclaredField("properties");
            propertyManagerPropertiesField.setAccessible(true);
            propertyManagerPropertiesField.set(PropertyManager.INSTANCE, replacementProperties);
        }
        catch (ReflectiveOperationException e)
        {
            log.warn("Failed to apply properties workaround for discoclient: " + e, e);
        }
    }
     */

    /**
     * @return the singleton disco client.
     */
    public static DiscoClient discoClient()
    {
        try
        {
            return discoClientInitializer.get();
        }
        catch (ConcurrentException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static class DiscoClientInitializer extends LazyInitializer<DiscoClient>
    {
        @Override
        protected DiscoClient initialize()
        {
            DiscoClient discoClient = new DiscoClient();

            workAroundDiscoClientExtraPropertiesFileGeneration();

            //This forces the DISTRIBUTIONS field to be populated, waits for it to finish and sets initialized to true
            DiscoClient.getDistributionFromText("");

            //If after this point no more disco client constructors are called it should be safe

            return discoClient;
        }

        /**
         * Work around disco client's extra properties file generation in the current directory (as opposed to discoclient.properties in the user home directory which
         * is what is supposed to be generated/used).  This only happens when disco client object is created, so afterwards, detect the extra file and mark it for
         * deletion at exit.  Can't delete it sooner on some platforms because it is written using an unclosed FileOutputStream - ugh.
         */
        private void workAroundDiscoClientExtraPropertiesFileGeneration()
        {
            //After this call, unfortunately this will cause an extra discoclient.properties to be written
            //let's just delete it on exit so as to not clutter up the system
            String extraDiscoClientPropertiesFileName = String.join(File.separator, System.getProperty("user.dir"), Constants.PROPERTIES_FILE_NAME); //Copied from Discoclient code exactly
            Path extraDiscoClientPropertiesFile = Path.of(extraDiscoClientPropertiesFileName);

            //Ensure we don't delete it though if it's the same as the main file that is supposed to exist in the home dir
            String propFilePath = new StringBuilder(Constants.HOME_FOLDER).append(File.separator).append(Constants.PROPERTIES_FILE_NAME).toString(); //copied from Discoclient code exactly
            Path propFile = Path.of(propFilePath);

            if (!propFile.equals(extraDiscoClientPropertiesFile) && Files.isRegularFile(extraDiscoClientPropertiesFile))
                extraDiscoClientPropertiesFile.toFile().deleteOnExit();
        }
    }

    private static class NoStoreProperties extends Properties
    {
        @Override
        public void store(OutputStream out, String comments) throws IOException
        {
            //Don't actually save the file but close it - solves the issue with the unclosed FileOutputStream being allowed to live on
            out.close();
        }
    }
}
