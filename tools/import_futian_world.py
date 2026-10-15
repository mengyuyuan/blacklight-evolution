"""Import an Arnis OSM world as bounded, block-only worldgen templates.

No entity, command block, block entity or executable world data is imported.
The source OSM extract and Arnis world stay outside the mod source tree.
"""
import argparse
import gzip
import io
import json
import struct
import zlib
from pathlib import Path
import nbtlib
import numpy as np

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--world', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
palette = [{'Name': 'minecraft:air'}]
index = {json.dumps(palette[0], sort_keys=True): 0}
max_y = 63
chunks = 0
unsupported = set()
for path in sorted((args.world / 'region').glob('*.mca')):
    region = path.read_bytes()
    for i in range(1024):
        offset = int.from_bytes(region[i * 4:i * 4 + 3], 'big') * 4096
        if not offset:
            continue
        length = int.from_bytes(region[offset:offset + 4], 'big')
        assert region[offset + 4] == 2
        root = nbtlib.File.parse(io.BytesIO(zlib.decompress(region[offset + 5:offset + 4 + length])))
        x, z = int(root['xPos']), int(root['zPos'])
        if x < 0 or z < 0 or x >= 90 or z >= 77:
            continue
        blocks = np.zeros((320, 16, 16), dtype=np.uint16)
        for section in root['sections']:
            y = int(section['Y']) * 16
            if y < 48 or y >= 320 or 'block_states' not in section:
                continue
            states = section['block_states']
            mapping = []
            for state in states['palette']:
                value = {'Name': str(state['Name'])}
                if 'Properties' in state:
                    value['Properties'] = {str(k): str(v) for k, v in state['Properties'].items()}
                # Import scenery, never executable/interactive block entities.
                if value['Name'] in {'minecraft:command_block', 'minecraft:chain_command_block',
                                     'minecraft:repeating_command_block', 'minecraft:structure_block',
                                     'minecraft:jigsaw', 'minecraft:spawner'}:
                    raise ValueError('Unexpected executable block: ' + value['Name'])
                key = json.dumps(value, sort_keys=True)
                if key not in index:
                    index[key] = len(palette)
                    palette.append(value)
                mapping.append(index[key])
            if len(mapping) == 1:
                blocks[y:y + 16] = mapping[0]
            else:
                bits = max(4, (len(mapping) - 1).bit_length())
                per_long = 64 // bits
                packed = np.array(states['data'], dtype=np.int64).view(np.uint64)
                pos = np.arange(4096)
                ids = ((packed[pos // per_long] >> ((pos % per_long) * bits).astype(np.uint64)) & ((1 << bits) - 1)).astype(int)
                blocks[y:y + 16] = np.array(mapping, dtype=np.uint16)[ids].reshape(16, 16, 16)
        # The procedural generator supplies bedrock/stone under the 63 m surface.
        blocks[:63] = 0
        encoded = bytearray(b'BFC1')
        for dz in range(16):
            for dx in range(16):
                column = blocks[:, dz, dx]
                boundaries = np.r_[0, np.nonzero(column[1:] != column[:-1])[0] + 1, 320]
                runs = [(int(a), int(b-a), int(column[a])) for a,b in zip(boundaries[:-1], boundaries[1:]) if column[a]]
                encoded += struct.pack('>H', len(runs))
                for a, length, block in runs:
                    encoded += struct.pack('>HHH', a, length, block)
                    max_y = max(max_y, a + length - 1)
        (args.output / f'{x}_{z}.bin.gz').write_bytes(gzip.compress(encoded, mtime=0))
        chunks += 1
    print(path.name, chunks, 'chunks', flush=True)
metadata = dict(schema=1, width=1440, depth=1232, height=320, ground=63,
                bbox=[22.541,114.048,22.552,114.062], scale=1,
                sources=['https://www.openstreetmap.org/copyright', 'https://github.com/louis-e/arnis/releases/tag/v3.3.0'],
                attribution='Map data © OpenStreetMap contributors, ODbL 1.0; generated with Arnis 3.3.0 (Apache-2.0).',
                generation='OSM road/building geometry; flat terrain; generated interiors. Not a survey-accurate replica.',
                chunks=chunks, maxY=max_y, palette=palette)
(args.output / 'map.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
print('complete',chunks,'palette',len(palette),'maxY',max_y)
