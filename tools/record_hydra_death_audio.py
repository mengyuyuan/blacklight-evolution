"""Record static source/audio provenance; never play audio or execute Java."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = Path('D:/CodexCache/work/p1-hydra-audio')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf8'))


source = read(ROOT/'art/hydra/death-source-provenance.json')
thermal = read(ROOT/'art/hydra/thermobaric-death-source.json')
audio = read(PRIVATE/'patch-wave-map.json')
patches = {p['hash']: p for p in audio['patches']}
tracks = []
for item in source['sourceSoundFields']:
    fields = item['fields']
    patch = patches.get(fields['Patch']['hash'])
    tracks.append({
        'offset': item['offset'], 'fields': fields,
        'sourceClock': 'sink node' if item['offset'] >= 7944 else 'normal death node',
        'patch': patch,
        'referenceWaves': [w for w in audio['waves'] if patch and w['name'] in patch['files']],
        'implemented': patch is not None,
    })

files = [
    'src/main/java/com/codex/blacklight/combat/HydraDeathAudioState.java',
    'src/main/java/com/codex/blacklight/client/audio/HydraDeathAudio.java',
    'src/main/java/com/codex/blacklight/client/audio/HydraDeathAudioEvents.java',
    'src/test/java/com/codex/blacklight/combat/HydraDeathAudioStateTest.java',
    'src/main/resources/assets/blacklight/sounds.json',
    'src/main/resources/assets/blacklight/lang/zh_cn.json',
    'src/main/resources/assets/blacklight/lang/en_us.json',
    *['src/main/resources/assets/blacklight/sounds/hydra/'+name+'.ogg' for name in
      ['death_roar_1', 'death_roar_2', 'death_roar_3', 'death_debris_loop']],
    *['src/main/resources/assets/blacklight/sounds/infection/swing_'+str(i)+'.ogg' for i in range(1, 4)],
    'art/creature-audio/hydra-death-authored-20261117.json',
    'art/creature-audio/hydra-death-LICENSE.md',
    'tools/build_hydra_death_audio.py', 'tools/record_hydra_death_audio.py',
    'tools/player-fix-unit.init.gradle',
]
record = {
    'schema': 1, 'date': '2026-11-17',
    'state': 'Source sound roles and authored client audio integrated; no compilation, playback or player acceptance.',
    'source': {
        'deathFig': source['sources'],
        'audioPath': 'audio/hydra.p3d',
        'privateAudioSha256': digest(PRIVATE/'hydra.p3d'),
        'privatePatchMapSha256': digest(PRIVATE/'patch-wave-map.json'),
        'parsing': 'Length-delimited P3D Patch names/hashes/file bindings and RADP header durations only; no original audio decoded, copied into resources or played.',
        'normalAndSinkTracks': tracks,
        'thermalTrack': {'offset': 6576, 'localSeconds': 0, 'patch': patches['7696709824372853067'],
                         'pitch': [.7, .8], 'clock': 'Outer death node, outside delayed TrackGroup6252 (group end6576).'},
        'thermalClockProof': 'art/hydra/thermobaric-death-source.json',
    },
    'integration': {
        'normal': 'Observed death ages0/30/40/60 trigger roar/whoosh/roar/whoosh. Second roar replaces the first matching actor patch; source pitch intervals retained.',
        'sink': 'Polled hydra_debris loop starts at normal age66+4=70 and stops at103 or lost ownership. Source float.200000003 is snapped to four20Hz sink ticks; no fifth-tick float-ceil artifact.',
        'thermal': 'Only outer track6576 roar at age0; no ordinary whoosh or sink loop. Finite unpolled roar can finish at last position after40tick deletion.',
        'observation': 'Existing synchronized death kind/age owns all timing. First tracking of a corpse baselines its snapshot; one-shot history is not replayed. Active polled debris may resume. Large gaps consume obsolete cues; no extra network field or server death clock.',
        'replacement': 'Kind replacement, age rewind or return to alive stops the old actor patch set and rebases; cues are consumed before calling playback.',
        'lifecycle': 'Entity identity and world are checked. Untracking/removal stops polled loops; unpolled finite tails retain last position. World unload/replacement stops all voices. Pause freezes observations; mute consumes cues and clears voices.',
        'budget': 'Nearest tracked actors considered first;24global voices,20tick loop retry,6observer-tick backend grace,180observer-tick finite safety cap. Grace is independent of SoundEngine.tick so failed startup cannot leak the voice budget.',
        'mcAdapters': 'FIG roar100/400 mapped directly to blocks with linear attenuation; source-world acoustic unit equivalence is not proven. Zero-distance tracks do not establish attenuation; whoosh24/debris48block caps are MC mix choices. Gain.35/.25 and UUID/tick-seeded pitch choice are authored adapters. Native probability, Patch DSP/randomization and exact source attenuation are not replicated.',
        'audio': 'Three authored Socapex Foley roar variants and one debris loop; existing licensed swing sounds supply whoosh. Reference durations guide authoring, not original waveforms. CC BY-SA3.0 credit/transforms retained.',
    },
    'limits': [
        'Sink track7944 hash7808c21846650ea5 has no proven Patch mapping and is deliberately unimplemented.',
        'Original particle effects, Crater lifetime, weighted Hydra mesh and native actor physics remain outside this audio change.',
        'No audio listening, client/subtitle/mix acceptance, compilation or automated test execution performed.',
    ],
    'validation': {'type': 'static source/data/provenance only', 'unitMethodsWritten': 4,
                   'compilation': 'not run', 'tests': 'not run', 'audioPlayback': 'not played', 'client': 'not launched'},
    'snapshots': {p: digest(ROOT/p) for p in files},
}
(ROOT/'art/creature-audio/hydra-death-integration-20261117.json').write_text(
    json.dumps(record, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
print('Recorded Hydra death audio provenance and', len(files), 'static snapshots; no playback or Java.')
