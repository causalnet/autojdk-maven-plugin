import groovy.io.FileType
import java.nio.file.Files
import java.nio.file.Path

//Verify that the local repository does not contain either archive or metadata for the fake JDK we installed
Path fakeJdkGroupDir = localRepositoryPath.toPath().resolve('au/net/causal/autojdk/jdk/galah-vendor')
assert Files.isDirectory(fakeJdkGroupDir)
Path fakeJdkVersionDir = fakeJdkGroupDir.resolve('17.0.2')
assert Files.isDirectory(fakeJdkVersionDir)

def foundFiles = []
fakeJdkVersionDir.toFile().eachFileMatch FileType.FILES, ~/.*.zip/, { foundFiles << it.name }
fakeJdkVersionDir.toFile().eachFileMatch FileType.FILES, ~/.*.xml/, { foundFiles << it.name }
assert foundFiles.isEmpty()
