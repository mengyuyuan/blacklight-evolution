"""Select military GameTests in build-only copies; preserve all original assertions and production files."""
from pathlib import Path
import hashlib
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ('blackwatchTest', 'cityMilitaryTest', 'officersTest', 'garrisonTest', 'terrainTest', 'homeTest', 'identityMilitaryTest')
ANNOTATION = re.compile(r'(@GameTest\s*\([^)]*\))(?=\s*public\s+static\s+void\s+(\w+)\s*\()')

def main():
    selected = {name.lower() for arg in sys.argv[1:] for name in arg.split(',') if name}
    if not selected: raise SystemExit('Pass one or more exact GameTest method names')
    output = ROOT/'build/isolated-military5'
    found = set()
    copies = []
    source_hashes = {}
    for source in SOURCES:
        source_root = ROOT/'src'/source/'java'
        for path in sorted(source_root.rglob('*.java')):
            original = path.read_bytes()
            source_hashes[path.relative_to(ROOT).as_posix()] = hashlib.sha256(original).hexdigest()
            def choose(match):
                name = match[2].lower()
                found.add(name)
                return match[1] if name in selected else ''
            text = ANNOTATION.sub(choose, original.decode('utf-8-sig'))
            copies.append((output/source/path.relative_to(source_root), text))
    if selected-found: raise SystemExit('Unknown tests: '+', '.join(sorted(selected-found)))
    for path, text in copies:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
    allowed = ','.join("new File('"+p.as_posix()+"').canonicalFile" for p, _ in copies)
    roots = ','.join("new File('"+(output/name).as_posix()+"')" for name in SOURCES)
    folder = output.as_posix()
    init = ROOT/'build/isolate-military5.init.gradle'
    init.write_text("""allprojects { p -> p.afterEvaluate {
    p.sourceSets.main.java.setSrcDirs(p.sourceSets.main.java.srcDirs + [ROOTS])
    def allowed = [ALLOWED] as Set
    def folder = new File('FOLDER').canonicalPath + File.separator
    p.sourceSets.main.java.exclude { e -> !e.directory && e.file.canonicalPath.startsWith(folder) && !allowed.contains(e.file.canonicalFile) }
    p.sourceSets.main.resources.srcDir('src/blackwatchTest/resources')
    p.sourceSets.main.resources.srcDir('src/cityMilitaryTest/resources')
    if (p.extensions.findByName('neoForge') != null) {
        def stageIsolatedMilitary5 = p.tasks.register('stageIsolatedMilitary5', Sync) {
            dependsOn p.tasks.named('classes')
            from p.sourceSets.main.output
            into p.layout.buildDirectory.dir('isolated-military5-mod')
        }
        p.tasks.named('runGameTestServer').configure {
            dependsOn stageIsolatedMilitary5
            doFirst { environment 'MOD_CLASSES', 'blacklight%%' + p.layout.buildDirectory.dir('isolated-military5-mod').get().asFile.absolutePath }
        }
    }
} }
allprojects { p ->
    p.tasks.withType(ProcessResources).configureEach { filteringCharset = 'UTF-8' }
    p.tasks.matching { it.name == 'generateModMetadata' }.configureEach { outputs.upToDateWhen { false } }
}
""".replace('ROOTS', roots).replace('ALLOWED', allowed).replace('FOLDER', folder), encoding='utf-8')
    for name, digest in source_hashes.items():
        assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest() == digest, 'Original QA source changed during isolation'
    record = {'selected': sorted(selected), 'source_sha256': source_hashes, 'assertionsChanged': False}
    (output/'selection.json').write_text(json.dumps(record, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'selected': sorted(selected), 'init': str(init)}, ensure_ascii=False))

if __name__ == '__main__': main()
