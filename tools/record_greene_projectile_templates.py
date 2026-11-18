"""Read P1 template/FIG/audio metadata and authored model contracts, without playback.

Original geometry and audio remain in the private reference tree. Only bounds,
names, field values, offsets and hashes are written to the public source record.
"""
from pathlib import Path
import hashlib
import importlib.util
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
REF = Path('D:/PrototypeReference/p1')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def hashed(name):
    value = 0
    for char in name.encode('ascii'):
        value = ((value * 65599) ^ char) & 0xffffffffffffffff
    return value


def inflated(relative):
    return zlib.decompress((REF / relative).read_bytes()[16:])


mesh_path = 'input/projectile-search/art/locations/manhattan/props.p3d.rz'
fig_path = 'input/remote-metadata/art/locations/manhattan/props_fig.p3d.rz'
tod_path = 'input/remote-metadata/art/locations/manhattan/props_tod.p3d.rz'
mesh, fig, tod = inflated(mesh_path), inflated(fig_path), inflated(tod_path)
objects = json.loads((REF / 'private/greene-projectile-templates.json').read_text())
audit = json.loads((REF / 'greene-projectile-fig-audit.json').read_text())
shapes = [
    dict(shape='goreGibTorso001_initialShape', meshOffset=13226533,
         boundsOffset=13254946, vertices=639, shader='a8ab232afdfcfc4636e3cbd37dd76b01',
         sourceTextures=['manhattanprop_06_plane0']),
    dict(shape='debrisBreakableAsphaltBig_initialShape', meshOffset=10676765,
         boundsOffset=10689323, vertices=217, shader='8461b469d2602119e593ec257a77d958',
         sourceTextures=['park_rock00_diffuse.dds', 'park_rock00_normal.dds'])]
for shape in shapes:
    at = shape['boundsOffset']
    assert struct.unpack_from('<3I', mesh, at) == (0x10003, 36, 36)
    assert shape['shape'].encode() in mesh[shape['meshOffset']:shape['meshOffset'] + 100]
    bounds = struct.unpack_from('<6f', mesh, at + 12)
    shape['minimum'], shape['maximum'] = bounds[:3], bounds[3:]
    shape['pose'] = 'Composite root and initial joint identity; source forward is -Z.'

templates = []
for obj in objects:
    if not obj['name'].startswith('Projectile_VomitMissile'):
        continue
    raw = bytes.fromhex(obj['hex'])
    assert tod[obj['dataOffset']:obj['dataOffset'] + len(raw)] == raw
    marker = b'VectorAttribute\x01\x00'
    scale = struct.unpack_from('<3f', raw, raw.index(marker) + len(marker))
    flesh = obj['name'] == 'Projectile_VomitMissile'
    body = 'goreGibTorso001' if flesh else 'debrisBreakableAsphaltBig'
    playback = body + '/motherVomit'
    assert body.encode() in raw and ('//' + playback).encode() in raw
    templates.append(dict(name=obj['name'], objectOffset=obj['offset'], metaOffset=obj['dataOffset'],
                          metaBytes=len(raw), scale=scale, render=body, playback='//' + playback,
                          collisionAudio='concrete_physics' if b'concrete_physics' in raw else None))
assert len(templates) == 3

owners = ('goreGibTorso001/motherVomit', 'debrisBreakableAsphaltBig/motherVomit')
tracks, visibility = [], []
sound_hashes = set()
for row in audit['records']:
    if not row['owner'].startswith(owners):
        continue
    if row['type'] == 'PrimitiveDisplay':
        # The generic open schema omits this variable array. Read the one-entry
        # payload directly: base u32, time f32, count u32, primitive u64, state u64.
        raw = fig[row['payload']:row['payload'] + row['length']]
        base, time, count, primitive, state = struct.unpack('<IfIQQ', raw)
        assert base == 0 and time == 0 and count == 1
        name = next(s['shape'] for s in shapes if hashed(s['shape']) == primitive)
        enabled = {hashed('On'): True, hashed('Off'): False}[state]
        visibility.append(dict(offset=row['offset'], owner=row['owner'], shape=name,
                               time=time, visible=enabled, stateHash=hex(state)))
    elif row['type'] in ('Effect', 'MotionTrail', 'Sound', 'Destroy'):
        assert 'fields' in row, (row['type'], row['offset'])
        tracks.append(dict(offset=row['offset'], owner=row['owner'], type=row['type'], fields=row['fields']))
        if row['type'] == 'Sound':
            sound_hashes.add(row['fields']['Patch']['hash'])
    assert row['type'] not in ('WorldDamage', 'WorldConeDamage'), 'Do not invent impact AOE'
