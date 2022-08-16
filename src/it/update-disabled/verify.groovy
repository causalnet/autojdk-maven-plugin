import groovy.io.FileType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

Path jdksDirectory = Paths.get(userHome, '.m2', 'autojdk', 'jdks')
assert Files.isDirectory(jdksDirectory)

def foundJdkNames = []
jdksDirectory.toFile().eachFileMatch FileType.DIRECTORIES, ~/zulu-1[7-8].+/, { foundJdkNames << it.name }

//Java 17 downloaded and not 18 since updates disabled
assert foundJdkNames.size() == 1
