"""Export the editable cuboid source to the same native Mercer rig, with exact UVs."""
from pathlib import Path
import json,math,shutil,sys
import numpy as np
ROOT=Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).resolve().parents[1]
OUT=ROOT/'art/mercer-armor'
def main():
    d=json.loads((OUT/'layout.json').read_text(encoding='utf8'))
    normal=json.loads((ROOT/'src/main/resources/assets/blacklight/models/mercer_v4.json').read_text(encoding='utf8'))
    native=dict(format=2,restHeight=normal['restHeight'],source='Authored block armor refined from our 86-cube blockout',bones=normal['bones'],parts=[])
    faces={'north':([1,0,3,2],[0,0,-1]),'south':([4,5,6,7],[0,0,1]),'west':([0,4,7,3],[-1,0,0]),'east':([5,1,2,6],[1,0,0]),'up':([3,7,6,2],[0,1,0]),'down':([0,1,5,4],[0,-1,0])}
    for c in d['cubes']:
        a,b=c['lo'],c['hi'];p=np.array(c['pivot']);x,y,z=map(math.radians,c['rot'])
        rx=np.array([[1,0,0],[0,math.cos(x),-math.sin(x)],[0,math.sin(x),math.cos(x)]])
        ry=np.array([[math.cos(y),0,math.sin(y)],[0,1,0],[-math.sin(y),0,math.cos(y)]])
        rz=np.array([[math.cos(z),-math.sin(z),0],[math.sin(z),math.cos(z),0],[0,0,1]])
        r=rz@ry@rx;reflect=np.diag([-1,1,1]);rotation=reflect@r
        points=np.array([[a[0],a[1],a[2]],[b[0],a[1],a[2]],[b[0],b[1],a[2]],[a[0],b[1],a[2]],[a[0],a[1],b[2]],[b[0],a[1],b[2]],[b[0],b[1],b[2]],[a[0],b[1],b[2]]])
        points=((points-p)@r.T+p)@reflect/16
        verts=[]
        for face,(indices,n) in faces.items():
            u,v,U,V=np.array(c['uv'][face])/1024;uvs=[[u,V],[U,V],[U,v],[u,v]];norm=rotation@np.array(n)
            for j in (3,2,1,0):verts.append(dict(p=points[indices[j]].round(7).tolist(),n=norm.round(7).tolist(),uv=uvs[j],w=[c['bone'],c['bone'],0],blink=[0,0,0]))
        bone=c['bone'];region=1 if bone=='Head' else (2 if 'Arm' in bone or 'Hand' in bone else 4) if bone.startswith('Right') else (3 if 'Arm' in bone or 'Hand' in bone else 5) if bone.startswith('Left') else 0
        native['parts'].append(dict(name=c['name'],region=region,shoulder='Shoulder' in c['name'],vertices=verts))
    assets=ROOT/'src/main/resources/assets/blacklight'
    target=assets/'models/mercer_armor.json'
    target.write_text(json.dumps(native,separators=(',',':')),encoding='utf8')
    shutil.copy2(OUT/'armor.png',assets/'textures/entity/mercer_armor.png')
    assert len(native['parts'])==116
    assert native['bones']==normal['bones']
    print('Exported',len(native['parts']),'cubes;',len(native['parts'])*12,'triangles; shared rig unchanged')
if __name__=='__main__':main()
