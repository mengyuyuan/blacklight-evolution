"""Copy segment-only geometry into an isolated Gecko model cache resource.
Does not run Java, tests, a renderer or the game; original texture pixels stay intact.
"""
from pathlib import Path
import copy
import hashlib
import json

ROOT=Path(__file__).resolve().parents[1]
source=ROOT/'src/main/resources/assets/blacklight/geo/elizabeth_greene_mc.geo.json'
data=json.loads(source.read_text(encoding='utf8'))
result=copy.deepcopy(data)
geometry=result['minecraft:geometry'][0]
geometry['description']['identifier']='geometry.elizabeth_greene_segment_corpse'
counts={i:0 for i in range(3)}
for bone in geometry['bones']:
    if bone['name'].startswith('weak_'):
        slot=int(bone['name'][5]);counts[slot]+=len(bone.get('cubes',[]))
    else:
        bone.pop('cubes',None)
if counts!={0:10,1:10,2:10}:raise ValueError('Expected ten source Slug leaves per health child: '+str(counts))
target=source.with_name('elizabeth_greene_segment_corpse.geo.json')
target.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
print(json.dumps({'corpseGeometry':str(target),'segmentCubes':counts,'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'sha256':hashlib.sha256(target.read_bytes()).hexdigest()}))
