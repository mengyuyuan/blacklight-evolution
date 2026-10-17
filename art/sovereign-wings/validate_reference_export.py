import bpy,json,sys
from pathlib import Path
from mathutils import Matrix
root=Path(__file__).resolve().parent/'model-v12'
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
bpy.context.scene.render.fps=24
bpy.ops.import_scene.gltf(filepath=str(root/'sovereign_wings.glb'))
source=json.loads((root/'fold_validation_left_over.json').read_text())
m=Matrix(((1,0,0),(0,0,-1),(0,1,0))).inverted()
results=[]
for frame in [1,19,37]:
 bpy.context.scene.frame_set(frame);dg=bpy.context.evaluated_depsgraph_get();points=[]
 for ob in bpy.context.scene.objects:
  if ob.type!='MESH':continue
  eo=ob.evaluated_get(dg);mesh=eo.to_mesh()
  points.extend(m@(eo.matrix_world@v.co) for v in mesh.vertices);eo.to_mesh_clear()
 actual={'min':[min(v[i] for v in points) for i in range(3)],'max':[max(v[i] for v in points) for i in range(3)]}
 expected=next(r for r in source['bounds'] if r['frame']==frame)
 error=max(abs(actual[k][i]-expected[k][i]) for k in ['min','max'] for i in range(3))
 results.append({'frame':frame,'max_bound_error':error})
 assert error<.05,(frame,error,actual,expected)
assert not any(o.name.startswith('QA_') for o in bpy.context.scene.objects)
(root/'export_validation.json').write_text(json.dumps({'glb_import':'passed','bounds_at_open_mid_closed':results,'no_body_proxy_exported':True},indent=2))
print('GLB_ROUNDTRIP_PASSED',results)
