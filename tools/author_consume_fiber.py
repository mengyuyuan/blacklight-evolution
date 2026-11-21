"""Author a 32 x 32 charcoal-fibre pixel atlas from a palette and hand-plotted pixel marks.

The four eight-pixel strips correspond to four block faces. No image, mesh, game archive
or UV file is required as input. Run from any directory with a standard Python 3.
"""
from pathlib import Path
import argparse
import struct
import zlib

PALETTE = [(13, 14, 19), (20, 22, 28), (29, 32, 40), (42, 44, 53),
           (61, 63, 72), (79, 79, 86), (40, 31, 38), (55, 40, 47)]
# Hand-placed longitudinal offsets create short, pixel-stepped, overlapping filaments.
RIDGES = [
    (2, 2, 3, 3, 2, 2, 1, 2), (4, 5, 5, 4, 4, 3, 3, 4),
    (3, 3, 2, 2, 3, 4, 4, 3), (4, 3, 3, 2, 3, 3, 4, 4),
]
GLINTS = [{2, 3, 13, 24}, {7, 8, 19, 29}, {5, 14, 15, 25}, {4, 10, 20, 21}]

def pigment(x, y):
    side, u = divmod(x, 8)
    ridge = RIDGES[side][y // 4]
    value = 1 if (u + 2 * (y // 3) + side) % 6 else 2
    if u == 0 or u == 7:
        value = 0 if (y + 3 * side) % 8 < 5 else 1
    if u == ridge:
        value = 4 if ((y + side) % 11 < 5) else 3
        if y in GLINTS[side]:
            value = 5
    elif u == ridge + 1:
        value = 2 if y % 5 else 3
    # Muted plum lives in isolated undercuts, not along every edge.
    elif u == ridge - 1 and (y + 3 * side) % 11 < 4:
        value = 6 if y % 4 else 7
    return PALETTE[value]

def png():
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    rows = bytearray()
    for y in range(32):
        rows.append(0)
        for x in range(32):
            rows.extend(pigment(x, y))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 32, 32, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))

if __name__ == '__main__':
    default = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/blacklight/textures/entity/consume_charcoal_fiber.png'
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=default)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if args.check:
        if args.out.read_bytes() != png():
            raise SystemExit('The packaged fibre atlas differs from the generator.')
    else:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_bytes(png())
