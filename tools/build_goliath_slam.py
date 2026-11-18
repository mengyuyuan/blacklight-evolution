"""Read local P2 curves/FIG prefixes and add only the verified biped front slam.

P2 state selection and simultaneous-Hit stacking are not decoded here.
No source mesh/textures are exported. Keep temporary work off the system disk.
"""
from pathlib import Path
import hashlib, importlib.util, json, struct, sys
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('source_motion',ROOT/'tools/export_encounter_source_motion.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
np=m.np
source=Path('D:/PrototypeReference/p2/input/p2/behemoth.p3d')
raw=source.read_bytes()
def hashed(text):
    value=0
    for c in text: value=((value*65599)^ord(c))&((1<<64)-1)
    return value
def payload(offset,kind,length):
    key,size=struct.unpack_from('<QI',raw,offset)
    expected={'Hit':447541633595,'AutoTarget':15089281274414485574}.get(kind,hashed(kind))
    assert key==expected and size==length,(offset,kind,size)
    return offset+12
def f(p,n): return struct.unpack_from('<f',raw,p+n)[0]
def h(p,n): return struct.unpack_from('<Q',raw,p+n)[0]

nodes=m.nodes(source);rig=m.skeleton(nodes,'behemoth_skeleton')
anims={m.decoder.text8(n['payload'],4)[0]:n for n in nodes if n['type']==0x121000}
animation='behemoth_com_biped_front_slam_to_biped'
clip=m.decoder.decode(anims[animation]);idle=m.decoder.decode(anims['behemoth_loco_biped_idle'])
reference=m.world(rig,idle,0)
table=json.loads((ROOT/'src/main/resources/assets/blacklight/models/encounter_goliath_source.json').read_text())
scale=table['pixelsPerSourceUnit']/16
assert table['sourceSha256']==hashlib.sha256(raw).hexdigest()
assert table['clips']['HAMMER']['source']==animation and clip['fps']==30

# Each branch's declared local duration is retained. Joining branches in this
# order is the bounded MC adapter; opaque transition predicates remain in report.
starts=[0,1.5,1.9,1.9,2.9]
offsets=[5465588,5466152,5466740,5466892,5468692]
segments=[];records=[]
for offset,start in zip(offsets,starts):
    p=payload(offset,'animation',132)
    assert h(p,16)==hashed(animation)
    begin,end,first,last=f(p,8),f(p,12),f(p,36),f(p,40)
    assert 0<=first<clip['frames'] and (last==-1 or first<=last<clip['frames'])
    resolved_last=clip['frames']-1 if last<0 else last
    assert end>begin and f(p,24)==1
    effective_speed=(resolved_last-first)/clip['fps']/(end-begin)
    segments.append(dict(at=round(start+begin,6),end=round(start+end,6),
        **{'from':first,'to':resolved_last},speed=effective_speed,sourceSpeed=f(p,24),
        blendIn=.1 if offset==offsets[0] else 0,blendOut=.2 if offset==offsets[-1] else 0,offset=offset))
    records.append(dict(offset=offset,kind='animation',begin=begin,end=end,first=first,last=last,speed=f(p,24),effectiveSpeed=effective_speed))

# Evaluate the actual animated Motion_Root, then use exactly the root-motion
# rebase already used by the shipped source skin exporter. Never assume that
# the raw joint stays at the bind-pose translation throughout the animation.
local=np.array([1.,-4.,-1.,1.]);samples=[]
for frame in range(int(clip['frames'])):
    sampled=m.world(rig,clip,frame)['Motion_Root']
    original=sampled@local
    anchored=(reference['Motion_Root']@np.linalg.inv(sampled))@original
    samples.append(dict(frame=frame,motionRoot=sampled.tolist(),original=original[:3].tolist(),anchored=anchored[:3].tolist()))
center=np.array(samples[48]['anchored'])
assert all(np.linalg.norm(np.array(s['anchored'])-center)<1e-7 for s in samples)
windows=[]
for offset in [5467068,5467492]:
    p=payload(offset,'Hit',404)
    assert h(p,28)==hashed('Motion_Root')
    localOffset=[f(p,n) for n in [44,48,52]]
    assert localOffset==list(local[:3])
    radius,damage=f(p,56),f(p,104)
    assert damage==f(p,108) and radius in [3.5,7] and damage in [750,400]
    windows.append(dict(begin=round(1.9+f(p,8),6),end=round(1.9+f(p,12),6),
        radius=radius*scale,localOffset=(center*[-1,1,1]*scale).tolist(),damage=damage,
        joint='Motion_Root',sourceRadius=radius,sourceLocalOffset=localOffset,offset=offset))
    records.append(dict(offset=offset,kind='Hit',localBegin=f(p,8),localEnd=f(p,12),joint='Motion_Root',localOffset=localOffset,radius=radius,damage=damage))
turning=[]
for offset,start,end in [(5465740,0,1.5),(5466304,1.5,1.9)]:
    p=payload(offset,'AutoTarget',96)
    turning.append(dict(begin=start+f(p,8),end=end,degreesPerSecond=f(p,16),checkDistance=False,maxDistance=f(p,44)))
    records.append(dict(offset=offset,kind='AutoTarget',localBegin=f(p,8),localEnd=f(p,12),degreesPerSecond=f(p,16)))
action=dict(source=str(source),clip='HAMMER',animation=animation,fps=30,seconds=3.9,
    segments=segments,windows=windows,range=7*scale,rangeMode='contact_preview',mode='goliath_slam',
    turning=turning,
    boundary='Decoded P2 animation/Hit prefixes; bounded sequential branch adapter. Damage is tuned for MC; strongest touched sphere only, never additive. Root travel is removed with the same Motion_Root rebase as the skin. Rebound authored skin now uses complete source joint transforms, including articulated translations. MC AI selection/cover remain; not full P2 AI. Native visual/receiving acceptance and authored hand volume fitting remain pending.')
path=ROOT/'src/main/resources/assets/blacklight/models/encounter_source_actions.json'
data=json.loads(path.read_text());data['actions']['goliath:HAMMER']=action
path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
report=dict(schema=1,entity='goliath',move='biped front slam',source=str(source),sha256=hashlib.sha256(raw).hexdigest(),
    sourceAnimation=animation,sourceFps=clip['fps'],sourceFrames=clip['frames'],blocksPerSourceUnit=scale,
    records=records,referenceMotionRoot=reference['Motion_Root'].tolist(),motionRootSamples=samples,
    decisionCondition=dict(offset=5465352,typeHash='bb26131e2ade7d5d',typeName='isDismembered',payloadHex=raw[5465364:5465380].hex(),status='Type name resolved by exact P2 runtime string hash; predicate fields/polarity remain opaque, not interpreted as range, health or phase',evidence='goliath-grounding-runtime-audit.json'),
    poseStatus='Source curves enabled on one consistently rebound authored rig; articulated source translations, rotations, SCL and native inverse-parent-scale compensation retained. Weighted source skin identifies authored giant right arm with source L arm and slender left with source R. Slender-hand static axis fitting is a mesh adaptation, not an invented source finger channel. Numerical correspondence is in goliath-bind-correction.json, not rendered/player acceptance.',
    groundEffects='goliath-ground-impact-provenance.json: nested WorldConeDamage at group5467984, three source cone-ray batches drive actual Minecraft material contact feedback, no extra damage or terrain removal.',
    pending=['Giant-hand support/volume and final source skin/ground baseline. goliath-grounding-runtime-audit.json decodes initial controllers and excludes automatic fist IK from their names alone; exact external event participation remains unproven. Unused finger chains are not weighted skin drivers','Original branch selection and transition predicates','Full Hit stacking/collision-record semantics','Other Goliath moves and leg/head phase transitions','Original WorldConeDamage decal resources and full world-damage dispatch','Original sound waveform identity','Compile/server/native-player validation'],
    nativeTiming='PositiveTimeEnd frame intervals fit the requested duration, not raw-speed playback followed by a hold. P2 runtime106829c0/10682460 proof and hashes: goliath-charge-fig-control.json. All5 source tracks have speed1 and positiveTimeEnd; derived speeds retained separately from sourceSpeed.',
    adaptations=['Source declared branch durations joined sequentially; 0.1-second entrance and 0.2-second exit blend fit current MC rig',
      'Hit localOffset uses runtime model axes (source X mirrored once), then SourceRadialWave applies render yaw. Earlier unmirrored X reflected this asymmetric sphere to the wrong side.',
      'Root travel stripped exactly as in source skin export; movement remains MC-controlled',
      'Sphere dimensions scaled to current model; strongest contacting sphere once per attack (750:400 ratio), never sum both',
      'MC block-cover ray to sphere contact; no claim this is original-engine occlusion',
      'Existing MC AI decision/recharge retained; HAMMER uses this action and CHARGE has its independent source bullrush record'])
(ROOT/'art/encounters/goliath-front-slam-provenance.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(action='goliath:HAMMER',contactTick=38,durationTicks=78,inner=windows[0]['radius'],outer=windows[1]['radius'],center=windows[0]['localOffset'],samples=len(samples))))
