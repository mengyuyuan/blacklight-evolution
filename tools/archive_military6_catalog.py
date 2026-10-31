"""Read current M6 production files and export a separate, reproducible making catalogue.

Never runs Git, Gradle, Minecraft, network requests, NBT commands or asset installers.
Only writes docs/制作资料/M6 and its generated M6_载具与车库.md entry. Existing M5
documents, player screenshots, runtime assets and user worlds are preserved.
"""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path("src/main/java/com/codex/blacklight")
RES = Path("src/main/resources")
NAMES = {"truck": "运输卡车", "apc": "装甲战斗车", "tank": "坦克"}
REVISION_SOURCES = tuple(JAVA/name for name in (
    'mixin/VehicleEntityCollisionMixin.java', 'mixin/VehicleProjectileCollisionMixin.java', 'mixin/client/VehiclePlayerPoseMixin.java',
    'mixin/client/PrototypeChaseCameraMixin.java', 'client/camera/PrototypeChaseCamera.java', 'client/animation/BlacklightRenderPose.java',
    'client/model/BlacklightPlayerModel.java', 'client/model/MercerPlayerMesh.java'))+(RES/'blacklight.mixins.json',)
REVISION_PATTERNS = ('src/main/java/com/codex/blacklight/military/**/*.java', 'src/main/java/com/codex/blacklight/client/vehicle/*.java',
                     'src/test/java/com/codex/blacklight/client/vehicle/*.java', 'src/test/java/com/codex/blacklight/military/vehicle/*.java',
                     'src/vehicleTest/java/com/codex/blacklight/gametest/vehicle/*.java')


def revision_sources(root):
    return set(REVISION_SOURCES) | {path.relative_to(root) for pattern in REVISION_PATTERNS for path in root.glob(pattern)}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def table(headers, rows):
    def cell(value):
        if isinstance(value, (dict, list)):
            value = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
        return str(value).replace("|", "\\|").replace("\n", "<br>")
    return "\n".join(["| " + " | ".join(map(cell, headers)) + " |",
                      "| " + " | ".join(["---"] * len(headers)) + " |"] +
                     ["| " + " | ".join(map(cell, row)) + " |" for row in rows]) + "\n"


def module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def vehicle_weapons(text, legacy):
    rows = []
    for body in re.findall(r'create\(("[^\n]+?)\)', text):
        values = legacy.split_args(body)
        if len(values) != 10:
            raise ValueError("Vehicle weapon schema changed; expected ten create arguments")
        rows.append(dict(id=values[0].strip('"'), ammo=values[1].strip('"'), cooldown_ticks=int(values[2]),
                         damage=legacy.number(values[3]), range=legacy.number(values[4]),
                         kind=values[5].split('.')[-1], radius=legacy.number(values[6]),
                         damage_class=values[7].split('.')[-1], speed=legacy.number(values[8]), secondary=values[9] == "true"))
    if len(rows) != 5:
        raise ValueError("Expected five current vehicle weapons")
    return rows


def loadouts(text, weapons):
    symbols = {row["symbol"]: row["id"] for row in weapons}
    rows = []
    for duty, body in re.findall(r'case (\w+)->new MilitaryLoadoutProfile\(duty,(.*)\);', text):
        guns = [dict(weapon=symbols[symbol], magazine=int(magazine), reserve=int(reserve))
                for symbol, magazine, reserve in re.findall(r'new Gun\(WeaponCatalog\.(\w+)\.id\(\),(\d+),(\d+)\)', body)]
        if not guns or len(guns) > 2:
            raise ValueError("Loadout gun schema changed")
        rows.append(dict(duty=duty, primary=guns[0], secondary=guns[1] if len(guns) > 1 else None))
    maps = re.findall(r'Map\.of\((Duty\.[^\n]+?)\)', text)
    weights = [{duty: int(count) for duty, count in re.findall(r'Duty\.(\w+),(\d+)', body)} for body in maps]
    if len(rows) != 5 or len(weights) != 2:
        raise ValueError("Expected five duties and both actual native weight maps")
    if '(size==8?5:8)' not in text or 'size==8&&mechanicalThreat?Duty.ANTI_ARMOR:Duty.GRENADIER' not in text:
        raise ValueError("Patrol composition changed; review before exporting")
    return dict(profiles=rows, native_weights=dict(military=weights[0], blackwatch=weights[1]),
                patrol8=dict(CARBINE=1, RIFLE=5, MACHINE_GUN=1, GRENADIER=1),
                patrol8_mechanical=dict(CARBINE=1, RIFLE=5, MACHINE_GUN=1, ANTI_ARMOR=1),
                patrol12=dict(CARBINE=1, RIFLE=8, MACHINE_GUN=1, GRENADIER=1, ANTI_ARMOR=1),
                patrol_status="Policy factory, not a claim that production automatically spawns 8/12-person patrols")


def recipe_ingredients(raw):
    if "pattern" in raw:
        symbols = Counter("".join(raw["pattern"]).replace(" ", ""))
        return {raw["key"][key]["item"]: count for key, count in symbols.items()}
    return dict(Counter(value["item"] for value in raw["ingredients"]))


def garage_rules(service, inventory, supply):
    repairs = {int(value) for value in re.findall(r'\.repair\((\d+)\)', service)}
    caps = re.findall(r'loadedNativeVehicles\(level\)>=(\d+)', service)
    primary = re.findall(r'count\*(\d+)<=capacity', supply)
    health = re.findall(r'boolean needsService\([^}]*?health<definition\.maxHealth\(\)\*\.(\d+)', supply, re.DOTALL)
    resume_primary = re.findall(r'return count\*(\d+)<capacity;', supply)
    resume_health = re.findall(r'boolean resumeNeedsRepair\([^}]*?health<definition\.maxHealth\(\)\*\.(\d+)', supply, re.DOTALL)
    retained = all(re.search(pattern, inventory) for pattern in (
        r'record Snapshot\([^)]*long fuelTicks', r'long addFuel\(long offered\)', r'boolean consumeFuel\(long ticks\)'))
    if '.addFuel(' in service or not retained or any(len(values) != 1 for values in (repairs, caps, primary, health, resume_primary, resume_health)) or '.addAmmunition(kind,offered)' not in service:
        raise ValueError("Fuel-disabled garage/inventory/ammunition contract changed; inspect actual sources")
    return dict(fuel_gameplay="disabled", dormant_fuel=dict(snapshot_and_finite_api_retained=True,
                capacity_field="maxFuelTicks", usage="inactive configuration and saved FuelTicks retained; no gameplay consumption"),
                health_per_repair_part=repairs.pop(), loaded_native_dispatch_cap=int(caps[0]),
                 low_primary_ammunition_ratio=1/int(primary[0]), low_health_ratio=float("0."+health[0]),
                 resume_primary_ammunition_ratio=1/int(resume_primary[0]), resume_health_ratio=float("0."+resume_health[0]),
                automatic_supply="actual ItemEntity ammunition/repair cargo; runtime acceptance belongs to root")


