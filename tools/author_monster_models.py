"""Individually authored monster anatomy, in the existing Hunter block style.
P1/P2 extracted previews supply proportions only; no source mesh/UV/texture.
"""
import json
from encounter_cuboid_style import Model,ROOT,ASSETS,sha
SIDES=[('right',-1),('left',1)]

def jaw(m,y,z,w=5,h=4,skull=4.8,opening=.5):
 m.bone('head','torso',[0,y,z+1.8]);m.bone('jaw','head',[0,y-1.4,z+.7])
 m.box('cranial_vault','head',[0,y+1.25,z+.5],[w+1,skull,5.7],'skin',[-10,0,0])
 m.box('maw_shadow','head',[0,y-.7-opening*.5,z-2.45],[w-.5,h+opening,.65],'dark')
 for s in [-1,1]:
  m.box('brow_ridge_'+str(s),'head',[s*w*.26,y+1.45,z-1.9],[w*.62,1,2.2],'fold',[-13,0,s*12])
  m.box('jaw_hinge_'+str(s),'head',[s*w*.52,y-.7-opening*.4,z-1.15],[1.1,h+opening,2.2],'skin',[0,s*8,s*9])
  m.box('muzzle_'+str(s),'head',[s*w*.23,y+.6,z-2.5],[w*.4,1.3,1.6],'fold',[8,0,s*-8])
 m.box('mandible','jaw',[0,y-h*.55-opening,z-1.2],[w+.5,1.35,3.5],'fold',[-8,0,0])
 m.box('lower_gum','jaw',[0,y-h*.55+.55-opening,z-2.4],[w-.3,.42,1],'scar')
 for i in range(6):
  x=(i-2.5)*w/7
  m.box('upper_tooth_'+str(i),'head',[x,y+.02,z-3.05],[min(.38,w*.085),h*.33 if i in (0,5) else h*.14,.52],'bone',[7,0,x*3])
  m.box('lower_tooth_'+str(i),'jaw',[x,y-h*.55+1.0-opening,z-2.85],[min(.33,w*.075),h*.14,.48],'bone',[-12,0,-x*3])

def limb(m,side,shoulder,elbow,wrist,upper,fore,mat='skin'):
 m.bone(side+'_arm','torso',shoulder)
 m.bone(side+'_forearm',side+'_arm',elbow)
 m.bone(side+'_hand',side+'_forearm',wrist)
 m.beam(side+'_humerus',side+'_arm',shoulder,elbow,upper,upper*1.15,mat)
 m.beam(side+'_radius',side+'_forearm',elbow,wrist,fore,fore*1.12,mat)
 m.box(side+'_elbow',side+'_forearm',elbow,[fore*1.1,fore*.7,fore*1.25],'fold')

def leg(m,side,hip,knee,ankle,w,mat='skin',toe=1):
 s=-1 if side=='right' else 1
 m.bone(side+'_leg','root',hip);m.bone(side+'_shin',side+'_leg',knee)
 m.beam(side+'_thigh',side+'_leg',hip,knee,w,w*1.25,mat)
 middle=[(hip[i]+knee[i])/2 for i in range(3)]
 m.box(side+'_quadriceps',side+'_leg',[middle[0]+s*.4,middle[1]+.4,middle[2]-.7],[w*.84,w*1.35,w*.9],mat,[0,0,s*-8])
 m.beam(side+'_tibia',side+'_shin',knee,ankle,w*.6,w*.72,'fold')
 m.box(side+'_kneecap',side+'_shin',[knee[0],knee[1],knee[2]-w*.3],[w*.72,w*.62,w*.5],'plate',[-12,0,0])
 m.box(side+'_foot',side+'_shin',[ankle[0],1.0,ankle[2]-1.2],[w*.85,1.8,4.8],'fold')
 for i in range(3):m.box(side+'_toe_'+str(i),side+'_shin',[ankle[0]+(i-1)*w*.23,.65,ankle[2]-3.55],[w*.2,.85,1.65],'bone' if toe else 'dark',[8,0,(i-1)*7])

