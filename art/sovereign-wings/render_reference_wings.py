"""Reference geometry QA; articulated folding plus the custom two-bone tissue skinning."""
from pathlib import Path
ROOT=Path(__file__).resolve().parent
base=(ROOT/'render_wings.py').read_text(encoding='utf8')
exec(compile(base[:base.index('# Actual rigid-node fold track.')],str(ROOT/'render_wings.py'),'exec'))
from mathutils import Quaternion
import numpy as np
pivots={b['name']:Vector(b['pivot']) for b in data['bones']}
parents={b['name']:b['parent'] for b in data['bones']}
paths={n:[Vector(p) for p in path] for n,path in data['skin_paths'].items()}
fold={n:Quaternion() for n in pivots}
errors=[]
variant="right_over" if "--right-over" in args else "left_over"
outer_side=-1 if variant=="right_over" else 1
def solve(a,b,c,target,pole):
 l1=(b-a).length;l2=(c-b).length;v=target-a;d=v.length
 dc=max(abs(l1-l2)+.001,min(l1+l2-.001,d));u=v.normalized()
 if abs(dc-d)>.01:errors.append({'target':list(target),'requested':d,'reachable':dc})
 target=a+u*dc;perp=(pole-u*pole.dot(u)).normalized()
 x=(l1*l1-l2*l2+dc*dc)/(2*dc);h=math.sqrt(max(0,l1*l1-x*x));mid=a+u*x+perp*h
 return (b-a).rotation_difference(mid-a),(c-b).rotation_difference(target-mid),target
for side,label in [(1,'left'),(-1,'right')]:
 for i in range(1,5):
  a,b,c,d=[label+'_'+part+'_'+str(i) for part in ['rib','arch','finger','tip']]
  apex=Vector((side*[8.7,8.0,8.0,10.5][i-1],[37.2,35.3,34,31.8][i-1],[7,8.1,9.2,10.2][i-1]+(.9 if side==outer_side else -.3)))
  q1,q2,apex=solve(pivots[a],pivots[b],pivots[c],apex,Vector((side*.65,0,1)))
  end=Vector((side*[3.2,2.1,-.5,4.3][i-1],[6,3.2,1.7,4.0][i-1],[7.8,8.0,8.2,9.2][i-1]+(1.3 if side==outer_side else -.4)))
  # Translate the distal rest chain to the folded apex before solving its bend.
  delta=apex-pivots[c]
  q3,q4,end=solve(apex,pivots[d]+delta,paths[d][-1]+delta,end,Vector((-side*.45,0,1)))
  fold[a]=q1;fold[b]=q1.conjugated()@q2;fold[c]=q2.conjugated()@q3;fold[d]=q3.conjugated()@q4

def distance(p,path):
 best=1e9
 for a,b in zip(path,path[1:]):
  delta=b-a;t=max(0,min(1,(p-a).dot(delta)/max(1e-9,delta.length_squared)))
  best=min(best,(p-(a+delta*t)).length)
 return best
weighted={};rest_coordinates={}
for bone in data['bones']:
 name=bone['name'];ob=bpy.data.objects.get(name+'_geometry')
 if ob is None:continue
 rest_coordinates[name]=[v.co.copy() for v in ob.data.vertices]
 entries=[];at=0
 for e in (e for e in data['elements'] if e['_parent']==name):
  for k in range(8):
   p=M.inverted()@ob.data.vertices[at+k].co+pivots[name]
   if '_root_skin' in e:
    a='wing_mount';b=e['_root_skin'];start,end=map(Vector,data['root_paths'][b]);v=end-start
    weight=0
   elif e.get('_skin'):
    side=name.split('_')[0];near=sorted([(distance(p,path),n) for n,path in paths.items() if n.startswith(side)])[:2]
    a=near[0][1];b=near[1][1];weight=near[0][0]/max(1e-8,near[0][0]+near[1][0])
   else:continue
   entries.append((at+k,p,a,b,weight))
  at+=8
 if entries:weighted[name]=entries

frames={};skins={}
def set_pose(t,store=False,frame=1):
 for name in pivots:
  part=name.split('_')[1] if '_' in name else ''
  delay={'rib':0,'arch':.08,'finger':.04,'tip':.14}.get(part,0)
  u=max(0,min(1,(t-delay)/(1-delay)));u=u*u*(3-2*u)
  q=Quaternion().slerp(fold[name],u);parent=parents[name]
  native=Matrix.Translation(pivots[name]-(pivots[parent] if parent else Vector()))@q.to_matrix().to_4x4()
  frames[name]=(frames[parent]@native) if parent else native
  skins[name]=frames[name]@Matrix.Translation(-pivots[name])
  rig[name].rotation_mode='QUATERNION';rig[name].rotation_quaternion=Quaternion((q.w,q.x,-q.z,q.y))
  if store:rig[name].keyframe_insert(data_path='rotation_quaternion',frame=frame)
 for name,entries in weighted.items():
  ob=bpy.data.objects[name+'_geometry'];inv=frames[name].inverted()
  for index,p,a,b,w in entries:ob.data.vertices[index].co=cv(inv@((skins[a]@p).lerp(skins[b]@p,w)))
  ob.data.update()
 bpy.context.view_layer.update()

