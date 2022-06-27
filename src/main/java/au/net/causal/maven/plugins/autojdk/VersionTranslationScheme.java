package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Collection;

/**
 * Translates between the world of toolchain specifications in projects which may not be fully 'maven version' compatible and
 * the world of defining toolchain JDK version numbers so they can be found.
 * <p>
 *
 * Toolchain version specs are supposed to be fully based on Maven versioning like how it works with dependencies, so a toolchain spec in a project of "17" actually
 * translates to Java version "17" explicitly, not "17.1" or anything like that.  However, because toolchains.xml JDK definitions for users are also loosely defined,
 * and users have defined any version of Java 17 as "17" in their toolchains.xml, this can still work.  However, this makes having multiple versions of Java 17
 * in toolchains impossible or at least impractical, since if a project defines "17" does it really mean "I want 17.0.0" or "I want any version of Java 17".  What is supposed
 * to happen is that project specs actually have a range definition "[17, 18)" which would all work properly and consistently, but many users do not do this.
 * <p>
 *
 * So to work around these issues, different version translations schemes may be used that:
 *
 * <ul>
 *     <li>
 *         Given a project definition of a required toolchain (which may be a version range or just an explicit version), translate that into an actual
 *         version range that we should search local and remote downloadable JDKs with.  e.g. "17" might translate to "17" for an explicit Java 17[.0.0] search, or
 *         with a different implementation, translate "17" into a range request "[17, 18)".
 *     </li>
 *     <li>
 *         And the opposite - when registering local JDKs, what version(s) should they be registered under.  One implementation would just render explicit version numbers only,
 *         so Java 17.0.2_4 is only registered as a toolchain JDK under version "17.0.2_4" - but that would mean only project requirements that either use this explicit version
 *         or a suitable range request would find it and work.  Or another implementation that seeks compatibility for projects that have a requirement of "17" but actually want
 *         "any version of Java 17" would register local Java 17.0.2_4 under both that explicit version number and "17" (so registering twice) so they can be picked up more easily -
 *         albeit with the downside of a project that explicitly asks for "17.0.0" would unfortunately pick up "17" as a match despite it actually being Java 17.0.2.
 *     </li>
 * </ul>
 */
public interface VersionTranslationScheme
{
    /**
     * Given a local JDK version, returns what versions this JDK should be registered under in the JDK toolchains definitions.
     *
     * @param actualJdkVersion the actual full JDK version.
     *
     * @return a collection of versions to register this JDK under.
     */
    public Collection<? extends ArtifactVersion> expandJdkVersionForRegistration(ArtifactVersion actualJdkVersion);

    /**
     * Translates a JDK version/range requirement from a project into a search criteria.
     *
     * @param projectRequiredJdkVersion the version that a project defined it needed as a requirement.
     *
     * @return the version/range to use for searching and matching JDKs.
     */
    public VersionRange translateProjectRequiredJdkVersionToSearchCriteria(VersionRange projectRequiredJdkVersion);
}
