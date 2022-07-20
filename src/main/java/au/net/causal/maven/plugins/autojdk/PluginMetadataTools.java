package au.net.causal.maven.plugins.autojdk;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

/**
 * Utilities for reading plugin metadata.
 */
public final class PluginMetadataTools
{
    /**
     * Private constructor to prevent instantiation.
     */
    private PluginMetadataTools()
    {
    }

    /**
     * Reads the version of a plugin or extension on the classpath according to its Maven metadata.
     *
     * @param groupId the group ID of the plugin or extension.
     * @param artifactId the artifact ID of the plugin or extension.
     * @param referenceClass a class from the plugin or extension, used for classloader resolution.
     *
     * @return the version of the plugin according to its Maven metadata, never null.
     *
     * @throws ArtifactMetadataException if the version could not be read, possibly because of missing or malformed metadata.
     */
    public static String lookupArtifactVersion(String groupId, String artifactId, Class<?> referenceClass)
    throws ArtifactMetadataException
    {
        URL artifactMetadataPropertiesResource =
                referenceClass.getResource("/META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties");
        if (artifactMetadataPropertiesResource == null)
            throw new ArtifactMetadataException("Could not find plugin metadata resource for " + groupId + ":" + artifactId + ".");

        Properties metadataProperties = new Properties();
        try (InputStream metadataPropertiesIs = artifactMetadataPropertiesResource.openStream())
        {
            metadataProperties.load(metadataPropertiesIs);
        }
        catch (IOException e)
        {
            throw new ArtifactMetadataException("Error reading plugin metadata resource for " + groupId + ":" + artifactId + ": " + e.getMessage(), e);
        }

        String version = metadataProperties.getProperty("version");
        if (version == null)
            throw new ArtifactMetadataException("No version property in plugin metadata for " + groupId + ":" + artifactId + ".");

        return version;
    }

    /**
     * Thrown when metadata for a plugin or extension could not be read.
     */
    public static class ArtifactMetadataException extends Exception
    {
        public ArtifactMetadataException()
        {
        }

        public ArtifactMetadataException(String message)
        {
            super(message);
        }

        public ArtifactMetadataException(String message, Throwable cause)
        {
            super(message, cause);
        }

        public ArtifactMetadataException(Throwable cause)
        {
            super(cause);
        }
    }
}
