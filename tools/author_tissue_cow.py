"""Authored, articulated three-key bovine anatomy. No image tracing or radial spikes.

Runtime coordinates are native ModelPart units; the editable Blockbench source uses
global Y-up model units. Run from the repository root. Only these owned files change.
"""
import json
import math
import uuid
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/blacklight'
ART = ROOT / 'art/tissue/cow'
REGIONS = []
PALETTE = [(118, 43, 52), (64, 31, 39), (43, 43, 46), (126, 122, 116),
           (151, 96, 96), (43, 15, 24), (82, 57, 57)]


def identity(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'blacklight/tissue/cow/' + name))


def region(name, part, reference, phase, origin=(0, 0, 0), body=False):
    r = dict(name=name, part=part, reference=reference, meshes=[])
    r['_phase'], r['_origin'], r['_body'] = phase, origin, body
    REGIONS.append(r)
    return r


def mesh(r, name, stages, faces, skin=False, motion='', pivot=(0, 0, 0), phase=None):
    assert len(stages) == 3 and len({len(s) for s in stages}) == 1
    m = dict(name=name, phase=r['_phase'] if phase is None else phase, skin=skin,
             stages=[[round(v, 5) for p in stage for v in p] for stage in stages], faces=faces)
    if motion:
        m.update(motion=motion, pivot=list(pivot))
    r['meshes'].append(m)
    return m


# Clockwise eight-point bevelled rectangle, viewed along its longitudinal axis.
RING = [(-1, -.65), (-.65, -1), (.65, -1), (1, -.65),
        (1, .65), (.65, 1), (-.65, 1), (-1, .65)]


