"""Author the four Blacklight Weave Nest block meshes and their original 32px tissue surfaces."""
from pathlib import Path
import json
import math
import random
import struct
import zlib

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/blacklight"
PARTS = ("core", "anchor", "channel", "bed")
FACES = ("up", "down", "north", "south", "west", "east")


def save_json(file, data):
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def texture(name, pigment, size=32):
    rng = random.Random(713)
    scan = bytearray()
    for y in range(size):
        scan.append(0)
        for x in range(size):
            rgb = pigment(x, y)
            grain = rng.choice((-2, -1, 0, 0, 1, 2))
            scan.extend(max(0, min(255, int(value + grain))) for value in rgb)

    def chunk(key, data):
        return struct.pack(">I", len(data)) + key + data + struct.pack(">I", zlib.crc32(key + data))

    header = struct.pack(">2I5B", size, size, 8, 2, 0, 0, 0)
    (ASSETS / "textures/block" / (name + ".png")).write_bytes(
        b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(scan)) + chunk(b"IEND", b""))


def enamel(x, y):
    ridge = (x * .52 + y + 2 * math.sin(x * .33)) % 11
    return (52, 50, 58) if ridge < 1.4 else (22, 22, 31) if ridge < 4 else (34, 32, 40)


def mycelium(x, y):
    sinew = (x + 1.6 * math.sin(y * .36) + y * .25) % 9
    return (105, 42, 50) if sinew < 1 else (78, 29, 42) if sinew < 3 else (53, 26, 36)


def bone(x, y):
    ridge = (x + .35 * y + math.sin(y * .5)) % 8
    return (100, 83, 85) if ridge < 1 else (78, 64, 70) if ridge < 3 else (52, 43, 54)


def seat(x, z, scale):
    # A shadowed square recess is picked out only at the four corners.
    x, z = abs(x) / scale, abs(z) / scale
    edge, cross = max(x, z), min(x, z)
    if .89 <= edge <= 1.0 and cross > .48:
        return 27
    if .76 <= edge < .89 and cross > .48:
        return -14
    return -5 if edge < .76 else 0


def bed_surface(part, x, y):
    u, v = x-15.5, y-15.5
    grey = 46 + seat(u, v, 10) + ((x//4 - y//3) % 3)*2
    if part == "core" and max(abs(u), abs(v)) < 6:
        return (101, 27, 34)
    if part == "channel" and abs(u-4) < 1.3:
        return (84, 32, 38)
    if part == "anchor" and min(abs(u), abs(v)) > 11:
        return (111, 106, 94)
    return (grey*.78, grey*.89, grey)


def cuticle(x, y):
    # Mineral flecks form in short growth strata parallel to the plate.
    block = (x//7*17 + y//3*13) % 19
    fiber = (y + (x//12)%4) % 13
    tone = 207 + (block-9)*1.3 + (18 if fiber==2 else -22 if fiber==1 else 0)
    return (tone, tone, tone)


def mantle(x, y):
    # A 48-texel atlas for each cell, laid out in one 5-by-5 sheet.
    ix, iz = x//48-2, y//48-2
    u, v = x%48-23.5, y%48-23.5
    edge = max(abs(u),abs(v))
    shade = 41 + (ix*11 + iz*5)%5 + ((x//12+y//3)%4)
    if edge > 22.4:
        shade = 23
    elif edge > 20.4:
        shade += 8 if (v < -20 or u < -20) else -7
    # Subtle crystalline striations read at grazing angles without obscuring the offerings.
    if (int((x+2*(y//7))/8)+y)%23 == 2:
        shade += 4
    shade += seat(u,v,12)
    if 13.4 < edge < 14.7 and min(abs(u),abs(v)) > 10:
        shade += 14
    return (shade*.83, shade*.94, shade*1.03)


def box(lo, hi, material, top=None):
    faces = {d: {"texture": "#" + (top if d == "up" and top else material)} for d in FACES}
    return {"from": lo, "to": hi, "faces": faces}


def model(part):
    pieces = [box([1,0,1],[15,2,15],"shell"), box([2,2,2],[14,8,14],"tissue"),
              box([.5,7,.5],[15.5,9,15.5],"shell"), box([0,9,0],[16,12,16],"shell","pad")]
    if part == "core":
        pieces += [box([4.5,12,4.5],[11.5,12.7,11.5],"shell"),
                   box([6,12.7,6],[10,13,10],"tissue")]
    if part in ("channel", "bed"):
        for x in (1.5,12.5):
            pieces.append(box([x,12,2],[x+2,12.7,14],"shell" if part=="bed" else "bone"))
    if part == "anchor":
        for x in (1,12):
            for z in (1,12):
                pieces.append(box([x,11.5,z],[x+3,13,z+3],"bone"))
    return {"textures": {"shell":"blacklight:block/weave_enamel","tissue":"blacklight:block/weave_sinew",
                         "bone":"blacklight:block/weave_bone","pad":"blacklight:block/weave_"+part,
                         "particle":"blacklight:block/weave_enamel"},
            "parent":"minecraft:block/block","elements":pieces}


if __name__ == "__main__":
    texture("weave_mantle",mantle,240)
    texture("weave_cuticle",cuticle,128)
    texture("weave_enamel",enamel)
    texture("weave_sinew",mycelium)
    texture("weave_bone",bone)
    for part in PARTS:
        name="weave_"+part
        texture(name,lambda x,y: bed_surface(part,x,y))
        save_json(ASSETS/"models/block"/(name+".json"),model(part))
        save_json(ASSETS/"blockstates"/(name+".json"),{"variants":{"":{"model":"blacklight:block/"+name}}})
        save_json(ASSETS/"models/item"/(name+".json"),{"parent":"blacklight:block/"+name})
