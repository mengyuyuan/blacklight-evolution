"""Accept NEW Greene eight-stage evidence; never upgrade a historical four-stage run.

This recorder does not build, run tests, launch a client, or create review evidence.
Run it only after separately authorized validation. Default input is
D:/CodexCache/work/greene-cycle-v2/review.json. The review manifest must have:
  schema: 2; platforms: {neo: {...}, forge: {...}}
Each platform contains sourceSha256 (captured for the build/run, covering FILES),
evidence/status artifact refs, unitXml refs, servers, and visualReview. An artifact
ref is {path: QA-relative-or-absolute, sha256: recorded-digest}. servers contains
greeneServer and encounterServer, each with log ref and sourceFiles: the EXACT
GameTest source files included in that run. visualReview contains reviewedBy,
passed:true, notes, and selectedFrames:[{image: basename, sha256: digest}].

Use fresh reports, not a newly hashed old run. Source/artifact hashes bind the
operator's run manifest; the recorder cannot independently prove which classes
an external process loaded. Current source hashes must still match that manifest.
Both loaders must pass before a new, exclusive v2 record is written. Existing
art/encounters/greene-cycle-validation.json is historical and is never touched.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import math
import re
import xml.etree.ElementTree as ET

ROOTS = {'neo': Path('D:/blacklight-evolution'), 'forge': Path('D:/blacklight-evolution-forge-1.20.1')}
SCHEMA = 2
PHASES = ['GUARDED', 'COLLAPSING', 'DOWN', 'RISING', 'SUBMERGING', 'UNDERGROUND', 'EMERGE_WAIT', 'EMERGING']
PHASE_PATH = [0, 1, 2, 3, 4, 5, 6, 7, 0]
STATUS = 'Captured two native-arrow eight-stage cycles; inspect geometry and frames'
MOTION = 'src/main/resources/assets/blacklight/models/encounter_elizabeth_greene_source.json'
VISUAL = 'src/greeneVisual/java/com/codex/blacklight/client/GreeneCycleVisual.java'
GREENE_TEST = 'src/greeneTest/java/com/codex/blacklight/gametest/GreeneCycleGameTest.java'
BODY_TEST = 'src/greeneTest/java/com/codex/blacklight/gametest/NativeBossBodyControlGameTest.java'
ENCOUNTER_TEST = 'src/encounterTest/java/com/codex/blacklight/gametest/EncounterGameTest.java'
UNIT_CLASSES = ['GreeneMotionTest', 'GreeneBattleHealthTest', 'GreeneRecoveryTimingTest']
FILES = ['src/main/java/com/codex/blacklight/' + p for p in (
    'combat/GreeneBattleCycle.java', 'combat/GreeneBattleHealth.java', 'combat/GreeneHealthEvents.java',
    'combat/GreeneBrood.java', 'combat/GreeneMotherHealth.java', 'combat/GreeneMotion.java',
    'combat/GreeneContact.java', 'combat/HydraContact.java', 'combat/SourceBossMotion.java',
    'combat/GreeneWeaponProgram.java', 'combat/GreeneWeaponController.java', 'combat/GreeneWeaponTarget.java',
    'entity/GreeneProjectile.java', 'entity/EncounterMob.java', 'entity/GreenePart.java',
    'client/model/SourceBossPose.java', 'mixin/HydraProjectileContactMixin.java',
    'mixin/GreeneHealthFloorMixin.java', 'config/GameplayBalanceConfig.java', 'network/ModNetwork.java')]
FILES += [MOTION, 'src/main/resources/assets/blacklight/geo/elizabeth_greene_mc.geo.json',
          'src/main/resources/assets/blacklight/models/greene_weapon_programs.json',
          'src/main/resources/assets/blacklight/models/encounter_source_actions.json',
          'src/main/resources/blacklight.mixins.json', GREENE_TEST, BODY_TEST, ENCOUNTER_TEST, VISUAL]
FILES += ['src/test/java/com/codex/blacklight/combat/' + name + '.java' for name in UNIT_CLASSES]
FILES += ['tools/record_greene_cycle.py', 'tools/greene-test.init.gradle', 'tools/greene-server.init.gradle',
          'tools/greene-visual.init.gradle', 'tools/encounter-test.init.gradle', 'tools/player-fix-unit.init.gradle']


def require(ok, why):
    if not ok:
        raise ValueError(why)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path):
    # Reject NaN/Infinity rather than letting a malformed metric bypass a bound.
    return json.loads(path.read_text(encoding='utf-8'), parse_constant=fail_json)


def fail_json(value):
    raise ValueError('Non-finite JSON number: ' + value)


def artifact(spec, qa):
    require(isinstance(spec, dict), 'Artifact needs a path and recorded SHA-256')
    path = Path(spec['path'])
    path = path.resolve() if path.is_absolute() else (qa / path).resolve()
    require(path.is_file(), 'Missing artifact: ' + str(path))
    digest = sha(path)
    require(spec.get('sha256') == digest, 'Artifact hash mismatch: ' + str(path))
    return path, {'path': str(path), 'sha256': digest}


def finite(value, name):
    require(type(value) in (int, float) and math.isfinite(value), 'Invalid number: ' + name)
    return value


def position(row):
    p = row['position']
    return tuple(finite(p[axis], 'position.' + axis) for axis in ('x', 'y', 'z'))


def method_names(path, annotation):
    source = path.read_text(encoding='utf-8')
    require(not re.search(r'@(?:Disabled|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b|\brequired\s*=\s*false', source),
            'Update recorder for non-plain required tests in ' + str(path))
    pattern = r'@' + annotation + r'\b(?:\([^)]*\))?\s+(?:public\s+)?(?:static\s+)?void\s+(\w+)\s*\('
    methods = re.findall(pattern, source)
    require(methods and len(methods) == len(set(methods)), 'Missing/duplicate test methods: ' + str(path))
    return set(methods)


def unit_evidence(specs, qa, root):
    require(isinstance(specs, list) and specs, 'Fresh JUnit XML artifacts are required')
    observed = {}; reports = []
    for spec in specs:
        path, ref = artifact(spec, qa); xml = ET.parse(path).getroot()
        cases = list(xml.iter('testcase'))
        require(cases, 'Empty JUnit report: ' + str(path))
        for suite in xml.iter('testsuite'):
            require(all(int(suite.get(key, '0')) == 0 for key in ('failures', 'errors', 'skipped')),
                    'JUnit failures/errors/skips: ' + str(path))
        for case in cases:
            require(not any(case.find(key) is not None for key in ('failure', 'error', 'skipped')),
                    'Unpassed JUnit case: ' + str(case.attrib))
            name = case.get('classname', '').rsplit('.', 1)[-1]
            observed.setdefault(name, set()).add(case.get('name', '').removesuffix('()'))
        reports.append({**ref, 'cases': len(cases)})
    for name in UNIT_CLASSES:
        expected = method_names(root / ('src/test/java/com/codex/blacklight/combat/' + name + '.java'), 'Test')
        require(expected <= observed.get(name, set()), 'Missing current JUnit cases: ' + name)
    return reports


def server_evidence(specs, qa, root, source_hashes):
    result = {}
    for run, required in [('greeneServer', {GREENE_TEST, BODY_TEST}), ('encounterServer', {ENCOUNTER_TEST})]:
        spec = specs[run]; sources = spec['sourceFiles']
        require(isinstance(sources, list) and len(sources) == len(set(sources)) and required <= set(sources),
                run + ' must declare its exact compiled fixture source files')
        names = []
        for relative in sources:
            require(re.fullmatch(r'src/(?:greeneTest|encounterTest)/java/[\w/]+GameTest\.java', relative) is not None,
                    'Unsupported GameTest source path: ' + relative)
            path = root / relative
            require(source_hashes.get(relative) == sha(path), 'Unbound GameTest source: ' + relative)
            names += [path.stem + '.' + method for method in sorted(method_names(path, 'GameTest'))]
        path, ref = artifact(spec['log'], qa); text = path.read_text(encoding='utf-8', errors='replace')
        require('BUILD SUCCESSFUL' in text and 'BUILD FAILED' not in text and
                re.search(r'\b[1-9]\d* (?:required|optional) tests failed\b', text, re.I) is None,
                'GameTest run did not complete cleanly: ' + run)
        passed = re.findall(r'All (\d+) required tests passed', text)
        require(len(passed) == 1 and int(passed[0]) == len(names),
                run + ' pass count does not cover the current declared methods (old 11/100 counts are not accepted)')
        result[run] = {'log': ref, 'requiredPassed': len(names), 'sourceMethods': names}
    return result


def targetable(stage, local):
    return local < 16 if stage == 4 else False if stage in (5, 6) else local >= 27 if stage == 7 else True


def scene_evidence(scene, states, frames, attempts, folder, pixels_per_unit, selected):
    rows = [row for row in states if row['scene'] == scene]
    images = [row for row in frames if row['scene'] == scene]
    shots = [row for row in attempts if row['scene'] == scene]
    require(rows and images, 'Missing scene ' + str(scene))
    phases = []; entries = []
    for row in rows:
        stage = row['stage']; local = finite(row['stageAge'], 'stageAge')
        require(type(stage) is int and 0 <= stage <= 7 and local >= 0, 'Bad stage clock')
        if not phases or phases[-1] != stage:
            phases.append(stage); entries.append(row)
    require(phases == PHASE_PATH, 'Incomplete/newly reordered eight-stage path: ' + str(phases))
    require(all(a['age'] < b['age'] for a, b in zip(rows, rows[1:])), 'Server sample ages are not strictly increasing')
    require(all(row['stageAge'] == 0 for row in entries[1:]), 'Server transition entries were not captured at their real local clock origin')
    for before, after in zip(rows, rows[1:]):
        if before['stage'] == after['stage']:
            require(abs((after['stageAge'] - before['stageAge']) - (after['age'] - before['age'])) < .001,
                    'Stage-local clock drift or a hidden reset')
    surface = position(rows[0]); max_health = finite(rows[0]['health'], 'initial health')
    require(max_health > 0, 'Initial healthy fixture is missing')
    depth = 20 * pixels_per_unit / 16
    restored = False
    for row in rows:
        stage, local = row['stage'], row['stageAge']; p = position(row)
        require(math.hypot(p[0] - surface[0], p[2] - surface[2]) < .02, 'Horizontal actor drift')
        require(surface[1] - depth - .05 <= p[1] <= surface[1] + .05, 'Root outside source Move displacement')
        restored |= stage == 6
        wave = 2 if restored else 1
        expected_armor = max_health * (700 if restored else 500) / 6000
        require(row['armourWave'] == wave and abs(finite(row['segmentMaximum'], 'segmentMaximum') - expected_armor) < .002,
                'Armor was hard-coded or restored in the wrong wave')
        require(row['waveCount'] == (1 if stage in (0, 1) and not restored else 2), 'Wave increment is not on DOWN entry')
        if stage in (1, 2, 3, 4, 5):
            require(row['mask'] == 7, 'Broken limbs regrew before emergence')
        if restored:
            require(row['mask'] == 0, 'Next-wave shield failed to restore')
        require(type(row['targetable']) is bool and row['targetable'] == targetable(stage, local), 'Wrong targetability gate')
        require(type(row['canAttack']) is bool and row['canAttack'] == (stage == 0 or stage == 7 and local >= 80),
                'Attack permission skipped source recovery/emergence gate')
        if stage in (5, 6):
            require(abs(p[1] - (surface[1] - depth)) < .05, 'Underground actor did not actually descend')
        if stage == 7 and local >= 27:
            require(abs(p[1] - surface[1]) < .05, 'Targetable emerging body did not return to surface')
    # Completion is published after local20; regular server rows use the global
    # ten-tick grid, so the last stored row may be local11..19, not exactly20.
    require(abs(position(rows[-1])[1] - surface[1]) < .05 and rows[-1]['stageAge'] >= 10, 'Final surface handoff is incomplete')
    require(entries[2]['age'] - entries[1]['age'] == 125 and entries[4]['age'] - entries[3]['age'] == 50,
            'Collapse/rise clocks differ from the source slices')
    require(75 <= entries[3]['age'] - entries[2]['age'] <= 2400, 'DOWN did not honor the supported selected hold')
    require(entries[5]['age'] - entries[4]['age'] == 59, 'Flat unobstructed source sink did not complete normally')
    require(entries[8]['age'] - entries[7]['age'] == 163, 'No-target visual scene skipped the full emergence clip')
    require(entries[6]['age'] - entries[4]['age'] == 400, 'Underground wait did not start with submerge')
    require(entries[7]['age'] - entries[6]['age'] == 6, 'Unblocked emergence wait gate differs')
    require([s['bone'] for s in shots] == ['weak_0', 'weak_1', 'weak_2', 'head'], 'Missing native-arrow limb/body contacts')
    for shot in shots[:3]:
        require(abs(shot['arrowBaseDamage'] - (shot['segmentMaximum'] + 1) / 6 * 1.25) < .001,
                'Weakpoint arrow did not scale with its current armor tier')
    head_age = shots[-1]['age']
    require(any(row['stage'] == 2 and row['age'] <= head_age for row in rows) and
            rows[-1]['health'] < next(row['health'] for row in rows if row['stage'] == 2), 'Native DOWN hit lacks observable health loss')
    require({row['stage'] for row in images} == set(range(8)), 'Client did not capture every stage')
    maximum = 0
    for row in images:
        local = finite(row['stageAge'], 'client stageAge'); stage = row['stage']; geometry = row['geometry']
        require('skinToReceiverMax' not in geometry, 'Historical geometry schema cannot pass v2')
        value = finite(geometry['skinToSourceSurfaceMax'], 'skinToSourceSurfaceMax')
        require(0 <= value < .0001 and geometry['vertices'] > 0, 'Rendered source surface mismatch or empty capture')
        count = geometry['receivingBoxes']
        require(type(count) is int and count >= 0 and type(row['targetable']) is bool and row['targetable'] == targetable(stage, local), 'Invalid client receiver gate')
        require((count > 0) == row['targetable'], 'Invisible underground body kept receiving boxes, or visible body lost them')
        maximum = max(maximum, value)
    # The visual scene has no committed target: natural 163-tick completion is
    # observable here. The 80-tick attack takeover is covered by GameTests.
    picked = [row for row in images if row['image'] in selected]
    require({row['stage'] for row in picked} == set(range(8)), 'Manual review must cover all client stages in each heading')
    final_age = entries[-1]['age']
    require(any(row['stage'] == 7 and row['stageAge'] >= 159 for row in picked) and
            any(row['stage'] == 0 and row['age'] >= final_age and row['stageAge'] < 4 for row in picked) and
            any(row['stage'] == 0 and row['age'] >= final_age and row['stageAge'] >= 8 for row in picked),
            'Manual review lacks outgoing emergence / four-tick handoff / settled guard images')
    matrices = []
    for stage in range(8):
        path = folder / f'matrices-{scene}-stage-{stage}.json'; data = read_json(path)
        require(data and len({row['bone'] for row in data}) == len(data), 'Missing/duplicate per-stage matrix rows')
        for row in data:
            for key in ('actual', 'expected'):
                require(len(row[key]) == 16, 'Incomplete matrix')
                for value in row[key]:
                    finite(value, 'matrix.' + key)
        matrices.append({'path': str(path), 'sha256': sha(path), 'bones': len(data)})
    return {'scene': scene, 'phasePath': phases, 'samples': len(rows), 'frames': len(images),
            'sourceDepthBlocks': depth, 'skinToSourceSurfaceMax': maximum, 'matrices': matrices}


def native_evidence(spec, qa, root):
    evidence_path, evidence_ref = artifact(spec['evidence'], qa)
    status_path, status_ref = artifact(spec['status'], qa)
    require(status_path.read_text(encoding='utf-8').strip() == STATUS, 'Not a completed NEW eight-stage visual run')
    data = read_json(evidence_path)
    require(data.get('schema') == SCHEMA and not data.get('error'), 'Old four-stage or failed visual evidence is not accepted')
    frames, states, attempts = data['frames'], data['states'], data['attempts']
    for collection in (frames, states, attempts):
        require(collection and {row['scene'] for row in collection} == {0, 1}, 'Exactly two heading scenes are required')
    names = [row['image'] for row in frames]
    require(len(names) == len(set(names)) and all(Path(name).name == name and name.endswith('.png') for name in names), 'Invalid image names')
    folder = evidence_path.parent; review = spec['visualReview']
    require(review.get('passed') is True and str(review.get('reviewedBy', '')).strip() and str(review.get('notes', '')).strip(),
            'Explicit visual inspection and notes are required; numerical geometry alone is not player acceptance')
    selected = {}
    for image in review['selectedFrames']:
        name = image['image']; require(name in names and name not in selected, 'Unknown/duplicate reviewed frame')
        path, ref = artifact({'path': str(folder / name), 'sha256': image['sha256']}, qa)
        require(path.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n', 'Reviewed image is not a PNG')
        selected[name] = ref
    for name in names:
        require((folder / name).is_file(), 'Missing captured image: ' + name)
    motion = read_json(root / MOTION); scale = finite(motion['pixelsPerSourceUnit'], 'pixelsPerSourceUnit')
    require(scale > 0, 'Missing source unit conversion')
    scenes = [scene_evidence(scene, states, frames, attempts, folder, scale, selected) for scene in (0, 1)]
    return {'evidence': evidence_ref, 'status': status_ref, 'scenes': scenes,
            'reviewedBy': review['reviewedBy'], 'reviewNotes': review['notes'], 'selectedReviewedImages': list(selected.values())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--qa', type=Path, default=Path('D:/CodexCache/work/greene-cycle-v2'))
    parser.add_argument('--output-name', default='greene-cycle-validation-v2.json')
    args = parser.parse_args(); qa = args.qa.resolve()
    require(re.fullmatch(r'greene-cycle-validation-v2(?:-[\w-]+)?\.json', args.output_name) is not None,
            'Output must use a separate v2 basename; historical records cannot be overwritten')
    destinations = [root / 'art/encounters' / args.output_name for root in ROOTS.values()]
    require(not any(path.exists() for path in destinations), 'Record already exists; select a new --output-name, never overwrite prior acceptance')
    review_path = qa / 'review.json'; review = read_json(review_path)
    require(review.get('schema') == SCHEMA, 'A new schema-2 run/review manifest is required; old review.json is historical')
    result = {'schema': SCHEMA, 'recordedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'scope': 'Eight-stage standalone Greene recovery, native arrows, dynamic armor, receiving gates and natural emergence handoff',
              'fullGoalComplete': False, 'phases': dict(enumerate(PHASES)), 'platforms': {},
              'reviewManifest': {'path': str(review_path), 'sha256': sha(review_path)},
              'limits': ['Two headings on flat disposable terrain; source-unit root displacement, not full native collision solver restoration.',
                         'Natural completion is observed visually; earlier attack handoff and save/callback behavior rely on the separately recorded current GameTests.',
                         'Brood spawning is restricted out of the visual arena. No inference of complete reinforcements, all source attacks, death choreography or Crater effects.',
                         'Manual selected-frame review is required. No audio, physical-input playthrough, multiplayer or changed-terrain acceptance is inferred.',
                         'Run manifest binds externally captured source/artifact hashes; this recorder does not execute or independently attest the loaded runtime.']}
    for platform, root in ROOTS.items():
        spec = review['platforms'][platform]; fingerprints = spec['sourceSha256']
        require(all(fingerprints.get(path) == sha(root / path) for path in FILES), 'Stale/missing build-run source fingerprint: ' + platform)
        result['platforms'][platform] = {'sourceSha256': {path: fingerprints[path] for path in FILES},
                                       'unit': unit_evidence(spec['unitXml'], qa, root),
                                       'servers': server_evidence(spec['servers'], qa, root, fingerprints),
                                       'native': native_evidence(spec, qa, root)}
    encoded = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    for path in destinations:
        with path.open('x', encoding='utf-8', newline='\n') as stream:
            stream.write(encoded)
    print('Recorded new eight-stage Greene evidence:', hashlib.sha256(encoded.encode()).hexdigest())


if __name__ == '__main__':
    main()
