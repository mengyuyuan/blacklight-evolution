"""Original Minecraft-style Hunter: cuboids + hand-defined pixel patterns.

Python standard library only. No extracted game assets or generated concept
images are read. Geometry JSON is editable in Blockbench; the companion
Blender script displays exactly these boxes and this pixel atlas.
"""
from pathlib import Path
import json, math, struct, zlib, binascii

R=Path(__file__).resolve().parents[1]
A=R/'src/main/resources/assets/blacklight'
ART=R/'art/hunter';ART.mkdir(parents=True,exist_ok=True)
SIZE=512
pixels=bytearray([45,33,31,255]*(SIZE*SIZE))
cursor=[1,1,0]
palette={'hide':(99,77,70),'back':(88,66,60),'chest':(105,79,71),'arm':(90,68,62),'hand':(55,43,40),'claw':(37,32,30),'head':(115,89,78),'jaw':(82,56,49),
         'pectoral':(106,76,65),'abdomen':(92,66,57),'muscle':(94,63,55),'tendon':(110,82,71),'spine':(79,55,49),'mouth':(32,16,17),'tooth':(159,142,110),'brow':(93,65,54),'nose':(75,49,40)}
def put(x,y,c):
    if 0<=x<SIZE and 0<=y<SIZE:
        i=(y*SIZE+x)*4;pixels[i:i+4]=bytes([max(0,min(255,int(v))) for v in c]+[255])
