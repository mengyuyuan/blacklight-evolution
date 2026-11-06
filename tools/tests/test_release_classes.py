"""Compile a real multi-type Java source and verify release selection/linkage."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release_classes import production_classes, verify_internal_closure

class ReleaseClassInventoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        javac = Path(os.environ['JAVA_HOME']) / 'bin/javac.exe' if os.name == 'nt' and os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac or not Path(javac).is_file():
            raise RuntimeError('A JDK is required for the real release-class regression')
        cls.parent = Path(tempfile.gettempdir()).resolve()
        cls.temporary = tempfile.TemporaryDirectory(prefix='blacklight-release-inventory-', dir=cls.parent)
        cls.root = Path(cls.temporary.name).resolve()
        assert cls.root.parent == cls.parent and cls.root.name.startswith('blacklight-release-inventory-')
        cls.sources = cls.root / 'main'
        package = cls.sources / 'com/codex/blacklight/inventory'
        package.mkdir(parents=True)
        main = package / 'Entry.java'
        main.write_text('''package com.codex.blacklight.inventory;
public class Entry {
    State[] values;
    public State first() { return values[0]; }
    static class Marker {}
}
record State(int level) {}
''', encoding='utf8')
        probe = cls.root / 'qa/com/codex/blacklight/inventory/Probe.java'
        probe.parent.mkdir(parents=True)
        probe.write_text('package com.codex.blacklight.inventory; public class Probe {}', encoding='utf8')
        classes = cls.root / 'classes'
        subprocess.run([str(javac), '-encoding', 'UTF-8', '--release', '17', '-d', str(classes), str(main), str(probe)], check=True, capture_output=True)
        cls.entries = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}

    @classmethod
    def tearDownClass(cls):
        # Verify the absolute recursive-cleanup target is our own temporary directory.
        assert cls.root.parent == cls.parent and cls.root.name.startswith('blacklight-release-inventory-')
        cls.temporary.cleanup()

    def test_same_source_sibling_record_and_nested_type_are_preserved_while_qa_is_excluded(self):
        selected = production_classes(self.entries, self.sources)
        self.assertEqual(set(selected), {
            'com/codex/blacklight/inventory/Entry.class',
            'com/codex/blacklight/inventory/Entry$Marker.class',
            'com/codex/blacklight/inventory/State.class',
        })
        verify_internal_closure(selected)

    def test_missing_package_private_record_is_rejected_before_shipping(self):
        selected = production_classes(self.entries, self.sources)
        del selected['com/codex/blacklight/inventory/State.class']
        with self.assertRaisesRegex(ValueError, 'inventory/State'):
            verify_internal_closure(selected)

if __name__ == '__main__':
    unittest.main()
