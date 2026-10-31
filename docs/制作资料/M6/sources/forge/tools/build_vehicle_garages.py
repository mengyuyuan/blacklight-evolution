"""Build original roofed 31x27 garages and finite chest stock (stdlib only).

Default output is build/vehicle-garages. --install writes only the dedicated
metadata and two templates under src/main/resources, never worlds or old bases.
Terrain approval, registration and one-time vehicle dispatch belong to the server.
"""
from __future__ import annotations
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
SIZE = [31, 10, 27]
BAYS = (
    {"id": "truck", "chassis": "truck", "center": [5.5, 1, 12.5],
     "repairBounds": [1, 1, 6, 10, 8, 20], "container": [5, 1, 2], "gateBounds": [2, 1, 26, 9, 8, 27]},
    {"id": "tank", "chassis": "tank", "center": [15.5, 1, 12.5],
     "repairBounds": [10, 1, 6, 21, 8, 20], "container": [15, 1, 2], "gateBounds": [12, 1, 26, 19, 8, 27]},
    {"id": "apc", "chassis": "apc", "center": [25.5, 1, 12.5],
     "repairBounds": [21, 1, 6, 30, 8, 20], "container": [25, 1, 2], "gateBounds": [22, 1, 26, 29, 8, 27]},
)
FUEL_STOCK = {chassis: (("military_fuel_can", 4),) for chassis in ("truck", "tank", "apc")}
STOCK = {
    "truck": (("vehicle_repair_parts", 4),),
    "tank": (("vehicle_repair_parts", 4),
             ("tank_he_shell", 12), ("tank_ap_shell", 8), ("machine_gun_round", 64),
             ("machine_gun_round", 64), ("machine_gun_round", 64), ("machine_gun_round", 48)),
    "apc": (("vehicle_repair_parts", 4),
            ("autocannon_round", 64), ("autocannon_round", 56), ("rocket_round", 4)),
}


def nbt_string(value):
    data = value.encode("utf8")
    return struct.pack(">H", len(data)) + data


def nbt_payload(kind, value):
    if kind in (1, 3):
        return struct.pack({1: ">b", 3: ">i"}[kind], value)
    if kind == 8:
        return nbt_string(value)
    if kind == 9:
        child, entries = value
        return bytes([child]) + struct.pack(">i", len(entries)) + b"".join(nbt_payload(child, v) for v in entries)
    if kind == 10:
        return b"".join(bytes([child]) + nbt_string(name) + nbt_payload(child, v)
                        for name, (child, v) in value.items()) + b"\0"
    raise ValueError("Unsupported NBT type " + str(kind))


