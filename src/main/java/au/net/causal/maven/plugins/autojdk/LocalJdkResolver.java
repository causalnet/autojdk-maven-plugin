package au.net.causal.maven.plugins.autojdk;

import java.util.Collection;

/**
 * Finds JDKs that are installed on the local filesystem that can be used for toolchains.
 */
public interface LocalJdkResolver
{
    public Collection<? extends LocalJdk> getInstalledJdks(ReleaseType releaseType)
    throws LocalJdkResolutionException;
}
