"""Blender CLI: --python this.py -- /path/to/FlyingBat.glb. See license alongside."""
import bpy,sys,json,math
from pathlib import Path
source=Path(sys.argv[sys.argv.index('--')+1])
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(source))
arm=next(o for o in bpy.context.scene.objects if o.type=='ARMATURE')
action=next(a for a in bpy.data.actions if a.name=='Fly')
arm.animation_data.action=action
if action.slots:arm.animation_data.action_slot=action.slots[0]
for track in arm.animation_data.nla_tracks:track.mute=True
curves={name:[] for name in ['Arm1.L','Arm2.L','Arm3.L','Arm4.L']}
for i in range(121):
 frame=action.frame_range[0]+(action.frame_range[1]-action.frame_range[0])*i/120
 bpy.context.scene.frame_set(int(frame),subframe=frame%1)
 for name in curves:
  curves[name].append([round(math.degrees(v),6) for v in arm.pose.bones[name].matrix_basis.to_quaternion().to_euler('XYZ')])
out=Path(__file__).resolve().parents[2]/'src/main/resources/assets/blacklight/animation/sovereign_wingbeat.json'
out.write_text(json.dumps({'source':'GreyFrogGames Flying Bat v1.0 / Fly','license':'CC-BY-4.0','duration':1.25,'samples':curves},separators=(',',':')),encoding='utf8')
print('Exported',out)
