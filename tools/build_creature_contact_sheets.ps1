param([string]$Frames='D:\CodexCache\creature-combat-qa\frames')
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $Frames).Path
if(-not $root.StartsWith('D:\',[StringComparison]::OrdinalIgnoreCase)){throw 'D: required'}
Add-Type -AssemblyName System.Drawing
$main=Get-Content -LiteralPath (Join-Path $root 'evidence.json') -Raw | ConvertFrom-Json
$rows=@($main.frames | Where-Object {$_.move -match '^(STALKER|SPITTER|BRUTE)'})
$extra=Join-Path $root 'extra-evidence.json'
if(Test-Path -LiteralPath $extra){$rows+=@((Get-Content -LiteralPath $extra -Raw | ConvertFrom-Json).frames)}
$font=[Drawing.Font]::new('Segoe UI',11,[Drawing.FontStyle]::Bold)
try{
 foreach($group in $rows | Group-Object {$_.move.Split('_')[0].ToLowerInvariant()}){
  $items=@($group.Group | Sort-Object move,age)
  $bitmap=[Drawing.Bitmap]::new(1280,[int]([Math]::Ceiling($items.Count/4.0)*238))
  $graphics=[Drawing.Graphics]::FromImage($bitmap)
  try{
   $graphics.Clear([Drawing.Color]::FromArgb(24,24,24))
   for($i=0;$i -lt $items.Count;$i++){
    $x=($i%4)*320;$y=[int][Math]::Floor($i/4)*238
    $image=[Drawing.Image]::FromFile((Join-Path $root $items[$i].image))
    try{$graphics.DrawImage($image,$x,$y+24,320,213)}finally{$image.Dispose()}
    $graphics.DrawString(($items[$i].image -replace '.png$',''),$font,[Drawing.Brushes]::White,$x+4,$y+2)
   }
   $bitmap.Save((Join-Path $root ('review-'+$group.Name+'.png')),[Drawing.Imaging.ImageFormat]::Png)
  }finally{$graphics.Dispose();$bitmap.Dispose()}
 }
}finally{$font.Dispose()}
"Built contact sheets from $($rows.Count) current manifest frames"
