"""Contact sheets containing every captured tick, without selecting flattering frames."""
import argparse
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

parser = argparse.ArgumentParser()
parser.add_argument('--filter', choices=['monster_style', 'elizabeth_greene'], required=True)
args = parser.parse_args()
root = Path('D:/CodexCache/encounter-qa/frames')
data = json.loads((root / 'evidence.json').read_text())
assert data['lastRerun'] == args.filter, 'Capture this batch before making its sheets'
prefixes = ('leader_hunter_', 'juggernaut_', 'goliath_', 'flyer_') if args.filter == 'monster_style' else ('elizabeth_greene_',)
scenes = {}
for row in data['frames']:
    if row['scene'].startswith(prefixes):
        scenes.setdefault(row['scene'], []).append(row)
for scene, frames in scenes.items():
    frames.sort(key=lambda row: row['age'])
    sheet = Image.new('RGB', (1200, math.ceil(len(frames) / 3) * 292), '#191a1b')
    draw = ImageDraw.Draw(sheet)
    for i, row in enumerate(frames):
        x, y = i % 3 * 400, i // 3 * 292
        with Image.open(root / row['image']) as source:
            sheet.paste(source.resize((400, 267), Image.Resampling.LANCZOS), (x, y + 25))
        draw.text((x + 5, y + 6), f"{scene} | tick {row['age']}", fill='white')
    sheet.save(root / f'review-{scene}.png')
print(f'{len(scenes)} sheets, {sum(map(len, scenes.values()))} frames; all captured ticks included')
