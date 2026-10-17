"""Continuous skinned creature surfaces. Blender authoring, no imported commercial mesh.
Hydra: shared ring topology from root through neck and head; continuous crescent jaws.
Hunter: fused primary anatomy with soft joint weights, no stacked cuboid body pieces.
"""
from pathlib import Path
import bpy,json,math,random,bmesh,struct,zlib,binascii,sys
from mathutils import Vector,Euler
R=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(R/'tools'))
from hunter_skin_weights import weight_vertex
import runpy
runpy.run_path(str(R/'tools/author_hunter_model.py'))
runpy.run_path(str(R/'tools/author_hydra_model.py'))
A=R/'src/main/resources/assets/blacklight';OUT=R/'art/creature-connected';OUT.mkdir(exist_ok=True)
def read(p):return json.loads(p.read_text(encoding='utf8'))
def linear(c):
 c=c/255
 return c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4
def skin_color(species,p,base):
 x,y,z=[q*16 for q in p]
 # Spatially coherent, pixel-sized fibres: neighbouring triangles sample the same field.
 grain=((int(x*1.4)*73856093 ^ int(y*1.4)*19349663 ^ int(z*1.4)*83492791)&15)-7
 if species=='hydra':
  fibre=math.sin(y*.95+abs(x)*.95+z*.3)
  vein=math.sin(y*.34+abs(x)*.7+z*.18)
  shade=grain*.7+fibre*8
  if base==0x61212b and abs(vein)<.12:shade-=22
 else:
  fibre=math.sin(y*.75+abs(x)*.5+z*.25)
  shade=grain*.65+fibre*6
 return [max(0,min(255,((base>>s)&255)+int(shade))) for s in (16,8,0)]
