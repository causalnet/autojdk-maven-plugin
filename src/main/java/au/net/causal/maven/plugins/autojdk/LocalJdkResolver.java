package au.net.causal.maven.plugins.autojdk;

import au.net.causal.maven.plugins.autojdk.xml.metadata.ReleaseType;

import java.util.Collection;

/**
 * Finds JDKs that are installed on the local filesystem that can be used for toolchains.
 */
public interface LocalJdkResolver
{
    public Collection<? extends LocalJdk> getInstalledJdks(ReleaseType releaseType)
    throws LocalJdkResolutionException;
}
