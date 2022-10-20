package au.net.causal.maven.plugins.autojdk;

import java.nio.file.Path;
import java.util.Objects;

public class JdkArchive<A extends JdkArtifact>
{
    private final A artifact;
    private final Path file;

    public JdkArchive(A artifact, Path file)
    {
        this.artifact = Objects.requireNonNull(artifact);
        this.file = Objects.requireNonNull(file);
    }

    public A getArtifact()
    {
        return artifact;
    }

    public Path getFile()
    {
        return file;
    }

    @Override
    public String toString() {
        return "JdkArchive{" +
                "artifact=" + artifact +
                ", file=" + file +
                '}';
    }
}
