"""Retarget decoded P1 global rotations to the authored creature rigs.
Run with Blender --background --python this_file -- --source DIR --brawler-rig FILE.
Keeps original timing, removes world locomotion, and records exact source hashes.
This is source-derived adaptation, not newly authored animation or an original-game engine.
"""
from pathlib import Path
import argparse,sys,json,math,hashlib
from mathutils import Matrix,Quaternion,Vector
sys.path.insert(0,str(Path(__file__).resolve().parent))
from hunter_retarget import calibration, rotations, pelvis_height
args=argparse.ArgumentParser();args.add_argument('--source',type=Path,required=True);args.add_argument('--brawler-rig',type=Path,required=True)
a=args.parse_args(sys.argv[sys.argv.index('--')+1:]);R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/blacklight'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def sample(c,t):
 ts,vs=c['times'],c['values']
 if t<=ts[0]:return vs[0]
 for i in range(len(ts)-1):
  if t<=ts[i+1]:
   f=(t-ts[i])/max(1e-8,ts[i+1]-ts[i]);v,w=vs[i:i+2]
   if c['semantic']=='ROT':
    q=Quaternion((v[3],*v[:3])).slerp(Quaternion((w[3],*w[:3])),f);return [q.x,q.y,q.z,q.w]
   return [x+(y-x)*f for x,y in zip(v,w)]
 return vs[-1]
def worlds(rig,clip,t):
 tracks={g['name']:{c['semantic']:c for c in g['channels']} for g in clip['groups']};result={};byindex=[]
 for i,j in enumerate(rig['joints']):
  m=Matrix([j['matrix'][n:n+4] for n in range(0,16,4)]).transposed();pos,rot,scale=m.decompose()
  for semantic,c in tracks.get(j['name'],{}).items():
   v=sample(c,t)
   if semantic=='ROT':rot=Quaternion((v[3],*v[:3]))
   elif semantic=='TRAN':pos=Vector(v)
  local=Matrix.LocRotScale(pos,rot,scale);m=local if i==0 else byindex[j['parent']]@local
  byindex.append(m);result[j['name']]=m
 return result
hydra=next(s for s in read(a.source/'Hydra-inventory.json')['skeletons'] if s['name']=='hydra_skeleton')
hunter=read(a.brawler_rig)
HMAP={'neck_0':'full_rig_body1','neck_1':'full_rig_body2','neck_2':'full_rig_body3','neck_3':'full_rig_body4','neck_4':'full_rig_body5','neck_5':'Neck_2','neck_6':'Neck_5','head':'Head','jaw':'Gum','pincer_left':'Lip_L','pincer_right':'Lip_R','claw_left':'Claw_L','claw_right':'Claw_R'}
BMAP={'hips':'Pelvis','torso':'Spine_3','head':'Head','jaw':'Jaw'}
for side,s in [('left','L'),('right','R')]:
 BMAP.update({side+'_arm':'Shoulder_'+s,side+'_forearm':'Elbow_'+s,side+'_thigh':'Hip_'+s,side+'_shin':'Knee_'+s})
