"""Decode local P2 grounding metadata against the verified P2 runtime.

Static data analysis only: never load/execute the DLL, Java, or a game. This
does not infer a floor clamp from class names or claim a final rendered pose.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import struct

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path('D:/PrototypeReference/p2/input/p2/behemoth.p3d')
RUNTIME = Path('D:/PrototypeReference/p2/input/prototype2engine.dll')
FIG = Path('D:/PrototypeReference/p2/creature-fig-structure.json')
SOURCE_SHA = '3737ff61e7042391bf12c95c2018e034f52a1945983d0a7053608a01d69240c7'
RUNTIME_SHA = 'bfd62e944eaed5762bc791306f54ab4c2ce03705e52180f44d0da5cd7cc7e07d'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def hash64(text):
    result = 0
    for byte in text:
        result = ((result * 65599) ^ byte) & ((1 << 64) - 1)
    return result


class Reader:
    def __init__(self, data, position):
        self.data, self.position, self.fields = data, position, []

    def value(self, fmt):
        values = struct.unpack_from('<' + fmt, self.data, self.position)
        self.position += struct.calcsize('<' + fmt)
        return values[0] if len(values) == 1 else list(values)

    def field(self, name, fmt, member=None):
        start = self.position
        if fmt in ('text8', 'text32'):
            length = self.value('B' if fmt == 'text8' else 'I')
            value = self.data[self.position:self.position + length].decode('ascii')
            self.position += length
        elif fmt == 'bool32':
            value = self.value('I')
            assert value in (0, 1), (name, start, value)
            value = bool(value)
        else:
            value = self.value(fmt)
        row = dict(name=name, value=value, fileOffset=start,
                   bytes=self.position - start, encoding=fmt)
        if member is not None:
            if member == 0:
                row['runtimeAccessor'] = 'Custom getter/setter; metadata descriptor offset 0 is not a direct struct member'
            else:
                row['runtimeMemberOffset'] = hex(member)
        self.fields.append(row)
        return value


def meta(raw, offset, expected_length):
    kind, size, total = struct.unpack_from('<III', raw, offset)
    assert kind == 0x7f00001 and size - 12 == expected_length
    assert raw[offset + 16:offset + 20] == b'META'
    assert struct.unpack_from('<I', raw, offset + 12)[0] == expected_length - 4
    return Reader(raw, offset + 20)


def pe_ranges(runtime, ranges):
    """Preserve exact file/VA boundaries; no platform loader or disassembler."""
    pe = struct.unpack_from('<I', runtime, 0x3c)[0]
    machine, count = struct.unpack_from('<HH', runtime, pe + 4)
    assert machine == 0x14c and runtime[pe:pe + 4] == b'PE\0\0'
    optional_size = struct.unpack_from('<H', runtime, pe + 20)[0]
    base = struct.unpack_from('<I', runtime, pe + 24 + 28)[0]
    sections = []
    for i in range(count):
        p = pe + 24 + optional_size + i * 40
        size, rva, raw_size, raw_offset = struct.unpack_from('<IIII', runtime, p + 8)
        sections.append((rva, raw_size, raw_offset))
    result = []
    for name, begin, end, meaning in ranges:
        rva = begin - base
        section = next(s for s in sections if s[0] <= rva < s[0] + s[1])
        offset = section[2] + rva - section[0]
        blob = runtime[offset:offset + end - begin]
        assert len(blob) == end - begin and end - base <= section[0] + section[1]
        result.append(dict(name=name, beginVA=hex(begin), endExclusiveVA=hex(end),
                           fileOffset=offset, bytes=len(blob), sha256=sha(blob), meaning=meaning))
    return result


def decode(raw, runtime, fig):
    assert sha(raw) == SOURCE_SHA and sha(runtime) == RUNTIME_SHA
    ground = meta(raw, 5701362, 151)
    # Ordered declarations at 0x106ce950..0x106cee1b, 21 fields.
    ground_fields = [
        ('name', 'text32', 8), ('sphereRadius', 'f', 0x10),
        ('intersectionProperties', 'text8', 0x30), ('origin', '3f', 0x34),
        ('direction', '3f', 0x40), ('restingLength', 'f', 0x4c),
        ('maxLength', 'f', 0x50), ('supportAngleTolerance', 'f', 0),
        ('directionFollowsSurfaceNormal', 'bool32', 0x58),
        ('forceDirectionInCharacterSpace', 'bool32', 0x59),
        ('characterSurfaceOrientation', 'i', 0x5c),
        ('characterUpVectorOrientation', 'i', 0x60),
        ('smoothRestingLength', 'bool32', 0x5b),
        ('smoothMaxLinearVelocity', 'f', 0x64),
        ('smoothFractionalMaxLinearVelocity', 'f', 0x68),
        ('smoothMaxLinearVelocityMapEntryCount', 'I', 0x6c),
        ('smoothMinRestingLength', 'f', 0x8c),
        ('smoothMaxRestingLength', 'f', 0x90),
        ('smoothDirection', 'bool32', 0x5a),
        ('smoothMaxAngularVelocity', 'f', 0x88), ('secondaryCheckRadius', 'f', 0x94)]
    for field in ground_fields:
        value = ground.field(*field)
        if field[0] == 'smoothMaxLinearVelocityMapEntryCount':
            assert value == 0, 'Nonempty map requires its entry schema'
    assert ground.position == 5701362 + 12 + 151

    actor_payload = 5702063 + 12
    solver = Reader(raw, actor_payload + 1431)
    assert solver.field('class', 'text32') == 'CharacterSolverBehaviour'
    assert solver.field('version', 'H') == 1
    assert solver.field('schemaCrc', 'I') == 0xafeeb79c
    # Base engine::Behaviour: serialized ushort mask then byte frame skip.
    # isActive is a transient property (metadata flag 't'), not another bool.
    solver.field('logicStateMask', 'H', 0)
    solver.field('timeSliceFrameSkip', 'B', 9)
    for field in [('supportingLimbs', 'text8', 0x6c),
                  ('limbIKEnabled', 'bool32', 0x98),
                  ('sweepAndClipMotion', 'i', 0xb4),
                  ('supportingLimbUpdateInterval', 'f', 0x80),
                  ('sharedProperties', 'text8', 0x94),
                  ('predictSupportingLimbSurfaceContact', 'bool32', 0x126),
                  ('stickToSupportingSurface', 'bool32', 0x68)]:
        solver.field(*field)
    assert solver.position == actor_payload + 1538

    limb_list = meta(raw, 5701640, 31)
    assert limb_list.field('count', 'I') == 1
    limb_list.field('definition', 'text8')
    assert limb_list.position == 5701640 + 12 + 31

    props = meta(raw, 5696839, 123)
    assert props.field('count', 'I') == 2
    strategies = []
    for i in range(2):
        before = len(props.fields)
        props.field('strategyName', 'text32', 8)
        props.field('strategyType', 'text32', 0x10)
        assert props.field('version', 'H') == 1
        assert props.field('schemaCrc', 'I') == 0x61f99528
        props.field('fixupEnabledByDefault', 'bool32', 0x14)
        props.field('alwaysEnabledWhileRagdolling', 'bool32', 0x15)
        strategies.append(props.fields[before:])
    assert props.position == 5696839 + 12 + 123

    # Exact source hashing resolves old unknown type IDs; never guess from a
    # nearby branch or treat an unresolved record as absence of a mechanism.
    words = sorted({m.group() for m in re.finditer(rb'[A-Za-z_][A-Za-z0-9_:]{2,180}', runtime)})
    names = {}
    for word in words:
        key = hash64(word)
        assert key not in names or names[key] == word.decode()
        names[key] = word.decode()
    records = [r for r in fig['records'] if 'behemoth' in r['file']]
    aliases, resolved = {}, []
    for record in records:
        kind = record['type']
        if kind.startswith('#'):
            key = int(kind[1:], 16)
            if key in names:
                aliases[kind] = names[key]
                kind = names[key]
        resolved.append(dict(type=kind, originalType=record['type'], offset=record['offset'], owner=record['owner']))
    unresolved = sorted({r['type'] for r in resolved if r['type'].startswith('#')})
    assert not unresolved
    relevant = [r for r in resolved if any(w in r['type'].lower() for w in ('offset', 'fixup', 'stuck', 'support', 'ground', 'root', 'limbik'))]
    slam = [r for r in resolved if 5465352 <= r['offset'] < 5469000]
    state = {r['name']: r['value'] for r in solver.fields}
    assert state['limbIKEnabled'] is False and state['supportingLimbs'] == 'BehemothLimbs'
    assert state['supportingLimbUpdateInterval'] == 0

    ranges = [
        ('Behaviour metadata', 0x106991a0, 0x10699294, 'logicStateMask ushort; timeSliceFrameSkip byte; transient isActive'),
        ('ushort binary reader', 0x10147c00, 0x10147c2e, 'Reads two bytes through 0x109b0770'),
        ('byte binary reader', 0x103b50e0, 0x103b510e, 'Reads one byte through 0x109b0770'),
        ('bool binary reader', 0x1012e0a0, 0x1012e0d5, 'Reads four bytes; converts nonzero to bool'),
        ('SupportingLimbDefinition metadata', 0x106ce950, 0x106cee1c, '21 ordered properties; name is a support definition identifier, no hand/finger joint field'),
        ('CharacterSolverBehaviour metadata', 0x10724d90, 0x10724fb3, 'Base Behaviour plus seven ordered properties'),
        ('PoseFixup strategy entry metadata', 0x102137d0, 0x102138d3, 'Name, strategy, default-enabled, enabled-while-ragdolling'),
        ('JointOffset state constructor', 0x1024b8a0, 0x1024b8c6, 'Zero offset and invalid joint index 0xffff; enabled strategy alone does not move a joint'),
        ('JointOffset apply', 0x10134410, 0x10134580, 'Adds externally supplied local translation to selected joint; propagates pose, no terrain query'),
        ('JointOffsetAddAction begin', 0x10318ae0, 0x10318c01, 'Resolves action joint, enables/queries joint_offset strategy and assigns state joint'),
        ('JointOffsetAddAction update', 0x100a3050, 0x100a34dd, 'Computes configured offset and writes state; not an always-running actor grounding behaviour'),
        ('JointStuck state factory', 0x10407950, 0x10407983, 'Creates state with the owning actor handle'),
        ('JointStuck state constructor', 0x1002bd60, 0x1002be58, 'Owner handle, invalid joint 0xffff, capture flag false'),
        ('JointStuck event', 0x10387480, 0x103874ac, 'Message group 0x1000 subtype 15 supplies joint index and resets captured matrix'),
        ('JointStuck apply', 0x1047eca0, 0x1047f17c, 'Validates owner/joint, captures joint world matrix then retains world anchor as owner changes; no terrain query'),
        ('PoseFixupBehaviour update', 0x10183100, 0x101831cb, 'Iterates strategies in property-list order, with state-active or ragdoll enable gating'),
        ('CharacterSolverBehaviour update', 0x10726180, 0x107266bb, 'Actor motion/support first; at 0x10726693 checks member +0x98 before calling limb IK 0x10722980'),
        ('CharacterSolver support dispatch', 0x10721900, 0x10721a41, 'Passes support state, entity transforms, stick/predict flags to 0x106cf5b0'),
        ('Supporting limb solve', 0x106cf5b0, 0x106cff76, 'Entity/support transforms and support surface query; separate from limb IK pose correction'),
        ('JointStuckTrack properties', 0x10779e30, 0x10779ef3, 'External animation track exposes timeBegin, timeEnd, stuckJoint; no automatic contact query'),
        ('JointStuckTrack instance creation', 0x107cc4c0, 0x107cc506, 'Resolves target actor and creates track instance via 0x107cc300; participation is not established by actor META'),
        ('JointStuckAction constructor', 0x107cc300, 0x107cc338, 'Instance vtable 0x10c76660; records action properties and actor handle'),
        ('JointStuckAction begin', 0x107cc360, 0x107cc415, 'Resolves configured stuckJoint through actor skeleton; sends group 0x1000 subtype 15 and resolved joint index to actor dispatcher 0x10699610'),
        ('JointStuckAction end', 0x107cbc00, 0x107cbc6a, 'Sends group 0x1000 subtype 15 with invalid joint 0xffff; releases strategy anchor'),
        ('Pure3D skeleton loader', 0x1086c740, 0x1086d0ad, 'Loads raw joint matrix plus twelve auxiliary floats, matrixType ushort and flag uint. Stores inverse-parent-scale flag iff matrixType==1 and flag!=0'),
        ('Pure3D bind hierarchy and inverse bind', 0x108a41c0, 0x108a4334, 'Builds reference local/world transforms and inverse-world-bind palette'),
        ('Pure3D local pose', 0x1088a2a0, 0x1088a7f5, 'Evaluates quaternion, translation and scale. At 0x1088a663..0x1088a6ac, flagged joint linear basis is multiplied componentwise by reciprocal parent channel scale; translation is unchanged'),
        ('Pure3D world pose', 0x10889b00, 0x10889cb6, 'Ordinary parent-world by compensated-local accumulation; compensation already happened in local pose'),
        ('Pure3D world plus skin update', 0x10889720, 0x10889af6, 'Updates world transforms and composes animated world with inverse raw reference world for skin'),
        ('Pure3D skin-only update', 0x10889cc0, 0x10889fa1, 'Composes animated world and inverse raw reference world; no additional terrain contact or wrist lift')]
    def occurrences(token):
        return [m.start() for m in re.finditer(re.escape(token),raw)]
    track_tokens={name:dict(textOffsets=occurrences(name.encode()),hashOffsets=occurrences(struct.pack('<Q',hash64(name.encode()))))
                  for name in ['jointStuck','JointStuck','stuckJoint','JointStuckTrack','JointOffsetAdd','jointOffsetAdd','limbIKEnabled']}
    for name in ['stuckJoint','JointStuckTrack','JointOffsetAdd','jointOffsetAdd','limbIKEnabled']:
        assert not track_tokens[name]['textOffsets'] and not track_tokens[name]['hashOffsets'],name
    return dict(
        schema=1, source=dict(file=str(SOURCE), sha256=SOURCE_SHA),
        runtime=dict(file=str(RUNTIME), sha256=RUNTIME_SHA, machine='x86', imageBase='0x10000000',
                     provenance='D:/PrototypeReference/p2/runtime-provenance.json'),
        characterSolver=dict(nodeOffset=5702063, componentPayloadOffset=1431, fields=solver.fields),
        supportDefinition=dict(nodeOffset=5701362, fields=ground.fields),
        supportingLimbs=dict(nodeOffset=5701640, fields=limb_list.fields),
        poseFixupProperties=dict(nodeOffset=5696839, strategies=strategies),
        runtimeEvidence=pe_ranges(runtime, ranges),
        jointStuckTrack=dict(instanceVtable='0x10c76660',begin='0x107cc360',end='0x107cbc00',consumer='0x10387480',
                            message=dict(group='0x1000',subtype=15,beginPayload='resolved configured joint',endPayload='0xffff'),
                            rawAssetTokens=track_tokens,
                            finding='Producer/consumer lifecycle is resolved. Local behemoth asset contains strategy names only, not a track instance/property or activation hash. Native/external event sources remain outside this absence claim.'),
        skinTransformPipeline=dict(
            localColumnConvention='T(channelTranslation) * inverseDiagonal(parentChannelScale) * R(channelQuaternion) * diagonal(channelScale) for matrixType=1 and flagged joints; omit inverseDiagonal when unflagged. Child translation is not compensated.',
            worldColumnConvention='parentAnimatedWorld * compensatedLocal',
            skinColumnConvention='animatedWorld * inverse(rawBindWorld)',
            flags='82 Goliath joints have matrixType=1; 81 have inverse-parent-scale flag 1. Only unweighted Middle_Base_L has flag 0. Twelve auxiliary floats are [0,0,0,1,1,1,0,0,0,0,0,0] on all joints.',
            fix='The prior Python world reader omitted SCL. Goliath-specific reader and source hierarchy now preserve SCL plus original segment-scale compensation. This is a supported pose correction, not a grounding heuristic.',
            detailedChecks='goliath-bind-correction.json',
            limitation='This recovers palette construction, not the final post-fixup frame inputs or actor-to-ground world transform. Giant source L arm has no SCL track; its support gap remains.'),
        fig=dict(index=str(FIG), sha256=sha(FIG.read_bytes()), records=len(records),
                 newlyResolvedTypes=aliases, unresolvedTypes=unresolved,
                 groundingRelatedRecords=relevant, frontSlamRecords=slam,
                 absenceScope='All 4699 local behemoth FIG records after every unknown type hash is resolved. No JointOffsetAdd/JointStuck/EnableLimbIK record; other actors, external scripts and native event senders are not ruled out.'),
        executionOrder=dict(
            proven=['Within PoseFixup: joint_offset then jointStuck, each gated and requiring a valid joint state.',
                    'Within CharacterSolver: proposed actor motion, support/collision solve, then optional limb IK. Serialized Behemoth limbIKEnabled=false skips the final IK call unless an external message changes it.'],
            notProven='The global scheduler phase order between Puppet, PoseAnimation, PoseFixup and CharacterSolver is not yet recovered. Actor META listing order is not asserted to be update order.'),
        conclusion='These installed components do not establish automatic giant-fist terrain IK. There is no proven source postprocessor to port as a wrist lift or cuboid floor clamp. Existing bare-source minY and authored excess remain descriptive shape/pose evidence, not original final rendered ground contact.',
        remainingEvidence=[
            'JointStuckTrack factory/begin/end and strategy consumer are now resolved; local front-slam has no instance/activation. Remaining evidence is an actual external/native sender affecting this actor, not another metadata-name search.',
            'Recover global animation/physics update order, final post-fixup local inputs for Wrist_L and Shield_L_1..3 at frame 48 and actual entity-to-ground transform. Skin palette math is now recovered.',
            'Compare that final source surface against the authored knuckle row to separate its measured extra 0.933065 block extension from any intentional source penetration. Do not change wrist, scale, foot waves or collision alone.'],
        nextStep='For the unresolved giant-fist gap, follow actual actor/entity support transformation and global animation/fixup scheduler call sites. A post-fixup source frame-48 joint/actor snapshot is the smallest decisive evidence if static control flow cannot establish participation. Runtime capture stays deferred.',
        sourceGroundEffectFollowup='goliath-ground-impact-provenance.json: forward fist cone-ray batches at source 0.033333/0.066667/0.116667 seconds after contact now drive real material feedback. Source foot/root positions constrain arbitrary mesh lifts. Effects do not establish a wrist-height correction or complete original actor/phase behaviour.',
        actorPoseFollowup='goliath-actor-pose-followup.json: Puppet phase 4/0x10, extracted root delta, actor spatial setter and support output remain separate from skin correction; WorldCone uses actor spatial, not a Motion_Root joint field. Final giant-fist/support baseline remains unresolved. Verified bullrush work continues in goliath-charge-provenance.json.',
        validation='Hash-bound source parsing, exact field boundaries and static PE byte ranges only. No Gradle, Java compilation/tests, GameTests, client or source DLL execution.',
        runtimeChanges='Goliath SCL and native parent-scale compensation restored using source/helper hierarchy and GoliathMotion reciprocal recomputation after blending. No wrist-height, collision-size or unsupported grounding correction introduced.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'art/encounters/goliath-grounding-runtime-audit.json')
    args = parser.parse_args()
    report = decode(SOURCE.read_bytes(), RUNTIME.read_bytes(), json.loads(FIG.read_text()))
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(output=str(args.output), supportFields=len(report['supportDefinition']['fields']),
                          resolvedFigTypes=len(report['fig']['newlyResolvedTypes']), records=report['fig']['records'],
                          limbIKEnabled=False, runtimeChanges='Goliath SCL/segment-scale compensation; no grounding clamp')))


if __name__ == '__main__':
    main()
