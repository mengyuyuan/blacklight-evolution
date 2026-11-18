param([string]$Frames='D:\CodexCache\encounter-qa\frames')
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $Frames).Path
if(-not $root.StartsWith('D:\',[StringComparison]::OrdinalIgnoreCase)){throw 'D: required'}
Add-Type -AssemblyName System.Drawing
$manifest=Get-Content -LiteralPath (Join-Path $root 'evidence.json') -Raw | ConvertFrom-Json
$font=[Drawing.Font]::new('Segoe UI',11,[Drawing.FontStyle]::Bold)
try{
 foreach($group in $manifest.frames | Group-Object { $_.scene -replace '_(none|claw|double|pounce|hammer|slam|charge|howl|orbs|volley|dive|blade|whip|eruption|dodge|baton|grenade|guard)_p\d+$','' }){
  $items=@(foreach($scene in $group.Group | Group-Object scene){
   $all=@($scene.Group | Sort-Object age)
   if($scene.Name -match '_none_|^radio$|^uav$'){$all}else{$all[0];$all[[int][Math]::Floor($all.Count/2)];$all[-1]}
  })
  $bitmap=[Drawing.Bitmap]::new(1200,[int]([Math]::Ceiling($items.Count/3.0)*291))
  $g=[Drawing.Graphics]::FromImage($bitmap)
  try{
   $g.Clear([Drawing.Color]::FromArgb(24,24,24))
   for($i=0;$i -lt $items.Count;$i++){
    $x=($i%3)*400;$y=[int][Math]::Floor($i/3)*291
    $im=[Drawing.Image]::FromFile((Join-Path $root $items[$i].image))
    try{$g.DrawImage($im,$x,$y+24,400,267)}finally{$im.Dispose()}
    $g.DrawString(($items[$i].image -replace '.png$',''),$font,[Drawing.Brushes]::White,$x+3,$y+2)
   }
   $bitmap.Save((Join-Path $root ('review-'+$group.Name+'.png')),[Drawing.Imaging.ImageFormat]::Png)
  }finally{$g.Dispose();$bitmap.Dispose()}
 }
}finally{$font.Dispose()}
"Built sheets from $($manifest.scenes) scenes, $($manifest.frames.Count) manifest frames"
