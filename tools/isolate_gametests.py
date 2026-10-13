"""Prepare selected existing GameTests without editing production or test assertions.

Run with method names, then use the printed Gradle init script with the usual test flags.
All helper classes remain compiled; only unselected @GameTest annotations are removed
from temporary copies in build/. A separate server run avoids neighbouring fixtures.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ('standaloneTest', 'traversalTest', 'hiveTest', 'releaseAuditTest')
ANNOTATION = re.compile(r'(@GameTest\s*\([^)]*\))(?=\s*public\s+static\s+void\s+(\w+)\s*\()')


def main():
    selected = {name.lower() for arg in sys.argv[1:] for name in arg.split(',') if name}
    if not selected:
        raise SystemExit('Pass one or more GameTest method names')
    output = ROOT / 'build' / 'isolated-gametests'
    discovered = set()
    copies = []
    for source in SOURCES:
        source_root = ROOT / 'src' / source / 'java'
        for path in source_root.rglob('*.java'):
            text = path.read_text(encoding='utf-8-sig')

            def choose(match):
                name = match[2].lower()
                discovered.add(name)
                return match[1] if name in selected else ''

            text = ANNOTATION.sub(choose, text)
            copies.append((output / source / path.relative_to(source_root), text))
    if selected - discovered:
        raise SystemExit('Unknown tests: ' + ', '.join(sorted(selected - discovered)))
    for path, text in copies:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
    # Restrict the source roots explicitly so a removed/renamed source cannot leave
    # a stale generated Java file registered as an extra test on the next run.
    roots = ','.join("'build/isolated-gametests/" + name + "'" for name in SOURCES)
    expected = ','.join("'src/" + name + "/java'" for name in SOURCES)
    paths = ','.join("file('" + path.relative_to(ROOT).as_posix() + "').canonicalFile" for path, _ in copies)
    script = ROOT / 'build' / 'isolate-gametests.init.gradle'
    script.write_text('allprojects { afterEvaluate {\n'
                      '    def excluded = [' + expected + '].collect { file(it).canonicalFile }\n'
                      '    def generated = [' + roots + '].collect { file(it) }\n'
                      '    def allowed = [' + paths + '] as Set\n'
                      '    sourceSets.main.java.setSrcDirs(sourceSets.main.java.srcDirs.findAll { !excluded.contains(it.canonicalFile) } + generated)\n'
                      "    def folder = file('build/isolated-gametests').canonicalPath + File.separator\n"
                      '    sourceSets.main.java.exclude { e -> !e.directory && e.file.canonicalPath.startsWith(folder) && !allowed.contains(e.file.canonicalFile) }\n'
                      '} }\n', encoding='utf-8')
    print('Selected:', ', '.join(sorted(selected)))
    print('Gradle argument: -I build/isolate-gametests.init.gradle')


if __name__ == '__main__':
    main()