def item_icons(archive, root, output, ammunition):
    from PIL import Image
    ids = {f'{faction}_{chassis}_chassis' for faction in ('military', 'blackwatch') for chassis in NAMES}
    ids |= set(ammunition) | {'military_fuel_can', 'vehicle_repair_parts'}
    rows = []
    for item in sorted(ids):
        model = archive.json(RES/'assets/blacklight/models/item'/(item+'.json'), root)
        if model.get('parent') != 'minecraft:item/generated' or model.get('textures', {}).get('layer0') != 'blacklight:item/'+item:
            raise ValueError('Expected original inventory sprite model: '+item)
        relative = RES/'assets/blacklight/textures/item'/(item+'.png');source = archive.source(root, relative)
        with Image.open(source) as image:
            size = list(image.size);image.verify()
        if item not in ('machine_gun_round', 'rocket_round') and size != [32, 32]:
            raise ValueError('Expected original 32px inventory sprite: '+item)
        target = output/'assets/items'/(item+'.png');target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        rows.append(dict(id=item, active=item!='military_fuel_can', source=relative.as_posix(),
                         path=target.relative_to(output).as_posix(), sha256=sha(target), size=size))
    return rows


def export(args):
    neo, forge, output = args.neo_root.resolve(), args.forge_root.resolve(), args.output.resolve()
    allowed = [(root / "docs/制作资料/M6").resolve() for root in (neo, forge)]
    if output not in allowed:
        raise ValueError("Output must be the dedicated docs/制作资料/M6 directory of either selected tree")
    legacy = module(neo / "tools/archive_military_catalog.py", "military6_legacy_reader")
    archive = legacy.Archive(output, forge)
    lang = archive.json(RES / "assets/blacklight/lang/zh_cn.json")
    weapons = archive.weapon_data(neo)
    if archive.weapon_data(forge) != weapons:
        raise ValueError("Infantry weapon definitions differ between loaders; review separate tables")
    soldiers = archive.soldiers(weapons, lang)
    for row in soldiers:
        row["initial_weapon_scope"] = "initializeWeapon direct/new entity default; native assignments use M6 typed profile"
        row["historical_m5_visual_card"] = "assets/soldiers/catalog-" + row["id"].split(":")[1] + "-card.png"
        row.pop("preview", None)
    organization = archive.organization_data(lang)
    vehicles = archive.json(RES / "data/blacklight/military/vehicles.json")
    mounts = archive.json(RES / "data/blacklight/military/vehicle-mounts.json")
    garages = archive.json(RES / "data/blacklight/military/vehicle_garages.json")
    authoring = archive.json(Path("art/military-vehicles/vehicle-authoring.json"))
    author_sha = sha(neo / "art/military-vehicles/vehicle-authoring.json")
    if mounts["sourceSha256"] != author_sha:
        raise ValueError("Runtime mounts do not match current vehicle authoring")
    vehicle_weapon_data = vehicle_weapons(archive.text(JAVA / "military/vehicle/VehicleWeaponDefinition.java"), legacy)
    duty_data = loadouts(archive.text(JAVA / "military/MilitaryLoadoutProfile.java"), weapons)
    service_source = archive.text(JAVA / "military/vehicle/VehicleGarageService.java")
    rules = garage_rules(service_source, archive.text(JAVA/'military/vehicle/VehicleInventory.java'),
                         archive.text(JAVA/'military/vehicle/VehicleSupplyRules.java'))
    forge_rules = garage_rules(archive.text(JAVA/'military/vehicle/VehicleGarageService.java', forge),
                               archive.text(JAVA/'military/vehicle/VehicleInventory.java', forge),
                               archive.text(JAVA/'military/vehicle/VehicleSupplyRules.java', forge))
    if rules != forge_rules:
        raise ValueError('Loader fuel-disabled ammunition/repair rules differ')
    for name, expected in [("vehicles.json", vehicles), ("vehicle-mounts.json", mounts), ("vehicle_garages.json", garages)]:
        if archive.json(RES / "data/blacklight/military" / name, forge) != expected:
            raise ValueError("Loader data differ; do not publish one shared table: " + name)
    for relative in ["military/MilitaryCombatProfile.java", "military/MilitaryRank.java", "military/MilitaryRole.java", "military/MilitaryLoadoutProfile.java"]:
        if archive.text(JAVA / relative, neo) != archive.text(JAVA / relative, forge):
            raise ValueError("Loader pure rule differs; review tables separately: " + relative)
    structure_rows = []
    structure_blocks = {}
    recipe_rows = []
    literals = {}
    for platform, root, structure_dir, recipe_dir in [("neoforge", neo, "structure", "recipe"), ("forge", forge, "structures", "recipes")]:
        icon_ids = {f'{faction}_{chassis}_chassis' for faction in ('military', 'blackwatch') for chassis in NAMES}
        icon_ids |= {row['ammo'].removeprefix('blacklight:') for row in vehicle_weapon_data} | {'military_fuel_can', 'vehicle_repair_parts'}
        for item in sorted(icon_ids):
            archive.source(root, RES/'assets/blacklight/models/item'/(item+'.json'))
            archive.source(root, RES/'assets/blacklight/textures/item'/(item+'.png'))
        metadata = archive.json(RES / "data/blacklight/military/large_bases.json", root)
        base_map = {base["name"]: base for base in metadata["bases"]}
        for source in sorted((root / RES / "data/blacklight" / structure_dir).glob("*.nbt")):
            archive.source(root, source.relative_to(root))
            raw, blocks = legacy.read_nbt(source)
            palette = raw.get("palette") or raw["palettes"][0]
            chests = [dict(position=block["pos"], nbt=block["nbt"]) for block in raw["blocks"]
                      if "nbt" in block and palette[block["state"]]["Name"] in ("minecraft:chest", "minecraft:trapped_chest")]
            row = dict(id=source.stem, platform=platform, size=raw["size"], serialized_blocks=len(raw["blocks"]),
                       non_air_blocks=len(blocks), palette_size=len(palette), embedded_entities=raw.get("entities", []),
                       chests=chests, runtime_metadata=base_map.get(source.stem), source=source.relative_to(root).as_posix(), sha256=sha(source))
            if row["runtime_metadata"]:
                row["native_personnel_slots"] = len(row["runtime_metadata"]["guards"])
            structure_rows.append(row)
            dump(output / "data/nbt" / platform / (source.stem + ".json"), raw)
            if platform == "neoforge":
                structure_blocks[source.stem] = blocks
        recipe_names = [f"{faction}_{chassis}_chassis" for faction in ("military", "blackwatch") for chassis in NAMES]
        recipe_names += ["autocannon_round", "tank_he_shell", "tank_ap_shell", "military_fuel_can", "vehicle_repair_parts"]
        for name in recipe_names:
            relative = RES / "data/blacklight" / recipe_dir / (name + ".json")
            raw = archive.json(relative, root)
            result = raw["result"]
            recipe_rows.append(dict(platform=platform, id=name, ingredients=recipe_ingredients(raw),
                                    result=result.get("id", result.get("item")), count=result.get("count", 1), source=relative.as_posix(), raw=raw,
                                    active=name!='military_fuel_can', policy='active' if name!='military_fuel_can' else 'preserved_on_disk_excluded_from_game_build'))
        for relative in sorted((root / JAVA / "military").rglob("*.java")):
            archive.source(root, relative.relative_to(root))
        for pattern in ["src/main/java/com/codex/blacklight/command/*Military*.java", "src/main/java/com/codex/blacklight/command/Vehicle*.java"]:
            for source in root.glob(pattern):
                text = archive.text(source.relative_to(root), root)
                literals[platform + "/" + source.name] = dict(literals=re.findall(r'Commands\.literal\("([^"]+)"\)', text),
                                                              arguments=re.findall(r'Commands\.argument\("([^"]+)"', text))
        for relative in ["entity/MilitaryVehicle.java", "entity/VehicleHullPart.java", "entity/MilitarySoldier.java", "entity/RegularSoldier.java",
                         "entity/BlackwatchSoldier.java", "registry/ModEntities.java", "registry/ModItems.java", "city/CityMilitaryService.java", "city/MilitaryBaseAccess.java",
                         "city/VehicleGaragePiece.java", "firearms/VehicleOrdnanceProjectile.java", "firearms/FirearmsCombat.java", "firearms/WeaponCatalog.java",
                         "command/BlacklightCommands.java", "client/ClientClawEvents.java", "network/VehicleControlPayload.java", "network/VehicleSeatPayload.java",
                          "client/VehicleHud.java", "client/VehicleControls.java", "client/ClientWeaponCombat.java", "client/SkillBindings.java",
                           "client/model/MilitaryVehicleModel.java", "client/model/BlackwatchModel.java"]:
            archive.source(root, JAVA / relative)
        for relative in sorted(revision_sources(root)):archive.source(root, relative)
        for relative in ["art/military-vehicles/vehicle-authoring.json", "art/military-vehicles/validation-receipt.json",
                         "art/military-vehicles/reference-observations.json", "art/military-vehicles/truck.bbmodel", "art/military-vehicles/apc.bbmodel",
                         "art/military-vehicles/tank.bbmodel", "art/military-vehicles/textures/military.png", "art/military-vehicles/textures/blackwatch.png",
                         "tools/archive_military6_catalog.py", "tools/archive_military_catalog.py", "tools/build_military_vehicles.py",
                          "tools/build_vehicle_garages.py", "tools/preview_military_vehicles.py", "tools/VehicleMeshCapture.java",
                          "src/test/java/com/codex/blacklight/client/model/VehicleRiderPoseTest.java",
                           "art/military-vehicles/previews/production/manifest.json", ".superpowers/military6-qa/visual-seating-final-receipt.json",
                           ".superpowers/military6-qa/visual-seat-sync-final-receipt.json",
                          "tools/preview_large_military_bases.py", "THIRD_PARTY_ASSETS.md"]:
            if relative == "tools/archive_military_catalog.py" and not (root / relative).is_file():
                continue  # The reused read-only helper is shipped by the selected Neo source tree.
            archive.source(root, Path(relative))
        for relative in ["data/blacklight/military/vehicles.json", "data/blacklight/military/vehicle-mounts.json", "data/blacklight/military/vehicle_garages.json",
                         "assets/blacklight/military/vehicle-models.json", "assets/blacklight/textures/entity/military_vehicles_military.png",
                         "assets/blacklight/textures/entity/military_vehicles_blackwatch.png"]:
            archive.source(root, RES / relative)
    if len(soldiers) != 10 or len(structure_rows) != 18:
        raise ValueError("Expected ten soldier types and nine current templates on each loader")
    recipes_by_platform = {platform: {row["id"]: (row["ingredients"], row["result"], row["count"]) for row in recipe_rows if row["platform"] == platform}
                           for platform in ("neoforge", "forge")}
    if recipes_by_platform["neoforge"] != recipes_by_platform["forge"]:
        raise ValueError("Loader recipe meanings differ; inspect both before publishing a shared recipe table")
    for relative in [".superpowers/military6-qa/neo-resume-summon-green.log", ".superpowers/military6-qa/neo-resume-summon-green.result.json"]:
        archive.source(neo, Path(relative))
    preview_root = neo if output == allowed[0] else forge
    preview_source = preview_root / "art/military-vehicles/previews/production"
    preview_manifest = json.loads((preview_source / "manifest.json").read_text(encoding="utf-8"))
    if preview_manifest.get("kind") != "actual-production-ModelPart-CPU-preview" or preview_manifest.get("scenes") != 8 or len(preview_manifest.get("files", {})) != 9 or not preview_manifest.get("not_game_screenshot"):
        raise ValueError("Expected eight labelled actual ModelPart CPU scenes and nine canonical PNGs")
    preview_rows = []
    for name, expected in preview_manifest["files"].items():
        source = preview_source / name
        if sha(source) != expected or Path(name).name != name:
            raise ValueError("Vehicle preview changed or unsafe filename: " + name)
        target = output / "assets/vehicles" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        preview_rows.append(dict(path=target.relative_to(output).as_posix(), sha256=sha(target), kind="actual_ModelPart_CPU_not_game_screenshot"))
    dump(output / "assets/vehicles/manifest.json", preview_manifest)
    soldier_capture_dir = neo / "docs/制作资料/参考模型/生产预览-military5"
    soldier_capture = json.loads((soldier_capture_dir / "manifest.json").read_text(encoding="utf-8"))
    if not soldier_capture.get("not_game_screenshot") or len(soldier_capture.get("catalog", {})) != 10:
        raise ValueError("Missing historical ten-soldier visual catalogue")
    for entity, row in soldier_capture["catalog"].items():
        name = row["card"]
        source = soldier_capture_dir / name
        if Path(name).name != name or sha(source) != soldier_capture["images"][name]:
            raise ValueError("Historical soldier preview hash mismatch")
        target = output / "assets/soldiers" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        preview_rows.append(dict(path=target.relative_to(output).as_posix(), sha256=sha(target),
                                 kind="historical_M5_actual_ModelPart_CPU_not_refreshed_for_M6", original_manifest_sha256=sha(soldier_capture_dir / "manifest.json")))
    dump(output / "assets/soldiers/original-M5-manifest.json", soldier_capture)
    for row in structure_rows:
        if row["platform"] != "neoforge" or row["id"].startswith("vehicle_garage_"):
            continue
        source = neo / "docs/制作资料/assets/建筑" / (row["id"] + ".png")
        target = output / "assets/buildings" / source.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        preview_rows.append(dict(path=target.relative_to(output).as_posix(), sha256=sha(target), kind="historical_M5_NBT_preview_not_refreshed_for_M6"))
    if not args.skip_render:
        renderer = module(neo / "tools/preview_large_military_bases.py", "military6_nbt_renderer").Renderer(neo / RES)
        for name in ("vehicle_garage_military", "vehicle_garage_blackwatch"):
            for suffix, yaw, blocks in [("exterior", 315, structure_blocks[name]),
                                        ("cutaway", 135, {p: state for p, state in structure_blocks[name].items() if p[1] < 2})]:
                target = output / "assets/garages" / (name + "-" + suffix + ".png")
                target.parent.mkdir(parents=True, exist_ok=True)
                renderer.render(blocks, yaw, size=(1400, 1000)).save(target)
                preview_rows.append(dict(path=target.relative_to(output).as_posix(), sha256=sha(target), kind="actual_NBT_geometry_approximate_vanilla_materials_not_game_screenshot"))
    else:
        previous = output / "data/preview-manifest.json"
        if not previous.is_file():
            raise ValueError("--skip-render requires existing rendered garage previews")
        preview_rows += [row for row in json.loads(previous.read_text(encoding="utf-8")) if row["path"].startswith("assets/garages/")]
    current = datetime.now().astimezone().isoformat(timespec="seconds")
    icons = item_icons(archive, preview_root, output, {row['ammo'].removeprefix('blacklight:') for row in vehicle_weapon_data})
    payload = dict(schema=1, generated_at=current, gameplay_validation="Not asserted by catalogue generation", authoring_sha256=author_sha,
                   soldiers=soldiers, infantry_weapons=weapons, organization=organization, vehicles=vehicles, mounts=mounts,
                   vehicle_weapons=vehicle_weapon_data, infantry_loadouts=duty_data, garages=garages, garage_rules=rules, structures=structure_rows,
                   recipes=recipe_rows, item_icons=icons, command_registration=literals)
    for key in ["soldiers", "infantry_weapons", "organization", "vehicles", "mounts", "vehicle_weapons", "infantry_loadouts", "garages", "garage_rules", "structures", "recipes", "item_icons", "command_registration"]:
        dump(output / "data" / (key + ".json"), payload[key])
    dump(output / "data/catalog.json", payload)
    dump(output / "data/source-manifest.json", sorted(archive.sources, key=lambda row: (row["platform"], row["path"])))
    dump(output / "data/preview-manifest.json", preview_rows)
    write_cards(output, payload)
    print(json.dumps(dict(output=str(output), soldier_types=len(soldiers), vehicle_chassis=len(vehicles["vehicles"]),
                         registered_vehicle_variants=sum(len(v["liveries"]) for v in vehicles["vehicles"]), templates_per_loader=9,
                         sources=len(archive.sources), preview_pngs=len(preview_rows), authoring_sha256=author_sha), ensure_ascii=False))


