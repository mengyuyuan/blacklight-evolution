"""Exact local P2 power surfaces retargeted like the accepted Runner1 blade adapter.
Reads the retained research decoder/index, retains source topology and two strongest
anatomical influences, and authors pixel-material UVs instead of copying textures.
"""
from pathlib import Path
import hashlib,importlib.util,json,sys
import numpy as np
from PIL import Image

REFERENCE=Path('E:/Prototype2-Research-20261024')
sys.path.insert(0,str(REFERENCE))
spec=importlib.util.spec_from_file_location('p2_variant_decode',REFERENCE/'export-reference-models.py')
decoder=importlib.util.module_from_spec(spec);spec.loader.exec_module(decoder)
C=np.diag([1.,-1.,1.]);CACHE={}

def unit(v):return v/max(1e-12,np.linalg.norm(v))
def frame(v):
    y=unit(v);z=unit(np.array([0.,0.,1.])-y*y[2]);x=unit(np.cross(y,z));return np.array([x,y,np.cross(x,y)]).T

def load(asset):
    if asset in CACHE:return CACHE[asset]
    source=REFERENCE/f'extracted/art/art/packages/powers/{asset}/{asset}.p3d'
    inventory=json.loads((REFERENCE/f'inventory/art/art/packages/powers/{asset}/{asset}.json').read_text('utf-8'))
    bones=inventory['joints'];world=[]
    for i,b in enumerate(bones):
        a=b['matrix15'];m=np.eye(4);m[:3,0]=a[:3];m[:3,1]=a[4:7];m[:3,2]=a[8:11];m[:3,3]=a[12:15];parent=b['parent'];world.append(world[parent]@m if 0<=parent<i else m)
    nodes=list(decoder.walk(decoder.tree(source.read_bytes())));prims={decoder.text8(n['payload'],4)[0]:n for n in nodes if n['type'] in (0x10040,0x10041)};parts=[]
    for n in nodes:
        if n['type'] not in (0x25002,0x25003):continue
        p=n['payload'];shader,q=decoder.text8(p,4);q+=4;ix,q=decoder.text8(p,q);vx,q=decoder.text8(p,q);sx,q=decoder.text8(p,q)
        # Whip tip has its own extension skeleton and will be represented by the real moving tendon.
        if 'whipfist_hand' in sx:continue
        skin,_=decoder.streams(prims[sx]);indices,_=decoder.streams(prims[ix]);uvs,_=decoder.streams(prims[vx]);color=None
        links=REFERENCE/f'reference-models/{asset}/source-links.json'
        if links.exists():
            item=next((p for p in json.loads(links.read_text('utf-8'))['parts'] if p['skinPrimitive']==sx),None)
            if item and item.get('diffuseForPreview'):
                path=links.parent/(Path(item['diffuseForPreview']).stem+'.png')
                if path.exists():color=np.array(Image.open(path).convert('RGB'))
        parts.append((sx,skin,indices['indices'].flatten().reshape(-1,3),uvs.get('tex0'),color))
    result=(source,bones,np.array(world),parts);CACHE[asset]=result;return result

