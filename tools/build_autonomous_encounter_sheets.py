"""Review every captured frame of real AI, with action state from the client entity."""
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

root=Path('D:/CodexCache/encounter-qa/autonomous')
data=json.loads((root/'evidence.json').read_text())
scenes={}
for row in data['frames']:
    scenes.setdefault(row['scene'],[]).append(row)
assert len(scenes)==15 and data['eggCount']==33
for scene,frames in scenes.items():
    frames.sort(key=lambda row:row['elapsed'])
    sheet=Image.new('RGB',(1200,math.ceil(len(frames)/4)*235),'#1b1c1e')
    draw=ImageDraw.Draw(sheet)
    for i,row in enumerate(frames):
        x,y=i%4*300,i//4*235
        with Image.open(root/row['image']) as source:
            sheet.paste(source.resize((300,207),Image.Resampling.LANCZOS),(x,y+28))
        draw.text((x+3,y+3),scene+' '+row['move']+' @'+str(row['moveTick']),fill='white')
        draw.text((x+3,y+15),'elapsed '+str(row['elapsed'])+' target HP '+str(round(row.get('victimHealth',0),1)),fill='white')
    sheet.save(root/('review-'+scene+'.png'))
    assert any(row['move']!='NONE' for row in frames),scene+' never attacked'
    # Infection may later replace the target entity; a missing/dead target is not hit evidence.
    assert any(0<row.get('victimHealth',2000)<2000 for row in frames),scene+' no live damage frame'
print(f'{len(scenes)} autonomous scenes / {len(data["frames"])} frames, all acted and damaged targets, 33 eggs')
