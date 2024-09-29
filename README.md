# autojdk-maven-plugin

The AutoJDK maven plugin allows the JDK to be treated similarly
to dependencies.  

- Automatically download and configure a project to use a specific version of a JDK 
- Each project (or even submodule) can use its own specific version of a JDK to build with
- Everything runs in Maven - no shell scripts, batch files, etc.
- When set-up as an extension, AutoJDK doesn't need any project modifications and can 
  auto-detect the version of Java needed for a project based on compiler settings

## How AutoJDK works

AutoJDK uses [Maven toolchains](https://maven.apache.org/guides/mini/guide-using-toolchains.html)
to configure a project with a specific JDK.  Toolchains allow a
different JDK to be used to actually build a project (compile, run tests, etc.) 
than the one being used to run Maven.

AutoJDK performs work at these different times in the build lifecycle:

### AutoJDK extension

For projects that don't configure AutoJDK explicitly, the 
AutoJDK extension can configure the project to use a suitable
JDK version.  

The extension will look at the following to determine the suitable JDK version:

- The system property `autojdk.jdk.version`, which can be set to a major version number or a Maven version range such as `[19, 20)`
- The source, target and release properties configured on the Maven Compiler plugin
- The jvmTarget property configured on the Kotlin plugin

If the extension can determine the Java version to use for the project it will inject the AutoJDK plugin and 
toolchain plugin appropriately.

### Preparation using AutoJDK

This runs first thing in the project build, and sets up the Maven toolchains.

- Checking if a local JDK suitable for building the project is available
- Downloading a suitable JDK using the
  [Foojay JDK discovery service](https://github.com/foojayio/discoapi)
  if there isn't a suitable one available locally
  (can be configured to use other sources, but Foojay is used by default)
- Registering the JDK with Maven toolchains so it can be used in other goals (compile, test, etc.)

### Toolchains

The standard [Maven Toolchains plugin](https://maven.apache.org/plugins/maven-toolchains-plugin/)
is then used to select that JDK that AutoJDK prepared.

### Compile/test/etc.

Standard Maven plugins/goals that support toolchains will work
with the JDK that AutoJDK prepared.

