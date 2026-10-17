"""Build Blender geometry from the same cuboids used by the editable bbmodel."""
import bpy, json, sys, math
from pathlib import Path
from mathutils import Vector, Matrix

ROOT=Path(__file__).resolve().parent
args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
OUT=ROOT/('model-v12' if '--reference' in args else 'model-v5' if '--bat-rig' in args else 'model-v2')
stem=args[0] if args else 'sovereign_wings'
data=json.loads((OUT/(stem+'.asset.json')).read_text(encoding='utf8'))
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
scene=bpy.context.scene
scene.render.engine='CYCLES';scene.cycles.samples=24
scene.cycles.use_denoising=True
scene.render.image_settings.file_format='PNG'
scene.render.film_transparent=False
scene.world.color=(.5,.5,.5)
scene.world.use_nodes=True;scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.45,.45,.45,1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value=.65
scene.view_settings.view_transform='Standard'
scene.view_settings.look='Medium High Contrast' if 'Medium High Contrast' in [i.name for i in bpy.types.ColorManagedViewSettings.bl_rna.properties['look'].enum_items] else 'None'
scene.view_settings.exposure=0;scene.view_settings.gamma=1
M=Matrix(((1,0,0),(0,0,-1),(0,1,0)))
def cv(v):return M@Vector(v)
rootcol=bpy.data.collections.new('SOVEREIGN_WINGS');scene.collection.children.link(rootcol)
rig={}
for bone in data['bones']:
 o=bpy.data.objects.new(bone['name'],None);rootcol.objects.link(o);o.empty_display_size=.7;o.empty_display_type='PLAIN_AXES'
 o['role']='Wing articulation pivot';o['native_pivot']=bone['pivot']
 parent=rig.get(bone['parent']);o.parent=parent
 o.location=cv(bone['pivot'])-(cv(next(b['pivot'] for b in data['bones'] if b['name']==bone['parent'])) if parent else Vector((0,0,0)))
 rig[bone['name']]=o
mat=bpy.data.materials.new('Blacklight_pixel_atlas');mat.use_nodes=True
nodes=mat.node_tree.nodes;links=mat.node_tree.links;bs=nodes.get('Principled BSDF');bs.inputs['Roughness'].default_value=.83
bs.inputs['Specular IOR Level'].default_value=.18
im=bpy.data.images.load(str(OUT/'sovereign_wings.png'));n=nodes.new('ShaderNodeTexImage');n.image=im;n.interpolation='Closest';links.new(n.outputs['Color'],bs.inputs['Base Color'])
emi=bpy.data.images.load(str(OUT/'sovereign_wings_emissive.png'));en=nodes.new('ShaderNodeTexImage');en.image=emi;en.interpolation='Closest';links.new(en.outputs['Color'],bs.inputs['Emission Color']);bs.inputs['Emission Strength'].default_value=.6

# Face corners, counter-clockwise when seen from outside in native Y-up space.
facespec=[('north',(0,3,2,1)),('south',(4,5,6,7)),('west',(0,4,7,3)),('east',(1,2,6,5)),('down',(0,1,5,4)),('up',(3,7,6,2))]
for bone in data['bones']:
 verts=[];faces=[];uvs=[]
 pivot=Vector(bone['pivot'])
 for e in (e for e in data['elements'] if e['_parent']==bone['name']):
  a=e['from'];b=e['to'];c=Vector(e['origin']);rx,ry,rz=[math.radians(v) for v in e['rotation']];rot=Matrix.Rotation(rx,3,'X')@Matrix.Rotation(ry,3,'Y')@Matrix.Rotation(rz,3,'Z')
  vs=[(a[0],a[1],a[2]),(b[0],a[1],a[2]),(b[0],b[1],a[2]),(a[0],b[1],a[2]),(a[0],a[1],b[2]),(b[0],a[1],b[2]),(b[0],b[1],b[2]),(a[0],b[1],b[2])]
  offset=len(verts);verts.extend(cv(rot@(Vector(v)-c)+c-pivot) for v in vs)
  for direction,idx in facespec:
   faces.append(tuple(offset+i for i in idx));u0,v0,u1,v1=e['faces'][direction]['uv']
   # Map U/V along actual face dimensions, not polygon-loop order. This
   # preserves the 2 px/unit density even on long thin membrane strips.
   ax,ay=(0,1) if direction in ('north','south') else ((2,1) if direction in ('east','west') else (0,2))
   uvface=[]
   for vi in idx:
    vv=vs[vi];fu=(vv[ax]-a[ax])/(b[ax]-a[ax]);fv=(vv[ay]-a[ay])/(b[ay]-a[ay])
    uvface.append(((u0+(u1-u0)*fu)/256,1-(v1-(v1-v0)*fv)/256))
   uvs.append(uvface)
 if not verts:continue
 mesh=bpy.data.meshes.new(bone['name']+'_cuboids');mesh.from_pydata(verts,[],faces);mesh.update()
 ob=bpy.data.objects.new(bone['name']+'_geometry',mesh);rootcol.objects.link(ob);ob.parent=rig[bone['name']];ob.data.materials.append(mat)
 layer=mesh.uv_layers.new(name='Atlas_2px_per_unit')
 for p,uv in zip(mesh.polygons,uvs):
  for loop,co in zip(p.loop_indices,uv):layer.data[loop].uv=co
 ob['cuboids']=len(verts)//8

