"""Original 128px skin-layout tissue material for the replaced upper-body surface."""
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[2]
out=ROOT/'src/main/resources/assets/blacklight/textures/sovereign_body.png'
im=Image.new('RGBA',(128,128),(23,22,25,255));d=ImageDraw.Draw(im)
# Broad interlocking pixel masses. Detail lives on the surface instead of dangling rods.
for x0,y0,w,h in [(16,20,24,6),(40,20,16,6),(32,52,16,6),(20,16,16,4),(44,16,8,4),(36,48,8,4)]:
 for y in range(h*2):
  for x in range(w*2):
   level=23+((x//5+y//3)%3)*4
   if (x+y//3)%9==0:level=15
   im.putpixel((x0*2+x,y0*2+y),(level,level-1,level+2,255))
for points in [[(20,20),(21,21),(21,23),(23,24)],[(27,20),(26,21),(26,23),(25,24)],
               [(33,20),(34,21),(34,23),(36,25)],[(39,20),(38,22),(38,24)],
               [(45,20),(46,22),(45,24)],[(37,52),(38,54),(37,56)],
               [(21,16),(22,18),(24,19)],[(45,17),(46,18),(46,19)],[(37,49),(38,50),(38,51)]]:
 points=[(x*2,y*2) for x,y in points]
 d.line(points,fill=(124,15,23,255),width=1)
 x,y=points[1];im.putpixel((x,y),(213,29,32,255))
out.parent.mkdir(parents=True,exist_ok=True);im.save(out)
print(out)