def leader():
 m=Model('leader_hunter',3.2,'P1 LeaderHunter/source-reference.png')
 m.colors.update(skin=(100,91,86),fold=(67,59,56),plate=(87,79,72),scar=(102,61,58),bone=(147,130,108))
 m.bone('torso','root',[0,17,8])
 m.box('ribcage','torso',[0,21,1.5],[13,8.6,15.8],'skin',[-12,0,0])
 m.box('rear_trunk','torso',[0,18.2,9],[9.8,7.4,10],'fold',[-10,0,0])
 m.box('lumbar_back','torso',[0,22,7.5],[8.7,3.3,10],'skin',[-16,0,0])
 for side,s in SIDES:
  m.box(side+'_pectoral','torso',[s*3.6,21.1,-6],[6.6,4.1,2.8],'skin',[10,s*8,s*8])
  m.box(side+'_scapula','torso',[s*4.4,24.3,3.5],[6.5,3.2,10],'skin',[-14,s*8,s*-12])
  m.box(side+'_lat','torso',[s*5.7,19,3.2],[2.8,5.5,8],'skin',[-12,0,s*17])
  for i in range(3):m.box(side+'_abdominal_'+str(i),'torso',[s*2.5,17.8-i*1.25,-5.9+i*.7],[4.5,1.25,1.6],'fold',[6,0,s*7])
  sh=[s*7.8,23.5,-1.3];el=[s*10.4,13.8,-4.6];wr=[s*11.4,4.0,-9.1]
  limb(m,side,sh,el,wr,4.7,3.5)
  m.box(side+'_deltoid',side+'_arm',[s*8,22.6,-1.9],[6.2,6.0,6.8],'skin',[0,0,s*14])
  m.beam(side+'_biceps',side+'_arm',[s*8.4,21.5,-4.3],[s*10.4,15.7,-6.1],3.1,1.6,'skin')
  m.beam(side+'_forearm_ridge',side+'_forearm',[s*10.6,12.2,-6.4],[s*11.4,5,-10.8],1.0,.65,'skin')
  m.box(side+'_palm',side+'_hand',[wr[0],2.7,-10.6],[5.2,2.4,5],'fold',[-5,0,0])
  m.box(side+'_wrist',side+'_hand',[wr[0],3.9,-9.4],[3.5,1.5,3.7],'fold',[-8,0,0])
  for i in range(4):
   x=wr[0]+(i-1.5)*1.2;n=side+'_finger_'+str(i);m.bone(n,side+'_hand',[x,2.8,-11.8])
   m.box(n+'_knuckle',n,[x,2.65,-12],[1,1.2,1.6],'skin',[-8,0,0])
   m.beam(n+'_digit',n,[x,2.3,-12.4],[x+s*.18,1.1,-14.6],.87,1,'fold')
   m.box(n+'_claw',n,[x+s*.2,.8,-15.3],[.6,.7,1.7],'dark',[14,0,0])
  leg(m,side,[s*4,17,9.4],[s*6.6,8.5,6.3],[s*7.6,2,12],4.5,toe=0)
 jaw(m,26.3,-8.5,4.8,4.3,4.3,.65)
 m.box('sagittal_ridge','head',[0,29.3,-7.5],[1.5,.85,4.3],'fold',[-12,0,0])
 m.box('upper_muzzle','head',[0,27.3,-11.2],[2.2,1.25,1.1],'skin',[-15,0,0])
 for s in [-1,1]:m.box('nostril_'+str(s),'head',[s*.65,27.1,-11.85],[.4,.38,.2],'dark')
 m.features=['Long low ape torso, pronounced scapulae and forward knuckles','Separate deltoids, biceps, forearm ridge, bent rear legs and jaw','Grey Hunter hide at the accepted material texel scale; no beveled frame boxes']
 return m

