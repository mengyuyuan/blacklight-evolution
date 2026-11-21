"""Author editable Minecraft cuboids and bake the same faces for the game renderer.
Inputs are the silhouettes and pixel patterns below. No extracted model or texture is read.
Usage: python tools/author_weapon_skill_models.py [repo root]
"""
import base64
import hashlib
import io
import json
import math
from pathlib import Path
import sys
import uuid
from PIL import Image, ImageDraw

ROOT=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[1]
ASSETS=ROOT/"src/main/resources/assets/blacklight"
ART=ROOT/"art/weapon-skill-models"
ART.mkdir(parents=True,exist_ok=True)
TEXTURE="skill_chitin.png"
tiles={"shell":(0,0),"rim":(16,0),"fiber":(32,0),"tip":(48,0),"shadow":(0,16),"joint":(16,16)}
palette={
    "shell":("#191b1e","#25282b","#383c40","#0e1012"),
    "rim":("#2a2d30","#373b3e","#515558","#17191c"),
    "fiber":("#181b1d","#242a2c","#373d40","#0b0d0f"),
    "tip":("#24272a","#34383b","#4a4e51","#131518"),
    "shadow":("#111315","#1c1f22","#303337","#090a0b"),
    "joint":("#17191b","#23272a","#36393c","#0b0d0e")}
