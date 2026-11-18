"""Consecutive rendered contact frames, with source age and actual fragment count."""
import json,sys,math
from pathlib import Path
from PIL import Image,ImageDraw
root=Path(sys.argv[1]).resolve()
if root.drive.lower()!='d:':raise ValueError('D: only')
e=json.loads((root/'evidence.json').read_text());pages=[]
for scene in range(e['scenes']):
 times=list(range(41,55))+list(range(59,67)) if scene in (0,5,6) else list(range(22,35)) if scene in (1,3) else list(range(38,51)) if scene==2 else list(range(44,55))
 if scene==5:times=list(range(41,51))+[55,65,75,80,84,85,86,87,88,89,90,95,100,110]
 if scene==7:times=[36,38,40,41,42,44,46,50,60,70,80,90]
 rows=[]
 for tick in times:
  choices=[f for f in e['frames'] if f['scene']==scene and int(f['age'])==tick]
  if choices:rows.append(min(choices,key=lambda f:f['age']))
 for start in range(0,len(rows),12):
  chosen=rows[start:start+12];im=Image.new('RGB',(1440,348*math.ceil(len(chosen)/3)),'#15151a');draw=ImageDraw.Draw(im)
  for i,r in enumerate(chosen):
   x=i%3*480;y=i//3*348
   with Image.open(root/r['image']) as f:im.paste(f.resize((480,320)),(x,y+28))
   draw.text((x+3,y+4),f"{r['image']}  clock={r['age']:.2f}  pose={r.get('poseAge',r['age']):.2f}  debris={r['debris']}",fill='white')
  name=f'hydra-review-{scene}-{start//12}.jpg';im.save(root/name,quality=92);pages.append(name)
(root/'review-pages.json').write_text(json.dumps(pages,indent=2)+'\n');print(json.dumps(pages))
