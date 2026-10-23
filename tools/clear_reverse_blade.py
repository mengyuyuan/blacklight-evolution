"""Bank the blade outward during the consume follow-through; preserve contact timing.
Run in Blender Python for mathutils/numpy. The first carry key stays unchanged.
"""
import json,math,copy,sys
from pathlib import Path
import numpy as np
from mathutils import Matrix,Quaternion
ROOT=Path(__file__).resolve().parents[1]
path=ROOT/'src/main/resources/assets/blacklight/native_animations/biped/action/consume_blade.json'
backup=ROOT/'tmp/weapons-r15-before/consume_blade.json'
if not backup.exists():backup.write_bytes(path.read_bytes())
original=json.loads(path.read_text())
old_bank=original.pop('r15ReverseBladeBankDegrees',0)
if old_bank:
 for tr in original['animation']:
  if tr['name']!='Hand_R':continue
  for time,f in zip(tr['time'],tr['transform']):
   w=max(0,min(1,time/.28,(2.4-time)/.32));w=w*w*(3-2*w)
   f['rot']=list((Quaternion(f['rot'])@Quaternion((0,1,0),-math.radians(old_bank)*w)).normalized())
rig_path=Path(sys.argv[sys.argv.index('--')+1])
rig=json.loads(rig_path.read_text())['armature']
mesh=json.loads((ROOT/'src/main/resources/assets/blacklight/models/form_blade.json').read_text())
verts=np.array(list({tuple(v) for f in mesh['joints']['Hand_R'] for v in f['p']}))
verts=np.c_[verts,np.ones(len(verts))]
def pose(tracks,time):
 out={}
 def walk(b,parent):
  tr=tracks[b['name']];i=min(range(len(tr['time'])),key=lambda i:abs(tr['time'][i]-time));f=tr['transform'][i]
  flat=b['transform'];rest=Matrix([flat[j:j+4] for j in range(0,16,4)])
  local=Matrix.Translation(f['loc'])@Quaternion(f['rot']).to_matrix().to_4x4()@Matrix.Diagonal((*f['sca'],1))
  world=parent@rest@local;out[b['name']]=world
  for child in b['children']:walk(child,world)
 for b in rig['hierarchy']:walk(b,Matrix.Identity(4))
 return out
def clearance(clip):
 tracks={tr['name']:tr for tr in clip['animation']};minimum=100;closest=0
 for time in tracks['Head']['time']:
  p=pose(tracks,time);transform=np.array(p['Head'].inverted()@p['Hand_R'])
  points=(verts@transform.T)[:,:3];points[:,1]-=.25
  d=np.maximum(np.abs(points)-.25,0);distance=np.sqrt((d*d).sum(axis=1)).min()
  if distance<minimum:minimum=float(distance);closest=time
 return minimum,closest
base=clearance(original);print('base',base,flush=True)
for degrees in (0,15,-15,30,-30,45,-45,60,-60,75,-75,90,-90):
 clip=copy.deepcopy(original)
 for tr in clip['animation']:
  if tr['name']!='Hand_R':continue
  for time,f in zip(tr['time'],tr['transform']):
   # Fully rolled for load/strike/recovery; blend smoothly from the carried pose.
   w=min(1,time/.28,(2.4-time)/.32);w=max(0,w);w=w*w*(3-2*w)
   q=Quaternion(f['rot'])@Quaternion((0,1,0),math.radians(degrees)*w)
   f['rot']=list(q.normalized())
 result=clearance(clip);print(degrees,result,flush=True)
 if result[0]>=.065:
  clip['r15ReverseBladeBankDegrees']=degrees
  path.write_text(json.dumps(clip,separators=(',',':'))+'\n')
  print('SAVED',degrees,'clearance',result,flush=True);break
else:raise RuntimeError('No safe bank found; change the authored shoulder path')
