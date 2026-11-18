"""Historical authored Greene bind/atlas generator, not the runtime model.

Do not publish this script's geometry as a replacement for the runtime asset.
Its original independent weakpoint ring is retained only as historical authored
input/material provenance; rebind_greene_source.py replaces that ring with the
three original shared-pose Slug segment regions and must supply runtime output.

The supplied P1 render is a proportion/silhouette reference only. Every box below
is an authored anatomical volume. No voxelization, source vertices or source
textures are used. Material pixels come from this project's accepted Hunter.
"""
import hashlib
import json
import math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/blacklight'
SCALE = 4
ATLAS = 512
source = Image.open(ASSETS / 'textures/entity/supreme_hunter_block.png').convert('RGB')
# Explicit anatomical material swatches from the accepted Supreme Hunter atlas.
patches = {
    'hide': source.crop((143, 37, 157, 45)),
    'fold': source.crop((81, 75, 91, 79)),
    'muscle': source.crop((185, 1, 192, 15)),
    'sinew': source.crop((46, 58, 49, 65)),
    'scar': source.crop((106, 58, 114, 64)),
    'joint': source.crop((185, 1, 192, 15)).point(lambda n:int(n*.48)),
}
# The crown is living tissue, so keep the accepted hide's pixel structure while
# shifting its midtones toward the same red muscle family, without rib motifs.
patches['tissue']=patches['hide'].copy()
for py in range(patches['tissue'].height):
    for px in range(patches['tissue'].width):
        r,g,b=patches['hide'].getpixel((px,py))
        patches['tissue'].putpixel((px,py),(min(255,r+5),max(0,g-12),max(0,b-6)))
bones = []
by_name = {}
parts = []

def bone(name, parent, pivot):
    b = {'name': name, 'pivot': [round(n*SCALE, 4) for n in pivot], 'cubes': []}
    if parent:
        b['parent'] = parent
    bones.append(b)
    by_name[name] = b

def box(name, owner, center, size, material='hide', rotation=None):
    c = {'origin': [round((p-s/2)*SCALE, 4) for p, s in zip(center, size)],
         'size': [round(s*SCALE, 4) for s in size]}
    if rotation and any(rotation):
        c['pivot'] = [round(p*SCALE, 4) for p in center]
        c['rotation'] = [round(r, 4) for r in rotation]
    by_name[owner]['cubes'].append(c)
    parts.append((name, owner, c, material))
    return c

def beam(name, owner, a, b, width, depth, material='hide'):
    d = [b[i]-a[i] for i in range(3)]
    return box(name, owner, [(a[i]+b[i])/2 for i in range(3)],
               [width, math.dist(a,b)+.12, depth], material,
               [-math.degrees(math.atan2(math.hypot(d[0],d[2]),d[1])),
                math.degrees(math.atan2(d[0],d[2])), 0])

bone('root', None, [0,0,0])
bone('torso', 'root', [0,16,1])
bone('head', 'torso', [.8,25,-4])
bone('left_arm', 'torso', [-5,26,1])
bone('right_arm', 'torso', [6,24,1])

# A load-bearing vertebral column. Red intervertebral tissue stays visible
# between offset hide plates, as on the accepted Hunter's armored joints.
beam('spinal_cord', 'root', [0,.4,1.2], [-.5,18.2,1.8], 3.7, 3.5, 'muscle')
for i in range(7):
    y = 1.5+i*2.35
    x = -.45*math.sin(i*.8)
    z = 1.35+.3*math.sin(i*.5)
    w = 4.8-.18*i
    box('vertebra_%s'%i, 'root', [x,y,z], [w,1.65,4.3], 'fold', [3,0,(-1)**i*4])
    box('vertebra_front_%s'%i, 'root', [x,y+.2,z-2], [2.2,1.55,1.1], 'hide', [-13,0,0])
    for side in [-1,1]:
        box('transverse_%s_%s'%(i,side), 'root', [x+side*(w/2+.15),y+.25,z+.55],
            [1.65,.85,1.9], 'hide', [0,side*14,side*-18])