def juggernaut():
 m=Model('juggernaut',3.6,'P2 infected3_hammerer/source-reference.png')
 m.colors.update(skin=(111,81,75),plate=(75,65,58),fold=(63,44,42),bone=(142,125,99),scar=(129,82,78))
 m.bone('torso','root',[0,13,1])
 m.box('upper_ribcage','torso',[0,21.7,1],[17.4,8,11.6],'fold',[-8,0,0])
 m.box('abdomen','torso',[0,12.5,-1.8],[13.2,9.8,11.8],'skin')
 m.box('lower_belly','torso',[0,8.5,-1.6],[11.6,3.2,9.2],'skin',[7,0,0])
 m.box('upper_belly','torso',[0,16.4,-1.1],[14.4,4.6,11.4],'skin',[-9,0,0])
 for side,s in SIDES:
  m.box(side+'_flank','torso',[s*6.3,12.7,-1.5],[3.2,8.4,9.7],'skin',[0,0,s*16])
  m.box(side+'_pectoral','torso',[s*4.5,22.2,-4.65],[8.5,5.3,4],'scar',[13,s*7,s*25])
  m.beam(side+'_abdominal_fold','torso',[s*1.1,17.6,-7.3],[s*6.3,15.2,-6.6],1.45,1.2,'fold')
  m.beam(side+'_clavicle','torso',[s*.8,25,-4.3],[s*6.8,23.1,-5.7],.85,.7,'skin')
  sh=[s*10,23.1,.4];el=[s*12.6,15.3,1.3];wr=[s*13.8,6.9,-2.1]
  limb(m,side,sh,el,wr,5.1,5.9,'fold')
  m.box(side+'_shoulder_growth',side+'_arm',[s*10.1,23.3,1.1],[6.9,5.6,7.4],'plate',[0,0,s*23])
  m.beam(side+'_upper_tendon',side+'_arm',[s*10.9,24.2,-3.1],[s*12.9,16.5,-2.1],.85,.7,'muscle')
  m.box(side+'_fist_core',side+'_hand',[wr[0],6.3,-2.9],[7.1,6.9,6.8],'fold',[0,0,s*6])
  for i in range(3):
   x=wr[0]+(i-1)*2.3
   m.box(side+'_knuckle_plate_'+str(i),side+'_hand',[x,7.4+(i%2)*.8,-6.1],[2.8,4.5,2.5],'plate',[12,(i-1)*13,(i-1)*10])
  m.box(side+'_forearm_plate',side+'_forearm',[s*13.8,12,-2.7],[6.5,5.1,6.4],'plate',[0,0,s*16])
  m.box(side+'_ulna_growth',side+'_forearm',[s*15.5,15,1.3],[2.8,4.4,3.1],'plate',[15,0,s*24])
  for i in range(4):
   x=wr[0]+(i-1.5)*1.5;n=side+'_finger_'+str(i);m.bone(n,side+'_hand',[x,4,-4])
   m.box(n+'_first',n,[x,3.7,-4.3],[1.35,2.4,2.0],'fold',[18,0,0])
   m.box(n+'_hook',n,[x,2.15,-5.25],[.75,1.7,1.0],'dark',[30,0,0])
  leg(m,side,[s*4.8,10.8,1.0],[s*5.9,5.8,-.9],[s*5.5,1.8,1.1],4.6,toe=0)
 for i in range(4):
  m.box('dorsal_growth_'+str(i),'torso',[(-1)**i*.6,18.2+i*3.1,6.2],[6.7-i*.7,4.3,4.2],'plate',[18+i*5,0,(-1)**i*12])
 jaw(m,27.9,-4.7,4.0,4.9,3.5,.95)
 m.features=['Protruding layered belly, oblique chest folds and short weight-bearing legs','Irregular individual fist plates, curled claws and dorsal growths','Project Hunter skin/muscle materials; no flat belly or fist frames']
 return m