assert len(visibility) == 6

spec = importlib.util.spec_from_file_location('audio_reader', ROOT / 'tools/inspect_helicopter_audio_source.py')
audio_reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audio_reader)
audio = []
audio_sources = []
for name in ('mother.p3d', 'sfx_shared.p3d'):
    path = REF / 'input/projectile-search/Audio' / name
    package = audio_reader.inspect(path)
    audio_sources.append(dict(path=str(path), sha256=package['sha256']))
    for patch in package['objects']:
        if patch['kind'] != 'Patch' or patch['hash'] not in sound_hashes:
            continue
        files = patch['properties']['files']
        waves = [w for w in package['objects'] if w['kind'] == 'AudioFile'
                 and (w['name'] in files or w.get('alias') in files)]
        assert len(waves) == len(files)
        audio.append(dict(package=name, patch=patch, audioFiles=waves))
assert len(audio) == 5

model_path = 'src/main/resources/assets/blacklight/models/greene_projectile_shapes.json'
model = json.loads((ROOT / model_path).read_text())
model_checks = {}
for name, cubes in model['shapes'].items():
    for cube in cubes:
        assert all(0 <= a < b <= 1 for a, b in zip(cube['from'], cube['to']))
        assert cube['material'] in model['materials']
        assert int(cube['color'], 16) >> 24 == 255
    for axis in range(3):
        assert min(c['from'][axis] for c in cubes) == 0
        assert max(c['to'][axis] for c in cubes) == 1
    model_checks[name] = dict(authoredCuboids=len(cubes), normalizedBounds=[0, 0, 0, 1, 1, 1])
assert (ROOT / 'src/main/resources/assets/blacklight/textures/block/hive_flesh.png').is_file()

files = [
    'src/main/java/com/codex/blacklight/combat/GreeneProjectileAppearance.java',
    'src/main/java/com/codex/blacklight/combat/GreeneProjectileEffects.java',
    'src/main/java/com/codex/blacklight/combat/GreeneMissileFlight.java',
    'src/test/java/com/codex/blacklight/combat/GreeneProjectileAppearanceTest.java',
    'src/test/java/com/codex/blacklight/combat/GreeneMissileFlightTest.java', model_path]
