"""Author native Epic Fight poses from observed Prototype silhouettes.
Quaternions in ATTRIBUTES assets are w,x,y,z; angles below are author-space degrees.
Keep locomotion physics and combat's server timing separate from visual Root motion.
"""
import json, math
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT/'src/main/resources/assets/blacklight/native_animations/biped'
JOINTS = ['Root','Torso','Chest','Head','Shoulder_R','Arm_R','Elbow_R','Hand_R','Tool_R',
          'Shoulder_L','Arm_L','Elbow_L','Hand_L','Tool_L','Thigh_R','Leg_R','Knee_R','Thigh_L','Leg_L','Knee_L']
def quat(x=0,y=0,z=0):
    x,y,z=[math.radians(v)*.5 for v in (x,y,z)]
    cx,cy,cz=map(math.cos,(x,y,z)); sx,sy,sz=map(math.sin,(x,y,z))
    q=[cx*cy*cz-sx*sy*sz,sx*cy*cz+cx*sy*sz,cx*sy*cz-sx*cy*sz,cx*cy*sz+sx*sy*cz]
    return [round(v,7) if abs(v)>1e-8 else 0 for v in q]
def frame(rot=(0,0,0),loc=(0,0,0)):
    return {'loc':list(loc),'rot':quat(*rot),'sca':[1,1,1]}
def joint_frame(j,rot,loc=(0,0,0)):
    # In-game calibration: right positive arm roll folds inward in this armature.
    # Designer-positive roll is outward, so invert it only for the upper-arm bones.
    if j.startswith('Arm_'): rot=(rot[0],rot[1],-rot[2])
    return frame(rot,loc)
def pose(**kw): return kw
def stance():
    return dict(Torso=(-9,0,0),Chest=(-3,0,0),Head=(9,0,0),Arm_R=(8,-5,15),Arm_L=(8,5,-15),
                Hand_R=(12,0,0),Hand_L=(12,0,0),Thigh_R=(-10,0,-3),Thigh_L=(-8,0,3),
                Leg_R=(16,0,0),Leg_L=(14,0,0),Knee_R=(-6,0,0),Knee_L=(-6,0,0))
def merged(base,**kw): return base|kw
def write(name,times,poses,heights=None):
    heights=heights or [0]*len(times)
    data={'format':'ATTRIBUTES','animation':[]}
    for j in JOINTS:
        frames=[joint_frame(j,p.get(j,(0,0,0)),(0,heights[i],0) if j=='Root' else (0,0,0)) for i,p in enumerate(poses)]
        data['animation'].append({'name':j,'time':times,'transform':frames})
    save(name,data)
