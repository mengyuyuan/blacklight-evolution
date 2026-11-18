"""Static P2 bullrush FIG/curve decoder; no native code or game execution.

The legacy action table remains a nominal single-cycle clock. The phase program
and full FIG control audit drive the live controller, including the normal exit.
"""
from pathlib import Path
import hashlib, json, math, struct, sys
sys.dont_write_bytecode = True
import export_encounter_source_motion as motion
import goliath_source_pose as pose
import read_goliath_grounding as grounding
import read_goliath_charge_fig as control
import build_goliath_taunt as taunt

ROOT = Path(__file__).resolve().parents[1]
raw = grounding.SOURCE.read_bytes()
runtime = grounding.RUNTIME.read_bytes()
assert hashlib.sha256(raw).hexdigest() == grounding.SOURCE_SHA
assert hashlib.sha256(runtime).hexdigest() == grounding.RUNTIME_SHA
table = json.loads((ROOT/'src/main/resources/assets/blacklight/models/encounter_goliath_source.json').read_text())
assert table['sourceSha256'] == grounding.SOURCE_SHA
scale = table['pixelsPerSourceUnit']/16
animation = 'behemoth_com_biped_bullrush'
nodes = motion.nodes(grounding.SOURCE)
rig = motion.skeleton(nodes, 'behemoth_skeleton')
clips = {motion.decoder.text8(n['payload'], 4)[0]: motion.decoder.decode(n) for n in nodes if n['type'] == 0x121000}
clip = clips[animation]
assert clip['fps'] == 30 and clip['frames'] == 151 and table['clips']['CHARGE']['source'] == animation
records = []

def read(offset, kind, size):
    key, length = struct.unpack_from('<QI', raw, offset)
    expected = {'Hit':447541633595, 'AutoTarget':15089281274414485574}.get(kind, grounding.hash64(kind.encode()))
    assert key == expected and length == size, (offset, kind, length)
    p = offset+12
    records.append(dict(offset=offset, kind=kind, payloadBytes=size,
                        payloadSha256=hashlib.sha256(raw[p:p+size]).hexdigest()))
    return p

def value(p, offset, fmt='f'):
    result = struct.unpack_from('<'+fmt, raw, p+offset)
    return result[0] if len(result) == 1 else list(result)

segments, clock = [], 0.
for offset in [5422816, 5423876, 5424300, 5426040, 5427252, 5428304]:
    p = read(offset, 'animation', 132)
    assert value(p, 16, 'Q') == grounding.hash64(animation.encode())
    begin, end, speed, first, last = [value(p, i) for i in [8, 12, 24, 36, 40]]
    assert begin == 0 and speed > 0 and 0 <= first <= last < clip['frames']
    root_flags = [value(p, i, 'I') for i in [80, 84, 88, 92]]
    assert root_flags == [1, 1, 1, 1]
    duration = (end-begin)/abs(speed) if end >= 0 else (last-first)/clip['fps']/speed
    effective_speed=(last-first)/clip['fps']/duration if end>=0 else speed
    segments.append(dict(at=clock, end=clock+duration, **{'from':first, 'to':last}, speed=effective_speed,sourceSpeed=speed,
                         blendIn=value(p, 116), blendOut=value(p, 120), offset=offset))
    records[-1].update(timeBegin=begin, timeEnd=end, first=first, last=last, speed=speed,effectiveSpeed=effective_speed,
                       hasRootTranslation=True, hasRootRotation=True,
                       blendOutRootTranslation=True, blendOutRootRotation=True)
    clock += duration

start_index = next(i for i, row in enumerate(rig) if row[0] == 'Elbow_L')
end_index = next(i for i, row in enumerate(rig) if row[0] == 'Wrist_L')
assert (start_index, end_index) == (34, 37)
chain = [row[0] for row in rig[start_index:end_index+1]]
windows = []
for offset, size, segment, radius in [(5425008, 444, segments[3], 4.), (5427404, 404, segments[4], 3.)]:
    p = read(offset, 'Hit', size)
    assert value(p, 8) == 0 and value(p, 12) == -1
    assert value(p, 16, 'I') == 0 and value(p, 20, 'i') == -1 and value(p, 24) == -1
    assert value(p, 28, 'Q') == grounding.hash64(b'Elbow_L')
    assert value(p, 36, 'Q') == grounding.hash64(b'Wrist_L')
    assert value(p, 44, '3f') == [0, 0, 0] and value(p, 56) == radius
    assert value(p, 104) == value(p, 108) == 750
    windows.append(dict(begin=segment['at'], end=segment['end'], radius=radius*scale,
                        sourceRadius=radius, joint='Elbow_L', jointEnd='Wrist_L',
                        localOffset=[0, 0, 0], damage=750, offset=offset))
    records[-1].update(joint='Elbow_L', jointEnd='Wrist_L', sourceJointIndices=list(range(start_index, end_index+1)),
                       useShapecast=False, collisionCountMax=-1, timeBetweenHits=-1,
                       radius=radius, damageMin=750, damageMax=750)
