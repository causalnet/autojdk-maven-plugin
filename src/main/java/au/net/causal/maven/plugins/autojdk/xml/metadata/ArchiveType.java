package au.net.causal.maven.plugins.autojdk.xml.metadata;

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

    public static ArchiveType forFileExtension(String fileExtension)
    {
        for (ArchiveType archiveType : values())
        {
            if (archiveType.getFileExtension().equals(fileExtension))
                return archiveType;
        }

        return null;
    }
}
