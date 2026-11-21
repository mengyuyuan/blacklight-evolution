#!/usr/bin/env python3
"""Check that relative links in Markdown files resolve inside the repository.

Standard library only, matching the other helpers under tools/. Read-only: it
never edits files, moves content, or contacts the network.

What counts as a link
---------------------
* inline links and images: ``[text](destination)`` / ``![alt](destination)``,
  including a balanced parentheses group inside the destination
  (``docs/foo(bar).md``) and the ``<destination>`` angle-bracket form;
* an optional title after the destination, in ``"``, ``'`` or ``(`` ``)``;
* reference definitions on their own line: ``[label]: destination``;
* ``<img src="...">`` and ``<a href="...">``.

Link text inside inline code spans or fenced code blocks is documentation, not
a link, and is skipped. Those spans keep their length, so reported line numbers
still match the file on disk.

What is not checked
-------------------
External URLs, ``mailto:``, bare fragments, and absolute filesystem paths are
not resolved. A Windows path such as ``E:/research/notes.md`` looks like a URI
scheme (``E:``), so it is treated as a local absolute path and skipped by
default; ``--strict-local`` reports it instead. This is deliberate: the
repository cannot resolve a path on the author's local disk, and reporting it
as "missing" would be wrong.

Exit codes: 0 = every checked link resolves, 1 = at least one finding,
2 = usage error.
"""
from __future__ import annotations

import argparse
import json
import os
import posixpath
import re
import sys
import urllib.parse
from pathlib import Path

SKIP_DIRS = {'.git', 'build', '.gradle', 'node_modules', 'run', 'run-wall-gametest'}
REF_DEF = re.compile(r'^\s{0,3}\[[^\]]+\]:\s*(\S+)', re.M)
HTML_ATTR = re.compile(r'<(?:img|a)\b[^>]*?\b(?:src|href)\s*=\s*"([^"]+)"', re.I)
SCHEME = re.compile(r'^[a-zA-Z][a-zA-Z0-9+.-]*:')
WINDOWS_ABS = re.compile(r'^[a-zA-Z]:[\\/]')
FENCE = re.compile(r'^\s{0,3}(`{3,}|~{3,})')
BACKTICKS = re.compile(r'`+')


# --------------------------------------------------------------- code masking
def mask_code(text: str) -> str:
    """Blank out fenced code blocks and inline code spans, preserving length."""
    lines = text.split('\n')
    out: list[str] = []
    fence: str | None = None
    for line in lines:
        if fence is None:
            match = FENCE.match(line)
            if match:
                fence = match.group(1)
                out.append(' ' * len(line))
                continue
            out.append(mask_inline_code(line))
        else:
            stripped = line.strip()
            if stripped.startswith(fence) and set(stripped) <= {fence[0], ' '}:
                fence = None
            out.append(' ' * len(line))
    return '\n'.join(out)


def mask_inline_code(line: str) -> str:
    """Blank out ``code`` spans inside one line, keeping byte length."""
    chars = list(line)
    i = 0
    while i < len(line):
        if line[i] != '`':
            i += 1
            continue
        run = BACKTICKS.match(line, i)
        ticks = run.group(0)
        close = line.find(ticks, run.end())
        if close == -1:
            i = run.end()
            continue
        for j in range(i, close + len(ticks)):
            chars[j] = ' '
        i = close + len(ticks)
    return ''.join(chars)


# ------------------------------------------------------------------- scanning
def parse_destination(text: str, start: int) -> tuple[str | None, int]:
    """Read a link destination starting just after '('.

    Returns (destination, index after the closing ')') or (None, start) when the
    group is not a well-formed inline link destination.
    """
    n = len(text)
    i = start
    while i < n and text[i] in ' \t':
        i += 1
    if i >= n or text[i] == '\n':
        return None, start

    if text[i] == '<':
        end = text.find('>', i + 1)
        if end == -1 or '\n' in text[i:end]:
            return None, start
        dest = text[i + 1:end]
        i = end + 1
    else:
        depth = 0
        begin = i
        while i < n:
            c = text[i]
            if c == '\\' and i + 1 < n:
                i += 2
                continue
            if c == '\n':
                return None, start
            if c == '(':
                depth += 1
            elif c == ')':
                if depth == 0:
                    break
                depth -= 1
            elif c in ' \t' and depth == 0:
                break
            i += 1
        dest = text[begin:i]

    # optional title, then the closing parenthesis
    while i < n and text[i] in ' \t':
        i += 1
    if i < n and text[i] in '"\'':
        quote = text[i]
        end = text.find(quote, i + 1)
        if end == -1:
            return None, start
        i = end + 1
        while i < n and text[i] in ' \t':
            i += 1
    if i < n and text[i] == ')':
        return dest.strip(), i + 1
    return None, start


def iter_links(masked: str):
    """Yield (index, destination) for every inline link or image."""
    n = len(masked)
    i = 0
    while i < n:
        if masked[i] == '!' and i + 1 < n and masked[i + 1] == '[':
            start, i = i, i + 2
        elif masked[i] == '[':
            start, i = i, i + 1
        else:
            i += 1
            continue

        depth = 1
        j = i
        while j < n and depth:
            c = masked[j]
            if c == '\\':
                j += 2
                continue
            if c == '[':
                depth += 1
            elif c == ']':
                depth -= 1
            j += 1
        if depth:
            i += 1
            continue

        if j < n and masked[j] == '(':
            dest, end = parse_destination(masked, j + 1)
            if dest is not None:
                yield start, dest
                i = end
                continue
        i = j


