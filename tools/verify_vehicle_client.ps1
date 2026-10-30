param([string]$Phase='green',[ValidateSet('neo','forge')][string]$Loader='neo')
$ErrorActionPreference='Stop'
$root=(Get-Location).Path
if(!(Test-Path -LiteralPath (Join-Path $root 'build'))){throw 'Run in the existing military6 worktree'}
$output=Join-Path $root ('build/military6-client-revision/'+$Phase)
[System.IO.Directory]::CreateDirectory((Join-Path $output 'classes'))|Out-Null
$tools=Join-Path (Split-Path (Split-Path $root -Parent) -Parent) '.blackwatch-tools'
$encoding=if($Loader -eq 'neo'){[System.Text.UTF8Encoding]::new($false)}else{[System.Text.Encoding]::Default}
$saved=[System.IO.File]::ReadAllLines((Join-Path $root 'build/military6-client-input/operator-green/javac.args'),$encoding)
$index=[Array]::IndexOf($saved,'"-cp"')
if($index -lt 0){throw 'Missing cached real-version javac classpath'}
$jars=@($saved[$index+1].Trim('"') -split ';')
$jars+=Join-Path $root 'src/main/resources'
$extra=Join-Path $tools $(if($Loader -eq 'neo'){'gradle/caches/neoformruntime/intermediate_results'}else{'gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1'})
if($Loader -eq 'forge'){$jars+=@([System.IO.Directory]::GetFiles($extra,'*extra*.jar',[System.IO.SearchOption]::AllDirectories))}
$sources=@('client/model/BlackwatchModel','client/model/MilitaryVehicleModel','client/model/BlacklightPlayerModel','client/renderer/BlacklightPlayerRenderer','client/model/MercerPlayerMesh','military/vehicle/VehicleDefinition','military/vehicle/VehicleCatalog',
    'client/VehicleHud','client/VehicleControls','client/renderer/StandalonePlayerRendering','client/renderer/MilitaryVehicleRenderer','client/camera/PrototypeChaseCamera','client/animation/BlacklightRenderPose','mixin/client/PrototypeChaseCameraMixin')|ForEach-Object {Join-Path $root ('src/main/java/com/codex/blacklight/'+$_+'.java')}
$sources+=@([System.IO.Directory]::GetFiles((Join-Path $root 'src/main/java/com/codex/blacklight/client/vehicle'),'*.java'))
foreach($name in @('VehiclePlayerPoseMixin')){
    $path=Join-Path $root ('src/main/java/com/codex/blacklight/mixin/client/'+$name+'.java')
    if(Test-Path -LiteralPath $path){$sources+=$path}
}
$sources+=Join-Path $root 'src/test/java/com/codex/blacklight/client/vehicle/VehiclePresentationTest.java'
$sources+=Join-Path $root 'src/test/java/com/codex/blacklight/client/vehicle/VehicleHudLayoutTest.java'
$sources+=Join-Path $root 'src/test/java/com/codex/blacklight/client/model/VehicleRiderPoseTest.java'
$smoke=Join-Path $root 'src/vehicleVisualTest/java/com/codex/blacklight/client/vehicle/VehicleViewSmoke.java'
if(Test-Path -LiteralPath $smoke){$sources+=$smoke}
$sources+=Join-Path $PSScriptRoot 'VehicleClientTestRunner.java'
if(!(Test-Path -LiteralPath (Join-Path $root 'build/classes/java/main/com/codex/blacklight/BlacklightNeoMod.class'))){
    # A failed root Gradle compilation may have emptied main classes. Build current
    # dependency sources with javac into this isolated evidence directory only.
    $sources+=@([System.IO.Directory]::GetFiles((Join-Path $root 'src/main/java'),'*.java',[System.IO.SearchOption]::AllDirectories))
    $sources=@($sources|Sort-Object -Unique)
    if($Loader -eq 'forge'){$jars+=Join-Path $tools 'gradle/caches/forge_gradle/deobf_dependencies/maven/modrinth/parcool/1.20.1-3.4.3.3_mapped_official_1.20.1/parcool-1.20.1-3.4.3.3_mapped_official_1.20.1.jar'}
}
$classes=Join-Path $output 'classes'
$jdk=Join-Path $tools $(if($Loader -eq 'neo'){'gradle/jdks/eclipse_adoptium-21-amd64-windows.2/bin'}else{'gradle/jdks/eclipse_adoptium-17-amd64-windows.2/bin'})
$quote={param($values) @($values|ForEach-Object {'"'+$_.Replace('\','/')+'"'})}
$compiler=@('-proc:none','-encoding','UTF-8','-Xmaxwarns','3','-cp',($jars -join ';'),'-d',$classes)+$sources
[System.IO.File]::WriteAllLines((Join-Path $output 'javac.args'),(& $quote $compiler),$encoding)
& python (Join-Path $PSScriptRoot 'vehicle_client_receipt.py') --root $root --output $output --loader $Loader --stage before
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
$ErrorActionPreference='Continue'
& (Join-Path $jdk 'javac.exe') ('@'+(Join-Path $output 'javac.args')) 2>&1|Tee-Object -FilePath (Join-Path $output 'compile.log')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
$runtime=@('-Djava.awt.headless=true',('-Dblacklight.test.vehiclePlayerVertices='+($output.Replace('\','/')+'/actual-player-vertices.json')),'-cp',($classes+';'+($jars -join ';')),'VehicleClientTestRunner',(Join-Path $output 'result.xml'),'com.codex.blacklight.client.vehicle.VehiclePresentationTest','com.codex.blacklight.client.model.VehicleRiderPoseTest','com.codex.blacklight.client.vehicle.VehicleHudLayoutTest')
[System.IO.File]::WriteAllLines((Join-Path $output 'java.args'),(& $quote $runtime),[System.Text.Encoding]::Default)
& (Join-Path $jdk 'java.exe') ('@'+(Join-Path $output 'java.args')) 2>&1|Tee-Object -FilePath (Join-Path $output 'run.log')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& python (Join-Path $PSScriptRoot 'vehicle_client_receipt.py') --root $root --output $output --loader $Loader --stage after
exit $LASTEXITCODE
