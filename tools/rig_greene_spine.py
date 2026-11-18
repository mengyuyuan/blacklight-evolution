"""Bind the existing authored neck to three source-driven segments; retain its texture/style."""
import copy,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
path=root/'src/main/resources/assets/blacklight/geo/elizabeth_greene_mc.geo.json'
data=json.loads(path.read_text());bones=data['minecraft:geometry'][0]['bones']
if not any(b['name']=='neck_base' for b in bones):
    base=next(b for b in bones if b['name']=='root')
    pieces=[{'name':n,'parent':'root','pivot':[0,y,4],'cubes':[]} for n,y in [('neck_base',0),('neck_mid',6),('neck_top',32)]]
    for cube in base.pop('cubes'):
        bottom=cube['origin'][1];height=cube['size'][1]
        if height>60:
            # Split only the long core, keeping its original cube-space pivot,
            # rotations and continuous side UVs. Existing surface plates are retained.
            for i,(low,high) in enumerate([(bottom,6),(6,32),(32,bottom+height)]):
                c=copy.deepcopy(cube);c['origin'][1]=low;c['size'][1]=high-low
                for side in ['north','south','east','west']:
                    uv=c['uv'][side];rate=cube['uv'][side]['uv_size'][1]/height
                    uv['uv'][1]+=((bottom+height)-high)*rate;uv['uv_size'][1]=(high-low)*rate
                pieces[i]['cubes'].append(c)
        else:
            center=bottom+height/2;index=0 if center<6 else 1 if center<32 else 2
            pieces[index]['cubes'].append(cube)
    bones[1:1]=pieces
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf8')
print('Greene existing neck bound to three articulated segments')
