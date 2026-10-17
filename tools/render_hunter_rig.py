"""Render fresh diagnostic geometry with Pillow; no image edits or GPU needed."""
from pathlib import Path
import json,math
import numpy as np
from PIL import Image,ImageDraw
R=Path(__file__).resolve().parents[1];data=json.loads((R/'art/hunter/rig-check.json').read_text())
W,H=360,330;im=Image.new('RGB',(W*6,H*2),(226,230,234));draw=ImageDraw.Draw(im)
az=math.radians(32);el=math.radians(10)
right=np.array([math.cos(az),0,math.sin(az)]);forward=np.array([math.sin(az)*math.cos(el),math.sin(el),-math.cos(az)*math.cos(el)]);up=np.cross(right,forward)
light=np.array([-.4,.8,-.4]);light/=np.linalg.norm(light)
for row in data['rows']:
    col=next(i for i,l in enumerate(['idle','swipe windup','swipe contact','swipe follow','barrage','slam impact']) if l==row['label']);line=int(row['fixed']);ox=col*W;oy=line*H
    v=np.array(row['vertices']);xy=np.column_stack((v@right,-v@up))*103+np.array([ox+W/2,oy+H-25]);depth=v@forward
    for f in sorted(data['faces'],key=lambda f:sum(depth[i] for i in f[:3])):
        a,b,c=v[f[:3]];n=np.cross(b-a,c-a);n/=max(np.linalg.norm(n),1e-9);shade=.5+.5*abs(n@light)
        color=tuple(int(((f[3]>>s)&255)*shade) for s in [16,8,0]);draw.polygon([tuple(xy[i]) for i in f[:3]],fill=color)
    for b,p in row['joints'].items():
        par=data['parents'].get(b)
        if not par or par=='root':continue
        def screen(p):p=np.array(p);return (ox+W/2+(p@right)*103,oy+H-25-(p@up)*103)
        end=screen(p);start=screen(row['joints'][par]);draw.line([start,end],fill=(25,155,230),width=2);draw.ellipse((end[0]-3,end[1]-3,end[0]+3,end[1]+3),fill=(235,85,30))
    draw.text((ox+10,oy+8),('CALIBRATED: ' if row['fixed'] else 'OLD AXES: ')+row['label'],fill=(20,20,20))
p=R/'art/hunter/rig-axis-comparison.png';im.save(p);print(p)
