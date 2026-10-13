import importlib.util
import json
import subprocess
import tempfile
import unittest
import sys
import os
from pathlib import Path

SPEC = importlib.util.spec_from_file_location('version_workflow', Path(__file__).resolve().parents[1] / 'version_workflow.py')
policy = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(policy)


class VersionWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.data = {'schemaVersion': 1, 'branches': [
            {'branch': 'main', 'minecraft': '1.21.1', 'loader': 'neoforge', 'java': 21, 'status': 'active'},
            {'branch': 'forge-1.20.1', 'minecraft': '1.20.1', 'loader': 'forge', 'java': 17, 'status': 'active'}]}
        (self.repo / 'docs').mkdir()
        (self.repo / 'docs/versions.json').write_text(json.dumps(self.data), encoding='utf-8')
        self.write_platform()

    def write_platform(self, minecraft='1.21.1', loader='neo_version', java=21):
        (self.repo / 'gradle.properties').write_text(f'minecraft_version={minecraft}\n{loader}=1\n', encoding='utf-8')
        (self.repo / 'build.gradle').write_text(f'java.toolchain.languageVersion = JavaLanguageVersion.of({java})', encoding='utf-8')

    def test_both_native_platforms_pass(self):
        policy.check_platform(self.repo, self.data['branches'][0])
        self.write_platform('1.20.1', 'forge_version', 17)
        policy.check_platform(self.repo, self.data['branches'][1])

    def test_gradle_globs_and_urls_do_not_hide_toolchain(self):
        path = self.repo / 'build.gradle'
        path.write_text("exclude 'assets/private_fx/**'\nurl \"https://example.invalid/maven\"\n"
                        + path.read_text(encoding='utf-8') + "\nexclude '**/*.class'", encoding='utf-8')
        policy.check_platform(self.repo, self.data['branches'][0])

    def test_comments_and_strings_cannot_claim_a_toolchain(self):
        (self.repo / 'build.gradle').write_text(
            "// languageVersion = JavaLanguageVersion.of(21)\n"
            "def note = 'languageVersion = JavaLanguageVersion.of(21)'\n"
            "/* languageVersion = JavaLanguageVersion.of(21) */", encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Java toolchain'):
            policy.check_platform(self.repo, self.data['branches'][0])

    def test_whole_forge_port_cannot_target_main(self):
        self.write_platform('1.20.1', 'forge_version', 17)
        with self.assertRaisesRegex(ValueError, 'Minecraft.*1.21.1'):
            policy.check_platform(self.repo, self.data['branches'][0])

    def test_loader_and_java_are_checked_even_when_minecraft_matches(self):
        self.write_platform('1.21.1', 'forge_version', 17)
        with self.assertRaisesRegex(ValueError, 'Java toolchain'):
            policy.check_platform(self.repo, self.data['branches'][0])

    def test_pr_registry_edit_cannot_redefine_base_platform(self):
        for args in [('init', '-q'), ('add', '.'), ('-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'base')]:
            subprocess.run(['git', '-C', str(self.repo), *args], check=True, capture_output=True)
        base = policy.git(self.repo, 'rev-parse', 'HEAD')
        self.data['branches'][0]['minecraft'] = '1.20.1'
        (self.repo / 'docs/versions.json').write_text(json.dumps(self.data), encoding='utf-8')
        trusted = policy.registry(self.repo, base)
        self.assertEqual('1.21.1', policy.platform(trusted, 'main')['minecraft'])

    def test_missing_metadata_produces_candidates_not_success_claims(self):
        rows = policy.sync_plan(self.data, 'main', policy.metadata('普通 PR'))
        self.assertEqual('待评估', rows[0]['status'])
        self.assertNotIn('已同步', str(rows))

    def test_platform_only_exclusion_requires_reason(self):
        with self.assertRaisesRegex(ValueError, 'reason'):
            policy.sync_plan(self.data, 'main', {'scope': 'platform'})

    def test_backport_records_source_without_creating_ping_pong(self):
        info = {'scope': 'backport', 'source_pr': 5, 'source_commit': 'a' * 40}
        rows = policy.sync_plan(self.data, 'forge-1.20.1', info)
        self.assertEqual('跟随来源跟踪记录', rows[0]['status'])
        with self.assertRaises(ValueError):
            policy.sync_plan(self.data, 'forge-1.20.1', {**info, 'source_commit': 'abc'})
        with self.assertRaises(ValueError):
            policy.sync_plan(self.data, 'forge-1.20.1', {**info, 'source_branch': 'forge-1.20.1'})

    def test_unknown_target_and_duplicate_metadata_fail(self):
        with self.assertRaises(ValueError):
            policy.sync_plan(self.data, 'main', {'scope': 'shared', 'targets': ['fake']})
        with self.assertRaises(ValueError):
            policy.metadata('<!-- blacklight-sync {"scope":"review"} -->' * 2)

    def test_malformed_metadata_is_rejected(self):
        with self.assertRaises(ValueError):
            policy.sync_plan(self.data, 'main', {'scope': []})
        with self.assertRaises(ValueError):
            policy.sync_plan(self.data, 'main', {'scope': 'backport', 'source_pr': 5, 'source_commit': 123})

    def test_ci_uses_base_and_emits_merged_record(self):
        for args in [('init', '-q'), ('add', '.'), ('-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'base')]:
            subprocess.run(['git', '-C', str(self.repo), *args], check=True, capture_output=True)
        sha = policy.git(self.repo, 'rev-parse', 'HEAD')
        event = {'pull_request': {'number': 1, 'base': {'sha': sha, 'ref': 'main'},
                 'body': '', 'merged': True, 'merge_commit_sha': sha}}
        event_path = self.repo / 'event.json'
        event_path.write_text(json.dumps(event), encoding='utf-8')
        output = self.repo / 'report'
        command = [sys.executable, str(Path(policy.__file__)), '--repo', str(self.repo), 'ci',
                   '--event', str(event_path), '--output', str(output)]
        child_env = {key: value for key, value in os.environ.items() if key != 'GITHUB_STEP_SUMMARY'}
        result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', env=child_env)
        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads((output / 'sync-plan.json').read_text(encoding='utf-8'))
        self.assertEqual(sha, report['merge_commit'])
        self.assertEqual('待评估', report['targets'][0]['status'])
        self.write_platform('1.20.1', 'forge_version', 17)
        result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', env=child_env)
        self.assertEqual(1, result.returncode)
        self.assertIn('Platform does not match', (output / 'error.json').read_text(encoding='utf-8'))
        self.assertFalse((output / 'sync-plan.json').exists(), 'A failed rerun must not retain a stale success record')

    def test_prepare_creates_requested_target_branch_only_when_clean(self):
        for args in [('init', '-q'), ('add', '.'), ('-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'base'),
                     ('branch', 'forge-1.20.1'), ('remote', 'add', 'origin', str(self.repo))]:
            subprocess.run(['git', '-C', str(self.repo), *args], check=True, capture_output=True)
        command = [sys.executable, str(Path(policy.__file__)), '--repo', str(self.repo), 'prepare',
                   '--target', 'forge-1.20.1', '--topic', 'example', '--create-branch',
                   '--output', str(self.repo / 'draft')]
        dirty = self.repo / 'unsaved.txt'
        dirty.write_text('unsaved work', encoding='utf-8')
        result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8')
        self.assertEqual(1, result.returncode)
        self.assertEqual('', policy.git(self.repo, 'branch', '--list', 'fix/forge-1.20.1-example'))
        dirty.unlink()
        result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('fix/forge-1.20.1-example', policy.git(self.repo, 'branch', '--show-current'))
        self.assertIn('forge-1.20.1', (self.repo / 'draft/pr-body.md').read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
