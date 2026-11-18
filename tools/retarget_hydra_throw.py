"""Add P1 concrete pickup / held pose / object throw; retain all existing clips."""
from export_encounter_source_motion import *

source=P1/'Hydra.p3d'; all_nodes=nodes(source); rig=skeleton(all_nodes,'hydra_skeleton')
def clip(name):
 return decoder.decode(next(n for n in all_nodes if n['type']==0x121000 and decoder.text8(n['payload'],4)[0]==name))
reference=world(rig,clip('hyd_act_idle'),0)
geo=json.loads((ASSETS/'geo/blacklight_hydra_mc.geo.json').read_text())['minecraft:geometry'][0]['bones']
pivots={b['name']:np.array([-b['pivot'][0],b['pivot'][1],b['pivot'][2]])/16 for b in geo}
mapping={'neck_0':'full_rig_body1','neck_1':'full_rig_body2','neck_2':'full_rig_body3','neck_3':'full_rig_body4','neck_4':'full_rig_body5','neck_5':'Neck_2','neck_6':'Neck_4','head':'Head','jaw':'Gum','pincer_left':'Lip_L','pincer_right':'Lip_R','claw_left':'Claw_L','claw_right':'Claw_R'}
aims={f'neck_{i}':f'neck_{i+1}' for i in range(6)}
aims.update({'neck_6':'head','head':'jaw','pincer_left':'claw_left','pincer_right':'claw_right'})
offsets={}; directions={}
for n in mapping:
 if n in aims:
  child=aims[n];directions[n]=unit(pivots[child]-pivots[n]);offsets[n]=rotation_between(directions[n],reference[mapping[child]][:3,3]-reference[mapping[n]][:3,3])
 else:offsets[n]=offsets['pincer_'+n.split('_')[1]] if n.startswith('claw_') else offsets['head']
def retarget(name):
 c=clip(name); result=[];last={}
 for t in range(int(c['frames'])):
  current=world(rig,c,t);rotations={};row=[]
  for bone in geo:
   n=bone['name'];parent=bone.get('parent');rotation=np.eye(3)
   if n in mapping:
    s=mapping[n];rotation=current[s][:3,:3]@reference[s][:3,:3].T@offsets[n]
    if n in aims:rotation=rotation_between(rotation@directions[n],current[mapping[aims[n]]][:3,3]-current[s][:3,3])@rotation
   rotations[n]=rotation;angles=np.array(euler(rotations[parent].T@rotation if parent else rotation))
   if n in last:
    alternate=np.array([angles[0]+math.pi,math.pi-angles[1],angles[2]+math.pi])
    angles=min([v+2*math.pi*np.round((last[n]-v)/(2*math.pi)) for v in [angles,alternate]],key=lambda v:np.linalg.norm(v-last[n]))
   last[n]=angles;row.append([round(float(v),7) for v in angles]+[0,0,0])
  result.append(row)
 return result
opened=np.array(retarget('hyd_obj_throw'));closed=np.array(retarget('hyd_obj_throw_closed'))
# Original concrete value 35 selects the <40 branch: open .3 / closed .7.
# Local shortest-angle interpolation is the documented MC retarget adaptation.
delta=opened-closed;delta[:,:,:3]=np.arctan2(np.sin(delta[:,:,:3]),np.cos(delta[:,:,:3]))
mixed=closed+.3*delta
new={'combat_pickup':retarget('hyd_grab_concrete'),'combat_hold':mixed[12:86].tolist(),'combat_throw':mixed[90:156].tolist()}
path=ASSETS/'models/hydra_motion.json';motion=json.loads(path.read_text());motion['clips'].update(new)
path.write_text(json.dumps(motion,separators=(',',':')),encoding='utf8')
# Gecko holds metadata clips; actual bones use the shared authoritative motion sampler.
path=ASSETS/'animations/blacklight_hydra.animation.json';animation=json.loads(path.read_text())
for name,frames in new.items():animation['animations'][name]={'loop':name=='combat_hold','animation_length':(len(frames)-1)/30,'bones':{}}
path.write_text(json.dumps(animation,separators=(',',':')),encoding='utf8')
report={'source':str(source),'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'fps':30,'mapping':mapping,'clips':{k:len(v) for k,v in new.items()},'adaptation':'Existing fixed-length MC combat rig. Local shortest-angle blend .3 open / .7 closed. Pickup 0..119; hold 12..85; throw 90..155. Existing combat, transport and emergence clips untouched.'}
(ROOT/'art/hydra/throw-rig-calibration.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(report['clips'])
