import importlib.util
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


if __name__ == '__main__':
    unittest.main()
