"""Disposable evidence, archive and Git fixtures; no Gradle or Minecraft processes."""
from contextlib import redirect_stdout
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import unittest
import uuid
from zipfile import ZipFile

SCRIPT = Path(__file__).with_name('collect_military6_delivery.py')


def collector():
    if not SCRIPT.is_file():
        raise AssertionError('M6 collector with fail-closed evidence gates is not implemented')
    spec = importlib.util.spec_from_file_location('military6_collector', SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def git(repo, *args):
    return subprocess.check_output(['git', '-c', 'core.autocrlf=true', '-C', str(repo), *args], stderr=subprocess.STDOUT)


def checkpoint(repo):
    tree = git(repo, 'write-tree').decode().strip()
    head = git(repo, '-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid',
               'commit-tree', tree, '-m', 'fixture').decode().strip()
    git(repo, 'update-ref', 'HEAD', head)
    return head


M5_BATCHES = {'homeTransfer5': 2, 'cores5': 3, 'militarySlope': 2, 'homeDimension5': 2,
              'defaultBatch': 60, 'terrain4': 10, 'homePersistence5': 2, 'homes5': 5,
              'isolatedPatrol': 1, 'homes5Orders': 1, 'homeFair5': 1, 'cityMilitary': 8,
              'homeUnload5': 1, 'garrison4Contracts': 1, 'garrison4': 2, 'garrison4Budget': 1,
              'militaryFormation': 12, 'militaryOrganization': 8, 'officers': 10}
M6_BATCHES = {'vehicle6garage': 6, 'vehicle6garage_dispatch_blackwatch': 1, 'vehicle6hullai': 7,
              'vehicle6_soldier_usability': 2, 'vehicle6': 43, 'vehicle6_projectile_hull': 5,
              'vehicle6_physical': 12, 'vehicle6garage_dispatch_military': 1, 'vehicle6boarding': 3,
              'vehicle6usability': 3, 'vehicle6permissions': 3, 'vehicle6_loadout': 16}


class CollectorEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.root = SCRIPT.resolve().parents[1]/'.superpowers/military6-qa/collector-fixtures'/str(uuid.uuid4())
        self.root.mkdir(parents=True)
        self.c = collector()

    def file(self, name, content, root=None):
        path = (root or self.root)/name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content.encode() if isinstance(content, str) else content)
        return {'path': name, 'sha256': self.c.sha(path)}

    def game(self, count=151, root=None, **changes):
        batches = changes.pop('batches', {'vehicle6': count});index = changes.pop('batchIndex', 0)
        run = changes.get('runDirectory', '.superpowers/run');log = changes.pop('logPath', '.superpowers/qa/game.log')
        text = ''.join(f"[Server thread/INFO] [minecraft/{'GameTestRunner' if index == 0 else 'GameTestBatchRunner'}]: Running test batch '{batch}:{index}' ({number} tests)...\n" for batch, number in batches.items())
        text += f'Fixture runtime: {(root or self.root)/run}\n'
        value = {'label': 'complete', 'expectedTests': count, 'runDirectory': run,
                 'log': self.file(log, text+f'{count} GAME TESTS COMPLETE\nAll {count} required tests passed\nStopping server\nAll dimensions are saved\nGame test server shutting down\nBUILD SUCCESSFUL\n', root),
                 'versionLogs': [self.file(run+'/logs/debug.log', self.c.VERSION, root)]}
        value.update(changes)
        return value

    def audit(self):
        report = {'schemaVersion': 1, 'loader': 'neoforge-1.21.1', 'status': 'PASS', 'preparePid': 101,
                  'verifyPid': 102, 'apcActuallyUnloaded': True, 'infantryFiniteReloads': 1,
                  'baseId': str(uuid.uuid4()), 'garageId': str(uuid.uuid4()), 'seed': 123,
                  'dimension': 'minecraft:overworld', 'nativePersonnelSlots': [str(uuid.uuid4())],
                  'slots': {'truck': {'state': 'ISSUED'}, 'apc': {'state': 'UNKNOWN'}, 'tank': {'state': 'LOST'}},
                  'vehicles': [{'uuid': str(uuid.uuid4())}, {'uuid': str(uuid.uuid4())}],
                  'people': [{'uuid': str(uuid.uuid4())}], 'containers': [{'items': []}],
                  'marker': [1, 2, 3], 'lostTankUuid': str(uuid.uuid4()), 'unknownApcUuid': str(uuid.uuid4()),
                  'prepareCheckpointUtc': '2026-10-27T01:00:00Z', 'verifiedUtc': '2026-10-27T01:01:00Z'}
        value = {'runDirectory': '.superpowers/run', 'json': self.file('.superpowers/run/vehicle-garage-audit.json', json.dumps(report)),
                 'prepareLog': self.file('.superpowers/qa/prepare.log', self.c.VERSION+'\nVEHICLE_GARAGE_AUDIT PHASE1_SAVED\nStopping server\nAll dimensions are saved\nBUILD SUCCESSFUL\n'),
                 'verifyLog': self.file('.superpowers/qa/verify.log', self.c.VERSION+'\nVEHICLE_GARAGE_AUDIT PASS\nStopping server\nAll dimensions are saved\nBUILD SUCCESSFUL\n'),
                 'versionLogs': []}
        return value, report

    def repo(self, directory='repo'):
        repo = self.root/directory;repo.mkdir(parents=True)
        git(repo, 'init', '-q')
        (repo/'README.md').write_bytes(b'baseline\r\n')
        (repo/'docs').mkdir();(repo/'docs/deleted.md').write_text('old\n')
        git(repo, 'add', '.')
        checkpoint(repo)
        return repo

    def runtime_changes(self, repo):
        names = ('logs/debug-1.log.gz', 'logs/debug-2.log.gz')
        for name in names:self.file(name, b'\0original tracked runtime\n', repo)
        git(repo, 'add', '--', *names)
        head = checkpoint(repo)
        payloads = {name: b'\0M6_REVIEWED_RUNTIME_PAYLOAD\n'+name.encode() for name in names}
        for name, data in payloads.items():self.file(name, data, repo)
        self.file('README.md', 'M6_ALLOWED_SOURCE_PAYLOAD\n', repo)
        return head, payloads

    def test_runtime_reviewed_logs_are_excluded_but_other_unexpected_changes_are_rejected(self):
        repo = self.repo();_, payloads = self.runtime_changes(repo)
        self.assertEqual(['README.md'], self.c.changed_paths(repo))
        inventory = self.c.source_inventory(repo)
        for name in payloads:
            self.assertFalse(self.c.allowed_source(name))
            self.assertNotIn(name, inventory)
        self.c.source_archive(repo, self.root/'source.zip', inventory)
        self.c.source_overlay(repo, self.root/'changes.zip')
        for name in ('source.zip', 'changes.zip'):
            with ZipFile(self.root/name) as archive:
                for path in archive.namelist():
                    self.assertNotIn(path, payloads)
                    self.assertNotIn(b'M6_REVIEWED_RUNTIME_PAYLOAD', archive.read(path))
        for index, name in enumerate(('logs/debug-3.log.gz', 'logs/debug-1.log.gz.bak', 'other/logs/debug-1.log.gz',
                                      'secrets/token.json', 'src/secrets/token.json', 'unexpected.txt')):
            with self.subTest(path=name):
                unsafe = self.repo('unsafe-'+str(index));self.runtime_changes(unsafe)
                self.file(name, 'private or unreviewed payload', unsafe)
                with self.assertRaisesRegex(RuntimeError, 'Unexpected non-source/private'):
                    self.c.changed_paths(unsafe)
        for name, data in payloads.items():self.assertEqual(data, (repo/name).read_bytes())

    def test_runtime_collect_excludes_logs_from_full_changes_diff_and_delivery(self):
        manifest = self.delivery();payloads = {}
        paths = ('settings.gradle', 'src/packaging-marker.txt', 'tools/packaging-marker.txt', 'docs/packaging-marker.txt',
                 'art/packaging-marker.txt', 'gradle/packaging-marker.txt', '.github/packaging-marker.txt')
        for platform, entry in manifest['platforms'].items():
            repo = Path(entry['repo']);head, payloads[platform] = self.runtime_changes(repo)
            self.c.AUTHOR_HEADS[platform] = entry['head'] = head
            for name in paths:self.file(name, 'M6_ALLOWED_SOURCE_PAYLOAD\n', repo)
            git(repo, 'add', '--', *paths)
            entry['sourceFiles'] = self.c.source_inventory(repo)
        manifest['docFiles'] = {name: digest for name, digest in manifest['platforms']['neoforge']['sourceFiles'].items() if name.startswith('docs/')}
        self.file('verified-fixture.json', json.dumps(manifest))
        output = self.root/'military6-vehicles-preview-runtime-fixture'
        print('Runtime collect fixture retained: '+str(self.root))
        stream = io.StringIO()
        with redirect_stdout(stream):
            self.c.collect(['--workspace', str(self.root), '--verified-manifest', str(self.root/'verified-fixture.json'), '--output', str(output)])
        result = json.loads(stream.getvalue())
        self.assertEqual('COLLECTED', result['status'])
        for platform, entry in manifest['platforms'].items():
            repo = Path(entry['repo']);diff = (output/'sources'/(platform+'-tracked.diff')).read_bytes()
            for name in ('README.md', *paths):self.assertIn(name.encode(), diff)
            for name in payloads[platform]:self.assertNotIn(name.encode(), diff)
            self.assertNotIn(b'M6_REVIEWED_RUNTIME_PAYLOAD', diff)
            for kind in ('full-source', 'changes'):
                with ZipFile(output/'sources'/(platform+'-'+kind+'.zip')) as archive:
                    for name in archive.namelist():
                        self.assertNotIn(name, payloads[platform])
                        self.assertNotIn(b'M6_REVIEWED_RUNTIME_PAYLOAD', archive.read(name))
            for name, data in payloads[platform].items():self.assertEqual(data, (repo/name).read_bytes())
        with ZipFile(result['archive']) as archive:
            self.assertIsNone(archive.testzip())
            for name in archive.namelist():
                for runtime in ('logs/debug-1.log.gz', 'logs/debug-2.log.gz'):self.assertNotIn(runtime, name)
                self.assertNotIn(b'M6_REVIEWED_RUNTIME_PAYLOAD', archive.read(name))

    def delivery(self):
        manifest = {'schemaVersion': 1, 'status': 'VERIFIED', 'version': self.c.VERSION, 'finalSourceFreeze': True, 'platforms': {}}
        manifest['protectedFiles'] = {**self.protected_fixture(self.c.M5_NAME), **self.protected_fixture(self.c.M6_NAME)}
        self.c.M5_ZIP_SHA = manifest['protectedFiles'][self.c.M5_NAME+'.zip']
        self.c.M6_ZIP_SHA = manifest['protectedFiles'][self.c.M6_NAME+'.zip']
        original = 'docs/\u5236\u4f5c\u8d44\u6599/\u53c2\u8003\u6a21\u578b/blackwatch_two_hand_sneak.bbmodel'
        self.c.ORIGINAL_BB_SHA = self.file(original, 'original model')['sha256']
        for platform, (mc, loader) in self.c.PLATFORMS.items():
            repo = self.repo('.blackwatch-worktrees/'+platform+'-military6')
            self.file('.gitignore', 'build/\n.superpowers/\n', repo)
            for name in ('docs/MILITARY6_PLAYER_TEST_ZH.md', 'docs/\u5236\u4f5c\u8d44\u6599/M6_\u8f7d\u5177\u4e0e\u8f66\u5e93.md',
                         'docs/verification/MILITARY6_fixture.md', original):
                self.file(name, 'original model' if name == original else 'fixture guide', repo)
            self.file('gradle.properties', f'mod_archive_name=Blacklight-Ascension\nminecraft_version={mc}\nmod_version={self.c.VERSION}\n', repo)
            native = 'assets/blacklight/native_animations/biped/idle.json'
            self.file('src/main/resources/'+native, '{}\n', repo)
            for index in range(253):self.file(f'art/motion-profile/{index}.json', '{}\n', repo)
            self.file('art/motion-profile-manifest.json', json.dumps({'files': {f'{i}.json': {} for i in range(253)}}), repo)
            for chassis in ('truck', 'apc', 'tank'):self.file('art/military-vehicles/'+chassis+'.bbmodel', '{}', repo)
            preview_sources = {}
            for name in ('tools/VehicleMeshCapture.java', 'src/main/java/com/codex/blacklight/client/model/MilitaryVehicleModel.java',
                         'src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java',
                         'src/main/resources/assets/blacklight/textures/entity/military_vehicles_blackwatch.png',
                         'src/main/resources/assets/blacklight/textures/entity/military_vehicles_military.png'):
                preview_sources[name] = self.file(name, 'capture fixture', repo)['sha256']
            for faction in ('military', 'blackwatch'):self.file('art/military-vehicles/textures/'+faction+'.png', b'png fixture', repo)
            for source_set, batches in (('blackwatchTest', M5_BATCHES), ('vehicleTest', M6_BATCHES)):
                annotations = ''.join(f'@GameTest(template="blackwatch_arena", batch="{batch}")\nvoid test{i}_{batch}() {{}}\n'
                                      for batch, count in batches.items() for i in range(count))
                self.file('src/'+source_set+'/java/Fixture.java', annotations, repo)
            for source_set in ('cityMilitaryTest', 'officersTest', 'garrisonTest', 'terrainTest', 'homeTest', 'identityMilitaryTest'):
                self.file('src/'+source_set+'/java/Support.java', 'class Support {}', repo)
            jar_name = f'Blacklight-Ascension-MC{mc}-{loader}-{self.c.VERSION}-bundled.jar'
            inactive = f'data/blacklight/{"recipe" if platform == "neoforge" else "recipes"}/military_fuel_can.json'
            fuel_sha = self.file('src/main/resources/'+inactive, '{}', repo)['sha256']
            self.file('tools/package_release.py', 'import json,sys\nfrom pathlib import Path\n'
                      'assert sys.argv[1:] == ["--profile", "bundled", "--verify-only"]\n'
                      f'assert Path("build/libs/{jar_name}").is_file()\n'
                      f'print(json.dumps({{"version": "{self.c.VERSION}", "profile": "bundled", "outputWritten": False, "geckoLibPackaged": False, "inactiveResourcesVerified": {{"{inactive}": "{fuel_sha}"}}}}))\n', repo)
            self.file('tools/archive_military6_catalog.py', 'import json\nprint(json.dumps({"status":"PASS","outputWritten":False,'
                      '"catalogue_integrity":"PASS","source_snapshots":208,"preview_pngs":30,"icon_pngs":13,"active_recipes_per_loader":10,"gameplay_pass_claimed":False,"errors":[]}))\n', repo)
            for name in self.c.CLIENT_VIEW_SOURCES:
                if not (repo/name).exists():self.file(name, '// isolated source fixture\n', repo)
            self.c.VEHICLE_AUTHORING_SHA = self.c.sha(repo/'art/military-vehicles/vehicle-authoring.json')
            git(repo, 'add', '.')
            head = checkpoint(repo);self.c.AUTHOR_HEADS[platform] = head
            entry = {'repo': str(repo), 'head': head, 'branch': git(repo, 'branch', '--show-current').decode().strip()}
            jar = repo/'build/libs'/jar_name;jar.parent.mkdir(parents=True)
            with ZipFile(jar, 'w') as archive:
                archive.writestr('blacklight.mixins.json', json.dumps({'mixins': ['VehicleProjectileCollisionMixin', 'VehicleEntityCollisionMixin'], 'client': ['client.VehiclePlayerPoseMixin']}))
                for name in self.c.VEHICLE_RECIPES:archive.writestr(f'data/blacklight/{"recipe" if platform == "neoforge" else "recipes"}/{name}.json', '{}')
                for name in self.c.VEHICLE_CLASSES:archive.writestr(name, b'compiled fixture')
                for name in ('assets/blacklight/military/vehicle-models.json', 'data/blacklight/military/vehicle-mounts.json'):
                    archive.writestr(name, json.dumps({'sourceSha256': self.c.VEHICLE_AUTHORING_SHA}))
                archive.writestr('com/codex/blacklight/registry/ModEntities.class', ' '.join(self.c.VEHICLE_ENTITIES))
                archive.writestr(native, b'{}\n')
                for index in range(253):archive.writestr(f'blacklight/bundled_motions/{index}.json', b'{}\n')
            entry['jar'] = {'path': jar.relative_to(repo).as_posix(), 'sha256': self.c.sha(jar)}
            xml = {f'build/test-results/test/TEST-{suite}.xml': self.file(f'build/test-results/test/TEST-{suite}.xml',
                   f'<testsuite name="{suite}" tests="1" failures="0" errors="0" skipped="0"/>', repo)['sha256'] for suite in self.c.REQUIRED_JUNIT_SUITES}
            entry['junit'] = {'expectedTests': 15, 'xmlFiles': xml, 'log': self.file('.superpowers/qa/unit.log', 'BUILD SUCCESSFUL', repo)}
            previous_root = self.root;self.root = repo
            game = self.game(234, batches={**M5_BATCHES, **M6_BATCHES}, batchIndex=0 if platform == 'neoforge' else 1,
                             covers=['military5-regression', 'military6-vehicles'])
            entry['gameTests'] = [game]
            garage, report = self.audit();report['loader'] = platform+'-'+mc
            garage['json'] = self.file('.superpowers/run/vehicle-garage-audit.json', json.dumps(report))
            entry['garageAudit'] = garage;self.root = previous_root
            capture = self.file('build/mesh.json', '[]', repo)
            images = {f'{i}.png': self.file('art/preview/'+str(i)+'.png', b'png fixture', repo)['sha256'] for i in range(9)}
            preview = self.file('art/preview/manifest.json', json.dumps({'kind': 'actual-production-ModelPart-CPU-preview',
                                'not_game_screenshot': True, 'scenes': 8, 'files': images, 'capture_sha256': capture['sha256']}), repo)
            entry['vehiclePreview'] = {'manifest': preview, 'capture': capture, 'sourceFiles': preview_sources}
            entry['clientView'] = self.client_view(repo)
            entry['sourceFiles'] = self.c.source_inventory(repo);entry['evidenceFiles'] = self.c.required_evidence(entry)
            manifest['platforms'][platform] = entry
        manifest['docFiles'] = {name: digest for name, digest in manifest['platforms']['neoforge']['sourceFiles'].items() if name.startswith('docs/')}
        return manifest

    def test_manifest_jar_must_be_the_exact_verified_canonical_artifact(self):
        manifest = self.delivery()
        self.c.preflight(self.root, manifest)
        for platform, entry in manifest['platforms'].items():
            with self.subTest(platform=platform):
                repo = Path(entry['repo']);original = entry['jar'];canonical = repo/original['path']
                stale = canonical.with_name('stale-'+canonical.name)
                stale.write_bytes(canonical.read_bytes())
                with ZipFile(stale, 'a') as archive:archive.writestr('stale-build.txt', 'older build')
                entry['jar'] = {'path': stale.relative_to(repo).as_posix(), 'sha256': self.c.sha(stale)}
                with self.assertRaisesRegex(RuntimeError, 'canonical|verified.*JAR'):
                    self.c.preflight(self.root, manifest)
                entry['jar'] = original

    def test_manifest_jar_hash_is_stable_during_package_verification(self):
        manifest = self.delivery();entry = manifest['platforms']['neoforge'];repo = Path(entry['repo'])
        script = repo/'tools/package_release.py'
        script.write_text(script.read_text(encoding='utf-8')+'from zipfile import ZipFile\n'
                          f'with ZipFile({entry["jar"]["path"]!r}, "a") as archive: archive.writestr("changed.txt", "changed during verification")\n', encoding='utf-8')
        entry['sourceFiles']['tools/package_release.py'] = self.c.sha(script)
        with self.assertRaisesRegex(RuntimeError, 'changed|SHA256'):
            self.c.preflight(self.root, manifest)

    def test_game_evidence_rejects_repeated_run_or_copied_logs(self):
        manifest = self.delivery();entry = manifest['platforms']['neoforge'];repo = Path(entry['repo'])
        original = self.game(18, root=repo, batches={'vehicle6': 12, 'vehicle6usability': 3, 'vehicle6permissions': 3},
                             covers=['military5-regression', 'military6-vehicles'])
        for kind in ('same-log-eight-times', 'copied-log-new-run', 'same-run-different-log'):
            with self.subTest(kind=kind):
                duplicate = dict(original)
                if kind != 'same-log-eight-times':
                    text = (repo/original['log']['path']).read_text(encoding='utf-8')
                    duplicate['log'] = self.file('.superpowers/qa/copy.log', text+('\n' if kind == 'same-run-different-log' else ''), repo)
                if kind == 'copied-log-new-run':
                    duplicate['runDirectory'] = '.superpowers/copied-run'
                    duplicate['versionLogs'] = [self.file('.superpowers/copied-run/logs/debug.log', self.c.VERSION, repo)]
                entry['gameTests'] = [original]*8 if kind == 'same-log-eight-times' else [original]+[duplicate]*7
                entry['evidenceFiles'] = self.c.required_evidence(entry)
                with self.assertRaisesRegex(RuntimeError, 'Duplicate|unique'):
                    self.c.preflight(self.root, manifest)

    def test_game_evidence_rejects_handwritten_coverage_for_partial_suites(self):
        manifest = self.delivery();entry = manifest['platforms']['neoforge'];repo = Path(entry['repo'])
        cases = ({'vehicle6': 151}, {**M5_BATCHES, 'vehicle6': 102},
                 {**M5_BATCHES, 'homeTransfer5': 1, **M6_BATCHES})
        for batches in cases:
            with self.subTest(batches=batches):
                entry['gameTests'] = [self.game(sum(batches.values()), root=repo, batches=batches,
                                              covers=['military5-regression', 'military6-vehicles'])]
                entry['evidenceFiles'] = self.c.required_evidence(entry)
                with self.assertRaisesRegex(RuntimeError, 'coverage|suite|batch'):
                    self.c.preflight(self.root, manifest)

    def test_game_evidence_cannot_reuse_log_bytes_between_loaders(self):
        manifest = self.delivery();neo = manifest['platforms']['neoforge'];forge = manifest['platforms']['forge']
        original = Path(neo['repo'])/neo['gameTests'][0]['log']['path']
        forge['gameTests'][0]['log'] = self.file(forge['gameTests'][0]['log']['path'], original.read_bytes(), Path(forge['repo']))
        forge['evidenceFiles'] = self.c.required_evidence(forge)
        with self.assertRaisesRegex(RuntimeError, 'Duplicate|unique'):
            self.c.preflight(self.root, manifest)

    def test_game_evidence_accepts_distinct_complete_suites_without_handwritten_covers(self):
        manifest = self.delivery()
        for entry in manifest['platforms'].values():
            repo = Path(entry['repo'])
            first = self.game(132, root=repo, batches=M5_BATCHES, runDirectory='.superpowers/m5-run', logPath='.superpowers/qa/m5.log')
            second = self.game(102, root=repo, batches=M6_BATCHES, runDirectory='.superpowers/m6-run', logPath='.superpowers/qa/m6.log', batchIndex=1)
            text = (repo/first['log']['path']).read_text(encoding='utf-8').replace(
                "'defaultBatch:0' (60 tests)...", "'defaultBatch:0' (50 tests)...\n[Server thread/INFO] [minecraft/GameTestRunner]: Running test batch 'defaultBatch:1' (10 tests)...")
            first['log'] = self.file(first['log']['path'], text, repo)
            entry['gameTests'] = [first, second];entry['evidenceFiles'] = self.c.required_evidence(entry)
        self.file('verified-fixture.json', json.dumps(manifest));output = self.root/'military6-vehicles-preview-fixture'
        stream = io.StringIO()
        with redirect_stdout(stream):
            self.c.collect(['--workspace', str(self.root), '--verified-manifest', str(self.root/'verified-fixture.json'),
                            '--output', str(output), '--check-only'])
        result = json.loads(stream.getvalue())
        self.assertEqual(('PASS', False), (result['status'], result['outputWritten']))
        self.assertFalse(any(path.exists() for path in (output, output.with_name(output.name+'.staging'), Path(str(output)+'.zip'))))
        for platform in manifest['platforms']:
            self.assertEqual([['military5-regression'], ['military6-vehicles']], [game['covers'] for game in result['platforms'][platform]['gameTests']])

    def test_game_log_rejects_missing_duplicate_or_inconsistent_batches(self):
        entry = self.game();text = (self.root/entry['log']['path']).read_text(encoding='utf-8')
        batch = text.splitlines(keepends=True)[0]
        for invalid in (text.replace(batch, ''), text+batch, text.replace('(151 tests)', '(150 tests)'), text+text):
            with self.subTest(log=invalid):
                entry['log'] = self.file(entry['log']['path'], invalid)
                with self.assertRaisesRegex(RuntimeError, 'batch|single.*run'):
                    self.c.game_gate(self.root, entry)

    def test_missing_manifest_fails_before_any_output_is_created(self):
        with self.assertRaisesRegex(RuntimeError, 'manifest'):
            self.c.load_verified_manifest(self.root/'absent.json')
        self.assertEqual([], list(self.root.iterdir()))

    def test_unverified_or_wrong_version_manifest_is_rejected(self):
        for status, version in [('PENDING', self.c.VERSION), ('VERIFIED', 'military.5')]:
            self.file('manifest.json', json.dumps({'schemaVersion': 1, 'status': status, 'version': version, 'finalSourceFreeze': True}))
            with self.assertRaises(RuntimeError):self.c.load_verified_manifest(self.root/'manifest.json')

    def test_hash_tampering_and_windows_parent_traversal_are_rejected(self):
        entry = self.file('run/data.json', '{}')
        (self.root/'run/data.json').write_text('changed')
        with self.assertRaises(RuntimeError):self.c.recorded_file(self.root, entry)
        for name in ('../outside', '..\\outside', 'C:/outside', '\\server\\share', 'safe/../../outside'):
            with self.assertRaises(RuntimeError):self.c.within(self.root, name)

    def test_actual_count_and_same_run_debug_version_are_accepted(self):
        value = self.c.game_gate(self.root, self.game())
        self.assertEqual(151, value['requiredPassed'])
        self.assertTrue(value['normalSavingShutdown'])

    def test_failed_or_stale_game_counts_are_rejected(self):
        entry = self.game()
        for text in ('132 GAME TESTS COMPLETE\nAll 132 required tests passed\n',
                     '151 GAME TESTS COMPLETE\n3 required tests failed :(\nAll 148 required tests passed\n'):
            entry['log'] = self.file('qa/game.log', text+'Stopping server\nAll dimensions are saved\nGame test server shutting down\nBUILD SUCCESSFUL\n')
            with self.assertRaises(RuntimeError):self.c.game_gate(self.root, entry)

    def test_game_pass_without_saving_shutdown_is_rejected(self):
        entry = self.game()
        entry['log'] = self.file('qa/game.log', self.c.VERSION+'\n151 GAME TESTS COMPLETE\nAll 151 required tests passed\nBUILD SUCCESSFUL\n')
        with self.assertRaises(RuntimeError):self.c.game_gate(self.root, entry)

    def test_unrelated_debug_log_cannot_supply_version(self):
        entry = self.game()
        entry['versionLogs'] = [self.file('other/logs/debug.log', self.c.VERSION)]
        with self.assertRaisesRegex(RuntimeError, 'same run'):self.c.game_gate(self.root, entry)

    def test_junit_uses_exact_xml_and_rejects_skipped_tests(self):
        entry = {'expectedTests': 2, 'log': self.file('qa/unit.log', 'BUILD SUCCESSFUL\n'),
                 'xmlFiles': {'build/test-results/test/TEST-A.xml': self.file('build/test-results/test/TEST-A.xml', '<testsuite name="A" tests="2" failures="0" errors="0" skipped="0"/>')['sha256']}}
        self.assertEqual(2, self.c.junit_gate(self.root, entry)['tests'])
        entry['xmlFiles']['build/test-results/test/TEST-A.xml'] = self.file('build/test-results/test/TEST-A.xml', '<testsuite name="A" tests="2" failures="0" errors="0" skipped="1"/>')['sha256']
        with self.assertRaises(RuntimeError):self.c.junit_gate(self.root, entry)

    def test_unlisted_junit_xml_is_rejected(self):
        first = self.file('build/test-results/test/TEST-A.xml', '<testsuite name="A" tests="2" failures="0" errors="0" skipped="0"/>')
        entry = {'expectedTests': 2, 'log': self.file('qa/unit.log', 'BUILD SUCCESSFUL\n'), 'xmlFiles': {first['path']: first['sha256']}}
        self.file('build/test-results/test/TEST-B.xml', '<testsuite name="B" tests="3"/>')
        with self.assertRaisesRegex(RuntimeError, 'XML inventory'):self.c.junit_gate(self.root, entry)

    def test_two_saved_processes_are_required(self):
        entry, report = self.audit()
        self.assertEqual('PASS', self.c.garage_gate(self.root, entry, 'neoforge')['status'])
        for field, invalid in [('verifyPid', 101), ('status', 'PHASE1_SAVED'), ('apcActuallyUnloaded', False)]:
            changed = dict(report);changed[field] = invalid
            entry['json'] = self.file('run/vehicle-garage-audit.json', json.dumps(changed))
            with self.assertRaises(RuntimeError):self.c.garage_gate(self.root, entry, 'neoforge')

    def test_restart_pass_without_normal_save_is_rejected(self):
        entry, _ = self.audit();entry['verifyLog'] = self.file('qa/verify.log', self.c.VERSION+'\nVEHICLE_GARAGE_AUDIT PASS\nBUILD SUCCESSFUL\n')
        with self.assertRaises(RuntimeError):self.c.garage_gate(self.root, entry, 'neoforge')

    def test_source_overlay_preserves_staged_unstaged_untracked_and_deletion(self):
        repo = self.repo();(repo/'src/main').mkdir(parents=True)
        (repo/'src/main/A.java').write_bytes(b'class A {}\n');git(repo, 'add', 'src/main/A.java')
        (repo/'README.md').write_text('modified\n');(repo/'docs/deleted.md').unlink()
        (repo/'tools').mkdir();(repo/'tools/new.py').write_bytes(b'print(1)\n')
        value = self.c.source_overlay(repo, self.root/'overlay.zip')
        self.assertEqual(['docs/deleted.md'], value['deleted'])
        with ZipFile(self.root/'overlay.zip') as archive:
            self.assertEqual(b'class A {}\n', archive.read('src/main/A.java'))
            self.assertEqual(b'print(1)\n', archive.read('tools/new.py'))
            self.assertFalse(json.loads(archive.read('PATCH-BASE.json'))['featureCommit'])

    def test_full_source_archive_preserves_bytes_and_excludes_runtime(self):
        repo = self.repo();(repo/'build').mkdir();(repo/'build/private.log').write_text('private')
        inventory = self.c.source_inventory(repo)
        self.c.source_archive(repo, self.root/'source.zip', inventory)
        with ZipFile(self.root/'source.zip') as archive:
            self.assertEqual(b'baseline\r\n', archive.read('README.md'))
            self.assertNotIn('build/private.log', archive.namelist())
            self.assertEqual(inventory, json.loads(archive.read('SOURCE_MANIFEST.json')))

    def test_source_archive_rejects_changed_freeze(self):
        repo = self.repo();inventory = self.c.source_inventory(repo);(repo/'README.md').write_text('later')
        with self.assertRaises(RuntimeError):self.c.source_archive(repo, self.root/'source.zip', inventory)
        self.assertFalse((self.root/'source.zip').exists())

    def test_private_and_commercial_cache_names_are_not_source_payload(self):
        for name in ('docs/vehicle-reference-cache/a.png', 'tools/.env', 'art/secrets/token.json', 'docs/PCL/log.txt', 'docs/world/level.dat'):
            self.assertFalse(self.c.allowed_source(name), name)

    def test_world_package_java_is_preserved_as_complete_source(self):
        repo = self.repo();name = 'src/main/java/com/codex/blacklight/world/WorldSource.java'
        path = repo/name;path.parent.mkdir(parents=True);path.write_bytes(b'class WorldSource {}\n')
        self.assertIn(name, self.c.source_inventory(repo))

    def test_existing_outputs_are_never_overwritten(self):
        output = self.root/'military6-vehicles-preview-test';output.mkdir()
        with self.assertRaises(RuntimeError):self.c.new_destination(self.root, {}, output)
        self.assertTrue(output.exists())

    def test_cli_refuses_pending_evidence_without_creating_delivery(self):
        self.file('pending.json', json.dumps({'schemaVersion': 1, 'status': 'PENDING', 'version': self.c.VERSION, 'finalSourceFreeze': False}))
        output = self.root/'military6-vehicles-preview-pending'
        with self.assertRaises(RuntimeError):
            self.c.collect(['--workspace', str(self.root), '--verified-manifest', str(self.root/'pending.json'), '--output', str(output)])
        self.assertFalse(output.exists());self.assertFalse(output.with_name(output.name+'.staging').exists())
        self.assertFalse(Path(str(output)+'.zip').exists())

    def test_author_bytes_accept_exact_git_crlf_filters_and_reject_arbitrary_edits(self):
        repo = self.repo();native = 'src/main/resources/assets/blacklight/native_animations/biped/idle.json'
        path = repo/native;path.parent.mkdir(parents=True);path.write_bytes(b'{"motion":true}\r\n')
        profile = repo/'art/motion-profile';profile.mkdir(parents=True)
        for index in range(253):(profile/f'{index}.json').write_bytes(b'{}\n')
        (repo/'art/motion-profile-manifest.json').write_text(json.dumps({'files': {f'{index}.json': {} for index in range(253)}}))
        git(repo, 'add', '.')
        checkpoint(repo)
        self.c.AUTHOR_HEADS['neoforge'] = git(repo, 'rev-parse', 'HEAD').decode().strip()
        jar = self.root/'author.jar'
        with ZipFile(jar, 'w') as archive:
            archive.writestr(native.removeprefix('src/main/resources/'), path.read_bytes())
            for item in profile.glob('*.json'):archive.writestr('blacklight/bundled_motions/'+item.name, item.read_bytes())
        result = self.c.author_gate(repo, jar, 'neoforge')
        self.assertNotEqual(result['authorBlobSha256'][native], result['checkoutSha256'][native])
        path.write_bytes(b'{ "motion":true}\r\n')
        with self.assertRaisesRegex(RuntimeError, 'Author resource changed'):self.c.author_gate(repo, jar, 'neoforge')

    def test_vehicle_jar_requires_single_mixin_and_all_ten_active_recipes(self):
        jar = self.root/'vehicles.jar'
        def write(mixins, omitted=None):
            with ZipFile(jar, 'w') as archive:
                archive.writestr('blacklight.mixins.json', json.dumps({'mixins': mixins+['VehicleEntityCollisionMixin'], 'client': ['client.VehiclePlayerPoseMixin']}))
                for name in self.c.VEHICLE_RECIPES:
                    if name != omitted:archive.writestr('data/blacklight/recipe/'+name+'.json', '{}')
                for name in self.c.VEHICLE_CLASSES:archive.writestr(name, b'compiled fixture')
                for name in ('assets/blacklight/military/vehicle-models.json', 'data/blacklight/military/vehicle-mounts.json'):
                    archive.writestr(name, json.dumps({'sourceSha256': self.c.VEHICLE_AUTHORING_SHA}))
                archive.writestr('com/codex/blacklight/registry/ModEntities.class', ' '.join(self.c.VEHICLE_ENTITIES))
        write(['VehicleProjectileCollisionMixin']);self.c.vehicle_jar_gate(jar, 'neoforge')
        write(['VehicleProjectileCollisionMixin', 'VehicleProjectileCollisionMixin'])
        with self.assertRaises(RuntimeError):self.c.vehicle_jar_gate(jar, 'neoforge')
        write(['VehicleProjectileCollisionMixin'], 'tank_he_shell')
        with self.assertRaises(RuntimeError):self.c.vehicle_jar_gate(jar, 'neoforge')

    def test_vehicle_jar_rejects_test_runtime_and_wrong_platform_recipe_path(self):
        jar = self.root/'bad.jar'
        with ZipFile(jar, 'w') as archive:archive.writestr('com/codex/blacklight/gametest/vehicle/Audit.class', b'qa')
        with self.assertRaises(RuntimeError):self.c.vehicle_jar_gate(jar, 'forge')

    def test_m61_requires_ten_active_recipes_absent_dormant_recipe_and_native_mixins(self):
        self.assertEqual('0.3.4-preview.20261028-military.6.1', self.c.VERSION)
        self.assertEqual(10, len(self.c.VEHICLE_RECIPES))
        self.assertNotIn('military_fuel_can', self.c.VEHICLE_RECIPES)
        for platform, directory in (('neoforge', 'recipe'), ('forge', 'recipes')):
            jar = self.root/(platform+'.jar')
            def write(missing=None, extra=None, duplicate=None):
                common = ['VehicleProjectileCollisionMixin', 'VehicleEntityCollisionMixin']
                client = ['client.VehiclePlayerPoseMixin']
                if missing in common:common.remove(missing)
                if missing in client:client.remove(missing)
                if duplicate:common.append(duplicate)
                with ZipFile(jar, 'w') as archive:
                    archive.writestr('blacklight.mixins.json', json.dumps({'mixins': common, 'client': client}))
                    for name in ('military_truck_chassis', 'military_apc_chassis', 'military_tank_chassis',
                                 'blackwatch_truck_chassis', 'blackwatch_apc_chassis', 'blackwatch_tank_chassis',
                                 'vehicle_repair_parts', 'autocannon_round', 'tank_ap_shell', 'tank_he_shell'):
                        if name != missing:archive.writestr(f'data/blacklight/{directory}/{name}.json', '{}')
                    for name in self.c.VEHICLE_CLASSES:archive.writestr(name, b'compiled fixture')
                    for name in ('assets/blacklight/military/vehicle-models.json', 'data/blacklight/military/vehicle-mounts.json'):
                        archive.writestr(name, json.dumps({'sourceSha256': self.c.VEHICLE_AUTHORING_SHA}))
                    archive.writestr('com/codex/blacklight/registry/ModEntities.class', ' '.join(self.c.VEHICLE_ENTITIES))
                    if extra:archive.writestr(extra, b'forbidden fixture')
            write();self.c.vehicle_jar_gate(jar, platform)
            for missing in ('VehicleEntityCollisionMixin', 'client.VehiclePlayerPoseMixin', 'tank_he_shell'):
                with self.subTest(platform=platform, missing=missing):
                    write(missing=missing)
                    with self.assertRaises(RuntimeError):self.c.vehicle_jar_gate(jar, platform)

            for duplicate in ('VehicleEntityCollisionMixin', 'client.VehiclePlayerPoseMixin'):
                write(duplicate=duplicate)
                with self.assertRaises(RuntimeError):self.c.vehicle_jar_gate(jar, platform)
            for extra in (f'data/blacklight/{directory}/military_fuel_can.json',
                          'com/codex/blacklight/client/vehicle/VehicleViewSmoke.class',
                          'com/codex/blacklight/client/vehicle/VehicleViewSmoke$Shot.class',
                          'src/vehicleVisualTest/java/com/codex/blacklight/client/vehicle/VehicleViewSmoke.java',
                          'com/codex/blacklight/client/vehicle/PlayerGLSmoke.class',
                          'META-INF/jarjar/vehicle-view-smoke.jar'):
                with self.subTest(platform=platform, extra=extra):
                    write(extra=extra)
                    with self.assertRaises(RuntimeError):self.c.vehicle_jar_gate(jar, platform)

    def package_fixture(self, platform, case):
        repo = self.repo('package-'+platform+'-'+case)
        mc, loader = self.c.PLATFORMS[platform]
        stem = f'Fixture-MC{mc}-{loader}-{self.c.VERSION}-bundled'
        self.file('.gitignore', 'build/\n', repo)
        self.file('gradle.properties', f'mod_archive_name=Fixture\nminecraft_version={mc}\nmod_version={self.c.VERSION}\nmod_id=blacklight\n', repo)
        notices = ('LICENSE', 'THIRD_PARTY_ASSETS.md', 'THIRD_PARTY_NOTICES.md')
        for name in (*notices, 'docs/guide.md'):self.file(name, 'fixture document\n', repo)
        motion = b'{"motion":"fixture"}\n'
        self.file('art/motion-profile/idle.json', motion, repo)
        self.file('art/motion-profile-manifest.json', json.dumps({'version': 1, 'files': {'idle.json': {'bytes': len(motion), 'sha256': hashlib.sha256(motion).hexdigest()}}}), repo)
        self.file('src/main/java/fixture/Production.java', 'package fixture; class Production {}', repo)
        self.file('src/vehicleVisualTest/java/com/codex/blacklight/client/vehicle/VehicleViewSmoke.java',
                  'package com.codex.blacklight.client.vehicle; class VehicleViewSmoke {}', repo)
        compiled = b'\xca\xfe\xba\xbe\0\0\0\x3dcompiled-fixture'
        self.file('build/classes/java/main/fixture/Production.class', compiled, repo)
        self.file('build/classes/java/main/com/codex/blacklight/client/vehicle/VehicleViewSmoke$Shot.class', compiled, repo)
        directory = 'recipe' if platform == 'neoforge' else 'recipes'
        dormant = f'data/blacklight/{directory}/military_fuel_can.json'
        resource_data = {'blacklight.mixins.json': b'{"package":"fixture","mixins":[],"client":[]}',
                         'assets/blacklight/lang/en_us.json': b'{}', 'assets/blacklight/lang/zh_cn.json': b'{}',
                         f'data/blacklight/{directory}/tank_he_shell.json': b'{"active":true}',
                         f'data/blacklight/{"loot_table" if platform == "neoforge" else "loot_tables"}/chests/base_supplies.json': b'{"entries":[]}',
                         'assets/blacklight/models/item/military_fuel_can.json': b'{"parent":"minecraft:item/generated"}',
                         'assets/blacklight/textures/item/military_fuel_can.png': b'preserved sprite',
                         'assets/blacklight/native_animations/biped/idle.json': b'{"author":"preserved"}'}
        if case != 'disk-missing':self.file('src/main/resources/'+dormant, '{"dormant":true}', repo)
        for name, data in resource_data.items():self.file('src/main/resources/'+name, data, repo)
        jar = repo/'build/libs'/(stem+'.jar');jar.parent.mkdir(parents=True)
        with ZipFile(jar, 'w') as archive:
            archive.writestr('META-INF/'+('neoforge.mods.toml' if platform == 'neoforge' else 'mods.toml'),
                             f'[[mods]]\nmodId="blacklight"\nversion="{self.c.VERSION}"\nauthors="末者"\n')
            archive.writestr('META-INF/MANIFEST.MF', 'Blacklight-Build-Profile: bundled\nBlacklight-Motion-Source: repository\nBlacklight-Bundled-Motions: true\nBlacklight-Asset-Notice: THIRD_PARTY_ASSETS.md\nBlacklight-Public-Release-Ready: false\n')
            for name in notices:archive.writestr(name, (repo/name).read_bytes())
            archive.writestr('blacklight/bundled_motions/idle.json', motion)
            archive.writestr('fixture/Production.class', compiled)
            if platform == 'forge':archive.writestr('blacklight.refmap.json', '{}')
            for name, data in resource_data.items():
                if case == 'active-missing' and name.endswith('/tank_he_shell.json'):continue
                if case == 'loot-missing' and '/chests/' in name:continue
                if case == 'model-missing' and '/models/item/' in name:continue
                archive.writestr(name, data)
            extras = {'fuel-present': dormant, 'qa-class': 'com/codex/blacklight/client/vehicle/VehicleViewSmoke.class',
                      'qa-inner': 'com/codex/blacklight/client/vehicle/VehicleViewSmoke$Shot.class',
                      'qa-source': 'src/vehicleVisualTest/java/com/codex/blacklight/client/vehicle/VehicleViewSmoke.java',
                      'nested-gecko': 'META-INF/jarjar/geckolib.jar'}
            if case in extras:archive.writestr(extras[case], compiled)
        self.file('build/reobfJarJar/output.jar', jar.read_bytes(), repo)
        dist = repo/'build/distributions'/(stem+'.zip');dist.parent.mkdir(parents=True)
        with ZipFile(dist, 'w') as archive:
            archive.writestr('mods/'+jar.name, jar.read_bytes())
            for name in (*notices, 'README.md', 'docs/guide.md', 'docs/deleted.md'):archive.writestr(name, (repo/name).read_bytes())
        self.file('tools/untracked-adapter.py', '# complete source\n', repo)
        self.file('art/untracked-model.bbmodel', '{}', repo)
        return repo

    def test_m61_real_package_verifier_preserves_disk_fuel_but_rejects_jar_fuel_and_other_omissions(self):
        for platform in self.c.PLATFORMS:
            script = SCRIPT.resolve().parents[2]/(platform+'-military6')/'tools/package_release.py'
            for case in ('good', 'fuel-present', 'disk-missing', 'active-missing', 'loot-missing', 'model-missing',
                         'qa-class', 'qa-inner', 'qa-source', 'nested-gecko'):
                with self.subTest(platform=platform, case=case):
                    repo = self.package_fixture(platform, case)
                    result = subprocess.run([sys.executable, '-B', '-X', 'utf8', str(script), '--repo', str(repo), '--verify-only'],
                                            capture_output=True, encoding='utf-8', errors='replace')
                    if case == 'good':
                        self.assertEqual(0, result.returncode, result.stdout+result.stderr)
                        report = json.loads(result.stdout)
                        self.assertIs(report['outputWritten'], False)
                        self.assertEqual(1, len(report['inactiveResourcesVerified']))
                        self.assertIs(report['geckoLibPackaged'], False)
                        spec = importlib.util.spec_from_file_location('actual_package_'+platform, script)
                        package = importlib.util.module_from_spec(spec);spec.loader.exec_module(package)
                        source = package.source_snapshot(repo, None)
                        for name in ('tools/untracked-adapter.py', 'art/untracked-model.bbmodel'):
                            self.assertIn(name, source)
                        self.assertFalse((repo/'release').exists())
                    else:self.assertNotEqual(0, result.returncode, 'accepted '+case)

    def protected_fixture(self, name):
        frozen = {'frozen.txt': self.file(name+'/frozen.txt', 'old delivery')['sha256']}
        manifest = self.file(name+'/MANIFEST.json', json.dumps({'files': frozen}))
        return {name+'.zip': self.file(name+'.zip', b'frozen fixture')['sha256'],
                name+'/MANIFEST.json': manifest['sha256'],
                name+'/SHA256SUMS.txt': self.file(name+'/SHA256SUMS.txt', frozen['frozen.txt']+'  frozen.txt\n'+manifest['sha256']+'  MANIFEST.json\n')['sha256']}

    def test_m61_protects_the_original_m60_zip_manifest_sums_and_full_inventory(self):
        self.assertTrue(hasattr(self.c, 'protected_deliveries'), 'M6.0 protection is required alongside M5')
        expected = {**self.protected_fixture(self.c.M5_NAME), **self.protected_fixture(self.c.M6_NAME)}
        self.c.M5_ZIP_SHA = expected[self.c.M5_NAME+'.zip'];self.c.M6_ZIP_SHA = expected[self.c.M6_NAME+'.zip']
        self.c.protected_deliveries(self.root, expected)
        for name in (self.c.M6_NAME+'.zip', self.c.M6_NAME+'/MANIFEST.json', self.c.M6_NAME+'/SHA256SUMS.txt'):
            missing = dict(expected);missing.pop(name)
            with self.assertRaises(RuntimeError):self.c.protected_deliveries(self.root, missing)
        self.file(self.c.M6_NAME+'/unexpected.txt', 'must not enter frozen directory')
        with self.assertRaises(RuntimeError):self.c.protected_deliveries(self.root, expected)
        with self.assertRaises(RuntimeError):self.c.new_destination(self.root, {}, self.root/self.c.M6_NAME)
        self.c.new_destination(self.root, {}, self.root/(self.c.M6_NAME+'-r2'))

    def client_view(self, root=None):
        root = root or self.root
        from PIL import Image
        data = io.BytesIO();Image.new('RGB', (64, 48), '#52684b').save(data, format='PNG')
        sources = {name: (self.file(name, '// isolated contract fixture\n', root)['sha256'] if not (root/name).exists()
                         else self.c.sha(root/name)) for name in self.c.CLIENT_VIEW_SOURCES}
        run = '.superpowers/run-military6-client-fixture'
        base = run+'/vehicle-view-evidence/'
        frames = [];images = {}
        for index in range(50):
            record = self.file(base+str(index)+'.png', data.getvalue(), root)
            images[record['path']] = record['sha256']
            frames.append({'image': str(index)+'.png', 'playerClass': 'net.minecraft.client.player.LocalPlayer',
                           'vehicleUuid': str(uuid.uuid4()), 'seat': 'driver', 'cameraType': 'FIRST_PERSON', 'camera': [1, 2, 3]})
        report = self.file(base+'vehicle-view-report.json', json.dumps({'complete': True, 'automaticPass': True,
                           'requiresVisualReview': True, 'arbitrarySkinRendererCompatibilityVerified': False,
                           'source': 'real integrated-server Player, synchronized seat, production Camera, native F5 queue and GL framebuffer',
                           'plannedFrames': 50, 'frames': frames}), root)
        review = self.file(base+'root-visual-review.json', json.dumps({'report': report, 'images': images,
                           'openedAllImages': True, 'status': 'PASS', 'reviewer': 'isolated fixture reviewer',
                           'reviewedAt': '2026-10-28T12:00:00+08:00'}), root)
        return {'runDirectory': run, 'report': report, 'images': images, 'review': review, 'sourceFiles': sources,
                'log': self.file('.superpowers/qa/client.log', self.c.VERSION+'\nGL fixture finished\n', root), 'versionLogs': []}

    def test_m61_gl_requires_real_frame_report_exact_images_and_root_review(self):
        self.assertTrue(hasattr(self.c, 'client_view_gate'), 'Required real Player GL gate is missing')
        with self.assertRaisesRegex(RuntimeError, 'GL|client'):self.c.client_view_gate(self.root, None)
        entry = self.client_view();self.c.client_view_gate(self.root, entry)
        for field in ('report', 'review', 'images'):
            missing = dict(entry);missing.pop(field)
            with self.assertRaises(RuntimeError):self.c.client_view_gate(self.root, missing)
        wrong = dict(entry);wrong['images'] = dict(entry['images']);wrong['images'].pop(next(iter(wrong['images'])))
        with self.assertRaisesRegex(RuntimeError, 'image|frame'):self.c.client_view_gate(self.root, wrong)
        review = json.loads((self.root/entry['review']['path']).read_text(encoding='utf-8'))
        review['openedAllImages'] = False
        wrong = dict(entry);wrong['review'] = self.file(entry['review']['path'], json.dumps(review))
        with self.assertRaisesRegex(RuntimeError, 'review|opened'):self.c.client_view_gate(self.root, wrong)
        report = json.loads((self.root/entry['report']['path']).read_text(encoding='utf-8'))
        report.update(complete=False, automaticPass=False)
        wrong['report'] = self.file(entry['report']['path'], json.dumps(report))
        with self.assertRaisesRegex(RuntimeError, 'complete|GL'):self.c.client_view_gate(self.root, wrong)

    def test_m61_catalogue_extracts_dormant_fuel_and_real_ammo_repair_without_addfuel(self):
        spec = importlib.util.spec_from_file_location('current_m61_catalogue', SCRIPT.with_name('archive_military6_catalog.py'))
        catalogue = importlib.util.module_from_spec(spec);spec.loader.exec_module(catalogue)
        self.assertTrue(hasattr(catalogue, 'garage_rules'), 'Catalogue needs fuel-disabled rule extraction')
        root = SCRIPT.resolve().parents[1];java = root/catalogue.JAVA/'military/vehicle'
        service = (java/'VehicleGarageService.java').read_text(encoding='utf-8')
        self.assertNotIn('.addFuel(', service)
        rules = catalogue.garage_rules(service, (java/'VehicleInventory.java').read_text(encoding='utf-8'),
                                     (java/'VehicleSupplyRules.java').read_text(encoding='utf-8'))
        self.assertEqual('disabled', rules['fuel_gameplay'])
        self.assertTrue(rules['dormant_fuel']['snapshot_and_finite_api_retained'])
        self.assertEqual((20, 12, .2, .35), (rules['health_per_repair_part'], rules['loaded_native_dispatch_cap'],
                                           rules['low_primary_ammunition_ratio'], rules['low_health_ratio']))
        self.assertEqual('actual ItemEntity ammunition/repair cargo; runtime acceptance belongs to root', rules['automatic_supply'])
        self.assertEqual((.5, .6), (rules['resume_primary_ammunition_ratio'], rules['resume_health_ratio']))

    def test_m61_catalogue_copies_original_icons_and_uses_real_ammunition_keys(self):
        spec = importlib.util.spec_from_file_location('current_m61_catalogue_icons', SCRIPT.with_name('archive_military6_catalog.py'))
        catalogue = importlib.util.module_from_spec(spec);spec.loader.exec_module(catalogue)
        self.assertTrue(hasattr(catalogue, 'item_icons'), 'Catalogue must preserve original inventory sprites')
        root = SCRIPT.resolve().parents[2]/'neoforge-military6'
        legacy = catalogue.module(root/'tools/archive_military_catalog.py', 'm61_icon_reader')
        output = self.root/'catalogue-fixture';archive = legacy.Archive(output, root.parent/'forge-military6')
        rows = catalogue.item_icons(archive, root, output, {'machine_gun_round', 'rocket_round', 'autocannon_round', 'tank_he_shell', 'tank_ap_shell'})
        self.assertEqual(13, len(rows))
        self.assertTrue({'machine_gun_round', 'rocket_round', 'tank_he_shell', 'tank_ap_shell'} <= {row['id'] for row in rows})
        for row in rows:
            self.assertEqual((root/row['source']).read_bytes(), (output/row['path']).read_bytes())
            self.assertEqual(row['id'] != 'military_fuel_can', row['active'])
            if row['id'] not in ('machine_gun_round', 'rocket_round'):self.assertEqual([32, 32], row['size'])

    def test_m61_preparation_only_produces_pending_from_explicit_actual_selections(self):
        spec = importlib.util.spec_from_file_location('m61_pending_preparation', SCRIPT.with_name('prepare_military6_revision_manifest.py'))
        preparation = importlib.util.module_from_spec(spec);spec.loader.exec_module(preparation)
        observations = self.delivery()
        with self.assertRaisesRegex(RuntimeError, 'PENDING|VERIFIED'):preparation.prepare(self.root, observations, self.c)
        observations.update(status='PENDING', finalSourceFreeze=False)
        result = preparation.prepare(self.root, observations, self.c)
        self.assertEqual(('PENDING', False), (result['status'], result['finalSourceFreeze']))
        for platform, entry in result['platforms'].items():
            self.assertEqual(observations['platforms'][platform]['junit']['expectedTests'], entry['junit']['expectedTests'])
            self.assertEqual(self.c.source_inventory(Path(entry['repo'])), entry['sourceFiles'])
            self.assertEqual(set(self.c.required_evidence(entry)), set(entry['evidenceFiles']))
        self.file('pending.json', json.dumps(result))
        with self.assertRaises(RuntimeError):self.c.load_verified_manifest(self.root/'pending.json')
        observations['platforms']['neoforge'].pop('clientView')
        with self.assertRaisesRegex(RuntimeError, 'actual|GL'):preparation.prepare(self.root, observations, self.c)

    def test_m61_full_preflight_rejects_missing_gl_and_validation_older_than_java(self):
        manifest = self.delivery();self.c.preflight(self.root, manifest)
        entry = manifest['platforms']['neoforge'];view = entry.pop('clientView')
        with self.assertRaisesRegex(RuntimeError, 'GL|client'):self.c.preflight(self.root, manifest)
        entry['clientView'] = view;repo = Path(entry['repo'])
        self.file('src/test/java/fixture/LaterSourceTest.java', 'class LaterSourceTest {}', repo)
        entry['sourceFiles'] = self.c.source_inventory(repo)
        with self.assertRaisesRegex(RuntimeError, 'changed after final actual validation'):self.c.preflight(self.root, manifest)

    def test_m61_catalogue_template_uses_current_sizes_inactive_fuel_and_item_keys(self):
        spec = importlib.util.spec_from_file_location('m61_catalogue_template', SCRIPT.with_name('archive_military6_catalog.py'))
        catalogue = importlib.util.module_from_spec(spec);spec.loader.exec_module(catalogue)
        root = SCRIPT.resolve().parents[2]/'neoforge-military6'
        data = json.loads((root/'docs/制作资料/M6/data/catalog.json').read_text(encoding='utf-8'))
        for key, path in (('vehicles', 'vehicles.json'), ('mounts', 'vehicle-mounts.json')):
            data[key] = json.loads((root/catalogue.RES/'data/blacklight/military'/path).read_text(encoding='utf-8'))
        java = root/catalogue.JAVA/'military/vehicle'
        data['garage_rules'] = catalogue.garage_rules(*((java/name).read_text(encoding='utf-8') for name in
                                                      ('VehicleGarageService.java', 'VehicleInventory.java', 'VehicleSupplyRules.java')))
        for row in data['recipes']:row['active'] = row['id'] != 'military_fuel_can'
        legacy = catalogue.module(root/'tools/archive_military_catalog.py', 'm61_template_reader')
        output = self.root/'template-fixture/M6';output.mkdir(parents=True)
        data['item_icons'] = catalogue.item_icons(legacy.Archive(output, root.parent/'forge-military6'), root, output,
                                                {'machine_gun_round', 'rocket_round', 'autocannon_round', 'tank_he_shell', 'tank_ap_shell'})
        catalogue.write_cards(output, data)
        text = (output.parent/'M6_载具与车库.md').read_text(encoding='utf-8')
        for required in ('无需燃油', '10个active配方', 'inactive', '2.9×7.5×3.7', '3.3×7.0×3.3', '4.0×8.0×3.4',
                         'ItemEntity', '20%', '35%', 'machine_gun_round', 'rocket_round'):
            self.assertIn(required, text)
        for obsolete in ('满油移动秒', '燃料罐每次消耗', '11个真实配方', '最终卡车司机/军官座位锚点为x=±0.48'):
            self.assertNotIn(obsolete, text)
    def test_forge_jarjar_allows_only_exact_production_dependency_and_metadata(self):
        jar = self.root/'forge.jar';nested = 'META-INF/jarjar/mixinextras-forge-0.4.1.jar'
        dependency = b'known fixture MixinExtras 0.4.1 bytes'
        self.c.FORGE_MIXINEXTRAS_SHA = hashlib.sha256(dependency).hexdigest()
        metadata = {'jars': [{'identifier': {'group': 'io.github.llamalad7', 'artifact': 'mixinextras-forge'},
                              'version': {'range': '[0.4.1,)', 'artifactVersion': '0.4.1'},
                              'path': nested, 'isObfuscated': False}]}
        def write(meta, extra=None, payload=dependency, nested_path=nested):
            with ZipFile(jar, 'w') as archive:
                archive.writestr('blacklight.mixins.json', json.dumps({'mixins': ['VehicleProjectileCollisionMixin', 'VehicleEntityCollisionMixin'], 'client': ['client.VehiclePlayerPoseMixin']}))
                for name in self.c.VEHICLE_RECIPES:archive.writestr('data/blacklight/recipes/'+name+'.json', '{}')
                for name in self.c.VEHICLE_CLASSES:archive.writestr(name, b'compiled fixture')
                for name in ('assets/blacklight/military/vehicle-models.json', 'data/blacklight/military/vehicle-mounts.json'):
                    archive.writestr(name, json.dumps({'sourceSha256': self.c.VEHICLE_AUTHORING_SHA}))
                archive.writestr('com/codex/blacklight/registry/ModEntities.class', ' '.join(self.c.VEHICLE_ENTITIES))
                if nested_path:archive.writestr(nested_path, payload)
                if meta is not None:archive.writestr('META-INF/jarjar/metadata.json', json.dumps(meta))
                for name, data in (extra or {}).items():archive.writestr(name, data)
        write(metadata)
        self.assertEqual('PASS', self.c.vehicle_jar_gate(jar, 'forge')['zipCrc'])
        bad_metadata = [None, {'jars': []}]
        for old, new in (('io.github.llamalad7', 'other.group'), ('mixinextras-forge', 'geckolib-forge'),
                         ('0.4.1', '0.4.2'), ('[0.4.1,)', '[0.4.1,0.5)'), ('false', 'true'), ('false', '0')):
            bad_metadata.append(json.loads(json.dumps(metadata).replace(old, new)))
        bad_metadata.append({'jars': metadata['jars']*2})
        for meta in bad_metadata:
            with self.subTest(metadata=meta):
                write(meta)
                with self.assertRaisesRegex(RuntimeError, 'nested|[Jj]ar[Jj]ar|MixinExtras'):
                    self.c.vehicle_jar_gate(jar, 'forge')
        for extra in ({'META-INF/jarjar/other.jar': b'other'}, {'META-INF/jarjar/geckolib.jar': b'gecko'},
                      {'META-INF/jarjar/other.JAR': b'other'}, {'com/codex/blacklight/gametest/QA.class': b'qa'},
                      {'software/bernie/geckolib/GeckoLib.class': b'gecko'}):
            with self.subTest(extra=extra):
                write(metadata, extra)
                with self.assertRaisesRegex(RuntimeError, 'nested|QA|Gecko'):
                    self.c.vehicle_jar_gate(jar, 'forge')
        for payload, nested_path in ((b'changed dependency with QA', nested), (dependency, ''),
                                     (dependency, 'META-INF/jarjar/mixinextras-forge0.4.1.jar')):
            with self.subTest(payload=payload, path=nested_path):
                write(metadata, payload=payload, nested_path=nested_path)
                with self.assertRaisesRegex(RuntimeError, 'nested|[Jj]ar[Jj]ar|MixinExtras'):
                    self.c.vehicle_jar_gate(jar, 'forge')
        write(metadata)
        with self.assertRaisesRegex(RuntimeError, 'nested|[Jj]ar[Jj]ar'):
            self.c.vehicle_jar_gate(jar, 'neoforge')

    def test_evidence_gate_refuses_omitted_actual_run_log(self):
        self.assertTrue(hasattr(self.c, 'evidence_gate'), 'Required evidence completeness gate is not implemented')
        game = self.game();garage, _ = self.audit()
        xml = self.file('build/test-results/test/TEST-A.xml', '<testsuite name="A" tests="1"/>')
        entry = {'jar': self.file('build/libs/main.jar', b'jar'),
                 'junit': {'log': self.file('.superpowers/qa/unit.log', 'BUILD SUCCESSFUL'), 'xmlFiles': {xml['path']: xml['sha256']}},
                 'gameTests': [game], 'garageAudit': garage,
                 'vehiclePreview': {'manifest': self.file('art/preview/manifest.json', '{}'), 'capture': self.file('build/mesh.json', '[]')}}
        entry['evidenceFiles'] = self.c.required_evidence(entry)
        self.c.evidence_gate(self.root, entry)
        del entry['evidenceFiles'][game['log']['path']]
        with self.assertRaisesRegex(RuntimeError, 'omits'):self.c.evidence_gate(self.root, entry)
        entry['evidenceFiles'] = self.c.required_evidence(entry)
        private = self.file('.superpowers/secrets/token.json', '{"private":"fixture"}')
        entry['evidenceFiles'][private['path']] = private['sha256']
        with self.assertRaisesRegex(RuntimeError, 'unreviewed'):self.c.evidence_gate(self.root, entry)

    def test_catalogue_requires_actual_read_only_m6_source_and_thirty_png_verification(self):
        self.assertTrue(hasattr(self.c, 'catalogue_gate'), 'Current M6 material verification gate is missing')
        tools = self.root/'tools';tools.mkdir()
        script = tools/'archive_military6_catalog.py'
        script.write_text('import json,sys\nfrom pathlib import Path\n'
                          'if "--verify" not in sys.argv: Path("UNEXPECTED-WRITE").write_text("bad")\n'
                          'print(json.dumps({"status":"PASS","outputWritten":False,"catalogue_integrity":"PASS","source_snapshots":208,"preview_pngs":30,"icon_pngs":13,"active_recipes_per_loader":10,"gameplay_pass_claimed":False,"errors":[]}))\n')
        self.assertEqual(30, self.c.catalogue_gate(self.root, self.root/'forge')['preview_pngs'])
        self.assertFalse((self.root/'UNEXPECTED-WRITE').exists())
        script.write_text('import json\nprint(json.dumps({"catalogue_integrity":"PASS","source_snapshots":208,"preview_pngs":29,"gameplay_pass_claimed":False,"errors":[]}))\n')
        with self.assertRaises(RuntimeError):self.c.catalogue_gate(self.root, self.root/'forge')
        script.write_text('import json\nprint(json.dumps({"status":"PASS","outputWritten":True,"catalogue_integrity":"PASS","source_snapshots":208,"preview_pngs":30,"gameplay_pass_claimed":False,"errors":[]}))\n')
        with self.assertRaises(RuntimeError):self.c.catalogue_gate(self.root, self.root/'forge')


if __name__ == '__main__':unittest.main()
