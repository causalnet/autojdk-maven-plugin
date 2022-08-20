package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import eu.hansolo.jdktools.util.Helper;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PlatformTools
{
    /**
     * A list of all well known platforms that have a JDK for them.
     */
    //List generated from DiscoReal.generateWellKnownPlatforms()
    //which does a Foojay search across all Java versions and enumerates all platforms of JDKs found
    public static final List<? extends Platform> WELL_KNOWN_PLATFORMS = List.of(
        new Platform(OperatingSystem.AIX, Architecture.PPC64),
        new Platform(OperatingSystem.LINUX, Architecture.AARCH32),
        new Platform(OperatingSystem.LINUX, Architecture.AARCH64),
        new Platform(OperatingSystem.LINUX, Architecture.PPC),
        new Platform(OperatingSystem.LINUX, Architecture.PPC64),
        new Platform(OperatingSystem.LINUX, Architecture.PPC64LE),
        new Platform(OperatingSystem.LINUX, Architecture.RISCV64),
        new Platform(OperatingSystem.LINUX, Architecture.S390X),
        new Platform(OperatingSystem.LINUX, Architecture.X32),
        new Platform(OperatingSystem.LINUX, Architecture.X64),
        new Platform(OperatingSystem.MACOS, Architecture.AARCH64),
        new Platform(OperatingSystem.MACOS, Architecture.X64),
        new Platform(OperatingSystem.SOLARIS, Architecture.SPARCV9),
        new Platform(OperatingSystem.SOLARIS, Architecture.X64),
        new Platform(OperatingSystem.WINDOWS, Architecture.AARCH64),
        new Platform(OperatingSystem.WINDOWS, Architecture.X32),
        new Platform(OperatingSystem.WINDOWS, Architecture.X64)
    );

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

    /**
     * Find the canonical operating system given a possible synonym.  Some operating systems might have synonyms, but it can be
     * useful to always use the same operating system for all its synonyms.
     *
     * @param os operating system to canonicalize.
     *
     * @return the canonical operating system which is equal to {@code os} or has it as a synonym.
     */
    public OperatingSystem canonicalOperatingSystem(OperatingSystem os)
    {
        if (os == null)
            return null;

        Map<String, OperatingSystem> x = OperatingSystem.getAsList()
                                                        .stream()
                                                        .collect(Collectors.toMap(OperatingSystem::getApiString, Function.identity(),
                                                                (a, b) -> (a.getSynonyms().size() > b.getSynonyms().size()) ? b : a));

        return x.get(os.getApiString());
    }
}
