"""Retarget the decoded P1 dive-roll, without changing target bone lengths.

Blender background Python; decoded animation and skeleton paths are private inputs.
The FIG explicitly enters dodge on a Run release held < .3 s. Its frame slices
are 8..20 / .3 s, 20..35 / .266667 s, 35..55 / .366667 s (53 source frames).
Root locomotion belongs to server physics; retain only the crouch/roll height.
"""
import argparse, json, math, sys, hashlib
from pathlib import Path
from mathutils import Matrix, Quaternion, Vector

args=argparse.ArgumentParser();args.add_argument('--decoded',type=Path,required=True);args.add_argument('--rig',type=Path,required=True)
a=args.parse_args(sys.argv[sys.argv.index('--')+1:]);root=Path(__file__).resolve().parents[1]
clip=json.loads(a.decoded.read_text(encoding='utf8'));assert not clip['failures']
rig=next(r for r in json.loads(a.rig.read_text(encoding='utf8')) if r['name']=='alex_reg_arms_skeleton')
tracks={g['name']:{c['semantic']:c for c in g['channels']} for g in clip['groups']}
def channel(c,t):
 ts,vs=c['times'],c['values']
 hi=next((i for i,x in enumerate(ts) if x>=t),len(ts)-1);lo=max(0,hi-1)
 f=max(0,min(1,(t-ts[lo])/max(1e-8,ts[hi]-ts[lo])))
 if c['semantic']=='ROT':
  v,w=vs[lo],vs[hi];return Quaternion((v[3],*v[:3])).slerp(Quaternion((w[3],*w[:3])),f)
 return Vector(vs[lo]).lerp(Vector(vs[hi]),f)
def worlds(t=None):
 result={};indexed=[]
 for i,j in enumerate(rig['joints']):
  local=Matrix([j['matrix'][k:k+4] for k in range(0,16,4)]).transposed()
  pos,rot,scale=local.decompose()
  if t is not None:
   for kind,c in tracks.get(j['name'],{}).items():
    if kind=='ROT':rot=channel(c,t)
    elif kind=='TRAN':pos=channel(c,t)
  local=Matrix.LocRotScale(pos,rot,Vector((1,1,1)))
  world=local if i==0 else indexed[j['parent']]@local
  indexed.append(world);result[j['name']]=world
 return result
bind=worlds();target=json.loads((root/'src/main/resources/assets/blacklight/animation/player_bind.json').read_text())
rest={};parents={};global_bind={}
def load(n,parent=None):
 name=n['name'];m=Matrix([n['transform'][k:k+4] for k in range(0,16,4)])
 if parent is None:m=Matrix.Rotation(-math.pi/2,4,'X')@m
 rest[name]=m;parents[name]=parent;global_bind[name]=(global_bind[parent]@m if parent else m)
 for c in n['children']:load(c,name)
load(target['hierarchy'][0])
mapping={'Root':'Pelvis','Torso':'Spine_1','Chest':'Spine_3','Head':'Head'}
for s in ('L','R'):
 mapping.update({'Thigh_'+s:'Hip_'+s,'Leg_'+s:'Knee_'+s,'Knee_'+s:'Knee_'+s,
 'Shoulder_'+s:'Clavicle_'+s,'Arm_'+s:'Shoulder_'+s,'Hand_'+s:'Elbow_'+s,'Tool_'+s:'Wrist_'+s,'Elbow_'+s:'Elbow_'+s})
S=Matrix.Diagonal(Vector((-1,1,1)));scale=global_bind['Root'].translation.y/bind['Pelvis'].translation.y
segments=[(0,.3,8,20),(.3,.566667,20,35),(.566667,.933333,35,52)]
times=[i/60 for i in range(57)];frames={n:[] for n in rest}
for time in times:
 start,end,first,last=next((s for s in segments if time<=s[1]+1e-6),segments[-1])
 t=(first+(last-first)*max(0,min(1,(time-start)/(end-start))))/30
 source=worlds(t);current={}
 for n in rest:
  src=mapping[n];rot=S@(source[src].to_quaternion()@bind[src].to_quaternion().inverted()).to_matrix()@S@global_bind[n].to_3x3()
  parent=parents[n]
  pos=(current[parent]@rest[n]).translation if parent else global_bind[n].translation.copy()
  if not parent:
   relative=source['Pelvis'].translation-source['Motion_Root'].translation
   baseline=bind['Pelvis'].translation-bind['Motion_Root'].translation
   pos.y+=(relative.y-baseline.y)*scale
  current[n]=rot.to_4x4();current[n].translation=pos
  local=current[parent].inverted()@current[n] if parent else Matrix.Rotation(math.pi/2,4,'X')@current[n]
  frames[n].append([round(v,8) for row in local for v in row])
result={'format':'MATRIX','animation':[{'name':n,'time':times,'transform':frames[n]} for n in rest],
 'blacklight':{'durationTicks':19,'transitionSeconds':.05},
 'source':{'animation':clip['name'],'sha256':hashlib.sha256(a.decoded.read_bytes()).hexdigest(),'figOwner':7080,'segments':segments},
 'privateNotice':'Original-derived motion for local testing. Target lengths are fixed; server physics owns displacement. Final source frame clamped to 52.'}
out=root/'art/motion-profile/basic_dodge_roll.json';out.write_text(json.dumps(result,separators=(',',':')),encoding='utf8')
print('EXPORTED',out,len(times),'frames',scale)

manifest_path=root/'art/motion-profile-manifest.json'
manifest=json.loads(manifest_path.read_text(encoding='utf8'))
manifest['files'][out.name]={'bytes':out.stat().st_size,'sha256':hashlib.sha256(out.read_bytes()).hexdigest()}
manifest['files']=dict(sorted(manifest['files'].items()))
manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
