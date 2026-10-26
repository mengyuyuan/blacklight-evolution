"""Fit the actual local P2 Runner1 arm silhouette to the Minecraft Evolved skeleton.

Unlike the player weapon or the earlier authored guard plates, this adapter
retains the source cutting profile, dorsal hooks and open tendon surfaces.
"""
from pathlib import Path
import argparse
import base64
import hashlib
import importlib.util
import json
import math
import uuid
import numpy as np
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parents[1]

def author(source_dir):
    spec=importlib.util.spec_from_file_location('p2_source',source_dir/'verify-rig.py');rig=importlib.util.module_from_spec(spec);spec.loader.exec_module(rig)
    C=np.diag([1.,-1.,1.]);bind=np.array(rig.global_bind)
    motion=json.loads((ROOT/'art/motion-profile/evolved-poses.data').read_text());bones=motion['bones'];index={b['name']:i for i,b in enumerate(bones)}
    pivots=np.array([b['pivot'] for b in bones]);path=ROOT/'src/main/resources/assets/blacklight/models/evolved_player_rig.json'
    old=json.loads(path.read_text());body=[f for f in old['faces'] if f[5]==0];used=sorted({i for f in body for i in f[:4]});remap={i:n for n,i in enumerate(used)}
    vertices=[old['vertices'][i] for i in used];faces=[[*[remap[i] for i in f[:4]],*f[4:]] for f in body]
    def unit(v):return v/max(1e-12,np.linalg.norm(v))
    def frame(direction):
        y=unit(direction);z=np.array([0.,0.,1.]);z=unit(z-y*(z@y));x=unit(np.cross(y,z));return np.array([x,y,np.cross(x,y)]).T
    armframes={};armfit={}
    for side in ['R','L']:
        s=index['Shoulder_'+side];e=index['Elbow_'+side];w=index['Wrist_'+side]
        for a,b in [(s,e),(e,w)]:
            armframes[a]=frame(C@(bind[b,:3,3]-bind[a,:3,3]));armfit[a]=np.linalg.norm(pivots[b]-pivots[a])/(np.linalg.norm(bind[b,:3,3]-bind[a,:3,3])*16)
    def bone_for(j):
        name=bones[j]['name'];side='R' if name.endswith('_R') else 'L'
        if any(part in name for part in ['Elbow','Forarm','Wrist','Thumb','Index','Middle','Ring','Pinky']):return index['Elbow_'+side]
        return j
    def fit(point,j):
        d=C@(point-bind[j,:3,3])*16
        if j in armframes:
            d=armframes[j].T@d;d*=np.array([1.05,armfit[j],1.05])
            if bones[j]['name'].startswith('Elbow'):
                # Preserve the wrist joint length. Adapt only the distal blade's
                # overhang to the shorter Minecraft torso and standing legs.
                if d[1]>5.5:d[1]=5.5+(d[1]-5.5)*.68
            return pivots[j]+d
        return pivots[j]+d*np.array([1.2,.95,1.1])
    source=next(part for part in rig.parts if 'arm_blade' in part[0]);name,points,weights,joints,triangles=source
    start=len(vertices);bound=[];side_for=[]
    for point,ws,js in zip(points,weights,joints):
        influences={}
        for w,j in zip(ws,js):
            if w>0:target=bone_for(int(j));influences[target]=influences.get(target,0)+float(w)
        ordered=sorted(influences.items(),key=lambda x:-x[1])[:2]
        if len(ordered)==1:ordered.append((ordered[0][0],0))
        total=sum(w for _,w in ordered);ordered=[(j,w/total) for j,w in ordered]
        p=sum((fit(point,j)*w for j,w in ordered),np.zeros(3))
        # Faceted source surfaces and pixel material keep the actual silhouette;
        # no collection of decorative cubes is substituted for the arm.
        p=np.round(p*16)/16
        vertices.append([*[round(float(v),6) for v in p],ordered[0][0],ordered[1][0],round(ordered[1][1],6)])
        bound.append(p);side_for.append(3 if point[0]>0 else 2)
    bound=np.array(bound)
    # New Minecraft material atlas. Original game textures are not copied.
    size=256;albedo=Image.new('RGBA',(size,size));glow=Image.new('RGBA',(size,size),(0,0,0,0))
    palette=[(30,28,32),(49,47,53),(78,76,82),(51,20,27)]
    for y in range(size):
        for x in range(size):
            tile=x//64;grain=((x*5+y*3)%7-3)//2;rgb=palette[tile]
            albedo.putpixel((x,y),(*tuple(max(0,v+grain) for v in rgb),255))
    draw=ImageDraw.Draw(albedo);emit=ImageDraw.Draw(glow)
    for tile in [0,1,3]:
        s=tile*64;vein=[(s+20,0),(s+17,22),(s+21,42),(s+18,61),(s+22,83),(s+20,107),(s+24,134),(s+22,161),(s+27,194),(s+25,230),(s+29,255)]
        draw.line(vein,fill=(11,8,13,255),width=4);draw.line(vein,fill=(99,24,34,255),width=2);emit.line(vein,fill=(116,23,32,65),width=1)
        for j in range(1,9,2):
            x,y=vein[j];draw.line([(x,y),(x+6,y-8),(x+10,y-17)],fill=(78,20,29,255),width=1)
    editable_faces=[];glow_faces=0
    for tri in triangles:
        ids=[int(i) for i in tri[::-1]];p=bound[ids];normal=np.cross(p[1]-p[0],p[2]-p[0])
        if np.linalg.norm(normal)<1e-7:continue
        center=p.mean(0);side=3 if points[ids].mean(0)[0]>0 else 2
        # The material follows the blade plane and tissue thickness, while the
        # existing source geometry supplies the actual relief and open spaces.
        axis=int(np.argmax(np.abs(normal)));tile=2 if axis!=0 and center[1]>10 else 1 if center[1]>7 else 0
        uv=[]
        for x,y,z in p:
            relative=x-(5 if side==3 else -5)
            u=(abs(relative)+2)*4 if axis!=0 else (z+4)*4
            uv.append([tile*64+max(1,min(62,u)),max(1,min(254,y*4+14))])
        meshids=[start+i for i in ids];flat=[round(v/size,6) for pair in [*uv,uv[-1]] for v in pair]
        faces.append([*meshids,meshids[-1],side,4,*flat,-1]);editable_faces.append((meshids,uv))
        box=(int(min(u for u,v in uv)),int(min(v for u,v in uv)),int(max(u for u,v in uv))+1,int(max(v for u,v in uv))+1)
        if glow.getchannel('A').crop(box).getbbox():faces.append([*meshids,meshids[-1],side,5,*flat,-1]);glow_faces+=1
    textures=ROOT/'src/main/resources/assets/blacklight/textures/entity';albedo.save(textures/'evolved_arms.png');glow.save(textures/'evolved_arms_emissive.png')
    runner_skin(textures/'evolved_runner_mc.png')
    source_file=source_dir/'extracted/art/art/packages/missions/evolved_runner1/evolved_runner1.p3d'
    lineage={'asset':name,'sourceFileSHA256':hashlib.sha256(source_file.read_bytes()).hexdigest(),'adaptation':'Original P2 Evolved Runner1 arm surfaces and cutting silhouette, fitted to Minecraft shoulder/elbow proportions; own pixel material. Wrist/blade influences follow the rigid forearm.'}
    mesh={'schema':2,'vertices':vertices,'faces':sorted(faces,key=lambda f:f[5]),'body':'Standard player cuboids; original P2 Runner1 arm silhouette adapted to Minecraft proportions.','armSource':lineage}
    path.write_text(json.dumps(mesh,separators=(',',':')),encoding='utf8')
    # Editable neutral reference: exact adapted mesh, separate upper/forearm groups.
    key=lambda name:str(uuid.uuid5(uuid.NAMESPACE_URL,'blacklight:p2-evolved-arm:'+name))
    elements=[];outliner=[]
    for side in [2,3]:
        side_name='R' if side==2 else 'L';vmap={};fs={}
        for ids,uv in editable_faces:
            if side_for[ids[0]-start]!=side:continue
            keys=[];mapped={}
            for i,t in zip(ids,uv):
                k=str(i);p=vertices[i];vmap[k]=[p[0],24-p[1],p[2]];keys.append(k);mapped[k]=t
            fs[str(len(fs))]={'vertices':keys[::-1],'uv':mapped,'texture':0}
        element=key(side_name);elements.append({'name':'Runner1 arm '+side_name,'uuid':element,'type':'mesh','origin':[0,0,0],'vertices':vmap,'faces':fs})
        outliner.append({'name':'Shoulder_'+side_name,'uuid':key('group'+side_name),'origin':[(-5 if side==2 else 5),22,0],'children':[element]})
    bb={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':'P2 Evolved MC arm adapter','resolution':{'width':size,'height':size},'elements':elements,'outliner':outliner,
        'textures':[{'name':'evolved_arms.png','id':'0','uuid':key('texture'),'width':size,'height':size,'uv_width':size,'uv_height':size,'source':'data:image/png;base64,'+base64.b64encode((textures/'evolved_arms.png').read_bytes()).decode()}]}
    editable=ROOT/'art/evolved-arm-adapter';(editable/'evolved_arms.bbmodel').write_text(json.dumps(bb,separators=(',',':')),encoding='utf8')
    stats={'source':lineage,'sourceVertices':len(points),'sourceTriangles':len(triangles),'bodyQuads':len(body),'adaptedArmTriangles':len(editable_faces),'glowTriangles':glow_faces,
           'adaptedBounds':[bound.min(0).tolist(),bound.max(0).tolist()],'body':'unchanged standard Minecraft proportions','textureSize':[size,size],'editable':'art/evolved-arm-adapter/evolved_arms.bbmodel'}
    (ROOT/'docs/design/evolved-complete-arms.json').write_text(json.dumps(stats,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    reportpath=ROOT/'docs/design/evolved-form-motion-sources.json';report=json.loads(reportpath.read_text());report.update(vertices=len(vertices),quads=len(faces));reportpath.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps(stats,ensure_ascii=False))

def runner_skin(path):
    """New pixel skin for the source Runner1's hood, respirator and tactical uniform."""
    image=Image.new('RGBA',(64,64),(0,0,0,0));d=ImageDraw.Draw(image)
    def box(u,v,w,h,depth,base):
        shades=[-4,3,-7,0]
        for a,b,shade in [(u,depth,shades[0]),(u+depth,w,shades[1]),(u+depth+w,depth,shades[2]),(u+2*depth+w,w,shades[3])]:
            color=tuple(max(0,c+shade) for c in base);d.rectangle((a,v+depth,a+b-1,v+depth+h-1),fill=(*color,255))
        d.rectangle((u+depth,v,u+depth+w-1,v+depth-1),fill=(*tuple(c+6 for c in base),255))
        d.rectangle((u+depth+w,v,u+depth+2*w-1,v+depth-1),fill=(*base,255))
    box(0,0,8,8,8,(23,26,32));box(16,16,8,12,4,(49,60,73));box(40,16,4,12,4,(42,52,65));box(32,48,4,12,4,(42,52,65));box(0,16,4,12,4,(46,55,64));box(16,48,4,12,4,(46,55,64))
    # Hood edge, goggles, respirator and filters; recognizable without a round head.
    d.rectangle((8,8,15,15),fill=(17,20,25,255));d.line([(8,10),(9,8),(14,8),(15,10)],fill=(43,47,55,255),width=1)
    d.rectangle((9,10,14,11),fill=(66,68,63,255));d.rectangle((9,10,10,10),fill=(153,145,55,255));d.rectangle((13,10,14,10),fill=(153,145,55,255))
    d.rectangle((11,11,12,14),fill=(39,42,46,255));d.point((11,12),fill=(72,74,78,255));d.rectangle((9,13,10,14),fill=(47,50,54,255));d.rectangle((13,13,14,14),fill=(47,50,54,255))
    d.line([(18,8),(18,14),(20,15)],fill=(35,39,46,255),width=1);d.line([(29,8),(29,14),(27,15)],fill=(35,39,46,255),width=1)
    # Vest panels, central fastener, chest insignia, belt and rear harness.
    d.rectangle((20,20,27,21),fill=(29,36,44,255));d.rectangle((21,22,23,26),fill=(69,73,76,255));d.rectangle((25,22,27,26),fill=(65,70,76,255))
    d.line([(24,21),(24,29)],fill=(26,31,38,255),width=1);d.rectangle((20,28,27,30),fill=(24,29,34,255));d.rectangle((23,29,24,29),fill=(108,104,86,255));d.rectangle((20,26,21,28),fill=(54,60,62,255));d.rectangle((26,26,27,28),fill=(54,60,62,255));d.point((21,22),fill=(161,150,55,255))
    d.rectangle((32,20,39,31),fill=(36,44,54,255));d.line([(33,20),(36,25),(38,31)],fill=(21,27,34,255),width=1);d.line([(38,20),(35,25),(33,31)],fill=(21,27,34,255),width=1)
    for u,v in [(4,20),(20,52)]:
        d.rectangle((u,v+5,u+3,v+7),fill=(26,32,39,255));d.rectangle((u+1,v+5,u+2,v+6),fill=(79,82,84,255));d.rectangle((u,v+9,u+3,v+11),fill=(22,25,29,255));d.line([(u,v+8),(u+3,v+8)],fill=(34,39,45,255),width=1)
    image.save(path)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--source-dir',type=Path,required=True);author(parser.parse_args().source_dir)
