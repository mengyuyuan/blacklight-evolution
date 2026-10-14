"""Bind the approved MC weapon masters to the shipped animation rig.

The explicit authoring landmarks describe the review A-pose, in metres.
No source-game meshes, textures or private tools are needed to rebuild.
"""
from pathlib import Path
import copy, hashlib, json, shutil
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/blacklight'
SHOULDER = np.array([-.21370, 1.51726, .03195])
ELBOW = np.array([-.36331, 1.26393, .02705])
WRIST = np.array([-.50622, 1.01464, -.04611])

# Static calibration from the original forearm binding, expressed directly in
# the review model's coordinate system. Reflection is intentional: native right
# is negative X; the Minecraft skeleton's right side is positive X.
FOREARM_BASIS = np.array([
    [-.7601140635203927, .42641412913219867, .03179444764675874],
    [-.4203332750866191, -.7332291695165114, -.215193421858803],
    [.0784846117381645, .20287745422489112, -.8445674575033982]])


def align(a, b):
    a=a/np.linalg.norm(a); b=b/np.linalg.norm(b)
    v=np.cross(a,b); c=np.dot(a,b)
    k=np.array([[0,-v[2],v[1]],[v[2],0,-v[0]],[-v[1],v[0],0]])
    return np.eye(3)+k+k@k/(1+c)


# Transport the same roll onto the upper arm, instead of rebuilding roll from
# global Z. Keep every segment a similarity transform: no flattened knuckles,
# separate blade-tip rotation, or distorted vein/decal geometry.
UPPER_BASIS = FOREARM_BASIS @ align(ELBOW-SHOULDER, WRIST-ELBOW)
FRAMES = {}
for joint,basis,origin in [('Arm_R',UPPER_BASIS,SHOULDER),('Hand_R',FOREARM_BASIS,ELBOW)]:
    m=np.eye(4);m[:3,:3]=basis;m[:3,3]=-basis@origin;FRAMES[joint]=m

# Relative to the calibrated source-to-player scale; identical on all axes.
# The whip is deliberately a substantial transformed arm, not a thin bracelet.
SIZES = {'claws':(1.60,1.65), 'blade':(1.60,1.45),
         'hammerfists':(1.70,2.10), 'whipfist':(1.85,2.05),
         'muscle_mass':(1.85,1.90)}


def local(points, joint, key):
    p=(np.c_[points,np.ones(len(points))] @ FRAMES[joint].T)[:,:3]
    return p*SIZES[key][0 if joint=='Arm_R' else 1]

def oriented_faces(part):
    """Consistent manifold orientation, then positive signed volume; decals keep their side."""
    faces=copy.deepcopy(part['faces'])
    if part.get('surfaceDecal'): return faces
    edges={}
    for i,f in enumerate(faces):
        for a,b in zip(f,f[1:]+f[:1]): edges.setdefault(tuple(sorted((a,b))),[]).append((i,a,b))
    assert all(len(v)==2 for v in edges.values()),part['name']
    signs={0:1};todo=[0]
    while todo:
        i=todo.pop();f=faces[i]
        for a,b in zip(f,f[1:]+f[:1]):
            for j,c,d in edges[tuple(sorted((a,b)))]:
                if j==i:continue
                s=-signs[i] if (a,b)==(c,d) else signs[i]
                if j not in signs:signs[j]=s;todo.append(j)
                else:assert signs[j]==s,part['name']
    faces=[f if signs[i]>0 else f[::-1] for i,f in enumerate(faces)]
    v=np.array(part['vertices'])/16
    volume=sum(np.dot(v[f[0]],np.cross(v[f[k]],v[f[k+1]])) for f in faces for k in range(1,len(f)-1))
    return faces if volume>0 else [f[::-1] for f in faces]

def clip(poly, positive):
    # Split the whip's long sheath at the elbow, so it can actually bend.
    result=[];axis=(WRIST-ELBOW);axis/=np.linalg.norm(axis)
    for a,b in zip(poly,poly[1:]+poly[:1]):
        da=np.dot(a-ELBOW,axis)*(1 if positive else -1)
        db=np.dot(b-ELBOW,axis)*(1 if positive else -1)
        if da>=0:result.append(a)
        if (da<0)!=(db<0):result.append(a+(b-a)*da/(da-db))
    return result

def joint_for(key, part):
    name=part['name']
    if key in ('hammerfists','muscle_mass') and not name.startswith('Vein'):
        return 'Arm_R' if name.startswith(('Deltoid','Rear triceps','Bicep','Upper rear','Upper front')) else 'Hand_R'
    if key=='whipfist' and name in ('Whip distal muscle core','Hook root cuff'):return 'Hand_R'
    return part['bone']

