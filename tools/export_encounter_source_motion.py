"""Retarget the user's local original skeletal curves. No authored attack keyframes.

The source assets stay in D:/PrototypeReference. Exported curves retain source
frame rate and duration; gameplay event timing is audited separately from FIG.
"""
import hashlib
import importlib.util
import json
import math
import re
from pathlib import Path
import struct
import sys
import zlib
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
LIB=Path('D:/PrototypeReference/p2/tools')
sys.path.insert(0,str(LIB))
spec=importlib.util.spec_from_file_location('source_decoder',LIB/'decode-animation.py')
decoder=importlib.util.module_from_spec(spec);spec.loader.exec_module(decoder)
ASSETS=ROOT/'src/main/resources/assets/blacklight'
P1=Path('D:/PrototypeReference/p1/private/comparison')
P2=Path('D:/PrototypeReference/p2/input/p2')

PROFILES={
 'juggernaut':(P2/'infected3.p3d','infected3_hammerer_skeleton','infected3_com_idle',{
  'idle':'infected3_com_idle','walk':'infected3_loco_walk','run':'infected3_com_run',
  'HAMMER':'infected3_com_2hit_combo','SLAM':'infected3_com_2hand_slam',
  'GUARD':'infected3_com_block','block_hit':'infected3_com_block_gethit',
  'exposed':'infected3_com_stuned'}),
 'goliath':(P2/'behemoth.p3d','behemoth_skeleton','behemoth_loco_biped_idle',{
  'idle':'behemoth_loco_biped_idle','walk':'behemoth_loco_biped_walk',
  'HAMMER':'behemoth_com_biped_front_slam_to_biped','SLAM':'behemoth_com_biped_slam',
  'CHARGE':'behemoth_com_biped_bullrush','ERUPTION':'behemoth_com_crawl_front_spike',
  'exposed':'behemoth_com_biped_leg_stun_to_biped','collapse':'behemoth_com_biped_leg_dismember_to_crawl',
  'crawl_idle':'behemoth_loco_crawl_idle','crawl':'behemoth_com_crawl',
  'crawl_slam':'behemoth_com_crawl_front_body_slam','rise':'behemoth_com_prone_to_biped',
  'death':'behemoth_com_biped_death_v2'}),
 'elizabeth_greene':(P1/'mother.p3d','mother_core_skeleton','mother_amb_idle_a',{
  'idle':'mother_amb_idle_a','HOWL':'mother_scream','ORBS':'mother_vomit','VOLLEY':'mother_vomit',
  'exposed':'mother_reveal_body','hit':'mother_collapse_hit','exit':'mother_loco_return_underground',
  'enter':'mother_loco_rise_from_ground','death':'mother_collapse_death'}),
 'leader_hunter':(P1/'LeaderHunter.p3d','leader_master_skeleton_skeleton','leader_act_calm',{
  'idle':'leader_act_calm','walk':'leader_loco_walk_n','run':'leader_loco_run_n',
  'CLAW':'leader_attack_2','DOUBLE':'leader_attack_1','POUNCE':'leader_pnch_air_slash',
  'HOWL':'leader_act_roar','exposed':'leader_gethit_stun','death':'leader_gethit_death_collapse'})
}

def nodes(path):
 raw=path.read_bytes()
 if raw[:2]==b'RZ':raw=zlib.decompress(raw[16:])
 return list(decoder.m.walk(decoder.m.tree(raw)))

def unit(v):
 n=np.linalg.norm(v)
 return v/n if n>1e-9 else np.array([0.,1.,0.])

def quat_matrix(q):
 x,y,z,w=np.asarray(q)/np.linalg.norm(q)
 return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],
  [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],
  [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])

def slerp(a,b,t):
 a=np.asarray(a);b=np.asarray(b);dot=np.dot(a,b)
 if dot<0:b=-b;dot=-dot
 if dot>.9995:return unit(a+(b-a)*t)
 angle=math.acos(max(-1,min(1,dot)))
 return (math.sin((1-t)*angle)*a+math.sin(t*angle)*b)/math.sin(angle)

