import groovy.io.FileType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.io.File

//Find our mock JDK that should have been downloaded from the proxy server
Path jdksDirectory = Paths.get(userHome, '.m2', 'autojdk', 'jdks')
assert Files.isDirectory(jdksDirectory)

def foundGalahJdks = []
jdksDirectory.toFile().eachFileMatch FileType.DIRECTORIES, ~/galah-vendor-17.+/, { foundGalahJdks << it }
assert foundGalahJdks.size() == 1
File foundJdkDirectory = foundGalahJdks[0]

//The mock JDK has one file, verify it was extracted and exists
File markerFile = new File(foundJdkDirectory, 'jdk-autojdk-marker.txt')
assert 'This is an AutoJDK test marker file for a fake JDK' in markerFile.readLines()
