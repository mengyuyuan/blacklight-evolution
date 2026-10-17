"""Continuous faceted surfaces. The atlas supplies pixels, never voxel geometry."""
from pathlib import Path
import json, math, uuid, base64, sys, shutil
import numpy as np

ROOT=Path(__file__).resolve().parent
OUT=ROOT/'model-v15';OUT.mkdir(exist_ok=True)
STAGE=sys.argv[1] if len(sys.argv)>1 else 'blockout'
old=json.loads((ROOT/'model-v12/sovereign_wings.asset.json').read_text())
# Native units are traced from the user's 1983x793 front panel. The rear profile
# supplies layered thickness; hidden undersides remain reconstruction assumptions.
def compact_x(x):
    return x
def traced(x,y,z=5.0):return [6.67+(402-x)*.070,23.5+(300-y)*.070,z]
up_traces=[[(402,300),(363,278),(329,247),(303,223),(278,184),(255,144),(239,104),(230,58)],
           [(395,303),(361,280),(330,257),(298,230),(273,196),(250,163),(223,132)],
           [(407,307),(373,291),(345,277),(313,252),(288,224),(260,195)],
           [(414,300),(380,296),(355,288),(327,266),(300,245),(270,224)]]
down_traces=[[(230,58),(199,102),(167,118),(137,161),(113,186),(92,239),(75,286),(57,338),(40,399),(23,469),(16,513)],
             [(223,132),(201,165),(181,216),(165,260),(146,298),(129,347),(112,401),(95,469),(80,542),(70,631)],
             [(260,195),(243,223),(232,268),(224,327),(216,378),(208,453),(195,529),(193,605),(200,725)],
             [(270,224),(265,274),(270,326),(278,379),(292,442),(307,513),(321,659)]]
for side,label in [(1,'left'),(-1,'right')]:
    for i,(up,down) in enumerate(zip(up_traces,down_traces)):
        arch_depth=[9.4,7.8,6.25,5.0][i]
        upper=[traced(x,y,4.35+(j/(len(up)-1))*(arch_depth-4.35)) for j,(x,y) in enumerate(up)]
        lower=[traced(x,y,upper[-1][2]-(j/(len(down)-1))*.95) for j,(x,y) in enumerate(down)]
        a=len(up)//2;b=len(down)//2
        for part,points in [('rib',upper[:a+1]),('arch',upper[a:]),('finger',lower[:b+1]),('tip',lower[b:])]:
            name=f'{label}_{part}_{i+1}';points=[[side*x,y,z] for x,y,z in points]
            old['skin_paths'][name]=points
            next(b for b in old['bones'] if b['name']==name)['pivot']=points[0]
# The retraced middle blades are longer than the earlier cuboid design. Curl
# their lower finger joints back when resting, keeping the open silhouette intact.
def quaternion_product(a,b):
    x,y,z,w=a;X,Y,Z,W=b
    return [w*X+x*W+y*Z-z*Y,w*Y-x*Z+y*W+z*X,w*Z+x*Y-y*X+z*W,w*W-x*X-y*Y-z*Z]
angle=math.radians(32)/2
for pose in old['fold_poses'].values():
    for side in ('left','right'):
        for number in (2,3):
            name=f'{side}_finger_{number}'
            pose[name]=quaternion_product(pose[name],[math.sin(angle),0,0,math.cos(angle)])
bones=old['bones'];bybone={b['name']:b for b in bones};paths=old['skin_paths'];elements=[]
uid=lambda n:str(uuid.uuid5(uuid.NAMESPACE_URL,'blacklight-mesh-v15/'+n))
segments={}
for side in ('left','right'):
    names=[];offsets=[];a=[];b=[]
    for name,path in paths.items():
        if not name.startswith(side):continue
        names.append(name);offsets.append(len(a))
        for start,end in zip(path,path[1:]):a.append(start);b.append(end)
    segments[side]=(names,np.array(offsets),np.array(a),np.array(b))