def rotation_between(a,b):
 a=unit(a);b=unit(b);dot=np.dot(a,b)
 if dot>.999999:return np.eye(3)
 if dot<-.999999:
  axis=unit(np.cross(a,[1,0,0] if abs(a[0])<.9 else [0,0,1]))
  return quat_matrix([*axis,0])
 return quat_matrix([*np.cross(a,b),1+dot])

def euler(r):
 # Rz * Ry * Rx, matching JOML rotationZYX and Gecko's runtime joint angles.
 y=math.asin(max(-1,min(1,-r[2,0])))
 if abs(math.cos(y))>1e-6:x=math.atan2(r[2,1],r[2,2]);z=math.atan2(r[1,0],r[0,0])
 else:x=math.atan2(-r[1,2],r[1,1]);z=0
 return [x,y,z]

def skeleton(all_nodes,name):
 sk=next((n for n in all_nodes if n['type']==0x23000 and decoder.text8(n['payload'])[0]==name),None)
 if sk is None:
  return skeleton(nodes(Path('D:/PrototypeReference/p1/input/startup.p3d.rz')),name)
 result=[]
 for j in sk['children']:
  if j['type']!=0x23001:continue
  name,p=decoder.text8(j['payload']);parent=struct.unpack_from('<i',j['payload'],p)[0]
  matrix=np.array(struct.unpack_from('<16f',j['payload'],p+4)).reshape(4,4).T
  result.append((name,parent,matrix))
 return result

def track_at(track,frame):
 keys=track['frames'];values=track['values'];index=int(np.searchsorted(keys,frame,side='right'))-1
 index=max(0,index);hi=min(index+1,len(keys)-1)
 if frame<=keys[0] or hi==index:return values[index]
 t=min(1,(frame-keys[index])/(keys[hi]-keys[index]))
 return slerp(values[index],values[hi],t) if track['tag']=='ROT' else np.asarray(values[index])*(1-t)+np.asarray(values[hi])*t

def world(rig,clip,frame):
 tracks={}
 for t in clip['tracks']:tracks.setdefault(t['bone'],{})[t['tag']]=t
 matrices=[];out={}
 for i,(name,parent,bind) in enumerate(rig):
  local=bind.copy()
  for tag,t in tracks.get(name,{}).items():
   if tag=='ROT':local[:3,:3]=quat_matrix(track_at(t,frame))
   elif tag=='TRAN':local[:3,3]=track_at(t,frame)
  value=local if i==0 else matrices[parent]@local
  matrices.append(value);out[name]=value
 return out

def mapping_for(species,rig,geo):
 names={j[0] for j in rig}
 mapping={'root':'Character_Root','torso':'Spine_3','head':'Head','jaw':'Jaw'}
 for side,s in [('left','L'),('right','R')]:
  mapping.update({side+'_arm':'Shoulder_'+s,side+'_forearm':'Elbow_'+s,
   side+'_hand':'Wrist_'+s,side+'_leg':'Hip_'+s,side+'_shin':'Knee_'+s})
 return {k:v for k,v in mapping.items() if k in geo and v in names}

