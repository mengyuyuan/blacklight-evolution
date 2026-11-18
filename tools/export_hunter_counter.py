"""Append the decoded P1 Hunter counter opening to the existing authored rig.
CPU-only source-data export. Does not compile, launch a client, or run tests.
Reuses the current Hunter calibration, leaves every other clip untouched.
"""
import importlib.util
import json
import math
from pathlib import Path
import sys
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('source_motion',ROOT/'tools/export_encounter_source_motion.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
ASSETS=ROOT/'src/main/resources/assets/blacklight'
SOURCE=Path('D:/PrototypeReference/p1/private/comparison/Brawler.p3d')
SKELETON='brawler_master_skeleton_skeleton'
CLIP='brawler_pnch_claw_counter'
TICKS=32
MAPPING={'hips':'Pelvis','torso':'Spine_3','head':'Head','jaw':'Jaw'}
for side,s in [('left','L'),('right','R')]:
    MAPPING.update({side+'_arm':'Shoulder_'+s,side+'_forearm':'Elbow_'+s,
                    side+'_thigh':'Hip_'+s,side+'_shin':'Knee_'+s})

def export():
    nodes=m.nodes(SOURCE);rig=m.skeleton(nodes,SKELETON)
    clips={m.decoder.text8(n['payload'],4)[0]:n for n in nodes if n['type']==0x121000}
    original=m.decoder.decode(clips[CLIP])
    reference=m.world(rig,m.decoder.decode(clips['brawler_act_calm']),0)
    geo=json.loads((ASSETS/'geo/blacklight_hunter_authored.geo.json').read_text())['minecraft:geometry'][0]['bones']
    pivots={b['name']:np.array(b['pivot'])*[-1,1,1] for b in geo}
    parents={b['name']:b.get('parent') for b in geo}
    aims={'hips':('torso','Spine_1'),'torso':('head','Neck')}
    for side,s,sign in [('left','L',-1),('right','R',1)]:
        pivots[side+'_hand_tip']=np.array([sign*14,3,-11])
        pivots[side+'_foot_tip']=np.array([sign*7,1.2,11.8])
        aims.update({side+'_arm':(side+'_forearm','Elbow_'+s),
                     side+'_forearm':(side+'_hand_tip','Wrist_'+s),
                     side+'_thigh':(side+'_shin','Knee_'+s),
                     side+'_shin':(side+'_foot_tip','Ball_'+s)})
    reflection=np.diag([-1.,1.,1.]);corrections={};directions={}
    for bone,source in MAPPING.items():
        if bone in aims:
            child,joint=aims[bone];directions[bone]=m.unit(pivots[child]-pivots[bone])
            desired=reflection@m.unit(reference[joint][:3,3]-reference[source][:3,3])
            corrections[bone]=m.rotation_between(directions[bone],desired)
        else:corrections[bone]=corrections['torso'].copy()
    document=json.loads((ASSETS/'animations/blacklight_hunter.animation.json').read_text())
    table=json.loads((ASSETS/'models/hunter_combat_motion.json').read_text())
    frames=[];animation={b:{'rotation':{},'position':{}} for b in table['bones']}
    def support(pose):
        return min(pose['Ball_L'][1,3],pose['Ball_R'][1,3])-pose['Motion_Root'][1,3]
    for f in range(int(TICKS*1.5)+1):
        t=f/30;current=m.world(rig,original,min(42,t*30*.9))
        world={};local={}
        for bone,source in MAPPING.items():
            delta=current[source][:3,:3]@reference[source][:3,:3].T
            rotation=reflection@delta@reflection@corrections[bone]
            if bone in aims:
                joint=aims[bone][1];desired=reflection@m.unit(current[joint][:3,3]-current[source][:3,3])
                rotation=m.rotation_between(rotation@directions[bone],desired)@rotation
            world[bone]=rotation
            parent=parents.get(bone)
            local[bone]=world[parent].T@rotation if parent in world else rotation
        transforms={'root':np.eye(4)}
        for b in geo[1:]:
            name=b['name'];rotation=local[name];p=pivots[name];transform=np.eye(4)
            transform[:3,:3]=rotation;transform[:3,3]=p-rotation@p
            transforms[name]=transforms[b['parent']]@transform
        feet=[(transforms[side+'_shin']@np.array([sign*7,1.2,11.8,1]))[1] for side,sign in [('left',-1),('right',1)]]
        height=1.2+(support(current)-support(reference))*18/max(.001,reference['Pelvis'][1,3])-min(feet)
        row=[]
        for bone in table['bones']:
            angles=m.euler(local[bone]);position=[0,float(height),0] if bone=='hips' else [0,0,0]
            row.append([round(float(x),7) for x in angles]+position)
            animation[bone]['rotation'][str(round(t,6))]=[round(math.degrees(v)*(-1 if i<2 else 1),6) for i,v in enumerate(angles)]
            animation[bone]['position'][str(round(t,6))]=position
        if not np.isfinite(np.array(row)).all():raise ValueError('Non-finite counter frame')
        frames.append(row)
    document['animations']['combat_counter']={'loop':False,'animation_length':TICKS/20,'bones':animation}
    table['clips']['combat_counter']=frames
    encoded=json.dumps(document,indent=2)
    (ASSETS/'animations/blacklight_hunter.animation.json').write_text(encoded,encoding='utf8')
    # Preserve the retired resource alias when appending a canonical Hunter clip.
    (ASSETS/'animations/brawler.animation.json').write_text(encoded,encoding='utf8')
    (ASSETS/'models/hunter_combat_motion.json').write_text(json.dumps(table,separators=(',',':')),encoding='utf8')
    print(json.dumps({'clip':CLIP,'sourceOffset':clips[CLIP]['offset'],'sourceFrames':[0,42],
                      'speed':.9,'sampledFrames':len(frames),'bones':len(table['bones']),'seconds':TICKS/20}))
if __name__=='__main__':export()
