"""Continuous faceted surfaces. The atlas supplies pixels, never voxel geometry."""
from pathlib import Path
import json, math, uuid, base64, sys, shutil
import numpy as np

ROOT=Path(__file__).resolve().parent
OUT=ROOT/'model-v13';OUT.mkdir(exist_ok=True)
STAGE=sys.argv[1] if len(sys.argv)>1 else 'blockout'
old=json.loads((ROOT/'model-v12/sovereign_wings.asset.json').read_text())
# Bring the articulation assembly toward the scapula instead of leaving a long
# exposed inner lever. Keep the long blade silhouette and vertical proportions.
def compact_x(x):
    return math.copysign(6.67+(abs(x)-6.67)*.84,x) if abs(x)>6.67 else x
for b in old['bones']:
    if b['name']!='wing_mount':b['pivot'][0]=compact_x(b['pivot'][0])
for group in ('skin_paths','root_paths'):
    for path in old[group].values():
        for p in path:p[0]=compact_x(p[0])
bones=old['bones'];bybone={b['name']:b for b in bones};paths=old['skin_paths'];elements=[]
uid=lambda n:str(uuid.uuid5(uuid.NAMESPACE_URL,'blacklight-mesh-v13/'+n))

def distance(p,path):
    best=1e9
    for a,b in zip(path,path[1:]):
        a=np.array(a);v=np.array(b)-a;t=np.clip(np.dot(p-a,v)/max(1e-8,np.dot(v,v)),0,1)
        best=min(best,float(np.linalg.norm(p-a-v*t)))
    return best

def web_weights(p,side):
    nearest=sorted((distance(np.array(p),path),name) for name,path in paths.items() if name.startswith(side))[:2]
    return [nearest[0][1],nearest[1][1],nearest[0][0]/max(1e-8,nearest[0][0]+nearest[1][0])]

