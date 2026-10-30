"""Remove disconnected reconstruction debris from PWG1, without editing textures.

Connectivity is measured across all material/rig seams in the neutral pose. The
largest connected surface is retained with its positions, normals and UVs intact.
Run on a copy first; the input file is replaced only after all validation passes.
"""
import argparse
import gzip
import io
import json
import struct
import os
import tempfile
from pathlib import Path


def clean(path):
    source = io.BytesIO(gzip.decompress(path.read_bytes()))

    def integer():
        return struct.unpack('>i', source.read(4))[0]

    def text():
        return source.read(struct.unpack('>H', source.read(2))[0])

    if integer() != 0x50574731:
        raise ValueError('Expected PWG1 precision wing mesh')
    count = integer()
    if not 1 <= count <= 12:
        raise ValueError('Invalid material count')
    meshes, parents, weld = [], [], {}

    def root(a):
        while parents[a] != a:
            parents[a] = parents[parents[a]]
            a = parents[a]
        return a

    for _ in range(count):
        part, color, glow = integer(), text(), text()
        vertices, indices, emission = integer(), integer(), integer()
        if not (0 <= part <= 2 and 0 < vertices <= 300_000 and 0 < indices <= 600_000
                and indices % 3 == 0 and 0 <= emission <= indices and emission % 3 == 0):
            raise ValueError('Invalid mesh budget')
        raw = source.read(vertices * 40)
        positions = [struct.unpack_from('>fff', raw, i * 40) for i in range(vertices)]
        ids = []
        for position in positions:
            key = tuple(round(v, 5) for v in position)
            if key not in weld:
                weld[key] = len(parents)
                parents.append(len(parents))
            ids.append(weld[key])
        faces = list(struct.unpack(f'>{indices}i', source.read(indices * 4)))
        glowing = list(struct.unpack(f'>{emission}i', source.read(emission * 4)))
        if any(v < 0 or v >= vertices for v in faces + glowing):
            raise ValueError('Index outside vertex buffer')
        for f in range(0, indices, 3):
            a, b, c = (root(ids[v]) for v in faces[f:f + 3])
            parents[b] = a
            parents[c] = a
        meshes.append((part, color, glow, raw, ids, faces, glowing))
    if source.read(1):
        raise ValueError('Trailing data')
    counts = {}
    for _, _, _, _, ids, faces, _ in meshes:
        for i in range(0, len(faces), 3):
            component = root(ids[faces[i]])
            counts[component] = counts.get(component, 0) + 1
    main = max(counts, key=counts.get)
    before = sum(counts.values())
    if counts[main] < before * .95:
        raise ValueError('Refusing to remove more than 5% of the accepted model')
    result = io.BytesIO()
    result.write(struct.pack('>ii', 0x50574731, count))
    after = 0
    for part, color, glow, raw, ids, faces, glowing in meshes:
        keep = [v for i in range(0, len(faces), 3)
                if root(ids[faces[i]]) == main for v in faces[i:i + 3]]
        light = [v for i in range(0, len(glowing), 3)
                 if root(ids[glowing[i]]) == main for v in glowing[i:i + 3]]
        used = sorted(set(keep))
        if not used:
            raise ValueError('Cleaning unexpectedly emptied a material group')
        remap = {v: i for i, v in enumerate(used)}
        if not set(light).issubset(remap):
            raise ValueError('Emission no longer belongs to the retained surface')
        result.write(struct.pack('>i', part))
        for value in [color, glow]:
            result.write(struct.pack('>H', len(value)) + value)
        result.write(struct.pack('>iii', len(used), len(keep), len(light)))
        for v in used:
            result.write(raw[v * 40:(v + 1) * 40])
        for values in [keep, light]:
            result.write(struct.pack(f'>{len(values)}i', *(remap[v] for v in values)))
        after += len(keep) // 3
    packed = gzip.compress(result.getvalue(), compresslevel=9, mtime=0)
    handle, temporary = tempfile.mkstemp(prefix=path.name + '.', suffix='.tmp', dir=path.parent)
    try:
        with os.fdopen(handle, 'wb') as target:
            target.write(packed)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    return {'components_before': len(counts), 'components_after': 1,
            'triangles_before': before, 'triangles_after': after,
            'removed_triangles': before - after, 'packed_bytes': len(packed),
            'retained_vertices_uvs_normals_unchanged': True}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mesh', type=Path)
    args = parser.parse_args()
    print(json.dumps(clean(args.mesh), indent=2))
