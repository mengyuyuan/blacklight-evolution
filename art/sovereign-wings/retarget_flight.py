"""Retarget the official Rokoko free superhero FBX to the native Blacklight bind.

Run in Blender: --background --python retarget_flight.py -- <source-directory> <output-directory>
Source is kept outside the repository; see FLIGHT_SOURCES.md for provenance.
"""
import bpy, json, math, sys
from pathlib import Path
from mathutils import Matrix, Quaternion, Vector, Euler

ROOT=Path(__file__).resolve().parents[2]
args=sys.argv[sys.argv.index('--')+1:]
SOURCE,OUT=map(Path,args[:2]);OUT.mkdir(parents=True,exist_ok=True)
bind=json.loads((ROOT/'src/main/resources/assets/blacklight/animation/player_bind.json').read_text())
local={};world={};parents={}
def load(j,parent=None):
    n=j['name'];m=Matrix([j['transform'][i:i+4] for i in range(0,16,4)])
    if parent is None:m=Matrix.Rotation(-math.pi/2,4,'X')@m
    local[n]=m;parents[n]=parent;world[n]=world[parent]@m if parent else m
    for c in j['children']:load(c,n)
load(bind['hierarchy'][0])
MAP={'Root':'Hips','Torso':'Spine1','Chest':'Spine2','Head':'Head',
     'Arm_L':'LeftArm','Arm_R':'RightArm','Hand_L':'LeftForeArm','Hand_R':'RightForeArm',
     'Thigh_L':'LeftUpLeg','Thigh_R':'RightUpLeg','Leg_L':'LeftLeg','Leg_R':'RightLeg'}
C=Matrix(((-1,0,0),(0,0,1),(0,1,0)))
cal={n:m.to_3x3() for n,m in world.items()}
# T-pose calibration: our bind has arms down, while the source FBX is T-posed.
for side,angle in [('L',90),('R',-90)]:
    delta=Matrix.Rotation(math.radians(angle),3,'Z')
    cal['Arm_'+side]=world['Arm_'+side].to_3x3()@delta
    cal['Hand_'+side]=cal['Arm_'+side]@local['Hand_'+side].to_3x3()

def retarget(arm,frame,ground=False):
    bpy.context.scene.frame_set(int(frame),subframe=frame-int(frame))
    src={n:(arm.matrix_world@arm.pose.bones['mixamorig:'+s].matrix).to_3x3().normalized() for n,s in MAP.items()}
    rest={n:(arm.matrix_world@arm.data.bones['mixamorig:'+s].matrix_local).to_3x3().normalized() for n,s in MAP.items()}
    rootq=(C@src['Root']@rest['Root'].inverted()@C.inverted()).to_quaternion()
    # In-place animation: actor turning is owned by the game, not by the capture.
    twist=Quaternion((rootq.w,0,rootq.y,0));twist.normalize();unwind=twist.inverted().to_matrix()
    desired={n:unwind@C@src[n]@rest[n].inverted()@C.inverted()@cal[n] for n in MAP}
    result={};posed={}
    for n,m in local.items():
        parent=parents[n];pg=posed[parent] if parent else Matrix.Identity(4)
        q=(m.to_3x3().inverted()@pg.to_3x3().inverted()@desired[n]).to_quaternion() if n in MAP else Quaternion()
        delta=q.to_matrix().to_4x4();posed[n]=pg@m@delta
        if n in MAP:result[n]={'q':[q.x,q.y,q.z,q.w],'p':[0,0,0]}
    if ground:
        feet=[posed['Leg_'+s]@Vector((0,.375,0)) for s in ['L','R']]
        result['Root']['p'][1]=-min(v.y for v in feet)
    return result

