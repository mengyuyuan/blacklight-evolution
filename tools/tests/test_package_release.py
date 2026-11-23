from zipfile import ZipFile
import struct
import gzip
import shutil
import os
import importlib.util
import io
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


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
        names = ['weapon-attack-scales.data', 'evolved-poses.data']
        for name in names:
            (self.profile / name).write_bytes(self.data)
            self.files[name] = {'bytes': len(self.data)}
        expected = {name: release.digest(self.data) for name in ['basic_idle.json', *names]}
        self.assertEqual(expected, self.load())

    def test_repository_manifest_matches_all_current_runtime_inputs(self):
        repo = Path(__file__).resolve().parents[2]
        expected = release.repository_profile(repo)
        self.assertEqual(release.profile_inventory(repo / 'art/motion-profile'), expected)
        self.assertIn('weapon-attack-scales.data', expected)


    def track_fixture(self):
        self.load()
        for args in [('init', '-q'), ('add', '.')]:
            subprocess.run(['git', '-C', str(self.repo), *args], check=True, capture_output=True)

    def test_dry_source_snapshot_checks_identity_without_writing_archive(self):
        self.track_fixture()
        hashes = release.source_snapshot(self.repo, None)
        self.assertEqual(release.digest(self.data), hashes['art/motion-profile/basic_idle.json'])
        self.assertEqual([], list(self.repo.rglob('*.zip')))

    def test_source_snapshot_rejects_motion_changed_after_input_verification(self):
        self.track_fixture()
        baseline = release.repository_profile(self.repo)
        (self.profile / 'basic_idle.json').write_bytes(self.data.replace(b'0,1', b'1,0'))
        with self.assertRaisesRegex(RuntimeError, 'changed baseline motion in source snapshot'):
            release.source_snapshot(self.repo, None, baseline)


class ProductionClassBoundaryTest(unittest.TestCase):
    """Selection reads real class metadata, so fixtures are compiled with javac."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.root = self.repo / 'build/classes/java/main'
        self.source = self.repo / 'src/main/java/com/codex/blacklight/Live.java'
        self.source.parent.mkdir(parents=True)
        self.source.write_text('package com.codex.blacklight;\npublic class Live { static class Nested {} }', encoding='utf-8')
        javac = Path(os.environ['JAVA_HOME']) / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac or not Path(javac).is_file():
            self.skipTest('A JDK is required for compiled class fixtures')
        self.javac = str(javac)

    def compile(self, *sources):
        subprocess.run([self.javac, '-encoding', 'UTF-8', '--release', '17', '-d', str(self.root), *map(str, sources)],
                       check=True, capture_output=True)

    def test_private_fixture_is_not_required_in_production_archive(self):
        qa = self.repo / 'src/qa/java/com/codex/blacklight/PrivateQaFixture.java'
        qa.parent.mkdir(parents=True)
        qa.write_text('package com.codex.blacklight;\npublic class PrivateQaFixture {}', encoding='utf-8')
        self.compile(self.source, qa)
        selected = release.production_classes(self.repo, self.root)
        self.assertEqual({'Live.class', 'Live$Nested.class'}, {p.name for p in selected})

    def test_missing_production_source_cannot_be_hidden_by_other_class_files(self):
        self.compile(self.source)
        (self.source.parent / 'NewFeature.java').write_text('package com.codex.blacklight;\npublic class NewFeature {}', encoding='utf-8')
        with self.assertRaisesRegex(RuntimeError, 'Production sources were not compiled'):
            release.production_classes(self.repo, self.root)

    def test_stream_comparison_checks_tail_content_and_length(self):
        prefix = b'x' * (1024 * 1024)
        self.assertTrue(release.same_stream_content(io.BytesIO(prefix + b'a'), io.BytesIO(prefix + b'a')))
        self.assertFalse(release.same_stream_content(io.BytesIO(prefix + b'a'), io.BytesIO(prefix + b'b')))
        self.assertFalse(release.same_stream_content(io.BytesIO(prefix), io.BytesIO(prefix + b'a')))


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
