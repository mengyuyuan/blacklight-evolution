"""Review real native frames for capture, consume, release and lethal handoffs."""
import json, sys
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(sys.argv[1]).resolve()
if root.drive.lower() != 'd:':
    raise ValueError('D: only')
e = json.loads((root / 'evidence.json').read_text())
for scene in sorted({f['scene'] for f in e['frames']}):
    frames = [f for f in e['frames'] if f['scene'] == scene]
    times = [60, 67, 68, 69, 71, 76, 85, 95, 105, 116, 125, 136, 148, 160, 180, 210]
    if scene == 4:
        times = [60, 65, 68, 71, 74, 77, 80, 83, 90, 95, 100, 105, 110, 125, 140, 155]
    sheet = Image.new('RGB', (1920, 1408), '#111')
    draw = ImageDraw.Draw(sheet)
    for i, t in enumerate(times):
        f = min(frames, key=lambda row: abs(row['age'] + row['partial'] - t))
        x, y = i % 4 * 480, i // 4 * 352
        with Image.open(root / f['image']) as im:
            sheet.paste(im.resize((480, 320)), (x, y + 32))
        body = f.get('physical', {})
        draw.text((x + 3, y + 2), f"{f['image']} stage={f['visual']['stage']} hp={f['health']}", fill='white')
        draw.text((x + 3, y + 17), f"parts={body.get('parts',0)} cuts={body.get('cuts',0)} components={body.get('components',0)}", fill='white')
    sheet.save(root / f'handoff-review-{scene}.jpg', quality=90)
print(f"Reviewed manifest: {len(e['frames'])} frames")