# Interlocking muscle masses, not a spherical eye or a uniform box grid.
shell = [
 ('rear_cranium',[-.4,24.2,1.9],[10.2,11.5,5.7],'fold',[6,0,-5]),
 ('crown_left',[-1.5,29.3,.25],[5.8,4.5,6.1],'tissue',[6,-6,-20]),
 ('crown_right',[2.1,28.8,1],[4.8,4.6,5.9],'tissue',[-8,8,24]),
 ('left_temple',[-5.2,25.2,.1],[4.3,8.1,5.5],'hide',[0,-9,-16]),
 ('left_frontal',[-2.9,26.8,-2.65],[4.9,5.7,3.2],'scar',[8,-10,-14]),
 ('left_cheek',[-4.45,21.6,-2.0],[4.5,6.6,4.2],'fold',[-8,-12,11]),
 ('left_hanging_lobe',[-5.0,18.4,-2.3],[2.7,4.5,3.1],'hide',[-12,4,9]),
 ('left_lower_scar',[-3.7,19.2,-3.65],[2.3,3.8,1.1],'muscle',[-8,0,-19]),
 ('right_mass',[5.1,23.5,.6],[5.9,8.8,6.7],'fold',[-4,-9,14]),
 ('right_cheek',[5.3,23,-2.1],[4.7,6.2,3.7],'tissue',[7,-13,29]),
 ('right_jowl',[4.3,19.6,-1.65],[5.3,3.6,4.9],'tissue',[10,4,-25]),
 ('bottom_fold',[.7,18.3,.15],[7.8,3.6,6.6],'fold',[-8,0,-8]),
 ('bottom_flesh',[-.8,19,-2.9],[4.5,3.4,1.8],'muscle',[-4,-8,12]),
 ('rear_left',[-3.6,25.8,4.45],[5.5,7.3,2.5],'hide',[-10,6,-20]),
 ('rear_right',[2.5,24,4.75],[6.3,8.6,2.3],'hide',[11,-8,14]),
 ('rear_nape',[-.3,19.2,3.4],[5.2,4.4,3.9],'scar',[12,0,-5]),
 ('front_left_upper',[-2.9,26.2,-3.7],[4.5,5.1,2.9],'tissue',[12,-16,-22]),
 ('front_left_lower',[-3.1,22.2,-3.3],[3.6,4.6,2.7],'tissue',[-10,-18,15]),
 ('front_right_lobe',[4.1,25.2,-3.25],[3.65,6.8,2.9],'tissue',[3,-14,18]),
 ('front_forehead',[.1,29,-2.9],[4.5,3.3,3.3],'tissue',[14,7,-24]),
]
for name, p, s, mat, r in shell:
    box(name, 'torso', p,s,mat,r)

# Broad, directed folds frame the embedded host. Dark material recedes behind
# the body; the red bands meet each other rather than floating as separate tiles.
box('host_recess','torso',[1.15,25,-4.15],[2.7,5.6,1.0],'joint')
beam('left_upper_fold','torso',[-.6,28.2,-4.65],[-.45,25.6,-5],.85,.8,'muscle')
beam('left_lower_fold','torso',[-.45,25.6,-5],[-.9,22,-4.8],.8,.8,'scar')
beam('right_upper_fold','torso',[2.8,28.2,-4.1],[3.3,25.2,-4.8],1.1,.9,'scar')
beam('right_lower_fold','torso',[3.3,25.2,-4.8],[2.4,21.1,-3.9],1.1,1,'muscle')
for side in [-1,1]:
    for i in range(3):
        a=[.6+side*1.1,21.1+i*.85,-4]
        b=[.6+side*(3.1+i*.45),20.2+i*1.15,-3.8]
        beam('lower_fascicle_%s_%s'%(side,i),'torso',a,b,.5,.65,'sinew' if i==1 else 'muscle')
for i in range(3):
    beam('crown_furrow_%s'%i,'torso',[-4.4+i*1.5,26.2,-5.1],[-3.6+i*1.3,29.1,-4.1],.32,.3,'fold')
beam('left_lobe_sinew','torso',[-3.8,22,-5.15],[-2,26.8,-5.1],.5,.35,'muscle')
beam('right_lobe_sinew','torso',[4.8,22.3,-4.9],[3.7,26.2,-4.9],.4,.35,'sinew')
beam('rear_tendon','torso',[1.2,18,4.5],[-.5,29.5,5],.8,.65,'muscle')

# The small human host is embedded, with a face, ribcage, bent arms and fused
# lower tissue. It is not the giant featureless 'eye' from the rejected draft.
box('host_neck','head',[.7,26.35,-4.25],[.58,.7,.65],'scar')
box('host_skull','head',[.7,27.05,-4.3],[1.15,1.25,1.15],'skin',[-9,0,-7])
box('host_hair','head',[.7,27.65,-4.17],[1.27,.38,1.3],'fold',[-9,0,-7])
box('host_hair_back','head',[.7,26.95,-3.72],[1.25,1.6,.36],'fold',[-9,0,-7])
box('host_brow','head',[.7,27.2,-4.91],[.93,.2,.12],'fold')
box('host_nose','head',[.7,26.95,-4.96],[.23,.36,.26],'skin')
box('host_mouth','head',[.7,26.64,-4.91],[.47,.12,.13],'joint')
box('host_ribcage','head',[.7,25.1,-4.32],[1.65,2.15,.95],'scar',[0,0,-6])
box('host_sternum','head',[.7,25.3,-4.87],[.25,1.6,.18],'skin')
for side in [-1,1]:
    for i in range(3):
        box('host_rib_%s_%s'%(side,i),'head',[.7+side*.46,25.75-i*.47,-4.84],
            [.64,.21,.19],'skin',[0,0,-side*13])