scene.frame_start=1;scene.frame_end=37;scene.render.fps=24
# Bake correction shape keys only for deforming tissue and membranes. Rigid
# armour remains on real articulation nodes; no whole-model squash or scaling.
for name in weighted:bpy.data.objects[name+'_geometry'].shape_key_add(name='Basis')
for frame in range(1,38):
 set_pose((frame-1)/36,True,frame)
 for name in weighted:
  ob=bpy.data.objects[name+'_geometry'];key=ob.shape_key_add(name=f'Fold_{frame:02d}')
  for vertex,src in zip(key.data,ob.data.vertices):vertex.co=src.co
  key.value=0;key.keyframe_insert('value',frame=frame-1)
  key.value=1;key.keyframe_insert('value',frame=frame)
  key.value=0;key.keyframe_insert('value',frame=frame+1)
for name,verts in rest_coordinates.items():
 ob=bpy.data.objects[name+'_geometry']
 for v,co in zip(ob.data.vertices,verts):v.co=co
for action in bpy.data.actions:
 # glTF samples the tracks; explicitly linear keys avoid interpolation overshoot.
 for layer in action.layers:
  for strip in layer.strips:
   for bag in strip.channelbags:
    for fc in bag.fcurves:
     for k in fc.keyframe_points:k.interpolation='LINEAR'

# Use the same view/light helpers; presentations include a neutral scale proxy.
a=base.index('def light(');b=base.index("if '--root-review'",a)
exec(compile(base[a:b],str(ROOT/'render_wings.py'),'exec'))
bodymat=bpy.data.materials.new('Body_scale_proxy');bodymat.use_nodes=True
bodymat.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value=(.065,.075,.09,1)
bodymat.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value=.85
proxy=[]
for name,center,size in [('torso',(0,18,0),(8,12,4)),('head',(0,28,0),(8,8,8)),('left_arm',(6,17.8,0),(4,11.6,4)),('right_arm',(-6,17.8,0),(4,11.6,4)),('left_leg',(2.05,6,0),(3.9,12,4)),('right_leg',(-2.05,6,0),(3.9,12,4))]:
 bpy.ops.mesh.primitive_cube_add(size=1,location=cv(center));ob=bpy.context.object;ob.name='QA_'+name
 ob.dimensions=(size[0],size[2],size[1]);ob.data.materials.append(bodymat);proxy.append(ob)
# Geometry-only intersection samples in the neutral body pose, across folding.
head_hits=[];bounds=[]
for frame in [1,7,13,19,25,31,37]:
 scene.frame_set(frame);dg=bpy.context.evaluated_depsgraph_get();points=[];hits=0
 for ob in rootcol.objects:
  if ob.type!='MESH':continue
  eo=ob.evaluated_get(dg);mesh=eo.to_mesh()
  for v in mesh.vertices:
   p=M.inverted()@(eo.matrix_world@v.co);points.append(p)
   if abs(p.x)<4 and 24<p.y<32 and abs(p.z)<4:hits+=1
  eo.to_mesh_clear()
 bounds.append({'frame':frame,'min':[min(p[i] for p in points) for i in range(3)],'max':[max(p[i] for p in points) for i in range(3)]})
 head_hits.append({'frame':frame,'head_vertex_samples':hits})
(OUT/('fold_validation_'+variant+'.json')).write_text(json.dumps({'unreachable_targets':errors,'head_samples':head_hits,'bounds':bounds,'scope':'Neutral body only. Not flight or Minecraft runtime validation.'},indent=2))
(OUT/('fold_pose_'+variant+'.json')).write_text(json.dumps({n:[q.x,q.y,q.z,q.w] for n,q in fold.items()},indent=2))
if '--pose-only' in args:raise SystemExit(0)
if '--fold-only' in args:
 view('folded_back_'+variant,(0,-95,19),(0,-7,19),46,1050,1150,37)
 view('folded_side_'+variant,(95,-8,19),(0,-8,19),45,1050,1150,37)
 raise SystemExit(0)
if '--export-only' not in args:
 if '--update-fold' not in args:
  view('front',(0,95,17),(0,-3,17),74,1500,1150,1)
  view('back',(0,-95,17),(0,-3,17),74,1500,1150,1)
  view('side',(95,-3,17),(0,-3,17),54,850,1150,1)
  view('perspective',(36,110,32),(0,-3,17),74,1500,1150,1)
 view('folded_back_'+variant,(0,-95,19),(0,-7,19),46,1050,1150,37)
 view('folded_side_'+variant,(95,-8,17),(0,-8,17),45,1050,1150,37)
 view('half_fold',(36,105,30),(0,-7,17),65,1250,1150,19)
 for ob in proxy:ob.hide_render=True
 if '--update-fold' not in args:view('wings_only',(22,110,27),(0,-3,17),74,1500,1150,1)
 view('folded_only_'+variant,(25,-95,25),(0,-8,19),46,1100,1150,37)
scene.frame_set(1)
for im in bpy.data.images:
 if im.source=='FILE':im.pack()
bpy.ops.object.select_all(action='DESELECT')
for ob in rootcol.objects:ob.select_set(True)
bpy.context.view_layer.objects.active=rig['wing_mount']
bpy.ops.export_scene.gltf(filepath=str(OUT/'sovereign_wings.glb'),export_format='GLB',use_selection=True,export_animations=True,export_animation_mode='SCENE',export_anim_scene_split_object=False,export_frame_range=True,export_yup=True)
camera.location=(36,110,32);camera.rotation_euler=(Vector((0,-3,17))-camera.location).to_track_quat('-Z','Y').to_euler();camera_data.ortho_scale=74
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'sovereign_wings.blend'))
print('REFERENCE_REFINEMENT_DONE',len(data['elements']),head_hits,errors)