turning = []
for offset, segment in [(5423060, segments[0]), (5424892, segments[3])]:
    p = read(offset, 'AutoTarget', 96)
    assert [value(p,i) for i in [8,12]]==[0,-1]
    assert [value(p,i,'I') for i in [20,24,48,52]]==[0,0,0,0]
    turning.append(dict(begin=segment['at'], end=segment['end'], degreesPerSecond=value(p, 16),
                        checkDistance=False, maxDistance=value(p, 44)))
    records[-1].update(degreesPerSecond=value(p,16),distance=value(p,44),timeBegin=0,timeEnd=-1,
                       checkArc=False,checkDistance=False,useInput=False,useCamera=False,
                       boundary='Source angular rate; current Minecraft target selection/distance policy retained.')

positions, samples, max_gap = [], [], 0.
reference = pose.world(motion, rig, clips['behemoth_loco_biped_idle'], 0)['Motion_Root']
for frame in range(int(clip['frames'])):
    world = pose.world(motion, rig, clip, frame)
    root = world['Motion_Root']
    assert motion.np.max(motion.np.abs(root[:3, :3]-motion.np.eye(3))) < 1e-6
    assert abs(root[1, 3]-6) < 1e-6  # MC gravity remains responsible for terrain support.
    positions.append((root[:3, 3]*[1, 1, -1]*scale).tolist())
    for a, b in zip(chain, chain[1:]):
        max_gap = max(max_gap, float(motion.np.linalg.norm(world[a][:3, 3]-world[b][:3, 3])))
    if frame in [85, 90, 100, 110, 115]:
        anchored = reference @ motion.np.linalg.inv(root)
        radius = (4 if frame <= 102 else 3)*scale
        points = [dict(joint=name, worldYaw0=((anchored@world[name])[:3, 3]*[1, 1, -1]*scale+[0, .01, 0]).tolist()) for name in chain]
        # Independent raw-world fixture, not Java/exported pose data.
        for a, b in zip(points[:4], points[1:4]):
            delta = motion.np.array(b['worldYaw0'])-a['worldYaw0']; distance = float(motion.np.linalg.norm(delta))
            count = max(0, math.ceil((distance-2*radius)/(2*radius)))
            if count:
                gap = (distance-2*radius)/count
                for j in range(count):
                    points.append(dict(joint=f"{a['joint']}->{b['joint']}:{j}",
                        worldYaw0=(a['worldYaw0']+delta/distance*(radius+gap*(j+.5))).tolist()))
        segment = segments[3 if frame <= 102 else 4]
        age = (segment['at']+(frame-segment['from'])/30/segment['speed'])*20
        samples.append(dict(frame=frame, age=age, radius=radius, points=points))
# The inclusive index range also includes Chest_L_End; it is not a hierarchy
# walk. Exit poses really exceed 2*r and require native intermediate spheres.
assert chain == ['Elbow_L', 'Chest_L_End', 'Forarm_L', 'Wrist_L'] and 8 < max_gap < 10
# Static data correspondence: reconstruct exported TRS matrices independently
# of the Java runtime and compare native joint origins before shipping data.
export_error = 0.
for sample in samples:
    matrices = []
    for i, row in enumerate(table['clips']['CHARGE']['frames'][sample['frame']]):
        pivot = motion.np.asarray(table['pivots'][i]); x, y, z = row[:3]
        cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
        rotation = motion.np.array([[cz*cy, cz*sy*sx-sz*cx, cz*sy*cx+sz*sx], [sz*cy, sz*sy*sx+cz*cx, sz*sy*cx-cz*sx], [-sy, cy*sx, cy*cx]])
        local = motion.np.eye(4); local[:3, :3] = rotation @ motion.np.diag(row[6:9])
        local[:3, 3] = (pivot+row[3:6])/16-local[:3, :3]@(pivot/16)
        parent = table['parents'][i]; matrices.append(local if parent < 0 else matrices[parent]@local)
    for point in sample['points'][:4]:
        actual = matrices[table['bones'].index('source_'+point['joint'])][:3, 3]*[-1, 1, -1]+[0, .01, 0]
        export_error = max(export_error, float(motion.np.linalg.norm(actual-point['worldYaw0'])))
