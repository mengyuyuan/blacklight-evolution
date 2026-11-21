"""Create the Supreme Hunter's block body and print atlas from Minecraft pixels."""
from pathlib import Path
import json
from PIL import Image

root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/blacklight'
path=root/'models/supreme_hunter_block.json'
model=json.loads(path.read_bytes())
bones={b['name']:(i,b['pivot']) for i,b in enumerate(model['bones'])}
patches=[(53,47,51),(105,90,84),(77,48,49),(141,124,101),(107,52,52),(44,42,45)]
atlas=Image.new('RGB',(256,256))
for y in range(256):
    for x in range(256):
        patch=(x//32)%len(patches)
        crease=-9 if (x+y//3)%15<2 else 0
        speck=((x*31+y*47+x*y*3)%13)-6
        light=7 if (x-y//2)%23==0 else 0
        atlas.putpixel((x,y),tuple(max(0,min(255,c+crease+speck+light)) for c in patches[patch]))
quads=[]
def box(bone,offset,size,tile):
    i,pivot=bones[bone]
    center=[x+y for x,y in zip(pivot,offset)]
    lo=[x-y/2 for x,y in zip(center,size)]
    hi=[x+y/2 for x,y in zip(center,size)]
    points=[(x,y,z) for z in (lo[2],hi[2]) for y in (lo[1],hi[1]) for x in (lo[0],hi[0])]
    sides=[((0,0,-1),(1,0,2,3)),((0,0,1),(4,5,7,6)),((-1,0,0),(0,4,6,2)),
           ((1,0,0),(5,1,3,7)),((0,1,0),(3,2,6,7)),((0,-1,0),(5,4,0,1))]
    for normal,ids in sides:
        row=[i,*normal]
        uv=[(1,1),(30,1),(30,30),(1,30)]
        for index,(u,v) in zip(ids,uv):
            row.extend([*[round(n,4) for n in points[index]],(tile*32+u)/256,(i%7*32+v)/256])
        quads.append(row)

for name,center,size in [('Pelvis',(0,-1,0),(16,9,12)),('Spine_1',(0,1,0),(15,8,11)),
                         ('Spine_2',(0,2,0),(21,9,13)),('Spine_3',(0,2,0),(24,9,14)),
                         ('Neck',(0,1,0),(7,7,7)),('Head',(0,2,-1.4),(8.5,7.5,9)),
                         ('Jaw',(0,-.8,-3.5),(8,3.2,7))]:
    box(name,center,size,1 if name in ('Head','Spine_3') else 0)
for side,sign in [('R',1),('L',-1)]:
    def limb(joint,offset,size,tile):
        box(joint+'_'+side,(offset[0]*sign,offset[1],offset[2]),size,tile)
    limb('Shoulder',(0,-4,0),(8.5,11,9),2)
    limb('Shoulder',(.8,.1,-.3),(10,5,10),1)
    limb('Elbow',(.2,-4,.2),(7,9,7),0)
    limb('Wrist',(.3,-3,-.3),(8.6,8,7.5),1)
    limb('Hip',(0,-8,0),(8.5,18,9),2)
    limb('Knee',(0,-.1,-2.3),(7.5,6,5),1)
    limb('Knee',(0,-6,0),(6.8,14,7.5),0)
    limb('Ankle',(0,-1,-1.3),(8,5,10),1)
    limb('Ball',(0,1,-1.0),(7.7,3.3,6.5),3)
    for k in range(3):
        box('Spine_3',(sign*(3.7+k*.55),2.8-k*2.1,-6.25),(6.5,1.65,1.55),1)
        box('Head',(sign*(1.6+k*.75),-.75,-5.9),(.62,1.8+(k%2)*.6,1.0),3)
    box('Head',(sign*2.3,2.4,-5.8),(3.65,1.2,1.65),1)
    box('Spine_2',(sign*6.6,3.6,5.65),(5,4,3.4),5)
for joint in ['Index','Middle','Ring','Thumb']:
    limb_name=joint+'_Base_R'
    box(limb_name,(0,-1.8,0),(2.3,5,3.6),1)
    box(joint+'_R',(0,-1.6,-.5),(1.8,4.5,2.1),3)
    box(joint+'_R',(0,-3.9,-1.2),(1.0,2.3,1.15),3)
# Left fist uses three knuckle wedges, replacing a single flat block face.
for x in (-2.5,0,2.5):
    box('Wrist_L',(x,-5,-3.1),(2.1,3.0,2.4),3)
model['quads']=quads
model['note']='Project-designed pixel cuboids and atlas, tools/author_supreme_shell.py. The separate bone and clip tables retain their animation provenance.'
path.write_text(json.dumps(model,separators=(',',':')),encoding='utf8')
atlas.save(root/'textures/entity/supreme_hunter_block.png')
