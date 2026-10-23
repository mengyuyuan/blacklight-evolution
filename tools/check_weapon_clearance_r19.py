"""Check authored weapon vertices against the posed head; bank blade only when necessary."""
import ast,json,sys,math,copy
from pathlib import Path
import numpy as np
from mathutils import Matrix,Quaternion
ROOT=Path(__file__).resolve().parents[1]
rig=json.loads(Path(sys.argv[sys.argv.index('--')+1]).read_text(encoding='utf8'))['armature']
tree=ast.parse((ROOT/'tools/clear_reverse_blade.py').read_text(encoding='utf8'))
fn=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='pose')
exec(compile(ast.Module(body=[fn],type_ignores=[]),'<pose>','exec'))
folder=ROOT/'src/main/resources/assets/blacklight/native_animations/biped/combat'
results=[]
for path in sorted(folder.glob('*.json')):
 prefix=path.stem.split('_')[0]
 if prefix not in ('blade','hammer','whip'):continue
 model={'blade':'blade','hammer':'hammerfists','whip':'whipfist'}[prefix]
 mesh=json.loads((ROOT/f'src/main/resources/assets/blacklight/models/form_{model}.json').read_text(encoding='utf8'))
 joints={j:np.c_[np.array(list({tuple(v) for f in faces for v in f['p']})),np.ones(len({tuple(v) for f in faces for v in f['p']}))] for j,faces in mesh['joints'].items() if faces}
 original=json.loads(path.read_text(encoding='utf8'))
 def clearance(clip):
  tracks={t['name']:t for t in clip['animation']};minimum=99;where=None
  for time in tracks['Head']['time']:
   p=pose(tracks,time)
   for joint,verts in joints.items():
    points=(verts@np.array(p['Head'].inverted()@p[joint]).T)[:,:3];points[:,1]-=.25
    delta=np.maximum(np.abs(points)-.25,0);distance=float(np.sqrt((delta*delta).sum(axis=1)).min())
    if distance<minimum:minimum=distance;where=(time,joint)
  return minimum,where
 result=clearance(original);bank=0
 if prefix=='blade' and result[0]<.06:
  duration=max(original['animation'][0]['time'])
  for degree in [-90,90,-60,60,-120,120,180]:
   clip=copy.deepcopy(original)
   for t in clip['animation']:
    if t['name']!='Hand_R':continue
    for time,f in zip(t['time'],t['transform']):
     w=max(0,min(1,time/.12,(duration-time)/.15));w=w*w*(3-2*w)
     f['rot']=list((Quaternion(f['rot'])@Quaternion((0,1,0),math.radians(degree)*w)).normalized())
   candidate=clearance(clip)
   if candidate[0]>=.06:
    result=candidate;bank=degree;clip['weaponClearanceBank']=degree;path.write_text(json.dumps(clip,separators=(',',':'))+'\n',encoding='utf8');break
 arm_result=result
 joints={j:v for j,v in joints.items() if j.startswith('Hand')}
 hand_result=clearance(original if bank==0 else clip)
 results.append({'clip':path.stem,'minimum':result[0],'where':result[1],'handClearance':hand_result[0],'bank':bank});print(results[-1],flush=True)
(ROOT/'tmp/gameplay-r19-authoring/clearance.json').write_text(json.dumps(results,indent=2),encoding='utf8')
if any(r['minimum']<.005 or r['handClearance']<.055 for r in results):raise RuntimeError('Weapon reaches head: inspect clearance report')