def save(species,verts,faces):
 # A nearest-filtered atlas preserves Minecraft pixel detail on a continuous skin.
 size=1
 while (size//10)**2<len(faces):size*=2
 pixels=bytearray(size*size*4);cols=size//10
 for i,f in enumerate(faces):
  ox=(i%cols)*10;oy=(i//cols)*10;a,b,c=[Vector(verts[n]['p']) for n in f[:3]];base=f[3]
  for yy in range(10):
   for xx in range(10):
    u=max(0,min(1,(xx-1)/7));v=max(0,min(1,(yy-1)/7))
    if u+v>1:u,v=u/(u+v),v/(u+v)
    rgb=skin_color(species,a+(b-a)*u+(c-a)*v,base)
    at=((oy+yy)*size+ox+xx)*4;pixels[at:at+4]=bytes(rgb+[255])
  f.extend([(ox+1.5)/size,(oy+1.5)/size,(ox+8.5)/size,(oy+1.5)/size,(ox+1.5)/size,(oy+8.5)/size])
 def pngchunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',binascii.crc32(t+d)&0xffffffff)
 raw=b''.join(b'\x00'+pixels[y*size*4:(y+1)*size*4] for y in range(size))
 texture_path=A/'textures/entity'/f'{species}_connected.png'
 texture_path.write_bytes(b'\x89PNG\r\n\x1a\n'+pngchunk(b'IHDR',struct.pack('>2I5B',size,size,8,6,0,0,0))+pngchunk(b'IDAT',zlib.compress(raw,9))+pngchunk(b'IEND',b''))
 texture=bpy.data.images.load(str(texture_path),check_existing=False);texture.colorspace_settings.name='sRGB'
 payload={'schema':2,'authoring':'Authored continuous surface; two-joint smooth skin weights and spatial pixel fibres.','vertices':verts,'faces':faces}
 if species=='hunter':payload['skinning']='dual_quaternion'
 (A/'models'/f'{species}_connected.json').write_text(json.dumps(payload,separators=(',',':')),encoding='utf8')
 print(species,len(verts),'shared vertices',len(faces),'triangles')
 # A Blender preview uses the exact runtime vertices and facet colors.
 bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
 preview_faces=[f for f in faces if verts[f[0]]['w'][0]!='coil']
 mesh=bpy.data.meshes.new(species);mesh.from_pydata([(v['p'][0],-v['p'][2],v['p'][1]) for v in verts],[],[f[:3] for f in preview_faces]);mesh.update()
 obj=bpy.data.objects.new(species,mesh);bpy.context.collection.objects.link(obj)
 mat=bpy.data.materials.new(species+'_pixel_skin');mat.use_nodes=True;bs=mat.node_tree.nodes['Principled BSDF'];bs.inputs['Roughness'].default_value=.83
 tex=mat.node_tree.nodes.new('ShaderNodeTexImage');tex.image=texture;tex.interpolation='Closest';mat.node_tree.links.new(tex.outputs['Color'],bs.inputs['Base Color']);obj.data.materials.append(mat)
 uv=mesh.uv_layers.new()
 for poly,f in zip(mesh.polygons,preview_faces):
  for n,loop in enumerate(poly.loop_indices):uv.data[loop].uv=(f[4+n*2],1-f[5+n*2])
 bpy.ops.wm.save_as_mainfile(filepath=str(OUT/(species+'.blend')))
 return obj
V=[];F=[]
def vertex(p,w):
 # Gecko already reflects the JSON bone/cube X axis at load time. These are runtime coordinates.
 V.append({'p':[p[0]/16,p[1]/16,p[2]/16],'w':w});return len(V)-1
def tube(path,radii,weights,color,sides=8):
 start=len(V)
 for i,(point,radius) in enumerate(zip(path,radii)):
  p=Vector(point);t=(Vector(path[min(i+1,len(path)-1)])-Vector(path[max(0,i-1)])).normalized()
  axis=t.cross(Vector((0,0,1)))
  if axis.length<.01:axis=t.cross(Vector((0,1,0)))
  axis.normalize();other=t.cross(axis).normalized()
  for j in range(sides):vertex(p+(axis*math.cos(j*2*math.pi/sides)+other*math.sin(j*2*math.pi/sides))*radius,weights[i])
  if i:
   for j in range(sides):
    a=start+(i-1)*sides+j;b=start+(i-1)*sides+(j+1)%sides;c=start+i*sides+(j+1)%sides;d=start+i*sides+j
    F.extend([[a,b,c,color],[a,c,d,color]])
 for end,flip in [(start,False),(start+(len(path)-1)*sides,True)]:
  for j in range(1,sides-1):F.append([end,end+j+(1 if flip else 0),end+j+(0 if flip else 1),color])
profiles=[(0,0,20,17),(7,0,18,15),(17,1,14,13),(29,0,12,11),(40,-2,10.8,10),(51,-6,9.6,9),(61,-12,8.8,8.5),(69,-21,10,8),(72,-30,9,7),(70,-37,7.5,6)]
def cat(p0,p1,p2,p3,t):return .5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t)
points=[]
for i in range(len(profiles)-1):
 p0=Vector(profiles[max(0,i-1)]);p1=Vector(profiles[i]);p2=Vector(profiles[i+1]);p3=Vector(profiles[min(len(profiles)-1,i+2)])
 for step in range(3):points.append(cat(p0,p1,p2,p3,step/3))
points.append(Vector(profiles[-1]))
# Weights transition over shared vertices at each joint; there are no per-section end caps.
levels=[(0,'base'),(14,'neck_0'),(23,'neck_1'),(32,'neck_2'),(41,'neck_3'),(50,'neck_4'),(59,'neck_5'),(66,'neck_6'),(73,'head')]
def neckweight(y,z):
 if z<-25:return ['head','head',0]
 for (lo,a),(hi,b) in zip(levels,levels[1:]):
  if y<=hi:return [a,b,max(0,min(1,(y-lo)/(hi-lo)))]
 return ['head','head',0]
N=24
for i,p in enumerate(points):
 y,z,rx,rz=p
 before=points[max(0,i-1)];after=points[min(len(points)-1,i+1)];tangent=Vector((0,after[0]-before[0],after[1]-before[1])).normalized();cross=Vector((0,-tangent.z,tangent.y))
 for j in range(N):
  a=2*math.pi*j/N;fold=1+.09*math.cos(a*5+y*.075)+.04*math.sin(y*.45+abs(math.cos(a))*2)
  q=Vector((math.cos(a)*rx*fold,y,z))+cross*(math.sin(a)*rz*fold)
  vertex(q,neckweight(y,z))
 for j in range(N):
  if i==0:continue
  n=(j+1)%N;ids=[(i-1)*N+j,(i-1)*N+n,i*N+n,i*N+j]
  # Dark dorsal carapace, broad red muscular underside. Color boundaries follow the form.
  back=math.sin((j+.5)*2*math.pi/N)>.45
  # Integrated carapace ridges follow the flesh instead of floating collars.
  color=0x30262a if back else 0x61212b
  F.extend([[ids[0],ids[2],ids[1],color],[ids[0],ids[3],ids[2],color]])
# Interior of the open mouth, recessed into the same final rim.
centre=vertex((0,70,-33),['head','head',0]);start=(len(points)-1)*N
for j in range(N):F.append([start+j,centre,start+(j+1)%N,0x170b10])
# The mandibles are tapered lofts, not overlapping boxes.
for sign in (-1,1):
 start=len(V);bone='pincer_left' if sign<0 else 'pincer_right'
 for i in range(10):
  t=i/9;x=sign*(8+19*math.sin(t*math.pi*.85));y=70+24*t;z=-29-17*t;r=max(.06,6.5*(1-t)**.65)
  for j in range(8):
   angle=j*math.pi/4
   weight=[bone,'head',max(0,.7-i*.35)] if t<.4 else [bone,'claw_left' if sign<0 else 'claw_right',min(1,(t-.4)/.35)]
   vertex((x,y+math.cos(angle)*r,z+math.sin(angle)*r*.65),weight)
  if i:
   for j in range(8):
    n=(j+1)%8;ids=[start+(i-1)*8+j,start+(i-1)*8+n,start+i*8+n,start+i*8+j]
    color=0xb1a082 if j in (2,3,4,5) or i>6 else 0x61212b
    if sign<0:F.extend([[ids[0],ids[2],ids[1],color],[ids[0],ids[3],ids[2],color]])
    else:F.extend([[ids[0],ids[1],ids[2],color],[ids[0],ids[2],ids[3],color]])
# Mouth rim, teeth and crest grow from the continuous head surface.
for sign in (-1,1):
 for k in range(3):
  x=sign*(2.2+k*2.2)
  tube([(x,74,-36),(x*.95,72.5,-38),(x*.9,71,-38)],[.8,.5,.04],[['head','head',0]]*3,0xc6b28d,6)
  tube([(x,65,-34),(x*.95,66.5,-37),(x*.9,68,-37)],[.75,.45,.04],[['jaw','jaw',0]]*3,0xb1a082,6)
 tube([(sign*5,76,-24),(sign*6,78,-22),(sign*7,80,-18)],[3.6,2.5,.08],[['head','head',0]]*3,0x30262a,6)
# A connected lower jaw, separated only at its actual articulating hinge.
tube([(-6,66,-28),(-5,64,-33),(0,63,-36),(5,64,-33),(6,66,-28)],[2.3,2.6,2.8,2.6,2.3],[['jaw','head',.15]]*5,0x61212b,8)
# Lengthen the trunk only; head/jaws move as a unit and retain their own proportions.
# The rooted base stays broad while the middle loses 27% of its diameter.
def stretch_trunk(p):
 x,y,z=p
 if y<61:
  slender=1-.27*max(0,min(1,(y-7)/24))*max(0,min(1,(61-y)/10))
  return [x*slender,y*1.35,z]
 return [x,y+21.35,z]
for v in V:v['p']=[q/16 for q in stretch_trunk([q*16 for q in v['p']])]
# Capture uses the two articulated horns. No wrapping tentacle on this creature.
save('hydra',V,F)
geo=read(A/'geo/blacklight_hydra.geo.json')
geo['minecraft:geometry'][0]['bones']=[b for b in geo['minecraft:geometry'][0]['bones'] if b['name']!='coil']
for b in geo['minecraft:geometry'][0]['bones']:
 if b['name']=='coil':b['pivot']=[0,0,-27]
 else:
  p=b['pivot'];runtime=stretch_trunk([-p[0],p[1],p[2]]);b['pivot']=[-runtime[0],runtime[1],runtime[2]]
 b['cubes']=[]
for sign,side in [(-1,'left'),(1,'right')]:
 geo['minecraft:geometry'][0]['bones'].append({'name':'claw_'+side,'parent':'pincer_'+side,'pivot':[-sign*26,103,-37.5],'cubes':[]})
geo['minecraft:geometry'][0]['description']['visible_bounds_height']=9
geo['minecraft:geometry'][0]['description']['visible_bounds_offset']=[0,3.4,0]
(A/'geo/blacklight_hydra.geo.json').write_text(json.dumps(geo,indent=2),encoding='utf8')
# Hunter's anatomy is welded before skinning. The original authoring volumes define proportions only.
data=read(R/'art/hunter/block-model-source.json');parts=[];volumes=[]
keep=['Pelvis','Tapered waist','Broad ribcage','Raised shoulder back','Neck bridge','Low bald head','Lower abdominal block','Open lower jaw','Defined pectoral','Collar muscle','Scapula','Cheek plane','Jaw hinge']
tokens=[' shoulder',' upper arm',' elbow',' long forearm',' wrist',' broad palm',' finger ',' thumb',' thigh',' forward knee',' rearward shin',' hock',' grounded foot']
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
for box in data['boxes']:
 name=box['name']
 if name not in keep and not any(t in name for t in tokens):continue
 if any(t in name for t in ['cap','tendon','claw']):continue
 c=Vector(box['center']);scale=Vector(box['size'])*.62
 bpy.ops.mesh.primitive_uv_sphere_add(segments=12,ring_count=8,location=c);o=bpy.context.object;o.scale=scale;o.rotation_euler=Euler([math.radians(x) for x in box['rotation']]);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);parts.append(o);volumes.append((c,scale,box['bone'],o.rotation_euler.to_matrix().transposed()))