assert export_error < .0002
action = dict(source=str(grounding.SOURCE), clip='CHARGE', animation=animation, fps=30,
              seconds=clock, segments=segments, windows=windows, turning=turning,
              mode='goliath_charge', range=12, rangeMode='adapted',
              boundary='Legacy nominal one-cycle/special-return clock retained for independent source fixtures and lookup compatibility only. Live GoliathCharge.State uses decoded native loop/exit predicates and both return clips. AI selection, gravity/collision and per-target damage tuning remain Minecraft adapters. Native Hit is a joint-index sphere chain, not whole-body AABB or capsule; root travel is applied once by the actor.')
resource = dict(schema=1, sourceSha256=grounding.SOURCE_SHA, animation=animation,
                blocksPerSourceUnit=scale, chain=chain, rootPositions=positions,
                nativeSphereSpacingMultiplier=2, maxAdjacentSourceJointDistance=max_gap,
                axis='Source X, Y, -Z -> Minecraft yaw 0; actor yaw rotates by -yaw. Source skin independently mirrors X before renderer 180-yaw, yielding the same world axes.',
                clock='SourceBossAction goliath:CHARGE; original root TRS has identity rotation and constant Y=6 for all frames. Apply successive differences, not absolute Y or accumulated absolute position.')
control_report=control.decode()
(ROOT/'art/encounters/goliath-charge-fig-control.json').write_text(json.dumps(control_report,indent=2)+'\n',encoding='utf-8')
return_name='behemoth_bullrush_to_aggro';return_clip=clips[return_name]
assert table['clips']['CHARGE_RETURN']['source']==return_name and return_clip['fps']==30
p=read(5428708,'animation',132)
assert value(p,16,'Q')==grounding.hash64(return_name.encode())
assert [value(p,i) for i in [8,12,24,36,40]]==[0,-1,1,117,125]
return_positions=[]
for frame in range(int(return_clip['frames'])):
    root=pose.world(motion,rig,return_clip,frame)['Motion_Root']
    assert motion.np.max(motion.np.abs(root[:3,:3]-motion.np.eye(3)))<1e-6 and abs(root[1,3]-6)<1e-6
    return_positions.append((root[:3,3]*[1,1,-1]*scale).tolist())
return_turn=read(5428860,'AutoTarget',96)
assert [value(return_turn,i) for i in [8,12]]==[0,-1]
assert [value(return_turn,i,'I') for i in [20,24,48,52]]==[0,0,0,0]
records[-1].update(degreesPerSecond=value(return_turn,16),distance=value(return_turn,44),
    timeBegin=0,timeEnd=-1,checkArc=False,checkDistance=False,useInput=False,useCamera=False)
phases=[]
for i,s in enumerate(segments):
    phases.append(dict(id=i,name=['windup','release','enter','cycle','exit','specialReturn'][i],
        clip='CHARGE',**{'from':s['from'],'to':s['to']},speed=s['speed'],seconds=s['end']-s['at'],
        blendIn=s['blendIn'],blendOut=s['blendOut'],offset=s['offset'],
        turn=120 if i==0 else 45 if i==3 else 0,
        radius=(4 if i==3 else 3 if i==4 else 0)*scale))
phases.append(dict(id=6,name='toAggro',clip='CHARGE_RETURN',**{'from':117,'to':125},speed=1,
    seconds=8/30,blendIn=value(p,116),blendOut=value(p,120),offset=5428708,
    turn=value(return_turn,16),radius=0))
resource.update(schema=2,phases=phases,rootCurves={'CHARGE':positions,'CHARGE_RETURN':return_positions},
    cycleSecondsLimit=2.5,targetArcDegrees=120,controlAudit='goliath-charge-fig-control.json',
    clock='GoliathCharge.State phase+phaseStartAge. Each phase rounds upward to the 20Hz boundary; advance applies only its root delta, then afterContact evaluates native onEnd predicates. No rewind displacement at cycle restart. Source skin and hit use the same snapshot.',
    boundary='Normal exit uses toAggro117..125, special exit117..150 only for native script inputs. Post-normal branch58 taunt chain, native root-delta blending and final support remain unported. No synthetic idle blend is substituted for the missing taunt successor.')
