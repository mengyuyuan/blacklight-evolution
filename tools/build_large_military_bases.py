"""Build original large military bases, scene blocks and finite loot (stdlib only).

Default output is a staging tree in build/large-military-bases. --install writes
only the new dedicated production assets; it never edits existing facilities or
main registration code. Entity lists are deliberately empty: runtime deployment
owns persisted UUIDs. No original-game assets or worlds are imported.
"""
from __future__ import annotations

import argparse
from collections import deque
import gzip
import hashlib
import json
from pathlib import Path
import random
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
AMMO_CHESTS = [[52, 1, 12], [54, 1, 12]]
SCENE_IDS = ("field_barrier", "sandbag_cover", "military_crate",
             "command_terminal", "communications_antenna", "military_searchlight")


def nbt_string(value):
    data = value.encode("utf8")
    return struct.pack(">H", len(data)) + data


def nbt_payload(kind, value):
    if kind == 1:
        return struct.pack(">b", value)
    if kind == 3:
        return struct.pack(">i", value)
    if kind == 8:
        return nbt_string(value)
    if kind == 9:
        element, entries = value
        return bytes([element]) + struct.pack(">i", len(entries)) + b"".join(
            nbt_payload(element, entry) for entry in entries)
    if kind == 10:
        return b"".join(bytes([kind]) + nbt_string(name) + nbt_payload(kind, val)
                        for name, (kind, val) in value.items()) + b"\0"
    raise ValueError(f"Unsupported NBT kind {kind}")


def json_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf8")


