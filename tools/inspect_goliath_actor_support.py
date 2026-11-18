"""Read P2 actor/root/support semantics without loading the original DLL.

This is a static source-data inspector, not a game/Java test. The small support
projection below models only the decoded successful-contact branch. It must
not be used as a complete physics solver or an automatic hand-floor clamp.
"""
from pathlib import Path
import argparse
import json
import re
import struct
import sys

sys.dont_write_bytecode = True
import read_goliath_grounding as grounding

ROOT = Path(__file__).resolve().parents[1]


def support_translation(proposed, contact, direction, resting_length,
                        rotated_origin=(0., 0., 0.), restrict_normal=False):
    """106cfe38..106cff25, after query/orientation/rest smoothing succeeded.

    Direction is the limb's normalized world direction, not the plane normal.
    A Simulating body passes restrict_normal=true through 10721900. That
    removes the correction parallel to direction; it does NOT set actor Y=6.
    """
    assert abs(sum(x*x for x in direction) - 1) < 1e-6
    correction = [contact[i] - rotated_origin[i] - direction[i]*resting_length
                  - proposed[i] for i in range(3)]
    if restrict_normal:
        along = sum(correction[i]*direction[i] for i in range(3))
        correction = [correction[i] - along*direction[i] for i in range(3)]
    return [proposed[i] + correction[i] for i in range(3)]


def smooth_resting_length(current, desired, dt, speed, minimum, maximum,
                          epsilon):
    """106cd0e0: finite-step clamp, not an exponential spring or hand IK."""
    if abs(current-desired) <= epsilon:
        return current
    step = max(-speed*dt, min(speed*dt, desired-current))
    return max(minimum, min(maximum, current+step))


def pe_sections(data):
    start = struct.unpack_from('<I', data, 0x3c)[0]
    count = struct.unpack_from('<H', data, start+6)[0]
    opt = struct.unpack_from('<H', data, start+20)[0]
    base = struct.unpack_from('<I', data, start+24+28)[0]
    sections = []
    for i in range(count):
        p = start+24+opt+40*i
        size, rva, raw_size, raw_offset = struct.unpack_from('<IIII', data, p+8)
        sections.append(dict(name=data[p:p+8].rstrip(b'\0').decode(),
                             virtualSize=size, rva=rva, rawSize=raw_size,
                             fileOffset=raw_offset))
    return base, sections


def image_bytes(data, address, length):
    """PE file bytes plus loader-zero virtual tail, without loading an image.

    pefile.get_offset_from_rva alone is unsafe for .bss: its computed file
    position can point into unrelated later file data. Never hash that as a
    constant. This routine returns zeros only for the virtual unbacked tail.
    """
    base, sections = pe_sections(data)
    rva = address-base
    section = next(s for s in sections if s['rva'] <= rva and
                   rva+length <= s['rva']+s['virtualSize'])
    relative = rva-section['rva']
    backed = max(0, min(length, section['rawSize']-relative))
    return (data[section['fileOffset']+relative:
                 section['fileOffset']+relative+backed] + b'\0'*(length-backed))


def decode_factory(raw):
    # Inline SimplePhysicsObjectFactory.volumeFactory, then outer fields.
    # Actual capsule schema is centre/axis/extent/radius. The nearby pointA /
    # pointB metadata belongs to a Constraint and must not be substituted.
    reader = grounding.meta(raw, 5701838, 146)
    assert reader.field('volumeFactory.class', 'text32') == 'ravenphysics::CollisionCapsuleFactory'
    assert reader.field('volumeFactory.version', 'H') == 1
    reader.field('volumeFactory.schemaCrc', 'I')
    reader.field('material', 'text8', 4)
    reader.field('intersectionProperties', 'text8', 8)
    reader.field('centre', '3f', 0x10)
    reader.field('axis', '3f', 0)
    reader.field('extent', 'f', 0x1c)
    reader.field('radius', 'f', 0x2c)
    reader.field('collisionType', 'i', 0x14)
    reader.field('createRigidBody', 'bool32', 0x18)
    reader.field('gravityEnabled', 'bool32', 0x19)
    reader.field('massProperties', 'text8', 0x1c)
    assert reader.position == 5701996
    values = {r['name']: r['value'] for r in reader.fields}
    assert values['collisionType'] == 2 and values['createRigidBody'] is True
    assert values['gravityEnabled'] is False
    assert values['centre'] == [0., 4., -2.5] and values['axis'] == [0., 1., 0.]
    assert values['radius'] == 7. and abs(values['extent']-.1) < 1e-7
    return reader.fields


