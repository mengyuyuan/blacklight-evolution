"""Contact sheets from captured native frames, without synthesizing game imagery."""
import json,sys
from pathlib import Path
from PIL import Image,ImageDraw
root=Path(sys.argv[1]).resolve()
if root.drive.lower()!='d:':raise ValueError('D: only')
e=json.loads((root/'evidence.json').read_text())
for scene in range(3):
 rows=[min([f for f in e['frames'] if f['scene']==scene],key=lambda f:abs(f['age']-t)) for t in [52,54,60,70,80,100,120,124,126,130,136,139,140,142,150,165]]
 sheet=Image.new('RGB',(1920,1360),'#111111');draw=ImageDraw.Draw(sheet)
 for i,f in enumerate(rows):
  with Image.open(root/f['image']) as im:sheet.paste(im.resize((480,320)),((i%4)*480,(i//4)*340))
  draw.text(((i%4)*480+4,(i//4)*340+321),f"{f['image']} action={f['action']} release={f.get('released','-')} grip={f['gripError']:.6f}",fill='white')
 sheet.save(root/f'throw-review-{scene}.jpg',quality=88)
rows=[min([f for f in e['frames'] if f['scene']==0],key=lambda f:abs(f['age']-t)) for t in [24,26,27,28,29,30,32,38,45]]
sheet=Image.new('RGB',(1440,1020),'#111111');draw=ImageDraw.Draw(sheet)
for i,f in enumerate(rows):
 with Image.open(root/f['image']) as im:sheet.paste(im.resize((480,320)),((i%3)*480,(i//3)*340))
 draw.text(((i%3)*480+4,(i//3)*340+321),f"{f['image']} actual supported pickup contact",fill='white')
sheet.save(root/'throw-ground-review.jpg',quality=88)
rows=[min([f for f in e['frames'] if f['scene']==0],key=lambda f:abs(f['age']-t)) for t in [136,137,138,139,140,141,142,143,144,145,147,150]]
sheet=Image.new('RGB',(1360,1110),'#111111');draw=ImageDraw.Draw(sheet)
for i,f in enumerate(rows):
 with Image.open(root/f['image']) as im:sheet.paste(im.crop((270,340,440,515)).resize((340,350)),((i%4)*340,(i//4)*370))
 draw.text(((i%4)*340+4,(i//4)*370+351),f['image']+' native crop',fill='white')
sheet.save(root/'throw-impact-review.jpg',quality=90)
print('Native pickup/carry/throw/impact review sheets written.')