class Base:
    def __init__(self, blackwatch=False):
        self.blackwatch = blackwatch
        self.name = "large_blackwatch_base" if blackwatch else "large_military_base"
        self.width, self.height, self.depth = 65, 18, 81 if blackwatch else 65
        self.blocks = {}
        self.modules, self.guards = [], []
        self.body = "gray_concrete" if blackwatch else "light_gray_concrete"
        self.dark = "polished_blackstone" if blackwatch else "gray_concrete"
        self.accent = "red_concrete" if blackwatch else "green_terracotta"

    def put(self, x, y, z, name, props=None, nbt=None):
        if not (0 <= x < self.width and 0 <= y < self.height and 0 <= z < self.depth):
            raise ValueError(f"{self.name}: out-of-bounds {(x, y, z)}")
        self.blocks[x, y, z] = (name if ":" in name else "minecraft:" + name, props or {}, nbt)

    def fill(self, x1, y1, z1, x2, y2, z2, name, props=None):
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1):
                    self.put(x, y, z, name, props)

    def scene(self, x, y, z, name, facing="north"):
        self.put(x, y, z, "blacklight:" + name, {"facing": facing})

    def chest(self, x, y, z, loot="base_supplies", facing="south"):
        self.put(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"},
                 {"id": (8, "minecraft:chest"), "LootTable": (8, "blacklight:chests/" + loot)})

    def sign(self, x, y, z, lines, facing="south"):
        text = {"messages": (9, (8, [json.dumps({"text": line}, ensure_ascii=False)
                                      for line in (list(lines) + [""] * 4)[:4]])),
                "color": (8, "black"), "has_glowing_text": (1, 0)}
        self.put(x, y, z, "birch_wall_sign", {"facing": facing, "waterlogged": "false"},
                 {"id": (8, "minecraft:sign"), "is_waxed": (1, 1), "front_text": (10, text)})

    def bed(self, x, y, z, color="gray"):
        for dz, part in ((0, "foot"), (-1, "head")):
            self.put(x, y, z + dz, color + "_bed", {"facing": "north", "part": part, "occupied": "false"})

    def guard(self, x, y, z, heavy=False, role="perimeter"):
        self.guards.append({"x": x, "y": y, "z": z, "heavy": heavy, "role": role})

    def shell(self, name, x1, z1, x2, z2, floors=1, door="south", entrance=None):
        roof = floors * 5
        self.fill(x1, 1, z1, x2, roof, z2, self.body)
        self.fill(x1 + 1, 1, z1 + 1, x2 - 1, roof - 1, z2 - 1, "air")
        for y in range(5, roof + 1, 5):
            self.fill(x1, y, z1, x2, y, z2, self.dark)
        # Deep concrete frames, slit windows, broad eaves and floor accent bands.
        for y in range(1, roof, 5):
            for x in range(x1 + 2, x2 - 1):
                if x % 5 not in (0, 1):
                    for z in (z1, z2):
                        self.put(x, y + 2, z, "black_stained_glass")
            for z in range(z1 + 2, z2 - 1):
                if z % 5 not in (0, 1):
                    for x in (x1, x2):
                        self.put(x, y + 2, z, "black_stained_glass")
            self.fill(x1, y + 3, z1, x2, y + 3, z1, self.accent)
            self.fill(x1, y + 3, z2, x2, y + 3, z2, self.accent)
            for x, z in ((x1 + 3, z1 + 3), (x2 - 3, z2 - 3)):
                self.put(x, y + 3, z, "sea_lantern")
        e = entrance if entrance is not None else ((x1 + x2) // 2 if door in ("north", "south") else (z1 + z2) // 2)
        if door in ("south", "north"):
            z = z2 if door == "south" else z1
            self.fill(e - 1, 1, z, e + 1, 3, z, "air")
            target = [e, 1, z - 2 if door == "south" else z + 2]
            self.fill(e - 2, 4, z, e + 2, 4, z, self.dark)
        else:
            x = x1 if door == "west" else x2
            self.fill(x, 1, e - 1, x, 3, e + 1, "air")
            target = [x + 2 if door == "west" else x - 2, 1, e]
            self.fill(x, 4, e - 2, x, 4, e + 2, self.dark)
        self.modules.append({"name": name, "bounds": [x1, 0, z1, x2, roof, z2],
                             "entry": target, "floors": floors})
        return roof

    def roof_rail(self, x1, z1, x2, z2, y):
        props = {"north": "false", "east": "true", "south": "false", "west": "true", "waterlogged": "false"}
        for z in (z1, z2):
            self.fill(x1, y, z, x2, y, z, "iron_bars", props)
        props = {"north": "true", "east": "false", "south": "true", "west": "false", "waterlogged": "false"}
        for x in (x1, x2):
            self.fill(x, y, z1, x, y, z2, "iron_bars", props)

    def tower(self, x, z):
        for px, pz in ((x, z), (x + 4, z), (x, z + 4), (x + 4, z + 4)):
            self.fill(px, 1, pz, px, 10, pz, self.dark)
        self.fill(x, 7, z, x + 4, 7, z + 4, self.dark)
        self.roof_rail(x, z, x + 4, z + 4, 8)
        self.fill(x, 11, z, x + 4, 11, z + 4, self.accent)
        # Climbable shaft, open landing, two-block headroom at the observation deck.
        self.fill(x + 1, 1, z, x + 1, 7, z, self.dark)
        for y in range(1, 8):
            self.put(x + 1, y, z + 1, "ladder", {"facing": "south", "waterlogged": "false"})
        self.scene(x + 3, 8, z + 2, "military_searchlight", "south")

    def layout(self):
        d = self.depth
        # Explicit air makes one template placement self-contained on a cleared city lot.
        self.fill(0, 0, 0, 64, 17, d - 1, "air")
        self.fill(0, 0, 0, 64, 0, d - 1, "polished_andesite")
        self.fill(28, 0, 21, 36, 0, d - 1, "gray_concrete")
        self.fill(5, 0, 23, 60, 0, 27, "gray_concrete")
        self.fill(26, 0, 4, 43, 0, 22, "gray_concrete")
        for z in range(22, d):
            for x in (28, 36):
                self.put(x, 0, z, "yellow_concrete" if z % 4 < 2 else "gray_concrete")
        for x in (0, 64):
            self.fill(x, 1, 0, x, 2, d - 1, self.body)
            self.fill(x, 3, 0, x, 4, d - 1, "iron_bars", {"north": "true", "south": "true", "east": "false", "west": "false", "waterlogged": "false"})
        for z in (0, d - 1):
            self.fill(0, 1, z, 64, 2, z, self.body)
            self.fill(0, 3, z, 64, 4, z, "iron_bars", {"north": "false", "south": "false", "east": "true", "west": "true", "waterlogged": "false"})
        self.fill(29, 1, d - 1, 35, 5, d - 1, "air")
        for x in (27, 37):
            self.fill(x, 1, d - 1, x, 7, d - 1, self.dark)
            self.put(x, 5, d - 2, "sea_lantern")
        self.fill(27, 7, d - 1, 37, 7, d - 1, self.dark)
        self.fill(29, 7, d - 1, 35, 7, d - 1, self.accent)
        self.sign(32, 7, d - 2, ["BLACKWATCH" if self.blackwatch else "MILITARY", "CONTROL ZONE" if self.blackwatch else "GARRISON 04", "AUTHORIZED", "PERSONNEL"], "north")
        for x, z in ((2, 2), (58, d - 7), (2, d - 7)):
            self.tower(x, z)
        # TWO-STOREY COMMAND, open south vestibule and climbable roof access.
        self.shell("command", 8, 6, 25, 21, 2, entrance=17)
        self.roof_rail(8, 6, 25, 21, 11)
        for y in range(1, 11):
            self.put(23, y, 8, "ladder", {"facing": "west", "waterlogged": "false"})
            self.put(24, y, 8, self.dark)
        for floor in (1, 6):
            for x in (11, 13, 15):
                self.put(x, floor, 9, "smooth_stone")
                self.scene(x, floor + 1, 9, "command_terminal", "south")
            self.fill(13, floor, 13, 19, floor, 14, "smooth_stone")
            for x in (13, 16, 19):
                self.put(x, floor + 1, 13, "blue_carpet")
            self.chest(10, floor, 19)
        self.scene(11, 11, 9, "communications_antenna")
        mast = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
        cross = {"north": "false", "south": "false", "east": "true", "west": "true", "waterlogged": "false"}
        self.fill(11, 12, 9, 11, 16, 9, "iron_bars", mast)
        self.fill(8, 14, 9, 14, 14, 9, "iron_bars", cross)
        self.put(11, 17, 9, "redstone_lamp", {"lit": "true"})
        self.fill(15, 11, 8, 18, 12, 10, self.dark)
        self.fill(15, 13, 8, 18, 13, 10, "iron_trapdoor", {"facing": "north", "half": "bottom", "open": "false", "powered": "false", "waterlogged": "false"})
        for x in (12, 15, 18):
            self.scene(x, 11, 19, "sandbag_cover")
        self.scene(21, 11, 17, "military_searchlight", "south")
        self.sign(17, 4, 22, ["01 / COMMAND", "OPERATIONS", "RADIO + BRIEF", "ROOF ACCESS"], "south")
        # INDEPENDENT AMMUNITION BUNKER. Inventory positions are a runtime contract.
        self.shell("ammunition", 45, 5, 60, 18, entrance=52)
        self.fill(45, 6, 5, 60, 6, 18, "smooth_stone_slab", {"type": "bottom", "waterlogged": "false"})
        for x in (46, 50, 54, 58):
            self.fill(x, 6, 6, x, 6, 17, "smooth_stone")
        for x in (47, 49, 56, 58):
            self.scene(x, 1, 8, "military_crate")
            self.scene(x, 2, 8, "military_crate")
        for x, y, z in AMMO_CHESTS:
            self.chest(x, y, z, "base_ammunition")
        for x in (46, 59):
            self.fill(x, 1, 19, x, 2, 21, "sandstone")
        self.sign(52, 4, 19, ["02 / AMMO", "LIVE ORDNANCE", "FINITE STOCK", "NO OPEN FLAME"], "south")
        # TWO-STOREY BARRACKS. Three-wide external concrete stairs serve both roof
        # observation and floor 2 via a landing cut into its east wall.
        self.shell("barracks", 8, 29, 24, 47, 2, "east", 44)
        self.roof_rail(8, 29, 24, 47, 11)
        for step in range(11):
            z = 30 + step
            if step:
                self.fill(25, 0, z, 27, step - 1, z, self.dark)
            self.fill(25, step, z, 27, step, z, "stone_brick_stairs", {"facing": "south", "half": "bottom", "shape": "straight", "waterlogged": "false"})
        self.fill(24, 6, 35, 24, 8, 37, "air")
        self.fill(24, 11, 39, 24, 12, 41, "air")
        for floor in (1, 6):
            for x in (10, 13, 19, 22):
                for z in (33, 38, 43):
                    self.bed(x, floor, z, "gray" if self.blackwatch else "green")
            self.chest(10, floor, 45)
            self.chest(13, floor, 45)
            self.put(22, floor, 31, "crafting_table")
        self.scene(11, 11, 31, "sandbag_cover")
        self.scene(21, 11, 43, "military_searchlight", "east")
        for z in (35, 40):
            self.fill(10, 11, z, 12, 11, z + 2, self.body)
            self.fill(10, 12, z, 12, 12, z + 2, "iron_trapdoor", {"facing": "east", "half": "bottom", "open": "false", "powered": "false", "waterlogged": "false"})
        self.sign(25, 4, 44, ["03 / BARRACKS", "24 BEDS", "STAIRS EAST", "ROOF WATCH"], "east")
        # ARMORY. Actual weapon loot is distinct from decorative crate/rack scenery.
        self.shell("armory", 43, 27, 59, 42, door="west", entrance=34)
        for z in (29, 31, 38, 40):
            self.scene(57, 1, z, "military_crate", "west")
        self.chest(46, 1, 29, "base_armory", "south")
        self.chest(46, 1, 39, "base_armory", "north")
        self.fill(50, 1, 31, 50, 1, 38, "smooth_stone")
        self.put(50, 2, 31, "grindstone", {"face": "floor", "facing": "north"})
        self.put(50, 2, 38, "smithing_table")
        self.scene(54, 1, 35, "command_terminal", "west")
        self.sign(42, 4, 34, ["04 / ARMORY", "ISSUE + REPAIR", "WEAPON STOCK", "CONTROLLED"], "west")
        # Open-air vehicle / landing pad, deliberately not a functional aircraft.
        self.fill(29, 0, 6, 41, 0, 18, "smooth_stone")
        for x in (29, 41):
            self.fill(x, 0, 6, x, 0, 18, "white_concrete")
        for z in (6, 18):
            self.fill(29, 0, z, 41, 0, z, "white_concrete")
        for x in (33, 37):
            self.fill(x, 0, 9, x, 0, 15, "yellow_concrete")
        self.fill(33, 0, 12, 37, 0, 12, "yellow_concrete")
        # SERVICE / SCIENCE side-wing; BW has two floors and sealed glass cells.
        if self.blackwatch:
            self.shell("medical_isolation", 8, 52, 25, 67, 2, "east", 61)
            self.shell("containment_research", 43, 48, 59, 67, 2, "west", 58)
            for y in (1, 6):
                for x in (11, 15, 19):
                    self.bed(x, y, 56, "white")
                self.chest(10, y, 64, "base_medical")
                self.scene(20, y, 64, "command_terminal", "north")
                for z in (51, 63):
                    self.fill(52, y, z, 56, y + 2, z, "light_blue_stained_glass")
                    self.fill(56, y, z, 56, y + 2, z + 2, "light_blue_stained_glass")
                    self.put(54, y, z + 1, "cauldron")
                self.scene(46, y, 50, "command_terminal", "south")
                self.scene(46, y, 65, "military_crate")
                self.chest(49, y, 65, "base_supplies")
            # Two independent shafts, with cleared floor and roof holes.
            for x, z in ((23, 54), (57, 50)):
                for y in range(1, 11):
                    self.put(x + 1, y, z, self.dark)
                    self.put(x, y, z, "ladder", {"facing": "west", "waterlogged": "false"})
            self.roof_rail(8, 52, 25, 67, 11)
            self.roof_rail(43, 48, 59, 67, 11)
            self.fill(14, 11, 56, 16, 11, 62, "white_concrete")
            self.fill(12, 11, 58, 18, 11, 60, "white_concrete")
            for x, z in ((46, 53), (49, 53), (46, 63), (49, 63)):
                self.fill(x, 11, z, x + 1, 13, z + 1, self.body)
                self.fill(x, 14, z, x + 1, 14, z + 1, "iron_trapdoor", {"facing": "south", "half": "bottom", "open": "false", "powered": "false", "waterlogged": "false"})
            self.fill(53, 11, 55, 55, 11, 62, "light_blue_stained_glass")
            self.scene(54, 12, 64, "communications_antenna")
            self.fill(8, 4, 52, 25, 4, 52, "white_concrete")
            self.fill(43, 4, 48, 59, 4, 48, "red_concrete")
            self.sign(42, 4, 58, ["06 / RESEARCH", "BIO-CONTAINMENT", "DECORATIVE LAB", "RESTRICTED"], "west")
            self.sign(26, 4, 61, ["05 / ISOLATION", "MEDICAL STOCK", "NO EXPERIMENT", "AUTOMATION"], "east")
            for x in (21, 39):
                self.fill(x, 1, 72, x + 4, 4, 76, self.body)
                self.fill(x + 1, 1, 73, x + 3, 3, 75, "air")
                self.fill(x + 1, 1, 72, x + 3, 2, 72, "air")
                self.scene(x + 2, 1, 75, "command_terminal")
                self.put(x + 2, 3, 74, "sea_lantern")
                self.fill(x, 5, 72, x + 4, 5, 76, self.accent)
        else:
            self.shell("medical", 8, 51, 23, 59, door="north", entrance=16)
            for x in (10, 13, 20):
                self.bed(x, 1, 56, "white")
            self.chest(10, 1, 57, "base_medical")
            self.put(21, 1, 57, "cauldron")
            self.sign(16, 4, 50, ["05 / MEDICAL", "FIELD AID", "FINITE SUPPLY", "GARRISON 04"], "north")
            self.fill(14, 6, 53, 16, 6, 57, "white_concrete")
            self.fill(12, 6, 54, 18, 6, 56, "white_concrete")
            # Open logistics canopy, readable structural columns rather than box rooms.
            for x, z in ((44, 49), (57, 49), (44, 58), (57, 58)):
                self.fill(x, 1, z, x, 4, z, self.dark)
            self.fill(43, 5, 48, 58, 5, 59, "green_concrete")
            for x in (47, 50, 53):
                self.scene(x, 1, 56, "military_crate")
            self.chest(45, 1, 52)
            self.chest(55, 1, 52)
            self.put(50, 4, 52, "sea_lantern")
            self.modules.append({"name": "logistics_canopy", "bounds": [43, 0, 48, 58, 5, 59], "entry": [50, 1, 51], "floors": 1})
        # Distributed cover does not obstruct the seven-block gate or central roads.
        for x, z, facing in ((23, 25, "north"), (41, 25, "north"), (26, d - 12, "east"),
                              (38, d - 12, "west"), (39, 45, "north"), (6, 25, "north")):
            self.scene(x, 1, z, "field_barrier", facing)
        for x, z in ((9, 24), (55, 24), (25, d - 4), (39, d - 4)):
            self.scene(x, 1, z, "sandbag_cover")
        # Yard floodlights, separate communication silhouettes and red/olive identifiers.
        for x, z in ((27, 22), (38, 44), (6, 49), (61, 23)):
            self.fill(x, 1, z, x, 6, z, self.dark)
            self.scene(x, 7, z, "military_searchlight", "south")
        self.guard(28, 1, d - 4, role="entry")
        self.guard(36, 1, d - 4, True, "entry")
        self.guard(32, 1, d - 12, role="motor_yard")
        self.guard(32, 1, 29, role="central_lane")
        self.guard(4, 8, 4, role="tower_northwest")
        self.guard(60, 8, d - 5, role="tower_southeast")
        self.guard(4, 8, d - 5, role="tower_southwest")
        self.guard(18, 1, 18, role="command")
        self.guard(18, 6, 18, role="operations")
        self.guard(18, 11, 12, role="command_roof")
        self.guard(50, 1, 15, True, "ammunition")
        self.guard(16, 1, 41, role="barracks")
        self.guard(16, 6, 41, role="barracks_upper")
        self.guard(48, 1, 37, True, "armory")
        self.guard(35, 1, 12, role="landing_pad")
        self.guard(40, 1, 51 if self.blackwatch else 53, role="logistics")
        if self.blackwatch:
            self.guard(47, 1, 55, True, "research")
            self.guard(48, 6, 60, role="research_upper")
            self.guard(20, 1, 59, role="isolation")
            self.guard(20, 6, 59, role="isolation_upper")
        return self

    def metadata(self):
        return {"name": self.name, "faction": "blackwatch" if self.blackwatch else "military",
                "width": self.width, "height": self.height, "depth": self.depth,
                "entrance": [32, 1, self.depth - 1], "entrance_width": 7,
                "ammo_chests": AMMO_CHESTS, "guards": self.guards, "modules": self.modules,
                "command_cores": [[x, y, 9] for y in (2, 7) for x in (11, 13, 15)],
                "entities_embedded": False, "loot_refresh": False}

    def validate(self):
        def name(pos):
            return self.blocks.get(pos, ("minecraft:air", {}, None))[0].split(":")[-1]

        def clear(pos):
            kind = name(pos)
            return kind in ("air", "ladder", "blue_carpet", "birch_wall_sign")

        def walkable(pos):
            x, y, z = pos
            if not (0 <= x < 65 and 1 <= y < self.height - 1 and 0 <= z < self.depth):
                return False
            return clear(pos) and clear((x, y + 1, z)) and (
                name(pos) == "ladder" or not clear((x, y - 1, z)))

        # Traverse the actual blocks, not just a list of intended room coordinates.
        start = (32, 1, self.depth - 1)
        assert walkable(start), "Gate must be open and supported"
        seen, queue = {start}, deque([start])
        while queue:
            x, y, z = queue.popleft()
            candidates = [(x + dx, y + dy, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                          for dy in (0, -1, 1)]
            if name((x, y, z)) == "ladder" or name((x, y - 1, z)) == "ladder":
                candidates.extend(((x, y + 1, z), (x, y - 1, z)))
            for pos in candidates:
                if pos not in seen and walkable(pos):
                    seen.add(pos)
                    queue.append(pos)
        checks = []
        for module in self.modules:
            pos = tuple(module["entry"])
            assert pos in seen, f"Unreachable module {module['name']} at {pos}"
            checks.append(module["name"])
        for guard in self.guards:
            pos = guard["x"], guard["y"], guard["z"]
            assert pos in seen, f"Unreachable guard {guard['role']} at {pos}"
        for pos, (block, props, nbt) in self.blocks.items():
            if block != "minecraft:chest":
                continue
            x, y, z = pos
            assert clear((x, y + 1, z)), f"Chest cannot open at {pos}"
            assert not clear((x, y - 1, z)), f"Unsupported chest at {pos}"
            assert any((x + dx, y, z + dz) in seen for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))), f"Chest unreachable at {pos}"
            assert nbt and nbt["LootTable"][1].startswith("blacklight:chests/base_"), "Only finite dedicated loot tables"
        for x, y, z in AMMO_CHESTS:
            assert name((x, y, z)) == "chest"
            assert self.blocks[x, y, z][2]["LootTable"][1] == "blacklight:chests/base_ammunition"
        for x in range(29, 36):
            assert walkable((x, 1, self.depth - 1)), "At least five clear gate blocks"
        assert self.height <= 20 and len(self.guards) == (20 if self.blackwatch else 16)
        assert all(name((x,y,9)) == "command_terminal" for y in (2,7) for x in (11,13,15)), "Every declared command core must exist in actual authored blocks"
        return {"name": self.name, "dimensions": [65, self.height, self.depth],
                "blocks_including_air": len(self.blocks), "reachable_foot_positions": len(seen),
                "module_access": checks, "guard_positions_reachable": len(self.guards),
                "ammo_chests_openable_and_reachable": True, "entities": 0, "status": "PASS"}

    def write_nbt(self, destination, data_version):
        palette, lookup, entries = [], {}, []
        for pos, (name, props, nbt) in sorted(self.blocks.items()):
            key = name, tuple(sorted(props.items()))
            if key not in lookup:
                lookup[key] = len(palette)
                state = {"Name": (8, name)}
                if props:
                    state["Properties"] = (10, {k: (8, v) for k, v in sorted(props.items())})
                palette.append(state)
            item = {"pos": (9, (3, list(pos))), "state": (3, lookup[key])}
            if nbt:
                item["nbt"] = (10, nbt)
            entries.append(item)
        data = {"DataVersion": (3, data_version), "size": (9, (3, [65, self.height, self.depth])),
                "palette": (9, (10, palette)), "blocks": (9, (10, entries)), "entities": (9, (10, []))}
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(gzip.compress(b"\x0a\0\0" + nbt_payload(10, data), mtime=0))
        return {"path": str(destination), "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
                "compressed_bytes": destination.stat().st_size, "palette_states": len(palette)}


