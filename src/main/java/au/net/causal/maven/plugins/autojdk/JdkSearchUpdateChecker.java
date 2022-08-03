package au.net.causal.maven.plugins.autojdk;

import java.time.Instant;

/**
 * Stores and retrieves check times for JDK search requests.  Used for determining whether an up-to-date check for
 * a particular JDK search should be performed based on the JDK update check policy.
 * <p>
 *
 * Each different search has its own check time.  So a search for JDK [17, 18) with Linux/X64 will have a different
 * check time to a search for JDK [17, 18) with Linux/ARM64.
 */
public interface JdkSearchUpdateChecker
{
    /**
     * Retrieves the last check time for a JDK search.
     *
     * @param searchRequest the JDK search.
     *
     * @return the last check time, or null if this search has not previously been performed.
     *
     * @throws JdkSearchUpdateCheckException if an error occurs.
     */
    public Instant getLastCheckTime(JdkSearchRequest searchRequest)
    throws JdkSearchUpdateCheckException;

    /**
     * Saves the last check time for a JDK search.
     *
     * @param searchRequest the JDK search.
     * @param checkTime the check time to store.
     *
     * @throws JdkSearchUpdateCheckException if an error occurs.
     */
    public void saveLastCheckTime(JdkSearchRequest searchRequest, Instant checkTime)
    throws JdkSearchUpdateCheckException;
}
