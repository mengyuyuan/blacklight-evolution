"""Contact sheets of every captured game frame; no synthetic model renders."""
from pathlib import Path
import json,math
from PIL import Image,ImageDraw,ImageFont
root=Path('D:/CodexCache/creature-combat-qa/hunter-source-frames')
data=json.loads((root/'evidence.json').read_text(encoding='utf8'))
font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',16)
for scene in range(data['scenes']):
 rows=[r for r in data['frames'] if r['scene']==scene]
 for page in range(math.ceil(len(rows)/32)):
  batch=rows[page*32:page*32+32];canvas=Image.new('RGB',(1280,math.ceil(len(batch)/4)*236),(22,22,22));draw=ImageDraw.Draw(canvas)
  for i,row in enumerate(batch):
   x=(i%4)*320;y=(i//4)*236
   with Image.open(root/row['image']) as picture:canvas.paste(picture.resize((320,213)),(x,y))
   draw.text((x+4,y+215),f"{row['image']}  {row['age']:.2f}",font=font,fill=(235,235,235))
  canvas.save(root/f'review-{scene}-{page}.jpg',quality=92)
print('Reviewed source frame inventory:',len(data['frames']),'frames; created bounded contact sheets')