box('host_waist','head',[.82,23.75,-4.3],[1.05,1.1,.77],'muscle',[0,0,8])
beam('host_fused_lower','head',[.8,23.6,-4.25],[.5,21.2,-3.65],.92,.8,'scar')
beam('host_left_upper','head',[-.12,25.85,-4.2],[-.9,25.0,-4.35],.48,.55,'skin')
beam('host_left_forearm','head',[-.9,25,-4.35],[-1.15,26.25,-4.2],.37,.43,'scar')
box('host_left_hand','head',[-1.15,26.45,-4.2],[.45,.6,.4],'skin')
beam('host_right_upper','head',[1.5,25.7,-4.2],[2.2,26.2,-4.2],.48,.55,'skin')
beam('host_right_forearm','head',[2.2,26.2,-4.2],[2.1,27.2,-3.9],.37,.43,'scar')
box('host_right_hand','head',[2.1,27.35,-3.9],[.43,.5,.4],'skin')
for cube in by_name['head']['cubes']:
    cube['origin'][0]+=.4*SCALE
    cube['origin'][2]-=.7*SCALE
    if 'pivot' in cube:
        cube['pivot'][0]+=.4*SCALE
        cube['pivot'][2]-=.7*SCALE

# Three sweeping lateral horns, each with deliberate bends and tapered ends.
for i in range(3):
    y=27.8-i*2.8
    pts=[[-5.1,y,1.3],[-8.1-i*.3,y+1.0,.6],[-11.2-i*.2,y+.25,-.7],[-13.6+i*.45,y-1.4,-1.8]]
    for j in range(3):
        beam('lateral_tendril_%s_%s'%(i,j),'left_arm',pts[j],pts[j+1],1.45-j*.48,1.6-j*.52,'fold')
        if j<2:
            a=[pts[j][0],pts[j][1]-.15,pts[j][2]-.64+j*.2]
            b=[pts[j+1][0],pts[j+1][1]-.15,pts[j+1][2]-.64+j*.2]
            beam('tendril_tissue_%s_%s'%(i,j),'left_arm',a,b,.38,.28,'muscle')
beam('right_drape','right_arm',[6.5,25.2,2.1],[7.5,20.4,1.4],1.3,1.2,'fold')
beam('right_drape_tip','right_arm',[7.5,20.4,1.4],[6.6,17.3,.5],.75,.8,'fold')

# Historical approximation only. These ring offsets and two-pronged crowns are
# not the original segment layout and must never replace the source-rebound
# runtime Slug leaves produced by rebind_greene_source.py.
for i in range(3):
    angle=i*math.tau/3
    x,z=-math.sin(angle)*13.86,-math.cos(angle)*13.86
    owner='weak_'+str(i)
    bone(owner,'root',[x,.6,z])
    def tr(p):
        return [x+p[0]*math.cos(angle)+p[2]*math.sin(angle),p[1],z-p[0]*math.sin(angle)+p[2]*math.cos(angle)]
    def wb(name,a,b,w,d,mat):
        beam('weak_%s_%s'%(i,name),owner,tr(a),tr(b),w,d,mat)
    wb('base',[0,.2,.6],[0,2.7,.15],4.5,4.3,'fold')
    wb('trunk',[0,2.2,.15],[0,5.4,-.55],3.8,3.5,'muscle')
    wb('upper',[0,4.9,-.5],[0,7.05,-1.25],2.65,2.5,'scar')
    for side in [-1,1]:
        wb('lower_hide_'+str(side),[side*1.8,.6,.15],[side*1.45,4.1,-.35],1.2,3.4,'hide')
        wb('upper_hide_'+str(side),[side*1.3,3.7,.15],[side*.85,6.5,-.9],.85,2.3,'hide')
        wb('crown_'+str(side),[side*.8,6.2,-1.15],[side*1.35,7.7,-1.8],.8,1.25,'fold')
        wb('crown_red_'+str(side),[side*.43,6.15,-1.9],[side*.95,7.4,-2.3],.36,.4,'sinew')
    for row in range(3):
        p=tr([0,1.4+row*1.35,-2.1-row*.05])
        box('weak_%s_front_fold_%s'%(i,row),owner,p,[3.9-row*.7,.52,.6],'fold',[0,math.degrees(angle),0])

