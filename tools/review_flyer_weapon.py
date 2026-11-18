"""Native weapon-session contact sheets; no generated gameplay imagery."""
import json,sys
from pathlib import Path
from PIL import Image,ImageDraw
root=Path(sys.argv[1]).resolve()
if root.drive.lower()!='d:':raise ValueError('D: only')
e=json.loads((root/'evidence.json').read_text())
for scene in sorted(set(f['scene'] for f in e['frames'])):
 frames=[f for f in e['frames'] if f['scene']==scene]
 rows=[min(frames,key=lambda f:abs(f['age']-t)) for t in [20,24,26,27,28,30,32,36,40,48,60,85]]
 sheet=Image.new('RGB',(1440,1392),'#111');draw=ImageDraw.Draw(sheet)
 for i,f in enumerate(rows):
  with Image.open(root/f['image']) as im:sheet.paste(im.resize((480,320)),((i%3)*480,(i//3)*348+28))
  mode='server' if f.get('serverBody') else 'legacy' if f['ragdoll'] else 'source'
  draw.text(((i%3)*480+3,(i//3)*348+4),f"{f['image']} hp={f['health']} body={mode}",fill='white')
 sheet.save(root/f'weapon-review-{scene}.jpg',quality=90)
 if scene==4:
  rows=[min(frames,key=lambda f:abs(f['age']-t)) for t in [33,36,39,48,52,55,60,70,80,85,90,95,97,100,110,130]]
  sheet=Image.new('RGB',(1920,1392),'#111');draw=ImageDraw.Draw(sheet)
  for i,f in enumerate(rows):
   with Image.open(root/f['image']) as im:sheet.paste(im.resize((480,320)),((i%4)*480,(i//4)*348+28))
   draw.text(((i%4)*480+3,(i//4)*348+4),f"{f['image']} minY={f.get('authoredSkin',{}).get('lowest',0):.4f} ground=81",fill='white')
  sheet.save(root/'heavy-recovery-review.jpg',quality=90)
print(len(e['frames']))
