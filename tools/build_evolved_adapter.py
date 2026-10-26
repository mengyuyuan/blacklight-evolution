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

# Keep the standard player cuboids, replacing the complete arm surfaces below.
meshpath = ROOT/'src/main/resources/assets/blacklight/models/evolved_player_rig.json'
old = json.loads(meshpath.read_text()); body = [f for f in old['faces'] if f[5] == 0]
used = sorted({i for f in body for i in f[:4]}); remap={i:k for k,i in enumerate(used)}
vertices=[old['vertices'][i] for i in used]; faces=[[*[remap[i] for i in f[:4]],*f[4:]] for f in body]

def surface(points, side, bone, patch, va, vb):
    ids=[]
    for x,y,z in points:
        vertices.append([round((-x if side=='L' else x)+(-5 if side=='R' else 5),5),y,z,index[bone.lower()],index[bone.lower()],0])
        ids.append(len(vertices)-1)
    uv=[(patch*32+2,va),(patch*32+29,va),(patch*32+29,vb),(patch*32+2,vb)]
    if side=='R': ids.reverse();uv.reverse()
    role=2 if side=='R' else 3
    flat=[round(v/128,6) for pair in uv for v in pair]
    faces.append([*ids,role,4,*flat,-1])
    faces.append([*ids,role,5,*flat,-1])

def ring(y, left, right, front, back):
    bevel=min(.48,(right-left)*.22,(back-front)*.22)
    return [(left+bevel,y,front),(right-bevel,y,front),(right,y,front+bevel),(right,y,back-bevel),
            (right-bevel,y,back),(left+bevel,y,back),(left,y,back-bevel),(left,y,front+bevel)]

def limb(side,bone,sections):
    rings=[ring(*section) for section in sections]
    for a,b in zip(rings,rings[1:]):
        for i in range(8):
            patch=3 if i in (1,7) else 2 if i in (2,6) else 1 if i in (0,4) else 0
            surface([a[i],a[(i+1)%8],b[(i+1)%8],b[i]],side,bone,patch,max(2,a[0][1]*6),min(125,b[0][1]*6+8))
    # Closed faceted ends, without spike tangles or floating wrist pieces.
    for end,reverse in [(rings[0],True),(rings[-1],False)]:
        center=tuple(np.mean(np.array(end),axis=0))
        for i in range(8):
            p=[center,end[i],end[(i+1)%8],center]
            surface(list(reversed(p)) if reverse else p,side,bone,0,2,26)

for side in ['R','L']:
    limb(side,'Shoulder_'+side,[(0.,-1.8,1.8,-1.8,1.8),(1.6,-2.05,2.05,-2.,2.),(4.8,-1.6,1.6,-1.6,1.6),(6.8,-1.55,1.55,-1.55,1.55)])
    limb(side,'Elbow_'+side,[(6.1,-1.6,1.6,-1.6,1.6),(8.5,-2.5,1.8,-2.4,1.8),(11.5,-3.,1.6,-2.8,1.1),
                            (14.,-2.4,.9,-2.1,.5),(17.,-.9,.35,-.9,.3),(18.5,-.12,.12,-.25,0.)])
mesh={'schema':2,'vertices':vertices,'faces':sorted(faces,key=lambda f:f[5]),
      'body':'Standard Minecraft player body with separately authored full biological blade arms, aligned to anatomical shoulder/elbow aims.',
      'armSource':{'authoring':'Dedicated Minecraft Evolved arm geometry, not the player weapon mesh','texture':'textures/entity/evolved_arms.png','emissiveTexture':'textures/entity/evolved_arms_emissive.png'}}
meshpath.write_text(json.dumps(mesh,separators=(',',':')),encoding='utf8')

image=Image.new('RGBA',(128,128)); glow=Image.new('RGBA',(128,128),(0,0,0,0))
palette=[(31,27,31),(44,43,47),(67,67,72),(91,91,96)]
for y in range(128):
    for x in range(128):
        patch=x//32;u=x%32;rgb=palette[patch]
        grain=((x*7+y*3)%7-3)//2
        shade=-8 if y%24 in (0,1) else 5 if u in (2,29) else 0
        rgb=tuple(max(0,min(255,v+grain+shade)) for v in rgb)
        vein=patch in (0,1) and abs(u-(12+(y//11)%3*2))<=1 and 5<y<124
        if vein:rgb=(124 if u%2 else 158,21,30);glow.putpixel((x,y),(145,19,28,150))
        image.putpixel((x,y),(*rgb,255))
textures=ROOT/'src/main/resources/assets/blacklight/textures/entity'
image.save(textures/'evolved_arms.png');glow.save(textures/'evolved_arms_emissive.png')
report={'sourceSkeleton':'evolved_runner1','sourceBones':len(bones),'body':'standard Minecraft player cuboids','vertices':len(vertices),'quads':len(faces),
        'motionBytes':motionpath.stat().st_size,'axes':{'sourceForward':'-Z','minecraftForward':'-Z','conversion':[1,-1,1]},'sources':sources}
(ROOT/'docs/design/evolved-form-motion-sources.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(ROOT/'docs/design/evolved-complete-arms.json').write_text(json.dumps({'source':mesh['armSource'],'bodyQuads':len(body),'armQuads':(len(faces)-len(body))//2,'bladeTipY':18.5,'shoulderPivotY':2,'elbowPivotY':6.5},indent=2)+'\n',encoding='utf8')
print(json.dumps({k:v for k,v in report.items() if k!='sources'}))
spec=importlib.util.spec_from_file_location('fitted_arms',ROOT/'tools/fit_p2_evolved_arms.py')
fitted=importlib.util.module_from_spec(spec);spec.loader.exec_module(fitted);fitted.author(args.source_dir)