bpy.ops.object.select_all(action='DESELECT')
for o in parts:o.select_set(True)
bpy.context.view_layer.objects.active=parts[0];bpy.ops.object.join();obj=parts[0]
bpy.ops.object.transform_apply(location=True,rotation=True,scale=True)
rem=obj.modifiers.new('Continuous welded tissue','REMESH');rem.mode='VOXEL';rem.voxel_size=.65;bpy.ops.object.modifier_apply(modifier=rem.name)
sm=obj.modifiers.new('Relax primary silhouette','SMOOTH');sm.factor=.8;sm.iterations=3;bpy.ops.object.modifier_apply(modifier=sm.name)
dec=obj.modifiers.new('Game facet budget','DECIMATE');dec.ratio=.18;bpy.ops.object.modifier_apply(modifier=dec.name)
tri=obj.modifiers.new('Triangles','TRIANGULATE');bpy.ops.object.modifier_apply(modifier=tri.name)
V=[];F=[]
parents={b['name']:b.get('parent') for b in read(A/'geo/blacklight_hunter_authored.geo.json')['minecraft:geometry'][0]['bones']}
for v in obj.data.vertices:
 vertex(v.co,weight_vertex(v.co,volumes,parents))
for i,face in enumerate(obj.data.polygons):
 a,b,c=face.vertices;center=face.center;bone=V[a]['w'][0];dark='forearm' in bone or 'shin' in bone
 color=0x302b28 if dark else 0x644c40
 F.append([a,b,c,color])