def nearest_paths(point,side,dimensions=3):
    names,offsets,a,b=segments[side];d=b[:,:dimensions]-a[:,:dimensions]
    t=np.clip(np.einsum('ij,ij->i',np.asarray(point[:dimensions])-a[:,:dimensions],d)/np.maximum(1e-8,np.einsum('ij,ij->i',d,d)),0,1)
    dist=np.linalg.norm(np.asarray(point[:dimensions])-a[:,:dimensions]-d*t[:,None],axis=1)
    minima=np.minimum.reduceat(dist,offsets);first,second=np.argsort(minima)[:2]
    return names,offsets,a,b,t,dist,minima,first,second

def distance(p,path):
    best=1e9
    for a,b in zip(path,path[1:]):
        a=np.array(a);v=np.array(b)-a;t=np.clip(np.dot(p-a,v)/max(1e-8,np.dot(v,v)),0,1)
        best=min(best,float(np.linalg.norm(p-a-v*t)))
    return best

def web_weights(p,side):
    names,_,_,_,_,_,minimum,a,b=nearest_paths(p,side)
    return [names[a],names[b],float(minimum[a]/max(1e-8,minimum[a]+minimum[b]))]

def surface_depth(point,side):
    names,offsets,a,b,t,dist,minimum,first,second=nearest_paths(point,side,2)
    depths=[]
    for bone in (first,second):
        start=offsets[bone];end=offsets[bone+1] if bone+1<len(offsets) else len(a)
        index=start+int(np.argmin(dist[start:end]));depths.append(a[index,2]+(b[index,2]-a[index,2])*t[index])
    weight=minimum[first]/max(1e-8,minimum[first]+minimum[second]);return float(depths[0]*(1-weight)+depths[1]*weight)

def mesh(name,verts,polys,mat,parent,weights=None,root=None):
    faces={}
    for number,ids in enumerate(polys):
        p=[np.array(verts[i]) for i in ids]
        if np.linalg.norm(np.cross(p[1]-p[0],p[2]-p[0]))<.00001:continue
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

def membrane(name,poly,side,parent,holes):
    # Triangulation is one welded sheet, with shared subdivision vertices for bending.
    p=[traced(x,y)[:2] for x,y in poly]
    tris=earclip(p);v=[np.array([side*x,y,5.1]) for x,y in p]
    for _ in range(2):
        cache={};nextfaces=[]
        def mid(a,b):
            key=tuple(sorted((a,b)))
            if key not in cache:cache[key]=len(v);v.append((v[a]+v[b])/2)
            return cache[key]
        for a,b,c in tris:
            ab,bc,ca=mid(a,b),mid(b,c),mid(c,a)
            nextfaces.extend([[a,ab,ca],[ab,b,bc],[ca,bc,c],[ab,bc,ca]])
        tris=nextfaces
    # Cut actual apertures into the welded sheet. These are geometry openings,
    # so silhouettes and shadows remain open from both sides, without alpha cards.
    def clip(points,a,b,inside):
        out=[]
        sign=lambda p:(b[0]-a[0])*(p[1]-a[1])-(b[1]-a[1])*(p[0]-a[0])
        for prev,cur in zip(points[-1:]+points[:-1],points):
            dp,dc=sign(prev),sign(cur);pin=dp>=-1e-9 if inside else dp<=1e-9;cin=dc>=-1e-9 if inside else dc<=1e-9
            if pin!=cin:out.append(prev+(cur-prev)*(dp/(dp-dc)))
            if cin:out.append(cur)
        return out
    pieces=[[v[i] for i in t] for t in tris]
    for cx,cy,rx,ry in holes:
        hole=[]
        for a in [i*math.pi/5 for i in range(10)]:
            irregular=1+.16*math.sin(a*3+cx)+.07*math.sin(a*5+cy)
            p=traced(cx+rx*math.cos(a)*irregular,cy+ry*math.sin(a)*irregular,5.1);p[0]*=side;hole.append(np.array(p))
        if side<0:hole.reverse()
        remaining=[]
        for piece in pieces:
            current=piece
            for a,b in zip(hole,hole[1:]+hole[:1]):
                if len(current)<3:break
                outside=clip(current,a,b,False)
                if len(outside)>=3:remaining.append(outside)
                current=clip(current,a,b,True)
        pieces=remaining
    v=[];tris=[];lookup={}
    def index(p):
        key=tuple(round(float(x),7) for x in p)
        if key not in lookup:lookup[key]=len(v);v.append(p)
        return lookup[key]
    for piece in pieces:
        ids=[index(p) for p in piece]
        for i in range(1,len(ids)-1):
            a,b,c=[v[k] for k in (ids[0],ids[i],ids[i+1])]
            if np.linalg.norm(np.cross(b-a,c-a))>1e-7:tris.append([ids[0],ids[i],ids[i+1]])
    for point in v:point[2]=surface_depth(point,'left' if side>0 else 'right')
    if side<0:tris=[t[::-1] for t in tris]
    return mesh(name,v,tris,5,parent,[web_weights(q,'left' if side>0 else 'right') for q in v])

