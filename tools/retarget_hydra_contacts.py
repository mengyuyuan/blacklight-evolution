"""Calibrate only combat curves; gateway, carry, emergence and idle remain intact.

The authored rigid cuboids and textures are unchanged. Original joint directions
drive the fixed-length MC chain, while FIG supplies the separate playback clock.
"""
from export_encounter_source_motion import *
source=P1/'Hydra.p3d';all_nodes=nodes(source);rig=skeleton(all_nodes,'hydra_skeleton')
def clip(name):
 return decoder.decode(next(n for n in all_nodes if n['type']==0x121000 and decoder.text8(n['payload'],4)[0]==name))
reference=world(rig,clip('hyd_act_idle'),0)
geo=json.loads((ASSETS/'geo/blacklight_hydra_mc.geo.json').read_text())['minecraft:geometry'][0]['bones']
pivots={b['name']:np.array([-b['pivot'][0],b['pivot'][1],b['pivot'][2]])/16 for b in geo}
mapping={'neck_0':'full_rig_body1','neck_1':'full_rig_body2','neck_2':'full_rig_body3','neck_3':'full_rig_body4','neck_4':'full_rig_body5','neck_5':'Neck_2','neck_6':'Neck_4','head':'Head','jaw':'Gum','pincer_left':'Lip_L','pincer_right':'Lip_R','claw_left':'Claw_L','claw_right':'Claw_R'}
aims={f'neck_{i}':f'neck_{i+1}' for i in range(6)}
aims.update({'neck_6':'head','head':'jaw','pincer_left':'claw_left','pincer_right':'claw_right'})
offsets={};directions={}
for b in mapping:
 if b in aims:
  child=aims[b];directions[b]=unit(pivots[child]-pivots[b])
  offsets[b]=rotation_between(directions[b],reference[mapping[child]][:3,3]-reference[mapping[b]][:3,3])
 else:offsets[b]=offsets['pincer_'+b.split('_')[1]] if b.startswith('claw_') else offsets['head']
motion_path=ASSETS/'models/hydra_motion.json';motion=json.loads(motion_path.read_text())
report={'source':str(source),'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'mapping':mapping,
 'method':'Fixed authored segment lengths; original animated child directions with original world twist; calibrated head/jaw and pincers. Combat only. MC pivots are authoritative.',
 'unchanged':'Authored geometry, texture, idle, capture, dive, arrival, toss, feeding and transport logic.', 'clips':{}}
for key,name in [('slam','hyd_slam'),('sweep','hyd_sweep'),('sidesweep','hyd_sidesweep')]:
 c=clip(name);frames=[];last={}
 for t in range(int(c['frames'])):
  current=world(rig,c,t);rotations={};row=[]
  for b in geo:
   n=b['name'];parent=b.get('parent');rotation=np.eye(3)
   if n in mapping:
    s=mapping[n];rotation=current[s][:3,:3]@reference[s][:3,:3].T@offsets[n]
    if n in aims:
     desired=current[mapping[aims[n]]][:3,3]-current[s][:3,3]
     rotation=rotation_between(rotation@directions[n],desired)@rotation
   rotations[n]=rotation
   local=rotations[parent].T@rotation if parent else rotation
   angles=np.array(euler(local))
   if n in last:
    alternate=np.array([angles[0]+math.pi,math.pi-angles[1],angles[2]+math.pi])
    candidates=[v+2*math.pi*np.round((last[n]-v)/(2*math.pi)) for v in [angles,alternate]]
    angles=min(candidates,key=lambda v:np.linalg.norm(v-last[n]))
   last[n]=angles;row.append([round(float(v),7) for v in angles]+[0,0,0])
  frames.append(row)
 motion['clips']['contact_'+key]=frames
 report['clips'][key]={'source':name,'frames':len(frames),'fps':c['fps']}
motion_path.write_text(json.dumps(motion,separators=(',',':')),encoding='utf8')
(ROOT/'art/hydra/combat-rig-calibration.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print('Calibrated original Hydra combat curves:',report['clips'])
