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
to configure a project with a specific JDK that it downloads and manages.  Toolchains allow a
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

## Using as a Maven extension

Adding AutoJDK as a Maven extension allows it to automatically run
on any existing project.  AutoJDK will try to detect what version of Java
should be used with a project.  Some places this is resolved from:

- The `source`, `target` and `release` properties configured for the
  [Maven compiler plugin](https://maven.apache.org/plugins/maven-compiler-plugin/)
  on the project.  This works no matter how these are configured
  (for example a default from an ancestor POM, such as from Spring Boot).
  This is usually configured somehow for every Java-based Maven project.
- The `jvmTarget` from the configuration of the 
  [Kotlin Maven plugin](https://kotlinlang.org/docs/maven.html) 
  on the project, if it is a Kotlin project.

AutoJDK does not need to be used as an extension and can be used as a plugin only,
but using it as an extension allows it to be used on any project without changing
each project's configuration.

### Setup as an extension

The extension needs to be downloaded and registered with Maven as an extension.

#### Downloading
This can be done easily through Maven itself, downloading the extension Maven Central to your local repository with:

```
mvn dependency:get -Dartifact=au.net.causal.maven.plugins:autojdk-maven-plugin:1.0
```

#### Registering the extension
The easiest and least invasive way of registering the extension is modifying the MAVEN_OPTS environment variable to contain:

```
-Dmaven.ext.class.path=<your m2 directory>/repository/au/net/causal/maven/plugins/autojdk-maven-plugin/1.0/autojdk-maven-plugin-1.0.jar
```

If you already have `maven.ext.class.path` set up in `MAVEN_OPTS`, add this extension to the end with your platform's 
path separator (';' on Windows, ':' on Mac/Linux).

Alternatively, you can copy the extension's JAR file into your Maven installation's lib/ext directory, 
but this installs it globally for all users.

### Usage as an extension

Once set up as a Maven extension, running any Maven build on a 
Java project should execute AutoJDK automatically.  On the first run,
a JDK version suitable for your project will be downloaded and installed
to an `autojdk` subdirectory of Maven's `.m2` directory in 
your user home.

The compile and test goals will use this JDK:

```
[INFO] --- compiler:3.10.1:compile (default-compile) @ my-project ---
[INFO] Toolchain in maven-compiler-plugin: JDK[/home/auser/.m2/autojdk/jdks/zulu-11.0.25-9-linux-x64]
[INFO] ...

```

## Using as a plugin only

When using AutoJDK as a plugin and not an extension, it must be 
configured explicitly along with the toolchains plugin in a project's POM
in the `plugins` section.

For example:

```
<plugin>
    <groupId>au.net.causal.maven.plugins</groupId>
    <artifactId>autojdk-maven-plugin</artifactId>
    <version>1.0</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare</goal>
            </goals>
        </execution>
    </executions>
</plugin>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-toolchains-plugin</artifactId>
    <version>3.0.0</version>
    <executions>
        <execution>
            <goals>
                <goal>toolchain</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <toolchains>
            <jdk>
                <version>21</version>
            </jdk>
        </toolchains>
    </configuration>
</plugin>
```

Configure toolchains with the version of the JDK you want to use.
The AutoJDK plugin will read this when running its prepare
goal to download a version of this JDK.

## Configuration

The XML configuration file `autojdk-configuration.xml` in the
`.m2/autojdk` directory can be configured with options.  If this
file does not exist, defaults will be used.

Typically, this is only needed for advanced usage.

### Proxy configuration

[Maven's own proxy settings](https://maven.apache.org/guides/mini/guide-proxies.html)
are used for downloading JDKs.

### Excluding JDK versions from being selected by the extension

When the AutoJDK extension runs, it will attempt to match the project's
desired Java version to a JDK version.  By default, this just matches
by major version number - so if a project needs Java 11, AutoJDK will attempt to 
download a version of JDK 11 for your platform.  However, there may be 
cases where this is not desired, for example:

- Prevent very old JDKs from being used since they are no longer have security updates
- Running on a platform that does not have any available old versions of the
  JDK (such as Linux/RISC-V, which only has Java 19 or later)

To configure this, use `extension-exclusion`s in the configuration file
using the [Maven version range syntax](https://cwiki.apache.org/confluence/display/MAVENOLD/Dependency+Mediation+and+Conflict+Resolution#DependencyMediationandConflictResolution-DependencyVersionRanges).

For example:

```
<?xml version="1.0" encoding="UTF-8"?>
<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>
    <extension-exclusions>
        <extension-exclusion>
            <!-- Anything under JDK 11, use JDK 11 -->
            <!-- Default does the same but with JDK 8 -->
            <version>(,11)</version>
            <substitution>[11,12)</substitution>
        </extension-exclusion>
    </extension-exclusions>
</autojdk-configuration>
```

In this example, an extension exclusion's matching `version` 
range `(,11)` (which matches any JDK version lower than 11)
will instead match on a JDK version 11 (specified in this 
example by the range `[11, 12)`).  

In practice, this configuration would mean that for a project that specified
Java 6 (through compiler configuration), AutoJDK would 
download/install/use JDK 11.

### Using specific JDK vendors

By default, AutoJDK has a preferred order of JDK vendors.
For example, a [Zulu OpenJDK from Azul](https://www.azul.com/downloads/#zulu)
is high on the preference list and will be used if available rather than an Oracle JDK.
This preference can be overridden if desired.

For example, to use [Eclipse Temurin](https://adoptium.net/temurin/releases/) OpenJDKs by default if available before 
falling back to any other JDK:

```
<?xml version="1.0" encoding="UTF-8"?>
<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>
    <vendors>
        <vendor>termurin</vendor>
        <vendor>*</vendor>
    </vendors>
</autojdk-configuration>
```

The `*` vendor matches any other available vendor for your platform.
If, in your overridden vendor list, the `*` vendor is not present,
only JDKs from the vendors explicitly specified in your list will be 
used.

### JDK update policy

By default, when AutoJDK is executed and needs to set up a JDK,
it will check remote repositories at most once-per-day as part of 
its execution.  This update policy can be reconfigured with:

```
<?xml version="1.0" encoding="UTF-8"?>
<autojdk-configuration xmlns='https://autojdk.causal.net.au/configuration/1.0'>
    <jdk-update-policy>
        <every>P2DT1H</every>
    </jdk-update-policy>
</autojdk-configuration>
```

Under `jdk-update-policy` these options are available:

- `<every>duration</every>` where duration is in ISO-8601 duration format.
  For example `P7D` for once per week.
- `<always/>` will always check every time.  
  Even if a matching JDK version already is installed, AutoJDK will
  check every time.
- `<never/>` will never check.  AutoJDK will always use an existing
  installed matching JDK if available.

The update policy only affects _matching_ JDK versions, so if a project
requires Java version `[11, 12)` and you only have JDK 17 and 
JDK 21 installed, AutoJDK will still attempt to download even with 
a `never` policy, once.

### Custom repositories

By default, AutoJDK downloads JDKs using the
[Foojay JDK discovery service](https://github.com/foojayio/discoapi).  However it is possible to 
download JDKs from custom remote Maven repositories instead.
This may be desirable in a corporate locked-down environment for
example.