record = dict(
    schema=1, date='2026-11-17', status='Static metadata reading and authored source/assets only; no compilation, test execution, engine/native execution, game rendering or audio playback.',
    sources=[dict(path=str(REF / path), sha256=sha(REF / path)) for path in (mesh_path, fig_path, tod_path, 'input/prototypeenginef.dll')] + audio_sources,
    templates=templates, sourceMeshes=shapes, primitiveDisplay=visibility, playbackTracks=tracks, audio=audio,
    gunMapping={'1..7': 'Projectile_VomitMissile (DeathFlower/InsectSwarm)',
                '8/9': 'Projectile_VomitMissile2 (Rifle uses 9; Shotgun uses 8 and 9)',
                '10': 'Projectile_VomitMissile3 (AntiAir/AntiTank)'},
    runtimeEvidence={
        '0x1089bca0': 'PrimitiveDisplay instance reads list from track+2c; resolves primitive hash and enum at 1089be34..6f, skips enum 2, emits bool(enum==0) in category 8 subtype 1 at 1089be85..bb. The source payload explicitly supplies shape and On/Off hashes; generic PrimitiveDisplayTrack.cs does not decode the list.',
        '0x10468800': 'Spin profile axes normalized independently, multiplied by per-axis lerped radians/second. motherVomit_Spin axis1=(1,0,0), axis2=(0,0,1), min1=max1=-10, min2=max2=10, randomOrient=true; angular velocity=(-10,0,10).',
        '0x107be440': 'Negates axis-angle before sin/cos and 107bb720. 107bdb70 multiplies row oldWorld*delta; column representation is delta*oldWorld. Spin bypasses ordinary flight alignment in 1046c0c0.',
        '0x1046ac20': '1046b198..1046b232 randomOrient selects each Euler independently in [-pi,pi], using constants 10d9b0a0 and 10e05af0, before 107bb870.',
        '0x1046c0c0': '1046c216..258 compares old-new absolute components with float 10e05afc = 9.999999747378752e-6. Movement above any component threshold: 1046c2fb..346 normalizes old-new, 1046c287..2ac negates into +A4 heading. Tiny motion retains heading. Actual movement thus feeds subsequent guidance and acceleration.',
        '0x1046c680': '1046c74d aliases previous position +8c for sweep; 1046cd29..39 copies current +80..88 to previous +8c..94 at collision-processing completion.'},
    implementation={
        'units': 'Source spatial units 1:1 blocks for projectile templates; seconds are source seconds. Direct damage scaling is owned separately by GreeneProjectile.',
        'geometry': '11 authored torso cuboids and 9 authored asphalt cuboids, normalized to source bounds. No original mesh vertices, UVs or textures exported. Existing project hive flesh and vanilla block textures are presentation adapters.',
        'visibility': 'Torso mesh is Off in flight and final; green effect/trail is its visible flight representation. Asphalt mesh is On in flight, Off immediately on impact. No invented opaque final linger or fade.',
        'lifetime': 'Source final Destroy at .3000000119 (torso) / .2666670084 (asphalt), rounded up to 6 server ticks. Final effects persist independently; source final contains no WorldDamage/AOE.',
        'spin': 'UUID-derived deterministic independent Euler samples retain the source randomOrient distribution, not its RNG sequence. Source world-axis angular velocity applies to synced flight seconds.',
        'particles': 'Authored MC dust/smoke/electric/gravel particle types and counts; no original particle materials or emitters. Asphalt emission density uses explicit source .6/metre, stochastic rounding with MC five-particle safety ceiling. Torso mid green bias recorded; final bias-versus-override priority is not assumed.',
        'audio': 'Five exact original Patch-to-AudioFile chains recorded; waveforms remain private. Existing budgeted authored infection BURST (torso) / CRUSH (stone) are impact role adapters. Stone preserves FIG 1.1..1.9 pitch. Neither loop waveforms nor original composite two-layer hit mix is claimed.',
        'flightCorrection': 'After native acceleration, direction is updated from real displacement with source component epsilon, preserving inherited drift in the next guidance step.'},
    unresolved=[
        'MotionTrail GenerateTime/FadeOutTime timer units and GPU trail material semantics are not yet decoded.',
        'Effect RGB bias versus override priority and all native emitter parameter transforms remain undecoded; MC particles are explicit visual adapters.',
        'Mother projectile and fire polled-lifetime continuous audio loops are source-mapped but not implemented with project Foley in this change.',
        'MC client interpolation, model visibility and particle density await the user-authorized later test session.'],
    staticReview=dict(modelContracts=model_checks, authoredAppearanceCases=4, executedTests=False, builtJava=False),
    implementationFiles=[dict(path=p, sha256=sha(ROOT / p)) for p in files],
    rendererImplementations={str(folder): sha(folder / 'src/main/java/com/codex/blacklight/client/renderer/GreeneProjectileRenderer.java')
                             for folder in (Path('D:/blacklight-evolution'), Path('D:/blacklight-evolution-forge-1.20.1'))})
output = ROOT / 'art/encounters/greene-projectile-template-source.json'
if (ROOT / 'art/encounters/greene-projectile-loop-audio-source.json').exists():
    record['unresolved'] = [item for item in record['unresolved'] if not item.startswith('Mother projectile and fire polled-lifetime')]
    record['implementation']['audioLoopFollowup'] = 'greene-projectile-loop-audio-source.json: two polled loops with native sentinel/pitch/gain/range/fade parameters; project Foley and bounded client lifecycle.'
output.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'record': str(output), 'templates': len(templates), 'visibilityEntries': len(visibility),
                  'mappedAudioPatches': len(audio), 'modelContracts': model_checks, 'sha256': sha(output)}))