def fit_juggernaut_floor(row,order,geo,pivots):
 # Original long-arm lengths differ from the authored mesh. A two-joint CCD
 # correction keeps the visible fists above the actor's support plane while
 # preserving the source torso, wrist twist, timing and opposite-hand motion.
 def matrices():
  out={}
  for n,v in zip(order,row):
   x,y,z=v[:3];cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
   r=np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])
   m=np.eye(4);m[:3,:3]=r;m[:3,3]=pivots[n]+np.asarray(v[3:6])-r@pivots[n]
   out[n]=out.get(geo[n].get('parent'),np.eye(4))@m
  return out
 for side in ['left','right']:
  hand=side+'_hand';c=geo[hand]['cubes'][0];o=np.array(c['origin']);size=np.array(c['size'])
  center=(o+size/2)*[-1,1,1]
  corners=[(o+size*np.array([x,y,z]))*[-1,1,1] for x in [0,1] for y in [0,1] for z in [0,1]]
  for repeat in range(5):
   ms=matrices();bottom=min((ms[hand]@np.r_[p,1])[1] for p in corners)
   if bottom>=.4:break
   target=(ms[hand]@np.r_[center,1])[:3];target[1]+=.4-bottom
   for iteration in range(10):
    for joint in [side+'_forearm',side+'_arm']:
     ms=matrices();end=(ms[hand]@np.r_[center,1])[:3];pivot=(ms[joint]@np.r_[pivots[joint],1])[:3]
     parent=ms[geo[joint]['parent']][:3,:3]
     local=parent.T@rotation_between(end-pivot,target-pivot)@ms[joint][:3,:3]
     row[order.index(joint)][:3]=euler(local)
 return [[round(float(x),6) for x in v] for v in row]

