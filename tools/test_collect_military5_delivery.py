"""Safety checks for the local collector; uses disposable files and Git repositories only."""
import importlib.util
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch
import uuid
from zipfile import ZipFile

SCRIPT = Path(__file__).with_name('collect_military5_delivery.py')

def collector():
    if not SCRIPT.is_file():
        raise AssertionError('military.5 collector and its actual evidence gates are not implemented')
    spec = importlib.util.spec_from_file_location('military5_collector', SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

def run_git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args], stderr=subprocess.STDOUT)

class CollectorSafetyTest(unittest.TestCase):
    def setUp(self):
        # Preserve disposable fixtures under build for diagnostics; never recursively delete user paths.
        self.root = SCRIPT.resolve().parents[1]/'build/military5-collector-tests'/str(uuid.uuid4())
        self.root.mkdir(parents=True)
        self.c = collector()

    def log(self, content):
        path = self.root/'run.log'
        path.write_text(content, encoding='ascii')
        return path

    def repo(self):
        repo=self.root/'repo';repo.mkdir()
        run_git(repo,'init','-q')
        (repo/'README.md').write_text('baseline\n')
        (repo/'docs').mkdir();(repo/'docs/old.md').write_text('old\n')
        run_git(repo,'add','.')
        run_git(repo,'-c','user.name=Collector Fixture','-c','user.email=fixture@example.invalid','commit','-qm','baseline')
        return repo

    def test_a_failed_132_run_is_not_accepted_as_129_passing(self):
        path=self.log('132 GAME TESTS COMPLETE\n3 required tests failed :(\nAll dimensions are saved\nGame test server shutting down\nBUILD SUCCESSFUL\n')
        with self.assertRaises(RuntimeError):self.c.game_test(path)

    def test_old_111_pass_does_not_satisfy_new_132_gate(self):
        path=self.log('111 GAME TESTS COMPLETE\nAll 111 required tests passed\nAll dimensions are saved\nGame test server shutting down\nBUILD SUCCESSFUL\n')
        with self.assertRaises(RuntimeError):self.c.game_test(path)

    def test_old_complete_131_pass_does_not_satisfy_new_132_gate(self):
        path=self.log('131 GAME TESTS COMPLETE\nAll 131 required tests passed\nStopping server\nAll dimensions are saved\nGame test server shutting down\nBUILD SUCCESSFUL\n')
        with self.assertRaises(RuntimeError):self.c.game_test(path)

    def test_game_pass_needs_the_normal_saving_shutdown(self):
        path=self.log('132 GAME TESTS COMPLETE\nAll 132 required tests passed\nBUILD SUCCESSFUL\n')
        with self.assertRaises(RuntimeError):self.c.game_test(path)

    def test_completed_game_run_keeps_its_exact_log_hash(self):
        path=self.log('132 GAME TESTS COMPLETE\nAll 132 required tests passed\nStopping server\nAll dimensions are saved\nGame test server shutting down\nBUILD SUCCESSFUL\n')
        value=self.c.game_test(path)
        self.assertEqual(132,value['requiredPassed']);self.assertEqual(self.c.sha(path),value['sha256'])

    def test_junit_rejects_skipped_tests_and_wrong_final_count(self):
        directory=self.root/'build/test-results/test';directory.mkdir(parents=True)
        path=directory/'TEST-fixture.xml'
        path.write_text('<testsuite tests="2" failures="0" errors="0" skipped="1"/>')
        with self.assertRaises(RuntimeError):self.c.junit_result(self.root,2)
        path.write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"/>')
        with self.assertRaises(RuntimeError):self.c.junit_result(self.root,3)
        self.assertEqual(2,self.c.junit_result(self.root,2)[0]['tests'])

    def test_file_manifests_reject_escape_and_real_tampering(self):
        path=self.root/'asset.bin';path.write_bytes(b'original')
        self.c.assert_files_unchanged(self.root,{'asset.bin':self.c.sha(path)})
        with self.assertRaises(RuntimeError):self.c.assert_files_unchanged(self.root,{'../secret':self.c.sha(path)})
        digest=self.c.sha(path);path.write_bytes(b'changed')
        with self.assertRaises(RuntimeError):self.c.assert_files_unchanged(self.root,{'asset.bin':digest})

    def test_overlay_contains_staged_unstaged_untracked_and_deleted(self):
        repo=self.repo();(repo/'src/main').mkdir(parents=True)
        (repo/'src/main/A.java').write_text('class A {}\n');run_git(repo,'add','src/main/A.java')
        (repo/'README.md').write_text('working change\n')
        (repo/'tools').mkdir();(repo/'tools/new.py').write_text('print(1)\n')
        (repo/'docs/old.md').unlink()
        output=self.root/'overlay.zip';value=self.c.source_overlay(repo,output)
        with ZipFile(output) as archive:
            self.assertEqual({'README.md','src/main/A.java','tools/new.py','PATCH-BASE.json'},set(archive.namelist()))
            self.assertEqual((repo/'README.md').read_bytes(),archive.read('README.md'))
        self.assertEqual(['docs/old.md'],value['deleted'])
        self.assertEqual({'README.md','src/main/A.java','tools/new.py'},set(value['files']))

    def test_native_animation_is_verified_against_actual_head_and_jar(self):
        repo=self.repo();path=repo/'src/main/resources/assets/blacklight/native_animations/idle.json';path.parent.mkdir(parents=True)
        path.write_bytes(b'{"authored":true}')
        (repo/'art/motion-profile').mkdir(parents=True);(repo/'art/motion-profile/idle.json').write_bytes(b'{"motion":1}')
        (repo/'art/motion-profile-manifest.json').write_text('{}')
        run_git(repo,'add','.');run_git(repo,'-c','user.name=Collector Fixture','-c','user.email=fixture@example.invalid','commit','-qm','author motions')
        jar=self.root/'mod.jar'
        with ZipFile(jar,'w') as archive:archive.writestr('assets/blacklight/native_animations/idle.json',path.read_bytes())
        report=self.c.protected_upstream_resources(repo,jar,'HEAD')
        self.assertEqual(1,report['nativeAnimationFiles'])
        path.write_bytes(b'{"authored":false}')
        with self.assertRaises(RuntimeError):self.c.protected_upstream_resources(repo,jar,'HEAD')

    def test_output_validation_never_creates_a_staging_directory(self):
        repo=self.root/'repo';repo.mkdir();output=self.root/'delivery'
        stage,zip_path,checksum=self.c.new_destination(self.root,{'neoforge':repo},output)
        self.assertFalse(any(path.exists() for path in (output,stage,zip_path,checksum)))
        output.mkdir()
        with self.assertRaises(RuntimeError):self.c.new_destination(self.root,{'neoforge':repo},output)
        with self.assertRaises(RuntimeError):self.c.new_destination(self.root,{'neoforge':repo},repo/'inside')

    def test_upgrade_gate_requires_two_processes_finite_snapshots_and_unchanged_source(self):
        source=self.root/'old-qa';source.mkdir();(source/'world').mkdir();(source/'world/level.dat').write_bytes(b'unchanged old QA bytes')
        ids=[str(uuid.uuid4()),str(uuid.uuid4())]
        original={**{name:True for name in self.c.NATIVE_FLAGS},'status':'PASS','auditRevision':4,'dimension':'blacklight:city',
                  'generator':'com.codex.blacklight.city.CityChunkGenerator',
                  'updatedUtc':'2026-10-24T01:00:00Z','guards':[{'uuid':v} for v in ids],'baseId':'old-base','unit':str(uuid.uuid4()),
                  'manualCommander':ids[0],'marker':{},'ammunitionChest':{},'issuedSlots':2,'savedRifleRounds':7}
        (source/'city-military-audit.json').write_text(json.dumps(original))
        run=self.root/'.superpowers/test-upgrade';run.mkdir(parents=True)
        baseline={'source':str(source),'target':str(run),'sourceResult':original,
                  'sourceFiles':{p.relative_to(source).as_posix():self.c.sha(p) for p in source.rglob('*') if p.is_file()}}
        baseline_path=run/'MILITARY4-BASELINE.json';baseline_path.write_text(json.dumps(baseline))
        native={**original,'updatedUtc':'2026-10-25T01:00:00Z'};(run/'city-military-audit.json').write_text(json.dumps(native))
        external={'uuid':str(uuid.uuid4()),'magazine':5,'reserve':11,'equipped':True,'homeBase':'old-base',
                  'homeDimension':'blacklight:city','originBase':''}
        field={'uuid':str(uuid.uuid4()),'magazine':3,'reserve':9,'equipped':False,'homeBase':'','originBase':'',
               'fieldAnchorPresent':True,'fieldDimension':'blacklight:city'}
        home={**{name:True for name in self.c.HOME_FLAGS},'schema':1,'status':'PASS','dimension':'blacklight:city','baseId':'old-base',
              'sourceRunDirectory':str(run),'military4BaselineSha256':self.c.sha(baseline_path),
              'firstProcessId':str(uuid.uuid4()),'restartProcessId':str(uuid.uuid4()),'updatedUtc':'2026-10-25T01:00:05Z',
              'nativeSlots':ids,'nativeSavedSnapshots':[{'uuid':v} for v in ids],
              'externalUuid':external['uuid'],'fieldUuid':field['uuid'],'externalSavedSnapshot':external,'fieldSavedSnapshot':field}
        home_path=run/'military-homes-audit.json';home_path.write_text(json.dumps(home))
        qa=self.root/'.superpowers/military5-qa';qa.mkdir()
        for phase,status in [(1,'PHASE1_SAVED'),(2,'PASS')]:
            (qa/f'test-upgrade-phase{phase}.log').write_text(self.c.VERSION+'\nCITY_MILITARY_SERVER_AUDIT PASS\n'
                +'CITY_MILITARY_HOMES_AUDIT '+status+'\nStopping server\nAll dimensions are saved\nBUILD SUCCESSFUL\n')
        def verify():return self.c.upgrade_audit(self.root,'.superpowers/test-upgrade','blacklight:city','test-upgrade',source)
        self.assertEqual('PASS',verify()['homes']['status'])
        home['status']='PHASE1_SAVED';home_path.write_text(json.dumps(home))
        with self.assertRaises(RuntimeError):verify()
        home['status']='PASS';home['restartProcessId']=home['firstProcessId'];home_path.write_text(json.dumps(home))
        with self.assertRaises(RuntimeError):verify()
        home['restartProcessId']=str(uuid.uuid4());home['externalSavedSnapshot']['reserve']=90;home_path.write_text(json.dumps(home))
        with self.assertRaises(RuntimeError):verify()
        home['externalSavedSnapshot']['reserve']=11;home_path.write_text(json.dumps(home));(source/'world/level.dat').write_bytes(b'tampered')
        with self.assertRaises(RuntimeError):verify()

    def test_native_animation_in_source_cannot_hide_a_stale_jar(self):
        repo=self.repo();path=repo/'src/main/resources/assets/blacklight/native_animations/idle.json';path.parent.mkdir(parents=True)
        path.write_bytes(b'{"authored":true}')
        (repo/'art').mkdir();(repo/'art/motion-profile-manifest.json').write_text('{}')
        run_git(repo,'add','.');run_git(repo,'-c','user.name=Collector Fixture','-c','user.email=fixture@example.invalid','commit','-qm','author motion')
        jar=self.root/'stale.jar'
        with ZipFile(jar,'w') as archive:archive.writestr('assets/blacklight/native_animations/idle.json',b'stale')
        with self.assertRaises(RuntimeError):self.c.protected_upstream_resources(repo,jar,'HEAD')

    def test_check_only_does_not_create_outputs_even_after_successful_preflight(self):
        args=['collect_military5_delivery.py','--workspace',str(self.root),'--check-only','--neoforge-junit','2','--forge-junit','2']
        output=self.root/'military5-reference-homes-preview-20261025'
        # Substitute only the expensive external audit/build gates. The real CLI and destination branch execute.
        with patch('sys.argv',args),patch.object(self.c,'check_old_releases'),patch.object(self.c,'preflight',return_value=({},{})):
            stream=io.StringIO()
            with redirect_stdout(stream):self.c.collect()
        self.assertFalse(json.loads(stream.getvalue())['outputWritten'])
        self.assertFalse(any(p.exists() for p in [output,output.with_name(output.name+'.staging'),Path(str(output)+'.zip'),Path(str(output)+'.zip.sha256')]))

if __name__=='__main__':unittest.main()
