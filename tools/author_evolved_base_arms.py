"""Build beveled arm rings and pixel textures using the standard MC body and joint-name table."""
from pathlib import Path
import json
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
bones=json.loads((ROOT/'art/motion-profile/evolved-poses.data').read_text())['bones']
index={bone['name'].lower():i for i,bone in enumerate(bones)}
# Keep the standard player cuboids, replacing the complete arm surfaces below.
meshpath = ROOT/'src/main/resources/assets/blacklight/models/evolved_player_rig.json'
old = json.loads(meshpath.read_text()); body = [f for f in old['faces'] if f[5] == 0]
used = sorted({i for f in body for i in f[:4]}); remap={i:k for k,i in enumerate(used)}
vertices=[old['vertices'][i] for i in used]; faces=[[*[remap[i] for i in f[:4]],*f[4:]] for f in body]

def surface(points, side, bone, patch, va, vb):
    ids=[]
    for x,y,z in points:
        vertices.append([round((-x if side=='L' else x)+(-5 if side=='R' else 5),5),y,z,index[bone.lower()],index[bone.lower()],0])
        ids.append(len(vertices)-1)
    uv=[(patch*32+2,va),(patch*32+29,va),(patch*32+29,vb),(patch*32+2,vb)]
    if side=='R': ids.reverse();uv.reverse()
    role=2 if side=='R' else 3
    flat=[round(v/128,6) for pair in uv for v in pair]
    faces.append([*ids,role,4,*flat,-1])
    faces.append([*ids,role,5,*flat,-1])

def ring(y, left, right, front, back):
    bevel=min(.48,(right-left)*.22,(back-front)*.22)
    return [(left+bevel,y,front),(right-bevel,y,front),(right,y,front+bevel),(right,y,back-bevel),
            (right-bevel,y,back),(left+bevel,y,back),(left,y,back-bevel),(left,y,front+bevel)]

def limb(side,bone,sections):
    rings=[ring(*section) for section in sections]
    for a,b in zip(rings,rings[1:]):
        for i in range(8):
            patch=3 if i in (1,7) else 2 if i in (2,6) else 1 if i in (0,4) else 0
            surface([a[i],a[(i+1)%8],b[(i+1)%8],b[i]],side,bone,patch,max(2,a[0][1]*6),min(125,b[0][1]*6+8))
    # Closed faceted ends, without spike tangles or floating wrist pieces.
    for end,reverse in [(rings[0],True),(rings[-1],False)]:
        center=tuple(sum(p[i] for p in end)/len(end) for i in range(3))
        for i in range(8):
            p=[center,end[i],end[(i+1)%8],center]
            surface(list(reversed(p)) if reverse else p,side,bone,0,2,26)

for side in ['R','L']:
    limb(side,'Shoulder_'+side,[(0.,-1.8,1.8,-1.8,1.8),(1.6,-2.05,2.05,-2.,2.),(4.8,-1.6,1.6,-1.6,1.6),(6.8,-1.55,1.55,-1.55,1.55)])
    limb(side,'Elbow_'+side,[(6.1,-1.6,1.6,-1.6,1.6),(8.5,-2.5,1.8,-2.4,1.8),(11.5,-3.,1.6,-2.8,1.1),
                            (14.,-2.4,.9,-2.1,.5),(17.,-.9,.35,-.9,.3),(18.5,-.12,.12,-.25,0.)])
mesh={'schema':2,'vertices':vertices,'faces':sorted(faces,key=lambda f:f[5]),
      'body':'Standard Minecraft player body with separately authored full biological blade arms, aligned to anatomical shoulder/elbow aims.',
      'armSource':{'authoring':'Dedicated Minecraft Evolved arm geometry, not the player weapon mesh','texture':'textures/entity/evolved_arms.png','emissiveTexture':'textures/entity/evolved_arms_emissive.png'}}
meshpath.write_text(json.dumps(mesh,separators=(',',':')),encoding='utf8')

image=Image.new('RGBA',(128,128)); glow=Image.new('RGBA',(128,128),(0,0,0,0))
palette=[(31,27,31),(44,43,47),(67,67,72),(91,91,96)]
for y in range(128):
    for x in range(128):
        patch=x//32;u=x%32;rgb=palette[patch]
        grain=((x*7+y*3)%7-3)//2
        shade=-8 if y%24 in (0,1) else 5 if u in (2,29) else 0
        rgb=tuple(max(0,min(255,v+grain+shade)) for v in rgb)
        vein=patch in (0,1) and abs(u-(12+(y//11)%3*2))<=1 and 5<y<124
        if vein:rgb=(124 if u%2 else 158,21,30);glow.putpixel((x,y),(145,19,28,150))
        image.putpixel((x,y),(*rgb,255))
textures=ROOT/'src/main/resources/assets/blacklight/textures/entity'
image.save(textures/'evolved_arms.png');glow.save(textures/'evolved_arms_emissive.png')
