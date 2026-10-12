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


def source_snapshot(repo: Path, output: Path) -> dict[str, str]:
    tracked = {p.decode('utf-8') for p in git(repo, 'ls-files', '-z').split(b'\0') if p}
    untracked = {p.decode('utf-8') for p in git(repo, 'ls-files', '--others', '--exclude-standard', '-z').split(b'\0') if p}
    root_files = {'README.md', 'LICENSE', 'THIRD_PARTY_ASSETS.md', 'THIRD_PARTY_NOTICES.md',
                  'CONTRIBUTING.md', 'TEMPLATE_LICENSE.txt', 'build.gradle', 'settings.gradle',
                  'gradle.properties', 'gradlew', 'gradlew.bat', '.gitignore'}

    def allowed(name: str) -> bool:
        return (name in root_files or name.startswith(('src/', 'gradle/', '.github/', 'docs/'))
                or name.startswith(('tools/', 'art/')) and name in tracked
                or name == 'tools/package_release.py')

    files = sorted(name for name in tracked | untracked if allowed(name) and (repo / name).is_file())
    hashes = {}
    with ZipFile(output, 'w', compression=ZIP_DEFLATED, compresslevel=9) as archive:
        for name in files:
            require('bundled_motions/' not in name, 'Private motion data found in source tree')
            path = repo / name
            require(not path.is_symlink(), f'Source symlink needs explicit review: {name}')
            data = path.read_bytes()
            info = ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            info.external_attr = (0o100755 if name == 'gradlew' else 0o100644) << 16
            archive.writestr(info, data)
            hashes[name] = digest(data)
        info = ZipInfo('SOURCE_MANIFEST.json', date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        archive.writestr(info, json.dumps(hashes, ensure_ascii=False, indent=2))
    return hashes


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--build-dir', type=Path, help='Defaults to <repo>/build')
    parser.add_argument('--output', type=Path, help='Defaults to release/local-<version>, with -source-only for that profile')
    parser.add_argument('--profile', choices=('local-preview', 'source-only'), default='local-preview')
    parser.add_argument('--private-motion-directory', type=Path, help='Exact private inputs used by this local-preview build; verify every bundled SHA-256')
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
    private = args.profile == 'local-preview'
    with ZipFile(jar) as archive:
        names = set(archive.namelist())
        require(len(names) == len(archive.namelist()), 'Duplicate JAR entries')
        meta = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode('utf-8'))
        mod = next(m for m in meta['mods'] if m['modId'] == props['mod_id'])
        require(mod['version'] == version and mod['authors'] == '末者', 'Version or author mismatch')
        manifest = manifest_values(archive.read('META-INF/MANIFEST.MF'))
        require(manifest.get('Blacklight-Build-Profile') == args.profile, 'Build profile mismatch')
        require(manifest.get('Blacklight-Private-Assets') == str(private).lower(), 'Private data marker mismatch')
        require(manifest.get('Blacklight-Public-Release-Ready') == 'false', 'This collector cannot approve public releases')
        for name in notices:
            require(archive.read(name) == (repo / name).read_bytes(), f'Stale or missing notice: {name}')
        motion_prefix = 'blacklight/bundled_motions/'
        motions = {name[len(motion_prefix):]: digest(archive.read(name)) for name in sorted(names)
                   if name.startswith(motion_prefix) and not name.endswith('/')}
        require(bool(motions) == private, 'Private resources leaked or local preview has none')
        if private:
            require(args.private_motion_directory is not None, 'Provide the exact --private-motion-directory used for this build')
            profile=args.private_motion_directory.resolve()
            data_names={'combat-motion-tuning.data','consume-placement.data','consume-receiver-events.data',
                        'receiver-poses.data','hunter-consume.data','hammer-scales.data','armor-growth.data',
                        'camera-profile.data','camera-events.data','camera-film.data','health-hud.data'}
            expected={p.name: digest(p.read_bytes()) for p in profile.iterdir()
                      if p.is_file() and (p.suffix == '.json' or p.name in data_names)}
            require(motions == expected, 'Bundled resources differ from the exact private input profile')
            require(not any(name.startswith('assets/blacklight/textures/private_devfx/') for name in names), 'Reference textures must not be bundled')
            for name in ('armor-growth.data', 'health-hud.data', 'camera-film.data', 'basic_idle.json',
                         'basic_run.json', 'basic_wall_top.json', 'utility_armor_transform.json',
                         'dev_groundspike.json','dev_air_groundspike.json','dev_tendril.json','dev_air_tendril.json',
                         'dev_critical_pain.json','dev_air_critical_pain.json','bulletdive_drop.json'):
                require(name in motions, f'Missing motion resource: {name}')
        classes = list((build / 'classes/java/main').rglob('*.class'))
        if not private:require(not any(name.startswith('assets/blacklight/textures/private_devfx/') for name in names),'Private effect textures leaked into source-only build')
        require(bool(classes), 'No compiled classes to verify')
        for path in classes:
            name = path.relative_to(build / 'classes/java/main').as_posix()
            require(archive.read(name) == path.read_bytes(), f'Stale class: {name}')
        require(not any('/preview/' in name or '/gametest/' in name or name.startswith('blacklight/test/')
                        for name in names), 'Development harness leaked into JAR')
        mixins = json.loads(archive.read('blacklight.mixins.json'))
        for name in mixins['mixins'] + mixins['client']:
            require((mixins['package'] + '.' + name).replace('.', '/') + '.class' in names, f'Missing mixin: {name}')
        for language in ('en_us', 'zh_cn'):
            json.loads(archive.read(f'assets/blacklight/lang/{language}.json'))
    with ZipFile(dist) as archive:
        require([name for name in archive.namelist() if name.endswith('.jar')] == ['mods/' + jar.name],
                'Distribution must contain exactly this build, without dependencies or old JARs')
        require(archive.read('mods/' + jar.name) == jar.read_bytes(), 'Distribution JAR mismatch')
        for name in (*notices, 'README.md'):
            require(archive.read(name) == (repo / name).read_bytes(), f'Distribution document mismatch: {name}')
        for name in ('INSTALL_ZH.md', 'ACCEPTANCE_0.2.0.md', 'RELEASE_PREPARATION_ZH.md', 'PLATFORM_PLAN_ZH.md'):
            require(archive.read('docs/' + name) == (repo / 'docs' / name).read_bytes(), f'Missing release document: {name}')

    output_suffix = '' if private else '-source-only'
    output = (args.output or repo / 'release' / f'local-{version}{output_suffix}').resolve()
    existing_manifest = output / 'RELEASE-MANIFEST.json'
    if existing_manifest.is_file():
        previous = json.loads(existing_manifest.read_text(encoding='utf-8'))
        require(previous.get('profile') == args.profile, 'Use a separate output directory for each build profile')
    other_profile = 'source-only' if private else 'local-preview'
    require(not any(output.glob(f'{stem}-{other_profile}.*')), 'Output contains the other build profile; choose a separate directory')
    output.mkdir(parents=True, exist_ok=True)
    for path in (jar, dist):
        if path.resolve() != (output / path.name).resolve():
            shutil.copy2(path, output / path.name)
    source = output / f'{stem}-sources.zip'
    sources = source_snapshot(repo, source)
    summary = {
        'name': props['mod_name'], 'author': mod['authors'], 'minecraft': props['minecraft_version'],
        'version': version, 'profile': args.profile, 'publicReleaseReady': False,
        'compiledClassesVerified': len(classes), 'privateFiles': len(motions),
        'baseCommit': git(repo, 'rev-parse', 'HEAD').decode().strip(),
        'workingTreeChanged': bool(git(repo, 'status', '--porcelain', '--untracked-files=normal').strip()),
        'sourceFiles': len(sources),
        'checks': ['compiled-class identity', 'metadata and author', 'license files', 'bundle isolation',
                   'mixin classes', 'language JSON', 'distribution identity', 'source snapshot'],
        'notPerformed': ['automated gameplay tests', 'dedicated-server or multiplayer acceptance', 'public upload'],
        'files': {path.name: digest(path.read_bytes()) for path in (jar, dist, source)},
        'privateResourceHashes': motions,
    }
    (output / 'RELEASE-MANIFEST.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (output / 'SHA256SUMS.txt').write_text(''.join(f'{sha}  {name}\n' for name, sha in summary['files'].items()), encoding='utf-8')
    print(json.dumps({key: value for key, value in summary.items() if key != 'privateResourceHashes'}, ensure_ascii=False, indent=2))
    print(output)


if __name__ == '__main__':
    main()