def write_cards(output, data):
    text = "# M6：载具、车库与当前军事资料\n\n"
    text += "本卡从双端本次生产文件导出；生成资料不代表测试、GL试玩或最终交付通过。最终实现与验收状态以[根实施记录](../verification/MILITARY6_VEHICLES_20261026_ZH.md)及[玩家指南](../MILITARY6_PLAYER_TEST_ZH.md)为准。旧 M5 文档与原图片保留原意。\n\n"
    text += f"生成时间：{data['generated_at']}。本次车辆 authoring SHA-256：`{data['authoring_sha256']}`。此哈希只对应该资产源文件，源码快照与机器数据对应本次导出时点；后续生产改动仍须在根最终冻结后刷新并核对，不能据此认定已完成游戏验收。旧 `7c3c0604…` / `5d…` 是历史制作阶段。\n\n"
    text += "| 内容 | 入口 |\n| --- | --- |\n| 易懂实际测试流程 | [M6玩家指南](../MILITARY6_PLAYER_TEST_ZH.md) |\n| 当前全10兵种数值与默认装备 | [兵种数据](M6/data/soldiers.json)、本页下方10外观卡 |\n| 军衔、岗位、编制 | [当前源码提取](M6/data/organization.json)、本页下方实际表格 |\n| 当前9建筑×双端尺寸、箱与嵌入实体 | [建筑数据](M6/data/structures.json)、[完整NBT解析](M6/data/nbt/) |\n| 旧7建筑外观/布局 | 本页下方保留的旧NBT图；未冒充M6重绘 |\n| 新车库外观 | 本页下方真实NBT离线预览 |\n| 新载具与带乘员外观 | 本页下方8个实际ModelPart CPU场景 |\n| 完整机器数据 | [catalog.json](M6/data/catalog.json) |\n| 原文、资源、可编辑模型 | [来源哈希索引](M6/data/source-manifest.json)，快照在M6/sources/ |\n\n"
    text += "## 当前全部10兵种与军衔/编制\n\n数值直接提取本次生产。岗位/军衔列描述注册构造默认，枪弹列描述正常新生时确定性的一次性初始化，不承诺任意自定义NBT或旧档会补发这些物品。下面人物图保留M5实际ModelPart CPU捕获及原manifest；没有为M6重绘，不是游戏截图，乘车姿态以M6车辆图为补充，GL仍需确认。\n\n"
    text += table(["实体", "名称", "耐久", "护甲", "NPC子弹伤害", "移动速", "岗位", "直接新生默认军衔", "直接新生默认枪/弹匣+备弹"],
                  [[r["id"], r["name"], r["health"], r["armor"], r["npc_effective_bullet_damage"], r["movement_speed"], r["role"], r["spawn_default_rank"],
                    f"{r['initial_weapon']} / {r['initial_magazine']}+{r['initial_reserve']}"] for r in data["soldiers"]])
    text += "\n军方/BW军官的注册构造默认均为 `officer` / `lieutenant`。`fleet create` 要求车内真实军官岗位为 `officer` 或 `commander`，且军衔至少 `lieutenant`，不会根据实体名称补身份。`MilitaryAutomaticJoin:0b` 本身也是Military存档字段，会进入已有/半旧Military数据读取逻辑，不能用它证明纯新生默认枪弹；带该字段的命令乘员应显式注明岗位/军衔，并只读检查实际值。真正无附加NBT的普通 `/summon` 保留构造身份和有限默认枪弹；历史Neo定向20/20日志保存在[实际日志](M6/sources/neoforge/.superpowers/military6-qa/neo-resume-summon-green.log)与[进程回执](M6/sources/neoforge/.superpowers/military6-qa/neo-resume-summon-green.result.json)。最终双端完整验证应读取[根记录](../verification/MILITARY6_VEHICLES_20261026_ZH.md)，本生成器不据历史定向结果推断全量通过。旧空兵、半旧/损坏存档继续不补枪、不免费填弹，已有实物 `HandItems` 不得复制。\n"
    for row in data["soldiers"]:
        text += f"\n![{row['name']} 历史M5实际ModelPart CPU外观卡](M6/assets/soldiers/catalog-{row['id'].split(':')[1]}-card.png)\n"
    text += "\n" + table(["军衔ID", "显示名称", "序号"], [[r["id"], r["name"], r["ordinal"]] for r in data["organization"]["ranks"]])
    text += "\n" + table(["岗位", "默认军衔"], [[r["id"], r["default_rank"]] for r in data["organization"]["roles"]])
    text += "\n" + table(["编制", "层级", "军方默认人数", "BW默认人数"], [[r["id"], r["level"], r["military_default_size"], r["blackwatch_default_size"]] for r in data["organization"]["unit_types"]])
    text += "\n## 当前全部9种建筑模板\n\n下表来自当前真实NBT，不以外观替代功能登记。大基地16/20为一次性原生人员槽；车库槽另算。已嵌入的实体NBT与运行时派发入口分开保存，箱的完整Items/LootTable在structures.json与完整NBT解析里。\n\n"
    text += table(["模板", "尺寸X×Y×Z", "非空气方块", "箱数", "嵌入实体", "原生人员槽"],
                  [[r["id"], "×".join(map(str, r["size"])), r["non_air_blocks"], len(r["chests"]), len(r["embedded_entities"]), r.get("native_personnel_slots", "无大基地名额")] for r in data["structures"] if r["platform"] == "neoforge"])
    for row in data["structures"]:
        if row["platform"] == "neoforge" and not row["id"].startswith("vehicle_garage_"):
            text += f"\n![{row['id']} 保留M5实际NBT离线图，非游戏截图](M6/assets/buildings/{row['id']}.png)\n"
    text += "## 三底盘、两涂装\n\n宽×长×高从本次同源authoring生成的实际配置读取，炮管额外进入碰撞/命中包围；速度单位格/tick（20tick约1秒）。驾驶无需燃油，行驶、转向及受阻均不消耗旧FuelTicks。六底盘放置均满HP、无乘员、零弹；普通新车 `/summon` 不领取初始弹药。燃料注册、模型、贴图、磁盘配方、有限API和NBT保留为inactive，正常创造栏及配方隐藏，旧燃料罐不接受补给。已有或残缺存档继续严格校验，不重置损伤或库存。自然车库仅首次派发给一次初始弹药，之后消耗真实补给。\n\n"
    text += table(["底盘", "实体/底盘物品前缀", "宽×长×高", "耐久", "最高速", "燃油玩法", "席位", "弹种上限"],
                  [[NAMES[v["id"]], "military / blackwatch：" + v["id"], f"{v['width']}×{v['length']}×{v['height']}", v["maxHealth"], v["maxSpeed"], "无需燃油",
                     ", ".join(s["id"] for s in v["seats"]), v["ammunitionCapacities"]] for v in data["vehicles"]["vehicles"]])
    text += "\n保留的inactive配置（不是行驶时长）：\n\n"+table(["底盘", "maxFuelTicks（保留）"], [[v['id'], v['maxFuelTicks']] for v in data['vehicles']['vehicles']])
    text += "\n实体ID为 `blacklight:military_truck/apc/tank` 与 `blacklight:blackwatch_truck/apc/tank` 的逐项组合；物品在各实体ID末尾加 `_chassis`。为免误复制，六个实际ID如下。\n\n"
    text += table(["实体", "完整车辆放置物品（保留_chassis ID）"], [[f"blacklight:{f}_{v['id']}", f"blacklight:{f}_{v['id']}_chassis"] for v in data["vehicles"]["vehicles"] for f in v["liveries"]])
    text += "\n## 五种车载武器\n\n所有伤害、尺寸、半径、数量和配方均为本模组玩法值，不是原作或现实军事统计。HE/AP共用主炮装填冷却，不能换弹种绕过。\n\n"
    text += table(["ID", "真实弹药", "伤害", "间隔tick", "距离格", "效果半径格", "伤害类别"],
                  [[w["id"], w["ammo"], w["damage"], w["cooldown_ticks"], w["range"], w["radius"], w["damage_class"]] for w in data["vehicle_weapons"]])
    text += "\n装甲倍率以 `VehicleWeaponDefinition.mechanicalDamage` 为准：普通子弹轻/装甲/重装1/.25/.10，自动炮1/.70/.30，HE/火箭1/.75/.50，AP均1。适用物理重击的单独倍率见源码伤害桥，不能把火器倍率当成所有能力倍率。\n\n"
    for faction in ("military", "blackwatch"):
        for chassis in NAMES:
            text += f"![{faction} {chassis} 实际ModelPart CPU预览，非截图](M6/assets/vehicles/{chassis}-{faction}.png)\n\n"
        text += f"![{faction} 卡车乘员实际座位CPU预览，非截图](M6/assets/vehicles/truck-crew-{faction}.png)\n\n"
    text += "本目录[主manifest](M6/assets/vehicles/manifest.json)保留8场景、9张canonical主图的实际CPU捕获来源；最终源冻结后须重新捕获与核对。CPU预览不能代替OpenGL截图、真人驾驶或双客户端炮手验收。旧visual-seat-sync/visual-seating回执只作历史，不证明本轮新尺寸和真实Player已通过。\n\n"
    text += "当前挂点由同一authoring导出：seat是乘员Entity脚点/基础位置，camera是单独的观察点。NPC和真实Player姿态、原型体皮肤及Mercer外观的源和测试快照已加入本轮来源索引；真实GL须读取独立run-military6-client-*的完整报告及root实际开图后的复核，任意YSM替代骨架兼容性仍不作推断。\n\n"
    text += table(["底盘", "挂点", "frame", "position"], [[chassis, name, anchor['frame'], anchor['position']] for chassis, row in data['mounts']['chassis'].items() for name, anchor in row['anchors'].items() if name.startswith(('seat_', 'camera_'))])
    text += "额外诊断剖视：[军方车内](../../art/military-vehicles/previews/production/diagnostics/truck-crew-interior-military.png)、[BW车内](../../art/military-vehicles/previews/production/diagnostics/truck-crew-interior-blackwatch.png)。这2张只供查看实际乘员顶点，不计入主manifest的9图或资料主图总数30张。\n\n"
    text += "## 玩家交互与目标规则\n\n默认W/S前进/倒车、A/D转向、空格刹车、Shift原版下车，读取Minecraft当前移动/跳跃/潜行绑定。普通空手右键上车；“黑光：地面载具”中的切换座位、切换主炮弹种默认未绑定，须自行设无冲突键。司机只在炮手席空时兼任武器操作员；有炮手则由炮手鼠标瞄准、攻击键主武器/使用键副武器，普通乘客和指挥席不获驾驶或火力控制。每发消耗本车实际对应弹药，空弹/冷却/炮口遮挡拒射，HE/AP共享主炮冷却。\n\n"
    text += "现有键位修复已接入：乘车时原型体抓取/技能/徒手攻击处理让出输入；空手Shift对车使用时也不抢走该次交互。炮手占席后司机不再发送无权火力/选弹动作，乘车移动输入可独立继续；攻击/使用仍尊重当前Minecraft绑定。实现依据见[客户端控制](M6/sources/neoforge/src/main/java/com/codex/blacklight/client/VehicleControls.java)及[原型体输入处理](M6/sources/neoforge/src/main/java/com/codex/blacklight/client/ClientClawEvents.java)。完整运行与真人改键验收以根回执为准。\n\n"
    text += "抢夺需原型体玩家下车、主手空手、Shift右键发起，再松Shift并保持空手和司机座接触点约3.5格内无遮挡满30tick。原AI乘员须全部为有效真实军人；真人占任何席位拒抢，退远/隔墙/乘员变化/无安全出口取消或拒绝。成功驱逐同UUID存活NPC并接管同一车，损伤、油弹和冷却保留，不自动吞噬、杀人或发奖励；发起时沿用原敌对接触和伪装识破规则。\n\n"
    text += "军方与BW保留两个独立阵营、默认同盟，平民沿用原规则。车载AI只主动选合法感染目标，保留创造/旁观、伪装和友军保护，不增设平民阵营或平民清剿目标；真人车内保护和PvP限制继续生效。`CLEAR_AREA` 已实现多车接敌横向展开：以领车接敌点为基准，按朝敌方向左右交替分配车位，相邻目标车位间距14格，各车使用原有加载区块寻路预算依次就位并停车后，真实普通兵乘客才下车作战；单车直接使用下车流程。路线/转向受阻累积失败后记 `BLOCKED` 并安全停，不缩小间距穿障或瞬移展开。源码见[车队实现](M6/sources/neoforge/src/main/java/com/codex/blacklight/military/vehicle/VehicleFleetSystem.java)，完整运行统计参见[根实施记录](../verification/MILITARY6_VEHICLES_20261026_ZH.md)和[玩家指南](../MILITARY6_PLAYER_TEST_ZH.md)。\n\n"
    text += "## 步兵真实多武器与有限弹药\n\n原10种注册身份、军衔、组织与归宿不因换武器变化。直接新生默认入口仍是步枪/重机枪/军官卡宾；新原生基地槽按真实UUID+任务只初始化一次新配装。备弹按弹种保存；枪的弹匣/冷却在各自真实物品上。旧档不会因为掉枪、换装、重登或换副枪免费重发。\n\n"
    text += table(["职责", "主枪", "主弹匣+备弹", "副枪", "副弹匣+备弹"], [[r["duty"], r["primary"]["weapon"], f"{r['primary']['magazine']}+{r['primary']['reserve']}",
                    r["secondary"]["weapon"] if r["secondary"] else "无", f"{r['secondary']['magazine']}+{r['secondary']['reserve']}" if r["secondary"] else "无"] for r in data["infantry_loadouts"]["profiles"]])
    text += "\n一次性原生权重：\n\n" + table(["阵营", "步枪", "卡宾", "机枪", "榴弹", "反装甲"], [[f, *[weights[key] for key in ("RIFLE", "CARBINE", "MACHINE_GUN", "GRENADIER", "ANTI_ARMOR")]] for f, weights in data["infantry_loadouts"]["native_weights"].items()])
    text += "\n8人策略：1卡宾、5步枪、1机枪、1榴弹；明确机械威胁时最后1人换反装甲。12人策略：1卡宾、8步枪、1机枪、1榴弹、1反装甲。这是现有策略工厂，不能据此声称当前会自然生成8/12人车队。导弹、温压、感染枪不进普通随机池。\n\n"
    text += "## 10个active配方与保留的燃料文件\n\n下表列六种完整车辆放置物品与自动炮弹、HE/AP、维修件共10项配方；两个加载器各自JSON原文存于recipes.json及sources。放置物品可逐级合成，不收纳有库存/乘员的已放置车辆。机枪弹、火箭弹及其余步兵弹药配方见[当前玩家指南](../MILITARY6_PLAYER_TEST_ZH.md)。\n\n"
    text += table(["产物", "数量", "实际原料"], [[r["result"], r["count"], r["ingredients"]] for r in data["recipes"] if r["platform"] == "neoforge" and r['active']])
    text += "\n`military_fuel_can.json`保留在磁盘和源码快照，标记inactive，不进入游戏JAR；这不是缺失资源。\n\n"
    text += table(["物品ID", "状态", "原始图标"], [[row['id'], 'active' if row['active'] else 'inactive（保留）', f"![{row['id']} 原sprite](M6/{row['path']})"] for row in data['item_icons']])
    rules = data["garage_rules"]
    text += "\n玩家手动补给须停稳、下车、主手持匹配弹药或维修件近距离右键车体；按实际接收数量扣除，错弹和满额拒收，创造模式也消耗实物。旧燃料罐不消耗、不补给，驾驶无需燃油。\n\n"
    text += f"野外维修已接入三类车：每次成功消耗1个 `blacklight:vehicle_repair_parts`，最多恢复{rules['health_per_repair_part']}HP且不超过本车上限，缺口不足{rules['health_per_repair_part']}HP仍扣1件。满血、超距、仍在移动、已毁/移除车拒收且不扣件；不能复活死车。玩家手动维修不要求车库，不会远程读取箱子。车库自动维修另有完整维修位和真实箱库存条件，两种入口都不产生免费资源。\n\n"
    text += "## 两方真实车库\n\n尺寸均31×10×27，模板地面在局部Y=0、车辆脚点Y=1。三条车道/车位按同一元数据登记；车库不是随便摆一个箱子。新生成基地可在独立地块合规时附加；旧基地仅发现已有记录，不自动翻新。\n\n"
    for garage in data["garages"]["garages"]:
        text += f"### {garage['id']}\n\n"
        text += table(["车位", "中心", "完整维修范围min/max", "真实箱", "门范围"], [[b["chassis"], b["center"], b["repairBounds"], b["container"], b["gateBounds"]] for b in garage["bays"]])
        text += "\n初始箱内实际物品（不会打开刷新）：\n\n"
        text += table(["箱位置", "车位", "物品数量"], [[box["position"], box["bayId"], dict(Counter({key: sum(item["count"] for item in box["items"] if item["id"] == key) for key in {item["id"] for item in box["items"]}}))] for box in garage["supplyContainers"]])
        for suffix in ("exterior", "cutaway"):
            text += f"\n![{garage['id']} {suffix} 真实NBT离线预览，非游戏截图](M6/assets/garages/{garage['id']}-{suffix}.png)\n"
        text += "\n"
    text += f"每车库卡车/APC/坦克各一个一次性原生槽。UNISSUED→RESERVED→ISSUED；真实毁坏记LOST，未知/卸载/开走/被抢或重启不重开。卡车需1名、APC/坦克各需2名原有空闲真实军人步行到位，不生成人员，不改变原16/20驻军名额。首次派发仅给一次初始弹药，后续箱料不刷新，旧FuelTicks不参与玩法。当前自动原生车辆默认加载预算{rules['loaded_native_dispatch_cap']}辆；旧24车压力记录只作历史。路径规划继续使用加载区块和预算，未知道路安全停，不强载追击。\n\n"
    text += f"当前源码实现使用原有空闲真实军人搬运同一实际ItemEntity内的有限弹药/维修件；无军人则不自动转移。低主弹≤{rules['low_primary_ammunition_ratio']:.0%}或HP<{rules['low_health_ratio']:.0%}时，原生无玩家车辆临时返回同维度、已加载、核心有效且有匹配库存的车库；原任务/目标不被RETURN改写，恢复阈值后继续原任务。取消、死亡或卸载应保留/在当地释放同一实物，不远程退款或重造。以上描述是本次源码契约，运行GREEN及真实两进程持久化必须由root的本轮实际证据确认，不能从旧M6.0 PASS推断。\n\n"
    text += "车库自动取料要求同维度同阵营、核心有效、已加载、完整车身和炮管在对应维修位内停稳、箱内有匹配实物。原生槽的定时服务还要求无玩家归属、无真人乘员、当前驻地匹配。`return` 仅为已有原生出处或驻地关联的无玩家归属车寻找并登记可用驻地；普通命令新车没有这些关联时只返车队出发点，不能拿它验证原生自动后勤。玩家底盘车/抢得的车使用持物手动补给和野外维修，AI不自动征用。\n\n"
    text += "核心损毁挡新派发和该处维修；已有车辆保留原生归属与资源。return寻找同维度已加载、同阵营、有效有路且有匹配弹药/维修件的车库；失败停车，不加载远处地形或远程取料。临时停车/驻地不能改写原生槽。\n\n"
    text += "## 重建与核对\n\n以下留给根在最终冻结后执行；列出命令不代表已执行，也不替代游戏验收。模板包含本页交互事实和待填验收表，根刷新后仍须核对最新生产与实际回执：\n\n```powershell\npython -X utf8 tools/archive_military6_catalog.py --neo-root ../neoforge-military6 --forge-root ../forge-military6\npython -X utf8 tools/archive_military6_catalog.py --neo-root ../neoforge-military6 --forge-root ../forge-military6 --verify\n```\n\n生成工具只读生产文件并写独立M6资料；不运行Git/Gradle/服务器，不联网，不修改资源或存档。`--verify`仅核对快照、当前源与PNG哈希，不代表游戏测试通过。生产还在修改时索引会过时；原导出时的核查结果不能证明最终源一致。旧M5源快照与外观证据保持历史。\n\n"
    text += "## 验证证据入口\n\n最终自动验证、性能观察与发行回执见[根实施记录](../verification/MILITARY6_VEHICLES_20261026_ZH.md)最末节及[玩家指南](../MILITARY6_PLAYER_TEST_ZH.md)。本生成器只导出资料，不重新判定游戏或发行PASS；下表说明应核对的真实证据和范围。\n\n"
    text += table(["验收项", "证据入口", "核对内容"], [
        ["双端完整自动验证", "根记录最终冻结表", "最终JUnit、M6 GameTest及M6上M5完整回归的实际发现数、结果、日志与版本"],
        ["普通军人/军官召唤", "最终完整载具GameTest及本页历史定向日志", "半旧Military字段不补发，已有HandItems不复制；历史定向结果不外推全量通过"],
        ["同产物两进程保存与旧M5副本", "根记录及各进程实际JSON", "不同PID、同档prepare/verify、UUID/HP/有限油弹/枪组件/身份/槽不增发"],
        ["3/12/24压力", "根记录六档实测表", "实际硬件、每档车辆/步兵持续移动、tick p50/p95、规划预算和库存边界；24车有尖峰，不承诺20TPS或客户端FPS"],
        ["双端CPU捕获与真人体验", "双端canonical捕获已完成；GL/真人体验待验收", "最终坐姿回执各端8场景9主图，另2诊断图；CPU图不代替GL或真人驾驶"],
        ["资料冻结与最终交付", "根记录及包外SHA256/交付回执", "冻结后刷新快照/核查，最终JAR/资料包路径、SHA256及独立验收结果"],
    ])
    (output.parent / "M6_载具与车库.md").write_text(text, encoding="utf-8")
    (output / "README.md").write_text("# M6独立制作档案\n\n阅读[完整数据卡](../M6_载具与车库.md)。data/保存当前双端结构化数据，sources/保存各条哈希对应原文件。assets/保存9张车辆CPU图（8场景+组合板）、4张实际NBT车库离线图、历史M5的10张人物卡与7张原建筑图。全部30张图均非游戏截图；历史M5图未当作M6重绘。旧M5资料不在此重写。\n", encoding="utf-8")


