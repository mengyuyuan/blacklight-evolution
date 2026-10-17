import bpy,json,sys,math
from pathlib import Path
from mathutils import Vector,Quaternion
ROOT=Path(__file__).resolve().parent;OUT=ROOT/'model-v13'
args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
if '--v14' in args:OUT=ROOT/'model-v14'
if '--v15' in args:OUT=ROOT/'model-v15'
stem='sovereign_wings' if '--detail' in args else 'blockout'
data=json.loads((OUT/(stem+'.asset.json')).read_text())
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=24
scene.render.image_settings.file_format='PNG';scene.world.color=(.35,.35,.35)
scene.view_settings.view_transform='Standard'
cv=lambda p:Vector((p[0],-p[2],p[1]))
mats=[]
colorImage=bpy.data.images.load(str(OUT/'sovereign_wings.png'))
emissionImage=bpy.data.images.load(str(OUT/'sovereign_wings_emissive.png'))
for i,color in enumerate(data['palette']):
    mat=bpy.data.materials.new('surface_'+str(i));mat.use_nodes=True
    node=mat.node_tree.nodes.get('Principled BSDF');node.inputs['Base Color'].default_value=tuple((v/255)**2.2 for v in color)+(1,)
    node.inputs['Roughness'].default_value=.68
    texture=mat.node_tree.nodes.new('ShaderNodeTexImage');texture.image=colorImage;texture.interpolation='Closest'
    glow=mat.node_tree.nodes.new('ShaderNodeTexImage');glow.image=emissionImage;glow.interpolation='Closest'
    mat.node_tree.links.new(texture.outputs['Color'],node.inputs['Base Color'])
    mat.node_tree.links.new(glow.outputs['Color'],node.inputs['Emission Color']);node.inputs['Emission Strength'].default_value=.75
    mats.append(mat)
rig={}
for b in data['bones']:
    ob=bpy.data.objects.new(b['name'],None);scene.collection.objects.link(ob);rig[b['name']]=ob
    ob.location=cv(b['pivot'])
    if b['parent']:ob.parent=rig[b['parent']];ob.location-=cv(next(n['pivot'] for n in data['bones'] if n['name']==b['parent']))
armdata=bpy.data.armatures.new('SovereignWingSkeleton');arm=bpy.data.objects.new('SovereignWingSkeleton',armdata);scene.collection.objects.link(arm)
bpy.context.view_layer.objects.active=arm;arm.select_set(True);bpy.ops.object.mode_set(mode='EDIT')
for b in data['bones']:
    bone=armdata.edit_bones.new(b['name']);bone.head=cv(b['pivot']);bone.tail=bone.head+Vector((0,1,0))
    if b['parent']:bone.parent=armdata.edit_bones[b['parent']]
bpy.ops.object.mode_set(mode='OBJECT');arm.select_set(False)
surfaces=[]
for e in data['elements']:
    keys=list(e['vertices']);indices={k:i for i,k in enumerate(keys)}
    pivot=next(b['pivot'] for b in data['bones'] if b['name']==e['_parent'])
    verts=[cv(e['vertices'][k]) for k in keys]
    mesh=bpy.data.meshes.new(e['name']);mesh.from_pydata(verts,[],[[indices[k] for k in f['vertices']] for f in e['faces'].values()]);mesh.update()
    uv=mesh.uv_layers.new(name='UVMap')
    for poly,face in zip(mesh.polygons,e['faces'].values()):
        for loop,key in zip(poly.loop_indices,face['vertices']):u,v=face['uv'][key];uv.data[loop].uv=(u/256,1-v/256)
    ob=bpy.data.objects.new(e['name'],mesh);scene.collection.objects.link(ob);ob.parent=arm;ob.data.materials.append(mats[e['_material']]);surfaces.append(ob)
    groups={}
    for i,key in enumerate(keys):
        a,b,t=e['_weights'][key]
        for name,weight in ((a,1-t),(b,t)):
            if weight<=0:continue
            if name not in groups:groups[name]=ob.vertex_groups.new(name=name)
            groups[name].add([i],weight,'ADD')
    mod=ob.modifiers.new('WeightedWingSurface','ARMATURE');mod.object=arm
    ob['source_weights']=json.dumps(e['_weights'])
