import subprocess
import struct
import shutil
import os
import gzip
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile


SPEC = importlib.util.spec_from_file_location(
    'package_release', Path(__file__).resolve().parents[1] / 'package_release.py')
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class RepositoryProfileTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.profile = self.repo / 'art/motion-profile'
        self.profile.mkdir(parents=True)
        self.data = b'{"frames":[0,1]}'
        (self.profile / 'basic_idle.json').write_bytes(self.data)
        self.files = {'basic_idle.json': {'bytes': len(self.data)}}
        self.manifest = self.repo / 'art/motion-profile-manifest.json'

    def load(self):
        self.manifest.write_text(json.dumps({'version': 1, 'files': self.files}), encoding='utf-8')
        return release.repository_profile(self.repo)

    def test_size_only_manifest_computes_identity_from_actual_inputs(self):
        self.assertEqual({'basic_idle.json': release.digest(self.data)}, self.load())

    def test_legacy_manifest_still_accepts_matching_hash(self):
        self.files['basic_idle.json']['sha256'] = release.digest(self.data)
        self.assertEqual({'basic_idle.json': release.digest(self.data)}, self.load())

    def test_legacy_manifest_rejects_same_size_content_with_wrong_hash(self):
        self.files['basic_idle.json']['sha256'] = release.digest(self.data)
        (self.profile / 'basic_idle.json').write_bytes(self.data.replace(b'0,1', b'1,0'))
        with self.assertRaisesRegex(RuntimeError, 'manifest hash mismatch: basic_idle.json'):
            self.load()

    def test_size_only_manifest_rejects_stale_byte_count(self):
        self.files['basic_idle.json']['bytes'] += 1
        with self.assertRaisesRegex(RuntimeError, 'manifest size mismatch: basic_idle.json'):
            self.load()

    def test_missing_motion_is_rejected(self):
        (self.profile / 'basic_idle.json').unlink()
        with self.assertRaisesRegex(RuntimeError, 'Missing repository motion: basic_idle.json'):
            self.load()

    def test_unlisted_motion_is_rejected(self):
        (self.profile / 'unlisted.json').write_bytes(self.data)
        with self.assertRaisesRegex(RuntimeError, 'inventory differs from its manifest'):
            self.load()

    def test_weapon_attack_configuration_is_part_of_profile_inventory(self):
        name = 'weapon-attack-scales.data'
        (self.profile / name).write_bytes(self.data)
        self.files[name] = {'bytes': len(self.data)}
        self.assertEqual({'basic_idle.json': release.digest(self.data), name: release.digest(self.data)}, self.load())

    def test_repository_manifest_matches_all_current_runtime_inputs(self):
        repo = Path(__file__).resolve().parents[2]
        expected = release.repository_profile(repo)
        self.assertEqual(release.profile_inventory(repo / 'art/motion-profile'), expected)
        self.assertIn('weapon-attack-scales.data', expected)


class PrivateCurveTest(unittest.TestCase):
    def test_explicit_private_curve_remains_blocked(self):
        data = {'format': 'MATRIX', 'privateNotice': 'Private local use only.'}
        self.assertTrue(release.private_curve(json.dumps(data).encode()))

    def test_current_descriptive_metadata_is_not_a_private_input_flag(self):
        repo = Path(__file__).resolve().parents[2]
        data = (repo / 'src/main/resources/assets/blacklight/authored_motions/block_throw_two_pickup.json').read_bytes()
        self.assertFalse(release.private_curve(data))

    def test_descriptive_text_without_project_metadata_stays_blocked(self):
        data = {'format': 'MATRIX', 'privateNotice':
                'Project-modified and adapted Blacklight motion data. Initial lineage / technical context: test.'}
        self.assertTrue(release.private_curve(json.dumps(data).encode()))

    def test_project_metadata_does_not_override_explicit_private_notice(self):
        data = {'format': 'MATRIX', 'privateNotice': 'Private local use only.', 'provenance': {
            'schema': 1, 'project': 'Blacklight: Ascension', 'currentStatus': 'project_modified_and_adapted'}}
        self.assertTrue(release.private_curve(json.dumps(data).encode()))


class GeographicPackageTest(unittest.TestCase):
    def fixture(self, root, extra=None, missing_tile=False, compact=False):
        repo = Path(root)
        descriptor = b'{"schema":1,"mode":"geographic"}'
        path = repo / 'src/main/resources/data/blacklight/city/generation.json'
        path.parent.mkdir(parents=True)
        path.write_bytes(descriptor)
        jar = repo / 'candidate.jar'
        with ZipFile(jar, 'w') as archive:
            archive.writestr('data/blacklight/city/generation.json', descriptor)
            for city in ('shenzhen', 'los_angeles'):
                archive.writestr(f'data/blacklight/worldmap/{city}/index.json', json.dumps(
                    {'schema': 1, 'region': city, 'tiles': [{'x': 0, 'z': 0}]}))
                if not missing_tile:
                    archive.writestr(f'data/blacklight/worldmap/{city}/0_0.json.'+('xz' if compact else 'gz'), b'fixture')
            for city in ('futian', 'los_angeles'):
                archive.writestr(f'data/blacklight/city/{city}/map.json', b'{}')
            if extra:
                archive.writestr(extra, b'old archive')
        return repo, jar

    def test_geographic_package_keeps_both_atlases_and_cores(self):
        with tempfile.TemporaryDirectory() as root:
            repo, jar = self.fixture(root)
            with ZipFile(jar) as archive:
                self.assertTrue(release.geographic_city_selected(repo, archive))

    def test_stale_archive_cannot_silently_inflate_geographic_package(self):
        for prefix in ('los_angeles_full', 'los_angeles_compact'):
            with self.subTest(prefix=prefix), tempfile.TemporaryDirectory() as root:
                repo, jar = self.fixture(root, f'data/blacklight/city/{prefix}/0_0.lar')
                with ZipFile(jar) as archive, self.assertRaisesRegex(RuntimeError, 'Baked municipal archives leaked'):
                    release.geographic_city_selected(repo, archive)

    def test_missing_geographic_tile_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            repo, jar = self.fixture(root, missing_tile=True)
            with ZipFile(jar) as archive, self.assertRaisesRegex(RuntimeError, 'Missing geographic tile'):
                release.geographic_city_selected(repo, archive)

    def test_logical_tile_inventory_accepts_compact_encoding(self):
        with tempfile.TemporaryDirectory() as root:
            repo, jar = self.fixture(root, compact=True)
            with ZipFile(jar) as archive:
                self.assertTrue(release.geographic_city_selected(repo, archive))


