"""Check public generated physical dimensions rather than a second size algorithm."""
import base64
from collections import Counter
import hashlib
import io
import json
import math
import unittest

from PIL import Image

import build_military_vehicles as builder


class VehicleDimensionsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = json.loads(builder.SOURCE.read_text(encoding="utf8"))
        cls.generated = builder.outputs(cls.source)
        cls.models = json.loads(cls.generated[builder.CLIENT])
        cls.mounts = json.loads(cls.generated[builder.MOUNTS])
        cls.definitions = json.loads(cls.generated[builder.VEHICLES])

    def test_public_outputs_use_full_size_bodies_on_client_and_server(self):
        expected = {"truck": [2.9, 7.5, 3.7], "apc": [3.3, 7.0, 3.3], "tank": [4.0, 8.0, 3.4]}
        for vehicle in self.definitions["vehicles"]:
            name = vehicle["id"]
            self.assertEqual(expected[name], self.models["chassis"][name]["dimensions"], name)
            self.assertEqual(expected[name], [vehicle[key] for key in ("width", "length", "height")], name)
        before = json.loads(builder.VEHICLES.read_text(encoding="utf8"))
        for original, generated in zip(before["vehicles"], self.definitions["vehicles"]):
            self.assertEqual({k: v for k, v in original.items() if k not in ("width", "length", "height")},
                             {k: v for k, v in generated.items() if k not in ("width", "length", "height")})

    def test_source_hash_blockbench_hierarchy_uv_and_embedded_atlases_match_actual_outputs(self):
        source_hash = hashlib.sha256(builder.SOURCE.read_bytes()).hexdigest()
        self.assertEqual(source_hash, self.models["sourceSha256"])
        self.assertEqual(source_hash, self.mounts["sourceSha256"])
        for name, chassis in self.source["chassis"].items():
            project = json.loads(self.generated[builder.ART / (name+".bbmodel")])
            self.assertEqual([part["name"] for part in chassis["parts"]],
                             [element["name"] for element in project["elements"]])
            references, groups = [], []
            def walk(node):
                if isinstance(node, str):
                    references.append(node)
                else:
                    groups.append(node["name"])
                    for child in node["children"]:
                        walk(child)
            for node in project["outliner"]:
                walk(node)
            self.assertEqual(Counter(element["uuid"] for element in project["elements"]), Counter(references))
            self.assertEqual(Counter([bone["name"] for bone in chassis["bones"]]+list(chassis["anchors"])), Counter(groups))
            for element, part in zip(project["elements"], chassis["parts"]):
                self.assertEqual([v*16 for v in part["box"][:3]], element["from"])
                self.assertEqual([(part["box"][i]+part["box"][i+3])*16 for i in range(3)], element["to"])
                for face in element["faces"].values():
                    self.assertTrue(all(0 <= v <= 2048 for v in face["uv"]), part["name"])
            for texture in project["textures"]:
                faction = texture["name"]
                actual = self.generated[builder.ART / "textures" / (faction+".png")]
                self.assertEqual(actual, base64.b64decode(texture["source"].split(",", 1)[1]))
                self.assertEqual(actual, self.generated[builder.TEXTURES / ("military_vehicles_"+faction+".png")])
                with Image.open(io.BytesIO(actual)) as image:
                    image.load()
                    self.assertEqual((2048, 2048), image.size)

    def test_actual_generated_parts_pivots_seats_and_muzzles_share_physical_source(self):
        for name, original in self.source["chassis"].items():
            model = self.models["chassis"][name]
            mount = self.mounts["chassis"][name]
            self.assertEqual(original["anchors"], model["anchors"])
            self.assertEqual(original["anchors"], mount["anchors"])
            pivots = {bone["name"]: bone["pivotBlocks"] for bone in model["bones"]}
            for bone in original["bones"]:
                self.assertEqual(bone["pivot"], pivots[bone["name"]])
            for part, authored in zip(model["parts"], original["parts"]):
                self.assertEqual(authored["name"], part["name"])
                for axis in range(3):
                    actual = part["box"][axis]/16 + pivots[part["parent"]][axis]
                    self.assertAlmostEqual(authored["box"][axis], actual, places=6)
                    self.assertAlmostEqual(authored["box"][axis+3], part["box"][axis+3]/16, places=6)
            for weapon in original["weapons"]:
                barrel = next(part for part in model["parts"] if part["name"] == weapon["muzzlePart"])
                box, pivot = barrel["box"], pivots[barrel["parent"]]
                actual = [pivot[i]+(box[i]+box[i+3]*(1 if i == 2 else .5))/16 for i in range(3)]
                for a, b in zip(actual, mount["anchors"]["muzzle_"+weapon["id"]]["position"]):
                    self.assertAlmostEqual(a, b, places=6)
            self.assertEqual(pivots.get("turret_yaw", [0, 0, 0]), mount["turretPivot"])
            self.assertEqual(pivots.get("gun_pitch", [0, 0, 0]), mount["gunPivot"])

    def test_complete_rest_geometry_fits_current_bays_and_aligned_seven_block_gates(self):
        garages = json.loads((builder.ROOT / "src/main/resources/data/blacklight/military/vehicle_garages.json").read_text(encoding="utf8"))
        for garage in garages["garages"]:
            self.assertEqual([31, 10, 27], garage["size"])
            for bay in garage["bays"]:
                model = self.models["chassis"][bay["chassis"]]
                pivots = {b["name"]: b["pivotBlocks"] for b in model["bones"]}
                bounds = [math.inf]*3 + [-math.inf]*3
                for part in model["parts"]:
                    for i in range(3):
                        low = part["box"][i]/16 + pivots[part["parent"]][i] + bay["center"][i]
                        high = low + part["box"][i+3]/16
                        bounds[i] = min(bounds[i], low)
                        bounds[i+3] = max(bounds[i+3], high)
                for i in range(3):
                    self.assertGreaterEqual(bounds[i], bay["repairBounds"][i]-1e-6)
                    self.assertLessEqual(bounds[i+3], bay["repairBounds"][i+3]+1e-6)
                width, _, height = model["dimensions"]
                self.assertEqual(7, bay["gateBounds"][3]-bay["gateBounds"][0])
                self.assertLess(width, garage["gateWidth"])
                self.assertLess(height, garage["gateClearanceHeight"])
                self.assertAlmostEqual(bay["center"][0], (bay["gateBounds"][0]+bay["gateBounds"][3])/2)

    def test_seat_optical_paths_exits_and_wheel_profiles_remain_physical(self):
        for name, model in self.models["chassis"].items():
            width, length, height = model["dimensions"]
            for key, seat in model["anchors"].items():
                if key.startswith("seat_"):
                    self.assertEqual("body", seat["frame"])
                    camera = model["anchors"][key.replace("seat_", "camera_")]["position"]
                    eye = [seat["position"][0], seat["position"][1]+1.62, seat["position"][2]]
                    self.assertLessEqual(math.dist(eye, camera), 1.8, name+" "+key)
                    self.assertLessEqual(abs(camera[0]), width/2)
                    self.assertLessEqual(abs(camera[2]), length/2)
                    self.assertTrue(0 <= camera[1] <= height)
                elif key.startswith("exit_"):
                    x, y, z = seat["position"]
                    self.assertEqual(.1, y)
                    self.assertTrue(abs(x) >= width/2+.3 or abs(z) >= length/2+.3)
            for bone in model["bones"]:
                if "wheel_" not in bone["name"]:
                    continue
                tires = [p["box"] for p in model["parts"] if p["parent"] == bone["name"] and "_tire_" in p["name"]]
                y_size = max(b[1]+b[4] for b in tires)-min(b[1] for b in tires)
                z_size = max(b[2]+b[5] for b in tires)-min(b[2] for b in tires)
                self.assertAlmostEqual(y_size, z_size, places=5, msg=bone["name"])

    def test_apc_driver_optic_is_in_its_port_above_front_armor_and_stays_behind_the_wall(self):
        chassis = self.source["chassis"]["apc"]
        camera = chassis["anchors"]["camera_driver"]["position"]
        port = next(part["box"] for part in chassis["parts"] if part["name"] == "glacis_vent_0")
        armor = next(part["box"] for part in chassis["parts"] if part["name"] == "glacis_step_3")
        for i in range(3):
            self.assertLessEqual(port[i], camera[i])
            self.assertLessEqual(camera[i], port[i]+port[i+3])
        self.assertGreaterEqual(camera[1], armor[1]+armor[4]+.05,
                                "driver periscope must see above the actual front armor")
        self.assertLess(camera[2], armor[2], "optic stays behind the front armor, never moved through it")


if __name__ == "__main__":
    unittest.main()
