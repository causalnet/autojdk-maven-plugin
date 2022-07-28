package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.util.Helper;

import java.util.Locale;

public class PlatformTools
{
    public OperatingSystem getCurrentOperatingSystem()
    {
        OperatingSystem operatingSystem = Helper.getOperatingSystem();
        if (operatingSystem == OperatingSystem.NOT_FOUND)
            throw new RuntimeException("Could not determine current operating system.");

        return operatingSystem;
    }

    public Architecture getCurrentArchitecture()
    {
        Architecture architecture = Helper.getArchitecture();
        if (architecture == Architecture.NOT_FOUND)
        {
            //Fallback/special case for RISC-V which jdktools does not detect
            String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
            if (arch.contains("riscv64"))
                return Architecture.RISCV64;

            throw new RuntimeException("Could not determine current system architecture.");
        }

        return canonicalArchitecture(architecture);
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