def goliath():
 m=Model('goliath',8,'P2 behemoth/source-reference.png')
 m.colors.update(skin=(117,87,75),fold=(80,53,46),plate=(53,47,42),bone=(148,132,107),muscle=(131,43,36))
 m.bone('torso','root',[0,14,1])
 m.box('upper_ribcage','torso',[0,23,1],[16.4,8.3,11.8],'skin',[-8,0,0])
 m.box('lower_ribcage','torso',[0,18.3,1],[11.8,6.1,9.5],'fold')
 m.box('waist','torso',[0,13.6,.8],[7.9,4.3,7.1],'muscle')
 for side,s in SIDES:
  m.box(side+'_pectoral','torso',[s*4.8,25,-4.45],[9.5,5.6,4.5],'skin',[8,s*8,s*13])
  m.box(side+'_clavicle','torso',[s*5,28,.1],[9.7,3,7.8],'skin',[-10,0,s*9])
  m.box(side+'_lat','torso',[s*6.3,20,2],[3.1,7.4,7.8],'skin',[0,0,s*-14])
  m.box(side+'_scapula','torso',[s*4.5,24.6,6.5],[7.4,5.5,2.1],'fold',[8,0,s*12])
  for i in range(3):m.box(side+'_abdominal_'+str(i),'torso',[s*(2.4-i*.2),19.7-i*2.2,-4.6+i*.25],[4.3-i*.4,2.25,2.0],'skin',[5,0,s*-7])
  m.beam(side+'_pectoral_sinew','torso',[s*1.2,22.9,-6.7],[s*8,25.5,-6.2],.5,.45,'muscle')
  big=side=='right';sh=[s*10.5,25.5,.2];el=[s*(15.1 if big else 14.6),18,0];wr=[s*(17 if big else 17.1),8.7,-3.2]
  limb(m,side,sh,el,wr,7.1 if big else 4.2,7.8 if big else 3.1,'muscle')
  m.box(side+'_deltoid',side+'_arm',[s*11.2,24.4,.3],[8.6 if big else 5.3,6.4,8.8 if big else 6.0],'skin',[0,0,s*22])
  m.beam(side+'_biceps',side+'_arm',[s*12.2,23,-2.7],[s*14.7,18.5,-3],3.7 if big else 2.1,2,'muscle')
  if big:
   m.box('giant_fist_palm',side+'_hand',[s*17,7.6,-3.3],[10.5,9.4,9.4],'dark',[0,0,-6])
   m.box('giant_fist_dorsal',side+'_hand',[s*17,11.3,-2.4],[10.8,4.1,9.2],'plate',[0,-12,12])
   for i in range(4):
    x=s*17+(i-1.5)*2.5;n=side+'_finger_'+str(i);m.bone(n,side+'_hand',[x,8,-6])
    m.box(n+'_knuckle',n,[x,8.3+(i%2)*.6,-7.4],[3.0,5.8,4.1],'plate',[12,(i-1.5)*8,(i-1.5)*6])
    m.box(n+'_curl',n,[x,4.8,-6.7],[2.6,3.1,3.7],'plate',[-20,0,0])
   m.box('giant_thumb',side+'_hand',[s*11.6,7.9,-3],[3.9,6.3,5.1],'plate',[0,-15,-22])
   m.box('giant_wrist_plate',side+'_forearm',[s*16.4,14.2,-2.5],[8.7,4.6,8.9],'plate',[0,0,-19])
   m.beam('giant_fist_split',side+'_hand',[s*17,4,-8],[s*16.4,11,-8],.5,.45,'muscle')
  else:
   m.box('small_hand',side+'_hand',[wr[0],7.7,-4],[4.5,3.5,4.6],'fold')
   for i in range(4):
    x=wr[0]+(i-1.5)*1.15;n=side+'_finger_'+str(i);m.bone(n,side+'_hand',[x,7,-5.3])
    m.beam(n+'_first',n,[x,7,-5.3],[x+s*.3,3.8,-7.3],.8,1.1,'bone')
    m.beam(n+'_tip',n,[x+s*.3,3.8,-7.3],[x+s*.6,1.5,-9.1],.42,.62,'bone')
  leg(m,side,[s*3.6,12.8,.4],[s*3.6,6.8,-.2],[s*3.6,1.8,1.3],3.65,'skin',toe=0)
  idx=0 if side=='right' else 1;b='weak_'+str(idx);m.bone(b,side+'_leg',[s*3.6,.75,-1.8])
  # Server weakpoint volume is unchanged; visible armor no longer fills that
  # entire hitbox with an oversized opaque box hiding the feet and knee.
  m.box(side+'_shin_armor',b,[s*3.6,4.35,-2.1],[3.8,4.6,1.7],'plate',[-8,0,0])
  m.box(side+'_shin_armor_side',b,[s*4.85,4.1,-.8],[1.2,4.2,2.2],'plate',[0,0,s*12])
  m.box(side+'_shin_tissue',b,[s*3.6,4.2,-3.0],[.65,3.8,.3],'muscle')
 jaw(m,28.4,-4.9,2.6,3.1,2.5,.25)
 m.features=['Massive layered chest over narrow waist and visible articulated legs','Asymmetric plated right fist with separate knuckles/thumb and opposite long claws','Small high head; shin armor follows anatomy while server weakpoint centers remain unchanged']
 return m