proxy=[]
proxyMat=bpy.data.materials.new('player_scale');proxyMat.use_nodes=True
proxyMat.node_tree.nodes.get('Principled BSDF').inputs['Base Color'].default_value=(.22,.24,.28,1)
for name,pos,size in [('body',(0,18,0),(8,12,4)),('head',(0,28,0),(8,8,8)),('arm_l',(6,18,0),(4,12,4)),('arm_r',(-6,18,0),(4,12,4)),('leg_l',(2,6,0),(4,12,4)),('leg_r',(-2,6,0),(4,12,4))]:
    bpy.ops.mesh.primitive_cube_add(size=1,location=cv(pos));ob=bpy.context.object;ob.name='QA_'+name;ob.dimensions=(size[0],size[2],size[1]);ob.data.materials.append(proxyMat);proxy.append(ob)
for name,loc,power,size in [('key',(20,45,70),20000,40),('fill',(-40,15,35),12000,40),('rim',(20,-30,50),24000,30)]:
    d=bpy.data.lights.new(name,'AREA');d.energy=power;d.shape='DISK';d.size=size;ob=bpy.data.objects.new(name,d);scene.collection.objects.link(ob);ob.location=loc;ob.rotation_euler=(Vector((0,0,20))-ob.location).to_track_quat('-Z','Y').to_euler()
cameraData=bpy.data.cameras.new('Camera');cameraData.type='ORTHO';camera=bpy.data.objects.new('Camera',cameraData);scene.collection.objects.link(camera);scene.camera=camera
def view(name,pos,scale,width=1100,height=900):
    camera.location=pos;camera.rotation_euler=(Vector((0,-3,19))-camera.location).to_track_quat('-Z','Y').to_euler();cameraData.ortho_scale=scale
    scene.render.resolution_x=width;scene.render.resolution_y=height;scene.render.resolution_percentage=100;scene.render.filepath=str(OUT/(stem+'-'+name+'.png'));bpy.ops.render.render(write_still=True)
if '--no-render' not in args:
    view('front',(0,110,19),76)
    view('back',(0,-110,19),76)
    view('side',(110,-3,19),52,700,900)
    view('left',(-110,-3,19),52,700,900)
    view('perspective',(38,110,34),76)
for ob in proxy:ob.hide_render=True
for variant,poses in data['fold_poses'].items():
    arm.animation_data_create();arm.animation_data.action=None
    for frame,amount in ((1,0),(16,.5),(31,1)):
        for name,q in poses.items():
            p=arm.pose.bones[name];p.rotation_mode='QUATERNION'
            target=Quaternion((q[3],q[0],-q[2],q[1]));p.rotation_quaternion=Quaternion().slerp(target,amount)
            p.keyframe_insert('rotation_quaternion',frame=frame,group=name)
    arm.animation_data.action.name='fold_'+variant
    track=arm.animation_data.nla_tracks.new();track.name='fold_'+variant
    strip=track.strips.new('fold_'+variant,1,arm.animation_data.action);track.mute=True
arm.animation_data.action=None
for p in arm.pose.bones:p.rotation_quaternion=Quaternion()
scene.frame_set(1)
bpy.ops.object.select_all(action='DESELECT');arm.select_set(True)
for ob in surfaces:ob.select_set(True)
bpy.ops.export_scene.gltf(filepath=str(OUT/(stem+'.glb')),export_format='GLB',use_selection=True,export_animations=True,export_animation_mode='ACTIONS')
bpy.ops.file.pack_all()
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/(stem+'.blend')))
print('CONTINUOUS_WING_RENDER_DONE',len(data['elements']))