def cuboid(a, b, texture, rotation=None):
    face = {"texture": "#" + texture}
    result = {"from": a, "to": b, "faces": {f: dict(face) for f in ("up", "down", "north", "south", "east", "west")}}
    if rotation:
        result["rotation"] = rotation
    return result


def scene_models():
    return {
        "field_barrier": [cuboid([0, 0, 3], [16, 3, 13], "concrete"), cuboid([1, 3, 4], [15, 7, 12], "concrete"), cuboid([3, 7, 6], [13, 14, 10], "hazard")],
        "sandbag_cover": [cuboid([0, 0, 3], [7.5, 4.5, 13], "sandbag"), cuboid([8, 0, 3], [16, 4.5, 13], "sandbag"), cuboid([0, 4.5, 4], [4, 9, 12], "sandbag"), cuboid([4.5, 4.5, 4], [12, 9, 12], "sandbag"), cuboid([12.5, 4.5, 4], [16, 9, 12], "sandbag")],
        "military_crate": [cuboid([1, 0, 1], [15, 14, 15], "crate"), cuboid([0, 1, 0], [3, 15, 16], "metal"), cuboid([13, 1, 0], [16, 15, 16], "metal"), cuboid([0, 12, 0], [16, 15, 16], "crate"), cuboid([6, 7, 0], [10, 10, 1], "hazard")],
        "command_terminal": [cuboid([2, 0, 3], [14, 2, 14], "metal"), cuboid([5, 2, 6], [11, 9, 12], "metal"), cuboid([0, 8, 1], [16, 10, 15], "metal"), cuboid([1, 10, 8], [15, 16, 11], "screen", {"origin": [8, 10, 8], "axis": "x", "angle": -22.5}), cuboid([3, 10, 2], [13, 10.5, 6], "screen")],
        "communications_antenna": [cuboid([2, 0, 2], [14, 3, 14], "metal"), cuboid([7, 3, 7], [9, 16, 9], "metal"), cuboid([0, 10, 7], [16, 12, 9], "metal"), cuboid([7, 5, 1], [9, 7, 15], "metal"), cuboid([0, 9, 6], [2, 14, 10], "screen"), cuboid([14, 9, 6], [16, 14, 10], "screen")],
        "military_searchlight": [cuboid([3, 0, 3], [13, 2, 13], "metal"), cuboid([7, 2, 7], [9, 8, 9], "metal"), cuboid([3, 7, 5], [13, 9, 12], "metal"), cuboid([2, 9, 3], [14, 16, 13], "metal"), cuboid([3, 10, 2.5], [13, 15, 3], "lamp")],
    }


