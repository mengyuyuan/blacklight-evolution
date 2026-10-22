"""Verify and collect a local release candidate. Does not upload or approve releases."""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import tomllib
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


PROFILE_DATA_NAMES = {
    'combat-motion-tuning.data', 'consume-placement.data', 'consume-receiver-events.data',
    'receiver-poses.data', 'hunter-consume.data', 'hammer-scales.data', 'weapon-attack-scales.data', 'armor-growth.data',
    'camera-profile.data', 'camera-events.data', 'camera-film.data', 'health-hud.data',
}


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


def source_snapshot(repo: Path, output: Path | None, baseline: dict[str, str] | None = None) -> dict[str, str]:
    tracked = {p.decode('utf-8') for p in git(repo, 'ls-files', '-z').split(b'\0') if p}
    untracked = {p.decode('utf-8') for p in git(repo, 'ls-files', '--others', '--exclude-standard', '-z').split(b'\0') if p}
    root_files = {'README.md', 'LICENSE', 'THIRD_PARTY_ASSETS.md', 'THIRD_PARTY_NOTICES.md',
                  'CONTRIBUTING.md', 'TEMPLATE_LICENSE.txt', 'build.gradle', 'settings.gradle',
                  'gradle.properties', 'gradlew', 'gradlew.bat', '.gitignore', '.gitattributes'}

    def allowed(name: str) -> bool:
        return (name in root_files or name.startswith(('src/', 'gradle/', '.github/', 'docs/', 'config-presets/'))
                or name.startswith(('tools/', 'art/')) and name in tracked
                or name == 'tools/package_release.py')

    files = sorted(name for name in tracked | untracked if allowed(name) and (repo / name).is_file())
    baseline = repository_profile(repo) if baseline is None else baseline
    payloads = {}
    for name in files:
        require('bundled_motions/' not in name, 'Private motion data found in source tree')
        path = repo / name
        require(not path.is_symlink(), f'Source symlink needs explicit review: {name}')
        require(path.resolve().is_relative_to(repo.resolve()), f'Source path escapes repository: {name}')
        data = path.read_bytes()
        if name.startswith('art/motion-profile/'):
            motion_name = name[len('art/motion-profile/'):]
            require(motion_name == 'README.md' or motion_name in baseline and digest(data) == baseline[motion_name],
                    f'Unexpected/changed baseline motion in source snapshot: {name}')
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
    parser.add_argument('--output', type=Path, help='Defaults to release/local-<version>-<profile>')
    parser.add_argument('--profile', choices=('bundled', 'external-profile'), default='bundled')
    parser.add_argument('--private-motion-directory', type=Path, help='Exact external-profile build input directory')
    parser.add_argument('--verify-only', action='store_true', help='Verify inputs and artifacts without writing a release directory')
    args = parser.parse_args()
    repo = args.repo.resolve()
    build = (args.build_dir or repo / 'build').resolve()
    props = dict(line.split('=', 1) for line in (repo / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                 if '=' in line and not line.lstrip().startswith('#'))
    version = props['mod_version']
    stem = f"{props['mod_archive_name']}-MC{props['minecraft_version']}-Forge-{version}"
    jar = build / 'libs' / f'{stem}-{args.profile}.jar'
    dist = build / 'distributions' / f'{stem}-{args.profile}.zip'
    require(jar.is_file() and dist.is_file(), f'Build the requested {args.profile} artifacts first')
    notices = ('LICENSE', 'THIRD_PARTY_ASSETS.md', 'THIRD_PARTY_NOTICES.md')
    external = args.profile == 'external-profile'
    baseline = repository_profile(repo)
    if external:
        require(args.private_motion_directory is not None, 'Provide the exact --private-motion-directory used for this build')
        expected = profile_inventory(args.private_motion_directory.resolve())
        require(set(baseline) <= set(expected), 'External profile is missing baseline runtime motion files')
    else:
        require(args.private_motion_directory is None, '--private-motion-directory only applies to external-profile builds')
        expected = baseline
    with ZipFile(jar) as archive:
        names = set(archive.namelist())
        require(len(names) == len(archive.namelist()), 'Duplicate JAR entries')
        meta = tomllib.loads(archive.read('META-INF/mods.toml').decode('utf-8'))
        mod = next(m for m in meta['mods'] if m['modId'] == props['mod_id'])
        require(mod['version'] == version and '末者' in mod['authors'], 'Version or original author mismatch')
        manifest = manifest_values(archive.read('META-INF/MANIFEST.MF'))
        require(manifest.get('Blacklight-Build-Profile') == args.profile, 'Build profile mismatch')
        require(manifest.get('Blacklight-Motion-Source') == ('external' if external else 'repository'),
                'Motion source marker mismatch')
        require(manifest.get('Blacklight-Bundled-Motions') == 'true', 'Missing bundled profile marker')
        require(manifest.get('Blacklight-Public-Release-Ready') == 'false', 'This collector cannot approve public releases')
        for name in notices:
            require(archive.read(name) == (repo / name).read_bytes(), f'Stale or missing notice: {name}')
        motion_prefix = 'blacklight/bundled_motions/'
        motions = {name[len(motion_prefix):]: digest(archive.read(name)) for name in sorted(names)
                   if name.startswith(motion_prefix) and not name.endswith('/')}
        require(motions == expected, 'Bundled resources differ from the exact selected input profile/manifest')
        classes = list((build / 'classes/java/main').rglob('*.class'))
        require(bool(classes), 'No compiled classes to verify')
        for path in classes:
            name = path.relative_to(build / 'classes/java/main').as_posix()
            require(name in names, f'Missing class: {name}')
            require(int.from_bytes(archive.read(name)[6:8], 'big') <= 61, f'Class requires newer than Java 17: {name}')
        # Forge changes Minecraft references during reobfuscation; class bytes therefore
        # differ from classes/java/main. Verify the packaged JAR against the reobf output.
        reobfuscated = build / 'reobfJarJar/output.jar'
        require(reobfuscated.is_file() and reobfuscated.read_bytes() == jar.read_bytes(),
                'Build and reobfuscate the Forge JAR before collecting a candidate')
        require('blacklight.refmap.json' in names, 'Missing Forge Mixin refmap')
        require(not any('/preview/' in name or '/gametest/' in name or name.startswith('blacklight/test/')
                        for name in names), 'Development harness leaked into JAR')
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
        docs = sorted(path for path in (repo / 'docs').rglob('*') if path.is_file())
        for path in docs:
            name = 'docs/' + path.relative_to(repo / 'docs').as_posix()
            require(not path.is_symlink(), f'Distribution document symlink needs explicit review: {name}')
            require(archive.read(name) == path.read_bytes(), f'Distribution document mismatch: {name}')
        require({name for name in archive.namelist() if name.startswith('docs/') and not name.endswith('/')}
                == {'docs/' + path.relative_to(repo / 'docs').as_posix() for path in docs}, 'Distribution docs inventory mismatch')

    verified_sources = source_snapshot(repo, None, baseline)
    if args.verify_only:
        print(json.dumps({'profile': args.profile, 'version': version, 'motionFilesVerified': len(motions),
                          'repositoryMotionFilesVerified': len(baseline), 'productionClassesVerified': len(classes),
                          'productionResourcesVerified': len(resources), 'sourceFilesVerified': len(verified_sources),
                          'distributionDocumentsVerified': len(docs) + len(notices) + 1,
                          'outputWritten': False}, ensure_ascii=False, indent=2))
        return

    output = (args.output or repo / 'release' / f'local-{version}-{args.profile}').resolve()
    existing_manifest = output / 'RELEASE-MANIFEST.json'
    if existing_manifest.is_file():
        previous = json.loads(existing_manifest.read_text(encoding='utf-8'))
        require(previous.get('profile') == args.profile, 'Use a separate output directory for each build profile')
    other_profile = 'bundled' if external else 'external-profile'
    require(not any(output.glob(f'{stem}-{other_profile}.*')), 'Output contains the other build profile; choose a separate directory')
    output.mkdir(parents=True, exist_ok=True)
    for path in (jar, dist):
        if path.resolve() != (output / path.name).resolve():
            shutil.copy2(path, output / path.name)
    source = output / f'{stem}-sources.zip'
    sources = source_snapshot(repo, source, baseline)
    require(sources == verified_sources, 'Source inputs changed during collection; rerun with a stable working tree')
    summary = {
        'name': props['mod_name'], 'author': mod['authors'], 'minecraft': props['minecraft_version'],
        'version': version, 'profile': args.profile, 'publicReleaseReady': False,
        'compiledClassesVerified': len(classes), 'bundledMotionFiles': len(motions), 'externalProfile': external,
        'productionResourcesVerified': len(resources), 'repositoryMotionFiles': len(baseline),
        'baseCommit': git(repo, 'rev-parse', 'HEAD').decode().strip(),
        'workingTreeChanged': bool(git(repo, 'status', '--porcelain', '--untracked-files=no').strip()),
        'sourceFiles': len(sources),
        'checks': ['reobfuscated-class identity and Java 17', 'metadata and author', 'license files', 'bundle isolation',
                   'mixin classes', 'language JSON', 'distribution identity', 'source snapshot'],
        'notPerformed': ['automated gameplay tests', 'dedicated-server or multiplayer acceptance', 'public upload'],
        'files': {path.name: digest(path.read_bytes()) for path in (jar, dist, source)},
        'bundledResourceHashes': motions,
    }
    (output / 'RELEASE-MANIFEST.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (output / 'SHA256SUMS.txt').write_text(''.join(f'{sha}  {name}\n' for name, sha in summary['files'].items()), encoding='utf-8')
    print(json.dumps({key: value for key, value in summary.items() if key != 'bundledResourceHashes'}, ensure_ascii=False, indent=2))
    print(output)


if __name__ == '__main__':
    main()