im=Image.new("RGB",(64,32),"#191b1e")
draw=ImageDraw.Draw(im)
for name,(x,y) in tiles.items():
    base,mid,lit,dark=palette[name];draw.rectangle((x,y,x+15,y+15),fill=base)
    # Hand-placed broken vertical grain, stepped in two-pixel runs.
    for i,(col,row,length) in enumerate([(1,1,7),(3,8,5),(7,0,5),(6,5,5),(5,10,5),(10,3,6),(12,9,4),(14,0,7)]):
        draw.rectangle((x+col,y+row,x+col+(i%3==0),y+row+length),fill=(mid,dark,mid)[i%3])
    for col,row,n in [(2,2,3),(6,11,2),(10,5,2),(13,11,1)]:
        draw.line((x+col,y+row,x+col,y+row+n),fill=lit)
    if name=="fiber":
        for a,b in [(2,7),(8,12)]:
            for j in range(6):draw.point((x+a+j//2,y+b-j),fill=lit)
tex=ASSETS/"textures/entity"/TEXTURE;tex.parent.mkdir(parents=True,exist_ok=True);im.save(tex)
image_bytes=tex.read_bytes()
texture={"name":TEXTURE,"id":"0","uuid":str(uuid.uuid5(uuid.NAMESPACE_URL,TEXTURE)),
    "width":64,"height":32,"uv_width":64,"uv_height":32,"source":"data:image/png;base64,"+base64.b64encode(image_bytes).decode()}
faces={"north":(0,3,2,1),"south":(5,6,7,4),"west":(4,7,3,0),"east":(1,2,6,5),
       "up":(3,7,6,2),"down":(4,0,1,5)}
def ident(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,"blacklight/skill-vfx/"+name))
def rotate(point,pivot,angles):
    x,y,z=(v-o for v,o in zip(point,pivot))
    # Blockbench free cube rotations use ZYX Euler (apply X, then Y, then Z).
    for axis,angle in enumerate(angles):
        c,s=math.cos(math.radians(angle)),math.sin(math.radians(angle))
        if axis==0:y,z=y*c-z*s,y*s+z*c
        elif axis==1:x,z=x*c+z*s,-x*s+z*c
        else:x,y=x*c-y*s,x*s+y*c
    return [x+pivot[0],y+pivot[1],z+pivot[2]]
def box(name,pos,size,material="shell",rotation=(0,0,0),pivot=None):
    x,y,z=pos;w,h,d=size; lo=[x-w/2,y,z-d/2];hi=[x+w/2,y+h,z+d/2]
    uv={}
    for side in faces:
        selection=material
        if side=="up" and material=="shell":selection="rim"
        if side=="down" and material!="tip":selection="shadow"
        xx,yy=tiles[selection];uw=max(2,min(15,round(d if side in ("west","east") else w)))
        vh=max(2,min(15,round(d if side in ("up","down") else h)))
        uv[side]={"uv":[xx+.5,yy+.5,xx+uw+.5,yy+vh+.5],"texture":0}
    return {"type":"cube","name":name,"from":lo,"to":hi,"origin":list(pivot or pos),"rotation":list(rotation),"faces":uv}
# Width and depth are separate. Each profile has a visible heel, an offset blade
# edge and successively shorter hard steps. Coordinates are Blockbench pixels.
profiles={
 "small":[(0,-2,0,10,10,8),(0,6,0,8,13,6),(1,17,0,6,12,5),(2,27,0,4,11,3),(3,36,0,2,8,2),(3.5,43,0,1,5,1)],
 "mid":[(0,-3,0,13,15,10),(0,10,0,10,17,8),(1,25,0,8,16,6),(2,39,0,6,14,5),(3,51,0,4,12,3),(4,61,0,2,8,2),(4.5,68,0,1,5,1)],
 "large":[(0,-4,0,18,19,13),(0,12,0,15,23,11),(1,32,0,12,23,9),(3,52,0,9,19,7),(5,69,0,7,17,5),(6,84,0,4,13,3),(7,95,0,2,10,2),(7.5,104,0,1,5,1)]}
def horn(tier,prefix="",root=(0,0,0),scale=1,lean=0,turn=0):
    result=[];rx,ry,rz=root
    sweep={"small":8,"mid":13,"large":19}[tier]
    height=profiles[tier][-1][1]+profiles[tier][-1][4]
    def piece(name,position,extents,mat):
        x,y,z=position
        bend=sweep*(max(0,y)/height)**2
        loc=rotate([(x+bend)*scale,y*scale,z*scale],[0,0,0],(lean,turn,0))
        at=[loc[0]+rx,loc[1]+ry,loc[2]+rz]
        roll=-math.degrees(math.atan(2*sweep*max(0,y+extents[1]*.5)/height**2))
        axes=[rotate(rotate(v,(0,0,0),(0,0,roll)),(0,0,0),(lean,turn,0))
              for v in ((1,0,0),(0,1,0),(0,0,1))]
        angles=tuple(math.degrees(a) for a in (math.atan2(axes[1][2],axes[2][2]),
                     math.asin(-max(-1,min(1,axes[0][2]))),math.atan2(axes[0][1],axes[0][0])))
        result.append(box(prefix+name,at,[v*scale for v in extents],mat,angles,at))
    rows=profiles[tier]
    for i,(x,y,z,w,h,d) in enumerate(rows):
        # The slimmer segments sweep along one cutting edge. The tip and heel
        # move by different amounts, giving each blocky fang a distinct side profile.
        piece("甲节 "+str(i+1),(x,y,z),(w,h,d),"tip" if i>=len(rows)-2 else "shell")
        if 0<i<len(rows)-2:
            piece("侧刃 "+str(i),(x+w*.43,y+1,z),(max(1,w*.24),h*.83,d*.67),"rim")
            piece("筋槽 "+str(i),(x-w*.2,y+1,z-d*.5),(max(1,w*.23),h*.65,1),"fiber")
    piece("后侧根甲",(-2,0,3),(rows[0][3]*.68,rows[0][4]*1.22,rows[0][5]*.78),"shadow")
    return result
def strand(low=False):
    if low:return [box("方形筋节",(0,0,0),(2.6,16,2.4),"fiber"),box("侧甲",(1,1,0),(1,13,2.7),"shell")]
    return [box("筋束",(0,0,0),(2.8,16,2.6),"fiber"),
        box("左侧甲",(-1.12,.1,.1),(1.3,6.6,2.8),"shell"),
        box("右侧甲",(1.12,5.4,-.1),(1.35,6.4,2.7),"shell"),
        box("背甲",(0,10.6,1.03),(2.7,5.3,1.3),"rim"),
        box("腹筋",(-.35,1,-1.20),(1,12.3,.65),"joint")]
def bake(name,elements):
    mesh={"positions":[],"normals":[],"uvs":[],"indices":[],"textureName":"blacklight:textures/entity/"+TEXTURE}
    for i,e in enumerate(elements):
        e["uuid"]=ident(name+"-"+str(i))
        x,y,z=e["from"];X,Y,Z=e["to"]
        corners=[[x,y,z],[X,y,z],[X,Y,z],[x,Y,z],[x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z]]
        for face,ids in faces.items():
            v=[rotate(corners[k],e["origin"],e["rotation"]) for k in ids]
            a=[q-p for p,q in zip(v[0],v[1])];b=[q-p for p,q in zip(v[0],v[2])]
            n=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
            length=math.sqrt(sum(x*x for x in n));assert length>0;n=[round(x/length,6) for x in n]
            u,v0,U,V=e["faces"][face]["uv"]
            begin=len(mesh["positions"])//3
            for point,uv in zip(v,[(u,V),(u,v0),(U,v0),(U,V)]):
                mesh["positions"].extend(round(t/16,6) for t in point);mesh["normals"].extend(n)
                mesh["uvs"].extend((uv[0]/64,uv[1]/32))
            mesh["indices"].extend(begin+t for t in (0,1,2,0,2,3))
    mesh["bounds"]={k:[fn(mesh["positions"][a::3]) for a in range(3)] for k,fn in (("min",min),("max",max))}
    bb={"meta":{"format_version":"4.10","model_format":"free","box_uv":False},"name":name,
        "resolution":{"width":64,"height":32},"elements":elements,"outliner":[e["uuid"] for e in elements],"textures":[texture]}
    (ART/(name+".bbmodel")).write_text(json.dumps(bb,ensure_ascii=False,separators=(",",":")),encoding="utf8")
    return mesh
meshes={}
for tier in profiles:
    meshes["groundspike_"+tier]=bake("groundspike_"+tier,horn(tier,lean=-5))
    cl=horn(tier,"主刃 ",lean=3)
    height=profiles[tier][-1][1]/48
    for i,(x,z,angle,tilt,s) in enumerate([(-7,5,53,23,.61),(6,-7,164,28,.72),(8,5,-65,35,.39)]):
        cl+=horn(tier,"侧刃"+str(i+1)+" ",(x*height,-1,z*height),s,tilt,angle)
    meshes["groundspike_cluster_"+tier]=bake("groundspike_cluster_"+tier,cl)
meshes["tendril_segment"]=bake("tendril_segment",strand())
meshes["tendril_segment_lod"]=bake("tendril_segment_lod",strand(True))
meshes["tendril_spike"]=bake("tendril_spike",[
    box("锚根",(0,0,0),(3.5,2.6,2.8),"joint"),box("外甲",(0,2,0),(2.7,3.2,2),"rim"),
    box("尖节",(.55,4.7,0),(1.45,2.5,1.4),"tip"),box("尖端",(1,6.8,0),(.65,1.5,.65),"tip")])
out=ASSETS/"models/devastators/weapon_skills.json";out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps({"meshes":meshes},separators=(",",":"),allow_nan=False),encoding="utf8")
print("Authored",len(meshes),"Blockbench projects. Runtime mesh bundle",hashlib.sha256(out.read_bytes()).hexdigest())
