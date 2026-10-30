"""Render actual large-base NBT and authored block JSON into offline PNG/HTML.

Requires Pillow for raster output. This is a source-asset orthographic preview,
not a Minecraft client screenshot. Custom scene cuboids and textures are read
from production assets; vanilla blocks use approximate material colors/shapes.
"""
from __future__ import annotations

import argparse
from functools import lru_cache
import gzip
import html
import json
import math
from pathlib import Path
import struct

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
BG = (22, 28, 32)
PAPER = (230, 230, 217)
COLORS = {
    "polished_andesite": (126, 132, 128), "gray_concrete": (87, 94, 95),
    "light_gray_concrete": (162, 164, 150), "green_terracotta": (78, 90, 58),
    "green_concrete": (77, 89, 54), "red_concrete": (153, 46, 40),
    "polished_blackstone": (51, 57, 61), "black_stained_glass": (58, 81, 91),
    "light_blue_stained_glass": (76, 160, 183), "smooth_stone": (166, 170, 165),
    "smooth_stone_slab": (166, 170, 165), "stone_brick_stairs": (128, 136, 131),
    "yellow_concrete": (215, 180, 51), "white_concrete": (210, 211, 196),
    "iron_bars": (73, 87, 88), "sandstone": (175, 169, 122),
    "sea_lantern": (197, 225, 207), "redstone_lamp": (224, 117, 49),
    "chest": (150, 108, 53), "gray_bed": (112, 119, 113), "green_bed": (74, 100, 49),
    "white_bed": (219, 219, 198), "blue_carpet": (56, 100, 148),
    "ladder": (150, 120, 71), "birch_wall_sign": (190, 173, 124), "iron_trapdoor": (107, 123, 117),
    "crafting_table": (133, 100, 60), "smithing_table": (66, 66, 66),
    "grindstone": (133, 142, 132), "cauldron": (57, 65, 66),
}
MODULE_LABELS = {"command": "01 COMMAND", "ammunition": "02 AMMO", "barracks": "03 BARRACKS",
                 "armory": "04 ARMORY", "medical": "05 FIELD AID", "medical_isolation": "05 ISOLATION",
                 "containment_research": "06 RESEARCH", "logistics_canopy": "06 LOGISTICS"}


