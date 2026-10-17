"""Blender source/preview of the actual authored Minecraft cubes and atlas."""
from pathlib import Path
import bpy,json,math
from mathutils import Vector,Euler
R=Path(__file__).resolve().parents[1];out=R/'art/hydra'
data=json.loads((out/'block-model-source.json').read_text(encoding='utf8'))
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
def V(p):return Vector((p[0]/16,-p[2]/16,p[1]/16))
mat=bpy.data.materials.new('Original pixel hide - nearest');mat.use_nodes=True
n=mat.node_tree.nodes;p=n.get('Principled BSDF');p.inputs['Roughness'].default_value=.95
image=bpy.data.images.load(data['texture']);image.pack()
t=n.new('ShaderNodeTexImage');t.image=image;t.interpolation='Closest'
mat.node_tree.links.new(t.outputs['Color'],p.inputs['Base Color'])
faces=[(0,3,2,1),(4,5,6,7),(1,2,6,5),(0,4,7,3),(3,7,6,2),(0,1,5,4)]
directions=['north','south','east','west','up','down'];objects=[]
for box in data['boxes']:
    if box['bone']=='coil':continue
    sx,sy,sz=[v/2 for v in box['size']];center=Vector(box['center']);pivot=Vector(box['pivot'])
    rot=Euler([math.radians(a) for a in box['rotation']],'XYZ').to_matrix()
    local=[(-sx,-sy,-sz),(sx,-sy,-sz),(sx,sy,-sz),(-sx,sy,-sz),(-sx,-sy,sz),(sx,-sy,sz),(sx,sy,sz),(-sx,sy,sz)]
    verts=[V(pivot+rot@(center+Vector(p)-pivot)) for p in local]
    mesh=bpy.data.meshes.new(box['name']);mesh.from_pydata(verts,[],faces);mesh.update()
    o=bpy.data.objects.new(box['name'],mesh);bpy.context.collection.objects.link(o);o.data.materials.append(mat)
    uv=mesh.uv_layers.new(name='Minecraft face pixels')
    for face,name in zip(mesh.polygons,directions):
        tile=box['uv'][name];u,v=tile['uv'];w,h=tile['uv_size']
        points=[(u,v+h),(u,v),(u+w,v),(u+w,v+h)]
        for loop,co in zip(face.loop_indices,points):uv.data[loop].uv=(co[0]/data['texture_size'],1-co[1]/data['texture_size'])
    objects.append((o,box['bone']))
# Each box stays rigid, as in Minecraft, with pivots shared with the runtime.
arm=bpy.data.armatures.new('Hunter block skeleton');rig=bpy.data.objects.new('Hunter Rig',arm);bpy.context.collection.objects.link(rig)
bpy.context.view_layer.objects.active=rig;rig.select_set(True);bpy.ops.object.mode_set(mode='EDIT')
b=arm.edit_bones.new('root');b.head=(0,0,0);b.tail=(0,0,.15)
for name,p in data['pivots'].items():
    b=arm.edit_bones.new(name);b.head=V(p);b.tail=b.head+Vector((0,0,.15));b.parent=arm.edit_bones[data['parents'][name]]
bpy.ops.object.mode_set(mode='OBJECT');rig.show_in_front=True
for obj,bone in objects:
    group=obj.vertex_groups.new(name=bone);group.add(list(range(8)),1,'REPLACE')
    mod=obj.modifiers.new('Rigid Minecraft bone','ARMATURE');mod.object=rig
ground=bpy.data.materials.new('Preview floor');ground.diffuse_color=(.32,.34,.32,1)
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.009));bpy.context.object.data.materials.append(ground)
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=16
scene.render.resolution_x=1000;scene.render.resolution_y=900;scene.render.resolution_percentage=100
scene.world.color=(.38,.38,.38);scene.view_settings.view_transform='Standard'
def light(name,pos,energy,size):
    d=bpy.data.lights.new(name,'AREA');o=bpy.data.objects.new(name,d);bpy.context.collection.objects.link(o)
    o.location=pos;d.energy=energy;d.size=size;o.rotation_euler=(Vector((0,0,2.6))-o.location).to_track_quat('-Z','Y').to_euler()
light('Soft key',(-5,6,8),1300,5);light('Fill',(6,3,6),700,4);light('Rim',(0,-5,7),900,4)
d=bpy.data.cameras.new('Camera');cam=bpy.data.objects.new('Camera',d);bpy.context.collection.objects.link(cam);scene.camera=cam
d.type='ORTHO';d.ortho_scale=7.6
def camera(pos):
    cam.location=pos;cam.rotation_euler=(Vector((0,0,2.6))-cam.location).to_track_quat('-Z','Y').to_euler()
camera((3.7,6,3.0));bpy.ops.wm.save_as_mainfile(filepath=str(out/'hydra_authored.blend'))
for name,pos in [('three-quarter',(3.7,6,3)),('front',(0,12,4)),('side',(12,0,4)),('back',(0,-12,4))]:
    camera(pos);scene.render.filepath=str(out/(name+'.png'));bpy.ops.render.render(write_still=True)
print('BLOCK_HUNTER',len(objects),'cuboids, nearest pixel texture, editable Blender rig')
