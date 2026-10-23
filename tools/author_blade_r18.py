"""Original contact-led grab/consume poses. Run after R11; preserves gameplay durations.

Blender seconds: hold -> load -> accelerate -> contact settle -> recoil -> recover.
The close contact keys deliberately occupy 60-100 ms, rather than smoothing an
entire strike over 250-350 ms. No animation or mesh is extracted from Prototype.
"""
import bpy, ast, math, json, sys, importlib.util
from pathlib import Path
from mathutils import Matrix, Vector, Quaternion

WORK,SOURCE,OUT=map(Path,sys.argv[sys.argv.index('--')+1:][:3])
OUT.mkdir(parents=True,exist_ok=True)
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources/assets/blacklight'
bpy.ops.wm.open_mainfile(filepath=str(SOURCE/'weight-authoring.blend'))
scene=bpy.context.scene
rig=bpy.data.objects['Blacklight_EpicFight']
controls={o.name:o for o in bpy.data.objects if o.name.startswith('CTRL /')}
data=json.loads((WORK/'epic-biped.json').read_text())
spec=importlib.util.spec_from_file_location('epic_vendor',WORK/'epic-blender-addon/__init__.py',submodule_search_locations=[str(WORK/'epic-blender-addon')])
vendor=importlib.util.module_from_spec(spec)
sys.modules[spec.name]=vendor
vendor.__path__=[str(WORK/'epic-blender-addon')]
from epic_vendor import compat
tree=ast.parse((ROOT/'tools/build_claw_blender.py').read_text(encoding='utf-8'))
for name in ['set_pose','clear_animation','stash_and_export','clip_text']:
    fn=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name)
    exec(compile(ast.Module(body=[fn],type_ignores=[]),'<author>','exec'))

# Blade only: the right arm owns the contact from impalement through consume.
hold=dict(lean=7,twist=5,R=(.59,-.43,1.30),L=(-.56,-.05,.98))
def pose(**kw):return hold|kw
clips={
 'grab_blade':(1.,[(0,{}),(.20,pose(lean=-4,twist=-23,height=-.065,R=(.70,.20,1.08))),
   (.30,pose(lean=20,twist=17,height=-.11,R=(.54,-.65,1.10))),
   (.38,pose(lean=20,twist=17,height=-.11,R=(.54,-.65,1.10))),
   (.65,pose(lean=-3,twist=8,height=-.06,R=(.60,-.45,1.37))),(1.,hold)]),
 'grab_blade_hold':(2.,[(0,hold),(.65,pose(lean=8)),(1.3,pose(lean=6)),(2.,hold)]),
 'grab_blade_throw':(.7,[(0,hold),(.16,pose(lean=-6,twist=-18,R=(.76,-.04,1.38))),
   (.30,pose(lean=14,twist=16,height=-.08,R=(.58,-.70,1.40))),
   (.38,pose(lean=16,twist=18,height=-.08,R=(.58,-.65,1.36))),(.50,pose(lean=12,twist=9,R=(.60,-.35,.90))),(.60,dict(R=(.50,-.20,.84))),(.7,{})]),
 'consume_blade':(2.4,[(0,hold),(.25,pose(lean=-3,twist=-7,R=(.62,-.39,1.36))),
   (.6,pose(lean=19,twist=16,height=-.08,R=(.57,-.47,1.22))),
   (.68,pose(lean=19,twist=16,height=-.08,R=(.57,-.47,1.22))),
   (1.1,pose(lean=-4,twist=-6,R=(.63,-.29,1.31))),
   (1.7,pose(lean=14,twist=9,height=-.08,R=(.59,-.25,1.22))),
   (2.15,pose(lean=9,R=(.60,-.28,1.25))),(2.4,hold)])}
for name,(duration,keys) in clips.items():
    result=stash_and_export('action/'+name,duration,keys)
    if name=='grab_blade_throw':
        # Bank the elbow guard away from the face during the forward release.
        for tr in result['animation']:
            if tr['name']!='Hand_R':continue
            for t,f in zip(tr['time'],tr['transform']):
                w=max(0,min(1,t/.16,(.7-t)/.25));w=w*w*(3-2*w)
                f['rot']=list((Quaternion(f['rot'])@Quaternion((0,1,0),-math.pi/2*w)).normalized())
        (RES/'native_animations/biped/action/grab_blade_throw.json').write_text(clip_text(result))
        (OUT/'exports/grab_blade_throw.json').write_text(clip_text(result))
rig.animation_data.action=bpy.data.actions['action/grab_blade']
for ob in controls.values():ob.animation_data.action=bpy.data.actions['action/grab_blade / '+ob.name]
scene.frame_start=0;scene.frame_end=100;scene.frame_set(65)
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'blade-authoring-r18.blend'))
