"""Derive shoreline levels, enclosed lowlands and urban road grades from the bundled atlas."""
from array import array
from base64 import b64encode
from collections import deque
import gzip
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/data/blacklight/worldmap"
SEA, STEP, TILE = 63, 32, 1024
MISSING = -32768


def build(region):
    directory = ROOT / region
    cells = [(item["x"], item["z"]) for item in json.loads((directory / "index.json").read_bytes())["tiles"]]
    x0, z0 = min(x for x, _ in cells) * 32 - 1, min(z for _, z in cells) * 32 - 1
    width = max(x for x, _ in cells) * 32 + 34 - x0
    count = width * (max(z for _, z in cells) * 32 + 34 - z0)
    elevation = array("h", [MISSING]) * count
    shore = {}
    junctions = {}
    ends = {}
    for tx, tz in cells:
        with gzip.open(directory / f"{tx}_{tz}.json.gz") as stream:
            tile = json.load(stream)
        heights = tile["e"]
        origin = (tz * 32 - z0) * width + tx * 32 - x0
        for row in range(33):
            start = origin + row * width
            elevation[start:start+33] = array("h", heights[row*33:row*33+33])
        # Atlas cuts are buffered 64 blocks outside their owner tile. Sample only its true shoreline.
        for feature in tile["f"]:
            if feature["k"] in (6, 7) and feature["l"] > 0:
                for line in feature["s"]:
                    points = list(zip(line[::2], line[1::2]))
                    for i, (x, z) in enumerate(points):
                        if not (tx*TILE <= x < (tx+1)*TILE and tz*TILE <= z < (tz+1)*TILE):
                            continue
                        key = (feature["k"], feature["l"], x, z)
                        adjacent = junctions.setdefault(key, set())
                        if i:
                            adjacent.add(points[i-1])
                        if i+1 < len(points):
                            adjacent.add(points[i+1])
                        if i == 0 or i+1 == len(points):
                            ends.setdefault(feature["id"], set()).add(key)
            if feature["k"] != 4:
                continue
            ident = feature["id"]
            for polygon in feature["p"]:
                for ring in polygon:
                    for i in range(2, len(ring), 2):
                        ax, az, bx, bz = ring[i-2:i+2]
                        steps = max(1, math.ceil(math.hypot(bx-ax, bz-az) / 4))
                        for step in range(steps+1):
                            x = (ax + (bx-ax)*step/steps - tx*TILE)/STEP
                            z = (az + (bz-az)*step/steps - tz*TILE)/STEP
                            if not (0 <= x < 32 and 0 <= z < 32):
                                continue
                            ix, iz = int(x), int(z)
                            a, b = x-ix, z-iz
                            n = iz*33+ix
                            y = math.floor((heights[n]*(1-a)+heights[n+1]*a)*(1-b)
                                           +(heights[n+33]*(1-a)+heights[n+34]*a)*b)
                            if y < feature["y"] and (ident not in shore or y < shore[ident][0]):
                                shore[ident] = (y, origin + round(z)*width + round(x))
    # Only low grid points connected to the unmapped sea can create implicit ocean.
    wet = bytearray(count)
    pending = deque()
    for i, value in enumerate(elevation):
        if value != MISSING and value <= SEA:
            if any(elevation[i+d] == MISSING for d in (-1, 1, -width, width)):
                wet[i] = 2
                pending.append(i)
            else:
                wet[i] = 1
    while pending:
        i = pending.popleft()
        for d in (-1, 1, -width, width):
            if wet[i+d] == 1:
                wet[i+d] = 2
                pending.append(i+d)
    water = {str(ident): max(SEA if wet[point] == 2 else 4, y)
             for ident, (y, point) in shore.items()}
    profiles = {}
    for tx, tz in cells:
        origin = (tz * 32 - z0) * width + tx * 32 - x0
        dry = bytearray(128)
        cuts = []
        for row in range(33):
            for col in range(33):
                p = origin+row*width+col
                if row < 32 and col < 32:
                    corners = (wet[p], wet[p+1], wet[p+width], wet[p+width+1])
                    if 1 in corners and 2 not in corners:
                        k = row*32+col
                        dry[k//8] |= 1 << (k % 8)
                # Opposite samples preserve a constant slope. Suppress isolated crests only on road approaches.
                ring = [elevation[p+d] for d in (-width-1,-width,-width+1,-1,1,width-1,width,width+1)]
                if MISSING not in ring:
                    med = sorted(ring)[3:5]
                    drop = min(elevation[p]-max(med)-3,
                               elevation[p]-min(max(ring[0],ring[7]),max(ring[1],ring[6]),
                                                max(ring[2],ring[5]),max(ring[3],ring[4]))-3)
                    if drop >= 3:
                        cuts.extend((row*33+col, drop))
        if any(dry) or cuts:
            profile = {}
            if any(dry):
                profile["dry"] = b64encode(dry).decode("ascii")
            if cuts:
                profile["cuts"] = cuts
            profiles[f"{tx}_{tz}"] = profile
    ramps = {}
    for ident, nodes in ends.items():
        terminals = sorted((x,z) for kind,layer,x,z in nodes if len(junctions[kind,layer,x,z]) == 1)
        if terminals:
            ramps[str(ident)] = [v for point in terminals for v in point]
    result = {"water": water, "tiles": profiles, "ramps": ramps}
    dest = directory / "surface_profiles.json"
    dest.write_text(json.dumps(result, sort_keys=True, separators=(",", ":"))+"\n", encoding="utf-8", newline="\n")
    print(region, len(water), "shore levels,", len(profiles), "tiles,", dest.stat().st_size, "bytes", flush=True)


if __name__ == "__main__":
    for name in json.loads((ROOT / "atlas.json").read_bytes())["regions"]:
        build(name)
