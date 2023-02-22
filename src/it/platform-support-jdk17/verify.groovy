File buildLog = new File(basedir, 'build.log')
assert buildLog.exists()
assert buildLog.size() > 0

//Parse results
def matcher = buildLog.text =~ "(?m)(?s)^\\[INFO] --- (${pluginArtifactId}|${pluginArtifactId.replaceAll('-maven-plugin', '')}):${pluginVersion}:platform-support.*?\\R(.*?)\\[INFO] -----------------"
assert matcher.find()
def lines = matcher.group(2).readLines().collect { it.takeAfter('[INFO]').trim() }
def platformMap = lines.collectEntries { [it.takeBefore(' - '), it.takeAfter(' - ').split(', ')] }

//Make sure we have some results for common platforms
assert ('windows-x64' in platformMap)
assert ('macos-x64' in platformMap)
assert ('linux-x64' in platformMap)
assert ('linux-aarch64' in platformMap)

//Verify some well-known vendors are present in the results
assert platformMap['linux-x64'].any{it ==~ /zulu\(.*/}
assert platformMap['linux-x64'].any{it ==~ /liberica\(.*/}
assert platformMap['linux-x64'].any{it ==~ /corretto\(.*/}