def grown_root(side,label):
    # A flattened, broad scapular foot embedded in the torso, narrowing outward.
    # These rings are one sealed surface; every vertex remains chest-bound.
    stations=[(1.3,19.3,2.1,2.6,.35),(2.8,20.0,2.4,2.5,.75),
              (4.2,20.6,2.6,2.8,1.1),(5.7,22.3,3.65,3.0,2.4),
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
    # Reference plates have blunt layered ends and clipped corners, not diamonds.
    outline=[a-side*width*.30,a+side*width*.34,a+d*.12+side*width*.48,
             a+d*.85+side*width*.48,b+side*width*.27,b-side*width*.34,
             a+d*.84-side*width*.49,a+d*.12-side*width*.49]
    center=a+d*.48
    verts=outline+[center+(p-center)*.88+np.array([0,0,face*.14]) for p in outline]
    faces=[[i,(i+1)%8,(i+1)%8+8,i+8] for i in range(8)]+[[8,9,10,11],[8,11,12,15],[12,13,14,15]]
    return mesh(name,verts,faces,mat,parent,[web_weights(q,parent.split('_')[0]) for q in verts])

def seam(name,path,width,parent,mat=6):
    verts=[];faces=[]
    for i,p in enumerate(path):
        p=np.array(p);d=np.array(path[min(i+1,len(path)-1)])-path[max(i-1,0)]
        side=np.array([-d[1],d[0],0]);side/=max(1e-8,np.linalg.norm(side))
        taper=.4+.6*math.sin(math.pi*(i+.5)/len(path))
        verts.extend([p-side*width*.5*taper,p+side*width*.5*taper])
        if i:faces.append([(i-1)*2,(i-1)*2+1,i*2+1,i*2])
    return mesh(name,verts,faces,mat,parent,[web_weights(v,parent.split('_')[0]) for v in verts])

def braid_roots(side,label):
    # Three distinct routes traced across the front root cluster, with actual
    # crossing depth. No fringe extending down the player's armpits.
    routes=[[(408,306),(385,298),(365,280),(345,278),(322,249),(300,247),(279,217),(260,195)],
            [(405,300),(383,282),(374,266),(349,251),(334,224),(310,213),(287,186),(273,166),(255,144)],
            [(398,305),(383,310),(364,299),(352,292),(333,286),(314,264),(294,255),(270,224)]]
    for face in (-1,1):
        for j,route in enumerate(routes):
            p=[]
            for i,(x,y) in enumerate(route):
                point=traced(x,y);point[0]*=side
                point[2]=surface_depth(point,label)+face*(.65+.18*math.sin(i*1.9+j));p.append(point)
            bone=label+'_rib_'+str(j+1)
            e=sweep(label+f'_root_braid_{face}_{j}',p,[.25]+[.55+.12*math.sin(i*2+j) for i in range(1,len(p)-1)]+[.24],bone,8,.85)
            e['_weights']={k:web_weights(v,label) for k,v in e['vertices'].items()}
            glow=[[x,y,z+face*.22] for x,y,z in p]
            seam(label+f'_braid_vein_{face}_{j}',glow,.12,bone)
            for n in (2,5):
                if n>=len(p)-1:continue
                q=np.array(glow[n]);branch=[q,q+np.array([side*.45,.45,face*.02]),q+np.array([side*.9,.3,face*.04])]
                seam(label+f'_braid_branch_{face}_{j}_{n}',branch,.085,bone)

def surface_details(bone,path,widths,tangents):
    # Long continuous fissures and sparse broad overlapping plates retain the
    # uninterrupted silhouette; small cubes never supply surface texture.
    for face in (-1,1):
        vein=[]
        for i,(p,w) in enumerate(zip(path,widths)):
            t=np.array(tangents[i]);cross=np.array([-t[1],t[0],0]);cross/=max(1e-8,np.linalg.norm(cross))
            vein.append(np.array(p)+cross*w*(.15+.06*math.sin(i*2))+np.array([0,0,face*w*.49]))
        broken=[]
        for j,(a,b) in enumerate(zip(vein,vein[1:])):
            broken.append(a)
            d=b-a;cross=np.array([-d[1],d[0],0]);cross/=max(1e-8,np.linalg.norm(cross))
            # Short branching fissures zigzag across the plate surface. The old
            # one-midpoint line read as a uniformly illuminated cable in game.
            for fraction,offset in ((.28,.17),(.57,-.12),(.78,.09)):
                middle=a+d*fraction+cross*offset*(1+.25*math.sin(j*2.3+len(bone)))
                broken.append(middle)
        broken.append(vein[-1]);seam(bone+f'_fissure_{face}',broken,.13,bone)
        for n in range(2,len(broken)-2,5):
            a=np.array(broken[n]);sign=-1 if n%2 else 1
            b=a+np.array([sign*.28,.3,face*.03]);c=b+np.array([sign*.25,-.12,face*.02])
            seam(bone+f'_branch_seam_{face}_{n}',[a,b,c],.07,bone)
        lengths=[0]
        for a,b in zip(path,path[1:]):lengths.append(lengths[-1]+np.linalg.norm(np.array(b)-a))
        def along(s):
            j=min(len(path)-2,next((i for i in range(len(path)-1) if lengths[i+1]>=s),len(path)-2))
            t=(s-lengths[j])/max(1e-8,lengths[j+1]-lengths[j])
            return np.array(path[j])*(1-t)+np.array(path[j+1])*t,widths[j]*(1-t)+widths[j+1]*t
        at=.25;j=0;stop=lengths[-1]-(3 if '_tip_' in bone else .2)
        while at<stop-.6:
            end=min(at+1.8+(j%3)*.25,stop)
            a,w=along(at);b,w2=along(end)
            d=b-a;cross=np.array([-d[1],d[0],0]);cross/=max(1e-8,np.linalg.norm(cross))
            shift=(-1 if j%2 else 1)*w*.12
            a+=cross*shift;b+=cross*shift
            a[2]+=face*w*.34;b[2]+=face*w2*.34
            plate(bone+f'_plate_{face}_{j}',a,b,min(w,w2)*.92,bone,11 if j%4==0 else 3,face)
            at=end+.3;j+=1

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
rib_widths=[[1.05,1.15,1.0,1.5,1.3,1.7,1.5,2.1],[1.1,1.25,1.05,1.6,1.35,1.7,1.9],[1.0,1.1,1.45,1.1,1.5,1.85],[.95,1.15,1.0,1.3,1.15,1.75]]
ray_widths=[[2.1,1.55,1.8,1.65,2.05,1.8,2.1,1.65,1.8,.95,.025],[1.9,1.65,2,1.85,2.2,1.7,2.1,1.75,1.3,.025],
            [1.85,1.55,1.8,1.65,2.1,1.8,2.25,1.45,.025],[1.75,1.45,1.6,1.75,1.9,1.55,.025]]
panels=[[(137,161),(167,118),(190,135),(201,165),(181,216),(165,260),(146,298),(129,347),(112,401),(84,448),(64,465),(90,402),(97,350),(99,304),(75,286),(92,239),(113,186)],
[(223,132),(242,163),(260,195),(243,223),(232,268),(224,327),(216,378),(208,453),(195,529),(174,544),(171,519),(151,533),(150,504),(130,514),(131,466),(119,458),(112,401),(129,347),(146,298),(165,260),(181,216),(201,165)],
[(260,195),(270,224),(265,274),(270,326),(278,379),(292,442),(307,513),(284,495),(280,476),(266,477),(253,461),(242,471),(228,445),(208,453),(216,378),(224,327),(232,268),(243,223)]]
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
            rootline=[[p[0],p[1]+.16,p[2]+.13] for p in line]
            e=sweep(label+f'_root_capillary_{n}',rootline,[.045,.06,.08,.035],'wing_mount',6,.6,True)
            e['_root_skin']=label+'_root'
            for k in e['_weights']:e['_weights'][k]=['wing_mount',label+'_root',0]
    for i in range(4):
        up=rib_widths[i];down=ray_widths[i];splitup=max(2,len(up)//2);splitdown=max(2,len(down)//2)
        full=np.array(paths[f'{label}_rib_{i+1}']+paths[f'{label}_arch_{i+1}'][1:]+paths[f'{label}_finger_{i+1}'][1:]+paths[f'{label}_tip_{i+1}'][1:])
        directions={tuple(p):full[min(j+1,len(full)-1)]-full[max(j-1,0)] for j,p in enumerate(full)}
        for part,widths in [('rib',up[:splitup+1]),('arch',up[splitup:]),('finger',down[:splitdown+1]),('tip',down[splitdown:])]:
            bone=f'{label}_{part}_{i+1}';path=paths[bone]
            widths=[w*(1.0 if part in ('rib','arch') else 1.12) for w in widths];tangents=[directions[tuple(p)] for p in path]
            if part=='arch':widths[-1]*=1.12
            sweep(bone+'_continuous',path,widths,bone,1,.65,tangents=tangents)
            if STAGE!='blockout':surface_details(bone,path,widths,tangents)
    apertures=[[(166,193,14,25),(125,310,8,20)],[(214,238,12,26),(177,355,10,18),(166,433,10,23),(184,394,5,10),(170,312,4,9)],[(241,397,10,24),(255,481,5,12),(250,327,11,28)]]
    for i,p in enumerate(panels):membrane(label+f'_membrane_{i+1}',p,side,label+f'_finger_{i+1}',apertures[i])
    if STAGE!='blockout':
        braid_roots(side,label)
        for j,(shape,bone) in enumerate([
            ([(8,25,5),(8.8,28,5.4),(9.1,30,5.5),(8.5,32.2,5.7),(10,34,5.7)],label+'_rib_1'),
            ([(13,30,5.5),(12.3,33,5.9),(13.5,35,6),(13.4,37,6)],label+'_arch_1'),
            ([(20,39,5.5),(22.5,38.7,5.5),(24,36.5,5.2),(25.5,35.5,5)],label+'_finger_1'),
            ([(9,26,4.7),(10,24.7,4.9),(11.2,23.8,5.0),(10.8,22.0,4.9)],label+'_rib_4')]):
            p=[[side*compact_x(x),y,z] for x,y,z in shape]
            # Attach to the retraced bone, not a former candidate's coordinates.
            # The second station is past the root blending zone and uses this
            # bone exclusively, matching the hook's rigid attachment weights.
            offset=np.array(paths[bone][1])-p[0]
            p=[list(np.array(point)+offset) for point in p]
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
