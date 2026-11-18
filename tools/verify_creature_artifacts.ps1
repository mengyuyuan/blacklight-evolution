param([ValidateSet('neo','forge')][string]$Platform)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root=if($Platform -eq 'neo'){'D:\blacklight-evolution'}else{'D:\blacklight-evolution-forge-1.20.1'}
$version=if($Platform -eq 'neo'){'MC1.21.1-NeoForge-1.0.0'}else{'MC1.20.1-Forge-1.0.1-dev.1'}
$name="Blacklight-Ascension-$version-bundled"
$jar=Join-Path $root "build\libs\$name.jar"
$zip=Join-Path $root "build\distributions\$name.zip"
$report="D:\CodexCache\creature-combat-qa\$Platform-artifact.json"
function Entry-Text($archive,[string]$name){
 $entry=$archive.GetEntry($name);if($null -eq $entry){throw "Missing entry: $name"}
 $reader=[IO.StreamReader]::new($entry.Open());try{$reader.ReadToEnd()}finally{$reader.Dispose()}
}
function Entry-Hash($entry){
 $stream=$entry.Open();$sha=[Security.Cryptography.SHA256]::Create()
 try{[Convert]::ToHexString($sha.ComputeHash($stream)).ToLowerInvariant()}finally{$stream.Dispose();$sha.Dispose()}
}
$archive=[IO.Compression.ZipFile]::OpenRead($jar)
try{
 $metadata=Entry-Text $archive $(if($Platform -eq 'neo'){'META-INF/neoforge.mods.toml'}else{'META-INF/mods.toml'})
 $mc=if($Platform -eq 'neo'){'1.21.1'}else{'1.20.1'}
 if(-not $metadata.Contains("versionRange=`"[$mc]`"")){throw 'Wrong Minecraft version'}
 foreach($class in @('entity/BlacklightMutant','entity/BlacklightCommonInfected','entity/BlacklightHive','entity/BlacklightHydra','combat/MutantMove','combat/CreaturePoseTable','combat/BlockThrowBallistics','client/vehicle/VehicleAudio','client/renderer/AdaptiveRagdoll')){
  if($null -eq $archive.GetEntry("com/codex/blacklight/$class.class")){throw "Missing feature: $class"}
 }
 $unexpected=@($archive.Entries | Where-Object {$_.FullName -match '(?i)(silentqa|silent-qa|CreatureSkillsVisual|GameTest|RagdollVisualAudit|VehicleFeedbackVisual|/private/|blackwatch_arena.nbt)'})
 if($unexpected.Count){throw "Test/private entries leaked: $($unexpected.FullName -join ', ')"}
 $resources=0
 foreach($species in @('stalker','spitter','brute')){
  foreach($resource in @("models/${species}_combat_motion.json","animations/blacklight_${species}.animation.json")){
   $path="assets/blacklight/$resource";$entry=$archive.GetEntry($path)
   if($null -eq $entry){throw "Missing motion: $path"}
   if((Entry-Hash $entry) -ne (Get-FileHash -LiteralPath (Join-Path $root "src/main/resources/$path") -Algorithm SHA256).Hash.ToLowerInvariant()){throw "Stale packaged motion: $path"}
   $resources++
  }
 }
 $mixins=(Entry-Text $archive 'blacklight.mixins.json') | ConvertFrom-Json
 foreach($mixin in @($mixins.mixins)+@($mixins.client)){
  if($null -eq $archive.GetEntry(($mixins.package+'.'+$mixin).Replace('.','/')+'.class')){throw "Missing mixin: $mixin"}
 }
 $major=if($Platform -eq 'neo'){65}else{61};$classes=0
 foreach($entry in $archive.Entries){if($entry.FullName.EndsWith('.class')){
  $stream=$entry.Open();try{$header=[byte[]]::new(8);$stream.ReadExactly($header,0,8);if((256*[int]$header[6]+[int]$header[7]) -gt $major){throw "Wrong Java class version: $($entry.FullName)"};$classes++}finally{$stream.Dispose()}
 }}
}finally{$archive.Dispose()}
$hash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
$archive=[IO.Compression.ZipFile]::OpenRead($zip)
try{
 $packed=@($archive.Entries | Where-Object {$_.FullName -like 'mods/*.jar'})
 if($packed.Count -ne 1 -or (Entry-Hash $packed[0]) -ne $hash){throw 'Distribution JAR differs'}
 if($null -eq $archive.GetEntry('docs/CREATURE_ACTIONS_2026_11_ZH.md')){throw 'Missing creature action documentation'}
}finally{$archive.Dispose()}
$suites=@{}
foreach($file in Get-ChildItem -LiteralPath (Join-Path $root 'build/test-results/test') -Filter 'TEST-*.xml'){
 [xml]$xml=Get-Content -LiteralPath $file.FullName;$suites[$xml.testsuite.name]=$xml.testsuite
}
$supplemental=0
if($Platform -eq 'neo'){
 foreach($file in Get-ChildItem -LiteralPath (Join-Path $root 'build/test-results/creature-private') -Filter 'TEST-*.xml'){
  [xml]$xml=Get-Content -LiteralPath $file.FullName
  if([int]$xml.testsuite.failures -or [int]$xml.testsuite.errors -or [int]$xml.testsuite.skipped){throw 'Private-source retest did not pass'}
  $suites[$xml.testsuite.name]=$xml.testsuite;$supplemental+=[int]$xml.testsuite.tests
 }
}
$tests=[ordered]@{tests=0;failures=0;errors=0;skipped=0}
foreach($suite in $suites.Values){foreach($key in @('tests','failures','errors','skipped')){$tests[$key]+=[int]$suite.$key}}
if($tests.tests -lt 1200 -or $tests.failures -or $tests.errors -or $tests.skipped){throw "Incomplete tests: $($tests | ConvertTo-Json -Compress)"}
$build=Get-Content -LiteralPath "D:\CodexCache\creature-combat-$Platform-package.log" -Raw
if(-not $build.Contains('BUILD SUCCESSFUL') -or -not $build.Contains('VERIFIED exactBundledOriginals=260')){throw 'Build/motion acceptance missing'}
$server=Get-Content -LiteralPath "D:\CodexCache\creature-combat-$Platform-server.log" -Raw
if(-not $server.Contains('All 19 required tests passed')){throw 'Current server acceptance missing'}
$result=[ordered]@{passed=$true;platform=$Platform;unitTests=$tests;supplementalPrivateSourceTests=$supplemental;gameTests=19;jar=$jar;jarSha256=$hash;jarBytes=(Get-Item -LiteralPath $jar).Length;distribution=$zip;distributionSha256=(Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant();motionResourcesMatched=$resources;classCount=$classes;maxClassVersion=$major;qaEntries=0}
$result | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $report -Encoding utf8
$result | ConvertTo-Json -Depth 7
