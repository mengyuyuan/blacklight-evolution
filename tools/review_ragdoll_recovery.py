"""Inspect the actual transition to the native renderer, not only distant keyframes."""
import json, sys
from pathlib import Path
from PIL import Image, ImageDraw

root=Path(sys.argv[1]).resolve()
if root.drive.lower()!='d:': raise SystemExit('D: required')
for check in json.loads((root/'case-checks.json').read_text(encoding='utf-8')):
    name=check['case']
    if '_survive' not in name: continue
    rows=json.loads((root/(name+'.json')).read_text(encoding='utf-8'))
    physical=[r for r in rows if 'renderTargetGap' in r]
    if not physical: continue
    last=physical[-1]['tick']
    choices=[]
    for tick in range(last-26,last+6,2):
        choices.append(min(rows,key=lambda r:abs(r['tick']-tick)))
    sheet=Image.new('RGB',(1440,4*266+30),'#171d22');draw=ImageDraw.Draw(sheet)
    draw.text((8,7),f'{name} | physical-to-native handoff | last physical tick={last}',fill='white')
    for i,r in enumerate(choices):
        x=(i%4)*360;y=30+(i//4)*266
        with Image.open(root/r['image']) as im: sheet.paste(im.resize((360,240)),(x,y))
        label=f't={r["tick"]} native renderer' if 'renderTargetGap' not in r else f't={r["tick"]} error={r["renderTargetGap"]:.4f} blocks'
        draw.text((x+3,y+243),label,fill='white')
    sheet.save(root/(name+'-recovery-review.jpg'),quality=92)
    print(root/(name+'-recovery-review.jpg'))
