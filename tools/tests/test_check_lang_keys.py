#!/usr/bin/env python3
"""Unit tests for tools/check_lang_keys.py."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location(
    'check_lang_keys', Path(__file__).resolve().parents[1] / 'check_lang_keys.py')
lang = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(lang)


class LangKeyCheckTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.dir = Path(self.temp.name)

    def write(self, name, data):
        path = self.dir / name
        path.write_text(json.dumps(data, ensure_ascii=False), encoding='utf-8')
        return path

    # --- key parity --------------------------------------------------------
    def test_parity_passes(self):
        self.write('en_us.json', {'a': 'Target', 'b': 'Hello %s'})
        self.write('zh_cn.json', {'a': '目标', 'b': '你好 %s'})
        self.assertEqual(lang.main(['--lang-dir', str(self.dir)]), 0)

    def test_missing_key_is_reported(self):
        self.write('en_us.json', {'a': 'A', 'b': 'B'})
        self.write('zh_cn.json', {'a': 'A'})
        self.assertEqual(lang.main(['--lang-dir', str(self.dir)]), 1)

    def test_extra_key_is_reported(self):
        self.write('en_us.json', {'a': 'A'})
        self.write('zh_cn.json', {'a': 'A', 'b': 'B'})
        findings = lang.compare({'a': 'A'}, {'a': 'A', 'b': 'B'}, 'en_us.json', 'zh_cn.json')
        self.assertEqual(len(findings), 1)

    # --- Java format syntax ------------------------------------------------
    def test_escaped_percent_is_not_a_placeholder(self):
        """%%s is literal text; it must not compare equal to %s."""
        findings = lang.compare({'k': '%%s'}, {'k': '%s'}, 'en', 'zh')
        self.assertEqual(len(findings), 1)
        self.assertIn('format arguments differ', findings[0])

    def test_escaped_percent_on_both_sides_is_clean(self):
        findings = lang.compare({'k': '100%% done'}, {'k': '已完成 100%%'}, 'en', 'zh')
        self.assertEqual(findings, [])

    def test_conversion_order_matters(self):
        """%s %d and %d %s consume different Java types in that order."""
        findings = lang.compare({'k': '%s %d'}, {'k': '%d %s'}, 'en', 'zh')
        self.assertEqual(len(findings), 1)
        self.assertIn('format arguments differ', findings[0])

    def test_implicit_and_explicit_single_argument_are_equivalent(self):
        """%s and %1$s both mean argument 1 as a string."""
        findings = lang.compare({'k': '%s'}, {'k': '%1$s'}, 'en', 'zh')
        self.assertEqual(len(findings), 1)
        self.assertIn('equivalent but differently spelled', findings[0])

    def test_explicit_indices_in_same_order_are_equivalent(self):
        findings = lang.compare({'k': '%s %s'}, {'k': '%1$s %2$s'}, 'en', 'zh')
        self.assertEqual(len(findings), 1)
        self.assertIn('equivalent but differently spelled', findings[0])

    def test_explicit_index_reorder_is_a_real_difference(self):
        """%1$s %2$s vs %2$s %1$s genuinely reverses the arguments."""
        findings = lang.compare({'k': '%1$s %2$s'}, {'k': '%2$s %1$s'}, 'en', 'zh')
        self.assertEqual(len(findings), 1)
        self.assertIn('format arguments differ', findings[0])

    def test_mixed_implicit_and_explicit_is_reported(self):
        """Java rejects mixing %s with %1$s in one string."""
        findings = lang.compare({'k': '%s %1$s'}, {'k': '%s %1$s'}, 'en', 'zh')
        self.assertTrue(any('mixes implicit' in f for f in findings))

    def test_width_precision_and_flags_are_tolerated(self):
        self.write('en_us.json', {'a': '%.2f / %5d / %-3s'})
        self.write('zh_cn.json', {'a': '%.2f / %5d / %-3s'})
        self.assertEqual(lang.main(['--lang-dir', str(self.dir)]), 0)

    def test_same_widths_with_different_order_differ(self):
        findings = lang.compare({'k': '%s %d'}, {'k': '%2$d %1$s'}, 'en', 'zh')
        self.assertTrue(any('differ' in f for f in findings))

    def test_percent_followed_by_a_letter_is_prose_by_default(self):
        """Java reads "50% off" as a space-flagged %o; prose mode does not."""
        findings = lang.compare({'k': '50% off'}, {'k': '五折'}, 'en', 'zh')
        self.assertEqual(findings, [])

    def test_prose_percentage_with_space_flag_is_ignored(self):
        """Real strings in this repo: "80% damage", "47.5% physical resistance"."""
        findings = lang.compare(
            {'a': '80% damage reduction', 'b': '47.5% physical resistance'},
            {'a': '减伤 80%', 'b': '物理减抗 47.5%'}, 'en', 'zh')
        self.assertEqual(findings, [])

    def test_java_semantics_mode_reports_prose_percentages(self):
        """--java-semantics matches java.util.Formatter exactly."""
        findings = lang.compare({'k': '50% off'}, {'k': '五折'}, 'en', 'zh',
                                java_semantics=True)
        self.assertEqual(len(findings), 1)
        self.assertIn('%o', findings[0])

    def test_qualified_spec_counts_even_outside_the_short_list(self):
        """An explicit index or width makes it a real spec, whatever the letter."""
        findings = lang.compare({'k': '%1$o'}, {'k': '%1$o'}, 'en', 'zh')
        self.assertEqual(findings, [])

    def test_trailing_lone_percent_consumes_nothing(self):
        findings = lang.compare({'k': '100%'}, {'k': '100%'}, 'en', 'zh')
        self.assertEqual(findings, [])

    def test_percent_without_conversion_is_left_alone(self):
        findings = lang.compare({'k': 'rate 5% '}, {'k': '率 5% '}, 'en', 'zh')
        self.assertEqual(findings, [])

    def test_brace_placeholders_are_compared(self):
        findings = lang.compare({'k': '{0} of {1}'}, {'k': '{0}'}, 'en', 'zh')
        self.assertTrue(any('{n} placeholders differ' in f for f in findings))

    def test_brace_and_percent_families_are_independent(self):
        self.write('en_us.json', {'a': '{0}'})
        self.write('zh_cn.json', {'a': '{0}'})
        self.assertEqual(lang.main(['--lang-dir', str(self.dir)]), 0)

    # --- untranslated source text -----------------------------------------
    def test_untranslated_source_text_is_reported(self):
        findings = lang.untranslated({'a': '至尊猎杀者 Supreme Hunter'})
        self.assertEqual(len(findings), 1)

    def test_translated_reference_passes(self):
        self.assertEqual(lang.untranslated({'a': 'Supreme Hunter'}), [])

    # --- input validation --------------------------------------------------
    def test_missing_file_is_usage_error(self):
        self.write('en_us.json', {'a': 'A'})
        self.assertEqual(lang.main(['--lang-dir', str(self.dir)]), 2)

    def test_invalid_json_is_usage_error(self):
        (self.dir / 'en_us.json').write_text('{', encoding='utf-8')
        self.write('zh_cn.json', {'a': 'A'})
        self.assertEqual(lang.main(['--lang-dir', str(self.dir)]), 2)

    def test_non_object_json_is_usage_error(self):
        (self.dir / 'en_us.json').write_text('[]', encoding='utf-8')
        self.write('zh_cn.json', {})
        self.assertEqual(lang.main(['--lang-dir', str(self.dir)]), 2)

    def test_null_value_is_usage_error(self):
        """{'k': null} used to raise an uncaught TypeError."""
        self.write('en_us.json', {'k': None})
        self.write('zh_cn.json', {'k': 'text'})
        self.assertEqual(lang.main(['--lang-dir', str(self.dir)]), 2)

    def test_integer_value_is_usage_error(self):
        self.write('en_us.json', {'k': 123})
        self.write('zh_cn.json', {'k': 'text'})
        self.assertEqual(lang.main(['--lang-dir', str(self.dir)]), 2)

    def test_object_value_is_usage_error(self):
        self.write('en_us.json', {'k': {'n': 1}})
        self.write('zh_cn.json', {'k': 'text'})
        self.assertEqual(lang.main(['--lang-dir', str(self.dir)]), 2)

    def test_error_names_the_file_and_key(self):
        self.write('en_us.json', {'some.key': None})
        self.write('zh_cn.json', {'some.key': 'x'})
        with self.assertRaises(lang.LangError) as caught:
            lang.load(self.dir / 'en_us.json')
        message = str(caught.exception)
        self.assertIn('en_us.json', message)
        self.assertIn('some.key', message)

    def test_list_value_is_usage_error(self):
        self.write('en_us.json', {'k': ['a']})
        self.write('zh_cn.json', {'k': 'a'})
        self.assertEqual(lang.main(['--lang-dir', str(self.dir)]), 2)


if __name__ == '__main__':
    unittest.main()
