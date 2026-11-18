"""Review contact sheets from native screenshots; no synthetic game imagery."""
import json,sys
from pathlib import Path
from PIL import Image,ImageDraw
root=Path(sys.argv[1]).resolve()
if root.drive.lower()!='d:':raise ValueError('D: only')
e=json.loads((root/'evidence.json').read_text())
for scene in sorted(set(f['scene'] for f in e['frames'])):
 frames=[f for f in e['frames'] if f['scene']==scene]
 rows=[min(frames,key=lambda f:abs(f['age']-t)) for t in [0,8,14,20,26,29,31,33,34,35,38,45,55,65,80,100]]
 sheet=Image.new('RGB',(1600,1280),'#111');draw=ImageDraw.Draw(sheet)
 for i,f in enumerate(rows):
  with Image.open(root/f['image']) as im:
   # Preserve the native centre, where the flyer and contact are visible.
   sheet.paste(im.crop((220,200,720,575)).resize((400,300)),((i%4)*400,(i//4)*320))
  draw.text(((i%4)*400+4,(i//4)*320+301),f"{f['image']} stage={f['stage']} err={f['boneError']:.5f}",fill='white')
 sheet.save(root/f'flyer-review-{scene}.jpg',quality=90)
print(json.dumps({'frames':len(e['frames']),'boneErrorMax':max(f['boneError'] for f in e['frames']),'hits':e['hits'],'end':[r for r in e['events'] if r['name']=='end'],'receipts':e['presentations']}))