taunt_report=taunt.extend(resource,motion,pose,rig,clips,table,scale,control_report)
path = ROOT/'src/main/resources/assets/blacklight/models/encounter_source_actions.json'
data = json.loads(path.read_text()); data['actions']['goliath:CHARGE'] = action
path.write_text(json.dumps(data, indent=2)+'\n', encoding='utf-8')
(ROOT/'src/main/resources/assets/blacklight/models/goliath_charge.json').write_text(json.dumps(resource, indent=2)+'\n', encoding='utf-8')
(ROOT/'src/test/resources/goliath-charge-reference.json').write_text(json.dumps(dict(sourceSha256=grounding.SOURCE_SHA, samples=samples), indent=2)+'\n', encoding='utf-8')
report = dict(schema=1, source=str(grounding.SOURCE), sourceSha256=grounding.SOURCE_SHA,
    runtimeSha256=grounding.RUNTIME_SHA, records=records, segments=segments,
    runtimeEvidence=grounding.pe_ranges(runtime, [
        ('P2 AnimationTrack metadata', 0x10271610, 0x10271b56, 'Ordered fields include root translation/rotation booleans and blendIn/Out times; not inferred from the P1 layout'),
        ('Root translation flag setter', 0x100706c0, 0x100706d8, 'Sets property +0x6c bit 0x200'),
        ('Root rotation flag setter', 0x1010ed40, 0x1010ed58, 'Sets property +0x6c bit 0x100'),
        ('CollisionHit metadata', 0x10384c90, 0x10385874, 'joint, jointEnd, offset, radius, damageMin and damageMax prefix'),
        ('CollisionHit sphere chain', 0x102c4550, 0x102c4e86, 'Inclusive joint indices start..end, not a hierarchy walk; pose joint origins transformed by actor matrix. Sphere spacing threshold is radius*2 at 0x10c56624; additional spheres n=ceil((distance-2r)/(2r)), step=(distance-2r)/n, first distance r+step/2. Original gap directions are normalized by 0x10847530.'),
        ('Normalize direction and return original length', 0x10847530, 0x108475b4, 'Normalizes the input vector in place and returns its length; sphere insertion offsets are distances, not lerp fractions.')]),
    implementation=dict(legacyNominalDurationTicks=math.ceil(clock*20),legacyNominalContactTick=math.ceil(windows[0]['begin']*20-.001),
        legacyNominalLastDamageTick=math.floor(windows[-1]['end']*20+.001),chain=chain,
        livePhaseDurationsTicks=[math.ceil(p['seconds']*20-1e-5) for p in phases],
        liveNormalDurationTicks=taunt_report['normalChargeTicks'],liveMaximumOrdinaryDurationTicks=taunt_report['normalChargeTicks']+40,
        liveSpecialReturnDurationTicks=98,
        liveContact='After enter closes at age56, cycle samples run ages57..66; exit samples67..76 for one-cycle normal play. Closing samples precede afterContact/Sequence. Each extra quantized cycle adds10 ticks.',
        skippedTicks='A gap in attackAge advances at most to the current phase end. No cross-phase catchup; the next phase starts at the observed current age after contact. The attack cannot teleport through multiple unobserved loops.',
        lifecycle='begin and cancel reset per-actor state; afterContact evaluates onEnd; snapshot phase/start drives server receiving skin, attacking spheres and client pose. Live completion bypasses legacy96 ticks; root schedules recharge from actual completion.',
        maxAdjacentSourceJointDistance=max_gap, rootTravelWorldYaw0=(motion.np.array(positions[150])-positions[5]).tolist(),
        staticExportedJointMatrixErrorBlocks=export_error,
        sourceContactOnlyHelper='Chest_L_End is unweighted and was previously trimmed. Its original local TRS and reciprocal-parent-scale helper are now retained; no cube, wrist curve, scale or support lift is changed.',
        contact='Current articulated pose sphere contacts against actual target surfaces. Source useShapecast is false; no unproven continuous arm capsule or whole-body contact.'),
    phaseControl='goliath-charge-fig-control.json; live GoliathCharge.State replaces the nominal legacy action clock.',
    adaptations=['Each phase rounds up to a 20Hz tick; the current phase final contact runs before its Sequence decision. No native subframe/physics pass ordering is claimed.',
        'Original global Player becomes the committed Player; non-player targets do not borrow an unrelated player to extend a charge. Script override variables default to zero rather than invented health mappings.',
        'Native normal exit117..125 now continues through ordinary source taunt58/60/61/116/44; GoliathTaunt supplies source Health|HitReaction windows. Special exit117..150 still stops before its separate44 howl successor.',
        'Source angular rates retained; existing EncounterMove.CHARGE 12-block selection range, MC target selection, loaded/world-border/block movement constraints and gravity remain.',
        'Root curve sampled at source frames and linearly interpolated by the 20 Hz server. Native layer blending of extracted root delta is not claimed.',
        'One damage per victim per move and MC damage balance/cover rules remain. Native unlimited collision record/repeat-hit semantics need separate verification.'],
    pending=['Source taunt story/grapple/dismember reactions and original audio waveforms; see goliath-taunt-provenance.json', 'External script variable producers', 'Native root-delta layer blending/actor support',
             'Giant fist grounding and original final skin/actor support transform', 'Compilation, unit/GameTest and player validation'],
    pendingTests=dict(unit='GoliathChargeTest',gameTest='GoliathChargeGameTest',
        batches=['goliath_charge_clock','goliath_charge_contact','goliath_charge_callback','goliath_charge_recharge'],
        scenarios=['Last cycle sample hits Player then suppresses repeat','120-degree arc is +/-60; support and signedY predicates','2.5s parent timer survives repeated phases','Phase root integration never rewinds at a cycle seam','Unobserved age gap cannot replay several source phases','Normal/special source last frames125/150','Two actual recipients cancel attacker from accepted hurt callback; only first can be hurt','NPC one-cycle natural completion gets85 recharge ticks from actual finish']),
    validation='Static binary hash/field/curve geometry checks only. All Java/unit/GameTest/player cases remain unexecuted; no Java compilation, tests, client or DLL execution.')