def append_power(model,asset,target_bones,sides=('R','L'),shield=False,append=False):
    source,bones,bind,parts=load(asset);index={b['name']:i for i,b in enumerate(bones)};target={b['name']:i for i,b in enumerate(target_bones)};pivots=np.array([b['pivot'] for b in target_bones])
    roles={2 if s=='R' else 3 for s in sides}
    if not append:model.faces=[f for f in model.faces if f[4] not in roles or f[5]==0]
    armframes={};scales={}
    if not shield:
        for side in ('R','L'):
            for a,b in [('Shoulder_'+side,'Elbow_'+side),('Elbow_'+side,'Wrist_'+side)]:
                if a not in index or b not in index:continue
                armframes[a]=frame(C@(bind[index[b],:3,3]-bind[index[a],:3,3]));scales[a]=np.linalg.norm(pivots[target[b]]-pivots[target[a]])/(np.linalg.norm(bind[index[b],:3,3]-bind[index[a],:3,3])*16)
    def mapped(j,side):
        for _ in range(len(bones)):
            name=bones[j]['name']
            if name=='Elbow_'+side or name=='Shoulder_'+side:return name
            parent=bones[j]['parent']
            if parent<0 or parent>=j:break
            j=parent
        return 'Elbow_'+side
    def fit(point,name):
        d=C@(point-bind[index[name],:3,3])*16;d=armframes[name].T@d;d*=np.array([1.05,scales[name],1.05])
        if name.startswith('Elbow') and d[1]>5.5:d[1]=5.5+(d[1]-5.5)*.68
        return pivots[target[name]]+d
    count=0
    for part,skin,triangles,source_uv,color_reference in parts:
        points=skin['position'][:,:3].astype(float);weights=skin['weights'];joints=skin['indices'];vertices={};positions={};side_for={}
        if shield:lo=points.min(0);hi=points.max(0);center=(lo+hi)*.5
        for i,(point,ws,js) in enumerate(zip(points,weights,joints)):
            side='L' if point[0]>0 else 'R'
            if shield:side='L'
            if side not in sides:continue
            if shield:
                # Preserve the actual shield silhouette; mount its broad axes on the left forearm.
                p=(point-center)*16;p=np.array([5+p[0],12-p[1],-2+p[2]])
                ordered=[(target['Elbow_L'],1),(target['Elbow_L'],0)]
            else:
                influences={}
                for w,j in zip(ws,js):
                    if w<=0:continue
                    name=mapped(int(j),side);influences[name]=influences.get(name,0)+float(w)
                ordered_names=sorted(influences.items(),key=lambda x:-x[1])[:2]
                if len(ordered_names)==1:ordered_names.append((ordered_names[0][0],0))
                total=sum(w for _,w in ordered_names);ordered_names=[(n,w/total) for n,w in ordered_names]
                p=sum((fit(point,n)*w for n,w in ordered_names),np.zeros(3));ordered=[(target[n],w) for n,w in ordered_names]
            vertices[i]=len(model.vertices);positions[i]=p;side_for[i]=side
            model.vertices.append([*[round(float(v),6) for v in p],ordered[0][0],ordered[1][0],round(ordered[1][1],7)])
        for triangle in triangles:
            ids=[int(i) for i in triangle[::-1]]
            if any(i not in vertices for i in ids):continue
            p=np.array([positions[i] for i in ids]);n=np.cross(p[1]-p[0],p[2]-p[0])
            if np.linalg.norm(n)<1e-8:continue
            side=side_for[ids[0]];role=2 if side=='R' else 3;center=p.mean(0);axis=int(np.argmax(np.abs(n)))
            tile=1 if shield or 'hammer' in asset and center[1]>7 else 2 if axis!=0 and center[1]>12 else 1 if center[1]>7 else 0
            uv=[]
            for x,y,z in p:
                u=(abs(x-(-5 if side=='R' else 5))+2)*4 if axis!=0 else (z+4)*4
                uv.append([(tile*64+max(1,min(62,u)))/256,max(1,min(254,y*4+14))/256])
            flat=[round(float(v),7) for q in [*uv,uv[-1]] for v in q];kind=4;color=-1
            if color_reference is not None and source_uv is not None:
                th,tw=color_reference.shape[:2];samples=source_uv[ids,:2];samples=np.vstack([samples,samples.mean(0)])
                rgb=color_reference[((samples[:,1]%1)*th).astype(int)%th,((samples[:,0]%1)*tw).astype(int)%tw].mean(0);red,green,blue=rgb
                luminance=float(rgb@[.2126,.7152,.0722])
                if red>green*1.35 and red>blue*1.15 and red>28:palette=(min(105,45+int(red*.25)),22+int(green*.15),29+int(blue*.15))
                else:
                    level=28 if luminance<18 else 38 if luminance<35 else 51 if luminance<65 else 72 if luminance<100 else 100 if luminance<150 else 137
                    palette=(level-3,level-1,level+2)
                color=0xff000000|palette[0]<<16|palette[1]<<8|palette[2];kind=8
            face=[*[vertices[i] for i in ids],vertices[ids[-1]],role,kind,*flat,color];model.faces.append(face)
            if kind==4 and tile in (0,1) and int(center[1]*4+14)%16<3:model.faces.append([*face[:5],5,*face[6:]])
            count+=1
    return {'asset':asset,'fileSHA256':hashlib.sha256(source.read_bytes()).hexdigest(),'sourceTriangles':sum(len(p[2]) for p in parts),'adaptedTriangles':count,'sides':list(sides),'textures':'source colour regions remapped to own MC vertex palette; source texture files not shipped'}
