package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Entrypoint to AutoJDK functionality.
 */
public class AutoJdk
{
    private final LocalJdkResolver localJdkResolver;
    private final JdkVersionExpander versionExpander;

    public AutoJdk(LocalJdkResolver localJdkResolver, JdkVersionExpander versionExpander)
    {
        this.localJdkResolver = Objects.requireNonNull(localJdkResolver);
        this.versionExpander = Objects.requireNonNull(versionExpander);
    }

    public List<? extends ToolchainModel> generateToolchainsFromLocalJdks()
    throws LocalJdkResolutionException
    {
        List<ToolchainModel> toolchains = new ArrayList<>();
        for (LocalJdk jdk : localJdkResolver.getInstalledJdks())
        {
            for (ArtifactVersion jdkVersion : versionExpander.expandVersions(jdk.getVersion()))
            {
                toolchains.add(localJdkToToolchainModel(jdk, jdkVersion));
            }
        }
        return toolchains;
    }

    private ToolchainModel localJdkToToolchainModel(LocalJdk jdk, ArtifactVersion jdkVersionToRegister)
    {
        ToolchainModel tcm = new ToolchainModel();
        tcm.setType("jdk");
        tcm.addProvide("version", jdkVersionToRegister.toString());
        tcm.addProvide("vendor", jdk.getVendor());
        Xpp3Dom conf = new Xpp3Dom("configuration");
        Xpp3Dom jdkHomeElement = new Xpp3Dom("jdkHome");
        jdkHomeElement.setValue(jdk.getJdkDirectory().toAbsolutePath().toString());
        conf.addChild(jdkHomeElement);
        tcm.setConfiguration(conf);

        return tcm;
    }

    public LocalJdk prepareJdk(JdkSearchRequest searchRequest)
    throws LocalJdkResolutionException, JdkNotFoundException
    {
        //First scan all existing local JDKs and use one of those if there is a match
        LocalJdk localJdk = findMatchingLocalJdk(searchRequest, localJdkResolver.getInstalledJdks());

        if (localJdk == null)
            throw new JdkNotFoundException();

        return localJdk;

        //TODO remotes
    }

    protected LocalJdk findMatchingLocalJdk(JdkSearchRequest searchRequest, Collection<? extends LocalJdk> jdks)
    {
        //Find highest versioned match
        return jdks.stream()
                   .filter(jdk -> localJdkMatches(jdk, searchRequest))
                   .max(Comparator.comparing(LocalJdk::getVersion))
                   .orElse(null);
    }

    private boolean localJdkMatches(LocalJdk jdk, JdkSearchRequest searchRequest)
    {
        //Reject if architecture does not match
        if (!searchRequest.getArchitecture().equals(jdk.getArchitecture()) &&
            !searchRequest.getArchitecture().getSynonyms().contains(jdk.getArchitecture()))
        {
            return false;
        }

        //Reject if operating system does not match
        if (!searchRequest.getOperatingSystem().equals(jdk.getOperatingSystem()) &&
            !searchRequest.getOperatingSystem().getSynonyms().contains(jdk.getOperatingSystem()))
        {
            return false;
        }

        //If vendor was specified in search request, reject JDK if vendor does not match
        if (searchRequest.getVendor() != null && !searchRequest.getVendor().equals(jdk.getVendor()))
            return false;

        //Version comparison
        if (localJdkVersionMatches(jdk.getVersion(), searchRequest.getVersionRange()))
            return false;

        //If we get here it matches
        return true;
    }

    //Needs to have the same logic as toolchains
    private boolean localJdkVersionMatches(ArtifactVersion jdkVersion, VersionRange searchVersion)
    {
        //This logic needs to emulate the logic of how we expand local JDKs into toolchains.xml definitions
        for (ArtifactVersion expandedVersion : versionExpander.expandVersions(jdkVersion))
        {
            //The contents of this loop must be the same as what toolchains does
            if (searchVersion.containsVersion(expandedVersion))
                return true;
        }

        //If we get here it does not match
        return false;
    }
}
