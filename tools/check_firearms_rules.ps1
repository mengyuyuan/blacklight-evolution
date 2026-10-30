$ErrorActionPreference = 'Stop'
$taskJdk = 'F:/MC建筑/.blackwatch-tools/gradle/jdks/eclipse_adoptium-21-amd64-windows.2'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskOut = Join-Path $taskRoot 'build/firearms-rules'
New-Item -ItemType Directory -Force -Path $taskOut | Out-Null
& "$taskJdk/bin/javac.exe" -encoding UTF-8 --release 17 -d $taskOut "$taskRoot/src/main/java/com/codex/blacklight/firearms/WeaponDefinition.java" "$taskRoot/src/main/java/com/codex/blacklight/firearms/AmmoState.java" "$taskRoot/src/firearmsTest/java/com/codex/blacklight/firearms/FirearmsRulesChecks.java"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$taskJdk/bin/java.exe" -cp $taskOut com.codex.blacklight.firearms.FirearmsRulesChecks
exit $LASTEXITCODE
