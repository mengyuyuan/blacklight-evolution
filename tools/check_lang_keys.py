#!/usr/bin/env python3
"""Check Minecraft language files for key parity and format-argument consistency.

Standard library only, matching the other helpers under tools/. Read-only.

What is compared
----------------
* key sets: every key in one file must exist in the other, and vice versa;
* format arguments: the arguments each translation consumes, in order. This is
  what ``String.format`` / Minecraft's ``Component.translatable`` actually
  depend on, so ``%s %d`` and ``%d %s`` are different and are reported;
* ``{0}``-style placeholders are compared as a separate, equality-only family.

Java's ``Formatter`` syntax is honoured rather than approximated:

* ``%%`` is an escaped literal percent and consumes no argument;
* ``%s``  uses the next implicit argument, in order;
* ``%1$s`` uses an explicit, absolute argument index;
* implicit and explicit forms cannot be mixed in one string (Java throws), so a
  mixed string is reported as an error instead of being "normalised";
* ``%tY`` / ``%<s`` style flags are recognised as far as argument counting goes.

Percent signs in prose
----------------------
Java's grammar is greedy: ``135% of`` really does parse as a space-flagged octal
conversion ``% o``, and ``47.5% resistance`` as ``%r``. Treating every such
percent as a placeholder would flag ordinary documentation text, and the mod's
own strings are full of it ("Restore 35% more health"). Two modes therefore
exist:

* default (``prose``): a conversion only counts when it is one that Minecraft
  translation strings actually use for arguments -- ``s``, ``d``, ``f`` and the
  ``t`` date/time family -- optionally with an explicit ``n$`` index, a width or
  a precision. A bare percent followed by any other letter is prose and is
  ignored.
* ``--java-semantics``: every ``%`` that Java would accept counts, matching
  ``java.util.Formatter`` exactly. Use this when auditing for the latent bug
  where a prose percentage is interpreted as a conversion.

Two spellings that reach the same argument are treated as equivalent: ``%s``
and ``%1$s`` in a single-argument string both mean "argument 1", so they are
reported as an equivalent form (a warning) rather than as a mismatch.

Not implemented
---------------
This tool reads the two language JSON files only. It does not scan Java sources
for ``Component.translatable`` keys, so a key missing from *both* locales is not
detected here. Dynamic keys built by concatenation would not be resolvable
without running the mod, so that check is deliberately out of scope.

Exit codes: 0 = clean, 1 = findings, 2 = usage error.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

DEFAULT_LANG_DIR = Path('src/main/resources/assets/blacklight/lang')
CJK = re.compile(r'[\u3000-\u303f\u4e00-\u9fff\uff00-\uffef]')

# %[argument_index$][flags][width][.precision][t]conversion
FORMAT_SPEC = re.compile(
    r'%(?:(\d+)\$)?'          # explicit 1-based argument index
    r'([-#+ 0,(<]*)'          # flags
    r'(\d+)?'                 # width
    r'(?:\.(\d+))?'           # precision
    r'(t)?'                   # date/time prefix
    r'([a-zA-Z%])'            # conversion
)
# Conversions Minecraft translations actually consume. Prose percentages
# ("135% of") must not be read as format specs, so the default mode only
# accepts these unless an explicit index, width or precision is present.
ARGUMENT_CONVERSIONS = set('sdf')
BRACE = re.compile(r'\{(\d+)\}')


class LangError(ValueError):
    """Invalid language file content; reported with the file and key name."""


def load(path: Path) -> dict[str, str]:
    """Parse a language JSON file, validating that every value is a string."""
    try:
        data = json.loads(path.read_text(encoding='utf-8'))
    except json.JSONDecodeError as error:
        raise LangError(f'{path.name}: invalid JSON: {error}') from error
    if not isinstance(data, dict):
        raise LangError(f'{path.name}: top level is {type(data).__name__}, expected an object')
    for key, value in data.items():
        if not isinstance(value, str):
            raise LangError(
                f'{path.name}: key {key!r} has {type(value).__name__} value '
                f'({value!r}), expected a string')
    return data


def format_args(value: str, java_semantics: bool = False) -> tuple[list[tuple[int | None, str]], bool]:
    """Return (specs in source order, mixed_implicit_and_explicit).

    Each spec is ``(explicit_index_or_None, conversion_character)``. The
    conversion character is kept because ``%s`` and ``%d`` consume different
    Java types: swapping them is a real defect even though both are "the next
    argument". ``%%`` contributes nothing; ``<`` reuses the previous spec.

    With ``java_semantics`` False (the default), a conversion only counts when
    it is one Minecraft arguments actually use -- ``s``, ``d``, ``f``, or the
    ``t`` date/time family -- or when it carries an explicit ``n$`` index, a
    width or a precision. That keeps prose like ``135% of`` from being read as
    the octal conversion ``% o``.
    """
    specs: list[tuple[int | None, str]] = []
    implicit = False
    explicit = False
    last: tuple[int | None, str] | None = None
    for match in FORMAT_SPEC.finditer(value):
        index, flags, width, precision, time_prefix, conversion = match.groups()
        if conversion == '%':
            continue
        if not java_semantics:
            qualified = (index is not None or width is not None
                         or precision is not None or time_prefix is not None)
            if not qualified and conversion not in ARGUMENT_CONVERSIONS:
                continue
            # "80% damage" parses in Java as a space-flagged %d, and "47.5%
            # resistance" as a space-flagged %s. Without an index, width or
            # precision that is prose, not an argument reference.
            if not qualified and ' ' in flags:
                continue
        if '<' in flags:
            if last is not None:
                specs.append(last)
            continue
        if index is not None:
            explicit = True
            last = (int(index), conversion)
        else:
            implicit = True
            last = (None, conversion)
        specs.append(last)
    return specs, (implicit and explicit)


def describe(specs: list[tuple[int | None, str]]) -> str:
    parts = []
    for index, conversion in specs:
        parts.append(f"%{'' if index is None else str(index) + '$'}{conversion}"
                     if index is not None else f'%{conversion}')
    return '[' + ' '.join(parts) + ']' if parts else '[]'


def compare(reference: dict[str, str], other: dict[str, str],
            reference_name: str, other_name: str,
            java_semantics: bool = False) -> list[str]:
    """Compare key sets and format arguments; return human-readable findings."""
    findings: list[str] = []
    for key in sorted(set(reference) - set(other)):
        findings.append(f'{key}: present in {reference_name}, missing from {other_name}')
    for key in sorted(set(other) - set(reference)):
        findings.append(f'{key}: present in {other_name}, missing from {reference_name}')

    for key in sorted(set(reference) & set(other)):
        left_args, left_mixed = format_args(reference[key], java_semantics)
        right_args, right_mixed = format_args(other[key], java_semantics)
        for name, mixed in ((reference_name, left_mixed), (other_name, right_mixed)):
            if mixed:
                findings.append(f'{key}: {name} mixes implicit %s with explicit %n$s '
                                f'argument indices; Java rejects this at runtime')
        if left_mixed or right_mixed:
            continue
        if left_args == right_args:
            continue
        if _same_arguments(left_args, right_args):
            findings.append(
                f'{key}: equivalent but differently spelled format arguments '
                f'({reference_name}={describe(left_args)} {other_name}={describe(right_args)})')
            continue
        findings.append(f'{key}: format arguments differ '
                        f'({reference_name}={describe(left_args)} {other_name}={describe(right_args)})')

    for key in sorted(set(reference) & set(other)):
        left, right = sorted(BRACE.findall(reference[key])), sorted(BRACE.findall(other[key]))
        if left != right:
            findings.append(f'{key}: {{n}} placeholders differ '
                            f'({reference_name}={left} {other_name}={right})')
    return findings


def _same_arguments(left: list[tuple[int | None, str]],
                    right: list[tuple[int | None, str]]) -> bool:
    """True when both consume the same Java arguments in the same order.

    A spec is one (index, conversion) pair. ``%s`` and ``%1$s`` in a
    single-argument string both mean "argument 1 as a string", so an all-implicit
    string is equivalent to one whose explicit indices count 1, 2, 3... in the
    same order with the same conversions. ``%%s`` consumes nothing, so it never
    compares equal to ``%s``.
    """
    return _expand(left) == _expand(right)


def _expand(specs: list[tuple[int | None, str]]) -> list[tuple[int, str]]:
    out: list[tuple[int, str]] = []
    counter = 0
    for index, conversion in specs:
        if index is None:
            counter += 1
            out.append((counter, conversion))
        else:
            out.append((index, conversion))
            counter = max(counter, index)
    return out


def untranslated(reference: dict[str, str]) -> list[str]:
    """Keys whose English value still contains CJK source text."""
    return [f'{key}: English value contains CJK text: {reference[key]!r}'
            for key in sorted(reference) if CJK.search(reference[key])]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description='Check Minecraft language files for key parity and format arguments.')
    parser.add_argument('--lang-dir', default=str(DEFAULT_LANG_DIR),
                        help='directory holding the locale files')
    parser.add_argument('--reference', default='en_us.json', help='reference locale file name')
    parser.add_argument('--other', default='zh_cn.json', help='locale file compared to the reference')
    parser.add_argument('--allow-cjk-in-reference', action='store_true',
                        help='skip the untranslated-source check')
    parser.add_argument('--java-semantics', action='store_true',
                        help='count every percent sequence java.util.Formatter would accept, '
                             'including prose percentages like "135%% of"')
    args = parser.parse_args(argv)

    lang_dir = Path(args.lang_dir)
    reference_path, other_path = lang_dir / args.reference, lang_dir / args.other
    for path in (reference_path, other_path):
        if not path.is_file():
            print(f'missing language file: {path}', file=sys.stderr)
            return 2
    try:
        reference, other = load(reference_path), load(other_path)
    except LangError as error:
        print(f'invalid language file: {error}', file=sys.stderr)
        return 2

    findings = compare(reference, other, args.reference, args.other,
                       java_semantics=args.java_semantics)
    if not args.allow_cjk_in_reference:
        findings.extend(untranslated(reference))
    for line in findings:
        print(line)
    print(f'\n{len(findings)} finding(s); {len(reference)} key(s) in {args.reference}, '
          f'{len(other)} in {args.other}')
    return 1 if findings else 0


if __name__ == '__main__':
    raise SystemExit(main())
