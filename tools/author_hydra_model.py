"""Authored P1-inspired hydra; editable Gecko geometry, independent neck and jaw bones.
Uses the project's existing procedural pixel-atlas writer, no original mesh/texture copying.
"""
from pathlib import Path
import json, math, struct, zlib, binascii
R=Path(__file__).resolve().parents[1]
exec((R/'tools/author_hunter_model.py').read_text(encoding='utf8').split("box('Pelvis'")[0])
ART=R/'art/hydra';ART.mkdir(parents=True,exist_ok=True)
SIZE=1024;pixels=bytearray([45,22,27,255]*(SIZE*SIZE));cursor=[1,1,0]
palette.update(hide=(96,36,43),back=(46,31,36),chest=(117,43,49),arm=(84,29,36),
               muscle=(130,44,48),tendon=(145,54,56),spine=(50,31,35),head=(91,31,40),jaw=(65,32,37),
               claw=(171,151,116),tooth=(207,189,149),mouth=(27,11,16))
base_tile=tile
def tile(w,h,kind,face,seed):
    patch=base_tile(w,h,kind,face,seed)
    x,y=patch['uv'];pw,ph=patch['uv_size'];base=palette[kind]
    if kind in ('hide','muscle','head','jaw','back','tendon'):
        for v in range(ph):
            for u in range(pw):
                # Broad pixel muscle bands and branching fissures, continuous across each mass.
                band=math.sin((u+v*.38+seed*3)*.38)
                shade=int(band*9)+(((u//3)*11+(v//3)*7+seed)%7-3)
                col=[c+shade for c in base]
                vein=(u+v//4+seed*3)%max(8,pw//3)
                if vein==0 and 2<v<ph-2:col=(49,18,26) if kind!='back' else (106,34,43)
                elif vein==1 and kind in ('hide','muscle','tendon'):col=(145,52,57)
                if v%11 in (0,1) and kind=='back':col=[c+8 for c in base]
                put(x+u,y+v,col)
    return patch
pivots={};parents={};boxes=[];bones=[{'name':'root','pivot':[0,0,0],'cubes':[]}];by_name={'root':bones[0]}
def bone(n,p,parent='root'):
    pivots[n]=p;parents[n]=parent;b={'name':n,'parent':parent,'pivot':[-p[0],p[1],p[2]],'cubes':[]};bones.append(b);by_name[n]=b
bone('base',(0,0,0));bone('coil',(0,0,0))
# Wide fused root and a forward-curving neck. Large interlocked masses, not a stack of collars.
box('Root core','base',(0,8,1),(33,17,29),'hide')
for j in range(8):
    a=j*math.pi/4
    box('Root buttress','base',(math.cos(a)*14,7,math.sin(a)*13),(12,20,15),'hide',(math.sin(a)*22,math.degrees(a),math.cos(a)*22))
    box('Root armor ridge','base',(math.cos(a)*17,10,math.sin(a)*15),(5,18,6),'back',(math.sin(a)*24,math.degrees(a),math.cos(a)*24))
previous='base'
for i in range(7):
    y=10+i*8.7;z=5-.55*i*i;w=25-i*1.85;depth=23-i*1.35
    n=f'neck_{i}';bone(n,(0,y,z),previous);previous=n
    box('Continuous trunk '+str(i),n,(0,y+5,z),(w,15,depth),'hide',(-i*3.2,0,0))
    box('Spinal carapace '+str(i),n,(0,y+6,z+depth*.43),(w*.5,14,4),'back',(-i*3.2,0,8 if i%2 else -8))
    for side in (-1,1):
        box('Flowing muscle '+str(i),n,(side*w*.24,y+5,z-depth*.43),(w*.46,14,4),'muscle',(-i*3.2,0,side*(12 if i%2 else 20)))
        box('Flank plate '+str(i),n,(side*w*.44,y+6,z),(4,14,depth*.65),'back',(-i*3.2,side*8,side*15))
        if i in (1,3,5):
            box('Swept ridge '+str(i),n,(side*w*.29,y+8,z-depth*.48),(w*.58,3,3),'back',(-i*3.2,0,side*-20))
bone('head',(0,70,-18),previous)
box('Skull core','head',(0,74,-25),(18,13,22),'hide',(-18,0,0))
box('Crown','head',(0,81,-24),(13,4,18),'back',(-18,0,0))
# Deep vertical opening and unequal fangs avoid an anthropomorphic smiling face.
box('Mouth recess','head',(0,71,-36),(11,12,3),'mouth')
for side in (-1,1):
    box('Cheek carapace','head',(side*7,74,-33),(5,13,8),'back',(0,0,side*20))
    box('Top fang','head',(side*4.2,75,-38),(2.3,6,2.5),'tooth',(0,0,side*14))
    box('Skull crest','head',(side*6,82,-26),(3,8,8),'back',(-23,0,side*22))
bone('jaw',(0,66,-24),'head')
box('Lower mandible','jaw',(0,64,-31),(13,4,14),'jaw',(-12,0,0))
for side in (-1,1):
    box('Lower fang','jaw',(side*4,67,-37),(2,4,2),'tooth',(0,0,-side*16))
    name='pincer_left' if side<0 else 'pincer_right';bone(name,(side*7,73,-27),'head')
    # Thick crescent jaws with an ivory cutting edge beneath a dark red dorsal surface.
    for j in range(7):
        x=side*(10+j*4.3);y=70+max(0,j-1)**1.55*1.05;z=-30-j*1.6
        height=12-j*1.65;depth=11-j*1.5;angle=side*(j*7-10)
        box('Ivory jaw edge '+str(j),name,(x,y-1,z),(6.4,height,depth),'claw',(0,0,angle))
        if j<5:
            box('Mandible flesh '+str(j),name,(x,y+height*.2,z+1),(6.9,height*.78,depth*.92),'hide',(0,0,angle))
            box('Mandible armor '+str(j),name,(x,y+height*.5,z+1),(7,height*.28,depth*.94),'back',(0,0,angle))
# Capture strands are hidden outside the capture action. They wrap the passenger instead of scaling its body.
for j in range(34):
    a=j*.47;r=10;cx=math.cos(a)*r;cz=-27+math.sin(a)*r;cy=12+j*.95
    box('Capture strand '+str(j),'coil',(cx,cy,cz),(5.6,2.4,2.4),'tendon',(0,-math.degrees(a)-90,0))
geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.blacklight.hydra','texture_width':SIZE,'texture_height':SIZE,'visible_bounds_width':8,'visible_bounds_height':7,'visible_bounds_offset':[0,2.7,0]},'bones':bones}]}
(A/'geo/blacklight_hydra.geo.json').write_text(json.dumps(geo,indent=2),encoding='utf8')
def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',binascii.crc32(t+d)&0xffffffff)
raw=b''.join(b'\x00'+pixels[y*SIZE*4:(y+1)*SIZE*4] for y in range(SIZE))
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',SIZE,SIZE,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
(A/'textures/entity/hydra_authored.png').write_bytes(png)
(ART/'block-model-source.json').write_text(json.dumps({'authoring':'Authored geometry and pixel atlas, P1 silhouette reference.','texture':str(A/'textures/entity/hydra_authored.png'),'texture_size':SIZE,'pivots':pivots,'parents':parents,'boxes':boxes},indent=2),encoding='utf8')
animations={}
def clip(name,length,channels):animations[name]={'loop':True,'animation_length':length,'bones':channels}
def rot(keys):return {'rotation':{str(t):v for t,v in keys}}
idle={f'neck_{i}':rot([(0,[0,0,-.65]),(1.5,[1.2,0,.65]),(3,[0,0,-.65])]) for i in range(7)}
idle['jaw']=rot([(0,[0,0,0]),(1.5,[5,0,0]),(3,[0,0,0])]);clip('idle',3,idle)
for name,length,strength in [('feed',1.5,5),('capture',2.25,9),('slam',2.2,11),('retract',1.5,3)]:
    channels={}
    for i in range(7):
        delay=i*.025
        channels[f'neck_{i}']=rot([(0,[0,0,0]),(.35+delay,[-3,0,0]),(.85+delay,[strength,0,0]),(length,[0,0,0])])
    channels['jaw']=rot([(0,[0,0,0]),(.5,[24,0,0]),(length*.72,[4,0,0]),(length,[0,0,0])])
    for s,n in [(-1,'pincer_left'),(1,'pincer_right')]:channels[n]=rot([(0,[0,0,0]),(.45,[0,s*15,0]),(.95,[0,-s*17,0]),(length,[0,0,0])])
    if name=='capture':channels['coil']={'scale':{'0':[.1,.1,.1],'.5':[1,1,1],'1.2':[1,1.8,1],'2.25':[.5,.3,.5]},'rotation':{'0':[0,0,0],'2.25':[0,80,0]}}
    clip(name,length,channels)
clip('emerge',2.5,{'neck_0':{'position':{'0':[0,-62,0],'0.4':[0,-48,0],'1.4':[0,3,0],'2.5':[0,0,0]}},'head':rot([(0,[20,0,0]),(1.4,[-12,0,0]),(2.5,[0,0,0])])})
(A/'animations/blacklight_hydra.animation.json').write_text(json.dumps({'format_version':'1.8.0','animations':animations},indent=2),encoding='utf8')
print('Hydra:',len(boxes),'cuboids;',len(bones),'bones; atlas',cursor[1]+cursor[2], '/',SIZE)