def tile(w,h,kind,face,seed):
    # Two crisp texels per modelling unit: readable face/tendon pixel artwork,
    # still deliberately pixelated rather than a photo or a smooth skin shader.
    w=max(1,math.ceil(w*2));h=max(1,math.ceil(h*2))
    if cursor[0]+w+1>SIZE:cursor[0]=1;cursor[1]+=cursor[2]+2;cursor[2]=0
    x,y=cursor[:2];cursor[0]+=w+2;cursor[2]=max(cursor[2],h)
    if y+h+1>SIZE:raise RuntimeError('Pixel atlas full')
    base=palette[kind]
    for j in range(-1,h+1):
        for i in range(-1,w+1):
            u=max(0,min(w-1,i));v=max(0,min(h-1,j))
            shade=(5 if face=='up' else -6 if face=='down' else 0)
            shade+=(((u//3)*13+(v//3)*7+seed*3)%5-2)
            if u in (0,w-1) or v in (0,h-1):shade-=5
            c=[b+shade for b in base]
            if kind in ('arm','tendon','muscle') and w>4 and h>4:
                line=(w//3+v//6+seed%2)%max(2,w-2)+1
                if u in (line,line+w//3) and 1<v<h-2:c=[b-16 for b in base]
                if u==line+1 and 2<v<h-3:c=[b+7 for b in base]
            if kind=='pectoral' and face in ('north','south'):
                curve=(v-abs(u-w//2)//4)%5
                if curve==0 and 1<u<w-2 and 1<v<h-1:c=[base[0]-17,base[1]-18,base[2]-15]
                if curve==1 and 1<u<w-2 and 1<v<h-1:c=[b+5 for b in base]
            if kind=='abdomen' and face in ('north','south'):
                if u in (w//2-1,w//2) or v%5==4:c=[b-16 for b in base]
                elif v%5==0:c=[b+6 for b in base]
            if kind=='spine':
                if abs(u-w//2)<2:c=[base[0]-9,base[1]-15,base[2]-12]
                if v%5==0:c=[b+9 for b in base]
            if kind=='hand' and h>4 and (v==h//2 or (u%5==0 and v>h//2)):c=[b-9 for b in base]
            if kind=='claw' and u==w//2:c=[b+18 for b in base]
            put(x+i,y+j,c)
    if kind=='head' and face=='north':
        for u in range(w):
            for v in range(h):
                c=None
                # Reference: continuous eyeless head shell, no eyes or human nose.
                if v>=h-2 and 1<u<w-2:c=(40,21,20)
                if c:put(x+u,y+v,c)
    if kind=='jaw' and face=='north':
        for u in range(1,w-1):put(x+u,y,(58,29,26))
    if kind=='nose' and face=='north':
        for u in (0,w-1):put(x+u,y+h-1,(29,20,18))
    return {'uv':[x,y],'uv_size':[w,h]}

pivots={'hips':(0,18,10),'torso':(0,21,5),'head':(0,24,-10),'jaw':(0,21,-12)}
parents={'hips':'root','torso':'hips','head':'torso','jaw':'head'}
for side,s in [('right',1),('left',-1)]:
    pivots.update({side+'_arm':(s*9,25,-4),side+'_forearm':(s*12,14,-5),side+'_thigh':(s*4,18,10),side+'_shin':(s*6,8,7)})
    parents.update({side+'_arm':'torso',side+'_forearm':side+'_arm',side+'_thigh':'hips',side+'_shin':side+'_thigh'})
bones=[{'name':'root','pivot':[0,0,0],'cubes':[]}]
for n,p in pivots.items():bones.append({'name':n,'parent':parents[n],'pivot':[-p[0],p[1],p[2]],'cubes':[]})
by_name={b['name']:b for b in bones};boxes=[]
def box(name,bone,center,size,kind='hide',rotation=(0,0,0),pivot=None):
    sx,sy,sz=size;cx,cy,cz=center;p=pivot or center
    uv={}
    for face,w,h in [('north',sx,sy),('south',sx,sy),('east',sz,sy),('west',sz,sy),('up',sx,sz),('down',sx,sz)]:
        uv[face]=tile(w,h,kind,face,len(boxes))
    cube={'origin':[-cx-sx/2,cy-sy/2,cz-sz/2],'size':list(size),'uv':uv}
    if any(rotation):cube.update({'pivot':[-p[0],p[1],p[2]],'rotation':[-rotation[0],-rotation[1],rotation[2]]})
    by_name[bone]['cubes'].append(cube)
    boxes.append({'name':name,'bone':bone,'center':center,'size':size,'rotation':rotation,'pivot':p,'uv':uv})

box('Pelvis','hips',(0,18,10),(10,8,8),'back')
box('Tapered waist','torso',(0,21,6),(11,8,10),'hide')
box('Broad ribcage','torso',(0,25,-2),(16,9,12),'chest')
box('Raised shoulder back','torso',(0,29.5,0),(12,3,11),'back')
box('Back ridge','torso',(0,31,0),(4,1,9),'spine')
box('Lower abdominal block','torso',(0,20,-2),(9,5,8),'abdomen')
box('Sternum','torso',(0,24,-8.8),(2,7,1),'tendon')
for s in (-1,1):
    box('Layered shoulder slope','torso',(s*6.7,29,0),(3,3,10),'back',(0,0,s*-10))
    box('Defined pectoral','torso',(s*4,25,-8),(7,5,3),'pectoral',(0,0,s*-4))
    box('Collar muscle','torso',(s*4,28,-6.8),(7,2,3),'tendon',(0,0,s*8))
    box('Side muscle recess','torso',(s*7.7,22.5,0),(1,5,7),'muscle')
    box('Scapula','torso',(s*4,27.7,4.65),(6,4,2),'back',(0,0,s*8))
box('Neck bridge','head',(0,25,-9),(7,5,5),'back')
box('Low bald head','head',(0,24,-12),(6,6,7),'head')
box('Crown plane','head',(0,27,-11.5),(5,1,5),'back')
box('Dark mouth recess','head',(0,20.6,-14.4),(5,1,3),'mouth')
box('Open lower jaw','jaw',(0,19.5,-13.3),(5,2,5),'jaw')
for s in (-1,1):
    box('Cheek plane','head',(s*3,22,-13.8),(1,3,4),'head')
    box('Jaw hinge','jaw',(s*2.5,20,-11.5),(1,3,2),'jaw')
    for j in range(2):
        box('Upper fang','head',(s*(1+j),20.7,-15.5),(.7,1.2,1),'tooth')
        box('Lower tooth','jaw',(s*(.7+j*.9),20.65,-15.2),(.6,.6,.8),'tooth')
for side,s in [('right',1),('left',-1)]:
    arm=side+'_arm';fore=side+'_forearm';thigh=side+'_thigh';shin=side+'_shin'
    box(side+' shoulder',arm,(s*9,25,-3),(6,7,8),'back')
    box(side+' upper arm',arm,(s*10.5,19.5,-4.5),(5,12,6),'arm',(5,0,s*15))
    box(side+' biceps plane',arm,(s*10.1,20,-7.5),(3,7,1.2),'muscle',(5,0,s*15))
    box(side+' triceps plane',arm,(s*11,21,-1.5),(4,7,1.5),'back',(5,0,s*15))
    box(side+' elbow',fore,(s*12,14,-5),(4,4,5),'arm')
    box(side+' long forearm',fore,(s*13,8.2,-8),(6,12,6),'hand',(25,0,s*9))
    box(side+' forearm tendon',fore,(s*13,8.7,-10.3),(2,8,.8),'back',(25,0,s*9))
    box(side+' elbow cap',fore,(s*12,14,-7.4),(3,3,1),'back')
    box(side+' wrist',fore,(s*14,3,-11),(4,4,4),'hand')
    box(side+' broad palm',fore,(s*14,1.5,-13.6),(7,3,5),'hand')
    for j in range(4):
        x=s*(11.5+j*1.7);length=(3,4,4,3)[j]
        box(side+' finger '+str(j),fore,(x,1,-16-length*.2),(1,2,length),'hand')
        box(side+' knuckle '+str(j),fore,(x,3.25,-15.3),(1.5,1,1.5),'hand')
        box(side+' claw '+str(j),fore,(x,.55,-16-length*.7),(1,1,2),'claw')
    box(side+' thumb',fore,(s*10.3,1.7,-13.8),(2,2,3),'hand',(0,s*-20,0))
    box(side+' thumb claw',fore,(s*9.7,.8,-15.3),(1,1,2),'claw',(0,s*-20,0))
    box(side+' thigh',thigh,(s*5,13.3,8.4),(6,11,7),'hide',(18,0,s*10))
    box(side+' forward knee',shin,(s*6,8,7),(4,4,4),'arm')
    box(side+' kneecap',shin,(s*6,8,4.8),(3,3,1),'back')
    box(side+' rearward shin',shin,(s*6.5,5.5,11),(3,9,4),'arm',(-53,0,0))
    box(side+' hock',shin,(s*7,2.6,14),(3,4,3),'hand')
    box(side+' grounded foot',shin,(s*7,1.2,11.8),(5,2,6),'hand')
    for j in range(3):box(side+' toe '+str(j),shin,(s*(5.2+j*1.8),.65,8.7),(1,1,2),'claw')

geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.blacklight.hunter_authored','texture_width':SIZE,'texture_height':SIZE,'visible_bounds_width':5,'visible_bounds_height':4,'visible_bounds_offset':[0,1.25,0]},'bones':bones}]}
(A/'geo/blacklight_hunter_authored.geo.json').write_text(json.dumps(geo,indent=2),encoding='utf8')
def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',binascii.crc32(t+d)&0xffffffff)
raw=b''.join(b'\x00'+pixels[y*SIZE*4:(y+1)*SIZE*4] for y in range(SIZE))
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',SIZE,SIZE,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
(A/'textures/entity/hunter_authored.png').write_bytes(png)
(ART/'block-model-source.json').write_text(json.dumps({'authoring':'Original cuboids and pixel artwork; no imported meshes/textures.','texture':str(A/'textures/entity/hunter_authored.png'),'texture_size':SIZE,'pivots':pivots,'parents':parents,'boxes':boxes},indent=2),encoding='utf8')
print('Authored block Hunter:',len(boxes),'cuboids,',len(pivots),'bones, atlas rows used',cursor[1]+cursor[2])
