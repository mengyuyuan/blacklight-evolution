"""Read the supplied private P1 mesh and material references from D:."""
from pathlib import Path
import sys
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/blacklight'
WORK=Path('D:/CodexCache/encounter-qa')
LIB=Path('D:/PrototypeReference/p2/tools')
sys.path.insert(0,str(LIB))
scope={'__file__':str(LIB/'export-p1-comparison.py')}
exec((LIB/'export-p1-comparison.py').read_text().split('for name,only in [')[0],scope)
reader=scope['m'];stream=scope['stream']
SOURCE=Path('D:/PrototypeReference/p1/private/comparison/mother.p3d')

def load_parts():
    nodes=list(reader.walk(reader.tree(SOURCE.read_bytes())))
    textures={};bindings={};parts=[]
    for n in nodes:
        if n['type']==0x19000:
            tex=reader.texture(n)
            if tex:textures[tex[0]]=np.asarray(tex[1].convert('RGB'))
        if n['type']==0x11015:
            shader,_=reader.text8(n['payload']);bindings[shader]={}
            for c in n['children']:
                if c['type']==0x11016:
                    key,p=reader.text8(c['payload']);value,_=reader.text8(c['payload'],p)
                    bindings[shader][key.lower()]=value
    for n in nodes:
        if n['type']!=0x10001:continue
        name,_=reader.text8(n['payload'])
        if not (name.startswith('mother_core_') or name.startswith('mother_segment_1_')):continue
        for g in n['children']:
            if g['type']!=0x10020:continue
            shader,_=reader.text8(g['payload'],4);values=stream(g)
            index=next(c['payload'] for c in g['children'] if c['type']==0x10013)
            texture=next((v for k,v in bindings.get(shader,{}).items() if k in ('diffuse','color','colour') and v in textures),None)
            positions=values[0x2c929929].copy();positions[:,0]*=-1
            parts.append(dict(name=name,p=positions,uv=values[0x00364509],
                tri=np.frombuffer(index[12:],dtype='<u2').reshape(-1,3),texture=textures.get(texture),texture_name=texture))
    return parts

