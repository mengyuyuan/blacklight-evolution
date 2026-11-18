param(
    [Parameter(Mandatory=$true)][string]$Jar,
    [Parameter(Mandatory=$true)][string]$Distribution,
    [Parameter(Mandatory=$true)][string]$Report
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath = (Resolve-Path -LiteralPath $Jar).Path
$zipPath = (Resolve-Path -LiteralPath $Distribution).Path
$reportPath = [IO.Path]::GetFullPath($Report)
if (-not $reportPath.StartsWith('D:\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Reports must remain on D:' }
function Read-Entry($Archive, [string]$Name) {
    $entry = $Archive.GetEntry($Name)
    if ($null -eq $entry) { throw "Missing archive entry: $Name" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}
$archive = [IO.Compression.ZipFile]::OpenRead($jarPath)
try {
    $metadata = Read-Entry $archive 'META-INF/mods.toml'
    foreach ($value in @('modId="blacklight"', 'version="1.0.1-dev.1"', 'versionRange="[1.20.1]"', 'versionRange="[47.4.10,)"', 'versionRange="[4.8.4,5)"')) {
        if (-not $metadata.Contains($value)) { throw "Wrong metadata: $value" }
    }
    $required = @(
        'com/codex/blacklight/client/renderer/AdaptiveRagdoll.class',
        'com/codex/blacklight/client/renderer/ConsumeBladeContact.class',
        'com/codex/blacklight/client/renderer/InfectedSkinLayout.class',
        'com/codex/blacklight/client/vehicle/VehicleAudio.class',
        'com/codex/blacklight/client/vehicle/VehicleDentMesh.class',
        'com/codex/blacklight/mixin/HeavySlowMotionServerMixin.class',
        'com/codex/blacklight/mixin/client/HeavySlowMotionTimerMixin.class',
        'com/codex/blacklight/power/TheBoysBodyCompatibility.class',
        'assets/blacklight/textures/entity/hive_mc.png',
        'assets/blacklight/textures/item/living_hive_tissue.png',
        'data/blacklight/tags/entity_types/ragdoll_disabled.json'
    )
    foreach ($path in $required) { if ($null -eq $archive.GetEntry($path)) { throw "Missing feature: $path" } }
    $unexpected = @($archive.Entries | Where-Object {
        $_.FullName -match '(?i)(silentqa|silent-qa|visualqa|visual-qa|RagdollVisualAudit|VehicleFeedbackVisual|HeavySlowMotionAudit|GameTest|HydraReleaseSmoke|CreatureCombatSmoke|Standalone[^/]*Smoke|WingFlightSmoke|WingSupersonicSmoke|InfectedCompositionSmoke|FeedbackRenderSmoke|/private/|blackwatch_arena\.nbt|data/blacklight/tags/entity_type/)'
    } | ForEach-Object FullName)
    if ($unexpected.Count) { throw "Test-only or incompatible entries: $($unexpected -join ', ')" }
    $config = (Read-Entry $archive 'blacklight.mixins.json') | ConvertFrom-Json
    foreach ($name in @($config.mixins) + @($config.client)) {
        $path = ($config.package + '.' + $name).Replace('.', '/') + '.class'
        if ($null -eq $archive.GetEntry($path)) { throw "Missing configured mixin: $path" }
    }
    $refmap = (Read-Entry $archive 'blacklight.refmap.json') | ConvertFrom-Json
    if (@($refmap.mappings.PSObject.Properties).Count -eq 0 -or @($refmap.data.searge.PSObject.Properties).Count -eq 0) { throw 'Empty runtime refmap' }
    $nested = (Read-Entry $archive 'META-INF/jarjar/metadata.json') | ConvertFrom-Json
    $mixinExtras = @($nested.jars | Where-Object { $_.identifier.artifact -eq 'mixinextras-forge' })
    if ($mixinExtras.Count -ne 1 -or $null -eq $archive.GetEntry($mixinExtras[0].path)) { throw 'Missing MixinExtras runtime' }
    $classCount = 0
    foreach ($entry in $archive.Entries) {
        if (-not $entry.FullName.EndsWith('.class')) { continue }
        $stream = $entry.Open()
        try {
            $header = [byte[]]::new(8)
            $stream.ReadExactly($header, 0, 8)
            $major = ([int]$header[6] * 256) + $header[7]
            if ($major -gt 61) { throw "Not Java 17: $($entry.FullName), major $major" }
            $classCount++
        } finally { $stream.Dispose() }
    }
    $audio = @($archive.Entries | Where-Object { $_.FullName -like 'assets/blacklight/sounds/vehicles/*.ogg' })
    if ($audio.Count -ne 10) { throw "Expected 10 vehicle audio files, got $($audio.Count)" }
    $entries = $archive.Entries.Count
} finally { $archive.Dispose() }
$jarHash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
$zipArchive = [IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    $mods = @($zipArchive.Entries | Where-Object { $_.FullName -like 'mods/*.jar' })
    if ($mods.Count -ne 1) { throw 'Distribution must contain exactly one installable mod JAR' }
    $stream = $mods[0].Open()
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $packedHash = [Convert]::ToHexString($sha.ComputeHash($stream)).ToLowerInvariant() }
    finally { $stream.Dispose(); $sha.Dispose() }
    if ($packedHash -ne $jarHash) { throw 'Distribution contains a different JAR' }
    foreach ($path in @('README.md', 'docs/FORGE_1_20_1_RAGDOLL_VEHICLES_ZH.md')) {
        if ($null -eq $zipArchive.GetEntry($path)) { throw "Missing installation documentation: $path" }
    }
} finally { $zipArchive.Dispose() }
$result = [ordered]@{
    passed = $true; jar = $jarPath; distribution = $zipPath
    jarBytes = (Get-Item -LiteralPath $jarPath).Length; jarSha256 = $jarHash
    distributionBytes = (Get-Item -LiteralPath $zipPath).Length
    distributionSha256 = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
    classes = $classCount; maxClassVersion = 61; entries = $entries
    vehicleAudioFiles = $audio.Count; testOnlyEntries = 0; configuredMixinsPresent = $true
    runtimeRefmapPresent = $true; mixinExtrasBundled = $true; distributionJarMatches = $true
}
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding utf8
$result | ConvertTo-Json -Depth 8
