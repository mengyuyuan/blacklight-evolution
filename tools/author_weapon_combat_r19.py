"""Author weapon actions with distinct sweeps, footwork and contact windows.

R20 uses the documented P1 move semantics; timings remain our Minecraft adaptation,
not measured source-game timings. See docs/PROTOTYPE_ATTACK_REFERENCE.md.
No animation or mesh is extracted from Prototype.
"""
import bpy, ast, math, json, sys, importlib.util
from pathlib import Path
from mathutils import Matrix, Vector, Quaternion

WORK,SOURCE,OUT=map(Path,sys.argv[sys.argv.index('--')+1:][:3])
selected=set(sys.argv[sys.argv.index('--')+4:])
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

idle={}
guard=dict(lean=8,R=(.61,-.16,1.0),L=(-.60,-.12,1.0))
def p(**kw):
 pose=guard|kw
 for side in ('R','L'):
  x,y,z=pose[side];pose[side]=(x+(.12 if x>0 else -.12),y,z)
 pose['twist']=max(-45,min(45,pose.get('twist',0)))
 return pose
moves={
 'blade_cut':(16,5,p(twist=-25,R=(.83,.16,1.22)),p(twist=24,lean=19,R=(.54,-.65,1.01))),
 'blade_return':(18,6,p(twist=18,R=(.44,-.55,1.00)),p(twist=-28,lean=12,R=(.86,-.28,1.03))),
 'blade_cleave':(30,14,p(twist=-15,lean=-5,R=(.92,.06,1.70)),p(twist=18,lean=30,height=-.13,R=(.56,-.68,.73))),
 'blade_rush':(24,8,p(twist=-22,lean=18,height=-.1,R=(.79,.21,1.14)),p(twist=17,lean=24,R=(.57,-.72,1.12))),
 'blade_dive':(24,8,p(lean=-9,R=(.94,.05,1.66),footR=(.20,-.08,.27),footL=(-.2,.12,.20)),p(lean=34,height=-.12,R=(.54,-.61,.73))),
 'hammer_jab':(26,7,p(twist=-19,R=(.86,.09,1.20)),p(twist=23,lean=18,R=(.58,-.65,1.16))),
 'hammer_hook':(28,9,p(twist=23,L=(-.90,.02,1.17)),p(twist=-24,lean=19,L=(-.52,-.63,1.13))),
 'hammer_quake':(36,18,p(lean=-12,height=-.1,R=(.94,.07,1.65),L=(-.94,.07,1.65)),p(lean=40,height=-.24,R=(.61,-.63,.57),L=(-.61,-.63,.57))),
 'hammer_ram':(26,9,p(lean=24,R=(.80,-.03,1.19),L=(-.75,-.10,1.04)),p(lean=31,height=-.10,R=(.61,-.59,1.13))),
 'hammer_drop':(30,12,p(lean=-12,R=(.94,.05,1.7),L=(-.94,.05,1.7),footR=(.2,-.1,.30),footL=(-.2,.12,.27)),p(lean=43,height=-.24,R=(.60,-.61,.56),L=(-.60,-.61,.56))),
 'whip_lash':(22,9,p(twist=-32,lean=-8,R=(.89,.24,1.32)),p(twist=25,lean=18,R=(.64,-.69,1.10))),
 'whip_return':(24,10,p(twist=25,R=(.60,-.61,1.1)),p(twist=-29,lean=15,R=(.92,-.22,1.13))),
 'whip_sweep':(32,13,p(twist=-28,R=(.94,.23,1.39)),p(twist=20,lean=12,R=(.80,-.55,1.03))),
 'whip_lunge':(26,10,p(twist=-24,lean=14,R=(.87,.18,1.18)),p(twist=22,lean=24,R=(.60,-.71,1.03))),
 'whip_air':(24,9,p(twist=-26,R=(.90,.08,1.46),footR=(.2,-.1,.22),footL=(-.2,.1,.16)),p(twist=23,lean=27,R=(.61,-.68,1.03))),
 'muscle_jab':(14,4,p(twist=-12,R=(.65,-.05,1.12)),p(twist=17,lean=12,R=(.43,-.66,1.19))),
 'muscle_cross':(17,6,p(twist=17,L=(-.63,-.03,1.14)),p(twist=-22,lean=17,L=(-.42,-.69,1.18))),
 'muscle_uppercut':(26,11,p(twist=-20,lean=18,height=-.15,R=(.61,.10,.78)),p(twist=20,lean=-8,R=(.52,-.57,1.51))),
 'muscle_rush':(23,8,p(twist=-22,lean=24,R=(.66,.02,1.00)),p(twist=20,lean=28,R=(.49,-.65,1.13))),
 'muscle_air':(22,8,p(twist=-15,lean=-8,R=(.74,.04,1.40),footR=(.2,-.1,.26)),p(twist=18,lean=24,R=(.47,-.65,1.06)))
}
def cut_pose(side,step=0):
 # Three points across the front of the body, below the head. The blade travels
 # through the target instead of extending straight out and reversing its path.
 return p(twist=side*18,lean=17,height=-.075,
          R=((.91,.18,1.08) if side<0 else (.66,-.60,.80)),
          L=(-.70,.16,1.01),footR=(.23,-.18-step,.015),footL=(-.24,.20-step,.015))

