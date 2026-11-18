"""Named anatomical cuboids using the accepted project models' material pixels."""
import hashlib,json,math
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/blacklight'
supreme=Image.open(ASSETS/'textures/entity/supreme_hunter_block.png').convert('RGB')
hunter=Image.open(ASSETS/'textures/entity/hunter_mc.png').convert('RGB')
old_geo=json.loads((ASSETS/'geo/blacklight_hunter_mc.geo.json').read_text())['minecraft:geometry'][0]
jaw=next(b for b in old_geo['bones'] if b['name']=='jaw')['cubes'][1]['uv']['north']
x,y=jaw['uv'];w,h=jaw['uv_size']
PATCHES={'skin':supreme.crop((143,37,157,45)),
 'fold':supreme.crop((81,75,91,79)),
 'muscle':supreme.crop((185,1,192,15)),
 'sinew':supreme.crop((46,58,49,65)),
 'plate':supreme.crop((143,37,157,45)),
 'bone':hunter.crop((int(x),int(y),int(x+w),int(y+h))),
 'dark':supreme.crop((81,75,91,79)),
 'scar':supreme.crop((106,58,114,64))}
DEFAULT={'skin':(108,81,76),'fold':(79,56,53),'muscle':(106,31,34),
 'sinew':(134,34,32),'plate':(90,78,72),'bone':(151,136,110),
 'dark':(32,25,25),'scar':(112,49,46)}
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()

class Model:
 def __init__(self,id,height,reference):
  self.id=id;self.height=height;self.scale=height/2;self.reference=reference
  self.bones=[];self.by={};self.parts=[];self.colors=DEFAULT.copy();self.features=[]
  self.bone('root',None,[0,0,0])
 def bone(self,name,parent,p):
  b={'name':name,'pivot':[round(v*self.scale,4) for v in p],'cubes':[]}
  if parent:b['parent']=parent
  self.bones.append(b);self.by[name]=b
  return name
 def box(self,name,b,p,s,mat='skin',r=None):
  c={'origin':[round((v-w/2)*self.scale,4) for v,w in zip(p,s)],'size':[round(v*self.scale,4) for v in s]}
  if r and any(r):c.update(pivot=[round(v*self.scale,4) for v in p],rotation=[round(v,4) for v in r])
  self.by[b]['cubes'].append(c);self.parts.append((name,b,c,mat));return c
 def beam(self,name,b,a,z,w,d,mat='skin'):
  v=[z[i]-a[i] for i in range(3)]
  return self.box(name,b,[(a[i]+z[i])/2 for i in range(3)],[w,math.dist(a,z)+.12,d],mat,
   [-math.degrees(math.atan2(math.hypot(v[0],v[2]),v[1])),math.degrees(math.atan2(v[0],v[2])),0])
 def export(self):
  n=512;atlas=Image.new('RGBA',(n,n),(26,20,20,255));faces=[]
  for name,b,c,mat in self.parts:
   w,h,d=c['size'];c['uv']={}
   for face,u,v in [('north',w,h),('south',w,h),('east',d,h),('west',d,h),('up',w,d),('down',w,d)]:
    faces.append((max(1,math.ceil(u)),max(1,math.ceil(v)),name,c,mat,face))
  faces.sort(key=lambda v:(-v[1],-v[0]));x=y=1;row=0
  means={k:tuple(sum(p[ch] for p in im.get_flattened_data())/(im.width*im.height) for ch in range(3)) for k,im in PATCHES.items()}
  for w,h,name,c,mat,face in faces:
   if x+w+1>=n:x=1;y+=row+2;row=0
   if y+h+1>=n:raise ValueError(self.id+' atlas overflow')
   c['uv'][face]={'uv':[x,y],'uv_size':[w,h]}
   patch=PATCHES[mat];mean=means[mat];base=self.colors[mat];salt=sum(map(ord,name))
   for v in range(-1,h+1):
    for u in range(-1,w+1):
     pu=max(0,min(w-1,u));pv=max(0,min(h-1,v));src=patch.getpixel(((pu+salt)%patch.width,(pv+salt//7)%patch.height))
     shade=5 if face=='up' else -7 if face=='down' else 0
     if w>3 and h>3 and (pu==0 or pv==h-1):shade-=5
     rgb=tuple(max(0,min(255,round(base[k]+src[k]-mean[k]+shade))) for k in range(3))
     atlas.putpixel((x+u,y+v),rgb+(255,))
   x+=w+2;row=max(row,h)
  data={'format_version':'1.12.0','minecraft:geometry':[{'description':{
   'identifier':'geometry.blacklight.'+self.id,'texture_width':n,'texture_height':n,
   'visible_bounds_width':12,'visible_bounds_height':max(6,self.height*1.6),'visible_bounds_offset':[0,self.height/2,0]},'bones':self.bones}]}
  gp=ASSETS/'geo'/f'{self.id}_mc.geo.json';tp=ASSETS/'textures/entity'/f'{self.id}_mc.png'
  gp.write_text(json.dumps(data,indent=2)+'\n');atlas.save(tp)
  return {'id':self.id,'revision':'supreme-style-authored-r6','bones':len(self.bones),'cuboids':len(self.parts),
   'textureSize':n,'atlasUsedHeight':y+row+1,'geometrySha256':sha(gp),'textureSha256':sha(tp),
   'reference':self.reference,'features':self.features,
   'parts':[{'name':name,'bone':b,'material':mat} for name,b,c,mat in self.parts]}
