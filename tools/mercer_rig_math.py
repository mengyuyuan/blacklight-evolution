"""Numeric independent-rig retarget for Blender authoring/preview. Runtime counterpart: MercerRig.java."""
import json,math
from mathutils import Matrix,Vector

def load_context(ROOT):
    meshdata=json.loads((ROOT/'src/main/resources/assets/blacklight/models/mercer_v4.json').read_text())
    binddata=json.loads((ROOT/'src/main/resources/assets/blacklight/animation/player_bind.json').read_text())
    axis=Matrix.Rotation(-math.pi/2,4,'X');bind={};hierarchy=binddata['hierarchy'][0]
    def matrix(a):return Matrix([a[i:i+4] for i in range(0,16,4)])
    def traverse(b,parent,output,tracks=None,time=0):
        m=matrix(b['transform'])
        if tracks and b['name'] in tracks:
            t=tracks[b['name']];ix=min(range(len(t['time'])),key=lambda i:abs(t['time'][i]-time));m=matrix(t['transform'][ix])
        output[b['name']]=parent@m
        for c in b['children']:traverse(c,output[b['name']],output,tracks,time)
    traverse(hierarchy,axis,bind)
    inverse={k:v.inverted() for k,v in bind.items()}
    def retarget(posed,pin=False):
        source={k:posed[k]@inverse[k] for k in bind};rotations={};positions={};drivers={};skin={}
        pivots={b['name']:Vector(b['pivot']) for b in meshdata['bones']}
        for bone in meshdata['bones']:
            name,parent=bone['name'],bone['parent'];driver=bone['driver'] or drivers.get(parent)
            rotation=source[driver].copy() if driver else Matrix.Identity(4);rotation.translation=(0,0,0)
            position=pivots[name].copy() if parent is None else positions[parent]+rotations[parent].to_3x3()@(pivots[name]-pivots[parent])
            if parent is not None and driver:
                pd=drivers.get(parent)
                if pd is None:position+=posed[driver].translation-bind[driver].translation
                elif driver!=pd:position+=posed[driver].translation-(source[pd]@bind[driver].translation)
            if pin and (name.endswith('ForeArm') or name.endswith('HandLocator')):position=posed[driver].translation.copy()
            rotations[name]=rotation;positions[name]=position;drivers[name]=driver
            skin[name]=rotation.copy();skin[name].translation=position-rotation.to_3x3()@pivots[name]
        return skin
    return dict(meshdata=meshdata,bind=bind,hierarchy=hierarchy,axis=axis,traverse=traverse,retarget=retarget)