def verify(args):
    output = args.output.resolve()
    failures = []
    sources = json.loads((output / "data/source-manifest.json").read_text(encoding="utf-8"))
    for row in sources:
        snapshot = output / row["snapshot"]
        root = args.neo_root if row["platform"] == "neoforge" else args.forge_root
        current = root / row["path"]
        if not snapshot.is_file() or sha(snapshot) != row["sha256"]:
            failures.append("snapshot: " + row["snapshot"])
        if not current.is_file() or sha(current) != row["sha256"]:
            failures.append("current source changed: " + row["platform"] + "/" + row["path"])
    previews = json.loads((output / "data/preview-manifest.json").read_text(encoding="utf-8"))
    for platform, root in [('neoforge', args.neo_root), ('forge', args.forge_root)]:
        recorded = {row['path'] for row in sources if row['platform'] == platform}
        for relative in revision_sources(root):
            if relative.as_posix() not in recorded:failures.append('missing revision source: '+platform+'/'+relative.as_posix())
    recipes = json.loads((output/'data/recipes.json').read_text(encoding='utf-8'))
    active_ids = {f'{faction}_{chassis}_chassis' for faction in ('military', 'blackwatch') for chassis in NAMES} | {'autocannon_round', 'tank_he_shell', 'tank_ap_shell', 'vehicle_repair_parts'}
    for platform in ('neoforge', 'forge'):
        rows = [row for row in recipes if row['platform'] == platform]
        if len(rows) != 11 or {row['id'] for row in rows if row.get('active') is True} != active_ids or len([row for row in rows if row.get('id') == 'military_fuel_can' and row.get('active') is False]) != 1:
            failures.append('expected ten active recipes and one preserved inactive fuel source: '+platform)
    rules = json.loads((output/'data/garage_rules.json').read_text(encoding='utf-8'))
    for root in (args.neo_root, args.forge_root):
        actual = garage_rules(*((root/JAVA/'military/vehicle'/name).read_text(encoding='utf-8') for name in
                                ('VehicleGarageService.java', 'VehicleInventory.java', 'VehicleSupplyRules.java')))
        if rules != actual:failures.append('current fuel-disabled ammunition/repair rules changed: '+root.name)
    icons = json.loads((output/'data/item_icons.json').read_text(encoding='utf-8'))
    if len(icons) != 13 or len({row['id'] for row in icons}) != 13:failures.append('inventory icon inventory')
    from PIL import Image
    for row in previews+icons:
        image = output / row["path"]
        if not image.is_file() or sha(image) != row["sha256"]:
            failures.append("preview: " + row["path"])
        elif image.is_file():
            with Image.open(image) as source:
                source.verify()
    for document in [output.parent / "M6_载具与车库.md", output / "README.md", output.parent.parent / "MILITARY6_PLAYER_TEST_ZH.md"]:
        for link in re.findall(r'\]\(([^)]+)\)', document.read_text(encoding="utf-8")):
            if not link.startswith(("https:", "http:", "#")) and not (document.parent / link.split("#")[0]).exists():
                failures.append("broken link: " + document.name + " -> " + link)
    status = "PASS" if not failures else "FAIL"
    print(json.dumps(dict(status=status, catalogue_integrity=status, outputWritten=False, source_snapshots=len(sources),
                          preview_pngs=len(previews), icon_pngs=len(icons), active_recipes_per_loader=10,
                          gameplay_pass_claimed=False, errors=failures), ensure_ascii=False))
    if failures:
        raise SystemExit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--neo-root", type=Path, default=ROOT if ROOT.name.startswith("neoforge") else ROOT.parent / "neoforge-military6")
    parser.add_argument("--forge-root", type=Path, default=ROOT if ROOT.name.startswith("forge") else ROOT.parent / "forge-military6")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/制作资料/M6")
    parser.add_argument("--skip-render", action="store_true", help="Refresh data while preserving already exported garage preview images")
    parser.add_argument("--verify", action="store_true", help="Read-only catalogue integrity check; no gameplay or GL claim")
    args = parser.parse_args()
    verify(args) if args.verify else export(args)


if __name__ == "__main__":
    main()
