"""Class selection by compiler SourceFile metadata, including sibling top-level types."""
from dataclasses import dataclass
from pathlib import Path
from collections.abc import Mapping
import re
import struct

@dataclass(frozen=True)
class ClassInfo:
    name: str
    source_file: str | None
    references: frozenset[str]

def class_info(data: bytes) -> ClassInfo:
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError('Not a Java class file')
    pos = 8
    def read(fmt):
        nonlocal pos
        size = struct.calcsize(fmt)
        value = struct.unpack_from(fmt, data, pos)
        pos += size
        return value[0] if len(value) == 1 else value
    def skip(length):
        nonlocal pos
        if length < 0 or pos + length > len(data):
            raise ValueError('Truncated Java class file')
        pos += length
    count = read('>H')
    constants = [None] * count
    class_names = []
    i = 1
    while i < count:
        tag = read('>B')
        if tag == 1:
            length = read('>H')
            constants[i] = data[pos:pos + length].decode('utf-8', errors='replace')
            skip(length)
        elif tag == 7:
            constants[i] = read('>H')
            class_names.append(constants[i])
        elif tag in (8, 16, 19, 20):
            skip(2)
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            skip(4)
        elif tag in (5, 6):
            skip(8)
            i += 1
        elif tag == 15:
            skip(3)
        else:
            raise ValueError(f'Unsupported class constant tag {tag}')
        i += 1
    _, this_class, _ = read('>HHH')
    name = constants[constants[this_class]]
    skip(read('>H') * 2)
    for _ in range(2):  # fields, methods
        for _ in range(read('>H')):
            skip(6)
            for _ in range(read('>H')):
                skip(2)
                skip(read('>I'))
    source_file = None
    for _ in range(read('>H')):
        attribute = constants[read('>H')]
        length = read('>I')
        if attribute == 'SourceFile':
            if length != 2:
                raise ValueError('Invalid SourceFile attribute')
            source_file = constants[read('>H')]
        else:
            skip(length)
    references = set()
    for index in class_names:
        value = constants[index]
        if not value.startswith('['):
            references.add(value)
    for value in constants:
        if isinstance(value, str):
            references.update(re.findall(r'L(com/codex/blacklight/[\w$/]+)(?:;|<)', value))
    return ClassInfo(name, source_file, frozenset(references))

def production_classes(entries: Mapping[str, bytes], source_root: Path) -> dict[str, bytes]:
    sources = {p.relative_to(source_root).as_posix() for p in source_root.rglob('*.java')}
    result = {}
    for entry, data in entries.items():
        if not entry.endswith('.class'):
            continue
        info = class_info(data)
        if info.name + '.class' != entry:
            raise ValueError(f'Class path mismatch: {entry} / {info.name}')
        source = info.source_file
        if source is None or '/' in source or '\\' in source:
            continue
        package = info.name.rpartition('/')[0]
        if (package + '/' if package else '') + source in sources:
            result[entry] = data
    return result

def verify_internal_closure(entries: Mapping[str, bytes], namespace: str = 'com/codex/blacklight/') -> None:
    names = {name[:-6] for name in entries if name.endswith('.class')}
    missing = {}
    for entry, data in entries.items():
        if not entry.endswith('.class'):
            continue
        info = class_info(data)
        unresolved = sorted(ref for ref in info.references if ref.startswith(namespace) and ref not in names)
        if unresolved:
            missing[entry] = unresolved
    if missing:
        raise ValueError(f'Missing internal runtime classes: {missing}')