def pose_comparison():
    import export_encounter_source_motion as m
    import goliath_source_pose as pose
    np = m.np
    path, rig_name, reference_name, clips = m.PROFILES['goliath']
    nodes = m.nodes(path)
    rig = m.skeleton(nodes, rig_name)
    assert rig[0][0] == 'Motion_Root'
    animations = {m.decoder.text8(n['payload'], 4)[0]: n
                  for n in nodes if n['type'] == 0x121000}
    idle = m.decoder.decode(animations[reference_name])
    slam = m.decoder.decode(animations[clips['HAMMER']])
    reference = pose.world(m, rig, idle, 0)['Motion_Root']
    expected = np.eye(4)
    expected[1, 3] = 6
    assert np.max(np.abs(reference-expected)) < 1e-6
    samples = []
    maximum = 0.
    for frame in (0, 18, 28, 48, 60):
        sampled = pose.locals(m, rig, slam, frame)
        root_scale = np.linalg.norm(sampled[0][:3, :3], axis=0)
        assert np.max(np.abs(root_scale-1)) < 1e-6
        # Native ordinary choreography removes root Q/T before local/world
        # calculation. Rebuild independently of inverse(animatedRoot).
        local_root_zero = [x.copy() for x in sampled]
        local_root_zero[0] = np.eye(4)
        native = []
        for i, (name, parent, _) in enumerate(rig):
            local = local_root_zero[i].copy()
            if i > 0 and name != 'Middle_Base_L':
                parent_scale = np.linalg.norm(local_root_zero[parent][:3, :3], axis=0)
                local[:3, :3] = np.diag(1/parent_scale) @ local[:3, :3]
            native.append(local if i == 0 else native[parent] @ local)
        current = pose.world(m, rig, slam, frame)
        rebase = reference @ np.linalg.inv(current['Motion_Root'])
        errors = [float(np.max(np.abs(reference @ native[i] - rebase @ current[name])))
                  for i, (name, _, _) in enumerate(rig)]
        maximum = max(maximum, max(errors))
        index = next(i for i, x in enumerate(rig) if x[0] == 'Wrist_L')
        samples.append(dict(frame=frame, jointCount=len(rig), maxMatrixError=max(errors),
                            rawAnimatedRootTranslation=sampled[0][:3, 3].tolist(),
                            rootIdentityWrist=native[index][:3, 3].tolist(),
                            referenceRebasedWrist=(reference@native[index])[:3, 3].tolist()))
    return dict(method='Independent root-channel identity rebuild vs referenceRoot * inverse(animatedRoot), original SCL and segment compensation retained.',
                referenceRoot=reference.tolist(), samples=samples, maxMatrixError=maximum,
                conclusion='Equal only when native actor transform supplies the same reference-root baseline. This arithmetic proves no native actor height or final support state.')