class ContributorAttributionTest(unittest.TestCase):
    repo = Path(__file__).resolve().parents[2]

    def test_real_gradle_template_with_dependency_placeholders_can_be_read(self):
        declared = release.declared_mod_attribution(self.repo, 'blacklight')
        self.assertIn('末者', declared['authors'])
        self.assertIn('WWYFC', declared['authors'])
        self.assertIn('@yfcyy889-ops', declared['credits'])
        release.verify_mod_attribution(self.repo, declared, 'blacklight')

    def test_old_single_author_metadata_cannot_drop_a_new_contributor(self):
        mod = dict(release.declared_mod_attribution(self.repo, 'blacklight'), authors='末者')
        with self.assertRaisesRegex(RuntimeError, 'Author attribution'):
            release.verify_mod_attribution(self.repo, mod, 'blacklight')

    def test_original_author_cannot_be_removed(self):
        mod = dict(release.declared_mod_attribution(self.repo, 'blacklight'), authors='WWYFC')
        with self.assertRaisesRegex(RuntimeError, 'Author attribution'):
            release.verify_mod_attribution(self.repo, mod, 'blacklight')

    def test_missing_contributor_credits_are_rejected(self):
        mod = dict(release.declared_mod_attribution(self.repo, 'blacklight'))
        mod.pop('credits')
        with self.assertRaisesRegex(RuntimeError, 'Contributor credits'):
            release.verify_mod_attribution(self.repo, mod, 'blacklight')


class GreeneClipVerificationTest(unittest.TestCase):
    def test_restores_both_jar_clips_and_rejects_a_stale_frame_or_missing_entry(self):
        repo = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            classes = root / 'build/classes/java/resourceTools'
            classes.mkdir(parents=True)
            jdk = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else None
            javac = str(jdk / 'javac') if jdk else shutil.which('javac')
            java = str(jdk / 'java') if jdk else shutil.which('java')
            if not javac or not java:
                self.skipTest('A JDK is required for compressed animation fixtures')
            subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(classes), str(repo / 'tools/CompactMoltPacker.java'),
                            str(repo / 'src/main/java/com/codex/blacklight/util/MoltAnimationData.java')], check=True)
            prefix = Path('assets/blacklight/greene/molt')
            source = root / 'src/main/resources'
            (source / prefix).mkdir(parents=True)
            # Two empty scene meshes, followed by two one-channel keyframes in both source clips.
            scene = struct.pack('<4I', 0x474d4c32, 0, 0, 2) + bytes(224) + struct.pack('<2I', 11, 17)
            climb = struct.pack('<6I', 0x434c4d31, 2, 0, 0, 21, 43)
            names = [(prefix / name).as_posix() for name in ('scene.bin.gz', 'clamber.bin.gz')]
            for name, raw in zip(names, (scene, climb)):
                (source / name).write_bytes(gzip.compress(raw))
            packed = root / 'packed'
            subprocess.run([java, '-cp', str(classes), 'CompactMoltPacker', str(source), str(packed)], check=True)
            jar = root / 'fixture.jar'

            def write_jar(entries, include_decoder=True):
                with ZipFile(jar, 'w') as archive:
                    if include_decoder:
                        decoder = 'com/codex/blacklight/util/MoltAnimationData.class'
                        archive.write(classes / decoder, decoder)
                    for name in entries:
                        archive.write(packed / name, name)

            write_jar(names)
            self.assertEqual(set(names), release.verify_compact_molt(root, root / 'build', jar))
            for name, raw in zip(names, (scene, climb)):
                with self.subTest(name=name):
                    (source / name).write_bytes(gzip.compress(raw[:-1] + bytes([raw[-1] ^ 1])))
                    with self.assertRaisesRegex(RuntimeError, 'Packaged Greene animation differs'):
                        release.verify_compact_molt(root, root / 'build', jar)
                    (source / name).write_bytes(gzip.compress(raw))
            write_jar(names, include_decoder=False)
            with self.assertRaisesRegex(RuntimeError, 'Missing shipped Greene animation decoder'):
                release.verify_compact_molt(root, root / 'build', jar)
            write_jar(names[:1])
            with self.assertRaisesRegex(RuntimeError, 'Missing packaged clamber'):
                release.verify_compact_molt(root, root / 'build', jar)


if __name__ == '__main__':
    unittest.main()
