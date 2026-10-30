"""Read-only evidence gates, then an exclusive NEW local military.5 handoff. Never installs or uploads."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET
from zipfile import ZIP_DEFLATED, ZipFile

VERSION = '0.3.4-preview.20261025-military.5'
SOURCE_SHA = '4d1b2213f160aa7a5ece31131c77102d654474277b46a6f93c8624cba71b4696'
AUTHOR_HEADS = {'neoforge': '996dffdf2d56091cbb01ed9f871bc440ed571a1a',
                'forge': '59f8fb10f2b79473fd81dd65e9f2fea3c4b6bee4'}
PROTECTED = {
    'military-organization-preview-20261023.zip': '3ef369f0f347562896226fc77b62cdc4035c1ad61b2dae07f45a91f9262dc031',
    'military-upgrade-preview-20261024.zip': '412732809bf4bbdb9f6e082075b937761dcdcf33c07519535483c50db1ec8119',
    'military-officers-preview-20261024.zip': '847aae78693cf6c3f592894c62bba5447beadeeacc3ecab13f97b544558ed9a3',
    'military-feedback-preview-20261025.zip': 'f7af4dbfe223cc42c3b560ff3ac783c24982a4c09e4b37ebcabcf987742e46e5',
}
SOURCE_ROOT_FILES = {'README.md', 'LICENSE', 'THIRD_PARTY_ASSETS.md', 'THIRD_PARTY_NOTICES.md', 'CONTRIBUTING.md',
                     'TEMPLATE_LICENSE.txt', 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', '.gitignore'}
UPGRADE_NAMES = ('world', 'config', 'defaultconfigs', 'server.properties', 'eula.txt', 'city-military-audit.json')
HOME_FLAGS = ('legacyMilitary4NativeLoadsObservedBeforeMigration', 'phaseOneHomeAssignmentsVerified',
              'initialEntityLoadFieldsVerifiedBeforeMigration', 'actualProcessRestartVerified',
              'nativeOriginAndSlotLedgerPreserved', 'externalDoesNotConsumeNativeSlots',
              'fieldAnchorAndDimensionPersisted', 'finiteWeaponRoleOutfitPersisted')
NATIVE_FLAGS = ('naturalStartVerified', 'restartVerified', 'uniqueSavedGuardUUIDs', 'savedOrganizationVerified',
                'officerRoleUniformManualAppointmentRestartVerified', 'persistedMarker', 'persistedSevenRounds',
                'ambientFixtureRestartVerified', 'structuralImpactDiskRestartVerified', 'actualRifleGlassAndWoodVerified',
                'ambientMobsDoNotConsumeGarrisonBudget')
JUNIT_SUITES = {'BlackwatchReferenceProfileTest', 'BlackwatchRigPoseTest', 'FirearmSupportPoseTest',
                'FirearmHandPoseTest', 'FirearmViewStateTest', 'MilitaryBaseHealthTest'}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def sha(path: Path) -> str:
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def git(repo: Path, *args: str) -> bytes:
    return subprocess.check_output(['git', '-C', str(repo), *args])


def within(root: Path, relative: str) -> Path:
    require(isinstance(relative, str) and bool(relative) and not Path(relative).is_absolute(), 'Invalid relative evidence path')
    require('..' not in Path(relative).parts, f'Parent traversal is forbidden: {relative}')
    path = root/relative
    require(path.resolve().is_relative_to(root.resolve()) and not path.is_symlink(), f'Unsafe evidence/source path: {path}')
    return path


def read_json(path: Path) -> dict:
    require(path.is_file() and not path.is_symlink(), f'Missing or unsafe JSON evidence: {path}')
    return json.loads(path.read_text(encoding='utf-8-sig'))


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


def assert_files_unchanged(root: Path, files: dict[str, str]) -> None:
    require(bool(files), f'Empty file manifest: {root}')
    for name, expected in files.items():
        path = within(root, name)
        require(path.is_file() and sha(path) == expected, f'Missing or changed recorded file: {path}')


def read_log(path: Path) -> str:
    require(path.is_file() and not path.is_symlink(), f'Missing actual run log: {path}')
    # Verification tokens are ASCII. Raw original bytes are copied, not re-encoded.
    return path.read_bytes().decode('utf-8', errors='replace')


def saving_shutdown(text: str) -> bool:
    return ('BUILD SUCCESSFUL' in text and 'Stopping server' in text
            and re.search(r'All dimensions (?:are )?saved', text) is not None)


def game_test(path: Path) -> dict:
    expected = 132
    text = read_log(path)
    totals = re.findall(r'(\d+) GAME TESTS COMPLETE', text)
    passed = re.findall(r'All (\d+) required tests passed', text)
    require(totals and passed and int(totals[-1]) == expected and int(passed[-1]) == expected
            and not re.search(r'\b[1-9]\d* required tests failed', text), f'Expected {expected} complete passing GameTests: {path}')
    require(saving_shutdown(text) and 'Game test server shutting down' in text,
            f'GameTest lacks a normal saving shutdown: {path}')
    return {'tests': expected, 'requiredPassed': expected, 'normalSavingShutdown': True,
            'log': str(path), 'sha256': sha(path)}


def junit_result(repo: Path, expected: int) -> tuple[dict, list[Path]]:
    xmls = sorted((repo/'build/test-results/test').glob('TEST-*.xml'))
    require(xmls and expected > 0, f'Missing final JUnit XML or invalid expected count: {repo}')
    result = dict(tests=0, failures=0, errors=0, skipped=0, suites=len(xmls))
    suites = []
    for path in xmls:
        root = ET.parse(path).getroot()
        require(root.tag == 'testsuite', f'Unexpected JUnit XML: {path}')
        suites.append(root.get('name', path.stem.removeprefix('TEST-')))
        for key in ('tests', 'failures', 'errors', 'skipped'):
            result[key] += int(root.get(key, '0'))
    require(result['tests'] == expected and not any(result[k] for k in ('failures', 'errors', 'skipped')),
            f'Final JUnit result does not meet {expected} tests/zero-failure gate: {repo}: {result}')
    result['suiteNames'] = suites
    return result, xmls


def current_source_paths(repo: Path, tests: bool) -> list[Path]:
    roots = ['src/main/java', 'src/main/resources', 'src/generated/resources']
    if tests:
        roots.append('src/test/java')
    return [p for name in roots for p in (repo/name).rglob('*') if p.is_file() and '.cache' not in p.parts]


def junit_gate(repo: Path, expected: int, log: Path) -> tuple[dict, list[Path]]:
    result, xmls = junit_result(repo, expected)
    actual_suites = {name.rsplit('.', 1)[-1] for name in result['suiteNames']}
    require(JUNIT_SUITES <= actual_suites, f'Final JUnit omits required reference/hand/core suites: {JUNIT_SUITES-actual_suites}')
    require('BUILD SUCCESSFUL' in read_log(log), f'Final JUnit Gradle run did not finish: {log}')
    newest = max(p.stat().st_mtime_ns for p in current_source_paths(repo, True))
    require(min(p.stat().st_mtime_ns for p in xmls) >= newest,
            f'Production or JUnit sources changed after the final full XML run: {repo}')
    result.update(log=str(log), logSha256=sha(log), xmlSha256={p.name: sha(p) for p in xmls})
    return result, xmls


def check_old_releases(workspace: Path) -> None:
    for name, expected in PROTECTED.items():
        path = workspace/name
        require(path.is_file() and not path.is_symlink() and sha(path) == expected, f'Protected old release missing/changed: {path}')


def protected_upstream_resources(repo: Path, jar: Path, ref: str) -> dict:
    tree = git(repo, 'ls-tree', '-r', '-z', ref, '--', 'src/main/resources', 'art/motion-profile', 'art/motion-profile-manifest.json')
    entries = []
    for record in tree.split(b'\0'):
        if not record:
            continue
        header, raw_name = record.split(b'\t', 1)
        name = raw_name.decode('utf-8')
        if '/native_animations/' in name or name.startswith('art/motion-profile/') or name == 'art/motion-profile-manifest.json':
            parts = header.split()
            require(parts[0] == b'100644' and parts[1] == b'blob', f'Unsafe author resource mode: {name}')
            entries.append((name, parts[2]))
    native_count = sum('/native_animations/' in name for name, _ in entries)
    require(native_count > 0 and any(name == 'art/motion-profile-manifest.json' for name, _ in entries),
            f'Actual author tree lacks expected native/profile inventories: {repo}@{ref}')
    batch = subprocess.run(['git', '-C', str(repo), 'cat-file', '--batch'], input=b''.join(oid+b'\n' for _, oid in entries),
                           capture_output=True, check=True).stdout
    offset, inventory = 0, {}
    with ZipFile(jar) as archive:
        jar_names = set(archive.namelist())
        for name, oid in entries:
            end = batch.index(b'\n', offset)
            header = batch[offset:end].split()
            require(header[0] == oid and header[1] == b'blob', f'Unexpected author blob response: {name}')
            length = int(header[2]);data = batch[end+1:end+1+length];offset = end+length+2
            current = within(repo, name)
            require(current.is_file() and current.read_bytes() == data, f'Author resource changed or removed: {name}')
            if '/native_animations/' in name:
                entry = name.removeprefix('src/main/resources/')
                require(entry in jar_names and archive.read(entry) == data, f'Author native animation missing/stale in JAR: {name}')
            inventory[name] = hashlib.sha256(data).hexdigest()
    return {'authorHead': ref, 'nativeAnimationFiles': native_count, 'protectedFiles': len(inventory), 'sha256': inventory}


def check_preview(repo: Path, manifest_path: Path) -> dict:
    value = read_json(manifest_path)
    require(value.get('kind') == 'actual_production_ModelPart_render_vertices_CPU_preview'
            and value.get('not_game_screenshot') is True and value.get('source_sha256') == SOURCE_SHA,
            f'Preview is not the actual authorised production-asset capture: {manifest_path}')
    require(value.get('npc_poses', 0) >= 24 and value.get('firstperson_poses', 0) >= 8
            and value.get('poses') == value['npc_poses']+value['firstperson_poses'], f'Incomplete NPC/first-person preview: {manifest_path}')
    required = {'src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java',
                'src/main/java/com/codex/blacklight/client/model/BlackwatchRigPose.java',
                'src/main/java/com/codex/blacklight/firearms/FirearmViewEvents.java',
                'src/main/java/com/codex/blacklight/firearms/FirearmHandTransforms.java',
                'src/main/resources/assets/blacklight/military/reference-rig.json', 'tools/ReferenceMeshCapture.java'}
    require(required <= value.get('sources', {}).keys(), f'Preview omits the real rig/hand/capture sources: {manifest_path}')
    assert_files_unchanged(repo, value['sources'])
    assert_files_unchanged(manifest_path.parent, value.get('images', {}))
    require({'blackwatch-reference-poses.png', 'military-reference-poses.png', 'firstperson-reference-poses.png'} <= value['images'].keys(),
            f'Missing readable faction/first-person preview sheets: {manifest_path}')
    profile = read_json(repo/'src/main/resources/assets/blacklight/military/reference-rig.json')
    require(profile.get('source_sha256') == SOURCE_SHA and profile.get('counts', {}).get('body_cubes') == 16
            and profile['counts'].get('weapon_cubes') == 5349 and len(profile.get('animations', {})) == 7,
            'Production reference profile lost its authored provenance/body-vs-gun separation')
    original = repo/'docs/制作资料/参考模型/blackwatch_two_hand_sneak.bbmodel'
    require(original.is_file() and sha(original) == SOURCE_SHA, f'Authorised original BB source is missing/changed: {original}')
    return {'manifest': str(manifest_path), 'manifestSha256': sha(manifest_path), 'poses': value['poses'],
            'sources': value['sources'], 'images': value['images'], 'notGameScreenshot': True}


def instant(value: str) -> datetime:
    return datetime.fromisoformat(value.replace('Z', '+00:00'))


def upgrade_audit(repo: Path, directory: str, dimension: str, prefix: str, source: Path) -> dict:
    run = within(repo, directory)
    baseline_path = run/'MILITARY4-BASELINE.json'
    baseline = read_json(baseline_path)
    require(Path(baseline.get('source', '')).resolve() == source.resolve() and Path(baseline.get('target', '')).resolve() == run.resolve(),
            f'Upgrade baseline points at another source/target: {baseline_path}')
    source_files = baseline.get('sourceFiles', {})
    assert_files_unchanged(source, source_files)
    actual = {p.relative_to(source).as_posix() for name in UPGRADE_NAMES if (source/name).exists()
              for p in ([source/name] if (source/name).is_file() else (source/name).rglob('*')) if p.is_file()}
    require(actual == source_files.keys(), f'Original military.4 QA source inventory changed: {source}')
    original = baseline['sourceResult']
    require(read_json(source/'city-military-audit.json') == original and original.get('status') == 'PASS' and original.get('auditRevision') == 4,
            f'Original military.4 native audit changed or was not completed: {source}')
    native_path = run/'city-military-audit.json';home_path = run/'military-homes-audit.json'
    native, home = read_json(native_path), read_json(home_path)
    require(native.get('status') == 'PASS' and native.get('auditRevision') == 4 and native.get('dimension') == dimension
            and all(native.get(flag) is True for flag in NATIVE_FLAGS), f'Missing real upgraded native/durability PASS: {native_path}')
    expected_generator = 'BlacklightWorldGenerator' if dimension == 'blacklight:world' else 'CityChunkGenerator'
    require(original.get('dimension') == dimension and native.get('generator') == original.get('generator')
            and native.get('generator', '').endswith('.'+expected_generator), f'Upgrade used another native generator: {native_path}')
    require(home.get('status') == 'PASS' and home.get('schema') == 1 and home.get('dimension') == dimension
            and all(home.get(flag) is True for flag in HOME_FLAGS), f'Missing actual two-process military.5 homes PASS: {home_path}')
    require(home.get('military4BaselineSha256') == sha(baseline_path)
            and Path(home.get('sourceRunDirectory', '')).resolve() == run.resolve(), f'Homes result uses a different baseline/run directory: {home_path}')
    first, second = home.get('firstProcessId'), home.get('restartProcessId')
    require(first and second and first != second, f'One in-memory pass cannot prove process restart: {home_path}')
    uuid.UUID(first);uuid.UUID(second)
    require(instant(native['updatedUtc']) > instant(original['updatedUtc']) and instant(home['updatedUtc']) > instant(original['updatedUtc']),
            f'Copied military.4 PASS is not new upgrade evidence: {run}')
    for name in ('guards', 'baseId', 'unit', 'manualCommander', 'marker', 'ammunitionChest', 'issuedSlots', 'savedRifleRounds'):
        require(name in original and native.get(name) == original[name], f'Upgrade changed original saved {name}: {run}')
    expected_ids = {entry['uuid'] for entry in native['guards']}
    require({value for value in home.get('nativeSlots', []) if value} == expected_ids
            and {entry['uuid'] for entry in home.get('nativeSavedSnapshots', [])} == expected_ids,
            f'Homes audit does not cover the original native UUIDs: {home_path}')
    external, field = home.get('externalSavedSnapshot', {}), home.get('fieldSavedSnapshot', {})
    require(external.get('uuid') == home.get('externalUuid') and field.get('uuid') == home.get('fieldUuid')
            and len(expected_ids | {external.get('uuid'), field.get('uuid')}) == len(expected_ids)+2
            and external.get('magazine') == 5 and external.get('reserve') == 11 and external.get('equipped') is True
            and field.get('magazine') == 3 and field.get('reserve') == 9 and field.get('equipped') is False,
            f'Independent saved actors lost their exact finite loadouts/identities: {home_path}')
    require(home.get('baseId') == native['baseId'] and external.get('homeBase') == native['baseId']
            and external.get('homeDimension') == dimension and external.get('originBase') == ''
            and field.get('homeBase') == '' and field.get('originBase') == ''
            and field.get('fieldAnchorPresent') is True and field.get('fieldDimension') == dimension,
            f'Actual independent/field saved assignment snapshots are incomplete: {home_path}')
    logs = []
    for phase, marker in ((1, 'PHASE1_SAVED'), (2, 'PASS')):
        path = repo/f'.superpowers/military5-qa/{prefix}-phase{phase}.log';text = read_log(path)
        require(VERSION in text and f'CITY_MILITARY_HOMES_AUDIT {marker}' in text
                and 'CITY_MILITARY_SERVER_AUDIT PASS' in text and saving_shutdown(text), f'Missing actual current-version phase {phase} normal save: {path}')
        logs.append({'path': str(path), 'sha256': sha(path)})
    return {'dimension': dimension, 'native': native, 'homes': home, 'sourceBaselineSha256': sha(baseline_path),
            'sourceFiles': source_files, 'logs': logs,
            'files': {str(path.relative_to(repo)): sha(path) for path in (native_path, home_path, baseline_path)}}


def json_command(repo: Path, arguments: list[str]) -> dict:
    process = subprocess.run([sys.executable, '-X', 'utf8', *arguments], cwd=repo, capture_output=True, encoding='utf-8', errors='replace')
    require(process.returncode == 0, f'Read-only verification failed: {repo}: {arguments}\n{process.stdout}\n{process.stderr}')
    return json.loads(process.stdout)


def catalogue(neo: Path, forge: Path) -> dict:
    baseline = read_json(neo/'docs/制作资料/data/baseline.json')
    require(baseline.get('final_source_freeze') is True, 'Catalogue is not the final frozen source snapshot')
    for platform, repo in (('neoforge', neo), ('forge', forge)):
        require(Path(baseline[platform]['path']).resolve() == repo.resolve()
                and baseline[platform]['head'] == git(repo, 'rev-parse', 'HEAD').decode().strip()
                and baseline[platform]['branch'] == git(repo, 'branch', '--show-current').decode().strip(),
                f'Catalogue still names another {platform} source checkout')
    result = json_command(neo, ['tools/archive_military_catalog.py', '--verify', '--forge-root', str(forge)])
    require(result.get('status') == 'PASS' and not result.get('errors'), 'Catalogue snapshots/previews/links did not verify')
    return result


def allowed_source(name: str) -> bool:
    return name in SOURCE_ROOT_FILES or name.startswith(('src/', 'tools/', 'docs/', 'art/', 'gradle/', '.github/'))


def changed_paths(repo: Path) -> list[str]:
    tracked = git(repo, 'diff', '--name-only', '-z', 'HEAD').decode('utf-8').split('\0')
    untracked = git(repo, 'ls-files', '--others', '--exclude-standard', '-z').decode('utf-8').split('\0')
    changed = sorted(set(filter(None, tracked+untracked)))
    require(all(allowed_source(name) for name in changed), 'Unexpected non-source/private changes must be reviewed before collection: '
            + ', '.join(name for name in changed if not allowed_source(name)))
    return changed


def source_overlay(repo: Path, destination: Path) -> dict:
    require(not git(repo, 'diff', '--name-only', '--diff-filter=U'), f'Unresolved Git conflicts: {repo}')
    changed = changed_paths(repo);files, deleted = {}, []
    with ZipFile(destination, 'x', ZIP_DEFLATED, compresslevel=6) as archive:
        for name in changed:
            path = within(repo, name)
            if not path.exists():
                deleted.append(name);continue
            require(path.is_file(), f'Changed source is not a regular file: {path}')
            data = path.read_bytes();archive.writestr(name, data);files[name] = hashlib.sha256(data).hexdigest()
        result = {'head': git(repo, 'rev-parse', 'HEAD').decode().strip(),
                  'branch': git(repo, 'branch', '--show-current').decode().strip(),
                  'remote': git(repo, 'remote', 'get-url', 'origin').decode().strip() if git(repo, 'remote').strip() else '',
                  'files': files, 'deleted': deleted,
                  'instructions': 'All uncommitted military.1 through military.5 changes, including index, working tree and untracked sources. '
                                  'Start at HEAD, overlay these files and review deletions explicitly. No commit or push was performed.'}
        archive.writestr('PATCH-BASE.json', json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    require(changed_paths(repo) == changed, f'Changed source list moved during collection: {repo}')
    if files:
        assert_files_unchanged(repo, files)
    return result


def new_destination(workspace: Path, repos: dict[str, Path], output: Path) -> tuple[Path, Path, Path]:
    workspace, output = workspace.resolve(), output.resolve()
    require(output != workspace and output.is_relative_to(workspace)
            and not any(output.is_relative_to(repo.resolve()) or repo.resolve().is_relative_to(output) for repo in repos.values()),
            'Delivery must be a new workspace directory outside both repositories')
    stage = output.with_name(output.name+'.staging');archive = Path(str(output)+'.zip');checksum = Path(str(archive)+'.sha256')
    require(not any(path.exists() for path in (output, stage, archive, checksum)), 'Existing output/staging/ZIP/checksum is never overwritten')
    return stage, archive, checksum


def load_packager(repo: Path):
    spec = importlib.util.spec_from_file_location('military5_package_'+repo.name.replace('-', '_'), repo/'tools/package_release.py')
    module = importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module


def preflight(repos: dict[str, Path], workspace: Path, args) -> tuple[dict, dict]:
    neo = repos['neoforge']
    for name in ('MILITARY5_PLAYER_TEST_ZH.md', 'MILITARY5_BASES_AND_HOMES_ZH.md', 'verification/MILITARY5_IMPLEMENTATION_20261025_ZH.md'):
        require((neo/'docs'/name).is_file(), f'Missing military.5 handoff document: {name}')
    reviews = sorted((neo/'docs/verification').glob('MILITARY5*FINAL*REVIEW*.md'))
    require(reviews, 'Missing final independent military.5 review record')
    data = {}
    for platform, repo in repos.items():
        prefix = 'neo' if platform == 'neoforge' else 'forge'
        require(git(repo, 'rev-parse', 'HEAD').decode().strip() == AUTHOR_HEADS[platform], f'Unexpected author base HEAD: {repo}')
        require(VERSION in (repo/'gradle.properties').read_text(encoding='utf-8'), f'Wrong runtime version: {repo}')
        changed_paths(repo)
        jars = list((repo/'build/libs').glob(f'*-{VERSION}-bundled.jar'))
        require(len(jars) == 1, f'Expected one normal complete military.5 bundled JAR: {repo}')
        jar = jars[0]
        with ZipFile(jar) as archive:
            require(archive.testzip() is None and len(archive.namelist()) == len(set(archive.namelist())), f'Corrupt or duplicate-entry JAR: {jar}')
        release = json_command(repo, ['tools/package_release.py', '--profile', 'bundled', '--verify-only'])
        require(release.get('version') == VERSION and release.get('profile') == 'bundled' and release.get('outputWritten') is False,
                f'Current complete JAR/distribution verification did not pass: {repo}')
        author_resources = protected_upstream_resources(repo, jar, AUTHOR_HEADS[platform])
        junit_log = within(repo, getattr(args, prefix+'_junit_log'))
        junit, xmls = junit_gate(repo, getattr(args, platform+'_junit'), junit_log)
        game_log = within(repo, getattr(args, prefix+'_game_log'))
        games = game_test(game_log)
        require(VERSION in read_log(game_log), f'GameTest log does not identify military.5: {game_log}')
        require(game_log.stat().st_mtime_ns >= max(p.stat().st_mtime_ns for p in current_source_paths(repo, False)),
                f'Production source/assets changed after the complete GameTest run: {repo}')
        preview_path = within(repo, getattr(args, prefix+'_preview_manifest'))
        preview = check_preview(repo, preview_path)
        if platform == 'neoforge':
            cases = [('city', '.superpowers/military5-upgrade-neoforge', 'blacklight:city', 'neo-upgrade', workspace/'blacklight-evolution/.superpowers/military4-server-neoforge-final'),
                     ('world', '.superpowers/military5-world-upgrade-neoforge', 'blacklight:world', 'neo-world-upgrade', workspace/'blacklight-evolution/.superpowers/military4-world-server-neoforge')]
        else:
            cases = [('city', '.superpowers/military5-upgrade-forge', 'blacklight:city', 'forge-upgrade', workspace/'blacklight-evolution-forge-1.20.1/.superpowers/military4-server-forge-final')]
        audits = {name: upgrade_audit(repo, directory, dimension, log_prefix, source) for name, directory, dimension, log_prefix, source in cases}
        evidence_files = [junit_log, game_log, preview_path, *xmls]
        for audit in audits.values():
            evidence_files += [within(repo, name) for name in audit['files']]
            evidence_files += [Path(entry['path']) for entry in audit['logs']]
        data[platform] = dict(repo=repo, jar=jar, jarSha256=sha(jar), release=release, junit=junit, xmls=xmls,
                              games=games, preview=preview, previewPath=preview_path, audits=audits, authorResources=author_resources,
                              qa=repo/'.superpowers/military5-qa', packager=load_packager(repo),
                              evidenceHashes={str(path): sha(path) for path in evidence_files})
    return data, {'catalogue': catalogue(neo, repos['forge']), 'finalReviewSha256': {p.name: sha(p) for p in reviews}}


def copy_file(source: Path, destination: Path) -> None:
    require(source.is_file() and not source.is_symlink(), f'Unsafe copy input: {source}')
    destination.parent.mkdir(parents=True, exist_ok=True);shutil.copy2(source, destination)


def collect() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, default=Path('F:/MC建筑'))
    parser.add_argument('--output', type=Path)
    parser.add_argument('--check-only', action='store_true')
    parser.add_argument('--neoforge-junit', type=int, required=True, help='Exact observed final full NeoForge count; never infer from old runs')
    parser.add_argument('--forge-junit', type=int, required=True, help='Exact observed final full Forge count')
    for prefix in ('neo', 'forge'):
        parser.add_argument('--'+prefix+'-junit-log', default=f'.superpowers/military5-qa/{prefix}-unit-final.log')
        parser.add_argument('--'+prefix+'-game-log', default=f'.superpowers/military5-qa/{prefix}-regression-final.log')
        parser.add_argument('--'+prefix+'-preview-manifest', default='docs/制作资料/参考模型/生产预览-military5/manifest.json')
    args = parser.parse_args();workspace = args.workspace.resolve()
    repos = {'neoforge': workspace/'.blackwatch-worktrees/neoforge-military5', 'forge': workspace/'.blackwatch-worktrees/forge-military5'}
    output = (args.output or workspace/'military5-reference-homes-preview-20261025').resolve()
    require(output.parent == workspace and output.name.startswith('military5-reference-homes-preview-'),
            'Use a new military5-reference-homes-preview-* directory directly under the workspace; never write old source trees')
    stage, archive_path, checksum_path = new_destination(workspace, repos, output)
    check_old_releases(workspace)
    inputs, checks = preflight(repos, workspace, args)
    if args.check_only:
        print(json.dumps({'status': 'PASS', 'outputWritten': False, 'version': VERSION, 'checks': checks,
                          'platforms': {key: {'junit': d['junit'], 'gameTest': d['games'], 'jarSha256': d['jarSha256'],
                                             'upgrades': {name: a['homes']['status'] for name, a in d['audits'].items()}}
                                        for key, d in inputs.items()}}, ensure_ascii=False, indent=2))
        return
    stage.mkdir(parents=True, exist_ok=False);(stage/'sources').mkdir()
    summary = {'version': VERSION, 'createdUtc': datetime.now(timezone.utc).isoformat(), 'publicUpload': False,
               'installedGameModified': False, 'clientOpenGLVerifiedByCollector': False, 'protectedOldReleases': PROTECTED,
               'checks': checks, 'platforms': {}}
    for platform, data in inputs.items():
        repo = data['repo'];jar = data['jar'];evidence = stage/'evidence'/platform
        copy_file(jar, stage/platform/jar.name)
        baseline = source_overlay(repo, stage/'sources'/f'{platform}-changes.zip');data['sourceBaseline'] = baseline
        (stage/'sources'/f'{platform}-tracked.diff').write_bytes(git(repo, 'diff', '--binary', 'HEAD'))
        full_source = stage/'sources'/f'{platform}-full-source.zip'
        full_manifest = data['packager'].source_snapshot(repo, full_source)
        for path in sorted(data['qa'].rglob('*')):
            if path.is_file() and path.suffix in ('.log', '.txt', '.json', '.xml'):
                copy_file(path, evidence/'qa'/path.relative_to(data['qa']))
        for path in data['xmls']:
            copy_file(path, evidence/'junit'/path.name)
        for name, audit in data['audits'].items():
            for relative in audit['files']:
                path = within(repo, relative);copy_file(path, evidence/'upgrades'/name/path.name)
        copy_file(data['previewPath'], evidence/'reference-preview-manifest.json')
        tests = {key: data[key] for key in ('junit', 'games', 'audits', 'release', 'preview', 'authorResources')}
        write_json(evidence/'TEST-RESULTS.json', tests)
        summary['platforms'][platform] = {'head': baseline['head'], 'branch': baseline['branch'], 'jar': f'{platform}/{jar.name}',
                                          'jarSha256': data['jarSha256'], 'overlayFiles': len(baseline['files']),
                                          'fullSourceFiles': len(full_manifest), 'tests': tests}
    docs = repos['neoforge']/'docs'
    require(not any(p.is_symlink() for p in docs.rglob('*')), 'Documentation contains a symlink')
    shutil.copytree(docs, stage/'docs')
    (stage/'README.md').write_text('''# military.5 完整本地预览交付

这是包含本轮功能的完整黑光主模组。退出游戏，移出旧黑光主JAR备份，再把对应平台目录内的唯一完整主JAR放入实例mods；保留已安装的GeckoLib。NeoForge用于MC1.21.1，Forge用于MC1.20.1，两端不能混放。源码、文档和整包ZIP不放进mods。本交付不操作PCL，也未公开上传。

- [简单测试流程](docs/MILITARY5_PLAYER_TEST_ZH.md)
- [基地识别、驻军名额、归属与补给](docs/MILITARY5_BASES_AND_HOMES_ZH.md)
- [兵种、枪弹、建筑及实际资产外观](docs/制作资料/README.md)
- [实施、证据与已知边界](docs/verification/MILITARY5_IMPLEMENTATION_20261025_ZH.md)

实际NPC骨架、衣料与动作取自已获准的参考模型并适配；原重枪体素整件没有导入。离线预览由实际生产ModelPart、玩家手臂和物品JSON生成，客户端GL与按键手感请按指南复核。平民、阵营关系及感染者敌我策略仍保持约定的既有行为。

sources中changes.zip保存HEAD之上的全部暂存、未暂存和未跟踪军事变更，PATCH-BASE含真实基线、逐文件哈希和删除清单；tracked.diff不能替代该覆盖快照。full-source.zip另存受审计的完整项目源文件与来源清单。evidence仅收本地QA、实际JUnit、三份旧档升级及模型预览校验，不收玩家完整私人日志。四个旧交付ZIP保持原哈希。不包含已授权提交或推送的声明。
''', encoding='utf-8')
    check_old_releases(workspace)
    require(catalogue(repos['neoforge'], repos['forge']) == checks['catalogue'], 'Catalogue changed during collection')
    for platform, data in inputs.items():
        repo = data['repo'];baseline = data['sourceBaseline']
        require(sha(data['jar']) == data['jarSha256'] and git(repo, 'rev-parse', 'HEAD').decode().strip() == baseline['head']
                and git(repo, 'branch', '--show-current').decode().strip() == baseline['branch']
                and changed_paths(repo) == sorted([*baseline['files'], *baseline['deleted']]), f'{platform} checkout/JAR changed during collection')
        if baseline['files']:
            assert_files_unchanged(repo, baseline['files'])
        check_preview(repo, data['previewPath'])
        for audit in data['audits'].values():
            assert_files_unchanged(repo, audit['files'])
        for path, expected in data['evidenceHashes'].items():
            require(Path(path).is_file() and sha(Path(path)) == expected, f'QA evidence changed during collection: {path}')
    inventory = {p.relative_to(stage).as_posix(): sha(p) for p in sorted(stage.rglob('*')) if p.is_file()}
    summary['files'] = inventory;write_json(stage/'MANIFEST.json', summary)
    checksums = {**inventory, 'MANIFEST.json': sha(stage/'MANIFEST.json')}
    (stage/'SHA256SUMS.txt').write_text(''.join(f'{digest}  {name}\n' for name, digest in sorted(checksums.items())), encoding='utf-8')
    assert_files_unchanged(stage, checksums)
    require(not output.exists(), 'Output appeared during collection; staging retained')
    stage.rename(output)
    with ZipFile(archive_path, 'x', ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(output.rglob('*')):
            if path.is_file():
                archive.write(path, output.name+'/'+path.relative_to(output).as_posix())
    with ZipFile(archive_path) as archive:
        require(archive.testzip() is None, 'Final ZIP CRC verification failed')
    with checksum_path.open('x', encoding='ascii') as stream:
        stream.write(sha(archive_path)+'  '+archive_path.name+'\n')
    check_old_releases(workspace)
    print(json.dumps({'directory': str(output), 'archive': str(archive_path), 'sha256': sha(archive_path),
                      'files': len(checksums)+1, 'version': VERSION}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    collect()
