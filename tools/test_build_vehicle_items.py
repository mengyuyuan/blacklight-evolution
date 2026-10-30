"""Exercise the public item generator against isolated real-resource fixtures."""
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import tempfile
import unittest

from PIL import Image

import build_vehicle_items as builder


class VehicleItemTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="m6-items-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.assets = self.root / "src/main/resources/assets/blacklight"
        (self.assets / "military").mkdir(parents=True)
        (self.assets / "lang").mkdir()
        origin = builder.ROOT / "src/main/resources/assets/blacklight"
        (self.assets / "military/vehicle-models.json").write_bytes((origin / "military/vehicle-models.json").read_bytes())
        for language in ("zh_cn", "en_us"):
            (self.assets / "lang" / (language + ".json")).write_text(
                '{"unrelated.fixture":"retain","hud.blacklight.vehicle":"client-owned format"}', encoding="utf8")

    def test_public_generation_uses_original_inventory_sprites(self):
        builder.generate(self.root, False, False)
        names = [f"{faction}_{kind}_chassis" for faction in ("military", "blackwatch")
                 for kind in ("truck", "apc", "tank")]
        names += ["military_fuel_can", "tank_he_shell", "tank_ap_shell", "autocannon_round", "vehicle_repair_parts"]
        icons = {}
        for name in names:
            model = json.loads((self.assets / "models/item" / (name + ".json")).read_text(encoding="utf8"))
            self.assertTrue(all(value.startswith("blacklight:item/") for value in model["textures"].values()),
                            name + " must use original pixel artwork, not vanilla block faces")
            self.assertEqual("minecraft:item/generated", model.get("parent"), name + " inventory sprite")
            texture = self.assets / "textures/item" / (name + ".png")
            self.assertTrue(texture.is_file(), name)
            with Image.open(texture) as icon:
                self.assertEqual((32, 32), icon.size)
                self.assertEqual("RGBA", icon.mode)
                self.assertEqual((0, 255), icon.getchannel("A").getextrema())
                small = icon.resize((16, 16), Image.Resampling.NEAREST)
                self.assertGreaterEqual(small.getbbox()[3]-small.getbbox()[1], 8, name)
                icons[name] = small.copy()
        vehicles = [icons[name].tobytes() for name in names[:6]]
        self.assertEqual(6, len(set(vehicles)), "all six faction/class sprites remain distinct at 16 px")
        for faction in ("military", "blackwatch"):
            for first, second in (("truck", "apc"), ("truck", "tank"), ("apc", "tank")):
                a = icons[f"{faction}_{first}_chassis"].getchannel("A").tobytes()
                b = icons[f"{faction}_{second}_chassis"].getchannel("A").tobytes()
                self.assertGreater(sum(x != y for x, y in zip(a, b)), 12, first+" / "+second)
                a = icons[f"{faction}_{first}_chassis"].tobytes()
                b = icons[f"{faction}_{second}_chassis"].tobytes()
                self.assertGreater(sum(a[i:i+4] != b[i:i+4] for i in range(0, len(a), 4)), 24,
                                   faction+" "+first+" / "+second+" colored pixels")
        supplies = names[6:]
        for index, first in enumerate(supplies):
            for second in supplies[index+1:]:
                a, b = icons[first].getchannel("A").tobytes(), icons[second].getchannel("A").tobytes()
                self.assertGreater(sum(x != y for x, y in zip(a, b)), 24, first+" / "+second+" silhouettes")
        builder.generate(self.root, False, True)

    def test_actual_names_tooltips_and_both_recipe_formats_keep_persisted_ids(self):
        for forge in (False, True):
            builder.generate(self.root, forge, False)
            for language in ("zh_cn", "en_us"):
                entries = json.loads((self.assets / "lang" / (language + ".json")).read_text(encoding="utf8"))
                self.assertEqual("retain", entries["unrelated.fixture"])
                self.assertEqual("client-owned format", entries["hud.blacklight.vehicle"])
                for faction in ("military", "blackwatch"):
                    for kind in ("truck", "apc", "tank"):
                        name = faction+"_"+kind+"_chassis"
                        label = entries["item.blacklight."+name]
                        self.assertNotIn("Empty Chassis", label)
                        self.assertNotIn("空底盘", label)
                        recipe = self.root / "src/main/resources/data/blacklight" / ("recipes" if forge else "recipe") / (name+".json")
                        result = json.loads(recipe.read_text(encoding="utf8"))["result"]
                        self.assertEqual("blacklight:"+name, result["item" if forge else "id"])
                self.assertIn("tooltip.blacklight.vehicle.place", entries)
                self.assertIn("tooltip.blacklight.vehicle.supply", entries)
            builder.generate(self.root, forge, True)

    def test_latest_supply_contract_has_no_active_refueling_instruction(self):
        builder.generate(self.root, False, False)
        for language in ("zh_cn", "en_us"):
            entries = json.loads((self.assets / "lang" / (language+".json")).read_text(encoding="utf8"))
            self.assertEqual(
                "放置完整车辆，初始无弹药，驾驶无需燃油" if language == "zh_cn" else
                "Places a complete vehicle, initially without ammunition; driving requires no fuel",
                entries["tooltip.blacklight.vehicle.place"])
            self.assertEqual(
                "停稳下车，持对应弹药/维修件右键车辆" if language == "zh_cn" else
                "Stop and dismount, then right-click the vehicle with matching ammunition or repair parts",
                entries["tooltip.blacklight.vehicle.supply"])
            fuel = entries["tooltip.blacklight.military_fuel_can"]
            self.assertIn("未启用" if language == "zh_cn" else "inactive", fuel)
            self.assertNotIn("加油" if language == "zh_cn" else "refuel", fuel)
            self.assertEqual(3, entries["hud.blacklight.vehicle_status"].count("%s"))
            self.assertNotIn("燃" if language == "zh_cn" else "fuel", entries["hud.blacklight.vehicle_status"].lower())
            for key in ("hud.blacklight.vehicle_primary", "hud.blacklight.vehicle_secondary"):
                self.assertEqual(1, entries[key].count("%s"))
            hint = entries["hud.blacklight.vehicle_supply_hint"]
            self.assertIn("弹药" if language == "zh_cn" else "ammunition", hint)
            self.assertNotIn("燃" if language == "zh_cn" else "fuel", hint.lower())

    def test_public_receipt_separates_active_items_from_preserved_fuel_in_both_formats(self):
        expected_active = {f"{faction}_{kind}_chassis" for faction in ("military", "blackwatch")
                           for kind in ("truck", "apc", "tank")}
        expected_active.update(("autocannon_round", "tank_he_shell", "tank_ap_shell", "vehicle_repair_parts"))
        expected_active.update(("rifle_round", "machine_gun_round", "grenade_round", "rocket_round", "missile_round"))
        for forge in (False, True):
            for check in (False, True):
                stream = io.StringIO()
                with redirect_stdout(stream):
                    builder.generate(self.root, forge, check)
                receipt = json.loads(stream.getvalue())
                self.assertEqual(expected_active, set(receipt["active"]["items"]))
                self.assertEqual(["military_fuel_can"], receipt["preserved"]["items"])
                self.assertEqual(15, receipt["active"]["recipes"])
                self.assertEqual(10, receipt["active"]["models"])
                self.assertEqual(10, receipt["active"]["textures"])
                self.assertEqual(11, receipt["models"])
                self.assertEqual(1, receipt["preserved"]["recipes"])
                self.assertEqual("exclude_from_game_build", receipt["preserved"]["recipePolicy"])
                self.assertEqual(38, receipt["outputFiles"])
            folder = self.root / "src/main/resources/data/blacklight" / ("recipes" if forge else "recipe")
            for name in expected_active | {"military_fuel_can"}:
                recipe = json.loads((folder / (name+".json")).read_text(encoding="utf8"))
                self.assertEqual("blacklight:"+name, recipe["result"]["item" if forge else "id"])
                if name in expected_active:
                    self.assertNotIn("military_fuel_can", json.dumps(recipe))

    def test_check_rejects_stale_active_and_preserved_resources_without_writing(self):
        builder.generate(self.root, False, False)
        recipe_folder = self.root / "src/main/resources/data/blacklight/recipe"
        paths = [self.assets / "textures/item/tank_he_shell.png", recipe_folder / "tank_ap_shell.json",
                 recipe_folder / "machine_gun_round.json",
                 recipe_folder / "military_fuel_can.json", self.assets / "models/item/military_fuel_can.json",
                 self.assets / "lang/zh_cn.json"]
        for path in paths:
            with self.subTest(resource=path.name):
                original = path.read_bytes()
                if path.name == "zh_cn.json":
                    entries = json.loads(original)
                    entries["tooltip.blacklight.vehicle.place"] = "stale"
                    path.write_text(json.dumps(entries), encoding="utf8")
                else:
                    path.write_bytes(original+b"stale")
                snapshot = {p: (p.read_bytes(), p.stat().st_mtime_ns)
                            for p in (self.root / "src/main/resources").rglob("*") if p.is_file()}
                with self.assertRaisesRegex(AssertionError, "Stale generated|Missing vehicle translations"):
                    builder.generate(self.root, False, True)
                self.assertEqual(snapshot, {p: (p.read_bytes(), p.stat().st_mtime_ns) for p in snapshot})
                path.write_bytes(original)
        builder.generate(self.root, False, True)

    def test_public_generator_gives_each_of_ten_ammunition_inputs_one_expected_result(self):
        cases = {
            "rifle_round": (["iron_ingot", "gunpowder"], 16),
            "machine_gun_round": (["iron_ingot", "copper_ingot", "gunpowder"], 16),
            "grenade_round": (["iron_ingot", "iron_ingot", "gunpowder"], 2),
            "rocket_round": (["iron_ingot", "iron_ingot", "gunpowder", "paper"], 2),
            "missile_round": (["iron_ingot", "iron_ingot", "gunpowder", "paper", "redstone"], 2),
            "thermobaric_round": (["iron_ingot", "gunpowder", "blaze_powder"], 2),
            "infection_round": (["iron_ingot", "gunpowder", "blacklight:blacklight_sample"], 2),
            "autocannon_round": (["iron_ingot", "iron_ingot", "copper_ingot", "gunpowder"], 8),
            "tank_he_shell": (["iron_ingot", "iron_ingot", "gunpowder", "gunpowder", "copper_ingot"], 1),
            "tank_ap_shell": (["iron_ingot", "iron_ingot", "iron_ingot", "gunpowder", "copper_ingot"], 1),
        }
        origin = builder.ROOT / "src/main/resources/data/blacklight"
        forge = (origin / "recipes/rifle_round.json").is_file()
        folder = "recipes" if forge else "recipe"
        target = self.root / "src/main/resources/data/blacklight" / folder
        target.mkdir(parents=True)
        for name in cases:
            (target / (name+".json")).write_bytes((origin / folder / (name+".json")).read_bytes())
        builder.generate(self.root, forge, False)
        recipes = {name: json.loads((target / (name+".json")).read_text(encoding="utf8")) for name in cases}
        signatures = {}
        for name, recipe in recipes.items():
            self.assertEqual("minecraft:crafting_shapeless", recipe["type"])
            signatures[name] = tuple(sorted(ingredient["item"] for ingredient in recipe["ingredients"]))
        for name, (items, count) in cases.items():
            with self.subTest(ammunition=name):
                wanted = tuple(sorted(item if ":" in item else "minecraft:"+item for item in items))
                matches = sorted(other for other, signature in signatures.items() if signature == wanted)
                self.assertEqual([name], matches, "each ordinary crafting input must identify only its intended ammunition")
                self.assertEqual({"item" if forge else "id": "blacklight:"+name, "count": count}, recipes[name]["result"])
        self.assertEqual(10, len(set(signatures.values())), "all ten ammunition multisets must differ")

    def test_recipe_only_mode_keeps_language_and_model_inputs_untouched_in_both_formats(self):
        snapshot = {p: (p.read_bytes(), p.stat().st_mtime_ns) for p in self.assets.rglob("*") if p.is_file()}
        for forge in (False, True):
            for check in (False, True):
                stream = io.StringIO()
                with redirect_stdout(stream):
                    builder.generate(self.root, forge, check, recipes_only=True)
                receipt = json.loads(stream.getvalue())
                self.assertEqual(16, receipt["outputFiles"])
                self.assertEqual(16, receipt["recipes"])
                self.assertEqual(0, receipt["models"])
                self.assertEqual(0, receipt["active"]["textures"])
                self.assertEqual(0, receipt["preserved"]["models"])
                self.assertEqual(snapshot, {p: (p.read_bytes(), p.stat().st_mtime_ns)
                                            for p in self.assets.rglob("*") if p.is_file()})


if __name__ == "__main__":
    unittest.main()
