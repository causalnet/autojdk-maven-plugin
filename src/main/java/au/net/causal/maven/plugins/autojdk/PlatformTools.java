package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.util.Helper;

public class PlatformTools
{
    public OperatingSystem getCurrentOperatingSystem()
    {
        return Helper.getOperatingSystem();
    }

    public Architecture getCurrentArchitecture()
    {
        return canonicalArchitecture(Helper.getArchitecture());
    }

    /**
     * Find the canonical architecture given a possible synonym.  Some architectures might have synonyms, but it can be
     * useful to always use the same architecture for all its synonyms.
     *
     * @param arch architecture to canonicalize.
     *
     * @return the canonical architecture which is equal to {@code arch} or has it as a synonym.
     */
    public Architecture canonicalArchitecture(Architecture arch)
    {
        if (arch == null)
            return null;

        for (Architecture cur : Architecture.values())
        {
            if (cur == arch || cur.getSynonyms().contains(arch))
                return cur;
        }

        return arch;
    }
}
