"""Compare exported weighted folds against the authored native mesh, in Blender."""
import bpy,json,sys
from pathlib import Path
from mathutils import Matrix,Vector,Quaternion

root=Path(__file__).resolve().parent/('model-v15' if '--v15' in sys.argv else 'model-v13')
data=json.loads((root/'sovereign_wings.asset.json').read_text())
def expected(variant,amount):
    frames={};skin={};pivots={}
    for b in data['bones']:
        name=b['name'];p=Vector(b['pivot']);pivots[name]=p
        parent=b['parent'];q=data['fold_poses'][variant][name]
        q=Quaternion().slerp(Quaternion((q[3],q[0],q[1],q[2])),amount)
        frames[name]=(frames[parent] if parent else Matrix.Identity(4))@Matrix.Translation(p-(pivots[parent] if parent else Vector()))@q.to_matrix().to_4x4()
        skin[name]=frames[name]@Matrix.Translation(-p)
    points=[]
    for e in data['elements']:
        for key,p in e['vertices'].items():
            a,b,t=e['_weights'][key];v=skin[a]@Vector(p);v=v.lerp(skin[b]@Vector(p),t)
            points.append(Vector((v.x,-v.z,v.y)))
    return bounds(points)
def bounds(points):return {k:[f(p[i] for p in points) for i in range(3)] for k,f in [('min',min),('max',max)]}
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
bpy.context.scene.render.fps=24
bpy.ops.import_scene.gltf(filepath=str(root/'sovereign_wings.glb'))
arms=[o for o in bpy.context.scene.objects if o.type=='ARMATURE'];assert len(arms)==1,len(arms)
arm=arms[0];assert len(arm.data.bones)==35,len(arm.data.bones)
surfaces=[o for o in bpy.context.scene.objects if o.type=='MESH' and any(m.type=='ARMATURE' for m in o.modifiers)]
assert len(surfaces)==len(data['elements']),(len(surfaces),len(data['elements']))
assert not any(o.name.startswith('QA_') for o in bpy.context.scene.objects)
results=[]
for variant in data['fold_poses']:
    action=next(a for a in bpy.data.actions if a.name.startswith('fold_'+variant))
    arm.animation_data_create()
    for track in arm.animation_data.nla_tracks:track.mute=True
    arm.animation_data.action=action
    if len(action.slots):arm.animation_data.action_slot=action.slots[0]
    for frame,amount in [(1,0),(16,.5),(31,1)]:
        bpy.context.scene.frame_set(frame);dg=bpy.context.evaluated_depsgraph_get();points=[]
        for ob in bpy.context.scene.objects:
            if ob.type!='MESH' or not ob.modifiers:continue # Blender's imported bone display widget is not exported geometry.
            evaluated=ob.evaluated_get(dg);mesh=evaluated.to_mesh()
            points.extend(evaluated.matrix_world@v.co for v in mesh.vertices);evaluated.to_mesh_clear()
        actual=bounds(points);target=expected(variant,amount)
        error=max(abs(actual[k][i]-target[k][i]) for k in target for i in range(3))
        assert error<.02,(variant,frame,error,actual,target)
        results.append({'variant':variant,'frame':frame,'max_bound_error':error})
(root/'export_validation.json').write_text(json.dumps({'weighted_glb_roundtrip':'passed','bones':35,'samples':results},indent=2))
print('WEIGHTED_GLB_ROUNDTRIP_PASS',results)
