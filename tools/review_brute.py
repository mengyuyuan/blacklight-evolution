"""Native Brute keyframes from the latest manifest, without reconstructed imagery."""
import json,sys
from pathlib import Path
from PIL import Image,ImageDraw
root=Path(sys.argv[1]).resolve()
if root.drive.lower()!='d:':raise ValueError('D: only')
e=json.loads((root/'evidence.json').read_text())
for scene in list(range(10))+[s for s in range(15,20) if any(f['scene']==s for f in e['frames'])]:
    frames=[f for f in e['frames'] if f['scene']==scene]
    times=([8,12,14,17,20,24,30,40] if scene in [15,19] else
           [10,18,20,23,27,31,38,48] if scene==16 else
           [10,16,18,21,25,30,38,48] if scene==17 else
           [22,24,25,27,30,34,40,50] if scene==18 else
           [18,26,29,33,36,40,46,54] if scene==1 else
           [18,24,27,31,35,38,45,52] if scene==6 else
           [0,12,16,18,21,26,34,54] if scene in [0,5,8] else
           [0,10,14,18,22,28,38,54] if scene in [2,3,4] else
           [0,18,22,25,28,36,46,62])
    sheet=Image.new('RGB',(1440,528),'#111');draw=ImageDraw.Draw(sheet)
    for i,t in enumerate(times):
        f=min(frames,key=lambda f:abs(f['age']+f['partial']-t));x=i%4*360;y=i//4*264
        with Image.open(root/f['image']) as im:sheet.paste(im.resize((360,240)),(x,y+24))
        draw.text((x+3,y+2),f"{f['image']} pose={f['poseAge']:.1f}",fill='white')
    sheet.save(root/f'brute-review-{scene}.jpg',quality=90)
print(f"Prepared combat/reaction sheets from {len(e['frames'])} native frames")
