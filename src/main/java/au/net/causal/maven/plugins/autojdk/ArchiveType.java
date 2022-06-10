package au.net.causal.maven.plugins.autojdk;

public enum ArchiveType
{
    ZIP("zip"),
    TAR_GZ("tar.gz");

    private final String fileExtension;

    private ArchiveType(String fileExtension)
    {
        this.fileExtension = fileExtension;
    }

    public String getFileExtension()
    {
        return fileExtension;
    }
}
