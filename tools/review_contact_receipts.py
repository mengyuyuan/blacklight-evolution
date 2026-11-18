"""Contact-adjacent frames only; writes one current set of review sheets beside evidence."""
import json, math, sys
from pathlib import Path
from PIL import Image, ImageDraw
root=Path(sys.argv[1])
if root.drive.lower()!='d:': raise ValueError('D: output only')
e=json.loads((root/'evidence.json').read_text())
rows=[r for r in e['frames'] if any(p['scene']==r['scene'] and p['contactAge']-2<=r['age']<=p['contactAge']+4 for p in e['presentations'])]
rows.sort(key=lambda r:(r['scene'],r['age']))
pages=[]
for page in range(math.ceil(len(rows)/16)):
    selected=rows[page*16:page*16+16]
    sheet=Image.new('RGB',(1280,245*math.ceil(len(selected)/4)),'#17181b');draw=ImageDraw.Draw(sheet)
    for i,r in enumerate(selected):
        x,y=i%4*320,i//4*245
        with Image.open(root/r['image']) as im:sheet.paste(im.resize((320,213)),(x,y+32))
        draw.text((x+3,y+2),r['image'],fill='white')
        draw.text((x+3,y+17),f"pose={r['age']:.2f} debris={r.get('debris',0)}",fill='#cccccc')
    name=f'receipt-review-{page+1}.jpg';sheet.save(root/name,quality=90);pages.append(name)
(root/'receipt-pages.json').write_text(json.dumps(pages,indent=2))
print(json.dumps(pages))
