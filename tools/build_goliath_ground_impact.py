"""Decode Goliath's nested front-slam WorldConeDamage tracks, statically only.

The P2 action casts random rays and forwards real collision contacts. This
adapter preserves ray geometry/timing; Minecraft contact presentation replaces
the original decal/effect resources. It introduces no damage or block removal.
"""
from pathlib import Path
import hashlib, importlib.util, json, math, re, struct, sys
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1]
import export_encounter_source_motion as motion
import goliath_source_pose as source_pose
spec=importlib.util.spec_from_file_location('grounding',ROOT/'tools/read_goliath_grounding.py')
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
raw=g.SOURCE.read_bytes();runtime=g.RUNTIME.read_bytes()
assert hashlib.sha256(raw).hexdigest()==g.SOURCE_SHA
assert hashlib.sha256(runtime).hexdigest()==g.RUNTIME_SHA
table=json.loads((ROOT/'src/main/resources/assets/blacklight/models/encounter_goliath_source.json').read_text())
action=json.loads((ROOT/'src/main/resources/assets/blacklight/models/encounter_source_actions.json').read_text())['actions']['goliath:HAMMER']
assert action['segments'][2]['at']==1.9 and action['segments'][2]['from']==48
words={g.hash64(word):word.decode() for word in re.findall(rb'[A-Za-z_][A-Za-z0-9_:]{2,180}',runtime)}
def field(data,p,fmt):return struct.unpack_from('<'+fmt,data,p)
group=5467984;key,length=field(raw,group,'QI')
assert key==g.hash64(b'trackGroup') and length==552
# Nested chunks are inside TrackGroup's payload, not top-level FIG records.
offsets=[5468052,5468184,5468316];pulses=[];evidence=[]
for offset in offsets:
    key,length=field(raw,offset,'QI');assert key==g.hash64(b'worldConeDamage') and length==112
    p=offset+12
    begin=field(raw,p+8,'f')[0]
    row=dict(atTick=math.ceil((1.9+begin)*20-.001),position=list(field(raw,p+12,'3f')),
             direction=list(field(raw,p+24,'3f')),up=list(field(raw,p+36,'3f')),
             minAngle=field(raw,p+48,'f')[0],maxAngle=field(raw,p+52,'f')[0],
             startDistance=field(raw,p+56,'f')[0],length=field(raw,p+60,'f')[0],
             minDecals=field(raw,p+64,'I')[0],maxDecals=field(raw,p+68,'I')[0],
             minDecalScale=field(raw,p+72,'f')[0],maxDecalScale=field(raw,p+76,'f')[0])
    assert row['position']==[2.5,0.,-13.] and row['direction']==[0.,-1.,0.] and row['up']==[0.,1.,0.]
    assert row['minDecals']==5 and row['maxDecals']==10
    pulses.append(row)
    references={}
    for name,at in [('damageType',80),('attackType',88),('hitType',96),('effectType',104)]:
        value=field(raw,p+at,'Q')[0];references[name]=dict(hash=f'{value:016x}',name=words.get(value))
    evidence.append(dict(offset=offset,payloadBytes=length,payloadSha256=hashlib.sha256(raw[p:p+length]).hexdigest(),localBegin=begin,fields=row,references=references))
assert [p['atTick'] for p in pulses]==[39,40,41]
assert [(p['minAngle'],p['maxAngle'],p['startDistance'],p['length'],p['minDecalScale'],p['maxDecalScale']) for p in pulses]==[(5.,15.,0.,17.,6.,6.),(15.,25.,0.,17.,8.,8.),(25.,35.,5.,20.,10.,10.)]
data=dict(schema=1,source=str(g.SOURCE),sourceSha256=g.SOURCE_SHA,move='HAMMER',
          blocksPerSourceUnit=table['pixelsPerSourceUnit']/16,pulses=pulses,
          origin='Reference Motion_Root in the same source pose as the authored skin, plus source actor-local position. Source X is mirrored once before render yaw.',
          adaptation='Original ray geometry/timing; real Minecraft collision contacts drive existing material/debris/audio feedback. No original decal texture, extra damage, block removal, or claimed actor solver reproduction.')
