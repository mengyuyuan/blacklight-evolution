"""Verify military.4 evidence and collect a new local handoff without touching PCL or old releases."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

VERSION = '0.3.4-preview.20261024-military.4'
PROTECTED = {
    'military-organization-preview-20261023.zip': '3ef369f0f347562896226fc77b62cdc4035c1ad61b2dae07f45a91f9262dc031',
    'military-upgrade-preview-20261024.zip': '412732809bf4bbdb9f6e082075b937761dcdcf33c07519535483c50db1ec8119',
    'military-officers-preview-20261024.zip': '847aae78693cf6c3f592894c62bba5447beadeeacc3ecab13f97b544558ed9a3',
}
JUNIT_COUNTS = {'neoforge': 554, 'forge': 551}
AUDIT_FLAGS = (
    'ambientMobsDoNotConsumeGarrisonBudget', 'actualRifleGlassAndWoodVerified',
    'ambientFixtureRestartVerified', 'structuralImpactDiskRestartVerified',
    'naturalStartVerified', 'restartVerified', 'uniqueSavedGuardUUIDs', 'savedOrganizationVerified',
    'officerRoleUniformManualAppointmentRestartVerified', 'persistedMarker', 'persistedSevenRounds',
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def read_json(path: Path) -> dict:
    require(path.is_file() and not path.is_symlink(), f'Missing or unsafe JSON evidence: {path}')
    return json.loads(path.read_text(encoding='utf-8-sig'))


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def git(repo: Path, *args: str) -> bytes:
    return subprocess.check_output(['git', '-C', str(repo), *args])


def within(root: Path, relative: str) -> Path:
    path = root / relative
    require(not path.is_symlink() and path.resolve().is_relative_to(root.resolve()),
            f'Path leaves its declared root or is a symlink: {path}')
    return path


def check_old_releases(workspace: Path) -> None:
    for name, expected in PROTECTED.items():
        path = workspace / name
        require(path.is_file() and not path.is_symlink() and sha(path) == expected,
                f'Protected old release is missing or changed: {path}')


def read_log(path: Path) -> str:
    require(path.is_file() and not path.is_symlink(), f'Missing actual run log: {path}')
    # Pass/fail tokens are ASCII; keep raw log bytes intact in the handoff.
    return path.read_text(encoding='gbk', errors='replace')


def game_test(path: Path, expected: int) -> dict:
    text = read_log(path)
    totals = re.findall(r'(\d+) GAME TESTS COMPLETE', text)
    require(bool(totals) and int(totals[-1]) == expected and f'All {expected} required tests passed' in text,
            f'Expected {expected} actual passing GameTest scenes: {path}')
    require('BUILD SUCCESSFUL' in text and 'All dimensions are saved' in text,
            f'GameTest must finish a normal saving shutdown: {path}')
    return {'tests': expected, 'requiredPassed': expected, 'log': path.name, 'sha256': sha(path),
            'normalSavingShutdown': True}


def junit_result(repo: Path, platform: str) -> tuple[dict, list[Path]]:
    xmls = sorted((repo / 'build/test-results/test').glob('TEST-*.xml'))
    require(bool(xmls), f'Missing actual JUnit XML: {platform}')
    result = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0, 'suites': len(xmls)}
    for path in xmls:
        root = ET.parse(path).getroot()
        require(root.tag == 'testsuite', f'Unexpected JUnit XML root: {path}')
        for name in ('tests', 'failures', 'errors', 'skipped'):
            result[name] += int(root.get(name, '0'))
    require(result['tests'] == JUNIT_COUNTS[platform] and not any(result[n] for n in ('failures', 'errors', 'skipped')),
            f'JUnit totals do not meet the final {platform} gate: {result}')
    return result, xmls


def ordinary_audit(path: Path, dimension: str) -> dict:
    result = read_json(path)
    require(result.get('status') == 'PASS' and result.get('auditRevision') == 4,
            f'New ordinary-server audit is not revision 4 PASS: {path}')
    require(result.get('dimension') == dimension, f'Audit verified the wrong dimension: {path}')
    generator = 'BlacklightWorldGenerator' if dimension == 'blacklight:world' else 'CityChunkGenerator'
    require(result.get('generator', '').endswith('.' + generator), f'Wrong actual native generator: {path}')
    require(result.get('ambientFixtureMobsAtFirstMuster') == 80
            and result.get('unrelatedMobsAtFirstMuster', 0) >= 80,
            f'The first muster did not traverse 80 real unrelated fixture mobs: {path}')
    require(all(result.get(flag) is True for flag in AUDIT_FLAGS),
            f'Ordinary-server audit lacks required true flags: {path}: '
            + ', '.join(flag for flag in AUDIT_FLAGS if result.get(flag) is not True))
    require(len(set(result.get('ambientFixtureUuids', []))) == 80 and result.get('issuedSlots') == 2,
            f'Expected 80 original ambient UUIDs and two issued real garrison slots: {path}')
    require(isinstance(result.get('structuralImpactMarker'), dict), f'Missing actual destroyed-block coordinate: {path}')
    return result


def migration_audit(repo: Path, platform: str, prefix: str) -> tuple[dict, Path, Path, Path]:
    directory = repo / f'.superpowers/military4-upgrade-{platform}'
    baseline_path = directory / 'MILITARY3-BASELINE.json'
    baseline = read_json(baseline_path)
    previous = baseline.get('sourceAudit', {})
    expected_source = repo / f'.superpowers/military3-server-{platform}/city-military-audit.json'
    require(Path(baseline.get('source', '')).resolve() == expected_source.parent.resolve(),
            f'Migration baseline points to another source world: {baseline_path}')
    source_hash = sha(expected_source)
    require(source_hash == baseline.get('sourceAuditSha256') and read_json(expected_source) == previous,
            f'Original military.3 baseline changed: {baseline_path}')
    path = directory / 'city-military-audit.json'
    result = read_json(path)
    require(result.get('status') == 'PASS' and result.get('restartVerified') is True
            and result.get('officerRoleUniformManualAppointmentRestartVerified') is True,
            f'Missing actual military.3 disk migration PASS: {path}')
    require(sha(path) != source_hash, f'Unmodified old PASS cannot prove a military.4 migration: {path}')
    updated = datetime.fromisoformat(result['updatedUtc'].replace('Z', '+00:00'))
    old_updated = datetime.fromisoformat(previous['updatedUtc'].replace('Z', '+00:00'))
    require(updated > old_updated, f'Migration evidence is not newer than its actual military.3 baseline: {path}')
    for name in ('guards', 'baseId', 'unit', 'manualCommander', 'marker', 'ammunitionChest', 'issuedSlots', 'savedRifleRounds'):
        require(name in previous and result.get(name) == previous[name], f'Migration changed saved {name}: {path}')
    log = repo / f'.superpowers/military4-qa/{prefix}-upgrade-phase1.log'
    text = read_log(log)
    require(VERSION in text and 'CITY_MILITARY_SERVER_AUDIT PASS' in text and 'BUILD SUCCESSFUL' in text,
            f'Missing completed military.4 migration run log: {log}')
    return result, path, baseline_path, log


def json_command(repo: Path, arguments: list[str]) -> dict:
    completed = subprocess.run([sys.executable, '-X', 'utf8', *arguments], cwd=repo,
                               capture_output=True, encoding='utf-8', errors='replace')
    require(completed.returncode == 0,
            f'Read-only verification failed in {repo}: {arguments}\n{completed.stdout}\n{completed.stderr}')
    return json.loads(completed.stdout)


def check_catalogue(neo: Path, forge: Path) -> dict:
    directory = neo / 'docs/制作资料'
    baseline = read_json(directory / 'data/baseline.json')
    require(baseline.get('final_source_freeze') is True, 'Catalogue is a draft; refresh with --final-source-freeze')
    for platform, repo in (('neoforge', neo), ('forge', forge)):
        require(baseline[platform]['head'] == git(repo, 'rev-parse', 'HEAD').decode().strip()
                and baseline[platform]['branch'] == git(repo, 'branch', '--show-current').decode().strip(),
                f'Catalogue Git baseline does not match the current {platform} checkout')
    result = json_command(neo, ['tools/archive_military_catalog.py', '--verify', '--forge-root', str(forge)])
    require(result.get('status') == 'PASS' and not result.get('errors'), f'Catalogue hash/preview/link check failed: {result}')
    return result


def preflight(repos: dict[str, Path]) -> tuple[dict, dict]:
    docs = repos['neoforge'] / 'docs'
    for name in ('MILITARY4_PLAYER_TEST_ZH.md', 'MILITARY3_PLAYER_TEST_ZH.md', 'MILITARY2_PLAYER_TEST_ZH.md'):
        require((docs / name).is_file(), f'Missing required player guide: {name}')
    require((docs / 'verification/MILITARY4_IMPLEMENTATION_20261024_ZH.md').is_file(), 'Missing military.4 implementation record')
    require(bool(list((docs / 'verification').glob('MILITARY4*FINAL*REVIEW*.md'))),
            'Missing final military.4 independent-review record')
    catalogue = check_catalogue(repos['neoforge'], repos['forge'])
    inputs = {}
    for platform, repo in repos.items():
        prefix = 'neo' if platform == 'neoforge' else 'forge'
        qa = repo / '.superpowers/military4-qa'
        jars = list((repo / 'build/libs').glob('*-military.4-bundled.jar'))
        require(len(jars) == 1 and VERSION in jars[0].name, f'Expected one final {VERSION} {platform} JAR: {jars}')
        jar = jars[0]
        with ZipFile(jar) as archive:
            require(archive.testzip() is None, f'Corrupt JAR: {jar}')
            require(not any('GameTest' in name or 'CityMilitaryServerAudit' in name for name in archive.namelist()),
                    f'QA leaked into release JAR: {jar}')
        report_path = qa / f'{prefix}-release-verify.log'
        report = read_json(report_path)
        require(report.get('profile') == 'bundled' and report.get('version') == VERSION
                and report.get('motionFilesVerified') == 253 and report.get('repositoryMotionFilesVerified') == 253
                and report.get('outputWritten') is False,
                f'Missing normal bundled-release/253-motion verification: {report_path}')
        # Re-run the existing read-only verifier against this exact live JAR, resources and distribution.
        current_report = json_command(repo, ['tools/package_release.py', '--profile', 'bundled', '--verify-only'])
        require(current_report == report, f'Release inputs changed after their recorded verification: {report_path}')
        junit, xmls = junit_result(repo, platform)
        regression = game_test(qa / f'{prefix}-regression.log', 111)
        audit_path = repo / f'.superpowers/military4-server-{platform}-final/city-military-audit.json'
        audit = ordinary_audit(audit_path, 'blacklight:city')
        migrated, migration_path, baseline_path, migration_log = migration_audit(repo, platform, prefix)
        inputs[platform] = {'repo': repo, 'jar': jar, 'jarSha256': sha(jar), 'qa': qa, 'junit': junit, 'xmls': xmls,
                            'gameTest': regression, 'ordinaryServerAudit': audit, 'auditPath': audit_path,
                            'military3DiskMigration': migrated, 'migrationPath': migration_path,
                            'baselinePath': baseline_path, 'migrationLog': migration_log,
                            'releaseVerification': current_report}
    neo = inputs['neoforge']
    neo['finalTerrainGameTest'] = game_test(neo['qa'] / 'neo-terrain-final.log', 10)
    world_path = repos['neoforge'] / '.superpowers/military4-world-server-neoforge/city-military-audit.json'
    neo['worldServerAudit'] = ordinary_audit(world_path, 'blacklight:world')
    neo['worldAuditPath'] = world_path
    return inputs, catalogue


def changed_paths(repo: Path) -> list[str]:
    tracked = git(repo, 'diff', '--name-only', '-z', 'HEAD').decode('utf-8').split('\0')
    untracked = git(repo, 'ls-files', '--others', '--exclude-standard', '-z').decode('utf-8').split('\0')
    return sorted(set(filter(None, tracked + untracked)))


def source_overlay(repo: Path, destination: Path) -> dict:
    changed = changed_paths(repo)
    inventory, deleted = {}, []
    with ZipFile(destination, 'x', ZIP_DEFLATED, compresslevel=6) as archive:
        for name in changed:
            path = within(repo, name)
            if not path.exists():
                deleted.append(name)
                continue
            require(path.is_file(), f'Changed source is not a regular file: {path}')
            data = path.read_bytes()
            archive.writestr(name, data)
            inventory[name] = hashlib.sha256(data).hexdigest()
        baseline = {'head': git(repo, 'rev-parse', 'HEAD').decode().strip(),
                    'branch': git(repo, 'branch', '--show-current').decode().strip(),
                    'remote': git(repo, 'remote', 'get-url', 'origin').decode().strip(),
                    'files': inventory, 'deleted': deleted,
                    'instructions': 'Overlay of all uncommitted military.1 + military.2 + military.3 + military.4 changes. '
                                    'Start at head, overlay these files and review deleted explicitly. This is not a standalone Git checkout.'}
        archive.writestr('PATCH-BASE.json', json.dumps(baseline, ensure_ascii=False, indent=2) + '\n')
    require(changed_paths(repo) == changed and all(sha(within(repo, name)) == digest for name, digest in inventory.items()),
            f'Source tree changed during overlay collection: {repo}')
    return baseline


def checked_copytree(source: Path, destination: Path) -> None:
    require(source.is_dir() and not source.is_symlink(), f'Missing or unsafe documentation directory: {source}')
    require(not any(path.is_symlink() for path in source.rglob('*')), f'Documentation symlink needs explicit review: {source}')
    shutil.copytree(source, destination)


def collect() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--output', type=Path)
    parser.add_argument('--check-only', action='store_true', help='Run all read-only gates without creating a staging directory or ZIP')
    args = parser.parse_args()
    workspace = args.workspace.resolve()
    repos = {'neoforge': workspace / 'blacklight-evolution', 'forge': workspace / 'blacklight-evolution-forge-1.20.1'}
    output = (args.output or workspace / 'military-feedback-preview-20261025').resolve()
    require(output != workspace and output.is_relative_to(workspace)
            and not any(output.is_relative_to(repo.resolve()) or repo.resolve().is_relative_to(output) for repo in repos.values()),
            'Delivery must be a separate new directory inside the workspace, outside both repositories')
    stage = output.with_name(output.name + '.staging')
    archive_path = Path(str(output) + '.zip')
    checksum_path = Path(str(archive_path) + '.sha256')
    require(not any(path.exists() for path in (output, stage, archive_path, checksum_path)),
            'Existing output, staging, ZIP or checksum is never overwritten; choose a new output')
    check_old_releases(workspace)
    inputs, catalogue = preflight(repos)
    if args.check_only:
        print(json.dumps({'status': 'PASS', 'outputWritten': False, 'version': VERSION,
                          'catalogue': catalogue, 'platforms': {p: {'junit': v['junit'], 'gameTest': v['gameTest'],
                              'jarSha256': v['jarSha256'], 'ordinaryServerRestart': v['ordinaryServerAudit']['status'],
                              'military3DiskMigration': v['military3DiskMigration']['status']} for p, v in inputs.items()}},
                         ensure_ascii=False, indent=2))
        return

    stage.mkdir(parents=True)
    (stage / 'sources').mkdir()
    summary = {'createdUtc': datetime.now(timezone.utc).isoformat(), 'version': VERSION,
               'publicUpload': False, 'installedGameModified': False, 'protectedOldReleases': PROTECTED,
               'catalogueVerification': catalogue, 'platforms': {}}
    for platform, data in inputs.items():
        repo, jar = data['repo'], data['jar']
        destination = stage / platform
        destination.mkdir()
        shutil.copy2(jar, destination / jar.name)
        baseline = source_overlay(repo, stage / 'sources' / f'{platform}-changes.zip')
        data['sourceBaseline'] = baseline
        (stage / 'sources' / f'{platform}-tracked.diff').write_bytes(git(repo, 'diff', '--binary', 'HEAD'))
        evidence = stage / 'evidence' / platform
        evidence.mkdir(parents=True)
        # These are local QA logs, never the user's PCL latest.log/debug.log or private chat.
        for path in sorted(data['qa'].iterdir()):
            if path.is_file() and path.suffix in ('.log', '.txt', '.json'):
                require(not path.is_symlink(), f'QA log symlink needs review: {path}')
                shutil.copy2(path, evidence / path.name)
        (evidence / 'junit').mkdir()
        for path in data['xmls']:
            shutil.copy2(path, evidence / 'junit' / path.name)
        for source, name in [(data['auditPath'], 'city-military-audit.json'),
                             (data['migrationPath'], 'military3-upgrade-audit.json'),
                             (data['baselinePath'], 'MILITARY3-BASELINE.json')]:
            shutil.copy2(source, evidence / name)
        tests = {key: data[key] for key in ('junit', 'gameTest', 'ordinaryServerAudit', 'military3DiskMigration', 'releaseVerification')}
        if platform == 'neoforge':
            shutil.copy2(data['worldAuditPath'], evidence / 'world-military-audit.json')
            tests.update({key: data[key] for key in ('finalTerrainGameTest', 'worldServerAudit')})
        write_json(evidence / 'TEST-RESULTS.json', tests)
        summary['platforms'][platform] = {'base': baseline['head'], 'branch': baseline['branch'],
            'jar': f'{platform}/{jar.name}', 'jarSha256': data['jarSha256'], 'sourceFiles': len(baseline['files']),
            'ordinaryServerRestart': data['ordinaryServerAudit']['status'], 'tests': tests}

    neo = repos['neoforge']
    # Keep the complete existing docs tree so its catalogue, old guides and relative preview/spec links remain usable.
    checked_copytree(neo / 'docs', stage / 'docs')
    (stage / 'README.md').write_text('''# military.4 完整预览交付

这是完整黑光主模组。退出游戏，把对应版本目录中的唯一主JAR放入PCL当前实例的mods；旧黑光主JAR移到外面备份，保留你已安装的GeckoLib。NeoForge版用于MC1.21.1，Forge版用于MC1.20.1，两端不能混放。源码、文档和整个交付ZIP不用放进mods。本交付不操作PCL、不公开上传。

- [先按简单流程测试](docs/MILITARY4_PLAYER_TEST_ZH.md)
- [兵种、枪弹、建筑、外观及来源资料](docs/制作资料/README.md)
- [军官与换装](docs/MILITARY3_PLAYER_TEST_ZH.md)
- [既有完整功能测试](docs/MILITARY2_PLAYER_TEST_ZH.md)
- [实施结果、真实验证和边界](docs/verification/MILITARY4_IMPLEMENTATION_20261024_ZH.md)

NeoForge自然军事基地接入blacklight:world与blacklight:city，Forge接入现有blacklight:city。只在新生成的安全区块自然布置，不覆盖重建旧地图。驻军按有限岗位逐步发放；周围史莱姆不再占基地守军额度。

枪械姿态、开火反馈和按材质方块破坏已整合；具体实测与配置见指南。模型预览是实际资产的离线渲染，用户旧失败截图另行标注，不冒充客户端GL验收。

sources中的changes.zip保存全部未提交military.1至military.4逐文件覆盖快照，PATCH-BASE.json记录真实分支/HEAD、哈希及删除列表；tracked.diff不能单独替代覆盖快照。evidence保留实际JUnit XML、成功/失败QA日志、普通服重启、新维度审计与旧存档迁移证明。三个旧交付ZIP保持原哈希。原用户完整私人日志未复制进交付。
''', encoding='utf-8')

    check_old_releases(workspace)
    require(check_catalogue(repos['neoforge'], repos['forge']) == catalogue, 'Catalogue changed during collection')
    for platform, data in inputs.items():
        require(sha(data['jar']) == data['jarSha256'], f'{platform} JAR changed during collection')
        baseline = data['sourceBaseline']
        require(git(data['repo'], 'rev-parse', 'HEAD').decode().strip() == baseline['head']
                and git(data['repo'], 'branch', '--show-current').decode().strip() == baseline['branch']
                and changed_paths(data['repo']) == sorted([*baseline['files'], *baseline['deleted']])
                and all(sha(within(data['repo'], name)) == digest for name, digest in baseline['files'].items()),
                f'{platform} checkout changed after its overlay was collected')
    files = {path.relative_to(stage).as_posix(): sha(path) for path in sorted(stage.rglob('*')) if path.is_file()}
    summary['files'] = files
    write_json(stage / 'MANIFEST.json', summary)
    checksums = dict(files, **{'MANIFEST.json': sha(stage / 'MANIFEST.json')})
    (stage / 'SHA256SUMS.txt').write_text(''.join(f'{digest}  {name}\n' for name, digest in sorted(checksums.items())), encoding='utf-8')
    require(all(sha(stage / name) == digest for name, digest in checksums.items()), 'Staged file hash changed before finalization')
    require(not output.exists(), f'Output appeared during collection; staging retained: {stage}')
    stage.rename(output)
    with ZipFile(archive_path, 'x', ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(output.rglob('*')):
            if path.is_file():
                archive.write(path, output.name + '/' + path.relative_to(output).as_posix())
    with ZipFile(archive_path) as archive:
        require(archive.testzip() is None, 'Delivery ZIP integrity check failed')
    with checksum_path.open('x', encoding='ascii') as stream:
        stream.write(sha(archive_path) + '  ' + archive_path.name + '\n')
    check_old_releases(workspace)
    print(json.dumps({'directory': str(output), 'archive': str(archive_path), 'sha256': sha(archive_path),
                      'files': len(checksums) + 1, 'platforms': summary['platforms']}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    collect()
