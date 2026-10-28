"""Author seven MC-scale arm sets; reuse the existing player body, atlas and full rig.
No source animations or existing blade geometry are rewritten.
"""
from pathlib import Path
import base64, io, json, math, uuid
from PIL import Image,ImageDraw
from p2_evolved_variant_surfaces import append_power

ROOT=Path(__file__).resolve().parents[1]
MODELS=ROOT/'src/main/resources/assets/blacklight/models'
base=json.loads((MODELS/'evolved_player_rig.json').read_text('utf-8'))
bones=json.loads((ROOT/'art/motion-profile/evolved-poses.data').read_text('utf-8'))['bones']
lookup={b['name']:i for i,b in enumerate(bones)}
body=[f for f in base['faces'] if f[5]==0]
used=sorted({i for f in body for i in f[:4]});remap={v:i for i,v in enumerate(used)}
body_vertices=[base['vertices'][i] for i in used]
body_faces=[[*[remap[i] for i in f[:4]],*f[4:]] for f in body]

textures=ROOT/'src/main/resources/assets/blacklight/textures/entity'
skin=Image.open(textures/'evolved_runner_mc.png').resize((128,128),Image.Resampling.NEAREST)
draw=ImageDraw.Draw(skin)
draw.rectangle((16,16,31,31),fill=(15,18,23,255))
draw.line([(17,19),(19,17),(28,17),(30,19)],fill=(32,36,42,255),width=1)
draw.rectangle((18,20,22,21),fill=(85,75,37,255));draw.point((19,20),fill=(126,112,53,255))
draw.rectangle((26,20,28,21),fill=(47,53,51,255));draw.point((27,20),fill=(70,76,65,255))
draw.rectangle((22,23,26,29),fill=(29,32,36,255));draw.line((23,24,25,24),fill=(45,48,51,255))
draw.rectangle((18,26,20,28),fill=(31,35,40,255));draw.rectangle((28,26,30,28),fill=(31,35,40,255))
draw.rectangle((0,64,31,95),fill=(45,53,63,255))
for y in range(65,95,6):draw.line((1,y,30,y),fill=(55,63,74,255))
for x in (1,30):draw.line((x,65,x,94),fill=(80,87,93,255))
draw.rectangle((64,0,95,31),fill=(24,27,32,255));draw.line((66,2,93,2),fill=(33,37,43,255),width=1)
draw.rectangle((96,0,127,31),fill=(62,65,68,255))
for i in range(1,28,5):draw.line((98,i,125,i),fill=(67,70,74,255))
skin.save(textures/'evolved_variant_body.png')
atlas=Image.new('RGBA',(256,256));glow=Image.new('RGBA',(256,256),(0,0,0,0))
palettes=[(30,28,32),(49,47,53),(78,76,82),(51,20,27)]
for y in range(256):
    for x in range(256):
        tile=x//64;u=x%64;rgb=palettes[tile]
        grain=((x*13+y*7)%9-4)//2
        fibre=-6 if (u+y//3)%11<2 else 3 if (u+y//3)%11==3 else 0
        seam=-7 if tile==1 and y%32<2 else 0
        highlight=10 if tile in (1,2) and u%32<3 else 0
        vein=tile in (0,1,3) and abs(u-(21+(y//16%3)*2))<=1
        color=(143,24,37) if vein else tuple(max(0,min(255,v+grain+fibre+seam+highlight)) for v in rgb)
        atlas.putpixel((x,y),(*color,255))
        if vein:glow.putpixel((x,y),(165,26,38,135))
atlas.save(textures/'evolved_variant_arms.png');glow.save(textures/'evolved_variant_arms_emissive.png')

class Model:
    def __init__(self,key):
        self.key=key;self.vertices=[v[:] for v in body_vertices];self.faces=[f[:] for f in body_faces]
        for v in self.vertices:
            if v[3] in (lookup['Hip_R'],lookup['Knee_R']):v[0]=-2.05+(v[0]+1.9)*.95
            elif v[3] in (lookup['Hip_L'],lookup['Knee_L']):v[0]=2.05+(v[0]-1.9)*.95
        self.detail(1,'Head',[(0,-8.18,-3.85,4.1,.3),(0,-7.5,-3.85,4.1,.3)],'hood')
        for s in [-1,1]:
            self.detail(1,'Head',[(s*3.95,-7.5,-3.75,.22,.3),(s*3.95,-1,-3.65,.22,.28)],'hood')
            top=2.0 if s<0 else 3.6;end=6.4 if s<0 else 5.8
            self.detail(0,'Spine_2',[(s*2.0,top,-2.1,1.25,.3),(s*2.0,end-.6,-2.2,1.3,.4),(s*2.0,end,-2.1,1.1,.25)],'cloth')
            self.detail(0,'Spine_2',[(s*3.75,8.0,.1,.6,1.0),(s*3.9,10.9,.1,.7,1.1),(s*3.7,11.5,.1,.45,.8)],'cloth')
            role=4 if s<0 else 5;bone='Knee_R' if s<0 else 'Knee_L'
            self.detail(role,bone,[(s*2,17.5,-2.0,1.1,.25),(s*2,18.3,-2.1,1.45,.45),(s*2,20.4,-2.1,1.4,.45),(s*2,21.0,-2,1,.2)],'plate')
            self.detail(role,bone,[(s*2.05,22,-.2,1.9,2),(s*2.05,23.8,-.45,1.95,2.25),(s*2.05,24,-.5,1.9,2.15)],'hood')
        self.detail(0,'Spine_2',[(0,.1,1.65,3.7,.5),(0,1.4,1.8,4, .7),(0,2.1,1.6,3.5,.5)],'cloth')
        self.detail(0,'Spine_2',[(0,9.1,-2.15,.75,.3),(0,10.2,-2.15,.75,.3)],'plate')
    def detail(self,role,bone,sections,material):
        first=len(self.faces);self.loft(role,bone,sections)
        corner={'hood':(66,2),'cloth':(2,66),'plate':(98,2)}[material]
        for f in self.faces[first:]:
            f[5]=0;f[-1]=-1;points=[self.vertices[i][:3] for i in f[:4]]
            spans=[max(p[i] for p in points)-min(p[i] for p in points) for i in range(3)];axes=sorted(range(3),key=lambda i:-spans[i])[:2]
            for j,p in enumerate(points):
                f[6+j*2]=(corner[0]+min(27,(p[axes[0]]-min(q[axes[0]] for q in points))*4))/128
                f[7+j*2]=(corner[1]+min(27,(p[axes[1]]-min(q[axes[1]] for q in points))*4))/128
    def face(self,points,role,bone,tile=0,glow=False):
        ids=[]
        for p in points:
            self.vertices.append([*[round(round(v*16)/16,5) for v in p],lookup[bone],lookup[bone],0]);ids.append(len(self.vertices)-1)
        a,b,c=points[:3];u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
        n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
        axis=max(range(3),key=lambda i:abs(n[i]));axes=[i for i in range(3) if i!=axis]
        uv=[]
        for p in points:
            uv.extend([(tile*64+min(62,max(1,(p[axes[0]]+8)*4)))/256,min(254,max(1,(p[axes[1]]+10)*4))/256])
        color=-1
        self.faces.append([*ids,role,5 if glow else 4,*[round(x,6) for x in uv],color])
    def loft(self,role,bone,sections,tile=0):
        rings=[]
        for x,y,z,rx,rz in sections:
            bevel=min(.35,rx*.28,rz*.28)
            rings.append([(x-rx+bevel,y,z-rz),(x+rx-bevel,y,z-rz),(x+rx,y,z-rz+bevel),(x+rx,y,z+rz-bevel),(x+rx-bevel,y,z+rz),(x-rx+bevel,y,z+rz),(x-rx,y,z+rz-bevel),(x-rx,y,z-rz+bevel)])
        for a,b in zip(rings,rings[1:]):
            for i in range(8):self.face([a[i],a[(i+1)%8],b[(i+1)%8],b[i]],role,bone,tile)
        for end,reverse in ((rings[0],True),(rings[-1],False)):
            center=tuple(sum(p[i] for p in end)/8 for i in range(3))
            for i in range(8):
                pts=[center,end[i],end[(i+1)%8],center]
                self.face(pts[::-1] if reverse else pts,role,bone,tile)
    def source_arm(self,side,distal=1):
        role=2 if side=='R' else 3
        self.faces=[f for f in self.faces if f[4]!=role or f[5]==0]
        fs=[f for f in base['faces'] if f[4]==role and f[5]>=4]
        indices=sorted({i for f in fs for i in f[:4]});mapping={}
        for i in indices:
            p=base['vertices'][i][:];p[1]=p[1] if p[1]<=12 else 12+(p[1]-12)*distal
            mapping[i]=len(self.vertices);self.vertices.append(p)
        self.faces.extend([*[mapping[i] for i in f[:4]],*f[4:]] for f in fs)
    def finish(self):
        used=sorted({i for f in self.faces for i in f[:4]});mapping={i:k for k,i in enumerate(used)}
        self.vertices=[self.vertices[i] for i in used];self.faces=[[*[mapping[i] for i in f[:4]],*f[4:]] for f in self.faces]
        result={'schema':2,'vertices':self.vertices,'faces':sorted(self.faces,key=lambda f:f[5]),'body':'Standard Minecraft player with fitted cloth/vest/boots and actual locally decoded power surfaces.','variant':self.key,'sourceSurfaces':getattr(self,'lineage',[base.get('armSource',{})] if self.key=='blade' else [])}
        target=MODELS/'evolved_variants';target.mkdir(exist_ok=True)
        (target/(self.key+'.json')).write_text(json.dumps(result,separators=(',',':')),'utf-8')
        # Editable static generic mesh, grouped by the actual runtime driver.
        elements=[];groups=[];texture=ROOT/'src/main/resources/assets/blacklight/textures/entity/evolved_variant_arms.png'
        def visible_body(f):
            if f[5]!=0:return False
            if f[4] not in (2,3):return True
            return self.key not in ('blade','skirmisher') and not (self.key=='armored' and f[4]==2) and sum(self.vertices[i][1] for i in f[:4])/4<=6.5
        authored=[f for f in self.faces if f[5] in (4,6,8) or visible_body(f)]
        colors=sorted({f[-1] for f in authored if f[5]==8})
        palette=Image.new('RGBA',(16,max(1,(len(colors)+15)//16)),(255,255,255,255))
        swatches={color:(i%16,i//16) for i,color in enumerate(colors)}
        for color,point in swatches.items():palette.putpixel(point,((color>>16)&255,(color>>8)&255,color&255,(color>>24)&255))
        def palette_source():
            data=io.BytesIO();palette.save(data,format='PNG');return 'data:image/png;base64,'+base64.b64encode(data.getvalue()).decode('ascii')
        def editable_uv(f,j):
            if f[5]==8:
                x,y=swatches[f[-1]];return [x+.5,y+.5]
            width=128 if f[5]==0 else 256
            return [f[6+j*2]*width,f[7+j*2]*width]
        for bone_index in sorted({self.vertices[f[0]][3] for f in authored}):
            fs=[f for f in authored if self.vertices[f[0]][3]==bone_index]
            indices=sorted({i for f in fs for i in f[:4]});ids={v:str(i) for i,v in enumerate(indices)}
            ident=str(uuid.uuid5(uuid.NAMESPACE_URL,self.key+str(bone_index)))
            vertices={ids[i]:[self.vertices[i][0],24-self.vertices[i][1],self.vertices[i][2]] for i in indices}
            def texture_index(f):return 0 if f[5]==0 else 2 if f[5]==6 else 3 if f[5]==8 else 1
            faces={str(i):{'vertices':[ids[x] for x in reversed(f[:4])],'uv':{ids[x]:editable_uv(f,j) for j,x in enumerate(f[:4])},'texture':texture_index(f)} for i,f in enumerate(fs)}
            elements.append({'name':bones[bone_index]['name'],'uuid':ident,'type':'mesh','origin':[0,0,0],'vertices':vertices,'faces':faces})
            groups.append({'name':bones[bone_index]['name'],'origin':[bones[bone_index]['pivot'][0],24-bones[bone_index]['pivot'][1],bones[bone_index]['pivot'][2]],'children':[ident]})
        if self.key in ('blade','skirmisher','armored'):texture=textures/'evolved_arms.png'
        editable={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':self.key+'_evolved','resolution':{'width':256,'height':256},'elements':elements,'outliner':groups,'textures':[{'path':str(textures/'evolved_variant_body.png'),'name':'evolved_variant_body.png','id':'0','width':128,'height':128,'uv_width':128,'uv_height':128},{'path':str(texture),'name':texture.name,'id':'1','width':256,'height':256,'uv_width':256,'uv_height':256}]}
        editable['textures'].extend([{'path':str(textures/'weapons_mc.png'),'name':'weapons_mc.png','id':'2','width':256,'height':256,'uv_width':256,'uv_height':256},{'name':self.key+'_vertex_palette.png','id':'3','width':palette.width,'height':palette.height,'uv_width':palette.width,'uv_height':palette.height,'source':palette_source()}])
        for item in editable['textures']:
            if 'path' in item:item['source']='data:image/png;base64,'+base64.b64encode(Path(item.pop('path')).read_bytes()).decode('ascii')
        art=ROOT/'art/evolved-variants';art.mkdir(exist_ok=True)
        (art/(self.key+'.bbmodel')).write_text(json.dumps(editable,separators=(',',':')),'utf-8')
        return {'type':self.key,'vertices':len(self.vertices),'submittedQuads':len(self.faces),'weaponSurfaces':sum(f[5] in (4,6,8) for f in self.faces),'editableSurfaces':len(authored),'bytes':(target/(self.key+'.json')).stat().st_size,'sourceSurfaces':result['sourceSurfaces']}

reports=[]
for key in ['blade','strength','armored','predator','tendril','skirmisher','sensory','regenerator']:
    m=Model(key);lineage=[]
    if key in ('blade','skirmisher'):
        m.source_arm('R',1 if key=='blade' else .83);m.source_arm('L',1 if key=='blade' else .83)
        lineage=[base.get('armSource',{})]
    elif key=='strength':lineage.append(append_power(m,'alex_hammerfist3',bones))
    elif key=='predator':lineage.append(append_power(m,'alex_claws3',bones))
    elif key=='tendril':lineage.append(append_power(m,'alex_tendril3',bones))
    elif key=='armored':
        lineage.append(append_power(m,'alex_tendril3',bones,sides=('L',)))
        lineage.append(append_power(m,'heller_shield_3',bones,sides=('L',),shield=True,append=True))
        m.source_arm('R',.78)
    elif key=='sensory':lineage.append(append_power(m,'alex_claws2',bones))
    elif key=='regenerator':lineage.append(append_power(m,'alex_tendril2',bones))
    m.lineage=lineage;reports.append(m.finish())
(ROOT/'docs/design/evolved-variants-geometry.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2)+'\n','utf-8')
print(json.dumps(reports))