(ROOT/'art/encounters/goliath-charge-provenance.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
followup = dict(schema=1, sourceSha256=grounding.SOURCE_SHA, runtimeSha256=grounding.RUNTIME_SHA,
    runtimeEvidence=grounding.pe_ranges(runtime, [
        ('Puppet update phases', 0x10728cd0, 0x10728ec4, 'Phase 4 advances/evaluates choreography and pose; phase 0x10 validates/consumes root delta. It does not contain a Behemoth hand-floor clamp.'),
        ('Character root delta to matrix', 0x10248a70, 0x10248a99, 'Constructs a matrix from choreography root quaternion and translation, not a fist correction.'),
        ('Owner spatial transform getter', 0x10698f20, 0x10698f27, 'Behaviour owner at +4 then actor spatial at +0x1c.'),
        ('Actor spatial transform setter', 0x10699820, 0x106999ce, 'Finite-checks and copies matrix to actor spatial, then transform-changed dispatch.'),
        ('CharacterSolver update', 0x10726180, 0x107266bb, 'Combines extracted root delta and actor spatial transform, then collision/support. Optional limb IK is a separate guarded branch.'),
        ('Support solve', 0x106cf5b0, 0x106cff76, 'Support-surface solve writes the actor output matrix; there is no per-hand joint lookup or skin offset in this path.'),
        ('WorldCone actor anchor', 0x100f7200, 0x100f7708, 'At 0x100f723a resolves actor handle; 0x100f723f reads actor+0x1c spatial transform. Track position is actor-local. No joint hash or Motion_Root lookup.')]),
    grounding=dict(finding='No new statically proven giant-fist lift or scale correction. Animation evaluation and actor support are distinct; a final actor/support state capture is still needed to explain the native skin vs terrain plane.',
        remaining=['Final native actor spatial matrix and support result on the slam contact frame',
                   'Final post-controller world/skin matrices for weighted Wrist_L and shields on the same frame'],
        unchanged='Keep source wrist curves, authored giant fist volume and receiving surfaces unchanged; giant support crossing remains open.'),
    worldConeAnchor=dict(native='Actor spatial transform * track-local position [2.5,0,-13].',
        currentAdapter='GoliathGroundImpact uses reference Motion_Root in the MC source-bound model, then that local position. This root anchor is a coordinate-baseline adapter, not a decoded WorldCone joint field.',
        unresolved='Equivalence of the native actor origin, source Motion_Root Y=6, and Minecraft entity floor origin is not established by the WorldCone track. Do not replace it with an arbitrary lift/lower constant.',
        change='No numerical anchor change in this round.'),
    actionablePivot='goliath-charge-provenance.json adds verified bullrush slices, original root travel and native joint-index sphere-chain contacts instead of repeatedly reporting the unresolved grounding gap.',
    validation='Static disassembly/field/curve analysis only. No original DLL execution, Java compilation, tests or client.')
(ROOT/'art/encounters/goliath-actor-pose-followup.json').write_text(json.dumps(followup, indent=2)+'\n', encoding='utf-8')
print(json.dumps(report['implementation']))