def inspect():
    raw, runtime = grounding.SOURCE.read_bytes(), grounding.RUNTIME.read_bytes()
    assert grounding.sha(raw) == grounding.SOURCE_SHA
    assert grounding.sha(runtime) == grounding.RUNTIME_SHA
    enums = []
    for i in range(3):
        pointer, value = struct.unpack('<II', image_bytes(runtime, 0x10f6ce44+8*i, 8))
        name = image_bytes(runtime, pointer, 20).split(b'\0')[0].decode()
        enums.append(dict(value=value, name=name))
    assert enums == [dict(value=0, name='Static'), dict(value=1, name='Keyframed'), dict(value=2, name='Simulating')]
    identity = list(struct.unpack('<16f', image_bytes(runtime, 0x10ec2498, 64)))
    assert identity == [1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]
    zero = image_bytes(runtime, 0x10ff5534, 12)
    assert zero == bytes(12)
    epsilon, = struct.unpack('<f', image_bytes(runtime, 0x10c56f00, 4))
    keyframed_signature = struct.pack('<QI', grounding.hash64(b'physicsSetKeyframed'), 8)
    keyframed_offsets = [m.start() for m in re.finditer(re.escape(keyframed_signature), raw)]
    assert keyframed_offsets == [5691928]
    assert struct.unpack_from('<iI', raw, 5691940) == (-1, 1)
    death_clip_hash, = struct.unpack_from('<Q', raw, 5691992+16)
    assert death_clip_hash == grounding.hash64(b'behemoth_com_biped_death_v2')
    rows = []
    for proposed, contact in [([0., 10., 0.], [0., 0., 0.]),
                              ([2., 12., 3.], [2., 4., 3.])]:
        rows.append(dict(proposed=proposed, surfacePoint=contact,
                         keyframed=support_translation(proposed, contact, [0,-1,0], 6),
                         simulating=support_translation(proposed, contact, [0,-1,0], 6, restrict_normal=True)))
    ranges = [
        ('Ordinary Puppet pose dispatch',0x10728e75,0x10728ec4,'Choreo advance, evaluate, local/world/skin; independent of PoseAnimationBehaviour/NIS.'),
        ('Choreography evaluation finishes by resetting root',0x10681b03,0x10681b20,'Calls 10680ad0 after curve evaluation.'),
        ('Ordinary root Q/T reset',0x10680ad0,0x10680b80,'Pose+8 root quaternion = [0,0,0,1]; pose+0xc translation = shared zero vector, then mark local/world/skin dirty. No root SCL overwrite here.'),
        ('Pose dirty dispatch',0x1088b330,0x1088b35a,'Local update 1088a2a0 then world/skin 10889720 or 10889b00/10889cc0.'),
        ('Supporting limb constructor',0x106cdd40,0x106cde68,'Support reference matrix copied from identity at 10ec2498.'),
        ('Supporting limb reset',0x106cdf80,0x106cdfd8,'Definition direction installed and reference matrix reset to identity.'),
        ('Supporting length smoother',0x106cd0e0,0x106cd1a1,'Clamp desired-current by maxSpeed*dt; clamp result to min/max, epsilon early return.'),
        ('Physics factory property table',0x109454a0,0x1094563c,'volumeFactory, collisionType, createRigidBody, gravityEnabled, massProperties.'),
        ('Collision volume base table',0x1094a730,0x1094a824,'material and intersectionProperties.'),
        ('Capsule property table',0x1094a880,0x1094a9b3,'centre, axis, extent, radius; these are NOT Constraint pointA/pointB fields.'),
        ('Collision type enum',0x10943dd0,0x10943def,'Three entries at 10f6ce44: Static=0, Keyframed=1, Simulating=2.'),
        ('Factory keyframed flag',0x109440a0,0x109440c3,'Only collisionType=1 calls 10942ac0(true); type=2 does not.'),
        ('Body flag constructor tail',0x10942e37,0x10942e46,'and 0xfa clears mask 0x4; or 0x1a leaves it clear.'),
        ('Keyframed flag setter',0x10942ac0,0x10942ad1,'Writes rigid-body flags bit 0x4.'),
        ('Keyframed flag getter',0x10938d00,0x10938d0b,'Returns rigid-body flags bit 0x4.'),
        ('CharacterSolver keyframed input',0x10726354,0x1072639a,'Reads body getter and passes it to support dispatch.'),
        ('Support restrict-normal flag',0x107219f9,0x10721a41,'Passes inverse of keyframed flag as final argument to 106cf5b0.'),
        ('Successful support correction',0x106cfe15,0x106cff76,'contact - rotatedOrigin - direction*restLength - proposedPosition. If restricted, subtract parallel-to-direction component, then add correction.'),
        ('PhysicsBehaviour phase dispatch',0x107f4110,0x107f415c,'Phase0x20 sends actor/pose to physics; other phase calls update-from-physics path.'),
        ('Physics to actor dispatch',0x107f2a50,0x107f2b5c,'Passes owner spatial matrix into 1072a250.'),
        ('Single-root-body matrix delivery',0x1072a250,0x1072a2e0,'When body count1 and no nonzero root mapping, writable simulating body matrix copies directly to actor. Other multi-body/ragdoll branch remains conditional.'),
    ]
    return dict(schema=1, sourceSha256=grounding.SOURCE_SHA, runtimeSha256=grounding.RUNTIME_SHA,
                factory=dict(nodeOffset=5701838, fields=decode_factory(raw), collisionTypes=enums),
                localKeyframedCommand=dict(offsets=keyframed_offsets,
                    payloadWords=[-1,1], adjacentAnimationTrack=5691980,
                    clip='behemoth_com_biped_death_v2',
                    context='Command is inside onBegin of node5691860; its Tracks contains the death clip. Full local FIG tree resolves the parent as behemoth/common/0x97e26b247b34278d/0x44325d69b61c1e.',
                    scope='Exact command-header scan of this entire Behemoth asset finds one. This is not evidence that external/native mode changes cannot happen during front slam.'),
                nativeEvidence=grounding.pe_ranges(runtime, ranges),
                rootZero=dict(address='0x10ff5534', origin='PE .data virtual zero-fill tail, not raw file bytes',
                              warning='Zero-fill describes initial image state. No original DLL execution or observed runtime snapshot.'),
                support=dict(direction=[0,-1,0], restingLength=6, maxLength=8,
                             normalFollowsSurface=False, smoothMaxVelocity=1000,
                             smoothEpsilon=epsilon, referenceMatrix=identity,
                             fixtureRows=rows, smoothedAt20Hz=smooth_resting_length(10,6,.05,1000,0,20,epsilon),
                             finding='Serialized Simulating2 body starts with keyframed bit clear, so the successful support branch removes vertical correction. RestingLength6 is not sufficient evidence of actor Y=6.'),
                poseComparison=pose_comparison(),
                executionBoundary=dict(proven='Ordinary animation root reset, factory initial mode, successful-contact correction formula, and conditional physics-to-actor matrix delivery.',
                    unresolved=['Actual active body mode and rigid-body world matrix at front-slam frame48 after game/physics scheduling and external events.',
                                'Support-query result and any active character/physics constraint response on the same frame.',
                                'Final post-fixup joint/skin matrices and terrain plane on that frame.'],
                    noInference='No wrist/arm lift, shrink, assumed whole-actor +6, or radius-based correction follows from these fields. Capsule dimensions alone are not a settled physics-state capture.'),
                implementation='Static helper/decoder and evidence only. Production geometry, pose, receiving body and effect anchors remain unchanged.',
                validation='Python static byte decoding/matrix arithmetic only; no Java/Gradle/tests/client, original DLL loading/execution or audio playback.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT/'art/encounters/goliath-support-plane-audit.json')
    args = parser.parse_args()
    report = inspect()
    args.output.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(dict(output=str(args.output), sourceFields=len(report['factory']['fields']),
                          nativeRanges=len(report['nativeEvidence']),
                          poseMatrixError=report['poseComparison']['maxMatrixError'])))
