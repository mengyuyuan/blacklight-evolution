$ErrorActionPreference='Stop'
$root='D:\CodexCache\encounter-qa\model-gallery'
$frames=Get-Content -LiteralPath (Join-Path $root 'evidence.json') -Raw|ConvertFrom-Json
if($frames.Count -ne 45){throw 'Expected 15 models and three views each'}
Add-Type -AssemblyName System.Drawing
$groups=[ordered]@{monsters=@('leader_hunter','juggernaut','goliath','flyer','elizabeth_greene');characters=@('alex_mercer','roland','archer','galloway','koenig','captain_cross','super_soldier');references=@('brawler','supreme_hunter_reference','recon_uav')}
$font=[Drawing.Font]::new('Segoe UI',11,[Drawing.FontStyle]::Bold)
try{foreach($name in $groups.Keys){$items=@(foreach($id in $groups[$name]){$frames|Where-Object model -eq $id});$bitmap=[Drawing.Bitmap]::new(1200,$groups[$name].Count*291);$g=[Drawing.Graphics]::FromImage($bitmap)
 try{$g.Clear([Drawing.Color]::FromArgb(24,24,24));for($i=0;$i -lt $items.Count;$i++){$x=($i%3)*400;$y=[int][Math]::Floor($i/3)*291;$im=[Drawing.Image]::FromFile((Join-Path $root $items[$i].image));try{$g.DrawImage($im,$x,$y+24,400,267)}finally{$im.Dispose()};$g.DrawString(($items[$i].model+' '+$items[$i].view),$font,[Drawing.Brushes]::White,$x+3,$y+2)};$bitmap.Save((Join-Path $root ('review-'+$name+'.png')),[Drawing.Imaging.ImageFormat]::Png)}finally{$g.Dispose();$bitmap.Dispose()}
}}finally{$font.Dispose()}
'Built three review sheets from 45 real client screenshots'
