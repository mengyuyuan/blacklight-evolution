"""Export the editable Mercer cuboids and their independent YSM character rig.

Usage: python tools/export_mercer.py [repository root]
No motion files are converted or replaced. Output is a textured, two-weight skin mesh.
"""
import json, math, sys
from pathlib import Path

FACES = {'north':[1,0,3,2], 'south':[4,5,6,7], 'west':[0,4,7,3],
         'east':[5,1,2,6], 'up':[3,7,6,2], 'down':[0,1,5,4]}

def mix(a,b,t): return [x+(y-x)*t for x,y in zip(a,b)]
def ramp(x,a,b):
    t=max(0,min(1,(x-a)/(b-a)));return t*t*(3-2*t)
def curve(x,knots):
    for (a,b),(c,d) in zip(knots,knots[1:]):
        if x<=c:return b+(x-a)*(d-b)/(c-a)
    a,b=knots[-1];c,d=knots[-2];return b+(x-a)*(b-d)/(a-c)
def rotate(p,pivot,angles):
    x,y,z=[a-b for a,b in zip(p,pivot)]
    a,b,c=map(math.radians,angles)
    y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
    x,z=x*math.cos(b)+z*math.sin(b),-x*math.sin(b)+z*math.cos(b)
    x,y=x*math.cos(c)-y*math.sin(c),x*math.sin(c)+y*math.cos(c)
    return [x+pivot[0],y+pivot[1],z+pivot[2]]

def region(b,parents):
    while b not in ('UpBody','DownBody','Head','LeftArm','RightArm','LeftLeg','RightLeg'):
        b=parents[b]
    return {'UpBody':0,'DownBody':0,'Head':1,'RightArm':2,'LeftArm':3,'RightLeg':4,'LeftLeg':5}[b]

def calibrate(p,r):
    # Coordinate conversion only: never resize the approved character to fit the old rig.
    x,y,z=p;return [-x/16,y/16,z/16]

def weight(r,p,bone):
    y=p[1]*16;side='Right' if r in (2,4) else 'Left'
    if r==1:return [bone,bone,0]
    if r==0:
        if bone.startswith('Coat'):return ['DownBody',bone,ramp(y,15.5,18)]
        if y>=24.5:return ['UpBody','Head',ramp(y,24.5,26.3)]
        return ['DownBody','UpBody',ramp(y,15.5,18)]
    if r in (2,3):
        if y>23.5:return [side+'Arm','UpBody',ramp(y,23.5,25.2)]
        if y>16:return [side+'ForeArm',side+'Arm',ramp(y,18.3,19.6)]
        return [side+'HandLocator',side+'ForeArm',ramp(y,14.5,15.4)]
    if y>14:return [side+'Leg','DownBody',ramp(y,14.5,16)]
    if y<4.0:return [side+'Foot',side+'LowerLeg',ramp(y,3,4)]
    return [side+'LowerLeg',side+'Leg',ramp(y,8.4,9.8)]