def save(name,data):
    if name.startswith('living/landing_') or name == 'combat/claw_groundspike':
        tracks={t['name']:t for t in data['animation']}
        # Plant the feet using the actual two 0.37472-block leg segments. Lowering
        # Root independently of knee flexion buries the lower body in the terrain.
        for i,f in enumerate(tracks['Root']['transform']):
            thigh=tracks['Thigh_R']['transform'][i]['rot'];leg=tracks['Leg_R']['transform'][i]['rot']
            a=2*math.atan2(thigh[1],thigh[0]);b=2*math.atan2(leg[1],leg[0])
            f['loc'][1]=round(.37472*(math.cos(a)+math.cos(a+b)-2),6)
    # Normalize edited and legacy rounded quaternions, then choose a continuous hemisphere.
    for tr in data['animation']:
        prev=None
        for f in tr['transform']:
            q=f['rot']; n=math.sqrt(sum(v*v for v in q));q=[v/n for v in q]
            if prev and sum(a*b for a,b in zip(prev,q))<0:q=[-v for v in q]
            f['rot']=[int(v) if v in (0,1,-1) else round(v,7) for v in q];prev=q
    (ASSETS/(name+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def load(name):return json.loads((ASSETS/(name+'.json')).read_text())
def replace_track(data,j,times,rotations):
    track=next((t for t in data['animation'] if t['name']==j),None)
    if track is None:track={'name':j};data['animation'].append(track)
    track['time']=times;track['transform']=[joint_frame(j,r) for r in rotations]

# Right diagonal tear, left return tear, committed finishing rake. Wind-up is slower
# than the crossing stroke; contact is exactly at each server startup boundary.
rest=stance()
for index,(end,wind,contact,follow,recover) in enumerate([
        (.7,.14,.2,.32,.50),(.8,.18,.25,.42,.58),(1.1,.26,.35,.56,.82)],1):
    left=index==2; a='L' if left else 'R'; b='R' if left else 'L'; side=-1 if left else 1
    heavy=index==3
    windup=merged(rest,Torso=(-7,24*side,-5*side),Chest=(-3,12*side,0),Head=(8,-18*side,0))
    windup.update({f'Arm_{a}':(-52 if not heavy else -86,-24*side,38*side),
                   f'Hand_{a}':(58,0,0),f'Arm_{b}':(25,12*side,-20*side),f'Hand_{b}':(32,0,0)})
    hit=merged(rest,Torso=(-21 if not heavy else -28,-23*side,6*side),Chest=(-7,-13*side,0),Head=(19,18*side,0))
    hit.update({f'Arm_{a}':(76 if not heavy else 104,28*side,-17*side),f'Hand_{a}':(12,0,0),
                f'Arm_{b}':(-24,-8*side,-28*side),f'Hand_{b}':(25,0,0)})
    through=merged(hit,Torso=(-24,-35*side,7*side),Chest=(-9,-16*side,0),Head=(21,25*side,0))
    through.update({f'Arm_{a}':(44,44*side,-35*side),f'Hand_{a}':(34,0,0)})
    recovery=merged(rest,Torso=(-13,-10*side,0),Chest=(-5,-6*side,0))
    recovery.update({f'Arm_{a}':(18,12*side,8*side),f'Hand_{a}':(24,0,0)})
    write(f'combat/claw_auto{index}',[0,wind,contact,follow,recover,end],
          [rest,windup,hit,through,recovery,rest],[0,-.025,-.065,-.075,-.025,0])

# Low shoulder-first passing slash. Arms separate during recovery instead of
# returning immediately to a symmetrical dagger guard.
for name,end,contact in [('claw_sprint_attack',.8,.1),('claw_dashing_slice',1.,.15)]:
    ready=merged(rest,Torso=(-28,12,0),Head=(24,-8,0),Arm_R=(-38,-20,28),Hand_R=(40,0,0),Arm_L=(-18,0,-25))
    hit=merged(rest,Torso=(-38,-22,6),Chest=(-10,-10,0),Head=(32,18,0),
               Arm_R=(90,24,-20),Hand_R=(8,0,0),Arm_L=(-42,-8,-30),Hand_L=(20,0,0))
    follow=merged(hit,Torso=(-30,-36,5),Arm_R=(48,45,-34),Hand_R=(22,0,0))
    write('combat/'+name,[0,contact*.55,contact,contact+.17,end*.72,end],
          [rest,ready,hit,follow,merged(rest,Arm_R=(20,15,4)),rest],[0,-.04,-.10,-.08,-.025,0])

# Idle breath affects chest/shoulders; the root does not tip the entire player.
breath=merged(rest,Torso=(-10,2,0),Chest=(-5,-1,0),Head=(10,-1,0),Arm_R=(10,-5,17),Arm_L=(9,5,-16))
write('living/claw_idle',[0,.8,1.6],[rest,breath,rest],[0,-.01,0])

# Keep the established foot phases and speed-aware locomotion; re-author the upper
# body and elbow chains for oversized claws. Cycles have matching first/last poses.
for name,arm_r,hand_r,spread in [
    ('claw_walk',[24,8,-22,-6,24],[25,18,12,20,25],18),
    ('claw_sprint',[-10,-26,-46,-30,-10],[26,20,12,20,26],24),
    ('wall_run',[65,26,-32,-4,65],[35,54,72,46,35],19)]:
    d=load('living/'+name);times=d['animation'][0]['time']
    for s,sign,shift in [('R',1,0),('L',-1,2)]:
        cyc=arm_r[:4];bends=hand_r[:4]
        values=[cyc[(i+shift)%4] for i in range(5)]
        elbows=[bends[(i+shift)%4] for i in range(5)]
        replace_track(d,'Arm_'+s,times,[(v,sign*4,sign*spread) for v in values])
        replace_track(d,'Hand_'+s,times,[(v,0,0) for v in elbows])
        replace_track(d,'Elbow_'+s,times,[(v*.5,0,0) for v in elbows])
        replace_track(d,'Shoulder_'+s,times,[(v*.12,0,sign*3) for v in values])
    save('living/'+name,d)

# Charge keeps the user's established crouch and release timing, but bends the
# actual forearm joints during pullback. Groundspike preserves its planted hold.
for name in ['living/prototype_jump_charge','combat/claw_groundspike']:
    d=load(name)
    if 'jump' in name:
        for s in ['R','L']:
            replace_track(d,'Hand_'+s,[0,.68,1.36,2.05],[(0,0,0),(10,0,0),(17,0,0),(22,0,0)])
            replace_track(d,'Elbow_'+s,[0,.68,1.36,2.05],[(0,0,0),(5,0,0),(8,0,0),(11,0,0)])
    else:
        for s in ['R','L']:
            times=[0,.24,.48,.55,1.6,1.9,2.2]
            replace_track(d,'Thigh_'+s,times,[(0,0,0),(-8,0,0),(-52,0,0),(-62,0,0),(-62,0,0),(-20,0,0),(0,0,0)])
            replace_track(d,'Leg_'+s,times,[(0,0,0),(14,0,0),(96,0,0),(118,0,0),(118,0,0),(36,0,0),(0,0,0)])
            replace_track(d,'Knee_'+s,times,[(0,0,0),(-6,0,0),(-44,0,0),(-56,0,0),(-56,0,0),(-16,0,0),(0,0,0)])
        # Free arm balances the one-handed plant. Keep plant/reverse key times intact.
        replace_track(d,'Arm_L',[0,.24,.48,.55,1.6,1.9,2.2],
                      [(8,5,-18),(18,8,-28),(-28,-8,-34),(-32,-8,-36),(-32,-8,-36),(-8,0,-22),(8,5,-18)])
        replace_track(d,'Hand_L',[0,.24,.48,.55,1.6,1.9,2.2],
                      [(12,0,0),(26,0,0),(18,0,0),(14,0,0),(14,0,0),(20,0,0),(12,0,0)])
    save(name,d)

# Launch extends from the crouch; descent opens the arms and lowers the legs.
rise=merged(rest,Torso=(-15,0,0),Head=(15,0,0),Arm_R=(-42,0,25),Arm_L=(-42,0,-25),
            Hand_R=(18,0,0),Hand_L=(18,0,0),Thigh_R=(-28,0,-5),Thigh_L=(-15,0,5),Leg_R=(55,0,0),Leg_L=(40,0,0))
rise_open=merged(rise,Arm_R=(-5,0,52),Arm_L=(-5,0,-52),Thigh_R=(-14,0,-5),Thigh_L=(5,0,5),Leg_R=(30,0,0),Leg_L=(22,0,0))
fall=merged(rest,Torso=(-10,0,0),Head=(12,0,0),Arm_R=(12,0,62),Arm_L=(12,0,-62),
            Hand_R=(22,0,0),Hand_L=(22,0,0),Thigh_R=(-12,0,-8),Thigh_L=(-12,0,8),Leg_R=(24,0,0),Leg_L=(24,0,0))
write('living/prototype_jump_rise',[0,.09,.2],[rise,rise_open,rise_open])
write('living/prototype_jump_fall',[0,.12,.2],[rise_open,fall,fall])
glide=merged(fall,Torso=(-24,0,0),Chest=(-8,0,0),Head=(25,0,0),Arm_R=(-18,-12,68),Arm_L=(-18,12,-68),
             Hand_R=(12,0,0),Hand_L=(12,0,0),Thigh_R=(12,0,-6),Thigh_L=(12,0,6),Leg_R=(18,0,0),Leg_L=(18,0,0))
write('living/prototype_glide',[0,.35,.7],[glide,merged(glide,Arm_R=(-20,-12,71),Arm_L=(-20,12,-71)),glide])
dash=merged(rise,Torso=(-42,0,0),Chest=(-8,0,0),Head=(35,0,0),Arm_R=(-62,0,24),Arm_L=(-62,0,-24),
            Thigh_R=(-32,0,0),Thigh_L=(-18,0,0),Leg_R=(66,0,0),Leg_L=(50,0,0))
write('action/air_dash',[0,.06,.15,.32,.45,.6],[rise_open,merged(rise,Hand_R=(45,0,0),Hand_L=(45,0,0)),dash,dash,rise_open,fall])

# Biologically weighted morph: lift and inspect hands, tense, let the mass settle.
gather=merged(rest,Torso=(-6,0,0),Arm_R=(48,-18,28),Arm_L=(44,18,-28),Hand_R=(58,0,0),Hand_L=(54,0,0),Head=(2,0,0))
burst=merged(rest,Torso=(-16,0,0),Chest=(-7,0,0),Arm_R=(12,-10,38),Arm_L=(8,10,-38),Hand_R=(24,0,0),Hand_L=(20,0,0))
write('action/transform_claws',[0,.2,.45,.64,.84,1.2],[rest,gather,gather,burst,merged(rest,Arm_R=(5,-5,23),Arm_L=(5,5,-23)),rest],
      [0,-.01,-.02,-.045,-.02,0])
grab=merged(rest,Torso=(-16,0,0),Arm_R=(75,-18,5),Arm_L=(64,18,-5),Hand_R=(15,0,0),Hand_L=(25,0,0))
pull=merged(rest,Torso=(-6,10,0),Arm_R=(32,-20,20),Arm_L=(25,15,-20),Hand_R=(96,0,0),Hand_L=(90,0,0),Head=(5,-10,0))
absorb=merged(pull,Torso=(-18,0,0),Chest=(-8,0,0),Head=(14,0,0),Hand_R=(105,0,0),Hand_L=(100,0,0))
write('action/consume',[0,.25,.6,.9,1.25,1.5,1.85,2.3],[rest,grab,grab,pull,absorb,absorb,burst,rest],
      [0,-.025,-.04,-.03,-.07,-.07,-.03,0])

# Landings retain their exact durations and zero horizontal Root displacement.
for name,end,pitch,depth in [('landing_light',.28,15,.07),('landing_heavy',.44,28,.17),('landing_devastating',.62,38,.25)]:
    contact=merged(rest,Torso=(-pitch,0,0),Chest=(-pitch*.2,0,0),Head=(pitch*.8,0,0),
                   Arm_R=(pitch*.8,0,20),Arm_L=(pitch*.8,0,-20),Hand_R=(24,0,0),Hand_L=(24,0,0),
                   Thigh_R=(-pitch,0,-4),Thigh_L=(-pitch,0,4),Leg_R=(pitch*1.8,0,0),Leg_L=(pitch*1.8,0,0),
                   Knee_R=(-pitch*.8,0,0),Knee_L=(-pitch*.8,0,0))
    settle=merged(contact,Torso=(-pitch*.65,0,0),Arm_R=(-8,0,25),Arm_L=(-8,0,-25))
    write('living/'+name,[0,round(end*.18,4),round(end*.43,4),round(end*.72,4),end],
          [fall,contact,contact,settle,rest],[0,-depth,-depth,-depth*.45,0])

print('Authored all 20 native clips; retained server durations and established movement foot cycles.')