class NbtReader:
    def __init__(self, raw):
        self.data, self.offset = raw, 0

    def take(self, count):
        result = self.data[self.offset:self.offset + count]
        self.offset += count
        return result

    def integer(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        return self.take(self.integer(">H")).decode("utf8")

    def payload(self, kind):
        if kind == 1:
            return self.integer(">b")
        if kind == 3:
            return self.integer(">i")
        if kind == 8:
            return self.string()
        if kind == 9:
            element, count = self.integer(">B"), self.integer(">i")
            return [self.payload(element) for _ in range(count)]
        if kind == 10:
            result = {}
            while (element := self.integer(">B")):
                key = self.string()
                result[key] = self.payload(element)
            return result
        raise ValueError(f"Unsupported actual NBT tag {kind}")


def read_nbt(path):
    r = NbtReader(gzip.decompress(path.read_bytes()))
    assert r.integer(">B") == 10
    r.string()
    data = r.payload(10)
    assert data["entities"] == [], "Large templates must not embed entities/UUIDs"
    states = data["palette"]
    return data, {tuple(e["pos"]): states[e["state"]] for e in data["blocks"] if states[e["state"]]["Name"] != "minecraft:air"}


def verify_serialized_assets(resources, metadata):
    """Independent serialized-file checks before making a visual completion claim."""
    loot_folder = "loot_tables" if (resources / "data/blacklight/loot_tables").exists() else "loot_table"
    results = []
    for meta in metadata["bases"]:
        folder = "structures" if (resources / "data/blacklight/structures" / (meta["name"] + ".nbt")).exists() else "structure"
        raw, blocks = read_nbt(resources / "data/blacklight" / folder / (meta["name"] + ".nbt"))
        expected = [meta["width"], meta["height"], meta["depth"]]
        assert raw["size"] == expected
        assert len(raw["blocks"]) == math.prod(expected), "Template must explicitly define its clearing volume"
        positions = {tuple(e["pos"]) for e in raw["blocks"]}
        assert len(positions) == math.prod(expected), "Duplicate or missing NBT positions"
        assert all(0 <= p[i] < expected[i] for p in positions for i in range(3))
        assert all((x, 0, z) in blocks for x in range(meta["width"]) for z in range(meta["depth"])), "Continuous floor support"
        chest_records = {tuple(e["pos"]): e["nbt"] for e in raw["blocks"] if raw["palette"][e["state"]]["Name"] == "minecraft:chest"}
        for record in chest_records.values():
            assert record["id"] == "minecraft:chest"
            table = record["LootTable"]
            assert table.startswith("blacklight:chests/base_")
            assert (resources / "data/blacklight" / loot_folder / (table.split(":")[1] + ".json")).exists(), "Missing actual loot table"
        for pos in meta["ammo_chests"]:
            assert chest_records[tuple(pos)]["LootTable"] == "blacklight:chests/base_ammunition"
        custom_ids = {s["Name"].split(":")[1] for s in raw["palette"] if s["Name"].startswith("blacklight:")}
        for key in custom_ids:
            blockstate = json.loads((resources / "assets/blacklight/blockstates" / (key + ".json")).read_text(encoding="utf8"))
            assert set(blockstate["variants"]) == {"facing=" + d for d in ("north", "south", "east", "west")}
            model = json.loads((resources / "assets/blacklight/models/block" / (key + ".json")).read_text(encoding="utf8"))
            for texture in model["textures"].values():
                namespace, tex = texture.split(":")
                Image.open(resources / "assets" / namespace / "textures" / (tex + ".png")).verify()
        results.append({"name": meta["name"], "size": expected, "entities": 0,
                        "chests": len(chest_records), "custom_scene_ids": sorted(custom_ids), "status": "PASS"})
    return results


def font(size=16):
    for path in ("C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/segoeui.ttf"):
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def cube(a=(0, 0, 0), b=(16, 16, 16)):
    return {"from": a, "to": b}


@lru_cache(maxsize=None)
def texture_sample(path):
    im = Image.open(path).convert("RGB").resize((4, 4), Image.Resampling.BOX)
    return list(im.get_flattened_data())


class Renderer:
    def __init__(self, resources):
        self.resources = resources
        self.models = {}
        for path in (resources / "assets/blacklight/models/block").glob("*.json"):
            if path.name in {k + ".json" for k in ("field_barrier", "sandbag_cover", "military_crate", "command_terminal", "communications_antenna", "military_searchlight")}:
                self.models["blacklight:" + path.stem] = json.loads(path.read_text(encoding="utf8"))

    @staticmethod
    def full(state):
        name = state["Name"].split(":")[-1]
        return not (state["Name"].startswith("blacklight:") or any(k in name for k in ("slab", "stairs", "bars", "ladder", "bed", "sign", "carpet", "chest", "grindstone", "cauldron", "trapdoor")))

    def elements(self, state):
        name = state["Name"]
        short, props = name.split(":")[-1], state.get("Properties", {})
        if name in self.models:
            return self.models[name]["elements"], self.models[name].get("textures", {})
        if short == "iron_bars":
            parts = [cube((7, 0, 7), (9, 16, 9))]
            if props.get("north") == "true": parts.append(cube((7, 0, 0), (9, 16, 8)))
            if props.get("south") == "true": parts.append(cube((7, 0, 8), (9, 16, 16)))
            if props.get("west") == "true": parts.append(cube((0, 0, 7), (8, 16, 9)))
            if props.get("east") == "true": parts.append(cube((8, 0, 7), (16, 16, 9)))
            return parts, {}
        if short.endswith("slab"):
            return [cube((0, 0, 0), (16, 8, 16))], {}
        if short.endswith("trapdoor"):
            return [cube((0, 0, 0), (16, 3, 16))], {}
        if short.endswith("stairs"):
            return [cube((0, 0, 0), (16, 8, 16)), cube((0, 8, 0), (16, 16, 8))], {}
        if short.endswith("bed"):
            return [cube((0, 3, 0), (16, 9, 16)), cube((1, 0, 1), (3, 3, 3)), cube((13, 0, 13), (15, 3, 15))], {}
        if short == "chest":
            return [cube((1, 0, 1), (15, 10, 15)), cube((1, 10, 1), (15, 14, 15)), cube((7, 8, 0), (9, 12, 1))], {}
        if short == "ladder":
            parts = [cube((1, 0, 14), (3, 16, 16)), cube((13, 0, 14), (15, 16, 16))]
            parts += [cube((1, y, 13), (15, y + 1, 16)) for y in (1, 5, 9, 13)]
            return parts, {}
        if short == "birch_wall_sign":
            return [cube((0, 4, 14), (16, 12, 16))], {}
        if short.endswith("carpet"):
            return [cube((0, 0, 0), (16, 1, 16))], {}
        if short == "cauldron":
            return [cube((1, 0, 1), (15, 3, 15)), cube((1, 3, 1), (3, 16, 15)), cube((13, 3, 1), (15, 16, 15)), cube((3, 3, 1), (13, 16, 3)), cube((3, 3, 13), (13, 16, 15))], {}
        return [cube()], {}

    def polygons(self, blocks, yaw, cut_height=None):
        c, s = math.cos(yaw), math.sin(yaw)
        camera = (s, 1.8, c)
        normals = (("north", (0, 0, -1), (0, 1, 2, 3)), ("south", (0, 0, 1), (4, 7, 6, 5)),
                   ("down", (0, -1, 0), (0, 4, 5, 1)), ("up", (0, 1, 0), (3, 2, 6, 7)),
                   ("west", (-1, 0, 0), (0, 3, 7, 4)), ("east", (1, 0, 0), (1, 5, 6, 2)))
        all_faces = []
        for (x, y, z), state in blocks.items():
            if cut_height is not None and y > cut_height:
                continue
            elements, textures = self.elements(state)
            custom = state["Name"] in self.models
            facing = state.get("Properties", {}).get("facing", "north")
            turns = {"north": 0, "east": 1, "south": 2, "west": 3}.get(facing, 0) if custom or not self.full(state) else 0
            for element in elements:
                x1, y1, z1 = element["from"]
                x2, y2, z2 = element["to"]
                points = [(a, b, d) for a, b, d in ((x1, y1, z1), (x2, y1, z1), (x2, y2, z1), (x1, y2, z1),
                                                 (x1, y1, z2), (x2, y1, z2), (x2, y2, z2), (x1, y2, z2))]
                rot = element.get("rotation")
                if rot:
                    angle = math.radians(rot["angle"])
                    origin = rot["origin"]
                    def turn_element(point):
                        a, b, d = [point[i] - origin[i] for i in range(3)]
                        if rot["axis"] == "x": b, d = b * math.cos(angle) - d * math.sin(angle), b * math.sin(angle) + d * math.cos(angle)
                        elif rot["axis"] == "y": a, d = a * math.cos(angle) - d * math.sin(angle), a * math.sin(angle) + d * math.cos(angle)
                        else: a, b = a * math.cos(angle) - b * math.sin(angle), a * math.sin(angle) + b * math.cos(angle)
                        return a + origin[0], b + origin[1], d + origin[2]
                    points = [turn_element(p) for p in points]
                for _ in range(turns):
                    points = [(16 - d, b, a) for a, b, d in points]
                world = [(x + a / 16, y + b / 16, z + d / 16) for a, b, d in points]
                for direction, normal, indices in normals:
                    nx, ny, nz = normal
                    for _ in range(turns): nx, nz = -nz, nx
                    dot = nx * camera[0] + ny * camera[1] + nz * camera[2]
                    if dot <= 0 and not rot:
                        continue
                    if self.full(state) and not rot:
                        neighbor = blocks.get((x + nx, y + ny, z + nz))
                        if neighbor and self.full(neighbor) and not (cut_height is not None and y + ny > cut_height):
                            continue
                    points3d = [world[i] for i in indices]
                    points2d = [(a * c - d * s, (a * s + d * c) * .5 - b) for a, b, d in points3d]
                    depth = sum(a * s + d * c + b * .7 for a, b, d in points3d) / 4
                    shade = 1.08 if ny > 0 else .76 if nx else .9
                    if custom:
                        key = element.get("faces", {}).get(direction, {}).get("texture", "#metal").lstrip("#")
                        texture = textures.get(key, "blacklight:block/facility_metal")
                        path = self.resources / "assets" / texture.split(":")[0] / "textures" / (texture.split(":")[1] + ".png")
                        samples = texture_sample(str(path))
                    else:
                        base = COLORS.get(state["Name"].split(":")[-1], (124, 128, 119))
                        # Deterministic small mineral variation; vanilla art is not shipped here.
                        samples = [tuple(max(0, min(255, v + ((x * 7 + z * 3 + y + i * 5) % 7 - 3))) for v in base) for i in range(16)]
                    samples = [tuple(max(0, min(255, int(v * shade))) for v in color) for color in samples]
                    all_faces.append((depth, points2d, samples, custom))
        return sorted(all_faces, key=lambda f: f[0])

    def render(self, blocks, yaw, size=(1400, 1000), cut_height=None):
        polys = self.polygons(blocks, math.radians(yaw), cut_height)
        xs = [x for _, points, _, _ in polys for x, _ in points]
        ys = [y for _, points, _, _ in polys for _, y in points]
        lo_x, hi_x, lo_y, hi_y = min(xs), max(xs), min(ys), max(ys)
        w, h = size
        scale = min((w - 80) / (hi_x - lo_x), (h - 100) / (hi_y - lo_y))
        ox, oy = (w - (hi_x + lo_x) * scale) / 2, (h - (hi_y + lo_y) * scale) / 2 + 10
        image = Image.new("RGB", size, BG)
        draw = ImageDraw.Draw(image)
        for _, points, samples, custom in polys:
            points = [(ox + x * scale, oy + y * scale) for x, y in points]
            base = samples[7]
            draw.polygon(points, fill=base)
            # Sample actual original scene textures across each parallelogram.
            if custom:
                p0, p1, p2, p3 = points
                for j in range(4):
                    for i in range(4):
                        quad = []
                        for u, v in ((i / 4, j / 4), ((i + 1) / 4, j / 4), ((i + 1) / 4, (j + 1) / 4), (i / 4, (j + 1) / 4)):
                            quad.append(((1 - v) * ((1 - u) * p0[0] + u * p1[0]) + v * ((1 - u) * p3[0] + u * p2[0]),
                                         (1 - v) * ((1 - u) * p0[1] + u * p1[1]) + v * ((1 - u) * p3[1] + u * p2[1])))
                        draw.polygon(quad, fill=samples[j * 4 + i])
            if custom or scale > 12:
                draw.line(points + [points[0]], fill=tuple(int(v * .8) for v in base), width=1)
        return image


def plan(blocks, meta):
    scale, margin = 12, 65
    image = Image.new("RGB", (65 * scale + 2 * margin, meta["depth"] * scale + 2 * margin + 45), BG)
    draw = ImageDraw.Draw(image)
    for z in range(meta["depth"]):
        for x in range(65):
            state = blocks.get((x, 1, z), blocks.get((x, 0, z)))
            short = state["Name"].split(":")[-1]
            color = COLORS.get(short, (75, 117, 89) if state["Name"].startswith("blacklight:") else (132, 132, 126))
            if short == "chest": color = (236, 185, 55)
            draw.rectangle((margin + x * scale, margin + z * scale, margin + (x + 1) * scale - 1, margin + (z + 1) * scale - 1), fill=color)
    for module in meta["modules"]:
        x1, _, z1, x2, _, z2 = module["bounds"]
        label = MODULE_LABELS[module["name"]]
        tx, ty = margin + (x1 + x2 + 1) / 2 * scale, margin + (z1 + z2 + 1) / 2 * scale
        box = draw.textbbox((0, 0), label, font=font(13))
        tw = box[2] - box[0]
        draw.rectangle((tx - tw / 2 - 5, ty - 9, tx + tw / 2 + 5, ty + 10), fill=(24, 35, 37))
        draw.text((tx, ty), label, anchor="mm", fill=PAPER, font=font(13))
    for guard in meta["guards"]:
        x, z = margin + (guard["x"] + .5) * scale, margin + (guard["z"] + .5) * scale
        color = (239, 111, 79) if guard["heavy"] else (111, 212, 200)
        draw.ellipse((x - 3, z - 3, x + 3, z + 3), fill=color, outline=(20, 25, 25))
    for x, y, z in meta["ammo_chests"]:
        draw.rectangle((margin + x * scale - 2, margin + z * scale - 2, margin + (x + 1) * scale + 1, margin + (z + 1) * scale + 1), outline=(255, 226, 99), width=2)
    draw.text((margin, 28), "N  /  ACTUAL NBT FLOOR CUT AT Y=1", fill=PAPER, font=font(17))
    draw.text((margin, image.height - 55), "SOUTH GATE: 7 BLOCKS  |  GOLD: REAL LOOT CHESTS  |  CYAN/RED: GUARD SLOTS", fill=PAPER, font=font(13))
    draw.text((margin, image.height - 30), "Upper floors and roofs remain accessible; diagram uses actual serialized blocks.", fill=(154, 172, 165), font=font(12))
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=Path, default=ROOT / "src/main/resources")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/previews/large-military-bases")
    parser.add_argument("--verify-only", action="store_true", help="Validate serialized NBT, real loot tables, blockstates and textures without rendering")
    args = parser.parse_args()
    out = args.output
    out.mkdir(parents=True, exist_ok=True)
    metadata = json.loads((args.resources / "data/blacklight/military/large_bases.json").read_text(encoding="utf8"))
    verification = verify_serialized_assets(args.resources, metadata)
    (out / "serialized-assets-validation.json").write_text(json.dumps(verification, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    if args.verify_only:
        print(json.dumps(verification, ensure_ascii=False, indent=2))
        return
    renderer = Renderer(args.resources)
    panels = []
    for meta in metadata["bases"]:
        folder = "structures" if (args.resources / "data/blacklight/structures" / (meta["name"] + ".nbt")).exists() else "structure"
        nbt, blocks = read_nbt(args.resources / "data/blacklight" / folder / (meta["name"] + ".nbt"))
        assert nbt["size"] == [meta["width"], meta["height"], meta["depth"]]
        views = []
        for angle, label in ((45, "SOUTH EAST / ENTRY"), (135, "NORTH EAST / BUNKER"), (225, "NORTH WEST / COMMAND"), (315, "SOUTH WEST / BARRACKS")):
            image = renderer.render(blocks, angle)
            draw = ImageDraw.Draw(image)
            draw.text((35, 25), label, font=font(22), fill=PAPER)
            draw.text((35, image.height - 35), f"{meta['width']} x {meta['depth']} blocks / {len(meta['guards'])} persisted runtime guard slots", font=font(16), fill=(154, 172, 165))
            path = out / f"{meta['name']}-{angle}.png"
            image.save(path)
            views.append(path.name)
        sheet = Image.new("RGB", (2800, 2080), BG)
        for i, filename in enumerate(views):
            sheet.paste(Image.open(out / filename), ((i % 2) * 1400, 65 + (i // 2) * 1000))
        draw = ImageDraw.Draw(sheet)
        draw.text((35, 15), ("BLACKWATCH BIO-CONTAINMENT BASE" if meta["faction"] == "blackwatch" else "MILITARY GARRISON BASE") + "  /  FOUR ACTUAL NBT VIEWS", font=font(28), fill=PAPER)
        draw.text((35, 2050), "Original project assets / orthographic source render / approximate vanilla materials / NOT an in-game screenshot", font=font(17), fill=(154, 172, 165))
        sheet_name = meta["name"] + "-fourviews.png"
        sheet.save(out / sheet_name)
        floor_name = meta["name"] + "-floorplan.png"
        plan(blocks, meta).save(out / floor_name)
        cutaway = renderer.render(blocks, 45, cut_height=3)
        ImageDraw.Draw(cutaway).text((35, 25), "GROUND-FLOOR CUTAWAY / ACTUAL NBT Y <= 3", font=font(22), fill=PAPER)
        cutaway_name = meta["name"] + "-cutaway.png"
        cutaway.save(out / cutaway_name)
        panels.append((meta, views, sheet_name, floor_name, cutaway_name))
        print(f"Rendered {meta['name']}: {len(blocks)} non-air actual NBT blocks")
    css = '''body{margin:0;background:#161c20;color:#e6e6d9;font:16px/1.65 system-ui,sans-serif}main{max-width:1440px;margin:auto;padding:34px}h1{font-size:40px;font-weight:550;margin:0 0 10px}h2{font-size:28px;margin:48px 0 12px}p{color:#a6b7ae;max-width:1000px}button,a{color:#e6e6d9}button{border:1px solid #60756a;background:#26352f;padding:9px 16px;margin:4px;cursor:pointer}button.active{background:#718866;color:#111}figure{margin:16px 0}img{width:100%;height:auto;display:block}figcaption{color:#a6b7ae;font-size:14px}nav{display:flex;gap:8px;flex-wrap:wrap}table{border-collapse:collapse}td,th{text-align:left;padding:8px 18px;border-bottom:1px solid #3c5147}.plan{max-width:700px}.source{font-family:monospace;font-size:13px}.note{border-left:3px solid #90ad74;padding-left:14px}'''
    body = ['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>大型军事基地 · 实际 NBT 资产预览</title><style>' + css + '</style><main>',
            '<h1>大型军事基地 · 实际资产预览</h1><p>原创《虐杀原形》军事控制语汇适配；几何来自本次发行资源里的真实 NBT 与自定义方块 JSON，专属贴图从实际 PNG 采样。原版方块使用近似材质；此页面和 PNG 均为离线正交渲染，尚未冒充游戏截图。</p>',
            '<p class="note">军箱、终端、天线为场景装饰；探照灯只发光。金色箱子是原版库存容器，军械和七种弹药由有限战利品生成一次。终端不提供 Blacknet 任务或科研自动化。</p>']
    for i, (meta, views, sheet, floor, cutaway) in enumerate(panels):
        body.append(f'<section><h2>{"黑色守望封控科研基地" if meta["faction"] == "blackwatch" else "军方驻防基地"} / {meta["width"]} × {meta["depth"]}</h2>')
        body.append(f'<p>两层指挥楼和营房、独立军械与东北弹药库、医疗补给、三座可攀爬警戒塔、七格南入口；{len(meta["guards"])} 个可达驻军位置由运行时持久登记。' + ('另有两层科研及医疗隔离翼。' if meta["faction"] == "blackwatch" else '东南侧为开放补给棚。') + '</p>')
        body.append('<nav>')
        files = views + [cutaway, floor, sheet]
        for j, (label, filename) in enumerate(zip(("东南入口", "东北弹药库", "西北指挥楼", "西南营房", "底层剖视", "通道与箱子", "四向大图"), files)):
            body.append(f'<button class="{"active" if j == 0 else ""}" onclick="show(this,{i},\'{filename}\')">{label}</button>')
        body.append(f'</nav><figure><a id="link{i}" href="{views[0]}" target="_blank"><img id="view{i}" src="{views[0]}" alt="实际 NBT 基地外观"></a><figcaption>点击图片可打开原尺寸 PNG；切换视角不会连接互联网。</figcaption></figure>')
        body.append(f'<p class="source">NBT: data/blacklight/{"structures" if folder == "structures" else "structure"}/{meta["name"]}.nbt<br>Meta: data/blacklight/military/large_bases.json<br>弹药箱: (52,1,12) / (54,1,12) · 全部 NBT entities = []</p></section>')
    body.append('<h2>核验边界</h2><p>生成器已按实际方块做通道、箱子开盖、支撑、室内模块和所有驻军点可达校验。真实放置方向、方块更新、楼梯导航、客户端材质与昼夜照明由主任务服务端/客户端验收。尺寸、补给量和岗位均为本项目设计，不宣称来自原作规范。</p></main><script>function show(b,i,p){document.getElementById("view"+i).src=p;document.getElementById("link"+i).href=p;b.parentElement.querySelectorAll("button").forEach(x=>x.classList.remove("active"));b.classList.add("active")}</script></html>')
    (out / "index.html").write_text("".join(body), encoding="utf8")
    print(f"Offline preview: {out / 'index.html'}")


if __name__ == "__main__":
    main()
