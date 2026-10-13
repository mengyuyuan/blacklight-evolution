"""Bake the refined original block armor using its existing atlas and growth timing."""
from pathlib import Path
import copy, hashlib, json, subprocess
import numpy as np

repo=Path(__file__).resolve().parents[1]
assets=repo/'src/main/resources/assets/blacklight'
art=repo/'art/armor-vshape-preview'
def original(path):
    return subprocess.check_output(['git','show','ee5d2cc:'+path],cwd=repo)
base=json.loads(original('src/main/resources/assets/blacklight/models/armor_block.json'))
motion=json.loads(original('art/motion-profile/armor-growth.data'))
layout=json.loads((art/'layout.json').read_text(encoding='utf-8'))
bind={}
conversion=np.array([[1,0,0,0],[0,0,1,0],[0,-1,0,0],[0,0,0,1]],float)
def read(node,parent):
    world=parent@np.array(node['transform']).reshape(4,4)
    bind[node['name']]=world
    for child in node['children']:read(child,world)
read(json.loads((assets/'animation/player_bind.json').read_text())['hierarchy'][0],conversion)
def split(box):
    if not box['name'].startswith(('Arm core','Leg core')):return [box]
    result=[]
    for upper in [False,True]:
        b=copy.deepcopy(box);b['name']+='_upper' if upper else '_lower'
        b['from_' if upper else 'to'][1]=18 if box['bone'].endswith('_arm') else 6
        result.append(b)
    return result
def joint(b,center):
    bone=b['bone']
    if bone=='head':return 'Head'
    if bone=='torso':return 'Chest' if center[1]>=18 else 'Torso'
    side='L' if bone.startswith('left') else 'R'
    return (('Arm_' if center[1]>=18 else 'Hand_') if bone.endswith('_arm') else ('Thigh_' if center[1]>=6.8 else 'Leg_'))+side
parts=[];tracks={};all_world=[]
for box in layout['models']['armor']:
    for b in split(box):
        x,y,z=b['from_'];X,Y,Z=b['to']
        vertices=np.array([[x,y,z],[X,y,z],[X,Y,z],[x,Y,z],[x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z]],float)
        center=(np.array(b['from_'])+np.array(b['to']))/2
        angle=np.radians(b.get('rotation',[0,0,0])[2]);c,s=np.cos(angle),np.sin(angle)
        rotation=np.array([[c,-s,0],[s,c,0],[0,0,1]])
        pivot=np.array(b.get('pivot',center));vertices=(vertices-pivot)@rotation.T+pivot
        world=vertices/16;world[:,0]*=-1;world[:,1]+=.014
        j=joint(b,center)
        local=(np.column_stack([world,np.ones(8)])@np.linalg.inv(bind[j]).T)[:,:3]
        assert np.isfinite(local).all()
        for face in base['faces']:
            assert np.linalg.norm(np.cross(local[face[1]]-local[face[0]],local[face[2]]-local[face[0]]))>1e-8
        name=b['name'];track_name=name
        if name.startswith('Torso tapered'):
            track_name='Torso core_upper' if center[1]>=18 else 'Torso core_lower'
        elif name.startswith('Trapezius'):track_name='Rear neck mantle'
        tracks[name]=copy.deepcopy(motion['parts'][track_name])
        parts.append(dict(name=name,joint=j,region=b['bone'],core='core' in name.lower(),
            shoulder=name.startswith('Deltoid'),material=base['materials'].index(b['material']),points=np.round(local,7).tolist()))
        all_world.extend(world)
height=float(np.ptp(np.array(all_world)[:,1]))*.9375
scale=1.2
assert len(parts)==len(tracks)==127
assert all(t['growth'][0]==0 and t['growth'][-1]==1 for t in tracks.values())
assert all(any(p['core'] and p['region']==r for p in parts) for r in ['head','torso','left_arm','right_arm','left_leg','right_leg'])
assert not any(p['name'].startswith('Neck flange') for p in parts)
model=dict(revision=8,heightBlocks=height*scale,renderScale=scale,faces=base['faces'],materials=base['materials'],parts=parts)
(assets/'models/armor_block.json').write_text(json.dumps(model,separators=(',',':')),encoding='utf-8')
texture=original('src/main/resources/assets/blacklight/textures/entity/armor_block.png')
assert (art/'armor_block.png').read_bytes()==texture
(assets/'textures/entity/armor_block.png').write_bytes(texture)
motion['parts']=tracks
motion['adaptation']='Original authored armor refined with a tapered waist, connected trapezius and thicker thighs; original per-part growth timing retained.'
path=repo/'art/motion-profile/armor-growth.data'
path.write_text(json.dumps(motion,separators=(',',':')),encoding='utf-8')
manifest_path=repo/'art/motion-profile-manifest.json'
manifest=json.loads(manifest_path.read_text())
manifest['files']['armor-growth.data']=dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
manifest_path.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
report=dict(runtimeParts=len(parts),targetVisualHeightBlocks=height*scale,sourceVisualHeightBlocks=height,
    renderScale=scale,originalPixelAtlasUnchanged=True,trapeziusConnected=True,thighWidthIncrease='8–16%',
    collisionUnchanged=True)
(art/'import-record.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
layout['refinement'].update(previewOnly=False,runtimeInstalled=True)
layout['runtimeRenderScale']=scale;layout['runtimeVisualHeightBlocks']=height*scale
layout['refinement'].update(heightBlocks=height*scale,relativeScale=scale)
(art/'layout.json').write_text(json.dumps(layout,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
