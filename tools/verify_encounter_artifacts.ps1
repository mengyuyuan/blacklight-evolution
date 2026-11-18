param([ValidateSet('neo','forge')][string]$Platform)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root=if($Platform -eq 'neo'){'D:\blacklight-evolution'}else{'D:\blacklight-evolution-forge-1.20.1'}
$version=if($Platform -eq 'neo'){'MC1.21.1-NeoForge-1.0.0'}else{'MC1.20.1-Forge-1.0.1-dev.1'}
$name="Blacklight-Ascension-$version-bundled"
$jar=Join-Path $root "build\libs\$name.jar"
$zip=Join-Path $root "build\distributions\$name.zip"
$report="D:\CodexCache\encounter-qa\$Platform-artifact.json"
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
 if($null -eq $archive.GetEntry('com/codex/blacklight/client/model/EncounterSourceMotion.class')){throw 'Missing source-motion adaptation'}
 if($null -eq $archive.GetEntry('com/codex/blacklight/client/model/EncounterBrawlerMotion.class')){throw 'Missing existing Brawler motion binding'}
 $mc=if($Platform -eq 'neo'){'1.21.1'}else{'1.20.1'}
 if(-not $metadata.Contains("versionRange=`"[$mc]`"")){throw 'Wrong Minecraft version'}
 foreach($class in @('entity/BlacklightMutant','entity/BlacklightCommonInfected','entity/BlacklightHive','entity/BlacklightHydra','combat/MutantMove','combat/CreaturePoseTable','combat/BlockThrowBallistics','client/vehicle/VehicleAudio','client/renderer/AdaptiveRagdoll','entity/EncounterMob','entity/EncounterKind','entity/EncounterPart','entity/EncounterProjectile','entity/MilitaryUav','registry/EncounterEntities','military/MilitaryTactics','combat/EncounterMove','client/model/EncounterPose')){
  if($null -eq $archive.GetEntry("com/codex/blacklight/$class.class")){throw "Missing feature: $class"}
 }
 $unexpected=@($archive.Entries | Where-Object {$_.FullName -match '(?i)(silentqa|silent-qa|EncounterVisual|EncounterModelGallery|CreatureSkillsVisual|GameTest|RagdollVisualAudit|VehicleFeedbackVisual|/private/|blackwatch_arena.nbt)'})
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
 $manifest=Get-Content -LiteralPath (Join-Path $root 'art/encounters/manifest.json') -Raw | ConvertFrom-Json
 foreach($alias in $manifest.resourceAliases){
  $target=$archive.GetEntry('assets/blacklight/'+$alias.target);$source=$archive.GetEntry('assets/blacklight/'+$alias.source)
  if($null -eq $target -or $null -eq $source -or (Entry-Hash $target) -ne (Entry-Hash $source) -or (Entry-Hash $source) -ne $alias.sha256){throw "Existing model replacement detected: $($alias.target)"}
 }
 if($manifest.entities.Count -ne 14){throw 'Incomplete encounter manifest'}
 foreach($entity in $manifest.entities){
  $id=$entity.id
  foreach($resource in @("geo/${id}_mc.geo.json","textures/entity/${id}_mc.png","animations/${id}.animation.json","models/item/${id}_spawn_egg.json")){
   $path="assets/blacklight/$resource";$entry=$archive.GetEntry($path)
   if($null -eq $entry -or (Entry-Hash $entry) -ne (Get-FileHash -LiteralPath (Join-Path $root "src/main/resources/$path") -Algorithm SHA256).Hash.ToLowerInvariant()){throw "Missing or stale encounter resource $path"}
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
 $doc=$archive.GetEntry('docs/ENCOUNTERS_AND_RECON_ZH.md')
 if($null -eq $doc -or (Entry-Hash $doc) -ne (Get-FileHash -LiteralPath (Join-Path $root 'docs/ENCOUNTERS_AND_RECON_ZH.md') -Algorithm SHA256).Hash.ToLowerInvariant()){throw 'Missing or stale creature action documentation'}
}finally{$archive.Dispose()}
$suites=@{}
foreach($file in Get-ChildItem -LiteralPath (Join-Path $root 'build/test-results/test') -Filter 'TEST-*.xml'){
 [xml]$xml=Get-Content -LiteralPath $file.FullName;$suites[$xml.testsuite.name]=$xml.testsuite
}
$tests=[ordered]@{tests=0;failures=0;errors=0;skipped=0}
foreach($suite in $suites.Values){foreach($key in @('tests','failures','errors','skipped')){$tests[$key]+=[int]$suite.$key}}
if($tests.tests -lt 1200 -or $tests.failures -or $tests.errors -or $tests.skipped){throw "Incomplete tests: $($tests | ConvertTo-Json -Compress)"}
$build=Get-Content -LiteralPath "D:\CodexCache\creature-combat-$Platform-package.log" -Raw
if(-not $build.Contains('BUILD SUCCESSFUL') -or -not $build.Contains('VERIFIED exactBundledOriginals=260')){throw 'Build/motion acceptance missing'}
$serverCounts=[ordered]@{}
foreach($stage in @('encounterServer','server','militaryServer')){
 $log=Get-Content -LiteralPath "D:\CodexCache\creature-combat-$Platform-$stage.log" -Raw
 if($log -notmatch 'All (\d+) required tests passed'){throw "Missing server acceptance $stage"}
 $serverCounts[$stage]=[int]$Matches[1]
}
if($serverCounts.encounterServer -ne 25 -or $serverCounts.server -ne 19 -or $serverCounts.militaryServer -lt 18){throw 'Server test coverage incomplete'}
$result=[ordered]@{passed=$true;platform=$Platform;unitTests=$tests;gameTests=$serverCounts;jar=$jar;jarSha256=$hash;jarBytes=(Get-Item -LiteralPath $jar).Length;distribution=$zip;distributionSha256=(Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant();motionResourcesMatched=$resources;classCount=$classes;maxClassVersion=$major;qaEntries=0}
$result | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $report -Encoding utf8
$result | ConvertTo-Json -Depth 7
