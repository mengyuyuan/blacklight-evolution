"""Create contact-sequence sheets from the native ragdoll audit; D: output only."""
import json, sys
from pathlib import Path
from PIL import Image, ImageDraw

root=Path(sys.argv[1]).resolve()
if root.drive.lower()!='d:': raise SystemExit('D: required')
checks=json.loads((root/'case-checks.json').read_text(encoding='utf-8'))
for check in checks:
    name=check['case']
    rows=json.loads((root/(name+'.json')).read_text(encoding='utf-8'))
    samples=[r for r in rows if r.get('imageQueued') and (root/r['image']).is_file()]
    first=next((i for i,r in enumerate(samples) if r.get('cuts',0)>0),None)
    chosen=set(round(i*(len(samples)-1)/11) for i in range(12))
    if first is not None: chosen.update(max(0,min(len(samples)-1,first+d)) for d in (-2,-1,0,1,3,8))
    while len(chosen)<20:
        candidates=[i for i in range(len(samples)) if i not in chosen]
        if not candidates: break
        chosen.add(max(candidates,key=lambda i:min(abs(i-j) for j in chosen)))
    chosen=sorted(chosen)[:20]
    sheet=Image.new('RGB',(1280,5*244+34),'#171d22');draw=ImageDraw.Draw(sheet)
    draw.text((8,7),f'{name} | native frame/contact review | assertions={check["passed"]}',fill='white')
    for n,i in enumerate(chosen):
        r=samples[i];x=(n%4)*320;y=34+(n//4)*244
        with Image.open(root/r['image']) as im: sheet.paste(im.resize((320,213)),(x,y))
        draw.text((x+3,y+215),f't={r["tick"]} hp={r["health"]:.1f} cut={r.get("cuts",0)} bodies={r.get("components",0)}',fill='white')
        draw.text((x+3,y+228),f'grab={r["grab"]} frame={r["frame"]}',fill='#a0bdc9')
    sheet.save(root/(name+'-review.jpg'),quality=90)
print(f'{len(checks)} sheets written under {root}')
