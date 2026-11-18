"""Eight native keyframes per spitter scene; no simulated or reconstructed images."""
import json,sys
from pathlib import Path
from PIL import Image,ImageDraw
root=Path(sys.argv[1]).resolve()
if root.drive.lower()!='d:':raise ValueError('D: only')
e=json.loads((root/'evidence.json').read_text())
for scene in range(7):
    frames=[f for f in e['frames'] if f['scene']==scene]
    times=[0,10,14,16,24,34,46,60] if scene<5 else [0,4,6,9,12,15,26,60]
    if scene==3:times=[0,5,14,16,18,19,24,60]
    if scene==4:times=[0,5,10,14,15,16,24,60]
    sheet=Image.new('RGB',(1440,528),'#111');draw=ImageDraw.Draw(sheet)
    for i,t in enumerate(times):
        f=min(frames,key=lambda f:abs(f['age']+f['partial']-t));x=i%4*360;y=i//4*264
        with Image.open(root/f['image']) as im:sheet.paste(im.resize((360,240)),(x,y+24))
        draw.text((x+3,y+2),f"{f['image']} mouth={f.get('mouthError',0):.4f}",fill='white')
    sheet.save(root/f'spitter-review-{scene}.jpg',quality=90)
print(f"Prepared seven sheets from {len(e['frames'])} native frames")