report=[]
for species,rig,mapping,baseclip,clips,model,animfile in [
 ('Hydra',hydra,HMAP,'hyd_act_idle',{'idle':'hyd_act_idle','emerge':'hyd_enter','retract':'hyd_exit','slam':'hyd_slam','sweep':'hyd_sweep','sidesweep':'hyd_sidesweep','capture':'hyd_grab_concrete','dive':'hyd_exit','arrival':'hyd_enter','toss':'hyd_ped_toss'},'blacklight_hydra.geo.json','blacklight_hydra.animation.json'),
 ('Brawler',hunter,BMAP,'brawler_act_calm',{'idle':'brawler_act_calm','walk':'brawler_loco_walk_n','run':'brawler_loco_run_n','swipe':'brawler_pnch_1','barrage':'brawler_claw_barrage','barrage_end':'brawler_claw_barrage_end','slam':'brawler_pnch_fist_slam','leap':'brawler_loco_jump_medium','air_slash':'brawler_pnch_air_slash','dodge':'brawler_loco_dodge_left'},'blacklight_hunter_authored.geo.json','blacklight_hunter.animation.json')]:
 geo=read(A/'geo'/model)['minecraft:geometry'][0]['bones'];parents={b['name']:b.get('parent') for b in geo}
 reference=worlds(rig,read(a.source/(species+'-'+baseclip+'.json')),0)
 output=read(A/'animations'/animfile)
 # Both rigs face -Z. Hunter's left joints use +X; Hydra's paired claws use -X.
 # Gecko negates JSON X/Y, so compensate those signs at export (not a Y half turn).
 S=Matrix.Identity(3) if species=='Hydra' else Matrix.Diagonal(Vector((-1,1,1)))
 setup=calibration(geo,reference,mapping,S) if species=='Brawler' else None
 if setup:
  (R/'art/hunter/rig-calibration.json').write_text(json.dumps(setup[3],indent=2),encoding='utf8')
 for target,name in clips.items():
  p=a.source/(species+'-'+name+'.json');clip=read(p);assert not clip['failures'],clip['failures']
  duration=(clip['num_frames']-1)/clip['frame_rate'];bones={b:{'rotation':{}} for b in mapping};last={}
  vertical=target in ['emerge','retract','dive','arrival']
  if vertical:bones['base']={'position':{}}
  if species=='Brawler':bones['hips']['position']={}
  for frame in range(int(clip['num_frames'])):
   t=frame/clip['frame_rate'];current=worlds(rig,clip,t);deltas={}
   calibrated=rotations(current,reference,mapping,S,setup) if setup else None
   for bone,source in mapping.items():
    world=current[source].to_quaternion()@reference[source].to_quaternion().inverted()
    # Retain the original partitioned jaw closure while the body uses exit/enter.
    # This is an adaptation for carrying a Minecraft player, not an original portal sequence.
    if species=='Hydra' and bone.startswith(('pincer','claw')) and target in ['capture','dive','arrival','toss']:
     grip_t=max(0,min(1,t-2.0)) if target=='capture' else 1
     if target=='toss':grip_t=max(0,min(1,(3.1-t)/.2))
     closed=worlds(rig,read(a.source/'Hydra-hyd_act_partitioned_grab.json'),grip_t)
     jaw_delta=closed[source].to_quaternion()@reference[source].to_quaternion().inverted()
     head_delta=current['Head'].to_quaternion()@reference['Head'].to_quaternion().inverted()
     world=head_delta@jaw_delta
    if calibrated is not None:world=calibrated[bone]
    deltas[bone]=world
    parent=parents.get(bone);local=deltas[parent].inverted()@world if parent in deltas else world
    matrix=local.to_matrix() if calibrated is not None else S@local.to_matrix()@S
    e=matrix.to_euler('XYZ',last[bone]) if bone in last else matrix.to_euler('XYZ');last[bone]=e
    bones[bone]['rotation'][str(round(t,6))]=[round(math.degrees(v)*(-1 if i<2 else 1),4) for i,v in enumerate(e)]
   if vertical:
    height=next(b['pivot'][1] for b in geo if b['name']=='head')
    scale=height/max(1,reference['Head'].translation.y-reference['Character_Root'].translation.y)
    y=(current['Character_Root'].translation.y-reference['Character_Root'].translation.y)*scale
    bones['base']['position'][str(round(t,6))]=[0,round(y,4),0]
   if species=='Brawler':
    # Missing pelvis height pinned every attack to its hunched idle origin.
    # Keep skeletal vertical motion; horizontal displacement stays AI-owned.
    bones['hips']['position'][str(round(t,6))]=[0,round(pelvis_height(geo,current,reference,mapping,calibrated),5),0]
  output['animations'][target]={'loop':target in ['idle','walk','run'],'animation_length':round(duration,6),'bones':bones}
  if species=='Brawler' and target in ('swipe','dodge'):
   output['animations']['source_'+target]=output['animations'][target]
  overlay=a.source/'Hydra-hyd_act_partitioned_grab.json' if species=='Hydra' and target in ['capture','dive','arrival','toss'] else None
  report.append(dict(species=species,runtime=target,source=name,sha256=hashlib.sha256(p.read_bytes()).hexdigest(),seconds=duration,boneMap=mapping,reference=baseclip,rootMotion='Removed horizontal locomotion; emerge/exit retain vertical Character_Root translation',jawOverlay=None if overlay is None else dict(source='hyd_act_partitioned_grab',sha256=hashlib.sha256(overlay.read_bytes()).hexdigest())))
 (A/'animations'/animfile).write_text(json.dumps(output,indent=2),encoding='utf8')
 if species=='Hydra':
  # Server and client use this same pose table. No client-only Gecko classes on server.
  names=[b['name'] for b in geo];pivots=[[-b['pivot'][0]/16,b['pivot'][1]/16,b['pivot'][2]/16] for b in geo]
  motion={'fps':30,'bones':names,'parents':[names.index(parents[n]) if parents[n] else -1 for n in names],'pivots':pivots,'clips':{}}
  for name,anim in output['animations'].items():
   if name=='feed':continue
   def channel(b,ch,t):
    data=anim['bones'].get(b,{}).get(ch,{})
    if not data:return [0,0,0]
    keys=sorted((float(k),v) for k,v in data.items())
    if t<=keys[0][0]:return keys[0][1]
    for (lo,v),(hi,w) in zip(keys,keys[1:]):
     if t<=hi:return [x+(y-x)*(t-lo)/(hi-lo) for x,y in zip(v,w)]
    return keys[-1][1]
   frames=[]
   for f in range(round(anim['animation_length']*30)+1):
    row=[]
    for n in names:
     rot=channel(n,'rotation',f/30);pos=channel(n,'position',f/30)
     row.append([round(math.radians(v)*(-1 if i<2 else 1),7) for i,v in enumerate(rot)]+[round(v/16,7) for v in pos])
    frames.append(row)
   motion['clips'][name]=frames
  (A/'models/hydra_motion.json').write_text(json.dumps(motion,separators=(',',':')),encoding='utf8')
(R/'art/hydra/motion-retarget-provenance.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print('RETARGETED',[(r['runtime'],r['source'],r['seconds']) for r in report])