# Actual rigid-node fold track. Source stays in the deployed rest pose at 1.
scene.frame_start=1;scene.frame_end=73;scene.render.fps=60
for label,sign in ([] if '--reference' in args else [('left',-1),('right',1)]):
 o=rig[label+'_root'];o.rotation_mode='XYZ'
 for frame,angle in [(1,0),(22,20),(52,58),(73,70)]:
  o.rotation_euler=(0,0,math.radians(angle*sign));o.keyframe_insert(data_path='rotation_euler',frame=frame)
 if o.animation_data and o.animation_data.action:o.animation_data.action.name=label+'_fold_1p2s'
scene.frame_set(1)

def light(name,loc,power,size):
 d=bpy.data.lights.new(name,'AREA');d.energy=power;d.shape='DISK';d.size=size;o=bpy.data.objects.new(name,d);scene.collection.objects.link(o);o.location=loc;o.rotation_euler=(Vector((0,-4,17))-o.location).to_track_quat('-Z','Y').to_euler()
light('Key',(-32,45,65),42000,48);light('Fill',(40,25,18),26000,42);light('Back',(-12,-35,55),34000,38)
camera_data=bpy.data.cameras.new('ReferenceCamera');camera=bpy.data.objects.new('ReferenceCamera',camera_data);scene.collection.objects.link(camera);scene.camera=camera;camera_data.type='ORTHO';camera_data.lens=50

def view(name,loc,target,scale,w=1500,h=1350,frame=1):
 scene.frame_set(frame);camera.location=loc;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();camera_data.ortho_scale=scale
 scene.render.resolution_x=w;scene.render.resolution_y=h;scene.render.resolution_percentage=100
 scene.render.filepath=str(OUT/(stem+'_'+name+'.png'));bpy.ops.render.render(write_still=True)

if '--root-review' in args or '--reference' in args:
 bodymat=bpy.data.materials.new('QA_body_proxy');bodymat.use_nodes=True;bodymat.node_tree.nodes.get('Principled BSDF').inputs['Base Color'].default_value=(.12,.15,.18,1)
 for name,center,size in [('torso',(0,18,0),(8,12,4)),('head',(0,28,0),(8,8,8)),('left_arm',(6,18,0),(4,12,4)),('right_arm',(-6,18,0),(4,12,4)),('left_leg',(2,6,0),(4,12,4)),('right_leg',(-2,6,0),(4,12,4))]:
  bpy.ops.mesh.primitive_cube_add(size=1,location=cv(center));ob=bpy.context.object;ob.name='QA_'+name
  ob.dimensions=(size[0],size[2],size[1]);ob.data.materials.append(bodymat)
 if '--reference' in args:
  view('front',(0,95,17),(0,-3,17),72,1500,1150)
  view('back',(0,-95,17),(0,-3,17),72,1500,1150)
  view('side',(95,-3,17),(0,-3,17),56,900,1150)
  view('perspective',(36,110,32),(0,-3,17),73,1500,1150)
  bpy.ops.wm.save_as_mainfile(filepath=str(OUT/(stem+'.blend')))
  raise SystemExit(0)
 view('root_back',(0,-95,20),(0,-2,20),24,1000,1000)
 view('root_side',(65,-70,24),(0,-2,20),24,1000,1000)
 bpy.ops.wm.save_as_mainfile(filepath=str(OUT/(stem+'_root_review.blend')))
 raise SystemExit(0)

if stem=='blockout':
 view('front',(0,95,16),(0,-4,16),68,1200,1050)
 bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'blockout.blend'))
else:
 view('front',(0,95,16),(0,-4,16),68)
 view('back',(0,-95,16),(0,-4,16),68)
 view('right',(95,-4,16),(0,-4,16),55,700,1350)
 view('perspective',(68,110,53),(0,-4,17),70,1700,1400)
 view('folded',(60,105,40),(0,-14,17),57,1200,1400,73)
 view('half_fold',(65,105,40),(0,-10,17),63,1200,1400,38)
 scene.frame_set(1)
 for image in (im,emi):image.pack()
 bpy.ops.object.select_all(action='DESELECT')
 for ob in rootcol.objects:ob.select_set(True)
 bpy.context.view_layer.objects.active=rig['wing_mount']
 # Game/interchange model export excludes lighting and presentation cameras.
 bpy.ops.export_scene.gltf(filepath=str(OUT/'sovereign_wings.glb'),export_format='GLB',use_selection=True,export_animations=True,export_animation_mode='SCENE',export_frame_range=True,export_yup=True)
 camera.location=(68,110,53);camera.rotation_euler=(Vector((0,-4,17))-camera.location).to_track_quat('-Z','Y').to_euler();camera_data.ortho_scale=70
 bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'sovereign_wings.blend'))
print('WINGS_RENDER_DONE',stem)
