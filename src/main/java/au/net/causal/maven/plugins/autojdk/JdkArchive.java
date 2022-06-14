package au.net.causal.maven.plugins.autojdk;

import java.io.File;
import java.util.Objects;

public class JdkArchive
{
    private final JdkArtifact artifact;
    private final File file;

    public JdkArchive(JdkArtifact artifact, File file)
    {
        this.artifact = Objects.requireNonNull(artifact);
        this.file = Objects.requireNonNull(file);
    }

    public JdkArtifact getArtifact()
    {
        return artifact;
    }

    public File getFile()
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