# Hard tips remain anatomical parts but share precisely the same bone transform as their flesh.
for box in data['boxes']:
 name=box['name'].lower();bone=box['bone'];x,y,z=box['center'];sx,sy,sz=box['size']
 if any(t in name for t in ['claw','toe']):
  tube([(x,y,z+sz*.6),(x,y-.1,z),(x,y-.2,z-sz*.9)],[sx*.6,sx*.42,.025],[[bone,bone,0]]*3,0x231f1c,6)
 elif any(t in name for t in ['fang','tooth']):
  sign=-1 if 'upper' in name else 1
  tube([(x,y-sign*sy*.5,z),(x,y,z-.15),(x,y+sign*sy*.8,z-.3)],[sx*.6,sx*.4,.025],[[bone,bone,0]]*3,0xb2a082,6)
 elif 'mouth' in name:
  tube([(x-sx*.5,y,z),(x,y,z-.2),(x+sx*.5,y,z)],[sy*.48,sy*.62,sy*.48],[[bone,bone,0]]*3,0x251b19,6)
save('hunter',V,F)
geo=read(A/'geo/blacklight_hunter_authored.geo.json')
for b in geo['minecraft:geometry'][0]['bones']:b['cubes']=[]
(A/'geo/blacklight_hunter_authored.geo.json').write_text(json.dumps(geo,indent=2),encoding='utf8')
