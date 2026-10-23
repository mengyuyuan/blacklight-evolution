"""Keep the IK authoring file, and make a portable rig with selectable baked Actions.
Run on claw-study.blend after build_claw_blender.py; arguments: OUTPUT.
"""
import bpy,json,sys
from pathlib import Path
out=Path(sys.argv[sys.argv.index('--')+1]);scene=bpy.context.scene
rig=bpy.data.objects['Blacklight_EpicFight']
bpy.ops.wm.save_as_mainfile(filepath=str(out/'claw-study-authoring.blend'))
rig.animation_data_clear()
for pb in rig.pose.bones:
    for constraint in list(pb.constraints):pb.constraints.remove(constraint)
for ob in list(bpy.data.objects):
    if ob.name.startswith('CTRL /'):bpy.data.objects.remove(ob,do_unlink=True)
for action in list(bpy.data.actions):bpy.data.actions.remove(action)
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/blacklight/native_animations/biped'
for path in sorted(root.rglob('*.json')):
    data=json.loads(path.read_text())
    if 'animation' not in data:continue
    rig.animation_data_clear()
    for tr in data['animation']:
        pb=rig.pose.bones[tr['name']];pb.rotation_mode='QUATERNION'
        for t,f in zip(tr['time'],tr['transform']):
            q=f['rot'];pb.rotation_quaternion=tuple(q)
            pb.location=f['loc'];pb.scale=f['sca'];frame=round(t*100)
            for prop in ['location','rotation_quaternion','scale']:pb.keyframe_insert(prop,frame=frame,group=pb.name)
    action=rig.animation_data.action;action.name=path.relative_to(root).with_suffix('').as_posix();action.use_fake_user=True
    for layer in action.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                for fc in bag.fcurves:
                    for k in fc.keyframe_points:k.interpolation='LINEAR'
rig.animation_data.action=bpy.data.actions['living/claw_idle'];scene.frame_set(0)
notes=bpy.data.texts.get('README / controls and export');notes.clear()
notes.write(f'{len(bpy.data.actions)} selectable baked Actions on Blacklight_EpicFight. Select the rig and choose an Action in the Action Editor.\n100 FPS is the authoring timebase. The runtime interpolation uses the same baked samples.\nThe separate claw-study-authoring.blend retains IK targets and Bezier source curves.\nRuntime JSON uses Blender WXYZ; loader conjugation and OpenMatrix transpose cancel.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out/'claw-study.blend'))
cam=scene.camera
from mathutils import Vector
for name,loc in [('front',(0,5,1.15)),('side',(5,0,1.15)),('three-quarter',(3,5,2.4))]:
    cam.location=loc;cam.rotation_euler=(Vector((0,0,.94))-cam.location).to_track_quat('-Z','Y').to_euler()
    scene.render.filepath=str(out/f'model-{name}.png');bpy.ops.render.render(write_still=True)
print('PLAYBACK_ACTIONS',len(bpy.data.actions),flush=True)
