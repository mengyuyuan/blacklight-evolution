"""Eight native keyframes per stalker scene, selected from the latest manifest."""
import json,sys
from pathlib import Path
from PIL import Image,ImageDraw
root=Path(sys.argv[1]).resolve()
if root.drive.lower()!='d:':raise ValueError('D: only')
e=json.loads((root/'evidence.json').read_text())
for scene in range(8):
    frames=[f for f in e['frames'] if f['scene']==scene]
    times=([0,7,10,12,14,18,24,40] if scene==0 else
           [0,9,12,18,23,26,30,40] if scene==1 else
           [0,9,12,15,18,22,26,40] if scene in [2,3,4,7] else
           [0,7,10,12,14,18,24,40])
    sheet=Image.new('RGB',(1440,528),'#111');draw=ImageDraw.Draw(sheet)
    for i,t in enumerate(times):
        f=min(frames,key=lambda f:abs(f['age']+f['partial']-t));x=i%4*360;y=i//4*264
        with Image.open(root/f['image']) as im:sheet.paste(im.resize((360,240)),(x,y+24))
        draw.text((x+3,y+2),f"{f['image']} claw={f.get('clawError',0):.5f}",fill='white')
    sheet.save(root/f'stalker-review-{scene}.jpg',quality=90)
print(f"Prepared eight sheets from {len(e['frames'])} native frames")
