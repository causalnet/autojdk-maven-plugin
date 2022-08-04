package au.net.causal.maven.plugins.autojdk;

import com.google.common.base.StandardSystemProperty;

import java.nio.file.Path;
import java.util.Objects;

/**
 * AutoJDK home directory, used for holding local JDKs and configuration.
 */
public class AutoJdkHome
{
    private final Path autoJdkHomeDirectory;

    public static AutoJdkHome defaultHome()
    {
        Path userHome = Path.of(StandardSystemProperty.USER_HOME.value());
        Path m2Home = userHome.resolve(".m2");
        Path autoJdkHome = m2Home.resolve("autojdk");

        return new AutoJdkHome(autoJdkHome);
    }

    public AutoJdkHome(Path autoJdkHomeDirectory)
    {
        this.autoJdkHomeDirectory = Objects.requireNonNull(autoJdkHomeDirectory);
    }

    public Path getAutoJdkHomeDirectory()
    {
        return autoJdkHomeDirectory;
    }

    public Path getLocalJdksDirectory()
    {
        return getAutoJdkHomeDirectory().resolve("jdks");
    }

    public Path getAutoJdkConfigurationFile()
    {
        return getAutoJdkHomeDirectory().resolve("autojdk-configuration.xml");
    }

    public Path getAutoJdkSearchUpToDateCheckMetadataFile()
    {
        return getAutoJdkHomeDirectory().resolve("autojdk-search-uptodate-check.xml");
    }
}
