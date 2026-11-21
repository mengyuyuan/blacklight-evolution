"""Draw a seamless charcoal tissue tile and tangent normals from authored pixel strokes."""
from pathlib import Path
import math
import struct
import zlib

SIZE = 48
# Control points are in a 48-pixel repeating square, with a one-tile overhang at its edges.
STROKES = [
    ([(-4, 8), (5, 11), (10, 15), (18, 16), (29, 13), (34, 10), (44, 8)], 2.6),
    ([(-4, 31), (5, 30), (12, 24), (18, 21), (28, 21), (35, 25), (44, 31)], 2.1),
    ([(0, -3), (7, 4), (9, 12), (16, 18), (20, 25), (18, 32), (12, 38), (0, 45)], 2.3),
    ([(25, -4), (31, 2), (31, 8), (26, 15), (27, 22), (35, 31), (34, 36), (25, 44)], 1.75),
    ([(-2, 39), (9, 40), (17, 35), (20, 35), (27, 38)], 1.25),
    ([(14, 5), (16, 11), (21, 15), (26, 15)], 1.0),
    ([(33, 28), (40, 23), (47, 22), (53, 18)], 1.25),
    ([(2, 3), (0, 4), (-5, 5), (-11, 9)], .8),
]
PALETTE = [(9, 10, 14), (17, 18, 23), (27, 27, 34), (39, 39, 47), (55, 53, 59), (74, 69, 73)]

def chunk(kind, raw):
    return struct.pack(">I", len(raw))+kind+raw+struct.pack(">I", zlib.crc32(kind+raw))

def save(path, pixels):
    rows = b"".join(b"\0"+bytes(c for px in row for c in px) for row in pixels)
    path.write_bytes(b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",SIZE,SIZE,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(rows,9))+chunk(b"IEND",b""))

def draw():
    relief = [[0.0]*SIZE for _ in range(SIZE)]
    for points,width in STROKES:
        for (ax,ay),(bx,by) in zip(points,points[1:]):
            dx,dy=bx-ax,by-ay
            for y in range(math.floor(min(ay,by)-width),math.ceil(max(ay,by)+width)+1):
                for x in range(math.floor(min(ax,bx)-width),math.ceil(max(ax,bx)+width)+1):
                    t=max(0,min(1,((x+.5-ax)*dx+(y+.5-ay)*dy)/(dx*dx+dy*dy)))
                    distance=math.hypot(x+.5-ax-t*dx,y+.5-ay-t*dy)
                    h=max(0,1-(distance/width)**2)
                    relief[y%SIZE][x%SIZE]=max(relief[y%SIZE][x%SIZE],h)
    diffuse=[]; normals=[]
    for y,row in enumerate(relief):
        colours=[]; bumps=[]
        for x,h in enumerate(row):
            nx=(row[(x-1)%SIZE]-row[(x+1)%SIZE])*.75
            ny=(relief[(y-1)%SIZE][x]-relief[(y+1)%SIZE][x])*.75
            length=math.sqrt(1+nx*nx+ny*ny)
            step=max(0,min(5,round(1+2.3*h+1.4*nx-1.1*ny))) if h else 0
            opacity=round(255*min(1,h*2.8))
            colours.append((*PALETTE[step],opacity))
            bumps.append(tuple(round(127.5+127.5*n/length) for n in (nx,ny,1))+(255,))
        diffuse.append(colours);normals.append(bumps)
    dest=Path(__file__).resolve().parents[1]/"src/main/resources/assets/blacklight/textures/entity"
    save(dest/"consume_charcoal_skin.png",diffuse)
    save(dest/"consume_charcoal_skin_normal.png",normals)

if __name__ == "__main__":
    draw()
