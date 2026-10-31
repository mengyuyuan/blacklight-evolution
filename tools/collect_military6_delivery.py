"""Verify frozen, actual M6 evidence before creating a NEW local delivery. Never builds, installs or uploads."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath, PureWindowsPath
import re
import shutil
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET
from zipfile import ZIP_DEFLATED, ZipFile

VERSION = '0.3.4-preview.20261028-military.6.1'
AUTHOR_HEADS = {'neoforge': '996dffdf2d56091cbb01ed9f871bc440ed571a1a',
                'forge': '59f8fb10f2b79473fd81dd65e9f2fea3c4b6bee4'}
PLATFORMS = {'neoforge': ('1.21.1', 'NeoForge'), 'forge': ('1.20.1', 'Forge')}
M5_NAME = 'military5-reference-homes-preview-20261026'
M5_ZIP_SHA = 'fb7abd664b7c1dfeea840fcf699112678e0a9ba90fa30718ebe0c2b8d89cc01e'
M6_NAME = 'military6-vehicles-preview-20261028'
M6_ZIP_SHA = '6b22bc4d17253a33066bdf18f78f68377a73ba4a733dca89b345149fe3f5f991'
VEHICLE_AUTHORING_SHA = '4ccafec7230accdac6d2d84c7ffbd043f2ece8d2b2848e9fe9eb98aa13523a21'
ORIGINAL_BB_SHA = '4d1b2213f160aa7a5ece31131c77102d654474277b46a6f93c8624cba71b4696'
FORGE_MIXINEXTRAS_SHA = '9d48cb0a40299d283248fdad8b02c6d175c45b27f9bec48ef63d7ee8a4ee3066'
SOURCE_ROOT_FILES = {'README.md', 'LICENSE', 'THIRD_PARTY_ASSETS.md', 'THIRD_PARTY_NOTICES.md',
                      'CONTRIBUTING.md', 'TEMPLATE_LICENSE.txt', 'build.gradle', 'settings.gradle',
                      'gradle.properties', 'gradlew', 'gradlew.bat', '.gitignore', '.gitattributes'}
REVIEWED_RUNTIME_PATHS = {'logs/debug-1.log.gz', 'logs/debug-2.log.gz'}
VEHICLE_ENTITIES = tuple(f'{faction}_{chassis}' for faction in ('military', 'blackwatch') for chassis in ('truck', 'apc', 'tank'))
VEHICLE_RECIPES = (*[name+'_chassis' for name in VEHICLE_ENTITIES],
                   'vehicle_repair_parts', 'autocannon_round', 'tank_ap_shell', 'tank_he_shell')
VEHICLE_CLASSES = ('com/codex/blacklight/entity/MilitaryVehicle.class',
                   'com/codex/blacklight/entity/VehicleHullPart.class',
                   'com/codex/blacklight/firearms/VehicleOrdnanceProjectile.class',
                    'com/codex/blacklight/mixin/VehicleProjectileCollisionMixin.class',
                    'com/codex/blacklight/mixin/VehicleEntityCollisionMixin.class',
                    'com/codex/blacklight/mixin/client/VehiclePlayerPoseMixin.class',
                    'com/codex/blacklight/military/vehicle/VehicleLogisticsService.class',
                    'com/codex/blacklight/military/vehicle/VehicleSupplyRules.class',
                   'com/codex/blacklight/military/vehicle/VehicleGarageData.class',
                   'com/codex/blacklight/client/model/MilitaryVehicleModel.class')
REQUIRED_COVERAGE = {'military5-regression', 'military6-vehicles'}
GAME_SOURCE_SETS = {'military5-regression': ('blackwatchTest', 'cityMilitaryTest', 'officersTest', 'garrisonTest',
                                             'terrainTest', 'homeTest', 'identityMilitaryTest'),
                    'military6-vehicles': ('vehicleTest',)}
REQUIRED_JUNIT_SUITES = {'BlackwatchReferenceProfileTest', 'BlackwatchRigPoseTest', 'FirearmSupportPoseTest',
                         'FirearmHandPoseTest', 'FirearmViewStateTest', 'MilitaryBaseHealthTest',
                         'MilitaryVehicleModelTest', 'VehicleRiderPoseTest', 'VehicleGarageNaturalPlanTest',
                          'VehicleControlPayloadTest', 'VehiclePresentationTest', 'VehicleFuelDeactivationTest',
                          'VehiclePhysicalContactTest', 'VehicleCollisionGeometryTest', 'VehicleSupplyRulesTest'}
CLIENT_VIEW_SOURCES = tuple('src/main/java/com/codex/blacklight/'+name for name in (
    'client/vehicle/VehicleCamera.java', 'client/vehicle/VehicleSeatPose.java', 'client/vehicle/VehicleViewRendering.java',
    'mixin/client/VehiclePlayerPoseMixin.java', 'mixin/client/PrototypeChaseCameraMixin.java',
    'client/camera/PrototypeChaseCamera.java', 'client/animation/BlacklightRenderPose.java',
    'client/model/BlacklightPlayerModel.java', 'client/model/MercerPlayerMesh.java', 'client/model/BlackwatchModel.java'))+(
    'src/vehicleVisualTest/java/com/codex/blacklight/client/vehicle/VehicleViewSmoke.java',
    'src/main/resources/blacklight.mixins.json', 'art/military-vehicles/vehicle-authoring.json')


def require(condition: bool, message: str) -> None:
    if not condition:raise RuntimeError(message)


def sha(path: Path) -> str:
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024*1024), b''):value.update(block)
    return value.hexdigest()


def git(repo: Path, *args: str) -> bytes:
    return subprocess.check_output(['git', '-C', str(repo), *args])


def within(root: Path, relative: str) -> Path:
    require(isinstance(relative, str) and relative and '\\' not in relative and ':' not in relative,
            'Invalid relative evidence/source path')
    parts = PurePosixPath(relative)
    require(not parts.is_absolute() and not PureWindowsPath(relative).is_absolute() and '..' not in parts.parts,
            f'Parent traversal or absolute path forbidden: {relative}')
    path = root.joinpath(*parts.parts)
    require(path.resolve().is_relative_to(root.resolve()) and not any(p.is_symlink() for p in (path, *path.parents)),
            f'Unsafe symlink or escaped path: {relative}')
    return path


def read_json(path: Path) -> dict:
    require(path.is_file() and not path.is_symlink(), f'Missing or unsafe JSON manifest/evidence: {path}')
    value = json.loads(path.read_text(encoding='utf-8-sig'))
    require(isinstance(value, dict), f'Expected JSON object: {path}')
    return value


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


def recorded_file(root: Path, entry: dict) -> Path:
    require(isinstance(entry, dict) and re.fullmatch('[0-9a-f]{64}', str(entry.get('sha256', ''))), 'Invalid evidence SHA256 record')
    path = within(root, entry.get('path', ''))
    require(path.is_file() and sha(path) == entry['sha256'], f'Missing or changed recorded file: {path}')
    return path


def assert_files(root: Path, files: dict[str, str]) -> None:
    require(isinstance(files, dict) and files, f'Empty file freeze: {root}')
    for name, digest in files.items():recorded_file(root, {'path': name, 'sha256': digest})


def load_verified_manifest(path: Path) -> dict:
    value = read_json(path)
    require(value.get('schemaVersion') == 1 and value.get('version') == VERSION
            and value.get('status') == 'VERIFIED' and value.get('finalSourceFreeze') is True,
            'Verified M6 manifest requires schemaVersion=1, exact version, VERIFIED and finalSourceFreeze=true')
    return value


def log_text(path: Path) -> str:
    return path.read_bytes().decode('utf-8', errors='replace')


def saving_shutdown(text: str) -> bool:
    return ('BUILD SUCCESSFUL' in text and 'Stopping server' in text
            and re.search(r'All dimensions (?:are )?saved', text) is not None)


def version_gate(repo: Path, entry: dict, primary_text: str) -> list[dict]:
    supplements = entry.get('versionLogs', [])
    require(isinstance(supplements, list), 'versionLogs must be a list of actual same-run logs')
    run = within(repo, entry.get('runDirectory', ''))
    texts = [primary_text]
    for supplement in supplements:
        path = recorded_file(repo, supplement)
        require(path.parent.resolve() == (run/'logs').resolve() and path.name in ('debug.log', 'latest.log'),
                'Supplemental version evidence must be debug/latest.log from the same run directory')
        texts.append(log_text(path))
    require(any(VERSION in text for text in texts), 'Actual run logs do not identify the current M6 runtime version')
    return supplements


def game_gate(repo: Path, entry: dict) -> dict:
    expected = entry.get('expectedTests')
    require(type(expected) is int and expected > 0, 'Exact observed GameTest count is required')
    path = recorded_file(repo, entry['log']);text = log_text(path)
    totals = re.findall(r'(\d+) GAME TESTS COMPLETE', text)
    passed = re.findall(r'All (\d+) required tests passed', text)
    require(len(totals) == len(passed) == 1, f'GameTest evidence must contain a single complete run: {path}')
    require(totals and passed and int(totals[-1]) == expected and int(passed[-1]) == expected
            and not re.search(r'\b[1-9]\d* required tests failed', text), f'Incomplete or failed exact {expected} GameTests: {path}')
    require(saving_shutdown(text) and 'Game test server shutting down' in text, f'GameTest lacks normal saving shutdown: {path}')
    version_gate(repo, entry, text)
    batches = re.findall(r"\[minecraft/GameTest(?:Batch)?Runner\]: Running test batch '([^']+):(\d+)' \((\d+) tests\)\.\.\.", text)
    require(batches and len({(name, index) for name, index, _ in batches}) == len(batches)
            and all(int(count) > 0 for _, _, count in batches) and sum(int(count) for _, _, count in batches) == expected,
            f'Missing, duplicate or inconsistent actual GameTest batches: {path}')
    counts = {}
    for name, _, count in batches:counts[name] = counts.get(name, 0)+int(count)
    return {'label': entry['label'], 'tests': expected, 'requiredPassed': expected,
             'normalSavingShutdown': True, 'log': entry['log'], 'versionLogs': entry.get('versionLogs', []),
            'batchCounts': counts}


def games_gate(repo: Path, entries: list[dict]) -> list[dict]:
    require(isinstance(entries, list) and entries, 'Actual M5/M6 GameTest runs are required')
    logs = set();hashes = set();runs = set()
    for entry in entries:
        log = within(repo, entry['log']['path']).resolve();run = within(repo, entry['runDirectory']).resolve()
        digest = entry['log']['sha256']
        require(log not in logs and digest not in hashes and run not in runs,
                'Duplicate GameTest evidence: log path, SHA256 and runDirectory must each be unique')
        logs.add(log);hashes.add(digest);runs.add(run)
    suites = {}
    for suite, source_sets in GAME_SOURCE_SETS.items():
        counts = {}
        for source_set in source_sets:
            files = sorted((repo/'src'/source_set/'java').rglob('*.java'))
            require(files, f'Missing frozen GameTest source set: {source_set}')
            for path in files:
                for attributes in re.findall(r'@GameTest\s*\((.*?)\)', path.read_text(encoding='utf-8'), re.DOTALL):
                    batch = re.search(r'\bbatch\s*=\s*"([^"]+)"', attributes)
                    name = batch.group(1) if batch else 'defaultBatch'
                    counts[name] = counts.get(name, 0)+1
        require(sum(counts.values()) >= (132 if suite == 'military5-regression' else 1), f'Incomplete frozen GameTest suite: {suite}')
        suites[suite] = counts
    games = []
    for entry in entries:
        game = game_gate(repo, entry)
        game['covers'] = sorted(suite for suite, counts in suites.items()
                                if all(game['batchCounts'].get(name) == count for name, count in counts.items()))
        require(set(entry.get('covers', game['covers'])) == set(game['covers']),
                'Declared GameTest coverage differs from actual complete frozen suite batches')
        games.append(game)
    require(REQUIRED_COVERAGE <= {suite for game in games for suite in game['covers']},
            'Actual GameTest batch coverage must include the complete M5 regression and current M6 suite')
    return games


def junit_gate(repo: Path, entry: dict) -> dict:
    expected = entry.get('expectedTests');files = entry.get('xmlFiles', {})
    require(type(expected) is int and expected > 0, 'Exact observed JUnit count is required')
    actual = {p.relative_to(repo).as_posix() for p in (repo/'build/test-results/test').glob('TEST-*.xml')}
    require(actual and actual == files.keys(), 'Final JUnit XML inventory is incomplete or changed')
    assert_files(repo, files);log = recorded_file(repo, entry['log'])
    require('BUILD SUCCESSFUL' in log_text(log), 'Final complete JUnit Gradle run did not succeed')
    result = dict(tests=0, failures=0, errors=0, skipped=0);suites = []
    for name in sorted(files):
        root = ET.parse(within(repo, name)).getroot()
        require(root.tag == 'testsuite', f'Unexpected JUnit XML root: {name}')
        suites.append(root.get('name', ''))
        for key in result:result[key] += int(root.get(key, '0'))
    require(result['tests'] == expected and not any(result[key] for key in ('failures', 'errors', 'skipped')),
            f'Final full JUnit is not exactly {expected}/zero failures/errors/skips: {result}')
    return {**result, 'suiteNames': suites, 'log': entry['log'], 'xmlFiles': files}


def garage_gate(repo: Path, entry: dict, platform: str) -> dict:
    report = read_json(recorded_file(repo, entry['json']))
    require(report.get('schemaVersion') == 1 and report.get('status') == 'PASS'
            and report.get('loader') == platform+'-'+PLATFORMS[platform][0], 'Actual garage audit is not a current-loader second-process PASS')
    first, second = report.get('preparePid'), report.get('verifyPid')
    require(type(first) is int and type(second) is int and first > 0 and second > 0 and first != second,
            'Garage durability requires two different operating-system processes')
    require(report.get('apcActuallyUnloaded') is True and report.get('infantryFiniteReloads', 0) > 0,
            'Actual unload/finite infantry reload evidence is incomplete')
    for key in ('baseId', 'garageId', 'dimension', 'slots', 'nativePersonnelSlots', 'vehicles', 'people', 'containers', 'marker', 'lostTankUuid', 'unknownApcUuid'):
        require(report.get(key), f'Actual garage audit lacks saved {key}')
    require(len(report['vehicles']) == 2 and set(report['slots']) == {'truck', 'apc', 'tank'}, 'Garage saved vehicle/slot inventory is incomplete')
    require(datetime.fromisoformat(report['verifiedUtc'].replace('Z', '+00:00')) >
            datetime.fromisoformat(report['prepareCheckpointUtc'].replace('Z', '+00:00')), 'Restart verification does not follow its checkpoint')
    run = within(repo, entry['runDirectory'])
    require(within(repo, entry['json']['path']).parent.resolve() == run.resolve(), 'Garage evidence belongs to another run directory')
    for key, marker in (('prepareLog', 'PHASE1_SAVED'), ('verifyLog', 'PASS')):
        text = log_text(recorded_file(repo, entry[key]))
        require(f'VEHICLE_GARAGE_AUDIT {marker}' in text and saving_shutdown(text)
                and 'VEHICLE_GARAGE_AUDIT FAIL' not in text, f'Missing actual garage {marker} saving shutdown')
        version_gate(repo, entry, text)
    return {**entry, 'status': report['status'], 'preparePid': first, 'verifyPid': second, 'report': report}


def allowed_source(name: str) -> bool:
    parts = {part.lower() for part in name.replace('\\', '/').split('/')}
    forbidden = {'.git', '.codex', '.agents', '.aws', '.env', 'secrets', 'pcl', 'saves',
                 'vehicle-reference-cache', 'reference-cache', '__pycache__'}
    if parts & forbidden or any(part.endswith(('.pem', '.key')) for part in parts):return False
    if 'world' in parts and '/src/' not in '/'+name or any(part.startswith('.env') for part in parts):return False
    return name in SOURCE_ROOT_FILES or name.startswith(('src/', 'tools/', 'docs/', 'art/', 'gradle/', '.github/'))


def changed_paths(repo: Path) -> list[str]:
    names = git(repo, 'diff', '--name-only', '-z', 'HEAD') + git(repo, 'ls-files', '--others', '--exclude-standard', '-z')
    changed = sorted({name.decode('utf-8') for name in names.split(b'\0') if name} - REVIEWED_RUNTIME_PATHS)
    require(all(allowed_source(name) for name in changed), 'Unexpected non-source/private changed paths require review: '+', '.join(name for name in changed if not allowed_source(name)))
    require(not git(repo, 'diff', '--name-only', '--diff-filter=U'), 'Unresolved Git conflicts')
    return changed


def source_inventory(repo: Path) -> dict[str, str]:
    names = git(repo, 'ls-files', '-z') + git(repo, 'ls-files', '--others', '--exclude-standard', '-z')
    inventory = {};checked_parents = set()
    for name in sorted({s.decode('utf-8') for s in names.split(b'\0') if s}):
        if not allowed_source(name):continue
        relative = PurePosixPath(name)
        require(not relative.is_absolute() and '..' not in relative.parts and '\\' not in name and ':' not in name,
                f'Unsafe Git source name: {name}')
        parent = relative.parent.as_posix()
        if parent not in checked_parents:
            within(repo, parent);checked_parents.add(parent)
        path = repo/name
        require(not path.is_symlink(), f'Source symlink needs explicit review: {name}')
        if path.is_file():inventory[name] = sha(path)
    return inventory


def source_archive(repo: Path, destination: Path, inventory: dict[str, str]) -> None:
    require(source_inventory(repo) == inventory, f'Source freeze changed before archive: {repo}')
    with ZipFile(destination, 'x', ZIP_DEFLATED, compresslevel=6) as archive:
        for name in inventory:archive.writestr(name, (repo/name).read_bytes())
        archive.writestr('SOURCE_MANIFEST.json', json.dumps(inventory, ensure_ascii=False, indent=2)+'\n')
    require(source_inventory(repo) == inventory, 'Source freeze changed while writing full source archive')
    with ZipFile(destination) as archive:
        require(archive.testzip() is None and all(hashlib.sha256(archive.read(name)).hexdigest() == digest for name, digest in inventory.items()), 'Source ZIP verification failed')


def source_overlay(repo: Path, destination: Path) -> dict:
    changed = changed_paths(repo);files = {};deleted = []
    for name in changed:
        path = within(repo, name)
        if path.is_file():files[name] = sha(path)
        else:deleted.append(name)
    result = {'head': git(repo, 'rev-parse', 'HEAD').decode().strip(), 'branch': git(repo, 'branch', '--show-current').decode().strip(),
              'featureCommit': False, 'files': files, 'deleted': deleted,
              'instructions': 'HEAD is the author base, not an M6 feature commit. Overlay staged, unstaged and untracked bytes; review deletions. No git add/commit/push/fetch was performed.'}
    with ZipFile(destination, 'x', ZIP_DEFLATED, compresslevel=6) as archive:
        for name in files:archive.writestr(name, within(repo, name).read_bytes())
        archive.writestr('PATCH-BASE.json', json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    require(changed_paths(repo) == changed, 'Changed source list moved during collection')
    if files:assert_files(repo, files)
    return result


def vehicle_jar_gate(jar: Path, platform: str) -> dict:
    with ZipFile(jar) as archive:
        names = archive.namelist();require(archive.testzip() is None and len(names) == len(set(names)), 'Corrupt or duplicate JAR entries')
        require(not any('/gametest/' in name.lower() or '/preview/' in name.lower() or 'ServerAudit' in name
                        or re.search(r'(?:VehicleViewSmoke|PlayerGL\w*Smoke|VehiclePlayerGL\w*)(?:\$|\.|/)', name, re.I)
                        or name.startswith('blacklight/test/') or name.lower().startswith('software/bernie/geckolib/') for name in names),
                'QA runtime, GeckoLib or preview leaked into main JAR')
        nested = {name for name in names if name.lower().endswith('.jar')}
        metadata = 'META-INF/jarjar/metadata.json';dependency = 'META-INF/jarjar/mixinextras-forge-0.4.1.jar'
        if nested or metadata in names:
            require(platform == 'forge' and nested == {dependency} and metadata in names,
                    'Only the declared Forge MixinExtras jarJar nested dependency is allowed')
            declaration = json.loads(archive.read(metadata))
            require(declaration == {'jars': [{
                'identifier': {'group': 'io.github.llamalad7', 'artifact': 'mixinextras-forge'},
                'version': {'range': '[0.4.1,)', 'artifactVersion': '0.4.1'},
                'path': dependency, 'isObfuscated': False}]} and declaration['jars'][0]['isObfuscated'] is False,
                'Forge jarJar metadata must exactly declare production MixinExtras 0.4.1')
            require(hashlib.sha256(archive.read(dependency)).hexdigest() == FORGE_MIXINEXTRAS_SHA,
                    'Forge nested MixinExtras 0.4.1 bytes differ from the production dependency')
        mixins = json.loads(archive.read('blacklight.mixins.json'))
        registrations = mixins.get('mixins', [])+mixins.get('client', [])+mixins.get('server', [])
        require(registrations.count('VehicleProjectileCollisionMixin') == 1, 'Vehicle projectile mixin must register exactly once')
        for name, section in (('VehicleEntityCollisionMixin', 'mixins'), ('client.VehiclePlayerPoseMixin', 'client')):
            require(registrations.count(name) == 1 and mixins.get(section, []).count(name) == 1,
                    f'{name} must register exactly once in {section}')
        directory = 'recipe' if platform == 'neoforge' else 'recipes'
        require(all(f'data/blacklight/{directory}/{name}.json' in names for name in VEHICLE_RECIPES), 'Missing one of the ten active M6.1 item recipes')
        require(not any(f'data/blacklight/{folder}/military_fuel_can.json' in names for folder in ('recipe', 'recipes')),
                'Dormant fuel recipe must be absent from the JAR')
        require(set(VEHICLE_CLASSES) <= set(names), 'Missing production vehicle/garage/render/projectile classes')
        for resource in ('assets/blacklight/military/vehicle-models.json', 'data/blacklight/military/vehicle-mounts.json'):
            require(json.loads(archive.read(resource)).get('sourceSha256') == VEHICLE_AUTHORING_SHA,
                    'Vehicle model/mount authoring differs from the M6.1 size contract')
        entities = archive.read('com/codex/blacklight/registry/ModEntities.class')
        require(all(name.encode() in entities for name in VEHICLE_ENTITIES), 'Six production vehicle entity identifiers are absent from compiled registry')
    return {'entityIds': list(VEHICLE_ENTITIES), 'recipes': list(VEHICLE_RECIPES), 'dormantFuelRecipeAbsent': True,
            'projectileMixinRegistrations': 1, 'entityCollisionMixinRegistrations': 1, 'playerPoseMixinRegistrations': 1, 'zipCrc': 'PASS'}


def author_gate(repo: Path, jar: Path, platform: str) -> dict:
    ref = AUTHOR_HEADS[platform];inventory = {};blobs = {};entries = []
    tree = git(repo, 'ls-tree', '-r', '-z', ref, '--', 'src/main/resources', 'art/motion-profile', 'art/motion-profile-manifest.json')
    for record in tree.split(b'\0'):
        if not record:continue
        header, raw = record.split(b'\t', 1);name = raw.decode('utf-8')
        if not ('/native_animations/' in name or name.startswith('art/motion-profile/') or name == 'art/motion-profile-manifest.json'):continue
        require(header.split()[:2] == [b'100644', b'blob'], f'Unsafe author resource: {name}')
        entries.append((name, header.split()[2]))
    batch = subprocess.run(['git', '-C', str(repo), 'cat-file', '--batch'], input=b''.join(oid+b'\n' for _, oid in entries),
                           capture_output=True, check=True).stdout
    offset = 0
    with ZipFile(jar) as archive:
        for name, oid in entries:
            end = batch.index(b'\n', offset);header = batch[offset:end].split()
            require(header[:2] == [oid, b'blob'], 'Unexpected Git author blob response')
            length = int(header[2]);data = batch[end+1:end+1+length];offset = end+length+2
            actual = within(repo, name).read_bytes()
            blobs[name] = hashlib.sha256(data).hexdigest()
            require(actual == data or actual == git(repo, 'cat-file', '--filters', ref+':'+name), f'Author resource changed: {name}')
            if '/native_animations/' in name:require(archive.read(name.removeprefix('src/main/resources/')) == actual, f'Author native resource stale in JAR: {name}')
            if name.startswith('art/motion-profile/') and name.endswith(('.json', '.data')):
                require(archive.read('blacklight/bundled_motions/'+Path(name).name) == actual, f'Bundled author motion stale in JAR: {name}')
            inventory[name] = hashlib.sha256(actual).hexdigest()
    profile = read_json(repo/'art/motion-profile-manifest.json')
    require(len(profile.get('files', {})) == 253 and any('/native_animations/' in name for name in inventory), 'Incomplete 253 author motion/native resource inventory')
    return {'authorHead': ref, 'authorBlobSha256': blobs, 'checkoutSha256': inventory,
            'checkoutPolicy': 'Exact author blob or exact Git --filters checkout bytes; JAR must match checkout', 'motionFiles': 253}


def preview_gate(repo: Path, entry: dict) -> dict:
    path = recorded_file(repo, entry['manifest']);value = read_json(path)
    require(value.get('kind') == 'actual-production-ModelPart-CPU-preview' and value.get('not_game_screenshot') is True
            and value.get('scenes') == 8 and len(value.get('files', {})) == 9, 'Expected eight actual CPU ModelPart scenes plus overview')
    assert_files(path.parent, value['files']);assert_files(repo, entry['sourceFiles'])
    required = {'tools/VehicleMeshCapture.java', 'src/main/java/com/codex/blacklight/client/model/MilitaryVehicleModel.java',
                'src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java',
                'src/main/resources/assets/blacklight/textures/entity/military_vehicles_blackwatch.png',
                'src/main/resources/assets/blacklight/textures/entity/military_vehicles_military.png'}
    require(required <= entry['sourceFiles'].keys(), 'Vehicle preview lacks frozen actual capture/model/texture sources')
    capture = recorded_file(repo, entry['capture'])
    require(sha(capture) == value.get('capture_sha256'), 'CPU preview does not match actual captured production vertices')
    require(path.stat().st_mtime_ns >= max(within(repo, name).stat().st_mtime_ns for name in entry['sourceFiles']),
            'Actual preview sources changed after CPU capture')
    return {**entry, 'scenes': 8, 'pngFiles': 9, 'notGameScreenshot': True}


def protected_deliveries(workspace: Path, expected: dict[str, str]) -> None:
    for name, digest in ((M5_NAME, M5_ZIP_SHA), (M6_NAME, M6_ZIP_SHA)):
        require(expected.get(name+'.zip') == digest and all(name+'/'+file in expected for file in ('MANIFEST.json', 'SHA256SUMS.txt')),
                f'Protected {name} ZIP, MANIFEST and SHA256SUMS must be explicitly pinned')
    assert_files(workspace, expected)
    for name in (M5_NAME, M6_NAME):
        directory = workspace/name;baseline = read_json(directory/'MANIFEST.json')
        assert_files(directory, baseline['files'])
        require({path.relative_to(directory).as_posix() for path in directory.rglob('*') if path.is_file()}
                == set(baseline['files']) | {'MANIFEST.json', 'SHA256SUMS.txt'}, f'Frozen {name} directory inventory changed')
        sums = {}
        for line in (directory/'SHA256SUMS.txt').read_text(encoding='utf-8-sig').splitlines():
            digest, file = line.split('  ', 1)
            require(file not in sums, f'Duplicate frozen checksum: {name}/{file}')
            recorded_file(directory, {'path': file, 'sha256': digest});sums[file] = digest
        require(sums == {**baseline['files'], 'MANIFEST.json': expected[name+'/MANIFEST.json']}, f'Frozen {name} checksum inventory changed')


def client_view_gate(repo: Path, entry: dict | None) -> dict:
    require(isinstance(entry, dict) and all(key in entry for key in ('report', 'images', 'review', 'sourceFiles', 'log', 'runDirectory')),
            'Required real Player GL client report, images and root visual review are missing')
    run = within(repo, entry['runDirectory'])
    require(run.parent == repo/'.superpowers' and run.name.startswith('run-military6-client-'), 'GL must use an independent run-military6-client-* directory')
    path = recorded_file(repo, entry['report']);report = read_json(path)
    require(path == run/'vehicle-view-evidence/vehicle-view-report.json', 'GL report belongs to another client run')
    require(report.get('complete') is True and report.get('automaticPass') is True and not report.get('error')
            and report.get('requiresVisualReview') is True and report.get('arbitrarySkinRendererCompatibilityVerified') is False
            and report.get('source') == 'real integrated-server Player, synchronized seat, production Camera, native F5 queue and GL framebuffer',
            'GL report is incomplete or is not the actual real Player framebuffer smoke')
    frames = report.get('frames', [])
    require(report.get('plannedFrames') == 50 and len(frames) == 50, 'GL must contain all 50 actual planned frames')
    images = [frame.get('image', '') for frame in frames]
    require(all(name and PurePosixPath(name).name == name and name.endswith('.png') for name in images)
            and len(set(images)) == len(images), 'Invalid or duplicate GL frame image names')
    expected = {(path.parent/name).relative_to(repo).as_posix() for name in images}
    require(isinstance(entry['images'], dict) and set(entry['images']) == expected, 'GL image freeze differs from the actual report frames')
    assert_files(repo, entry['images'])
    from PIL import Image
    for name in entry['images']:
        with Image.open(within(repo, name)) as image:
            require(image.format == 'PNG', 'GL image is not PNG');image.verify()
    assert_files(repo, entry['sourceFiles'])
    require(set(CLIENT_VIEW_SOURCES) <= entry['sourceFiles'].keys(), 'GL lacks final Player/camera/smoke source provenance')
    require(path.stat().st_mtime_ns >= max(within(repo, name).stat().st_mtime_ns for name in entry['sourceFiles']), 'GL sources changed after framebuffer capture')
    review_path = recorded_file(repo, entry['review']);review = read_json(review_path)
    require(review.get('report') == entry['report'] and review.get('images') == entry['images']
            and review.get('openedAllImages') is True and review.get('status') == 'PASS'
            and isinstance(review.get('reviewer'), str) and review['reviewer'].strip(), 'Root must review the actual opened GL images and pin their hashes')
    require(datetime.fromisoformat(review.get('reviewedAt', '').replace('Z', '+00:00')).tzinfo is not None
            and review_path.stat().st_mtime_ns >= path.stat().st_mtime_ns, 'GL root review must follow capture and include a timestamp with timezone')
    version_gate(repo, entry, log_text(recorded_file(repo, entry['log'])))
    return {**entry, 'requiredGate': True, 'frames': len(frames), 'rootVisualReview': 'PASS', 'arbitrarySkinRendererCompatibilityVerified': False}


def package_release_gate(repo: Path, jar_entry: dict, platform: str) -> dict:
    props = dict(line.split('=', 1) for line in (repo/'gradle.properties').read_text(encoding='utf-8').splitlines()
                 if '=' in line and not line.lstrip().startswith('#'))
    mc, loader = PLATFORMS[platform]
    require(props['minecraft_version'] == mc and props['mod_version'] == VERSION, 'Wrong canonical JAR version or Minecraft platform')
    canonical = within(repo, f"build/libs/{props['mod_archive_name']}-MC{mc}-{loader}-{VERSION}-bundled.jar")
    jar = recorded_file(repo, jar_entry)
    require(jar.resolve() == canonical.resolve(), 'Manifest JAR must be the exact canonical JAR verified by package_release')
    process = subprocess.run([sys.executable, '-X', 'utf8', 'tools/package_release.py', '--profile', 'bundled', '--verify-only'],
                             cwd=repo, capture_output=True, encoding='utf-8', errors='replace')
    require(process.returncode == 0, f'Complete bundled JAR/distribution verification failed: {repo}\n{process.stdout}\n{process.stderr}')
    value = json.loads(process.stdout)
    require(value.get('version') == VERSION and value.get('profile') == 'bundled' and value.get('outputWritten') is False,
            'Read-only package verification did not identify the complete current bundled build')
    inactive = 'data/blacklight/'+('recipe' if platform == 'neoforge' else 'recipes')+'/military_fuel_can.json'
    require(value.get('inactiveResourcesVerified') == {inactive: sha(repo/'src/main/resources'/inactive)}
            and value.get('geckoLibPackaged') is False, 'Package verification must pin the preserved inactive fuel source and exclude GeckoLib')
    recorded_file(repo, jar_entry)
    return {**value, 'jar': jar_entry}


def required_evidence(entry: dict) -> dict[str, str]:
    records = [entry['junit']['log'], entry['jar'], entry['vehiclePreview']['manifest'], entry['vehiclePreview']['capture']]
    files = dict(entry['junit']['xmlFiles'])
    if entry.get('clientView'):
        client = entry['clientView']
        records += [client['report'], client['review'], client['log'], *client.get('versionLogs', [])]
        files.update(client['images'])
    for game in entry['gameTests']:records += [game['log'], *game.get('versionLogs', [])]
    garage = entry['garageAudit']
    records += [garage['json'], garage['prepareLog'], garage['verifyLog'], *garage.get('versionLogs', [])]
    for record in records:
        require(record['path'] not in files or files[record['path']] == record['sha256'], 'Conflicting frozen evidence hashes')
        files[record['path']] = record['sha256']
    # Main JAR is copied to its loader directory, not duplicated into evidence.
    del files[entry['jar']['path']]
    return files


def evidence_gate(repo: Path, entry: dict) -> None:
    evidence = entry.get('evidenceFiles', {});assert_files(repo, evidence)
    require(all(evidence.get(name) == digest for name, digest in required_evidence(entry).items()),
            'Evidence freeze omits actual logs, exact final XML, restart JSON or capture provenance')
    gl_images = entry.get('clientView', {}).get('images', {})
    require(all(name.startswith(('.superpowers/', 'build/', 'docs/', 'art/')) and (Path(name).suffix in ('.log', '.json', '.xml', '.txt', '.md') or name in gl_images and Path(name).suffix == '.png')
                and not {part.lower() for part in PurePosixPath(name).parts}
                & {'world', 'saves', 'config', 'pcl', '.aws', '.codex', '.agents', '.git', 'secrets', 'token.json',
                   'credentials.json', 'credentials.toml', 'vehicle-reference-cache', 'reference-cache'} for name in evidence),
            'Evidence includes an unreviewed runtime/save/cache file')


def catalogue_gate(neo: Path, forge: Path) -> dict:
    process = subprocess.run([sys.executable, '-X', 'utf8', 'tools/archive_military6_catalog.py', '--verify',
                              '--neo-root', str(neo), '--forge-root', str(forge), '--output', str(neo/'docs/制作资料/M6')],
                             cwd=neo, capture_output=True, encoding='utf-8', errors='replace')
    require(process.returncode == 0, f'Current M6 materials failed read-only verification:\n{process.stdout}\n{process.stderr}')
    value = json.loads(process.stdout)
    require(value.get('status') == 'PASS' and value.get('outputWritten') is False
             and value.get('catalogue_integrity') == 'PASS' and not value.get('errors') and value.get('preview_pngs') == 30
            and value.get('icon_pngs') == 13 and value.get('active_recipes_per_loader') == 10
             and value.get('source_snapshots', 0) > 0 and value.get('gameplay_pass_claimed') is False,
            'M6 materials must verify current sources and all 30 explicitly labelled PNGs without gameplay claims')
    return value


def preflight(workspace: Path, manifest: dict) -> dict:
    require(set(manifest.get('platforms', {})) == set(PLATFORMS), 'Both actual loader platforms are required')
    protected_deliveries(workspace, manifest.get('protectedFiles', {}));result = {};game_hashes = set()
    for platform, entry in manifest['platforms'].items():
        repo = (workspace/'.blackwatch-worktrees'/(platform+'-military6')).resolve()
        require(Path(entry.get('repo', '')).resolve() == repo and repo.is_dir(), 'Manifest names another source checkout')
        require(entry.get('head') == AUTHOR_HEADS[platform] == git(repo, 'rev-parse', 'HEAD').decode().strip()
                and entry.get('branch') == git(repo, 'branch', '--show-current').decode().strip(), 'Author base HEAD/branch changed')
        changed_paths(repo);require(source_inventory(repo) == entry.get('sourceFiles'), f'Complete {platform} source freeze is missing or changed')
        require(entry['sourceFiles'].get('art/military-vehicles/vehicle-authoring.json') == VEHICLE_AUTHORING_SHA,
                'Full source must preserve the M6.1 physical authoring contract')
        release = package_release_gate(repo, entry['jar'], platform);jar = within(repo, entry['jar']['path'])
        vehicles = vehicle_jar_gate(jar, platform);author = author_gate(repo, jar, platform)
        junit = junit_gate(repo, entry['junit']);games = games_gate(repo, entry.get('gameTests', []))
        hashes = {game['log']['sha256'] for game in games}
        require(not game_hashes & hashes, 'Duplicate GameTest log SHA256 across loader platforms')
        game_hashes.update(hashes)
        require(REQUIRED_JUNIT_SUITES <= {name.rsplit('.', 1)[-1] for name in junit['suiteNames']}, 'Final complete JUnit omits required old/new model, hand, garage or network suites')
        garage = garage_gate(repo, entry['garageAudit'], platform);preview = preview_gate(repo, entry['vehiclePreview'])
        client = client_view_gate(repo, entry.get('clientView'))
        require(all(entry['sourceFiles'].get(name) == digest for name, digest in client['sourceFiles'].items()), 'GL source hashes differ from full source freeze')
        production = [within(repo, name) for name in entry['sourceFiles'] if name.startswith('src/') and (name.endswith('.java') or '/resources/' in name)]
        newest = max(path.stat().st_mtime_ns for path in production)
        finals = [recorded_file(repo, entry['junit']['log']), *(within(repo, name) for name in entry['junit']['xmlFiles'])]
        finals += [recorded_file(repo, game['log']) for game in entry['gameTests']]
        finals += [recorded_file(repo, entry['garageAudit']['verifyLog']), jar]
        finals += [recorded_file(repo, client['report']), recorded_file(repo, client['review'])]
        require(all(path.stat().st_mtime_ns >= newest for path in finals), 'Production/JUnit sources changed after final actual validation/build')
        evidence_gate(repo, entry)
        result[platform] = {'repo': repo, 'jar': jar, 'release': release, 'vehicles': vehicles, 'author': author,
                            'junit': junit, 'gameTests': games, 'garageAudit': garage, 'vehiclePreview': preview, 'clientView': client}
    neo = result['neoforge']['repo'];docs = manifest.get('docFiles', {})
    require(docs and all(manifest['platforms']['neoforge']['sourceFiles'].get(name) == digest for name, digest in docs.items()),
            'Documentation hashes differ from the complete frozen source inventory')
    required = {'docs/MILITARY6_PLAYER_TEST_ZH.md', 'docs/制作资料/M6_载具与车库.md',
                'docs/制作资料/参考模型/blackwatch_two_hand_sneak.bbmodel'}
    require(required <= docs.keys() and docs['docs/制作资料/参考模型/blackwatch_two_hand_sneak.bbmodel'] == ORIGINAL_BB_SHA,
            'Chinese player guide, new production material or original M5 model archive is missing')
    require({name for name in manifest['platforms']['neoforge']['sourceFiles'] if name.startswith('docs/')} == docs.keys(), 'Documentation freeze must include the full relative materials and provenance')
    require(any(name.startswith('docs/verification/MILITARY6') and name.endswith('.md') for name in docs), 'Missing M6 source/verification receipt')
    for chassis in ('truck', 'apc', 'tank'):
        require('art/military-vehicles/'+chassis+'.bbmodel' in manifest['platforms']['neoforge']['sourceFiles'], 'Missing vehicle Blockbench source')
    require(all('art/military-vehicles/textures/'+faction+'.png' in manifest['platforms']['neoforge']['sourceFiles'] for faction in ('military', 'blackwatch')), 'Missing vehicle authoring textures')
    result['neoforge']['catalogue'] = catalogue_gate(neo, result['forge']['repo'])
    return result


def new_destination(workspace: Path, repos: dict[str, Path], output: Path) -> tuple[Path, Path, Path]:
    workspace = workspace.resolve();output = output.resolve()
    require(output.parent == workspace and output.name.startswith('military6-vehicles-preview-')
            and output.name != M6_NAME
            and not any(output.is_relative_to(repo.resolve()) or repo.resolve().is_relative_to(output) for repo in repos.values()),
            'Use a new military6-vehicles-preview-* directory directly under workspace, outside source/installed games')
    stage = output.with_name(output.name+'.staging');archive = Path(str(output)+'.zip');checksum = Path(str(archive)+'.sha256')
    require(not any(path.exists() for path in (output, stage, archive, checksum)), 'Existing output/staging/ZIP/checksum is never overwritten')
    return stage, archive, checksum


def copy_file(source: Path, target: Path) -> None:
    require(source.is_file() and not source.is_symlink(), 'Unsafe copy input')
    target.parent.mkdir(parents=True, exist_ok=True);shutil.copy2(source, target)


def collect(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, default=Path('F:/MC建筑'))
    parser.add_argument('--verified-manifest', required=True, type=Path, help='Root verifier authored final actual-evidence freeze; collector never creates VERIFIED')
    parser.add_argument('--output', type=Path);parser.add_argument('--check-only', action='store_true')
    args = parser.parse_args(argv);workspace = args.workspace.resolve();manifest_path = args.verified_manifest.resolve()
    manifest = load_verified_manifest(manifest_path);manifest_sha = sha(manifest_path)
    output = (args.output or workspace/(M6_NAME+'-r2')).resolve()
    repos = {platform: workspace/'.blackwatch-worktrees'/(platform+'-military6') for platform in PLATFORMS}
    stage, archive_path, checksum_path = new_destination(workspace, repos, output)
    checked = preflight(workspace, manifest)
    if args.check_only:
        print(json.dumps({'status': 'PASS', 'version': VERSION, 'outputWritten': False,
                          'verifiedManifestSha256': manifest_sha,
                           'platforms': {platform: {key: value[key] for key in ('release', 'junit', 'gameTests', 'garageAudit', 'vehiclePreview', 'clientView')} for platform, value in checked.items()}}, ensure_ascii=False, indent=2));return
    stage.mkdir();(stage/'sources').mkdir()
    summary = {'version': VERSION, 'createdUtc': datetime.now(timezone.utc).isoformat(), 'publicUpload': False,
               'installedGameModified': False, 'clientOpenGLVerifiedByCollector': False, 'clientOpenGLRootReviewVerified': True, 'verifiedManifestSha256': manifest_sha,
               'catalogue': checked['neoforge']['catalogue'], 'platforms': {}}
    for platform, data in checked.items():
        repo = data['repo'];entry = manifest['platforms'][platform]
        copy_file(data['jar'], stage/platform/data['jar'].name)
        source_archive(repo, stage/'sources'/(platform+'-full-source.zip'), entry['sourceFiles'])
        overlay = source_overlay(repo, stage/'sources'/(platform+'-changes.zip'))
        (stage/'sources'/(platform+'-tracked.diff')).write_bytes(git(repo, 'diff', '--binary', 'HEAD', '--',
            *sorted(SOURCE_ROOT_FILES), 'src/', 'tools/', 'docs/', 'art/', 'gradle/', '.github/'))
        write_json(stage/'sources'/(platform+'-untracked-files.json'), [name.decode('utf-8') for name in git(repo, 'ls-files', '--others', '--exclude-standard', '-z').split(b'\0') if name])
        for name in entry['evidenceFiles']:copy_file(within(repo, name), stage/'evidence'/platform/name)
        summary['platforms'][platform] = {**{key: data[key] for key in ('release', 'vehicles', 'author', 'junit', 'gameTests', 'garageAudit', 'vehiclePreview', 'clientView')}, 'sourceBaseline': overlay, 'jar': platform+'/'+data['jar'].name, 'jarSha256': entry['jar']['sha256']}
    neo = checked['neoforge']['repo']
    for name in manifest['docFiles']:copy_file(within(neo, name), stage/name)
    for name in manifest['platforms']['neoforge']['sourceFiles']:
        if name.startswith('art/military-vehicles/'):copy_file(within(neo, name), stage/name)
    copy_file(manifest_path, stage/'evidence/VERIFIED-INPUT.json')
    (stage/'README.md').write_text('# military.6 完整本地预览交付\n\n退出游戏，备份并移出旧黑光主JAR，再把对应平台目录中唯一完整主JAR放入mods。NeoForge对应MC1.21.1，Forge对应MC1.20.1；保留已安装的GeckoLib。源码、文档和整包ZIP不放进mods。\n\n[中文玩家测试指南](docs/MILITARY6_PLAYER_TEST_ZH.md) · [载具与车库制作资料](docs/制作资料/M6_载具与车库.md)\n\nart保留原创载具Blockbench、贴图和8个实际生产模型CPU场景；docs另保留M5原模型与历史资料，维持工程原有相对链接。离线图不是游戏截图，不代表客户端GL或按键手感验收。\n\nsources含两端完整来源清单、相对作者HEAD的全部暂存/未暂存/未跟踪覆盖快照、删除列表和二进制diff。HEAD只是作者基线，M6功能尚未提交；diff不能替代未跟踪源。evidence只复制明确冻结的本地QA证据，不含玩家存档或实例日志。\n\n本工具不运行Gradle、不操作PCL或玩家实例、不提交推送、不安装GeckoLib、不包含商业参考截图缓存。\n', encoding='utf-8')
    require(sha(manifest_path) == manifest_sha, 'Verified manifest changed during collection')
    preflight(workspace, manifest)
    inventory = {path.relative_to(stage).as_posix(): sha(path) for path in sorted(stage.rglob('*')) if path.is_file()}
    summary['files'] = inventory;write_json(stage/'MANIFEST.json', summary)
    checksums = {**inventory, 'MANIFEST.json': sha(stage/'MANIFEST.json')}
    (stage/'SHA256SUMS.txt').write_text(''.join(f'{digest}  {name}\n' for name, digest in sorted(checksums.items())), encoding='utf-8')
    assert_files(stage, checksums);require(not output.exists(), 'Output appeared during collection; staging retained');stage.rename(output)
    with ZipFile(archive_path, 'x', ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(output.rglob('*')):
            if path.is_file():archive.write(path, output.name+'/'+path.relative_to(output).as_posix())
    with ZipFile(archive_path) as archive:require(archive.testzip() is None, 'Final ZIP CRC verification failed')
    with checksum_path.open('x', encoding='ascii') as stream:stream.write(sha(archive_path)+'  '+archive_path.name+'\n')
    protected_deliveries(workspace, manifest['protectedFiles'])
    print(json.dumps({'status': 'COLLECTED', 'version': VERSION, 'directory': str(output), 'archive': str(archive_path),
                      'sha256': sha(archive_path), 'zipCrc': 'PASS', 'installedGameModified': False}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    if hasattr(sys.stdout, 'reconfigure'):sys.stdout.reconfigure(encoding='utf-8')
    collect()
