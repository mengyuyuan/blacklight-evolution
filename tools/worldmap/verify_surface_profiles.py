"""Verify every shared elevation sample, including diagonal corner coverage and the distant sea."""
import base64
import gzip
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/data/blacklight/worldmap"


def edges(values):
    return (tuple(values[::33]), tuple(values[32::33]), tuple(values[:33]), tuple(values[-33:]))


def verify(region):
    directory = ROOT / region
    index = json.loads((directory / "index.json").read_bytes())
    profiles = json.loads((directory / "surface_profiles.json").read_bytes())
    borders = {}
    for tile in index["tiles"]:
        x, z = tile["x"], tile["z"]
        with gzip.open(directory / f"{x}_{z}.json.gz") as stream:
            borders[x,z] = edges(json.load(stream)["e"])
    for key, data in profiles["fringe"].items():
        coord = tuple(map(int, key.split("_")))
        if coord in borders:
            raise ValueError(f"{region} transition overlaps survey at {coord}")
        elevations = struct.unpack(">1089h", zlib.decompress(base64.b64decode(data)))
        borders[coord] = edges(elevations)
    sea = edges([48]*1089)
    comparisons = 0
    for (x,z), sides in borders.items():
        for side,(dx,dz) in enumerate(((-1,0),(1,0),(0,-1),(0,1))):
            other = x+dx,z+dz
            if other in borders and (dx<0 or dz<0):
                continue
            opposite = borders.get(other,sea)[side^1]
            if sides[side] != opposite:
                sample = next(i for i in range(33) if sides[side][i] != opposite[i])
                raise ValueError(f"{region} {(x,z)} to {other}, sample {sample}: {sides[side][sample]} != {opposite[sample]}")
            comparisons += 33
    print(f"{region}: {len(profiles['fringe'])} transition tiles, {comparisons} matching elevation samples")


if __name__ == "__main__":
    for name in json.loads((ROOT / "atlas.json").read_bytes())["regions"]:
        verify(name)