def export(species,profile):
 if species in {'goliath','elizabeth_greene'}:
  # The old orientation-only export cannot preserve this rig's endpoints.
  # Always regenerate geometry and full articulated poses together from the
  # preserved authored bind, never export another pose onto the new pivots.
  import runpy
  script='rebind_goliath_source.py' if species=='goliath' else 'rebind_greene_source.py'
  runpy.run_path(str(ROOT/'tools'/script),run_name=species+'_rebind')
  out=ASSETS/'models'/f'encounter_{species}_source.json';result=json.loads(out.read_text())
  return {k:result[k] for k in ['source','sourceSha256','skeleton','mapping','reference','rootMotion']}|{'entity':species,'clips':{k:{'source':v['source'],'fps':v['fps'],'seconds':v['seconds']} for k,v in result['clips'].items()},'outputSha256':hashlib.sha256(out.read_bytes()).hexdigest()}
 path,rig_name,reference_name,clips=profile;all_nodes=nodes(path);rig=skeleton(all_nodes,rig_name)
 anims={decoder.text8(n['payload'],4)[0]:n for n in all_nodes if n['type']==0x121000}
 decoded={n:decoder.decode(anims[n]) for n in set(clips.values())|{reference_name}}
 reference=world(rig,decoded[reference_name],0)
 geo_list=json.loads((ASSETS/'geo'/f'{species}_mc.geo.json').read_text())['minecraft:geometry'][0]['bones']
 geo={b['name']:b for b in geo_list};mapping=mapping_for(species,rig,geo)
 order=[b['name'] for b in geo_list if b['name'] in mapping]
 # Runtime Geo pivots mirror JSON X; source rigs face -Z.
 S=np.diag([-1.,1.,1.]);pivots={n:np.array([-b['pivot'][0],b['pivot'][1],b['pivot'][2]]) for n,b in geo.items()}
 aims={'torso':('head','Head')}
 for side,s in [('left','L'),('right','R')]:
  aims.update({side+'_arm':(side+'_forearm','Elbow_'+s),side+'_forearm':(side+'_hand','Wrist_'+s),side+'_leg':(side+'_shin','Knee_'+s)})
 corrections={}
 for bone,source in mapping.items():
  if bone in aims and aims[bone][0] in pivots and aims[bone][1] in reference:
   child,original=aims[bone];corrections[bone]=rotation_between(pivots[child]-pivots[bone],S@(reference[original][:3,3]-reference[source][:3,3]))
  else:corrections[bone]=np.eye(3)
 # Motion_Root is a controller pivot (six units high on behemoth), not the
 # ground plane. Subtracting it magnifies vertical motion several times.
 head=reference['Head'][:3,3]
 scale=abs(pivots['head'][1])/max(1,abs(head[1]))
 result={'schema':1,'source':str(path),'sourceSha256':hashlib.sha256(path.read_bytes()).hexdigest(),
  'skeleton':rig_name,'reference':reference_name,'bones':order,'mapping':mapping,
  'pivots':[pivots[n].tolist() for n in order],
  'parents':[order.index(geo[n]['parent']) if geo[n].get('parent') in order else -1 for n in order],
  'pixelsPerSourceUnit':scale,
  'rootMotion':'Source ground is Y=0. Motion_Root travel and heading are removed from the skin; the Minecraft server supplies actor movement. Skin retains Character_Root lean and vertical displacement.',
  'clips':{}}
 for key,original in clips.items():
  clip=decoded[original];frames=[]
  for frame in range(int(clip['frames'])):
   current=world(rig,clip,frame)
   # Locomotion channels belong to the actor controller, not the skin. Keep the
   # reference Motion_Root basis while retaining all articulated body motion.
   motion=reference['Motion_Root']@np.linalg.inv(current['Motion_Root'])
   current={n:motion@matrix for n,matrix in current.items()};rotations={};transforms={};row=[]
   for bone in order:
    source=mapping[bone];delta=current[source][:3,:3]@np.linalg.inv(reference[source][:3,:3])
    rotation=S@delta@S@corrections[bone]
    if bone in aims and aims[bone][0] in pivots and aims[bone][1] in current:
     child,original_child=aims[bone];rest=unit(pivots[child]-pivots[bone]);desired=S@(current[original_child][:3,3]-current[source][:3,3])
     rotation=rotation_between(rotation@rest,desired)@rotation
    bone_scale=np.ones(3)
    rotations[bone]=rotation
    parent=geo[bone].get('parent')
    while parent and parent not in rotations:parent=geo[parent].get('parent')
    local=rotations[parent].T@rotation if parent else rotation
    pos=[0,0,0]
    if bone=='root':
     # Our mesh root is at the floor; original Character_Root rotates at the
     # pelvis. Rebase the same rotation without moving the body's support pivot.
     anchor=(pivots['left_leg']+pivots['right_leg'])/2 if 'left_leg' in pivots else pivots['root']
     pos=anchor-rotation@anchor
     pos[1]+=(current['Character_Root'][1,3]-reference['Character_Root'][1,3])*scale
    local_matrix=np.eye(4);local_matrix[:3,:3]=local@np.diag(bone_scale)
    local_matrix[:3,3]=pivots[bone]+np.asarray(pos)-local_matrix[:3,:3]@pivots[bone]
    transforms[bone]=transforms.get(parent,np.eye(4))@local_matrix
    row.append([round(float(x),6) for x in euler(local)+list(pos)+(list(bone_scale) if bone.startswith('neck_') else [])])
   if species=='juggernaut':row=fit_juggernaut_floor(row,order,geo,pivots)
   assert all(np.isfinite(p).all() for p in row)
   frames.append(row)
  result['clips'][key]={'source':original,'fps':clip['fps'],'frames':frames,'seconds':(clip['frames']-1)/clip['fps']}
 out=ASSETS/'models'/f'encounter_{species}_source.json'
 out.write_text(json.dumps(result,separators=(',',':')),encoding='utf8')
 print(species,len(result['clips']),sum(len(c['frames']) for c in result['clips'].values()),out.stat().st_size)
 return {k:result[k] for k in ['source','sourceSha256','skeleton','mapping','reference','rootMotion']}|{'entity':species,'clips':{k:{'source':v['source'],'fps':v['fps'],'seconds':v['seconds']} for k,v in result['clips'].items()},'outputSha256':hashlib.sha256(out.read_bytes()).hexdigest()}

if __name__=='__main__':
 selected=set(sys.argv[1:]) if len(sys.argv)>1 else set(PROFILES)
 if selected-set(PROFILES):raise ValueError('Unknown species: '+str(selected-set(PROFILES)))
 destination=ROOT/'art/encounters/source-motion-provenance.json'
 report=json.loads(destination.read_text(encoding='utf8')) if destination.exists() else []
 report=[r for r in report if r['entity'] not in selected]
 report.extend(export(k,v) for k,v in PROFILES.items() if k in selected)
 destination.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
