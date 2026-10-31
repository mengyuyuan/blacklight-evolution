"""Verify and collect a local release candidate. Does not upload or approve releases."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tomllib
import tempfile
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


PROFILES = ('bundled', 'external-profile', 'local-preview', 'source-only')
MOTION_PREFIX = 'blacklight/bundled_motions/'
PROFILE_DATA_NAMES = {
    'combat-motion-tuning.data', 'consume-placement.data', 'consume-receiver-events.data',
    'receiver-poses.data', 'hunter-consume.data', 'hammer-scales.data', 'weapon-attack-scales.data', 'armor-growth.data',
    'camera-profile.data', 'camera-events.data', 'camera-film.data', 'health-hud.data', 'evolved-poses.data',
}
SKILL_MOTIONS = ('dev_groundspike', 'dev_air_groundspike', 'dev_tendril', 'dev_air_tendril',
                'dev_critical_pain', 'dev_air_critical_pain', 'bulletdive_drop')
INACTIVE_RESOURCES = {'data/blacklight/recipe/military_fuel_can.json'}
# Exact Maven artifact used by the author's pinned compact-map/motion decoder.
XZ_DEPENDENCY_SHA256 = '95c63c1a55b22dd6453890a419cc1a640f790bbf7d8ae82db1e30aefefb08888'


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
    if re.search(r'(?:VehicleViewSmoke|PlayerGL\w*Smoke|VehiclePlayerGL\w*)(?:\$|\.|/)', name, re.I):
        return True
    if name.endswith('.java') and any(name.endswith(qa+'.java') for qa in qa_names):
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


def same_stream_content(left, right) -> bool:
    """Compare large artifacts without retaining either whole payload."""
    while True:
        a, b = left.read(1024 * 1024), right.read(1024 * 1024)
        if a != b:
            return False
        if not a:
            return True


def file_sha256(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def source_snapshot(repo: Path, output: Path | None, baseline: dict[str, str] | None = None,
                    private_hashes: set[str] | None = None) -> dict[str, str]:
    tracked = {p.decode('utf-8') for p in git(repo, 'ls-files', '-z').split(b'\0') if p}
    untracked = {p.decode('utf-8') for p in git(repo, 'ls-files', '--others', '--exclude-standard', '-z').split(b'\0') if p}
    root_files = {'README.md', 'LICENSE', 'THIRD_PARTY_ASSETS.md', 'THIRD_PARTY_NOTICES.md',
                  'CONTRIBUTING.md', 'TEMPLATE_LICENSE.txt', 'build.gradle', 'settings.gradle',
                  'gradle.properties', 'gradlew', 'gradlew.bat', '.gitignore'}

    def allowed(name: str) -> bool:
        return name in root_files or name.startswith(('src/', 'gradle/', '.github/', 'docs/', 'tools/', 'art/', 'licenses/'))

    files = sorted(name for name in tracked | untracked if allowed(name) and (repo / name).is_file())
    baseline = repository_profile(repo) if baseline is None else baseline
    private_hashes = private_hashes or set()
    private_names = extension_motion_names()
    hashes: dict[str, str] = {}
    temporary = output.with_suffix(output.suffix + '.tmp') if output is not None else None
    archive = ZipFile(temporary, 'w', compression=ZIP_DEFLATED, compresslevel=9) if temporary is not None else None
    try:
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
            hashes[name] = sha
            if archive is not None:
                info = ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
                info.compress_type = ZIP_DEFLATED
                info.external_attr = (0o100755 if name == 'gradlew' else 0o100644) << 16
                archive.writestr(info, data)
        if archive is not None:
            info = ZipInfo('SOURCE_MANIFEST.json', date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, json.dumps(hashes, ensure_ascii=False, indent=2))
    finally:
        if archive is not None:
            archive.close()
    if output is None:
        return hashes
    with ZipFile(temporary) as archive:
        require(json.loads(archive.read('SOURCE_MANIFEST.json')) == hashes, 'Source manifest identity mismatch')
        for name, sha in hashes.items():
            require(digest(archive.read(name)) == sha, f'Source snapshot identity mismatch: {name}')
    temporary.replace(output)
    return hashes


def verify_runtime_dependencies(archive: ZipFile) -> None:
    """Permit only the declared, byte-pinned XZ decoder required by compact assets."""
    names = set(archive.namelist())
    require(not any(name.lower().startswith('software/bernie/geckolib/') for name in names),
            'GeckoLib leaked into JAR')
    nested = {name for name in names if name.lower().endswith('.jar')}
    metadata_name = 'META-INF/jarjar/metadata.json'
    if not nested:
        if metadata_name in names:
            require(json.loads(archive.read(metadata_name)).get('jars') == [], 'Declared embedded dependency is missing')
        return
    require(metadata_name in names, 'Nested dependency lacks jarJar metadata')
    libraries = json.loads(archive.read(metadata_name)).get('jars')
    require(isinstance(libraries, list) and len(libraries) == 1, 'Unexpected nested dependencies')
    decoder = libraries[0]
    require(decoder.get('identifier') == {'group': 'org.tukaani', 'artifact': 'xz'}
            and decoder.get('version', {}).get('artifactVersion') == '1.10', 'Unexpected embedded decoder')
    path = decoder.get('path', '')
    require(path.startswith('META-INF/jarjar/') and '\\' not in path and '..' not in path.split('/')
            and nested == {path}, 'Embedded decoder path or inventory mismatch')
    require(digest(archive.read(path)) == XZ_DEPENDENCY_SHA256, 'Embedded XZ decoder differs from the tested 1.10 artifact')


def verify_compact_motions(repo: Path, build: Path, jar: Path, profile: Path) -> None:
    """The same bounded Java decoder must restore every original input byte."""
    classes = build / 'classes/java/resourceTools'
    require((classes / 'CompactMotionPacker.class').is_file(), 'Compile resourceTools classes before checking compact motions')
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')) if java_home else shutil.which('java')
    require(bool(java), 'Java 21 is required to verify the exact compact motion originals')
    temporary_root = build / 'tmp/compact-verification'
    temporary_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='decoder-', dir=temporary_root) as directory:
        target = Path(directory).resolve()
        require(target.is_relative_to(temporary_root.resolve()), 'Decoder temporary path escaped its build directory')
        with ZipFile(jar) as archive:
            metadata = json.loads(archive.read('META-INF/jarjar/metadata.json'))
            libraries = [item for item in metadata['jars'] if item['identifier'] == {'group': 'org.tukaani', 'artifact': 'xz'}]
            require(len(libraries) == 1 and libraries[0]['version']['artifactVersion'] == '1.10', 'Missing tested pure-Java XZ dependency')
            name = libraries[0]['path']
            require(name.startswith('META-INF/jarjar/') and '..' not in name.split('/'), 'Invalid embedded decoder path')
            library = target / 'xz.jar'
            library.write_bytes(archive.read(name))
        command = [java, '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp', os.pathsep.join((str(classes), str(library))),
                   'CompactMotionPacker', '--verify', str(jar), str(profile)]
        result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=600)
        require(result.returncode == 0, f'Exact compact motion restoration failed: {result.stdout}\n{result.stderr}')


def geographic_city_selected(repo: Path, archive: ZipFile) -> bool:
    name = 'data/blacklight/city/generation.json'
    names = set(archive.namelist())
    if name not in names:
        return False
    profile = json.loads(archive.read(name))
    require(profile.get('schema') == 1 and profile.get('mode') in ('archive', 'geographic'), 'Invalid city generation profile')
    if profile['mode'] != 'geographic':
        return False
    require(archive.read(name) == (repo / 'src/main/resources' / name).read_bytes(), 'Stale geographic generation profile')
    require(not any(n.startswith(('data/blacklight/city/los_angeles_full/', 'data/blacklight/city/los_angeles_compact/'))
                    for n in names), 'Baked municipal archives leaked into the geographic package')
    for city in ('shenzhen', 'los_angeles'):
        index_name = f'data/blacklight/worldmap/{city}/index.json'
        index = json.loads(archive.read(index_name))
        require(index.get('schema') == 1 and index.get('region') == city and index.get('tiles'), f'Missing geographic city: {city}')
        for tile in index['tiles']:
            tile_name = f"data/blacklight/worldmap/{city}/{tile['x']}_{tile['z']}.json.gz"
            require(tile_name in names or tile_name[:-3]+'.xz' in names, f'Missing geographic tile: {tile_name}')
    require('data/blacklight/city/futian/map.json' in names
            and 'data/blacklight/city/los_angeles/map.json' in names, 'Authored city cores must be retained')
    return True


def verify_compact_maps(repo: Path, build: Path, jar: Path) -> None:
    classes = build / 'classes/java/resourceTools'
    require((classes / 'CompactMapPacker.class').is_file(), 'Compile resourceTools classes before checking compact maps')
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')) if java_home else shutil.which('java')
    require(bool(java), 'Java 21 is required to verify the compact maps')
    temporary_root = build / 'tmp/compact-map-verification'
    temporary_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='decoder-', dir=temporary_root) as directory:
        with ZipFile(jar) as archive:
            metadata = json.loads(archive.read('META-INF/jarjar/metadata.json'))
            libraries = [item for item in metadata['jars'] if item['identifier'] == {'group': 'org.tukaani', 'artifact': 'xz'}]
            require(len(libraries) == 1 and libraries[0]['version']['artifactVersion'] == '1.10', 'Missing tested map decoder')
            name = libraries[0]['path']
            require(name.startswith('META-INF/jarjar/') and '..' not in name.split('/'), 'Invalid embedded map decoder path')
            library = Path(directory) / 'xz.jar'
            library.write_bytes(archive.read(name))
        command = [java, '-Xmx384m', '-XX:ActiveProcessorCount=2', '-cp', os.pathsep.join((str(classes), str(library))),
                   'CompactMapPacker', '--verify', str(jar), str(repo / 'src/main/resources')]
        result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=600)
        require(result.returncode == 0, f'Exact compact map restoration failed: {result.stdout}\n{result.stderr}')


def compact_city_selected(repo: Path, archive: ZipFile) -> bool:
    name = 'data/blacklight/city/los_angeles_compact/index.json'
    if name not in archive.namelist():
        return False
    index = json.loads(archive.read(name))
    legacy_path = repo / 'src/main/resources/data/blacklight/city/los_angeles_full/index.json'
    legacy = json.loads(legacy_path.read_bytes())
    require(index.get('complete') is True and index['schema'] == 3, 'Incomplete compact municipality')
    require(index['sourceIndexSHA256'] == file_sha256(legacy_path), 'Compact map has a different baseline city')
    for key in ('projection', 'worldBounds', 'nativeChunks', 'coverageSHA256'):
        require(index[key] == legacy[key], f'Compact map changed its geographic reference: {key}')
    source = {(r['x'], r['z']): r for r in legacy['regions']}
    converted = {(r['x'], r['z']): r for r in index['regions']}
    require(converted.keys() == source.keys(), 'Compact city lost or added geographic regions')
    require(sum(row['chunks'] for row in converted.values()) == legacy['nativeChunks'], 'Compact city chunk coverage differs')
    for key, row in converted.items():
        require(row['sourceSha256'] == source[key]['sha256'] and row['verifiedCells'] > 0, f'Unverified compact region: {key}')
    require(not any(n.startswith('data/blacklight/city/los_angeles_full/') for n in archive.namelist()), 'Redundant original municipal payload in compact build')
    return True


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--build-dir', type=Path, help='Defaults to <repo>/build')
    parser.add_argument('--output', type=Path, help='Defaults to a separate release/local-<version>-<profile> directory')
    parser.add_argument('--profile', choices=PROFILES, default='bundled')
    parser.add_argument('--private-motion-directory', type=Path, help='Exact external-profile/local-preview build input directory; every staged file SHA-256 is verified')
    parser.add_argument('--verify-only', action='store_true', help='Verify artifacts and source inputs without creating or changing a release directory')
    parser.add_argument('--directory-only', action='store_true', help='Verify a prepared ordinary folder instead of a transport ZIP; requires --verify-only and --output')
    args = parser.parse_args()
    if args.directory_only and (not args.verify_only or args.output is None):
        parser.error('--directory-only requires --verify-only and --output')
    repo = args.repo.resolve()
    build = (args.build_dir or repo / 'build').resolve()
    props = dict(line.split('=', 1) for line in (repo / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                 if '=' in line and not line.lstrip().startswith('#'))
    version = props['mod_version']
    stem = f"{props['mod_archive_name']}-MC{props['minecraft_version']}-NeoForge-{version}"
    jar = build / 'libs' / f'{stem}-{args.profile}.jar'
    dist = build / 'distributions' / f'{stem}-{args.profile}.zip'
    require(jar.is_file() and (args.directory_only or dist.is_file()), f'Build the requested {args.profile} artifacts first')
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
        verify_runtime_dependencies(archive)
        inactive = {}
        for name in INACTIVE_RESOURCES:
            path = repo/'src/main/resources'/name
            require(path.is_file() and not path.is_symlink(), f'Dormant resource must remain on disk: {name}')
            require(name not in names, f'Inactive resource leaked into JAR: {name}')
            inactive[name] = digest(path.read_bytes())
        meta = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode('utf-8'))
        mod = next(m for m in meta['mods'] if m['modId'] == props['mod_id'])
        declared_meta = tomllib.loads((repo / 'src/main/templates/META-INF/neoforge.mods.toml').read_text(encoding='utf-8'))
        declared_mod = next(m for m in declared_meta['mods'] if m['modId'] == '${mod_id}')
        require(mod['version'] == version, 'Version mismatch')
        require(mod.get('authors') == declared_mod.get('authors') and '末者' in mod.get('authors', ''),
                'Author attribution differs from the source declaration or omits the original author')
        require(mod.get('credits') == declared_mod.get('credits'), 'Contributor credits differ from the source declaration')
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
        compact_motions = any(name.startswith(MOTION_PREFIX) and name.endswith('.jz') for name in names)
        if compact_motions:
            verify_compact_motions(repo, build, jar, profile if external else repo / 'art/motion-profile')
            logical = [name[len(MOTION_PREFIX):-3] if name.endswith('.jz') else name[len(MOTION_PREFIX):]
                       for name in names if name.startswith(MOTION_PREFIX) and not name.endswith('/') and name != MOTION_PREFIX + 'compact-index.json']
            require(len(logical) == len(set(logical)) and set(logical) == set(expected), 'Compact logical motion inventory differs')
            motions = dict(expected)  # Each actual decoded byte was compared by the Java verifier above.
            require(archive.read('META-INF/licenses/XZ-JAVA-0BSD.txt') == (repo / 'licenses/XZ-JAVA-0BSD.txt').read_bytes(), 'Missing decoder license')
        else:
            motions = {name[len(MOTION_PREFIX):]: digest(archive.read(name)) for name in sorted(names)
                       if name.startswith(MOTION_PREFIX) and not name.endswith('/')}
        require(motions == expected, 'Bundled resources differ from the exact selected input profile/manifest')
        geographic_city = geographic_city_selected(repo, archive)
        compact_city = compact_city_selected(repo, archive)
        compact_maps = any(name.endswith('.xz') for name in names)
        if compact_maps:
            verify_compact_maps(repo, build, jar)
        forbidden = sorted(name for name in names if forbidden_runtime(name, qa_names))
        require(not forbidden, f'Reference FX, source Shader or opt-in QA content leaked into JAR: {forbidden}')
        entry_hashes = {}
        for name in sorted(names, key=lambda item: archive.getinfo(item).header_offset):
            if name.endswith('/') or name.startswith(MOTION_PREFIX):
                continue
            data = archive.read(name)
            entry_hashes[name] = digest(data)
            require(not name.endswith('.jz'), f'Compact motion outside its verified profile: {name}')
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
                if name.endswith(('.verified.json', '.tmp')) or compact_city and name.startswith('data/blacklight/city/los_angeles_full/'):
                    continue
                if geographic_city and name.startswith(('data/blacklight/city/los_angeles_full/', 'data/blacklight/city/los_angeles_compact/')):
                    continue
                if name in INACTIVE_RESOURCES:
                    require(not path.is_symlink() and digest(path.read_bytes()) == inactive[name], f'Conflicting dormant resource: {name}')
                    continue
                if forbidden_runtime(name, qa_names):
                    continue
                require(not path.is_symlink(), f'Production resource symlink needs explicit review: {name}')
                data = path.read_bytes()
                require(name not in resources or resources[name] == digest(data), f'Conflicting production resource roots: {name}')
                resources[name] = digest(data)
                if compact_maps and name.endswith('.gz') and name[:-3]+'.xz' in names:
                    # Every decoded byte and the complete logical inventory were checked above.
                    require(name not in names, f'Both map encodings shipped: {name}')
                    continue
                actual = entry_hashes.get(name)
                if actual is None and name in names:
                    actual = digest(archive.read(name))
                require(actual == digest(data), f'Stale or missing production resource: {name}')
    if args.directory_only:
        folder = args.output.resolve()
        installed = folder / 'mods' / jar.name
        dependency_name = f"geckolib-neoforge-{props['minecraft_version']}-{props['geckolib_version']}.jar"
        require(installed.is_file(), 'Missing main mod in the ordinary folder')
        require({path.name for path in (folder / 'mods').glob('*.jar')} == {jar.name, dependency_name}, 'Ordinary folder must contain exactly this mod and its GeckoLib dependency')
        require({path.resolve() for path in folder.rglob('*.jar')} == {installed.resolve(), (folder / 'mods' / dependency_name).resolve()}, 'Unexpected loose dependency or old JAR outside mods/')
        if not os.path.samefile(installed, jar):
            with installed.open('rb') as packed, jar.open('rb') as built:
                require(same_stream_content(packed, built), 'Ordinary-folder JAR differs from the verified build')
        docs = sorted(path for path in (repo / 'docs').rglob('*') if path.is_file())
        for name in (*notices, 'README.md'):
            require((folder / name).read_bytes() == (repo / name).read_bytes(), f'Ordinary-folder document mismatch: {name}')
        for path in docs:
            require((folder / path.relative_to(repo)).read_bytes() == path.read_bytes(), f'Ordinary-folder document mismatch: {path.name}')
    else:
        with ZipFile(dist) as archive:
            require(len(set(archive.namelist())) == len(archive.namelist()), 'Duplicate distribution entries')
            require([name for name in archive.namelist() if name.endswith('.jar')] == ['mods/' + jar.name],
                    'Distribution must contain exactly this build, without dependencies or old JARs')
            with archive.open('mods/' + jar.name) as packed, jar.open('rb') as built:
                require(same_stream_content(packed, built), 'Distribution JAR mismatch')
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
                          'inactiveResourcesVerified': inactive, 'geckoLibPackaged': False,
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
        'inactiveResourcesVerified': inactive, 'geckoLibPackaged': False,
        'repositoryMotionFiles': len(baseline), 'externalMotionFiles': len(set(motions) - baseline.keys()) if external else 0,
        'baseCommit': git(repo, 'rev-parse', 'HEAD').decode().strip(),
        'workingTreeChanged': bool(git(repo, 'status', '--porcelain', '--untracked-files=normal').strip()),
        'sourceFiles': len(sources),
        'checks': ['production-class identity', 'metadata and author', 'license files', 'exact motion profile/manifest',
                   'private FX and QA exclusion', 'mixin classes', 'language JSON', 'distribution/document identity', 'source snapshot isolation'],
        'notPerformed': ['automated gameplay tests', 'dedicated-server or multiplayer acceptance', 'public upload'],
        'files': {path.name: file_sha256(path) for path in (jar, dist, source)},
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
