"""Small M5 catalogue checks: no Gradle, game, full renderer or final freeze."""
from pathlib import Path
import hashlib
import importlib.util
import json
import shutil
import tempfile
import unittest

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('archive_military_catalog',HERE/'archive_military_catalog.py')
archive=importlib.util.module_from_spec(spec);spec.loader.exec_module(archive)
ROOT=HERE.parent
REVISION=ROOT/'docs/制作资料/参考模型/生产预览-military5-revision4'
CANONICAL=Path('docs/制作资料/参考模型/生产预览-military5')

class Military5ArchiveTests(unittest.TestCase):
    def setUp(self):
        scratch=ROOT/'build/military5-catalog-checks';scratch.mkdir(parents=True,exist_ok=True)
        self.temp=tempfile.TemporaryDirectory(prefix='catalog-',dir=scratch);self.addCleanup(self.temp.cleanup)
        self.project=Path(self.temp.name)/'project';self.output=Path(self.temp.name)/'output'
        self.fixture=self.project/CANONICAL;shutil.copytree(REVISION,self.fixture)
        self.capture=json.loads((self.fixture/'manifest.json').read_text(encoding='utf8'))
        for relative in self.capture['sources']:
            target=self.project/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/relative,target)
    def reader(self):
        reader=getattr(archive,'read_reference_capture',None)
        self.assertTrue(callable(reader),'M5 catalogue must validate the real capture manifest instead of parsing the old straight-arm geometry')
        return reader
    def test_real_capture_has_ten_registered_cards_and_twenty_outfits(self):
        capture=self.reader()(self.project)
        self.assertEqual(10,len(capture['manifest']['catalog']))
        self.assertEqual(20,capture['manifest']['catalog_poses'])
        heavy=capture['manifest']['catalog']['blacklight:blackwatch_heavy']
        self.assertTrue(heavy['heavy']);self.assertEqual('military_machine_gun',heavy['weapon'])
    def test_changed_runtime_input_cannot_reuse_a_capture(self):
        source=self.project/'src/main/java/com/codex/blacklight/client/model/BlackwatchRigPose.java'
        source.write_text(source.read_text(encoding='utf8')+'\n// changed after capture\n',encoding='utf8')
        with self.assertRaisesRegex(ValueError,'source|input'):
            self.reader()(self.project)
    def test_tampered_image_cannot_reuse_a_capture(self):
        image=self.fixture/self.capture['catalog']['blacklight:military_heavy']['card'];image.write_bytes(image.read_bytes()+b'tampered')
        with self.assertRaisesRegex(ValueError,'image'):
            self.reader()(self.project)
    def test_missing_rig_input_proof_is_not_an_actual_capture(self):
        self.capture['sources'].pop('src/main/java/com/codex/blacklight/client/model/BlackwatchRigPose.java')
        (self.fixture/'manifest.json').write_text(json.dumps(self.capture),encoding='utf8')
        with self.assertRaisesRegex(ValueError,'source|input'):
            self.reader()(self.project)
    def test_current_cards_copy_real_capture_and_preserve_legacy_gallery(self):
        method=getattr(archive.Archive,'soldier_previews',None)
        self.assertTrue(callable(method),'Soldier cards need a dedicated real-capture path that never invokes preview_soldiers')
        legacy=self.output/'assets/兵种/military_soldier.png';legacy.parent.mkdir(parents=True,exist_ok=True);legacy.write_bytes(b'legacy historical image')
        old_hash=hashlib.sha256(legacy.read_bytes()).hexdigest()
        rows=[{'id':entity,'preview':'assets/兵种/'+entity.split(':')[1]+'.png'} for entity in self.capture['catalog']]
        item=archive.Archive(self.output,ROOT.parent/'forge-military5')
        from unittest.mock import patch
        with patch.object(archive,'ROOT',self.project):item.soldier_previews(rows)
        self.assertEqual(old_hash,hashlib.sha256(legacy.read_bytes()).hexdigest())
        for row in rows:
            original=self.fixture/self.capture['catalog'][row['id']]['card']
            self.assertEqual(original.read_bytes(),(self.output/row['preview']).read_bytes())
            self.assertTrue((self.output/row['preview_equipped']).is_file());self.assertTrue((self.output/row['preview_uniform']).is_file())
        self.assertNotEqual('assets/兵种/military_soldier.png',rows[0]['preview'])
    def test_runtime_index_separates_original_and_live_derivatives(self):
        method=getattr(archive.Archive,'reference_runtime_data',None)
        self.assertTrue(callable(method),'The source index must describe the actual production profile, rig, hands and homes')
        item=archive.Archive(self.output,ROOT.parent/'forge-military5');data=item.reference_runtime_data()
        for platform in ['neoforge','forge']:
            record=data['platforms'][platform]
            self.assertFalse(record['original_mesh_imported']);self.assertEqual(7,record['preserved_animation_count']);self.assertEqual(6,len(record['sampled_animations']))
            self.assertFalse(record['dodge_runtime']);self.assertEqual('blacklight_authored',record['reload_source']);self.assertEqual(2174,record['keyframes'])
            self.assertTrue(any(s['path'].endswith('BlackwatchRigPose.java') and s['platform']==platform for s in item.sources))
            self.assertTrue(any(s['path'].endswith('MilitaryHomeSystem.java') and s['platform']==platform for s in item.sources))

if __name__=='__main__':unittest.main()
