package au.net.causal.maven.plugins.autojdk;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A place where JDK archives can be extracted and installed on the local system.
 */
public interface JdkInstallationTarget
{
    /**
     * Extracts a JDK from an archive, installing it into the autojdk installation directory and generating an appropriate metadata file.
     *
     * @param jdkArchive an archive containing the JDK to install.
     * @param metadata metadata for the JDK.
     *
     * @return the local directory where the JDK was extracted and installed.
     *
     * @throws IOException if an error occurs during the installation.
     */
    public Path installJdkFromArchive(Path jdkArchive, LocalJdkMetadata metadata)
    throws IOException;
}
