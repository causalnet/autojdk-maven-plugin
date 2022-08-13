import groovy.io.FileType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

//Verify that the JDKs directory does not include the one that should have been purged
Path jdksDirectory = Paths.get(userHome, '.m2', 'autojdk', 'jdks')
assert Files.isDirectory(jdksDirectory)

def foundJdkNames = []
jdksDirectory.toFile().eachFileMatch FileType.DIRECTORIES, ~/zulu-17.+/, { foundJdkNames << it.name }
assert foundJdkNames.isEmpty()
