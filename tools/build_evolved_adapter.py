"""Retarget the locally decoded P2 Evolved rig and author its own Minecraft blade arms.

Run with --source-dir pointing to the existing local research extraction. No game
body mesh is copied. Source rotations, names, hierarchy and timing are retained;
bone aim frames map them onto the shorter Minecraft limbs.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import math
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--source-dir', type=Path, required=True)
args = parser.parse_args()
spec = importlib.util.spec_from_file_location('source_rig', args.source_dir / 'verify-rig.py')
rig = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rig)
index = rig.lookup
# Both source feet and Minecraft toes point toward -Z. Only Y changes handedness.
C = np.diag([1., -1., 1.])
bind = np.array(rig.global_bind)

def unit(v):
    return v / max(1e-12, np.linalg.norm(v))

def frame_y(direction, hint=np.array([0., 0., 1.])):
    y = unit(direction)
    z = hint - y * np.dot(y, hint)
    if np.linalg.norm(z) < .001:
        z = np.array([1., 0., 0.]) - y * y[0]
    z = unit(z)
    x = unit(np.cross(y, z))
    return np.array([x, y, np.cross(x, y)]).T

def quaternion(a):
    t = np.trace(a)
    if t > 0:
        s = math.sqrt(t + 1) * 2
        q = [(a[2, 1]-a[1, 2])/s, (a[0, 2]-a[2, 0])/s, (a[1, 0]-a[0, 1])/s, s/4]
    else:
        i = int(np.argmax(np.diag(a))); j = (i+1) % 3; k = (i+2) % 3
        s = math.sqrt(max(1e-12, 1+a[i, i]-a[j, j]-a[k, k]))*2
        q = [0., 0., 0., 0.]
        q[i] = s/4; q[j] = (a[j, i]+a[i, j])/s; q[k] = (a[k, i]+a[i, k])/s; q[3] = (a[k, j]-a[j, k])/s
    return [round(float(v), 6) for v in unit(np.array(q))]

fixed = {'motion_root':(0,24,0), 'balance_root':(0,12,0), 'character_root':(0,12,0), 'pelvis':(0,12,0),
         'spine_1':(0,11.5,0), 'spine_2':(0,7.5,0), 'spine_3':(0,3.5,0), 'neck':(0,0,0), 'head':(0,-2,0)}
aim = {}
for side, x in [('r',-1), ('l',1)]:
    fixed.update({f'clavicle_{side}':(x,1,0), f'shoulder_{side}':(x*5,2,0), f'elbow_{side}':(x*5,6.5,0),
                  f'forarm_{side}':(x*5,9,0), f'wrist_{side}':(x*5,12,0), f'hip_{side}':(x*1.9,12,0),
                  f'knee_{side}':(x*1.9,18,0), f'ankle_{side}':(x*1.9,23,0), f'ball_{side}':(x*1.9,24,-1)})
    aim.update({f'shoulder_{side}':f'elbow_{side}', f'elbow_{side}':f'wrist_{side}', f'forarm_{side}':f'wrist_{side}',
                f'hip_{side}':f'knee_{side}', f'knee_{side}':f'ankle_{side}', f'ankle_{side}':f'ball_{side}'})
bones = []; pivots = []
for i, b in enumerate(rig.bones):
    name = b['name']; parent = rig.parents[i]
    pivot = np.array(fixed[name.lower()], float) if name.lower() in fixed else (pivots[parent] + C @ (bind[i,:3,3]-bind[parent,:3,3])*16 if parent >= 0 else np.array([0.,24.,0.]))
    pivots.append(pivot)
    bones.append({'name':name, 'parent':parent, 'pivot':[round(float(v),6) for v in pivot]})

selected = {'idle':'evolved_loco_idle_to_run', 'run':'evolved_loco_run', 'sprint':'evolved_loco_sprint',
            'left':'evolved_loco_strafe_run_left', 'right':'evolved_loco_strafe_run_right',
            'slash':'evolved_com_dash_back_double_slash', 'leap':'evolved_com_special_jumpslash',
            'dodge_left':'evolved_com_dodge_left', 'dodge_right':'evolved_com_dodge_right',
            'transition':'evolved_transition_1', 'consume':'alex_hunt_consume_evolved_front_gv',
            'victim':'alex_hunt_consume_evolved_front_rcv', 'hit':'evolved_gethit_claw_pain_left',
            'heavy_hit':'evolved_gethit_claw_pain_right', 'back':'evolved_loco_strafe_run_back',
            'rush':'evolved_com_combo_4hit_from_run', 'thrust_left':'evolved_com_special_impale_left',
            'thrust_right':'evolved_com_special_impale_right',
            'guard':'evolved_taunt_change_side','taunt_sharpen':'evolved_taunt_stand_sharpen_blades',
            'taunt_beckon':'evolved_taunt_come_here','idle_shrug':'evolved_act_reaction_shrug',
            'idle_neck':'evolved_act_reaction_crack_neck',
            'guard_forward':'evolved_taunt_walk_forward','guard_back':'evolved_taunt_walk_back',
            'guard_left':'evolved_taunt_walk_left','guard_right':'evolved_taunt_walk_right'}
clips = {}; sources = []
for key, name in selected.items():
    path = args.source_dir / 'decoded-animation' / (name+'.json')
    source = json.loads(path.read_text()); rows = []; root_frames=[]
    root_track=next((t for t in source['tracks'] if t['bone'].lower()=='motion_root' and t['tag']=='TRAN'),None)
    root_start=rig.sample(root_track,0) if root_track else np.zeros(3)
    for f in range(1 if key in ('idle','guard') else int(source['frames'])):
        world, matched = rig.pose(source, f, in_place=True); row = []
        for i, b in enumerate(bones):
            delta = C @ world[i,:3,:3] @ bind[i,:3,:3].T @ C
            child = aim.get(b['name'].lower())
            if child:
                j = index[child]
                reference = frame_y(C @ (bind[j,:3,3]-bind[i,:3,3]))
                animated = frame_y(C @ (world[j,:3,3]-world[i,:3,3]), delta @ reference[:,2])
                target = frame_y(pivots[j]-pivots[i])
                rotation = animated @ target.T
            else:
                rotation = delta
            parent = b['parent']
            expected = bind[i,:3,3] if parent < 0 else world[parent,:3,3] + world[parent,:3,:3] @ bind[parent,:3,:3].T @ (bind[i,:3,3]-bind[parent,:3,3])
            correction = C @ (world[i,:3,3]-expected)*16
            if parent < 0: correction[:] = 0
            row += quaternion(rotation) + [round(float(v),6) for v in correction]
        rows.append(row)
        displacement=rig.sample(root_track,f)-root_start if root_track else np.zeros(3)
        root_frames.append([round(float(displacement[0]),6),round(float(displacement[1]),6),round(float(-displacement[2]),6)])
    if len(rows) == 1: rows.append(rows[0]);root_frames.append(root_frames[0])
    clips[key] = {'seconds':float(source['frames'])/source['fps'], 'frames':rows, 'root':root_frames, 'source':name}
    sources.append({'clip':key, 'source':name, 'sha256':hashlib.sha256(path.read_bytes()).hexdigest(), 'matchedJoints':len(set(matched))})
motion = {'schema':2, 'bones':bones, 'reference':'Original 67-joint P2 Evolved hierarchy; anatomical aim frames fit Minecraft limb lengths. Source -Z forward is retained. Authored local translation is retained; motion-root travel belongs to server physics.', 'clips':clips}
motionpath = ROOT/'art/motion-profile/evolved-poses.data'
motionpath.write_text(json.dumps(motion,separators=(',',':')),encoding='utf8')
manifestpath = ROOT/'art/motion-profile-manifest.json'
manifest = json.loads(manifestpath.read_text()); manifest['files']['evolved-poses.data']={'bytes':motionpath.stat().st_size,'sha256':hashlib.sha256(motionpath.read_bytes()).hexdigest()}
manifestpath.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

report = {'sourceSkeleton':'evolved_runner1','sourceBones':len(bones),'sources':sources}
(ROOT/'docs/design/evolved-form-motion-sources.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