def mesh(name,verts,polys,mat,parent,weights=None,root=None):
    faces={}
    for number,ids in enumerate(polys):
        p=[np.array(verts[i]) for i in ids]
        axis=p[1]-p[0];axis/=max(1e-8,np.linalg.norm(axis))
        normal=np.cross(axis,p[2]-p[0]);normal/=max(1e-8,np.linalg.norm(normal));up=np.cross(normal,axis)
        uv=np.array([[np.dot(v-p[0],axis)*2,np.dot(v-p[0],up)*2] for v in p]);uv-=uv.min(axis=0)
        assert uv.max()<62,(name,uv.max())
        uv+=np.array([(mat%4)*64+1,(mat//4)*64+1])
        faces['f'+str(number)]={'vertices':['v'+str(i) for i in ids],'uv':{'v'+str(i):list(map(float,u)) for i,u in zip(ids,uv)},'texture':0}
    e={'name':name,'uuid':uid(name),'type':'mesh','origin':[0,0,0],'rotation':[0,0,0],
       'vertices':{'v'+str(i):list(map(float,p)) for i,p in enumerate(verts)},'faces':faces,
       'visibility':True,'_parent':parent,'_material':mat,
       '_weights':{'v'+str(i):w for i,w in enumerate(weights or [[parent,parent,0]]*len(verts))}}
    if root:e['_root_skin']=root
    elements.append(e)
    return e

def sweep(name,path,widths,parent,mat=1,thickness=.60,fixed=False,tangents=None):
    path=np.array(path,float);verts=[];weights=[];polys=[];travel=0
    ring=[(-.5,-.30),(-.30,-.5),(.30,-.5),(.5,-.30),(.5,.30),(.30,.5),(-.30,.5),(-.5,.30)]
    for i,p in enumerate(path):
        tangent=np.array(tangents[i]) if tangents is not None else path[min(i+1,len(path)-1)]-path[max(i-1,0)]
        cross=np.array([-tangent[1],tangent[0],0]);cross/=max(1e-8,np.linalg.norm(cross))
        if i:travel+=np.linalg.norm(p-path[i-1])
        parent_name=bybone[parent]['parent'] or parent
        blend=min(1,travel/1.5);blend=blend*blend*(3-2*blend)
        for x,z in ring:
            v=p+cross*x*widths[i]+np.array([0,0,z*widths[i]*thickness]);verts.append(v)
            weights.append([parent,parent,0] if fixed else [parent_name,parent,blend])
        if i:
            for j in range(8):polys.append([(i-1)*8+j,(i-1)*8+(j+1)%8,i*8+(j+1)%8,i*8+j])
    for end,reverse in [(0,True),(len(path)-1,False)]:
        center=len(verts);verts.append(path[end]);weights.append(weights[end*8])
        for j in range(8):
            ids=[center,end*8+j,end*8+(j+1)%8];polys.append(ids[::-1] if reverse else ids)
    return mesh(name,verts,polys,mat,parent,weights)

def earclip(poly):
    p=np.array(poly,float);idx=list(range(len(poly)));faces=[]
    signed=sum(p[i,0]*p[(i+1)%len(p),1]-p[(i+1)%len(p),0]*p[i,1] for i in range(len(p)))
    if signed<0:idx.reverse()
    cross=lambda a,b: a[0]*b[1]-a[1]*b[0]
    while len(idx)>3:
        for j,b in enumerate(idx):
            a,c=idx[j-1],idx[(j+1)%len(idx)];pa,pb,pc=p[a],p[b],p[c]
            if cross(pb-pa,pc-pb)<=1e-8:continue
            if any(cross(pb-pa,p[k]-pa)>=-1e-8 and cross(pc-pb,p[k]-pb)>=-1e-8 and cross(pa-pc,p[k]-pc)>=-1e-8 for k in idx if k not in (a,b,c)):continue
            faces.append([a,b,c]);idx.pop(j);break
        else:raise ValueError('Non-simple membrane outline')
    faces.append(idx);return faces

def membrane(name,poly,side,parent):
    # Triangulation is one welded sheet, with shared subdivision vertices for bending.
    p=[[compact_x((600-x)/17.125),(790-y)/17.125] for x,y in poly]
    tris=earclip(p);v=[np.array([side*x,y,5.1]) for x,y in p]
    for _ in range(3):
        cache={};nextfaces=[]
        def mid(a,b):
            key=tuple(sorted((a,b)))
            if key not in cache:cache[key]=len(v);v.append((v[a]+v[b])/2)
            return cache[key]
        for a,b,c in tris:
            ab,bc,ca=mid(a,b),mid(b,c),mid(c,a)
            nextfaces.extend([[a,ab,ca],[ab,b,bc],[ca,bc,c],[ab,bc,ca]])
        tris=nextfaces
    if side<0:tris=[t[::-1] for t in tris]
    return mesh(name,v,tris,4,parent,[web_weights(q,'left' if side>0 else 'right') for q in v])

def grown_root(side,label):
    # A flattened, broad scapular foot embedded in the torso, narrowing outward.
    # These rings are one sealed surface; every vertex remains chest-bound.
    stations=[(2.9,20.4,2.1,2.0,1.0),(3.7,20.6,2.7,2.4,1.8),
              (4.7,21.1,2.9,3.2,2.6),(5.7,22.3,3.65,3.0,2.4),
              (6.67,23.5,3.9,2.4,2.0),(7.2,24.0,4.15,1.8,1.6)]
    verts=[];faces=[]
    for row,(x,y,z,width,depth) in enumerate(stations):
        for i in range(10):
            angle=2*math.pi*i/10
            # Fleshy lobes are integrated into the cross-section, not added cubes.
            scallop=1+.10*math.cos(angle*3+row*.5)
            v=[side*x,y+math.cos(angle)*width*.5*scallop,z+math.sin(angle)*depth*.5]
            if abs(v[0])<5.0:v[1]=min(v[1],20.75)
            verts.append(v)
        if row:
            for i in range(10):faces.append([(row-1)*10+i,(row-1)*10+(i+1)%10,row*10+(i+1)%10,row*10+i])
    for row in (0,len(stations)-1):
        center=len(verts);verts.append([side*stations[row][0],stations[row][1],stations[row][2]])
        for i in range(10):faces.append([center,row*10+i,row*10+(i+1)%10])
    e=mesh(label+'_scapular_tissue',verts,faces,8,'wing_mount',
           [['wing_mount',label+'_root',0]]*len(verts),label+'_root')
    return e

def plate(name,a,b,width,parent,mat,face=1):
    a=np.array(a);b=np.array(b);d=b-a;side=np.array([-d[1],d[0],0]);side/=max(np.linalg.norm(side),1e-8)
    # A single tapered overlapping scale, with a raised faceted ridge.
    outline=[a-side*width*.40,a+side*width*.44,a+d*.68+side*width*.55,
             b,a+d*.72-side*width*.51]
    verts=outline+[a+d*.42+np.array([0,0,face*.26])]
    faces=[[i,(i+1)%5,5] for i in range(5)]
    return mesh(name,verts,faces,mat,parent,[web_weights(q,parent.split('_')[0]) for q in verts])

def surface_details(bone,path,widths,tangents):
    # Long continuous fissures and sparse broad overlapping plates retain the
    # uninterrupted silhouette; small cubes never supply surface texture.
    for face in (-1,1):
        vein=[]
        for i,(p,w) in enumerate(zip(path,widths)):
            t=np.array(tangents[i]);cross=np.array([-t[1],t[0],0]);cross/=max(1e-8,np.linalg.norm(cross))
            vein.append(np.array(p)+cross*w*(.15+.06*math.sin(i*2))+np.array([0,0,face*w*.49]))
        e=sweep(bone+f'_fissure_{face}',vein,[.065 if i==len(path)-1 else .105 for i in range(len(path))],bone,6,.55)
        e['_weights']={k:web_weights(v,bone.split('_')[0]) for k,v in e['vertices'].items()}
        for j in range(len(path)-1):
            if j%2:continue
            a=np.array(path[j]);b=np.array(path[min(j+2,len(path)-1)])
            w=widths[j]
            a[2]+=face*w*.36;b[2]+=face*widths[min(j+2,len(path)-1)]*.36
            plate(bone+f'_plate_{face}_{j}',a,b,w*1.06,bone,2+(j//2)%2,face)

def scapular_bed():
    # Thin integrated back surface; its rim penetrates the skin and its centre
    # rises into both roots. No rigid crossbar or replacement shoulder geometry.
    outline=[(-3.8,22,1.65),(-2.4,22.1,1.7),(0,21.7,1.65),(2.4,22.1,1.7),
             (3.8,22,1.65),(3.8,20.3,1.65),(2.2,19.7,1.65),(0,19.1,1.65),
             (-2.2,19.7,1.65),(-3.8,20.3,1.65)]
    verts=outline+[(x*.7,20.8+(y-20.8)*.7,2.5+.12*math.cos(i*1.7)) for i,(x,y,z) in enumerate(outline)]+[(0,20.8,2.6)]
    faces=[]
    for i in range(10):faces.extend([[i,(i+1)%10,(i+1)%10+10,i+10],[i+10,(i+1)%10+10,20]])
    mesh('scapular_flesh_bed',verts,faces,8,'wing_mount',[['wing_mount','left_root' if p[0]>=0 else 'right_root',0] for p in verts],'left_root')

# Keep the original traced outline, but give each segment a continuous cross-section.
rib_widths=[[1.1,1.15,1.3,1.5,1.45,1.65,1.8,1.9,2.1],[1.1,1.0,1.2,1.4,1.3,1.7,1.9,2.2],[1.0,1.1,1.3,1.4,1.2,1.9,2.1],[.95,1.0,1.1,1.2,1.4,1.7,1.8]]
ray_widths=[[2.1,1.8,1.7,1.8,2.0,1.9,2.1,1.6,1.0,.04],[2.2,1.8,2.2,2.3,2,1.7,.95,.04],[2.1,1.5,1.6,2.1,1.75,2.8,2.0,.04],[1.8,1.3,1.5,1.65,1.9,1.15,.04]]
panels=[[(150,248),(240,240),(214,285),(197,346),(175,409),(157,477),(143,511),(138,477),(125,491),(120,448),(100,458),(102,402),(97,354),(126,292)],
[(214,285),(303,300),(303,373),(283,455),(268,525),(258,609),(250,650),(233,602),(224,631),(225,563),(205,585),(205,529),(176,548),(175,470),(197,346)],
[(303,300),(329,327),(339,406),(350,490),(370,586),(395,699),(378,667),(369,617),(350,647),(341,596),(319,612),(311,550),(293,556),(283,455),(303,373)]]
scapular_bed()
for side,label in [(1,'left'),(-1,'right')]:
    grown_root(side,label)
    if STAGE!='blockout':
        for n in range(3):
            line=[[side*3.6,19.6+n*.4,2.7],[side*4.8,20.3+n*.35,3.7],
                  [side*5.8,21.6+n*.4,4.6],[side*6.6,23+n*.4,4.8]]
            e=sweep(label+f'_root_muscle_{n}',line,[.20,.42,.46,.16],'wing_mount',8,.65,True)
            e['_root_skin']=label+'_root'
            for k in e['_weights']:e['_weights'][k]=['wing_mount',label+'_root',0]
            seam=[[p[0],p[1]+.16,p[2]+.13] for p in line]
            e=sweep(label+f'_root_capillary_{n}',seam,[.045,.06,.08,.035],'wing_mount',6,.6,True)
            e['_root_skin']=label+'_root'
            for k in e['_weights']:e['_weights'][k]=['wing_mount',label+'_root',0]
    for i in range(4):
        up=rib_widths[i];down=ray_widths[i];splitup=max(2,len(up)//2);splitdown=max(2,len(down)//2)
        full=np.array(paths[f'{label}_rib_{i+1}']+paths[f'{label}_arch_{i+1}'][1:]+paths[f'{label}_finger_{i+1}'][1:]+paths[f'{label}_tip_{i+1}'][1:])
        directions={tuple(p):full[min(j+1,len(full)-1)]-full[max(j-1,0)] for j,p in enumerate(full)}
        for part,widths in [('rib',up[:splitup+1]),('arch',up[splitup:]),('finger',down[:splitdown+1]),('tip',down[splitdown:])]:
            bone=f'{label}_{part}_{i+1}';path=paths[bone]
            widths=[w*1.35 for w in widths];tangents=[directions[tuple(p)] for p in path]
            sweep(bone+'_continuous',path,widths,bone,1,.65,tangents=tangents)
            if STAGE!='blockout':surface_details(bone,path,widths,tangents)
    for i,p in enumerate(panels):membrane(label+f'_membrane_{i+1}',p,side,label+f'_finger_{i+1}')
    if STAGE!='blockout':
        for j,(shape,bone) in enumerate([
            ([(8,25,5),(8.8,28,5.4),(9.1,30,5.5),(8.5,32.2,5.7),(10,34,5.7)],label+'_rib_1'),
            ([(13,30,5.5),(12.3,33,5.9),(13.5,35,6),(13.4,37,6)],label+'_arch_1'),
            ([(20,39,5.5),(22.5,38.7,5.5),(24,36.5,5.2),(25.5,35.5,5)],label+'_finger_1'),
            ([(9,26,4.7),(10,24.7,4.9),(11.2,23.8,5.0),(10.8,22.0,4.9)],label+'_rib_4')]):
            p=[[side*compact_x(x),y,z] for x,y,z in shape]
            e=sweep(label+f'_organic_hook_{j}',p,[.6]+[.35]*(len(p)-2)+[.025],bone,1,.7)
            e['_weights']={k:[bone,bone,0] for k in e['vertices']}

for f in ['sovereign_wings.png','sovereign_wings_emissive.png']:shutil.copy2(ROOT/'model-v12'/f,OUT/f)
data={k:v for k,v in old.items() if k!='elements'};data.update(elements=elements,stage=STAGE,geometry='continuous_mesh')
stem='blockout' if STAGE=='blockout' else 'sovereign_wings'
(OUT/(stem+'.asset.json')).write_text(json.dumps(data,separators=(',',':')),encoding='utf8')
def outline(name):return {'uuid':bybone[name]['uuid'],'isOpen':False,'children':[outline(b['name']) for b in bones if b['parent']==name]+[e['uuid'] for e in elements if e['_parent']==name]}
bb={'meta':{'format_version':'5.0','model_format':'free','box_uv':False},'name':'sovereign_wings_continuous','resolution':{'width':256,'height':256},
    'elements':[{k:v for k,v in e.items() if not k.startswith('_')} for e in elements],
    'groups':[{'name':b['name'],'uuid':b['uuid'],'origin':b['pivot'],'rotation':[0,0,0]} for b in bones],
    'outliner':[outline('wing_mount')],'textures':[{'name':'sovereign_wings.png','id':'0','uuid':uid('texture'),'source':'data:image/png;base64,'+base64.b64encode((OUT/'sovereign_wings.png').read_bytes()).decode(),'mode':'bitmap','uv_width':256,'uv_height':256}],'animations':[]}
(OUT/(stem+'.bbmodel')).write_text(json.dumps(bb,separators=(',',':')),encoding='utf8')
counts={'stage':STAGE,'meshes':len(elements),'cuboids':0,'vertices':sum(len(e['vertices']) for e in elements),'triangles':sum(len(f['vertices'])-2 for e in elements for f in e['faces'].values())}
(OUT/(stem+'.metrics.json')).write_text(json.dumps(counts,indent=2));print(counts)