# Face-specific UVs maintain the accepted model's approximate texel density.
# Reuse its actual authored pixels; no original-game material/image is copied.
atlas=Image.new('RGBA',(ATLAS,ATLAS),(26,20,20,255))
faces=[]
for name,owner,c,mat in parts:
    w,h,d=c['size'];c['uv']={}
    for face,fw,fh in [('north',w,h),('south',w,h),('east',d,h),('west',d,h),('up',w,d),('down',w,d)]:
        faces.append((max(1,math.ceil(fw)),max(1,math.ceil(fh)),name,c,mat,face))
faces.sort(key=lambda v:(-v[1],-v[0]))
x=y=1;row=0
for w,h,name,c,mat,face in faces:
    if x+w+1>=ATLAS:x=1;y+=row+2;row=0
    if y+h+1>=ATLAS:raise ValueError('Atlas overflow')
    c['uv'][face]={'uv':[x,y],'uv_size':[w,h]}
    patch=patches['hide' if mat=='skin' else mat]
    salt=sum(map(ord,name))
    for v in range(-1,h+1):
        for u in range(-1,w+1):
            pu=max(0,min(w-1,u));pv=max(0,min(h-1,v))
            rgb=patch.getpixel(((pu+salt)%patch.width,(pv+salt//7)%patch.height))
            shade=5 if face=='up' else -7 if face=='down' else 0
            # Only the embedded host is paler, preserving the common material range.
            if mat=='skin':rgb=(rgb[0]+28,rgb[1]+22,rgb[2]+17)
            if w>3 and h>3 and (pu==0 or pv==h-1):shade-=5
            atlas.putpixel((x+u,y+v),tuple(max(0,min(255,k+shade)) for k in rgb)+(255,))
    x+=w+2;row=max(row,h)

geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{
    'identifier':'geometry.blacklight.elizabeth_greene','texture_width':ATLAS,'texture_height':ATLAS,
    'visible_bounds_width':12,'visible_bounds_height':10,'visible_bounds_offset':[0,4,0]},'bones':bones}]}
geo_path=ASSETS/'geo/elizabeth_greene_mc.geo.json'
geo_path.write_text(json.dumps(geo,indent=2)+'\n',encoding='utf8')
texture_path=ASSETS/'textures/entity/elizabeth_greene_mc.png'
atlas.save(texture_path)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
provenance={
    'revision':'supreme-style-authored-r6','method':'Named anatomical cuboids authored from the supplied reference silhouette; no copied source mesh, source textures or voxelization.',
    'styleReference':'Existing Supreme Hunter block anatomy and its authored material atlas',
    'styleTextureSha256':sha(ASSETS/'textures/entity/supreme_hunter_block.png'),
    'originalReference':'D:/PrototypeReference/p1/reference-models/mother_core/source-reference.png',
    'geometrySha256':sha(geo_path),'textureSha256':sha(texture_path),
    'cuboids':len(parts),'atlasSize':ATLAS,'atlasUsedHeight':y+row+1,
    'authoredParts':[{'name':name,'bone':owner,'material':mat} for name,owner,c,mat in parts],
    'reviewStatus':'Awaiting runtime comparison against unchanged Supreme Hunter; previous r3, r4 and r5 rejected.'}
(ROOT/'art/encounters/greene-surface-provenance.json').write_text(json.dumps(provenance,indent=2)+'\n',encoding='utf8')
manifest_path=ROOT/'art/encounters/manifest.json'
manifest=json.loads(manifest_path.read_text(encoding='utf8'))
manifest['greeneSurface']=provenance
manifest['authoredRevision']=provenance['revision']
manifest['modelStatus']='Greene r3/r4/r5 rejected. Authored r6 awaits visual comparison/user review. Other models retain their documented draft status.'
manifest['modelAudit']=[m for m in manifest.get('modelAudit',[]) if m['id']!='elizabeth_greene']+[
    {'id':'elizabeth_greene','bones':len(bones),'cuboids':len(parts),'textureSize':ATLAS,
     'geometrySha256':provenance['geometrySha256'],'features':[
         'Asymmetric muscle crown and embedded host',
         'Vertebral column, sweeping lateral horns and separate tapered weakpoints',
         'Authored anatomical cuboids with existing Supreme Hunter material pixels']}]
manifest_path.write_text(json.dumps(manifest,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
print(json.dumps({k:v for k,v in provenance.items() if k!='authoredParts'}))