def export(key,parts,palette,source):
    joints={};ranges={};keys=list(palette)
    for part in parts:
        start={k:len(v) for k,v in joints.items()}
        vertices=np.array(part['vertices'])/16
        split=key=='whipfist' and part['name'].startswith(('Long leaf sheath','Outer sheath fan','Vein long sheath','Vein sheath outer','Vein outer fork'))
        for f,mat in zip(oriented_faces(part),part['materials']):
            for i in range(1,len(f)-1):
                tri=[vertices[j] for j in (f[0],f[i],f[i+1])]
                chunks=[(joint_for(key,part),tri)] if not split else [('Arm_R',clip(tri,False)),('Hand_R',clip(tri,True))]
                for joint,poly in chunks:
                    for k in range(1,len(poly)-1):
                        p=local(np.array([poly[0],poly[k],poly[k+1]]),joint,key)
                        # The source-to-runtime conversion changes handedness.
                        if np.linalg.det(FRAMES[joint][:3,:3])<0:p=p[[0,2,1]]
                        n=np.cross(p[1]-p[0],p[2]-p[0]);length=np.linalg.norm(n)
                        if length<1e-10:continue
                        n/=length
                        # Project each triangle into its dominant plane at 64 pixels/metre.
                        axis=int(np.argmax(abs(n)));q=np.delete(p,axis,axis=1)*64
                        q-=q.min(0);q=np.minimum(q,14)+.5
                        tile=keys.index(mat);uv=np.c_[(q[:,0]+tile*16)/144,q[:,1]/16]
                        record={'p':p.round(7).tolist(),'n':[n.round(7).tolist()]*3,'c':palette[mat],
                                'uv':uv.round(7).tolist(),'emissive':mat in ('vein_red','vein_hot')}
                        joints.setdefault(joint,[]).append(record)
        for joint,fs in joints.items():
            begin=start.get(joint,0)
            if len(fs)>begin:ranges[part['name']+' / '+joint]={'joint':joint,'start':begin,'count':len(fs)-begin}
    anchors={}
    authored={'palm':[-.55,.927,-.083], 'tether':[-.537,.970,-.096],
              'bladeGrip':[-.652,.780,-.105], 'trailRoot':[-.554,.940,-.075],
              'trailTip':[-.879,.393,-.262], 'reverseRoot':[-.377,1.255,.125],
              'reverseTip':[-.014,1.899,.382]}
    for name,p in authored.items():anchors[name+'_R']=local(np.array([p]),'Hand_R',key)[0].round(7).tolist()
    if key in ('claws','hammerfists','muscle_mass'):
        for joint,fs in list(joints.items()):
            left=[]
            for f in fs:
                g=copy.deepcopy(f)
                for attr in ('p','n'):
                    g[attr]=[[-v[0],v[1],v[2]] for v in reversed(f[attr])]
                g['uv']=list(reversed(g['uv']));left.append(g)
            joints[joint.replace('_R','_L')]=left
        for name,p in list(anchors.items()):anchors[name.replace('_R','_L')]=[-p[0],p[1],p[2]]
    result={'version':3,'designRevision':6 if key=='blade' else 4,'source':source,
            'texture':'textures/entity/weapons_mc.png','emissiveTexture':'textures/entity/weapons_mc_emissive.png',
            'approvedProportions':True,'mountRevision':2,'uniformSegmentScale':SIZES[key],'anchors':anchors,'joints':joints,'parts':ranges}
    if key=='whipfist':
        hook=[]
        for name,part in ranges.items():
            if not name.startswith(('Forward main hook','Forward inner hook','Hook outer guard','Hook inner guard')):continue
            for f in joints[part['joint']][part['start']:part['start']+part['count']]:
                g=copy.deepcopy(f)
                g['p']=(np.array(g['p'])-anchors['tether_R']).round(7).tolist()
                hook.append(g)
        # Traveling hooks use the shared white-tissue draw call; preserve palette RGB.
        tip={'version':3,'designRevision':4,'source':source,'joints':{'Hand_R':hook}}
        (ASSETS/'models/whip_tip.json').write_text(json.dumps(tip,separators=(',',':'))+'\n')
    file='claw_blender' if key=='claws' else 'form_'+key
    (ASSETS/'models'/f'{file}.json').write_text(json.dumps(result,separators=(',',':'),allow_nan=False)+'\n')
    return {'source':source,'sha256':hashlib.sha256((ROOT/source).read_bytes()).hexdigest(),
            'triangles':sum(map(len,joints.values())),'joints':list(joints),'file':file}

def main():
    suite=json.loads((ROOT/'art/weapons-mc-v4/layout.json').read_text())
    blade=json.loads((ROOT/'art/blade-mc-v6/layout.json').read_text())
    report={}
    for key,parts in suite['models'].items():
        source='art/weapons-mc-v4/layout.json'
        if key=='blade':parts=blade['models']['blade'];source='art/blade-mc-v6/layout.json'
        report[key]=export(key,parts,suite['palette'],source)
    for src,dst in [('weapons_pixel.png','weapons_mc.png'),('weapons_emissive.png','weapons_mc_emissive.png')]:
        shutil.copyfile(ROOT/'art/weapons-mc-v4'/src,ASSETS/'textures/entity'/dst)
    out=ROOT/'art/weapons-mc-runtime';out.mkdir(exist_ok=True)
    (out/'integration.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
