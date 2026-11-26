#!/usr/bin/env python3
"""Unit tests for tools/check_doc_links.py."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location(
    'check_doc_links', Path(__file__).resolve().parents[1] / 'check_doc_links.py')
links = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(links)


class DocLinkCheckTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def write(self, rel, text='x'):
        path = self.root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
        return path

    # --- baseline behaviour ------------------------------------------------
    def test_existing_relative_link_passes(self):
        self.write('docs/target.md')
        self.write('README.md', '[a](docs/target.md)\n')
        self.assertEqual(links.check(self.root), [])

    def test_missing_relative_link_is_reported(self):
        self.write('README.md', '[a](docs/missing.md)\n')
        findings = links.check(self.root)
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0][0], 'README.md')
        self.assertEqual(findings[0][1], 1)
        self.assertIn('missing', findings[0][3])

    def test_parent_relative_link_resolves(self):
        self.write('docs/other/target.md')
        self.write('docs/spec/README.md', '[a](../other/target.md)\n')
        self.assertEqual(links.check(self.root), [])

    def test_directory_link_passes(self):
        self.write('docs/inside.md')
        self.write('README.md', '[d](docs)\n')
        self.assertEqual(links.check(self.root), [])

    def test_anchor_and_query_are_stripped(self):
        self.write('docs/target.md')
        self.write('README.md', '[a](docs/target.md#s)\n[b](docs/target.md?raw=1)\n')
        self.assertEqual(links.check(self.root), [])

    def test_case_mismatch_is_reported(self):
        self.write('docs/Target.md')
        self.write('README.md', '[a](docs/target.md)\n')
        findings = links.check(self.root)
        self.assertEqual(len(findings), 1)
        self.assertIn('case mismatch', findings[0][3])

    def test_escaping_root_is_reported(self):
        self.write('README.md', '[a](../../etc/passwd)\n')
        findings = links.check(self.root)
        self.assertEqual(len(findings), 1)
        self.assertIn('escapes', findings[0][3])

    def test_percent_encoded_and_chinese_paths(self):
        self.write('docs/制作资料/说明.md')
        self.write('README.md',
                   '[a](docs/制作资料/说明.md)\n'
                   '[b](docs/%E5%88%B6%E4%BD%9C%E8%B5%84%E6%96%99/%E8%AF%B4%E6%98%8E.md)\n')
        self.assertEqual(links.check(self.root), [])

    def test_image_link_pointing_at_file_passes(self):
        self.write('docs/img.png')
        self.write('docs/a.md', '![i](img.png)\n')
        self.assertEqual(links.check(self.root), [])

    def test_build_and_run_dirs_are_skipped(self):
        self.write('build/tmp.md', '[a](missing.md)\n')
        self.write('run/tmp.md', '[a](missing.md)\n')
        self.assertEqual(links.check(self.root), [])

    def test_exit_code_reflects_findings(self):
        self.write('README.md', '[a](missing.md)\n')
        self.assertEqual(links.main(['--root', str(self.root), '--summary']), 1)
        self.write('missing.md')
        self.assertEqual(links.main(['--root', str(self.root), '--summary']), 0)

    # --- regressions reported on PR #111 ----------------------------------
    def test_angle_bracket_destination_resolves(self):
        """[a](<docs/foo.md>) must not keep the angle brackets in the target."""
        self.write('docs/foo.md')
        self.write('README.md', '[a](<docs/foo.md>)\n')
        self.assertEqual(links.check(self.root), [])

    def test_angle_bracket_destination_missing_is_reported_cleanly(self):
        self.write('README.md', '[a](<docs/nope.md>)\n')
        findings = links.check(self.root)
        self.assertEqual(len(findings), 1)
        self.assertNotIn('<', findings[0][2])

    def test_parentheses_in_destination_resolve(self):
        """[a](docs/foo(bar).md) must not be truncated at the first ') '."""
        self.write('docs/foo(bar).md')
        self.write('README.md', '[a](docs/foo(bar).md)\n')
        self.assertEqual(links.check(self.root), [])

    def test_nested_parentheses_in_destination(self):
        self.write('docs/a(b(c)).md')
        self.write('README.md', '[x](docs/a(b(c)).md)\n')
        self.assertEqual(links.check(self.root), [])

    def test_title_after_destination_is_ignored(self):
        self.write('docs/foo.md')
        self.write('README.md',
                   '[a](docs/foo.md "title")\n[b](docs/foo.md \'title\')\n')
        self.assertEqual(links.check(self.root), [])

    def test_inline_code_links_are_not_checked(self):
        self.write('README.md', 'Use `[x](nonexistent.md)` like this.\n')
        self.assertEqual(links.check(self.root), [])

    def test_fenced_code_links_are_not_checked(self):
        self.write('README.md', '```\n[y](also-missing.md)\n```\n')
        self.assertEqual(links.check(self.root), [])

    def test_tilde_fence_links_are_not_checked(self):
        self.write('README.md', '~~~markdown\n[z](nope.md)\n~~~\n')
        self.assertEqual(links.check(self.root), [])

    def test_real_link_after_code_block_is_still_checked(self):
        self.write('README.md', '```\n[ok](skipped.md)\n```\n\n[real](missing.md)\n')
        findings = links.check(self.root)
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0][1], 5)

    def test_line_numbers_survive_code_masking(self):
        self.write('README.md', '`[a](x.md)`\n`[b](y.md)`\n[c](missing.md)\n')
        findings = links.check(self.root)
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0][1], 3)

    def test_windows_local_path_is_skipped_by_default(self):
        """E:/... looks like a URI scheme but is a local absolute path."""
        self.write('README.md', '[p](E:/research/notes.md)\n')
        self.assertEqual(links.check(self.root), [])
        self.assertEqual(links.classify('E:/research/notes.md'), 'local-abs')

    def test_windows_local_path_reported_in_strict_mode(self):
        self.write('README.md', '[p](E:/research/notes.md)\n')
        findings = links.check(self.root, strict_local=True)
        self.assertEqual(len(findings), 1)
        self.assertIn('local absolute path', findings[0][3])

    def test_external_urls_and_mailto_are_skipped(self):
        self.write('README.md', '[h](https://example.invalid/x)\n[m](mailto:a@b)\n')
        self.assertEqual(links.check(self.root), [])


if __name__ == '__main__':
    unittest.main()
