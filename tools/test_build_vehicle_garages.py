"""Exercise serialized garage assets without Gradle or a Minecraft world."""
from pathlib import Path
import gzip
import json
import math
import struct
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read_nbt(path):
    raw = memoryview(gzip.decompress(path.read_bytes()))
    offset = 0

    def take(count):
        nonlocal offset
        result = raw[offset:offset + count]
        offset += count
        return result

    def number(fmt):
        return struct.unpack(fmt, take(struct.calcsize(fmt)))[0]

    def string():
        return bytes(take(number(">H"))).decode("utf8")

    def payload(kind):
        if kind in (1, 3):
            return number({1: ">b", 3: ">i"}[kind])
        if kind == 8:
            return string()
        if kind == 9:
            child, size = number(">b"), number(">i")
            return [payload(child) for _ in range(size)]
        if kind == 10:
            result = {}
            while (child := number(">b")) != 0:
                name = string()
                result[name] = payload(child)
            return result
        raise AssertionError("Unexpected NBT type " + str(kind))

    assert number(">b") == 10
    string()
    result = payload(10)
    assert offset == len(raw)
    return result


class VehicleGarageAssetTests(unittest.TestCase):
    def generate(self, loader):
        scratch = ROOT / "build/vehicle-garage-asset-checks"
        scratch.mkdir(parents=True, exist_ok=True)
        temp = tempfile.TemporaryDirectory(prefix=loader + "-", dir=scratch)
        self.addCleanup(temp.cleanup)
        destination = Path(temp.name)
        result = subprocess.run(
            [sys.executable, "-X", "utf8", str(ROOT / "tools/build_vehicle_garages.py"),
             "--loader", loader, "--output", str(destination)],
            capture_output=True, text=True, encoding="utf8")
        self.assertEqual(0, result.returncode,
                         "Garage generator must produce usable assets: " + result.stderr)
        resources = destination / "resources/data/blacklight"
        metadata = json.loads((resources / "military/vehicle_garages.json").read_text(encoding="utf8"))
        structures = resources / ("structures" if loader == "forge" else "structure")
        return metadata, structures

    @staticmethod
    def blocks(template):
        return {tuple(block["pos"]): (template["palette"][block["state"]]["Name"], block.get("nbt"))
                for block in template["blocks"]}

    def test_roofed_three_bays_allow_full_vehicle_exit_through_seven_block_gates(self):
        metadata, structures = self.generate("neoforge")
        self.assertEqual(1, metadata["schemaVersion"])
        for garage in metadata["garages"]:
            template = read_nbt(structures / (garage["template"].split(":")[1] + ".nbt"))
            self.assertEqual([31, 10, 27], template["size"])
            blocks = self.blocks(template)
            self.assertEqual(3, len(garage["bays"]))
            self.assertEqual([0, 0, 0, 30, 9, 26], garage["terrainBounds"])
            for x in range(31):
                for z in range(27):
                    self.assertNotEqual("minecraft:air", blocks[x, 0, z][0])
                    self.assertNotEqual("minecraft:air", blocks[x, 9, z][0])
            for bay in garage["bays"]:
                center = bay["center"]
                self.assertEqual(12.5, center[2])
                cx = math.floor(center[0])
                for z in range(7, 26):
                    for x in range(cx - 4, cx + 5):
                        for y in range(1, 8):
                            self.assertEqual("minecraft:air", blocks[x, y, z][0])
                for z in range(7, 27):
                    # A 3.4 x 6.5 hull fits this supported straight corridor.
                    for x in range(cx - 2, cx + 3):
                        for y in range(1, 5):
                            self.assertEqual("minecraft:air", blocks[x, y, z][0])
                for x in range(cx - 3, cx + 4):
                    for y in range(1, 8):
                        self.assertEqual("minecraft:air", blocks[x, y, 26][0])

    def test_tank_central_bay_clears_the_actual_long_barrel_turning_circle(self):
        metadata, structures = self.generate("neoforge")
        for garage in metadata["garages"]:
            blocks = self.blocks(read_nbt(structures / (garage["template"].split(":")[1] + ".nbt")))
            tank = next(bay for bay in garage["bays"] if bay["chassis"] == "tank")
            self.assertEqual([15.5, 1, 12.5], tank["center"])
            # Current authoring muzzle is 4.9 blocks from the turret origin;
            # cover its 0.13 radius and every horizontal angle independently.
            for degree in range(360):
                angle = math.radians(degree)
                x = math.floor(15.5 + math.sin(angle) * 5.04)
                z = math.floor(12.5 + math.cos(angle) * 5.04)
                for y in range(1, 8):
                    self.assertEqual("minecraft:air", blocks[x, y, z][0])

    def test_real_supply_items_are_finite_and_use_each_loader_item_nbt(self):
        for loader in ("neoforge", "forge"):
            metadata, structures = self.generate(loader)
            for garage in metadata["garages"]:
                template = read_nbt(structures / (garage["template"].split(":")[1] + ".nbt"))
                blocks = self.blocks(template)
                self.assertEqual([], template["entities"])
                self.assertEqual(3, len(garage["supplyContainers"]))
                self.assertFalse(garage["lootRefresh"])
                totals = {}
                for container in garage["supplyContainers"]:
                    kind, nbt = blocks[tuple(container["position"])]
                    self.assertEqual("minecraft:chest", kind)
                    self.assertEqual("minecraft:chest", nbt["id"])
                    self.assertNotIn("LootTable", nbt)
                    self.assertEqual(len(nbt["Items"]), len({item["Slot"] for item in nbt["Items"]}))
                    for item in nbt["Items"]:
                        count = item["Count" if loader == "forge" else "count"]
                        self.assertGreater(count, 0)
                        self.assertLessEqual(count, 64)
                        totals[item["id"]] = totals.get(item["id"], 0) + count
                self.assertTrue(all(item["id"] != "blacklight:military_fuel_can"
                                    for container in garage["supplyContainers"] for item in container["items"]))
                self.assertEqual({"blacklight:vehicle_repair_parts": 12,
                                  "blacklight:autocannon_round": 120,
                                  "blacklight:rocket_round": 4,
                                  "blacklight:tank_he_shell": 12,
                                  "blacklight:tank_ap_shell": 8,
                                  "blacklight:machine_gun_round": 240}, totals)
                self.assertEqual(3465 if loader == "forge" else 3955, template["DataVersion"])

    def test_generation_is_deterministic_and_does_not_touch_existing_base_assets(self):
        metadata, first = self.generate("neoforge")
        _, second = self.generate("neoforge")
        self.assertEqual(2, len(metadata["garages"]))
        self.assertEqual({"military", "blackwatch"}, {g["faction"] for g in metadata["garages"]})
        self.assertEqual({"vehicle_garage_military.nbt", "vehicle_garage_blackwatch.nbt"},
                         {path.name for path in first.iterdir()})
        for path in first.iterdir():
            self.assertEqual(path.read_bytes(), (second / path.name).read_bytes())


if __name__ == "__main__":
    unittest.main()