def flyer():
 m=Model('flyer',1.2,'P2 flyer/source-reference.png');m.scale=1
 m.colors.update(skin=(94,70,65),fold=(48,36,36),plate=(49,44,42),bone=(151,140,117),dark=(25,22,23))
 m.bone('torso','root',[0,10,1]);m.bone('head','torso',[0,10,-5]);m.bone('jaw','head',[0,7,-7]);m.bone('tail','torso',[0,9,6])
 m.box('thorax','torso',[0,11,1],[6.1,5.2,10.5],'skin',[-18,0,0])
 m.box('breastbone','torso',[0,8.5,-1.8],[2,3.9,7.4],'bone',[-20,0,0])
 m.box('dorsal_plate','torso',[0,13.5,2.3],[5.6,2.4,9.4],'fold',[-15,0,0])
 m.box('skull','head',[0,8.6,-7.4],[3.4,3.9,5.8],'bone',[-22,0,0])
 m.box('beak_root','head',[0,6.5,-10.4],[2.5,2.3,3.8],'bone',[-26,0,0])
 m.box('beak_hook','head',[0,4.7,-12.1],[1.25,2.8,1.8],'bone',[-24,0,0])
 m.box('beak_tip','head',[0,3.2,-12.2],[.6,1.4,1.2],'dark',[12,0,0])
 m.box('lower_beak','jaw',[0,5.8,-9.2],[1.4,1.1,4.5],'fold',[-20,0,0])
 for side,s in SIDES:
  m.box(side+'_eye_socket','head',[s*1.6,8.7,-8.9],[.5,1.0,1.7],'dark',[0,s*13,0])
  m.bone(side+'_arm','torso',[s*2.8,12,.1]);m.bone(side+'_leg','torso',[s*2.2,7,4.5])
  m.beam(side+'_humerus',side+'_arm',[s*2.8,12,.1],[s*12.7,16.1,.8],2.2,2.3,'muscle')
  m.beam(side+'_radius',side+'_arm',[s*12.7,16.1,.8],[s*20.4,14.5,3.3],1.65,1.9,'bone')
  m.box(side+'_upper_membrane',side+'_arm',[s*10.5,11.8,4.9],[13.7,.45,8.4],'fold',[-6,s*13,s*8])
  m.box(side+'_outer_membrane',side+'_arm',[s*22,11.2,8],[11.8,.4,8.7],'fold',[6,s*25,s*-5])
  for i in range(5):
   a=[s*(15.5+i*1.4),14-i*.25,1.8+i*.45];bend=[s*(25.2+i*2.8),12-i*.45,8+i*.65];tip=[s*(27.5+i*3.2),10-i*.45,17.5-i*2.15]
   m.beam(side+'_wing_digit_'+str(i),side+'_arm',a,bend,1.0-i*.08,1.25-i*.1,'plate')
   m.beam(side+'_wing_tip_'+str(i),side+'_arm',bend,tip,.55-i*.06,.7-i*.07,'dark')
   if i<3:m.box(side+'_trailing_membrane_'+str(i),side+'_arm',[s*(26+i*3),10.7-i*.35,11.4-i*.7],[5.5,.35,8-i*1.5],'fold',[8,s*(20+i*7),s*-6])
  m.beam(side+'_femur',side+'_leg',[s*2.2,7,4.5],[s*3,3.7,6.2],1.25,1.5,'fold')
  m.beam(side+'_ankle',side+'_leg',[s*3,3.7,6.2],[s*3.1,1.8,4.8],.7,.9,'bone')
  for i in range(3):m.beam(side+'_talon_'+str(i),side+'_leg',[s*3.1+(i-1)*.65,1.8,4.8],[s*3.1+(i-1)*.9,.7,2.7],.38,.5,'bone')
  for i in range(3):m.box(side+'_rib_'+str(i),'torso',[s*2.7,10.2-i*.7,-.8+i*1.1],[.55,.5,3.1],'bone',[0,s*17,s*22])
 for i in range(3):m.beam('tail_'+str(i),'tail',[(i-1)*.8,9,6],[(i-1)*1.8,6.5,14],.85,.95,'plate')
 m.features=['Pale hooked skull, compact ribcage and articulated trailing talons','Continuous stepped patagium joins five tapering wing fingers','Thin membrane cuboids and bone spars use the accepted pixel material scale']
 return m

audit=[make().export() for make in (leader,juggernaut,goliath,flyer)]
record={'revision':'supreme-style-authored-r6','method':'Named anatomy authored individually from supplied P1/P2 silhouette references. No uniform bevel shells, voxelization or source meshes/textures.',
 'styleReference':'Existing accepted Hunter and Supreme Hunter geometry/material scale',
 'styleBaselines':[{'file':p,'sha256':sha(ASSETS/p)} for p in ('textures/entity/hunter_mc.png','textures/entity/supreme_hunter_block.png')],
 'models':audit,'reviewStatus':'Awaiting in-game multi-view and action review'}
(ROOT/'art/encounters/monster-style-provenance.json').write_text(json.dumps(record,indent=2)+'\n')
mf=ROOT/'art/encounters/manifest.json';manifest=json.loads(mf.read_text(encoding='utf8'))
ids={a['id'] for a in audit};manifest['modelAudit']=[m for m in manifest['modelAudit'] if m['id'] not in ids]+[{k:v for k,v in a.items() if k!='parts'} for a in audit]
manifest['monsterStyleRevision']=record['revision'];manifest['modelStatus']='Greene and four nonhumanoid monsters authored in the accepted Hunter cuboid/material style; visual review recorded separately. Human encounter drafts retain their prior status.'
mf.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps([{k:v for k,v in a.items() if k not in ('parts','features')} for a in audit]))