def export(root):
    source=root/'art/mercer-v4';d=json.loads((source/'layout.json').read_text(encoding='utf8'))
    drivers={'Root':None,'AllBody':'Root','DownBody':'Root','UpBody':'Chest','Head':'Head'}
    for side,suffix in [('Left','L'),('Right','R')]:
        drivers.update({side+'Arm':'Arm_'+suffix,side+'ForeArm':'Hand_'+suffix,side+'HandLocator':'Tool_'+suffix,
                        side+'Leg':'Thigh_'+suffix,side+'LowerLeg':'Leg_'+suffix,side+'Foot':'Leg_'+suffix})
    for name,pivot in [('CoatFrontLeft',[1.5,17.0,0]),('CoatFrontRight',[-1.5,17.0,0]),('CoatBack',[0,17.0,1])]:
        d['pivots'][name]=pivot;d['parents'][name]='UpBody'
    bones=[]
    for name,pivot in d['pivots'].items():
        p=pivot
        if name!='Head' and name not in ('Root','AllBody') and region(name,d['parents'])==1:
            p=rotate(p,d['pivots']['Head'],d.get('rotations',{}).get('Head',[0,0,0]))
        bones.append({'name':name,'parent':d['parents'].get(name),'pivot':calibrate(p,0),'driver':drivers.get(name)})
    parts=[];vertex_count=0
    for c in d['cubes']:
        if 'Front hem' in c['name'] or 'Lower flare' in c['name']:
            c['bone']='CoatFrontLeft' if c['name'].startswith('Left') else 'CoatFrontRight'
        if c['name']=='Back lower hem':c['bone']='CoatBack'
        r=region(c['bone'],d['parents']);lo,hi=c['lo'],c['hi']
        a,b=lo,hi
        points=[(a[0],a[1],a[2]),(b[0],a[1],a[2]),(b[0],b[1],a[2]),(a[0],b[1],a[2]),
                (a[0],a[1],b[2]),(b[0],a[1],b[2]),(b[0],b[1],b[2]),(a[0],b[1],b[2])]
        points=[rotate(p,c['pivot'],c['rot']) for p in points]
        def positioned(p,closed=False):
            p=list(p)
            if c['bone'].endswith('Lid') and not closed:
                pivot=d['pivots'][c['bone']];p[1]=pivot[1]+(p[1]-pivot[1])*.001
            if r==1:p=rotate(p,d['pivots']['Head'],d.get('rotations',{}).get('Head',[0,0,0]))
            return calibrate(p,r)
        vertices=[]
        for face,ids in FACES.items():
            ps=[points[i] for i in ids];uv=c['uv'][face]
            # Subdivide before skinning; long coat/neck surfaces bend continuously.
            cols=max(1,math.ceil(math.dist(ps[0],ps[1])/2)) if r!=1 else 1
            rows=max(1,math.ceil(math.dist(ps[0],ps[3]))) if r!=1 else 1
            for row in range(rows):
                for col in range(cols):
                    quad=[]
                    for u,v in [(col/cols,row/rows),((col+1)/cols,row/rows),((col+1)/cols,(row+1)/rows),(col/cols,(row+1)/rows)]:
                        q=mix(mix(ps[0],ps[1],u),mix(ps[3],ps[2],u),v)
                        p=positioned(q);closed=positioned(q,True)
                        quad.append([p,[(uv[0]+(uv[2]-uv[0])*u)/1024,(uv[3]+(uv[1]-uv[3])*v)/1024],weight(r,p,c['bone']),[z-x for x,z in zip(p,closed)]])
                    # Author-to-native mirror changes handedness. Reverse each quad.
                    quad.reverse()
                    pa,pb,pc=[v[0] for v in quad[:3]];ab=[b-a for a,b in zip(pa,pb)];ac=[c-a for a,c in zip(pa,pc)]
                    n=[ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]]
                    length=math.sqrt(sum(x*x for x in n));n=[x/max(length,1e-12) for x in n]
                    for p,texuv,w,delta in quad:vertices.append({'p':p,'n':n,'uv':texuv,'w':w,'blink':delta})
        parts.append({'name':c['name'],'region':r,'shoulder':r in (2,3) and lo[1]>=24,'vertices':vertices});vertex_count+=len(vertices)
    height=max(v['p'][1] for part in parts for v in part['vertices'])-min(v['p'][1] for part in parts for v in part['vertices'])
    result={'format':2,'restHeight':height,'source':'Alex Mercer V4.5 / rounded hood, preserved native motion rig','bones':bones,'parts':parts}
    out=root/'src/main/resources/assets/blacklight/models/mercer_v4.json';out.parent.mkdir(parents=True,exist_ok=True)
    # Stable compact decimals, keeping facial depth separation (down to 1e-5 blocks).
    def rounded(o):
        if isinstance(o,float):return round(o,7)
        if isinstance(o,list):return [rounded(v) for v in o]
        if isinstance(o,dict):return {k:rounded(v) for k,v in o.items()}
        return o
    out.write_text(json.dumps(rounded(result),ensure_ascii=False,separators=(',',':')),encoding='utf8')
    print(json.dumps({'parts':len(parts),'vertices':vertex_count,'output':str(out)}))

if __name__=='__main__':export(Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).resolve().parents[1])
