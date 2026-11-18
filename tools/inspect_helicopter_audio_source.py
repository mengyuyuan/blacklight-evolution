"""Read original P1 helicopter Patch/AudioFile metadata; never export its PCM.

Input packages stay in the private reference directory. This is a binary data
reader, not a game/native-code runner or a sound synthesis script.
"""
from pathlib import Path
import argparse
import hashlib
import json
import struct


def hashed(name):
    value = 0
    for char in name.encode('ascii'):
        value = ((value * 65599) ^ char) & 0xffffffffffffffff
    return value


class Reader:
    def __init__(self, data, offset=0):
        self.data, self.at = data, offset

    def take(self, fmt):
        values = struct.unpack_from('<' + fmt, self.data, self.at)
        self.at += struct.calcsize('<' + fmt)
        return values[0] if len(values) == 1 else list(values)

    def string(self):
        length = self.take('I')
        value = self.data[self.at:self.at + length].decode('ascii')
        self.at += length
        if self.take('B') != 0:
            raise ValueError('Unterminated metadata string')
        return value

    def named(self):
        key, present = self.take('Q'), self.take('B')
        if present != 1:
            raise ValueError('Expected explicit metadata name')
        name = self.string()
        if key != hashed(name):
            raise ValueError('Metadata name/hash mismatch: ' + name)
        return name


def inspect(path):
    data = path.read_bytes()
    rows, position = [], 12
    while position < len(data):
        kind, header, length = struct.unpack_from('<III', data, position)
        if kind != 0xfe000000 or header != length or length < 12:
            raise ValueError('Unexpected audio chunk at ' + hex(position))
        body = data[position + 12:position + length]
        reader = Reader(body, 4)
        kind = reader.string()
        version, name = reader.take('I'), reader.string()
        row = {'kind': kind, 'name': name, 'hash': str(hashed(name)),
               'chunkOffset': position, 'version': version}
        if kind == 'Patch':
            row['player'] = reader.named()
            row['flags'] = reader.take('B')
            count = reader.take('I')
            values, evidence = {}, {}
            for _ in range(count):
                typ, key = reader.named(), reader.named()
                offset = position + 12 + reader.at
                if typ == 'Float32':
                    value = reader.take('f')
                elif typ == 'Bool':
                    value = reader.take('B')
                    if value not in (0, 1):
                        raise ValueError('Invalid boolean ' + key)
                    value = bool(value)
                elif typ == 'Vector':
                    value = reader.take('3f')
                elif typ == 'String':
                    value = reader.string()
                elif typ == 'String List':
                    value = [reader.string() for _ in range(reader.take('I'))]
                elif typ == 'EnvelopeData':
                    value = [reader.take('2f') for _ in range(reader.take('I'))]
                else:
                    raise ValueError('Unsupported metadata value ' + typ)
                if key in values:
                    raise ValueError('Duplicate patch property ' + key)
                values[key] = value
                evidence[key] = {'type': typ, 'valueByteOffset': offset}
            if reader.at != len(body):
                raise ValueError('Unconsumed patch bytes for ' + name)
            row.update(properties=values, propertyEvidence=evidence)
        elif kind == 'AudioFile':
            row['alias'] = reader.string()
            rad = body.index(b'RADP', reader.at)
            channels, rate, unknown, size = struct.unpack_from('<IIII', body, rad + 4)
            encoded = body[rad + 20:]
            if len(encoded) != size or channels < 1 or size % (20 * channels):
                raise ValueError('Invalid RADP block layout for ' + name)
            frames = size // (20 * channels) * 32
            row.update(codec='RADP', channels=channels, sampleRate=rate,
                       sampleFrames=frames, seconds=frames / rate,
                       encodedSha256=hashlib.sha256(encoded).hexdigest())
        rows.append(row)
        position += length
    if position != len(data):
        raise ValueError('Incomplete audio package')
    return {'source': path.name, 'bytes': len(data),
            'sha256': hashlib.sha256(data).hexdigest(), 'objects': rows}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--private-dir', required=True, type=Path)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    packages = [inspect(args.private_dir / name) for name in
                ('helicopters_global.p3d', 'heli_bh_marine.p3d', 'heli_gunship_marine.p3d')]
    result = {'format': 'P1 Audio Patch and AudioFile metadata', 'packages': packages,
              'scope': 'Exact name/hash/property values and encoded-audio metadata only; no original waveform exported.'}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    for package in packages:
        for obj in package['objects']:
            if obj.get('player') == 'Helicopters':
                print(package['source'], obj['name'], 'properties', len(obj['properties']))


if __name__ == '__main__':
    main()
