"""Contact sheets of every captured game tick around the changed visual events."""
import json, math
from pathlib import Path
from PIL import Image, ImageDraw

root = Path('D:/CodexCache/creature-combat-qa/terrain-impact-frames')
evidence = json.loads((root / 'evidence.json').read_text())
windows = {0: [(14, 25), (38, 49)], 1: [(39, 58)], 2: [(14, 27)],
           3: [(26, 40)], 4: [(14, 24), (38, 48)], 6: [(39, 58)], 7: [(14, 24), (38, 48)]}
pages = []
for scene, spans in windows.items():
    rows = sorted((r for r in evidence['frames'] if r['scene'] == scene
                   and any(a <= int(r['image'][-7:-4]) <= b for a, b in spans)), key=lambda r: r['image'])
    for page in range(math.ceil(len(rows) / 16)):
        selected = rows[page * 16:page * 16 + 16]
        sheet = Image.new('RGB', (1280, 245 * math.ceil(len(selected) / 4)), '#17181b')
        draw = ImageDraw.Draw(sheet)
        for i, row in enumerate(selected):
            x, y = i % 4 * 320, i // 4 * 245
            with Image.open(root / row['image']) as im:
                sheet.paste(im.resize((320, 213)), (x, y + 32))
            draw.text((x + 3, y + 2), row['image'], fill='white')
            draw.text((x + 3, y + 17), f"ground={row['groundBursts']} hit={row['bodyBursts']} terrain={row['terrainCells']} debris={row['debris']}", fill='#cccccc')
        name = f'contact-{scene}-{page + 1}.jpg'
        sheet.save(root / name, quality=90)
        pages.append(name)
(root / 'contact-pages.json').write_text(json.dumps(pages, indent=2))
print(json.dumps(pages))
