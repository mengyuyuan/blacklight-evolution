"""Consecutive native projectile contact frames from the hidden receiver audit."""
import json,sys
from pathlib import Path
from PIL import Image,ImageDraw
root=Path(sys.argv[1]).resolve()
if root.drive.lower()!='d:':raise ValueError('D: only')
e=json.loads((root/'evidence.json').read_text());pages=[]
for scene in range(6):
    rows=[]
    for tick in [19,20,21,22,23,24,25,26,28,32,40,50]:
        choices=[f for f in e['frames'] if f['scene']==scene and f['age']==tick]
        if not choices:
            choices=sorted([f for f in e['frames'] if f['scene']==scene],key=lambda f:abs(f['age']-tick))[:1]
        rows.append(min(choices,key=lambda f:f['partial']))
    im=Image.new('RGB',(1440,1392),'#15151a');d=ImageDraw.Draw(im)
    for i,r in enumerate(rows):
        x=i%3*480;y=i//3*348
        with Image.open(root/r['image']) as f:im.paste(f.resize((480,320)),(x,y+28))
        d.text((x+3,y+4),f"{r['image']}  hp={r['health']:.0f}  pose error={r.get('poseError',-1):.6f}",fill='white')
    name=f'incoming-review-{scene}.jpg';im.save(root/name,quality=92);pages.append(name)
(root/'review-pages.json').write_text(json.dumps(pages,indent=2)+'\n');print(json.dumps(pages))