def loft(r, name, keys, axis=1, skin=False, material=1, start=.04,
         motion='', pivot=(0, 0, 0), bevel=True, phase=None, face_style=None,
         square_initial=False, planar_caps=False):
    """Connected broad cross-sections. Shape keys preserve topology and muscle flow."""
    stages = []
    ring = RING if bevel else [(-1, -1), (1, -1), (1, 1), (-1, 1)]
    for stage, key in enumerate(keys):
        verts = []
        for centre, width, depth in key:
            for x, y in ring:
                # Duplicate corner vertices keep the initial native cube intact;
                # the bevel opens continuously as the same topology grows.
                if square_initial and stage == 0:
                    x, y = math.copysign(1, x), math.copysign(1, y)
                v = list(centre)
                a, b = ((0, 2) if axis == 1 else (0, 1))
                v[a] += width * x
                v[b] += depth * y
                verts.append(v)
        stages.append(verts)
    sides, count = len(ring), len(keys[0])
    faces = []
    for k in range(count-1):
        for j in range(sides):
            n = (j+1) % sides
            ids = [k*sides+j, (k+1)*sides+j, (k+1)*sides+n, k*sides+n]
            # Establish outward winding using the mature centre line.
            vs = stages[2]
            a, b, c = [vs[i] for i in ids[:3]]
            u, v = [b[t]-a[t] for t in range(3)], [c[t]-a[t] for t in range(3)]
            normal = (u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
            mid = [(keys[2][k][0][t]+keys[2][k+1][0][t])*.5 for t in range(3)]
            outward = [sum(vs[i][t] for i in ids)/4-mid[t] for t in range(3)]
            if sum(normal[t]*outward[t] for t in range(3)) < 0:
                ids.reverse()
            mat, begin, end = material, start, start+.26
            if face_style:
                mat, begin, end = face_style(k, j)
            faces.append(ids + [mat, round(begin, 4), round(end, 4)])
    # Large planar caps, triangulated around their centre. They also animate.
    for end in (0, count-1):
        if planar_caps:
            # Broad coplanar strips avoid a radial fan of independently mapped
            # triangles across the muzzle. The initial corner duplicates remain valid.
            polygons=[[0,1,2,3],[0,3,4,7],[7,4,5,6]] if sides==8 else [[0,1,2,3]]
            for polygon in polygons:
                ids=[end*sides+j for j in polygon]
                vs=stages[2];a,b,c=[vs[i] for i in ids[:3]]
                u,v=[b[t]-a[t] for t in range(3)],[c[t]-a[t] for t in range(3)]
                normal=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
                direction=keys[2][end][0][axis]-keys[2][1 if end==0 else count-2][0][axis]
                if normal[axis]*direction<0:ids.reverse()
                faces.append(ids+[material,start,start+.26])
            continue
        for s, key in enumerate(keys):
            stages[s].append(list(key[end][0]))
        centre = len(stages[0])-1
        for j in range(sides):
            ids = [end*sides+j, end*sides+(j+1)%sides, centre, centre]
            vs=stages[2]; a,b,c=[vs[i] for i in ids[:3]]
            u,v=[b[t]-a[t] for t in range(3)],[c[t]-a[t] for t in range(3)]
            normal=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
            direction=keys[2][end][0][axis]-keys[2][1 if end==0 else count-2][0][axis]
            if normal[axis]*direction<0:
                ids=[ids[2],ids[1],ids[0],ids[0]]
            faces.append(ids+[material,start,start+.26])
    return mesh(r,name,stages,faces,skin,motion,pivot,phase)


def box(r, name, keys, material, start=.12, motion='', pivot=(0, 0, 0), phase=None):
    # Keys: six bounds. Bevelled silhouette comes from loft, broad plates from boxes.
    stages=[]
    for lo,hi in keys:
        stages.append([[hi[0] if i&1 else lo[0],hi[1] if i&2 else lo[1],hi[2] if i&4 else lo[2]] for i in range(8)])
    faces=[[0,4,6,2],[1,3,7,5],[0,1,5,4],[2,6,7,3],[0,2,3,1],[4,5,7,6]]
    return mesh(r,name,stages,[f+[material,start,start+.24] for f in faces],False,motion,pivot,phase)


body=region('body','body',[-6,-10,-7,6,8,3],0,body=True)
# Body-local Y points to the rear, Z up. Hip remains compact; shoulders rise and broaden.
zs=[-10,-7,-3,2,6,8]
base=[((0,z,-2),6,5) for z in zs]
middle=[((0,z,-2),w,h) for z,w,h in zip(zs,[7,8.4,8,6.6,6,5.5],[5.8,7.5,6.5,5.3,5,4.5])]
mature=[((c,z,u),w,h) for z,c,u,w,h in zip(zs,[1.3,1.7,1.4,.5,0,0],[0,1.4,1,-1,-2.5,-2.8],[8.4,10.8,10.4,8,6.1,5.4],[6.8,9.5,8.7,6,4.8,4.5])]
def body_style(k,j):
    # Top and shoulder become shell and muscle; rear hide remains readable.
    dorsal=j in (4,5,6)
    dominant=j in (2,3,4)
    if k>=3 and j not in (4,5): return (0,0,1)
    if k>=3: return (3,.62,.95)
    if dorsal: return (3,.30+k*.04,.58+k*.04)
    return (1 if dominant else 2,.01 if dominant and k==1 else .12+k*.04,.35+k*.04)
loft(body,'ribcage', [base,middle,mature],skin=True,face_style=body_style)
# A local wound is already present in the young infected host; later mass grows around it.
lesion=[((6.08,-7,-1.3),.1,1),((6.25,-5.7,-1),.28,1.6),((6.1,-3.7,-1.2),.12,.7)]
loft(body,'initial_lesion',[lesion,lesion,lesion],material=1,start=-.28)
loft(body,'neck_bridge',[
    [((0,-9,-2),3.8,3),((0,-9.5,-2),3.8,3),((0,-10,-2),3.8,3)],
    [((0,-9,-1.8),4.8,3.6),((0,-10.5,-2),4.3,3.3),((0,-11,-2),3.9,3.1)],
    [((0,-9,-1),6,4.5),((0,-11,-1.5),5,3.6),((0,-12.8,-2),4.1,3.15)]
],material=1,start=.06)
# Plate silhouette follows shoulder blades, with large pieces and exposed red channels.
for side in (-1,1):
    for index,(front,rear,height) in enumerate([(-9,-4,9.2),(-3,2,8.2)]):
        scale=1 if side==1 else .82
        keys=[([side*.5,front*.45,-1],[side*.5+.3,rear*.45,0])]
        # Positive/negative bounds explicitly ordered, no mirror-winding accident.
        def plate(w,z0,z1,zheight,centre):
            a,b=sorted([side*centre,side*(centre+w)])
            return ([a,z0,zheight-2.2],[b,z1,zheight])
        keys=[plate(.5,front*.4,rear*.4,-1,.5),plate(3*scale,front,rear,4.5*scale,2.4),plate(4.6*scale,front,rear,height*scale,3.4)]
        box(body,f'scapula_{side}_{index}',keys,3,.18+index*.12)
# Raised rim and recessed floor: one gland behind the dominant shoulder.
def gland_box(name,lo,hi,mat):
    keys=[]
    for factor in (.01,.56,1):
        keys.append(([6+(lo[0]-6)*factor,-4+(lo[1]+4)*factor,-2+(lo[2]+2)*factor],
                     [6+(hi[0]-6)*factor,-4+(hi[1]+4)*factor,-2+(hi[2]+2)*factor]))
    box(body,name,keys,mat,.05)
gland_box('gland_floor',[12.7,-6.5,-3.5],[12.9,-2.7,-.5],6)
gland_box('gland_top',[12.8,-6.8,-.5],[13.5,-2.4,.2],3)
gland_box('gland_bottom',[12.8,-6.8,-4.2],[13.5,-2.4,-3.5],2)
gland_box('gland_front',[12.8,-6.8,-3.5],[13.5,-6.1,-.5],2)
gland_box('gland_back',[12.8,-3.1,-3.5],[13.5,-2.4,-.5],2)

for dominant in (True,False):
    phase=1 if dominant else 2
    side=1 if dominant else -1
    r=region('dominant_front' if dominant else 'secondary_front','left_front_leg' if dominant else 'right_front_leg',[-2,0,-2,2,12,2],phase,(4*side,12,-6))
    # Canonical dominant geometry points outward +X; secondary is reflected here.
    def c(x,y,z): return (side*x,y,z)
    factor=1 if dominant else .8
    upper0=[(c(0,0,0),2,2),(c(0,3,0),2,2),(c(0,6,0),2,2)]
    upper1=[(c(1.8,-4,0),3.9*factor,3.2),(c(2.8,0,1.1),3.4*factor,3.1),(c(2.8,5,1),2.7*factor,2.3)]
    upper2=[(c(4,-8,0),5.4*factor,4.5),(c(6,-3,2),4.7*factor,4),(c(6,2.7,2),3.6*factor,3)]
    loft(r,'upper_arm',[upper0,upper1,upper2],skin=True,material=1,start=.03)
    fore0=[(c(0,6,0),2,2),(c(0,9,0),2,2),(c(0,12,0),2,2)]
    fore1=[(c(2.8,4.7,1),2.9*factor,2.4),(c(3,8,-1),2.7*factor,2.3),(c(3,11.5,-2.1),3.1*factor,2.5)]
    fore2=[(c(6,2,2),3.8*factor,3.1),(c(6,6,-.8),3.5*factor,3.1),(c(5.8,10.5,-4.1),4.1*factor,3)]
    loft(r,'forearm',[fore0,fore1,fore2],skin=True,material=1,start=.07,motion='forearm',pivot=c(6,2,2))
    # Broad forearm dorsal blade; muscle channels stay visible on either side.
    keys=[]
    for amount in (0,.55,1):
        x=side*(.5+5.4*amount); width=.2+2.7*amount*factor
        keys.append(([x-width,6-3*amount,-1-3*amount],[x+width,10.8,-.5-1.3*amount]))
    box(r,'forearm_plate',keys,3,.18,motion='forearm',pivot=c(6,2,2))
    # Three genuine claw digits with knuckles and hooked tips, rooted inside the palm.
    for digit in range(3):
        x=(digit-1)*2.55
        initial=[(c(x*.18,10.5,-.6),.24,.3),(c(x*.18,11.3,-1),.22,.26),(c(x*.18,11.7,-1.5),.08,.1)]
        mid=[(c(3+x*.72,9.5,-3.2),.76,.8),(c(3+x*.82,10.4,-5.2),.68,.7),(c(3+x*.86,11.9,-6.5),.12,.12)]
        end=[(c(5.8+x,8.8,-5.4),1.12,1.2),(c(5.8+x*1.05,9.6,-8.7+(digit==1)*-.9),.96,1),(c(5.8+x*1.08,11.9,-10.2+(digit==1)*-1.1),.12,.14)]
        digit_mesh=loft(r,f'claw_{digit}',[initial,mid,end],axis=2,material=4,start=.23,motion='digit',pivot=c(5.8,8.8,-5.4),bevel=False)
        digit_mesh['parentPivot']=list(c(6,2,2))
    # Deltoid armour grows into the torso, rather than hanging from a thin socket.
    keys=[]
    for amount in (0,.55,1):
        x=side*(.6+4.5*amount);w=.2+3.9*amount*factor
        keys.append(([x-w,-.6-8*amount,-1.1-1.7*amount],[x+w,1.4-5*amount,1.4+2*amount]))
    box(r,'shoulder_plate',keys,3,.19)

for side in (-1,1):
    r=region('hind_right' if side<0 else 'hind_left','right_hind_leg' if side<0 else 'left_hind_leg',[-2,0,-2,2,12,2],3,(side*4,12,7))
    base=[((0,y,0),2,2) for y in (0,5,9,12)]
    mid=[((side*x,y,z),w,d) for x,y,z,w,d in [(1,0,0,2.6,2.6),(1.3,5,1.5,2.1,2.2),(.6,9,-.5,1.7,1.8),(0,12,-.5,2,2)]]
    mature=[((side*x,y,z),w,d) for x,y,z,w,d in [(1.5,-1,0,3.4,3),(2.4,4,2.8,2.6,2.3),(1,8.5,-1.2,1.8,1.7),(0,12,-1.8,2.2,2.3)]]
    def style(k,j): return (1,.16,.6) if j in (0,1,7) and k<2 else (0,0,1)
    loft(r,'bent_haunch',[base,mid,mature],skin=True,face_style=style)

# The head itself reconstructs, rather than keeping a vanilla cube under cheek props.
# Upper skull and mandible partition the original cube exactly at Y=1; their
# original skin UVs survive early growth. Both follow the real head/look joint.
head=region('head','head',[-4,-4,-6,4,4,0],0,(0,4,-8))
skull0=[((0,-1.5,z),4,2.5) for z in (0,-2,-4,-6)]
skull1=[((0,y,z),w,d) for y,z,w,d in [(-1.5,.3,4.8,3),(-1.6,-2.7,5,2.9),(-.8,-5.3,4.4,2.4),(-.25,-8,3.8,1.8)]]
skull2=[((0,y,z),w,d) for y,z,w,d in [(-1.25,.8,5.9,3.9),(-1.55,-3.6,6.2,3.45),(-.55,-7.2,4.9,2.55),(.2,-10.6,4,1.85)]]
def skull_style(k,j):
    if j in (0,1,2): return (3,.18+k*.05,.56+k*.04)
    if j in (4,5,6): return (2,.08,.55)
    return (1 if j==3 else 7,.10+k*.06,.64+k*.06)
loft(head,'reconstructed_skull',[skull0,skull1,skull2],axis=2,skin=True,
     face_style=skull_style,square_initial=True,planar_caps=True)

jaw0=[((0,2.5,z),4,1.5) for z in (0,-3,-6)]
jaw1=[((0,y,z),w,d) for y,z,w,d in [(2.4,.2,4.5,1.7),(3,-4.6,4.7,1.9),(3.1,-7.8,3.6,1.25)]]
jaw2=[((0,y,z),w,d) for y,z,w,d in [(3.15,.7,5.1,1.9),(3.9,-5.5,4.6,1.7),(3.8,-10.2,3.8,1.3)]]
def jaw_style(k,j):
    return (6 if j in (0,1,2) else 2 if j in (4,5,6) else 1,.10,.62)
jaw=loft(head,'mandible',[jaw0,jaw1,jaw2],axis=2,skin=True,face_style=jaw_style,
         motion='jaw',square_initial=True,planar_caps=True)
jaw['pivots']=[[0,1.8,-.1],[0,2,.1],[0,2.5,.35]]

# A recessed palate closes the mouth cavity as the hinged jaw opens. Broad
# surfaces and just two rooted fangs keep the face readable at Minecraft scale.
loft(head,'palate',[
    [((0,1.6,z),2.4,.1) for z in (-1,-3,-5)],
    [((0,1.8,z),3,.4) for z in (-1,-4,-7.5)],
    [((0,2.1,z),w,.65) for z,w in [(-1,3.7),(-5.5,3.8),(-10.1,3.2)]]
],axis=2,material=6,start=.16,bevel=False,planar_caps=True)
for side in (-1,1):
    # Native horns remain inside the widening skull; new horn geometry grows
    # through their roots, swept back instead of adding a crown of spikes.
    loft(head,f'grown_horn_{side}',[
        [((side*4.3,-3.8,-3.4),.15,.18),((side*4.4,-4.2,-3.3),.12,.14),((side*4.5,-4.5,-3.2),.04,.04)],
        [((side*4.7,-3.2,-2.7),.7,.7),((side*5.6,-4.7,-1.3),.45,.42),((side*5.8,-5.6,-.5),.07,.08)],
        [((side*5.2,-3.2,-1.8),1,.95),((side*6.7,-5.2,-.2),.6,.55),((side*7.1,-6.3,1),.08,.1)]
    ],material=4,start=.23,bevel=False,planar_caps=True)
    loft(head,f'upper_fang_{side}',[
        [((side*2.4,1.1,-4.8),.08,.1),((side*2.4,1.3,-4.8),.04,.05)],
        [((side*3.1,1.3,-6.9),.5,.5),((side*2.9,2.6,-7.2),.08,.1)],
        [((side*3.3,1.6,-8.9),.65,.7),((side*3.0,3.6,-9.4),.08,.1)]
    ],material=4,start=.28,bevel=False,planar_caps=True)


def texture():
    # Both the reference project and procedural hosts use the same material source.
    from build_tissue_atlas import build_texture
    build_texture(OUT/'textures/entity/tissue_anatomy.png')


def global_point(r,v):
    if r['_body']:
        return (v[0],24-(5-v[2]),2+v[1])
    if r['name']=='head':
        # The runtime head joint moves down/forward with growth. Match that
        # mature attachment pose in the editable reference, too.
        return (v[0],24-(v[1]+7),v[2]-10.6)
    o=r['_origin'];return (v[0]+o[0],24-(v[1]+o[1]),v[2]+o[2])


def bbmodel():
    elements=[];groups=[]
    for r in REGIONS:
        group=dict(name=r['name'],uuid=identity(r['name']),origin=list(global_point(r,(0,0,0))),rotation=[0,0,0],children=[])
        for m in r['meshes']:
            vs=m['stages'][2];name=r['name']+'/'+m['name'];uid=identity(name)
            vertices={str(i//3):list(global_point(r,vs[i:i+3])) for i in range(0,len(vs),3)}
            faces={}
            for i,f in enumerate(m['faces']):
                mat=int(f[4]);tile=max(0,mat-1)*16
                ids=[str(int(k)) for k in f[:4]];ids=list(dict.fromkeys(ids))
                faces[str(i)]=dict(vertices=ids,uv={v:[tile+(j in (1,2))*12,(j>=2)*12] for j,v in enumerate(ids)},texture=0)
            elements.append(dict(name=m['name'],uuid=uid,type='mesh',origin=[0,0,0],rotation=[0,0,0],vertices=vertices,faces=faces))
            group['children'].append(uid)
        groups.append(group)
    project=dict(meta=dict(format_version='5.0',model_format='free',box_uv=False),name='Blacklight bovine anatomical growth',
                 resolution=dict(width=128,height=32),elements=elements,outliner=groups,
                 textures=[dict(path=str(OUT/'textures/entity/tissue_anatomy.png'),name='tissue_anatomy.png',uuid=identity('texture'),id='0',width=128,height=32,uv_width=128,uv_height=32)])
    (ART/'cow_anatomy.bbmodel').write_text(json.dumps(project,ensure_ascii=False,separators=(',',':')),encoding='utf-8')


if __name__=='__main__':
    ART.mkdir(parents=True,exist_ok=True);texture()
    for r in REGIONS:
        if 'front' not in r['name']:continue
        side=1 if r['name'].startswith('dominant') else -1
        pivots=[[0,6,0],[side*2.8,4.7,1],[side*6,2,2]]
        for m in r['meshes']:
            if m.get('motion')=='forearm':m['pivots']=pivots
            elif m.get('motion')=='digit':
                m['parentPivots']=pivots
                m['pivots']=[[sum(stage[i*3+axis] for i in range(4))/4 for axis in range(3)] for stage in m['stages']]
    runtime=dict(format=1,species='minecraft:cow',regions=[{k:v for k,v in r.items() if not k.startswith('_')} for r in REGIONS])
    target=OUT/'tissue_blueprints/cow.json';target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(runtime,separators=(',',':')),encoding='utf-8');bbmodel()
    count=sum(len(m['faces']) for r in REGIONS for m in r['meshes'])
    print(f'{len(REGIONS)} regions; {sum(len(r["meshes"]) for r in REGIONS)} meshes; {count} quads; {target.stat().st_size} bytes')
