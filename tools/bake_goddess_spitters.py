"""Bake donor mouth offsets relative to their torso for server-side hair-colony contacts."""
from pathlib import Path
import gzip
import struct

models = Path(__file__).resolve().parents[1] / "src/main/resources/assets/blacklight/models"
def rotated(v, q):
    x, y, z, w = q
    a, b, c = v
    dx, dy, dz = 2*(y*c-z*b), 2*(z*a-x*c), 2*(x*b-y*a)
    return (a+w*dx+y*dz-z*dy, b+w*dy+z*dx-x*dz, c+w*dz+x*dy-y*dx)

def main():
    with gzip.open(models / "greene_outbreak_fullbody.mesh.gz", "rb") as src:
        magic, groups, frames, rate = struct.unpack(">4i", src.read(16))
        assert magic == 0x47464d31 and (groups-35) % 12 == 0
        out = bytearray(struct.pack(">4i", 0x474d5431, (groups-35)//12, frames, rate))
        for group in range(groups):
            count, = struct.unpack(">i", src.read(4))
            pose = list(struct.iter_unpack(">7f", src.read(frames * 28)))
            verts = src.read(count * 36)
            if group < 35:
                continue
            if (group-35) % 12 == 0:
                torso = pose
            if (group-35) % 12 != 1:
                continue
            vertices = list(struct.iter_unpack(">8fi", verts))
            bounds = [(min(v[i] for v in vertices), max(v[i] for v in vertices)) for i in range(3)]
            mouth = (sum(bounds[0])/2, bounds[1][0]+.30*(bounds[1][1]-bounds[1][0]), bounds[2][0])
            for head, root in zip(pose, torso):
                lip = rotated(mouth, head[3:])
                offsets = [round((head[i]-root[i]+lip[i])*4096) for i in range(3)]
                out.extend(struct.pack(">3h", *offsets))
        target = models / "greene_goddess_mouths.bin.gz"
        target.write_bytes(gzip.compress(out, compresslevel=9, mtime=0))

if __name__ == "__main__":
    main()