custom={}
for name,ticks,start in [('blade_cleave',44,10)]:
 keys=[(0,idle),((start-3)/20,cut_pose(-1))]
 for hit,sign in [(start,1),(start+10,-1),(start+20,1)]:
  t=hit/20
  keys.extend([(t-.05,cut_pose(-sign)),
    (t,p(twist=0,lean=24,height=-.09,R=(.76,-.64,.94),L=(-.72,.1,1.0),footR=(.25,-.27,.015),footL=(-.23,.20,.06))),
    (t+.10,cut_pose(sign)),(t+.20,cut_pose(sign)|{'height':-.10})])
 keys.extend([((ticks-4)/20,guard),(ticks/20,idle)])
 custom[name]=(ticks,keys)

# A slash includes overshoot and a separate settle, including the support leg.
custom['blade_cut']=(16,[(0,idle),(.12,cut_pose(-1)),
 (.25,p(twist=8,lean=20,height=-.07,R=(.76,-.64,.99),L=(-.68,.08,1.05),footR=(.24,-.25,.015))),
 (.38,cut_pose(1)),(.53,cut_pose(1)|{'lean':12}),(.70,guard),(.8,idle)])
custom['blade_return']=(18,[(0,idle),(.17,cut_pose(1)),
 (.30,p(twist=0,lean=20,height=-.07,R=(.76,-.64,.94),footL=(-.24,-.22,.015),footR=(.23,.17,.015))),
 (.43,cut_pose(-1)),(.60,cut_pose(-1)|{'lean':10}),(.78,guard),(.9,idle)])

# Elbow first: keep the striking forearm folded, tuck one knee, then compress
# onto the support leg at actual landing. Do not reuse the two-fist ground slam.
fold=p(twist=-24,lean=14,height=-.06,R=(.73,-.12,1.22),L=(-.85,.10,1.08),
       footR=(.24,.12,.43),footL=(-.26,-.12,.20))
elbow=p(twist=24,root_pitch=24,lean=32,height=-.24,R=(.62,-.17,1.10),L=(-.83,-.30,.67),
        footR=(.25,.25,.12),footL=(-.28,-.25,.015))
custom['hammer_drop']=(30,[(0,idle),(.12,fold),(.40,fold|{'root_pitch':18}),
 (.50,elbow|{'height':-.08}),(.60,elbow),(.68,elbow),(.90,elbow|{'root_pitch':8,'lean':24}),
 (1.22,p(lean=24,height=-.12,R=(.75,-.15,.81),L=(-.75,-.10,.81))),(1.5,idle)])

# The sweep is a full turn, with planted-to-pivot footwork and a trailing arm.
sweep_guard=p(lean=18,height=-.08,R=(.98,.1,1.04),L=(-.59,-.22,1.05),footR=(.27,-.14,.015),footL=(-.27,.17,.015))
custom['whip_sweep']=(40,[(0,idle),(.35,sweep_guard|{'twist':-32}),
 (.60,sweep_guard),(.90,sweep_guard|{'twist':18,'footL':(-.20,.08,.16)}),
 (1.20,sweep_guard|{'twist':30,'R':(.95,-.2,.91),'footR':(.20,-.10,.08)}),
 (1.40,p(lean=20,R=(.80,-.24,.87))), (1.72,guard),(2.0,idle)])

turns={'whip_sweep':[(0,0),(.35,-90),(.60,-140),(1.20,220),(1.60,360),(2.0,360)]}
turns['blade_cut']=[(0,0),(.12,-40),(.25,0),(.38,55),(.53,55),(.8,0)]
turns['blade_return']=[(0,0),(.17,55),(.30,0),(.43,-40),(.60,-40),(.9,0)]
for name,ticks,start in [('blade_cleave',44,10)]:
 turn_keys=[(0,0),((start-3)/20,-40)]
 for hit,sign in [(start,1),(start+10,-1),(start+20,1)]:
  turn_keys.extend([((hit-1)/20,-40 if sign==1 else 55),(hit/20,0),((hit+2)/20,55 if sign==1 else -40),((hit+4)/20,55 if sign==1 else -40)])
 turn_keys.append((ticks/20,0));turns[name]=turn_keys
def turn_at(keys,time):
 for (a,x),(b,y) in zip(keys,keys[1:]):
  if a<=time<=b:return x+(y-x)*(time-a)/(b-a)
 return keys[-1][1]

for name,(ticks,hit,load,strike) in moves.items():
    if selected and name not in selected:continue
    contact=hit/20;duration=ticks/20
    keys=[(0,idle),(max(.1,contact-.12),load),(contact,strike),(contact+.06,strike),(duration-.17,guard),(duration,idle)]
    if name in custom:
     ticks,keys=custom[name];duration=ticks/20
    result=stash_and_export('combat/'+name,duration,keys)
    if name in turns:
     for track in result['animation']:
      if track['name']=='Root':
       for time,frame in zip(track['time'],track['transform']):
        frame['rot']=list((Quaternion((0,1,0),math.radians(turn_at(turns[name],time)))@Quaternion(frame['rot'])).normalized())
     (RES/'native_animations/biped/combat'/f'{name}.json').write_text(clip_text(result),encoding='utf8')
     (OUT/'exports'/f'{name}.json').write_text(clip_text(result),encoding='utf8')
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'weapon-combat-r19.blend'))