def iter_targets(text: str):
    """Yield (line, destination) for every link-looking target in `text`."""
    masked = mask_code(text)
    for index, dest in iter_links(masked):
        if dest:
            yield masked.count('\n', 0, index) + 1, dest
    for regex in (REF_DEF, HTML_ATTR):
        for match in regex.finditer(masked):
            target = match.group(1).strip()
            if target:
                yield masked.count('\n', 0, match.start()) + 1, target


# ------------------------------------------------------------------ resolving
def classify(target: str) -> str:
    """Return 'external', 'local-abs', 'fragment' or 'relative'."""
    if not target:
        return 'fragment'
    if target.startswith('#'):
        return 'fragment'
    if WINDOWS_ABS.match(target):
        return 'local-abs'
    if target.startswith('//'):
        return 'external'
    if SCHEME.match(target):
        return 'external'
    return 'relative'


def resolve(doc: Path, root: Path, target: str) -> str | None:
    """Return a repo-relative path, or None when the target is not local."""
    if classify(target) != 'relative':
        return None
    cleaned = urllib.parse.unquote(target.split('#')[0].split('?')[0]).strip()
    if not cleaned:
        return None
    if cleaned.startswith('/'):
        joined = cleaned.lstrip('/')
    else:
        joined = posixpath.normpath(
            posixpath.join(doc.parent.relative_to(root).as_posix(), cleaned))
    if joined == '..' or joined.startswith('../'):
        return ''
    return joined


def existing_paths(root: Path) -> tuple[set[str], set[str]]:
    files: set[str] = set()
    dirs: set[str] = {'', '.'}
    for base, names, filenames in os.walk(root):
        names[:] = [n for n in names if n not in SKIP_DIRS]
        rel_base = Path(base).relative_to(root).as_posix()
        if rel_base != '.':
            dirs.add(rel_base)
        for name in filenames:
            files.add(posixpath.join(rel_base, name) if rel_base != '.' else name)
    return files, dirs


def markdown_files(root: Path) -> list[Path]:
    found = []
    for base, names, filenames in os.walk(root):
        names[:] = sorted(n for n in names if n not in SKIP_DIRS)
        for name in filenames:
            if name.lower().endswith('.md'):
                found.append(Path(base) / name)
    return sorted(found)


def check(root: Path, strict_local: bool = False) -> list[tuple[str, int, str, str]]:
    files, dirs = existing_paths(root)
    casefold = {f.casefold(): f for f in files} | {d.casefold(): d for d in dirs}
    findings: list[tuple[str, int, str, str]] = []
    for doc in markdown_files(root):
        try:
            text = doc.read_text(encoding='utf-8')
        except (UnicodeDecodeError, OSError):
            text = doc.read_text(encoding='utf-8', errors='replace')
        rel_doc = doc.relative_to(root).as_posix()
        for line, target in iter_targets(text):
            kind = classify(target)
            if kind in ('external', 'fragment'):
                continue
            if kind == 'local-abs':
                if strict_local:
                    findings.append((rel_doc, line, target, 'local absolute path outside the repository'))
                continue
            resolved = resolve(doc, root, target)
            if resolved is None:
                continue
            if resolved == '':
                findings.append((rel_doc, line, target, 'escapes repository root'))
                continue
            if resolved in files or resolved in dirs:
                continue
            if resolved.casefold() in casefold:
                findings.append((rel_doc, line, target,
                                 'case mismatch: ' + casefold[resolved.casefold()]))
                continue
            findings.append((rel_doc, line, target, 'missing: ' + resolved))
    return findings


def count_local_abs(root: Path) -> int:
    total = 0
    for doc in markdown_files(root):
        try:
            text = doc.read_text(encoding='utf-8')
        except (UnicodeDecodeError, OSError):
            text = doc.read_text(encoding='utf-8', errors='replace')
        total += sum(1 for _, target in iter_targets(text) if classify(target) == 'local-abs')
    return total


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description='Check relative Markdown links inside the repository.')
    parser.add_argument('--root', default='.', help='repository root (default: current directory)')
    parser.add_argument('--json', action='store_true', help='emit machine-readable output')
    parser.add_argument('--summary', action='store_true', help='print only the count')
    parser.add_argument('--strict-local', action='store_true',
                        help='report local absolute paths (for example E:/notes.md) as findings')
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    if not root.is_dir():
        print(f'not a directory: {root}', file=sys.stderr)
        return 2

    findings = check(root, strict_local=args.strict_local)
    scanned = len(markdown_files(root))

    if args.json:
        print(json.dumps([{'file': f, 'line': l, 'target': t, 'reason': r}
                          for f, l, t, r in findings], ensure_ascii=False, indent=1))
    elif args.summary:
        print(f'{len(findings)} finding(s) in {scanned} Markdown file(s)')
    else:
        for rel_doc, line, target, reason in findings:
            print(f'{rel_doc}:{line}: {target} -> {reason}')
        print(f'\n{len(findings)} finding(s) in {scanned} Markdown file(s)')
        if not args.strict_local:
            skipped = count_local_abs(root)
            if skipped:
                print(f'{skipped} local absolute path link(s) skipped '
                      f'(not resolvable from the repository; use --strict-local to report)')
    return 1 if findings else 0


if __name__ == '__main__':
    raise SystemExit(main())