TEXTURE_COLORS = {"concrete": (132, 137, 132), "hazard": (210, 169, 53), "sandbag": (135, 130, 94),
                  "crate": (67, 85, 68), "metal": (45, 55, 58), "screen": (70, 144, 137), "lamp": (236, 228, 183)}


def write_png(path, pixels, width=32, height=32):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    raw = b"".join(b"\0" + bytes(sum(pixels[y * width:(y + 1) * width], ())) for y in range(height))
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def write_scene_assets(resources):
    models = scene_models()
    for key, elements in models.items():
        textures = {t: "blacklight:block/facility_" + t for t in TEXTURE_COLORS}
        model = {"parent": "minecraft:block/block", "ambientocclusion": True, "textures": {**textures, "particle": textures["metal"]}, "elements": elements}
        json_write(resources / "assets/blacklight/models/block" / (key + ".json"), model)
        variants = {"facing=" + facing: {"model": "blacklight:block/" + key, "y": yaw, "uvlock": True}
                    for facing, yaw in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
        json_write(resources / "assets/blacklight/blockstates" / (key + ".json"), {"variants": variants})
        json_write(resources / "assets/blacklight/models/item" / (key + ".json"), {"parent": "blacklight:block/" + key})
    for name, color in TEXTURE_COLORS.items():
        rng = random.Random(name)
        pixels = []
        for y in range(32):
            for x in range(32):
                c = color
                noise = rng.randrange(-8, 9)
                if name == "hazard" and (x + y) % 16 < 7:
                    c = (37, 43, 42)
                if name == "crate" and (x in (3, 4, 27, 28) or y in (3, 4, 27, 28)):
                    c = (32, 42, 36)
                if name == "sandbag" and (x in (0, 1, 30, 31) or y in (0, 1, 30, 31)):
                    c = (94, 89, 63)
                if name == "metal" and (x % 16 in (2, 3) and y % 16 in (2, 3)):
                    c = (136, 148, 145)
                if name == "screen":
                    c = (17, 44, 43) if x < 3 or x > 28 or y < 3 or y > 28 else ((92, 196, 162) if y % 5 == 1 and x < 24 else (28, 86, 79))
                if name == "lamp" and (x % 8 == 0 or y % 8 == 0):
                    c = (105, 113, 102)
                pixels.append(tuple(max(0, min(255, v + noise)) for v in c))
        write_png(resources / "assets/blacklight/textures/block" / ("facility_" + name + ".png"), pixels)


def loot_entry(item, minimum, maximum=None):
    count = minimum if maximum is None else {"min": minimum, "max": maximum}
    return {"type": "minecraft:item", "name": item, "functions": [{"function": "minecraft:set_count", "count": count}]}


def write_loot(resources, forge):
    folder = "loot_tables" if forge else "loot_table"
    pools = []
    for ammo, minimum, maximum in (("rifle_round", 24, 48), ("machine_gun_round", 24, 48), ("grenade_round", 4, 8),
                                    ("rocket_round", 1, 3), ("missile_round", 1, 2), ("thermobaric_round", 1, 2), ("infection_round", 1, 2)):
        pools.append({"rolls": 1, "entries": [loot_entry("blacklight:" + ammo, minimum, maximum)]})
    tables = {"base_ammunition": pools,
              "base_armory": [{"rolls": 2, "entries": [loot_entry("blacklight:" + i, 1) for i in (
                  "military_rifle", "military_carbine", "military_machine_gun", "grenade_launcher", "rocket_launcher", "missile_launcher", "thermobaric_launcher", "infection_launcher")]}],
              "base_medical": [{"rolls": 1, "entries": [loot_entry("minecraft:golden_apple", 1, 2)]},
                               {"rolls": 1, "entries": [loot_entry("minecraft:cooked_beef", 4, 8)]}],
              "base_supplies": [{"rolls": 1, "entries": [loot_entry("minecraft:bread", 6, 12)]},
                                {"rolls": 1, "entries": [loot_entry("minecraft:iron_ingot", 3, 6)]},
                                {"rolls": 1, "entries": [loot_entry("minecraft:torch", 8, 16)]}]}
    for name, p in tables.items():
        json_write(resources / "data/blacklight" / folder / "chests" / (name + ".json"), {"type": "minecraft:chest", "pools": p})
    # Scene blocks are ordinary mineable props, never storage block entities.
    for name in SCENE_IDS:
        json_write(resources / "data/blacklight" / folder / "blocks" / (name + ".json"),
                   {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "blacklight:" + name}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def registry_source(forge):
    imports = ("import net.minecraftforge.registries.DeferredRegister;\nimport net.minecraftforge.registries.RegistryObject;\nimport net.minecraftforge.registries.ForgeRegistries;\nimport net.minecraftforge.eventbus.api.IEventBus;" if forge else
               "import net.neoforged.neoforge.registries.DeferredRegister;\nimport net.neoforged.neoforge.registries.DeferredBlock;\nimport net.neoforged.neoforge.registries.DeferredItem;\nimport net.neoforged.bus.api.IEventBus;")
    registers = ("private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, \"blacklight\");\n    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, \"blacklight\");" if forge else
                 "private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(\"blacklight\");\n    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(\"blacklight\");")
    rt = "RegistryObject<Block>" if forge else "DeferredBlock<Block>"
    decl = []
    shapes = ((0, 0, 3, 16, 14, 13), (0, 0, 3, 16, 9, 13), (0, 0, 0, 16, 15, 16),
              (0, 0, 1, 16, 16, 15), (6, 0, 6, 10, 16, 10), (2, 0, 3, 14, 16, 13))
    for name, box in zip(SCENE_IDS, shapes):
        lum = 12 if name == "military_searchlight" else 4 if name == "command_terminal" else 0
        decl.append(f'public static final {rt} {name.upper()} = prop("{name}", {lum}, Block.box({", ".join(map(str, box))}));')
    helper = (f"private static {rt} prop(String name, int light, VoxelShape shape) {{\n        {rt} block = BLOCKS.register(name, () -> new FacilityBlock(BlockBehaviour.Properties.of().strength(2.5F, 6).sound(SoundType.METAL).noOcclusion().lightLevel(s -> light), shape));\n        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));\n        return block;\n    }}")
    codec_import = "" if forge else "import com.mojang.serialization.MapCodec;"
    codec_method = "" if forge else '''private static final MapCodec<FacilityBlock> CODEC = simpleCodec(p -> new FacilityBlock(p, Block.box(0, 0, 0, 16, 16, 16)));
        @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }'''
    pathfinding_method = ("""@Override public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.level.pathfinder.PathComputationType type) {
            return type != net.minecraft.world.level.pathfinder.PathComputationType.LAND && super.isPathfindable(state, level, pos, type);
        }""" if forge else """@Override protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
            return type != net.minecraft.world.level.pathfinder.PathComputationType.LAND && super.isPathfindable(state, type);
        }""")
    return f'''package com.codex.blacklight.registry;

{codec_import}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
{imports}

/** Original static scenery. Crates have NO inventory; use vanilla chests for supplies.
 * Terminals have no Blacknet/quest behavior. Searchlights emit light, not a beam AI.
 */
public final class MilitaryFacilityBlocks {{
    {registers}
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "blacklight");
    {chr(10).join("    " + line for line in decl)}

    static {{
        TABS.register("military_facilities", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.blacklight.military_facilities"))
                .icon(() -> new ItemStack(FIELD_BARRIER.get()))
                .displayItems((parameters, output) -> {{
                    output.accept(FIELD_BARRIER.get()); output.accept(SANDBAG_COVER.get());
                    output.accept(MILITARY_CRATE.get()); output.accept(COMMAND_TERMINAL.get());
                    output.accept(COMMUNICATIONS_ANTENNA.get()); output.accept(MILITARY_SEARCHLIGHT.get());
                }}).build());
    }}

    {helper}

    public static void register(IEventBus bus) {{ BLOCKS.register(bus); ITEMS.register(bus); TABS.register(bus); }}
    private MilitaryFacilityBlocks() {{}}

    private static final class FacilityBlock extends HorizontalDirectionalBlock {{
        {codec_method}
        private final VoxelShape northShape;
        private final VoxelShape sideShape;
        FacilityBlock(BlockBehaviour.Properties properties, VoxelShape shape) {{
            super(properties);
            this.northShape = shape;
            var box = shape.bounds();
            this.sideShape = Block.box(box.minZ * 16, box.minY * 16, box.minX * 16,
                                      box.maxZ * 16, box.maxY * 16, box.maxX * 16);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {{ builder.add(FACING); }}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {{ return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }}
        @Override public BlockState rotate(BlockState state, Rotation rotation) {{ return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }}
        @Override public BlockState mirror(BlockState state, Mirror mirror) {{ return state.rotate(mirror.getRotation(state.getValue(FACING))); }}
        // These non-full shapes still collide: the default LAND rule incorrectly calls them open.
        {pathfinding_method}
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {{
            return state.getValue(FACING).getAxis() == Direction.Axis.X ? sideShape : northShape;
        }}
    }}
}}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--install", action="store_true", help="Install only new dedicated assets into src/main")
    parser.add_argument("--output", type=Path, help="Override staging output root")
    parser.add_argument("--forge", action="store_true", help="Generate Forge 1.20.1 layout/API")
    args = parser.parse_args()
    forge = args.forge or "minecraft_version=1.20.1" in (ROOT / "gradle.properties").read_text(encoding="utf8")
    out = ROOT / "src/main" if args.install else (args.output or ROOT / "build/large-military-bases")
    resources = out / "resources"
    validation, meta = [], []
    for blackwatch in (False, True):
        base = Base(blackwatch).layout()
        result = base.validate()
        result.update(base.write_nbt(resources / "data/blacklight" / ("structures" if forge else "structure") / (base.name + ".nbt"), 3465 if forge else 3955))
        validation.append(result)
        meta.append(base.metadata())
        json_write(out / "blueprints" / (base.name + ".json"), base.metadata())
    json_write(resources / "data/blacklight/military/large_bases.json", {"schema": 1, "coordinate_system": "template_local_feet", "bases": meta})
    write_scene_assets(resources)
    write_loot(resources, forge)
    source = out / "java/com/codex/blacklight/registry/MilitaryFacilityBlocks.java"
    source.parent.mkdir(parents=True, exist_ok=True)
    source.write_text(registry_source(forge), encoding="utf8")
    json_write(out / "large-base-validation.json", {"loader": "forge-1.20.1" if forge else "neoforge-1.21.1", "checks": validation})
    json_write(out / "facility-localization.json", {
        "zh_cn": {**{"block.blacklight." + k: v for k, v in zip(SCENE_IDS, ("军用混凝土路障", "沙袋掩体", "军用装饰箱（无库存）", "基地指挥终端", "通信天线", "军用探照灯"))}, "itemGroup.blacklight.military_facilities": "黑光：军事设施"},
        "en_us": {**{"block.blacklight." + k: v for k, v in zip(SCENE_IDS, ("Military Concrete Barrier", "Sandbag Cover", "Military Prop Crate (No Storage)", "Base Command Terminal", "Communications Antenna", "Military Searchlight"))}, "itemGroup.blacklight.military_facilities": "Blacklight: Military Facilities"}})
    print(json.dumps({"output": str(out), "forge": forge, "checks": validation}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