class Garage:
    def __init__(self, faction, forge):
        self.faction, self.forge = faction, forge
        self.name = "vehicle_garage_" + faction
        self.blocks = {}

    def put(self, x, y, z, name, props=None, nbt=None):
        if not (0 <= x < SIZE[0] and 0 <= y < SIZE[1] and 0 <= z < SIZE[2]):
            raise ValueError("Garage block outside dedicated footprint")
        self.blocks[x, y, z] = ("minecraft:" + name, props or {}, nbt)

    def fill(self, x1, y1, z1, x2, y2, z2, name):
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1):
                    self.put(x, y, z, name)

    def layout(self):
        dark = "gray_concrete" if self.faction == "blackwatch" else "light_gray_concrete"
        accent = "red_concrete" if self.faction == "blackwatch" else "green_terracotta"
        # Explicit air requires the production preview to approve an empty site.
        self.fill(0, 0, 0, 30, 9, 26, "air")
        self.fill(0, 0, 0, 30, 0, 26, "smooth_stone")
        self.fill(0, 1, 0, 30, 8, 0, dark)
        self.fill(0, 1, 1, 0, 8, 26, dark)
        self.fill(30, 1, 1, 30, 8, 26, dark)
        self.fill(1, 1, 26, 29, 8, 26, dark)
        self.fill(0, 9, 0, 30, 9, 26, "deepslate_tiles")
        self.fill(0, 8, 0, 30, 8, 0, accent)
        self.fill(0, 8, 26, 30, 8, 26, accent)
        for center in (5, 15, 25):
            self.fill(center - 3, 1, 26, center + 3, 7, 26, "air")
            for edge in (center - 4, center + 4):
                self.fill(edge, 0, 6, edge, 0, 25, "yellow_concrete")
            for z in (6, 19):
                self.fill(center - 3, 0, z, center + 3, 0, z, "white_concrete")
            for z in (7, 18):
                self.put(center, 8, z, "sea_lantern")
        for bay in BAYS:
            x, y, z = bay["container"]
            count_key, count_type = ("Count", 1) if self.forge else ("count", 3)
            items = [{"Slot": (1, i), "id": (8, "blacklight:" + item), count_key: (count_type, count)}
                     for i, (item, count) in enumerate(STOCK[bay["chassis"]])]
            self.put(x, y, z, "chest", {"facing": "south", "type": "single", "waterlogged": "false"},
                     {"id": (8, "minecraft:chest"), "Items": (9, (10, items))})
            self.put(x - 2, y, z, "crafting_table")
            self.put(x + 2, y, z, "anvil", {"facing": "north"})
        for z in (5, 10, 15, 20):
            for x in (0, 30):
                self.put(x, 5, z, "black_stained_glass")
                self.put(x, 5, z + 1, "black_stained_glass")
        return self

    def metadata(self):
        return {"id": self.name, "template": "blacklight:" + self.name, "faction": self.faction,
                "size": list(SIZE), "terrainBounds": [0, 0, 0, 30, 9, 26], "foundationDepth": 3,
                "boundsConvention": "repairBounds and gateBounds are min-inclusive max-exclusive block boundaries",
                "bayFacingYaw": 0, "bayFacing": "south +Z", "longitudinalLaneWidth": 9,
                "gateWidth": 7, "gateClearanceHeight": 7,
                "roadConnections": [[5.5, 1, 26.5], [15.5, 1, 26.5], [25.5, 1, 26.5]],
                "bays": [dict(bay) for bay in BAYS],
                "supplyContainers": [{"bayId": bay["id"], "position": bay["container"],
                                      "items": [{"id": "blacklight:" + item, "count": count}
                                                for item, count in STOCK[bay["chassis"]]]} for bay in BAYS],
                "entitiesEmbedded": False, "lootRefresh": False, "automaticOldWorldAttachment": False,
                "initialDispatchChassis": ["truck", "apc", "tank"],
                "authorship": "Original Blacklight block layout; no imported commercial game assets."}

    def write_nbt(self, path):
        palette, lookup, blocks = [], {}, []
        for position, (name, props, nbt) in sorted(self.blocks.items()):
            key = name, tuple(sorted(props.items()))
            if key not in lookup:
                lookup[key] = len(palette)
                state = {"Name": (8, name)}
                if props:
                    state["Properties"] = (10, {k: (8, v) for k, v in sorted(props.items())})
                palette.append(state)
            block = {"pos": (9, (3, list(position))), "state": (3, lookup[key])}
            if nbt:
                block["nbt"] = (10, nbt)
            blocks.append(block)
        template = {"DataVersion": (3, 3465 if self.forge else 3955), "size": (9, (3, SIZE)),
                    "palette": (9, (10, palette)), "blocks": (9, (10, blocks)), "entities": (9, (10, []))}
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(gzip.compress(b"\x0a\0\0" + nbt_payload(10, template), mtime=0))
        return {"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                "bytes": path.stat().st_size, "blockCount": len(blocks)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    destination = parser.add_mutually_exclusive_group()
    destination.add_argument("--install", action="store_true")
    destination.add_argument("--output", type=Path)
    parser.add_argument("--loader", choices=("neoforge", "forge"))
    parser.add_argument("--forge", action="store_true", help="Alias for --loader forge")
    args = parser.parse_args()
    if args.forge and args.loader == "neoforge":
        parser.error("--forge conflicts with --loader neoforge")
    forge = args.forge or args.loader == "forge" or (args.loader is None and
        "minecraft_version=1.20.1" in (ROOT / "gradle.properties").read_text(encoding="utf8"))
    out = ROOT / "src/main" if args.install else (args.output or ROOT / "build/vehicle-garages")
    data = out / "resources/data/blacklight"
    structures = data / ("structures" if forge else "structure")
    garages, files = [], []
    for faction in ("military", "blackwatch"):
        garage = Garage(faction, forge).layout()
        garages.append(garage.metadata())
        files.append(garage.write_nbt(structures / (garage.name + ".nbt")))
    metadata = data / "military/vehicle_garages.json"
    metadata.parent.mkdir(parents=True, exist_ok=True)
    metadata.write_text(json.dumps({"schemaVersion": 1,
        "coordinateSystem": "template-local block boundaries; feet at y=1", "garages": garages},
        ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"loader": "forge" if forge else "neoforge", "metadata": str(metadata),
                      "templates": files}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
