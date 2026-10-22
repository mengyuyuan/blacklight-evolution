"""Verify and collect a local release candidate. Does not upload or approve releases."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import tomllib
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


PROFILES = ('bundled', 'external-profile', 'local-preview', 'source-only')
MOTION_PREFIX = 'blacklight/bundled_motions/'
PROFILE_DATA_NAMES = {
    'combat-motion-tuning.data', 'consume-placement.data', 'consume-receiver-events.data',
    'receiver-poses.data', 'hunter-consume.data', 'hammer-scales.data', 'weapon-attack-scales.data', 'armor-growth.data',
    'camera-profile.data', 'camera-events.data', 'camera-film.data', 'health-hud.data',
}
SKILL_MOTIONS = ('dev_groundspike', 'dev_air_groundspike', 'dev_tendril', 'dev_air_tendril',
                'dev_critical_pain', 'dev_air_critical_pain', 'bulletdive_drop')


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def manifest_values(data: bytes) -> dict[str, str]:
    text = data.decode('utf-8').replace('\r\n', '\n').replace('\n ', '')
    return dict(line.split(': ', 1) for line in text.splitlines() if ': ' in line)


def git(repo: Path, *args: str) -> bytes:
    return subprocess.check_output(['git', '-C', str(repo), *args])


def profile_inventory(directory: Path) -> dict[str, str]:
    require(directory.is_dir(), f'Missing motion input directory: {directory}')
    files = sorted(p for p in directory.iterdir()
                   if p.is_file() and (p.suffix == '.json' or p.name in PROFILE_DATA_NAMES))
    require(bool(files), f'Empty motion input profile: {directory}')
    require(not any(p.is_symlink() for p in files), f'Motion symlinks need explicit review: {directory}')
    return {p.name: digest(p.read_bytes()) for p in files}


def repository_profile(repo: Path) -> dict[str, str]:
    manifest = json.loads((repo / 'art/motion-profile-manifest.json').read_bytes())
    require(manifest.get('version') == 1 and isinstance(manifest.get('files'), dict), 'Invalid repository motion manifest')
    expected = {}
    for name, entry in manifest['files'].items():
        require(Path(name).name == name and not any(c in name for c in '/\\'), f'Invalid motion manifest filename: {name}')
        path = repo / 'art/motion-profile' / name
        require(path.is_file() and not path.is_symlink(), f'Missing repository motion: {name}')
        data = path.read_bytes()
        require(len(data) == entry['bytes'], f'Repository motion manifest size mismatch: {name}')
        sha = digest(data)
        if entry.get('sha256') is not None:
            require(sha == entry['sha256'], f'Repository motion manifest hash mismatch: {name}')
        expected[name] = sha
    require(profile_inventory(repo / 'art/motion-profile') == expected, 'Repository motion inventory differs from its manifest')
    return expected


def extension_motion_names() -> set[str]:
    return ({f'{name}{suffix}.json' for name in SKILL_MOTIONS for suffix in ('', '_charge', '_recovery')}
            | {f'{name}_aim_{direction}.json' for name in ('dev_critical_pain', 'dev_air_critical_pain') for direction in ('up', 'down')}
            | {'bulletdive_drop_land.json', 'bulletdive_drop_flight.json'})


def java_class_names(root: Path) -> set[str]:
    names = set()
    for path in root.rglob('*.java'):
        text = path.read_text(encoding='utf-8')
        match = re.search(r'^\s*package\s+([\w.]+)\s*;', text, re.MULTILINE)
        prefix = match.group(1).replace('.', '/') + '/' if match else ''
        names.add(prefix + path.stem)
    return names


def qa_class_names(repo: Path) -> set[str]:
    return ({name for directory in (repo / 'src').iterdir() if directory.is_dir() and directory.name != 'main'
             for name in java_class_names(directory)} | java_class_names(repo / 'tools'))


def forbidden_reference_asset(name: str) -> bool:
    basename = name.rsplit('/', 1)[-1]
    return (basename == 'devastator-fx.data' or basename.startswith('devfx_')
            or '/private_devfx/' in name or basename.startswith('source_biomass.')
            or basename.startswith('DevastatorSourceMesh') and name.endswith('.class'))


def forbidden_runtime(name: str, qa_names: set[str]) -> bool:
    if forbidden_reference_asset(name):
        return True
    if '/preview/' in name or '/gametest/' in name or name.startswith('blacklight/test/'):
        return True
    if name == 'data/blacklight/structure/wall_test.nbt':
        return True
    if name.endswith('.class'):
        top_level = name[:-6].split('$', 1)[0]
        return (top_level in qa_names
                or re.fullmatch(r'com/codex/blacklight/client/Standalone\w*(?:Smoke|Checks)', top_level) is not None
                or top_level == 'com/codex/blacklight/client/InfectedCompositionSmoke')
    return False


def private_curve(data: bytes) -> bool:
    if data.lstrip().startswith(b'{') and b'"privateNotice"' in data:
        parsed = json.loads(data)
        if isinstance(parsed, dict) and parsed.get('format') == 'MATRIX':
            provenance = parsed.get('provenance')
            # Current project motions retain this legacy key as descriptive
            # metadata; explicit private inputs still fail this check.
            descriptive = (isinstance(parsed.get('privateNotice'), str)
                           and parsed['privateNotice'].startswith(
                               'Project-modified and adapted Blacklight motion data. Initial lineage / technical context: '))
            adapted = (isinstance(provenance, dict)
                       and provenance.get('schema') == 1
                       and provenance.get('project') == 'Blacklight: Ascension'
                       and provenance.get('currentStatus') == 'project_modified_and_adapted')
            return not (descriptive and adapted)
    return False


def source_snapshot(repo: Path, output: Path | None, baseline: dict[str, str] | None = None,
                    private_hashes: set[str] | None = None) -> dict[str, str]:
    tracked = {p.decode('utf-8') for p in git(repo, 'ls-files', '-z').split(b'\0') if p}
    untracked = {p.decode('utf-8') for p in git(repo, 'ls-files', '--others', '--exclude-standard', '-z').split(b'\0') if p}
    root_files = {'README.md', 'LICENSE', 'THIRD_PARTY_ASSETS.md', 'THIRD_PARTY_NOTICES.md',
                  'CONTRIBUTING.md', 'TEMPLATE_LICENSE.txt', 'build.gradle', 'settings.gradle',
                  'gradle.properties', 'gradlew', 'gradlew.bat', '.gitignore'}

    def allowed(name: str) -> bool:
        return name in root_files or name.startswith(('src/', 'gradle/', '.github/', 'docs/', 'tools/', 'art/'))

    files = sorted(name for name in tracked | untracked if allowed(name) and (repo / name).is_file())
    baseline = repository_profile(repo) if baseline is None else baseline
    private_hashes = private_hashes or set()
    private_names = extension_motion_names()
    payloads = {}
    for name in files:
        require('bundled_motions/' not in name, f'Generated/private runtime motion path found in source: {name}')
        require(not forbidden_reference_asset(name), f'Private effect reference asset found in source: {name}')
        path = repo / name
        require(not path.is_symlink(), f'Source symlink needs explicit review: {name}')
        require(path.resolve().is_relative_to(repo.resolve()), f'Source path escapes repository: {name}')
        data = path.read_bytes()
        sha = digest(data)
        require(sha not in private_hashes, f'Private skill/recovery curve leaked into source: {name}')
        if name.startswith('art/motion-profile/'):
            motion_name = name[len('art/motion-profile/'):]
            require(motion_name == 'README.md' or motion_name in baseline and sha == baseline[motion_name],
                    f'Unexpected/changed baseline motion in source snapshot: {name}')
            if motion_name == 'README.md':
                require(not private_curve(data), f'Private MATRIX curve disguised as baseline documentation: {name}')
        else:
            require(path.name not in private_names, f'Private skill/recovery curve leaked into source: {name}')
            require(not private_curve(data), f'Private MATRIX curve leaked into source: {name}')
        payloads[name] = data
    hashes = {name: digest(data) for name, data in payloads.items()}
    if output is None:
        return hashes
    with ZipFile(output, 'w', compression=ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in payloads.items():
            info = ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            info.external_attr = (0o100755 if name == 'gradlew' else 0o100644) << 16
            archive.writestr(info, data)
        info = ZipInfo('SOURCE_MANIFEST.json', date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        archive.writestr(info, json.dumps(hashes, ensure_ascii=False, indent=2))
    with ZipFile(output) as archive:
        require(json.loads(archive.read('SOURCE_MANIFEST.json')) == hashes, 'Source manifest identity mismatch')
        for name, sha in hashes.items():
            require(digest(archive.read(name)) == sha, f'Source snapshot identity mismatch: {name}')
    return hashes


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--build-dir', type=Path, help='Defaults to <repo>/build')
    parser.add_argument('--output', type=Path, help='Defaults to a separate release/local-<version>-<profile> directory')
    parser.add_argument('--profile', choices=PROFILES, default='bundled')
    parser.add_argument('--private-motion-directory', type=Path, help='Exact external-profile/local-preview build input directory; every staged file SHA-256 is verified')
    parser.add_argument('--verify-only', action='store_true', help='Verify artifacts and source inputs without creating or changing a release directory')
    args = parser.parse_args()
    repo = args.repo.resolve()
    build = (args.build_dir or repo / 'build').resolve()
    props = dict(line.split('=', 1) for line in (repo / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                 if '=' in line and not line.lstrip().startswith('#'))
    version = props['mod_version']
    stem = f"{props['mod_archive_name']}-MC{props['minecraft_version']}-NeoForge-{version}"
    jar = build / 'libs' / f'{stem}-{args.profile}.jar'
    dist = build / 'distributions' / f'{stem}-{args.profile}.zip'
    require(jar.is_file() and dist.is_file(), f'Build the requested {args.profile} artifacts first')
    notices = ('LICENSE', 'THIRD_PARTY_ASSETS.md', 'THIRD_PARTY_NOTICES.md')
    baseline = repository_profile(repo)
    external = args.profile in ('external-profile', 'local-preview')
    bundled = args.profile != 'source-only'
    if external:
        require(args.private_motion_directory is not None, 'Provide the exact --private-motion-directory used for this build')
        profile = args.private_motion_directory.resolve()
        expected = profile_inventory(profile)
        if args.profile == 'external-profile':
            require(set(baseline) <= set(expected), 'External profile is missing baseline runtime motion files')
            missing = extension_motion_names() - expected.keys()
            require(not missing, f'External skill/recovery profile is incomplete: {sorted(missing)}')
    else:
        require(args.private_motion_directory is None, '--private-motion-directory only applies to external-profile/local-preview builds')
        expected = baseline if bundled else {}
    private_hashes = set(expected.values()) - set(baseline.values()) if external else set()
    qa_names = qa_class_names(repo)
    with ZipFile(jar) as archive:
        names = set(archive.namelist())
        require(len(names) == len(archive.namelist()), 'Duplicate JAR entries')
        meta = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode('utf-8'))
        mod = next(m for m in meta['mods'] if m['modId'] == props['mod_id'])
        require(mod['version'] == version and mod['authors'] == '末者', 'Version or author mismatch')
        manifest = manifest_values(archive.read('META-INF/MANIFEST.MF'))
        require(manifest.get('Blacklight-Build-Profile') == args.profile, 'Build profile mismatch')
        if args.profile in ('bundled', 'external-profile'):
            require(manifest.get('Blacklight-Motion-Source') == ('external' if external else 'repository'), 'Motion source marker mismatch')
            require(manifest.get('Blacklight-Bundled-Motions') == 'true', 'Bundled motion marker mismatch')
            require(manifest.get('Blacklight-Asset-Notice') == 'THIRD_PARTY_ASSETS.md', 'Asset notice marker mismatch')
        else:
            require(manifest.get('Blacklight-Private-Assets') == str(external).lower(), 'Legacy private data marker mismatch')
            require(manifest.get('Blacklight-Public-Release-Ready') == 'false', 'Legacy release readiness marker mismatch')
        require(manifest.get('Blacklight-Public-Release-Ready', 'false') == 'false', 'This collector cannot approve public releases')
        for name in notices:
            require(archive.read(name) == (repo / name).read_bytes(), f'Stale or missing notice: {name}')
        motions = {name[len(MOTION_PREFIX):]: digest(archive.read(name)) for name in sorted(names)
                   if name.startswith(MOTION_PREFIX) and not name.endswith('/')}
        require(motions == expected, 'Bundled resources differ from the exact selected input profile/manifest')
        forbidden = sorted(name for name in names if forbidden_runtime(name, qa_names))
        require(not forbidden, f'Reference FX, source Shader or opt-in QA content leaked into JAR: {forbidden}')
        for name in names:
            if name.endswith('/') or name.startswith(MOTION_PREFIX):
                continue
            data = archive.read(name)
            require(Path(name).name not in extension_motion_names() and digest(data) not in private_hashes,
                    f'External skill/recovery curve leaked outside the runtime motion directory: {name}')
            require(not private_curve(data), f'Private MATRIX curve leaked outside the runtime motion directory: {name}')
        class_root = build / 'classes/java/main'
        production = java_class_names(repo / 'src/main/java')
        production = {name for name in production if not forbidden_runtime(name + '.class', qa_names)
                      and name.rsplit('/', 1)[-1] not in ('package-info', 'module-info')}
        classes = [path for path in class_root.rglob('*.class')
                   if path.relative_to(class_root).as_posix()[:-6].split('$', 1)[0] in production]
        require(bool(classes), 'No compiled classes to verify')
        compiled_roots = {path.relative_to(class_root).as_posix()[:-6].split('$', 1)[0] for path in classes}
        require(production <= compiled_roots, f'Production sources were not compiled: {sorted(production - compiled_roots)}')
        for path in classes:
            name = path.relative_to(class_root).as_posix()
            require(archive.read(name) == path.read_bytes(), f'Stale class: {name}')
        mixins = json.loads(archive.read('blacklight.mixins.json'))
        for name in mixins['mixins'] + mixins['client']:
            require((mixins['package'] + '.' + name).replace('.', '/') + '.class' in names, f'Missing mixin: {name}')
        for language in ('en_us', 'zh_cn'):
            json.loads(archive.read(f'assets/blacklight/lang/{language}.json'))
        resources = {}
        for resource_root in (repo / 'src/main/resources', repo / 'src/generated/resources'):
            for path in resource_root.rglob('*'):
                if not path.is_file() or path.suffix == '.bbmodel' or '.cache' in path.parts:
                    continue
                name = path.relative_to(resource_root).as_posix()
                if forbidden_runtime(name, qa_names):
                    continue
                require(not path.is_symlink(), f'Production resource symlink needs explicit review: {name}')
                data = path.read_bytes()
                require(name not in resources or resources[name] == digest(data), f'Conflicting production resource roots: {name}')
                resources[name] = digest(data)
                require(archive.read(name) == data, f'Stale or missing production resource: {name}')
    with ZipFile(dist) as archive:
        require(len(set(archive.namelist())) == len(archive.namelist()), 'Duplicate distribution entries')
        require([name for name in archive.namelist() if name.endswith('.jar')] == ['mods/' + jar.name],
                'Distribution must contain exactly this build, without dependencies or old JARs')
        require(archive.read('mods/' + jar.name) == jar.read_bytes(), 'Distribution JAR mismatch')
        for name in (*notices, 'README.md'):
            require(archive.read(name) == (repo / name).read_bytes(), f'Distribution document mismatch: {name}')
        docs = (sorted(path for path in (repo / 'docs').rglob('*') if path.is_file())
                if args.profile in ('bundled', 'external-profile') else
                [repo / 'docs' / name for name in ('INSTALL_ZH.md', 'ACCEPTANCE_0.2.0.md', 'RELEASE_PREPARATION_ZH.md', 'PLATFORM_PLAN_ZH.md')])
        for path in docs:
            name = 'docs/' + path.relative_to(repo / 'docs').as_posix()
            require(not path.is_symlink(), f'Distribution document symlink needs explicit review: {name}')
            require(archive.read(name) == path.read_bytes(), f'Distribution document mismatch: {name}')
        if args.profile in ('bundled', 'external-profile'):
            require({name for name in archive.namelist() if name.startswith('docs/') and not name.endswith('/')}
                    == {'docs/' + path.relative_to(repo / 'docs').as_posix() for path in docs}, 'Distribution docs inventory mismatch')

    verified_sources = source_snapshot(repo, None, baseline, private_hashes)
    if args.verify_only:
        print(json.dumps({'profile': args.profile, 'version': version, 'motionFilesVerified': len(motions),
                          'repositoryMotionFilesVerified': len(baseline), 'productionClassesVerified': len(classes),
                          'productionResourcesVerified': len(resources),
                          'sourceFilesVerified': len(verified_sources), 'distributionDocumentsVerified': len(docs) + len(notices) + 1,
                          'outputWritten': False}, ensure_ascii=False, indent=2))
        return
    output_suffix = '' if args.profile == 'local-preview' else '-' + args.profile
    output = (args.output or repo / 'release' / f'local-{version}{output_suffix}').resolve()
    existing_manifest = output / 'RELEASE-MANIFEST.json'
    if existing_manifest.is_file():
        previous = json.loads(existing_manifest.read_text(encoding='utf-8'))
        require(previous.get('profile') == args.profile, 'Use a separate output directory for each build profile')
    require(not any(path for other in PROFILES if other != args.profile for path in output.glob(f'{stem}-{other}.*')),
            'Output contains another build profile; choose a separate directory')
    output.mkdir(parents=True, exist_ok=True)
    for path in (jar, dist):
        if path.resolve() != (output / path.name).resolve():
            shutil.copy2(path, output / path.name)
    source = output / f'{stem}-sources.zip'
    sources = source_snapshot(repo, source, baseline, private_hashes)
    require(sources == verified_sources, 'Source inputs changed during collection; rerun with a stable working tree')
    summary = {
        'name': props['mod_name'], 'author': mod['authors'], 'minecraft': props['minecraft_version'],
        'version': version, 'profile': args.profile, 'publicReleaseReady': False,
        'compiledClassesVerified': len(classes), 'motionFiles': len(motions), 'privateFiles': len(motions) if external else 0,
        'productionResourcesVerified': len(resources),
        'repositoryMotionFiles': len(baseline), 'externalMotionFiles': len(set(motions) - baseline.keys()) if external else 0,
        'baseCommit': git(repo, 'rev-parse', 'HEAD').decode().strip(),
        'workingTreeChanged': bool(git(repo, 'status', '--porcelain', '--untracked-files=normal').strip()),
        'sourceFiles': len(sources),
        'checks': ['production-class identity', 'metadata and author', 'license files', 'exact motion profile/manifest',
                   'private FX and QA exclusion', 'mixin classes', 'language JSON', 'distribution/document identity', 'source snapshot isolation'],
        'notPerformed': ['automated gameplay tests', 'dedicated-server or multiplayer acceptance', 'public upload'],
        'files': {path.name: digest(path.read_bytes()) for path in (jar, dist, source)},
        'motionResourceHashes': motions,
        'privateResourceHashes': motions if external else {},
    }
    (output / 'RELEASE-MANIFEST.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (output / 'SHA256SUMS.txt').write_text(''.join(f'{sha}  {name}\n' for name, sha in summary['files'].items()), encoding='utf-8')
    print(json.dumps({key: value for key, value in summary.items()
                      if key not in ('privateResourceHashes', 'motionResourceHashes')}, ensure_ascii=False, indent=2))
    print(output)


if __name__ == '__main__':
    main()
