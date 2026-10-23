"""Small neutral fibre tile. Geometry owns shape; albedo adds Minecraft-scale folds.

16 texels per block, opaque nearest-filtered pixels, no normal-map/shader dependency.
"""
from pathlib import Path
import binascii
import struct
import zlib

SIZE = 16
pixels = bytearray()
for y in range(SIZE):
    for x in range(SIZE):
        groove = (x - (y // 4) % 2) % 8
        value = 255
        if groove == 0:
            value = 172
        elif groove == 1:
            value = 218
        elif groove == 7:
            value = 237
        if (x * 3 + y * 5) % 19 == 0:
            value = min(value, 225)
        pixels.extend((value, value, value, 255))

def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', binascii.crc32(kind + data) & 0xffffffff)

raw = b''.join(b'\0' + pixels[y * SIZE * 4:(y + 1) * SIZE * 4] for y in range(SIZE))
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>2I5B', SIZE, SIZE, 8, 6, 0, 0, 0))
png += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
path = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/blacklight/textures/entity/tissue_fibres.png'
path.write_bytes(png)
print(f'{path}: {len(png)} bytes')