resource=ROOT/'src/main/resources/assets/blacklight/models/goliath_ground_impact.json'
resource.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
nodes=motion.nodes(g.SOURCE);rig=motion.skeleton(nodes,'behemoth_skeleton')
animations={motion.decoder.text8(n['payload'],4)[0]:motion.decoder.decode(n) for n in nodes if n['type']==0x121000}
reference=source_pose.world(motion,rig,animations['behemoth_loco_biped_idle'],0)
contact=source_pose.world(motion,rig,animations['behemoth_com_biped_front_slam_to_biped'],48)
root_y=float(reference['Motion_Root'][1,3]);assert root_y==6
expected_center=(reference['Motion_Root']@motion.np.array([2.5,0,-13,1]))[:3]*[1,1,-1]*data['blocksPerSourceUnit']
report=dict(schema=1,source=data['source'],sourceSha256=g.SOURCE_SHA,runtimeSha256=g.RUNTIME_SHA,
    groupOffset=group,groupConditions=['Onscreen'],records=evidence,
    nativeEvidence=g.pe_ranges(runtime,[
        ('WorldConeDamage property table',0x1042cc50,0x1042cfb2,'timeBegin,position,direction,up,minAngle,maxAngle,startDistance,length,minDecals,maxDecals,minDecalScale,maxDecalScale,damageType,attackType,hitType,effectType'),
        ('Position setter',0x100bbf40,0x100bbf5a,'position -> runtime +0x18'),
        ('Direction setter',0x1028afb0,0x1028afca,'direction -> runtime +0xc'),
        ('Up setter',0x10285e80,0x10285e9a,'up -> runtime +0x24'),
        ('Minimum angle setter',0x104b9130,0x104b9148,'Degrees converted to radians, stored +0x4c'),
        ('Maximum angle setter',0x104a10a0,0x104a10b8,'Degrees converted to radians, stored +0x44'),
        ('WorldConeDamage instance',0x103decf0,0x103ded35,'Stores actor handle and track; vtable 0x10be0d48'),
        ('Inclusive random count',0x102bcaa0,0x102bcaf5,'Modulo max-min+1 then adds min; decal count includes both 5 and 10'),
        ('WorldConeDamage execution',0x100f7200,0x100f7708,'Executes once for action state 0; random decal count, uniformly sampled polar angle, phase plus evenly spaced azimuth and jitter +/-0.2 radians. startDistance shifts along sampled ray; length extends from that shifted origin.'),
        ('World collision contact',0x104c4c80,0x104c4f40,'Raycasts against world, calculates first contact and dispatches world damage/effect request 0x10164fd0 only on collision')]),
    implementation=dict(resource=str(resource),entryPoint='GoliathGroundImpact.tick(EncounterMob,int age)',
        schedule='Quantized to 20 Hz at ticks 39,40,41, once per source event per attack. No replay after its source tick.',
        sampling='5..10 rays; native angle/length/start-distance values. Isotropic azimuth basis handles the original parallel direction/up vectors; random generator identity is an MC adapter.',
        presentation='One aggregate feedback packet per pulse containing only actual upward block-face ray contacts; source decal scale sizes the feedback flash in MC units, not a hurt radius. Existing material audio/debris replace source world decals. Wall hits stop rays; they are not projected through to ground.',
        bounds='Before raycast, every crossed XZ chunk and the entire segment bounds including 0.001 voxel-traversal margin must be loaded and within world border. Only actual nonfluid MODEL surfaces are used.'),
    geometricChecks=dict(expectedModelAnchoredConeCenterWorldYaw0=expected_center.tolist(),
        flatGroundContactDistanceFromConeAxis=[dict(pulse=i,min=expected_center[1]*math.tan(math.radians(p['minAngle'])),max=expected_center[1]*math.tan(math.radians(p['maxAngle']))) for i,p in enumerate(pulses)]),
    groundingEvidence=dict(referenceMotionRootY=root_y,sourceGroundRestingHeight=6,frontSlam48SourceJointY={name:float(contact[name][1,3]) for name in ['Ball_L','Ball_R','Ankle_L','Ankle_R','Wrist_L']},
        finding='A uniform actor/mesh lift would also lift the already low feet. CharacterSolver updates actor/support/collision state, not a dedicated giant-fist branch. Final post-fixup inputs and actor-to-ground transform remain unresolved; no wrist/mesh correction follows from this effect.'),
    boundaries=['Source Hit damage windows remain separate; WorldConeDamage does not justify an added MC damage pulse here.',
        'Original Onscreen culling is replaced by the existing nearby-client presentation path.',
        'Complete original damage/decal/material request dispatch and external actor solver are not reproduced.',
        'Giant authored fist support/volume gap and complete original Goliath phases/attacks remain open.'],
    validation='Original binary/hash checks and static decoding only. No Java compilation/tests, GameTest, client or DLL execution.')
(ROOT/'art/encounters/goliath-ground-impact-provenance.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(pulses=len(pulses),ticks=[p['atTick'] for p in pulses],resource=str(resource))))