def close_loop(frames,tail=12):
    # Blend the end into the start without a pose jump at repeat.
    tail=min(tail,len(frames)//3)
    for i in range(tail+1):
        w=i/tail;w=w*w*(3-2*w);f=frames[-tail-1+i]
        for n,t in f.items():
            q=Quaternion((t['q'][3],*t['q'][:3]));end=frames[0][n]
            r=Quaternion((end['q'][3],*end['q'][:3]));q=q.slerp(r,w)
            t['q']=[q.x,q.y,q.z,q.w];t['p']=[a+(b-a)*w for a,b in zip(t['p'],end['p'])]
    frames[-1]=json.loads(json.dumps(frames[0]))

def filter_capture(frames):
    # Remove capture spikes before time-compressed gameplay playback. Quaternion
    # filtering avoids Euler wrapping and keeps the exported curve deterministic.
    for _ in range(2):
        old=json.loads(json.dumps(frames))
        for i,f in enumerate(frames):
            for n,t in f.items():
                q=None;total=0;p=Vector()
                for off,weight in [(-2,1),(-1,2),(0,3),(1,2),(2,1)]:
                    k=max(0,min(len(old)-1,i+off));b=old[k][n]
                    r=Quaternion((b['q'][3],*b['q'][:3]));total+=weight
                    q=r if q is None else q.slerp(r,weight/total);p+=Vector(b['p'])*weight
                t['q']=[q.x,q.y,q.z,q.w];t['p']=list(p/total)

def ground_contact(pose):
    posed={}
    for n,m in local.items():
        t=pose.get(n);d=Matrix.Identity(4)
        if t:
            d=Quaternion((t['q'][3],*t['q'][:3])).to_matrix().to_4x4()
        posed[n]=(posed[parents[n]] if parents[n] else Matrix.Identity(4))@m@d
    pose['Root']['p'][1]=-min((posed['Leg_'+s]@Vector((0,.375,0))).y for s in ['L','R'])

# Source frame ranges use a 24 fps Blender import clock (FBX time is preserved).
specs={
 'takeoff':('SuperHeroLanding_Takeoff_mixamo',370,425,.9,False,True),
 'landing':('SuperHeroLanding_Takeoff_mixamo',72,165,.8,False,True),
 'hover':('SuperHeroLanding_Takeoff_mixamo',210,275,2.4,True,False),
 'cruise':('SuperHeroFlying_mixamo',198,278,3.0,True,False),
 'boost':('SuperHeroFlying_mixamo',290,352,2.2,True,False),
 'brake':('SuperHeroFlying_mixamo',742,780,.65,False,False),
}
output={'format':'BLACKLIGHT_LOCAL_QUAT_V1','fps':30,'clips':{}}
debug={}
for source in sorted({x[0] for x in specs.values()}):
    bpy.ops.wm.read_factory_settings(use_empty=True);bpy.context.scene.render.fps=24
    bpy.ops.import_scene.fbx(filepath=str(SOURCE/(source+'.fbx')))
    arm=next(o for o in bpy.data.objects if o.type=='ARMATURE')
    for name,(src,start,end,duration,loop,ground) in specs.items():
        if src!=source:continue
        count=round(duration*30)+1
        frames=[retarget(arm,start+(end-start)*i/(count-1),ground) for i in range(count)]
        # Keep the captured body/leg motion, adapt Superman arms to Blacklight.
        if name in ('cruise','boost','hover'):
            angles={'Arm_R':(-22,0,-13),'Arm_L':(-26,0,16),'Hand_R':(24,0,0),'Hand_L':(30,0,0)}
            if name=='hover':angles.update({'Head':(4,0,0),'Arm_R':(-8,0,-16),'Arm_L':(-12,0,18),'Thigh_R':(5,0,-3),'Thigh_L':(10,0,3),'Leg_R':(-16,0,0),'Leg_L':(-27,0,0)})
            for f in frames:
                for n,angle in angles.items():
                    t=f[n];q=Quaternion((t['q'][3],*t['q'][:3]));target=Euler(tuple(math.radians(v) for v in angle),'XYZ').to_quaternion()
                    q=q.slerp(target,.85);t['q']=[q.x,q.y,q.z,q.w]
        filter_capture(frames)
        if ground:
            for f in frames:ground_contact(f)
        if loop:close_loop(frames)
        output['clips'][name]={'duration':duration,'loop':loop,'source':source,'source_frames':[start,end],'frames':frames}
    debug[source]=[{'frame':f,'pose':retarget(arm,f)} for f in range(10,int(arm.animation_data.action.frame_range[1]),12)]
(OUT/'sovereign_flight.json').write_text(json.dumps(output,separators=(',',':')))
(OUT/'retarget_inspection.json').write_text(json.dumps({'bind':{n:list(map(list,m)) for n,m in local.items()},'parents':parents,'sources':debug},separators=(',',':')))
print('Exported', {k:len(v['frames']) for k,v in output['clips'].items()})
