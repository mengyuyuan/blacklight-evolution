"""Refresh the offline production catalogue, never modify runtime assets or manual captures.

Run from any directory with Python 3 + Pillow + NumPy. Reads both loader trees.
Generated output is owned by this tool; 实机截图 and 参考模型 are append/preserve only.
No Minecraft, Gradle, NBT command, network, or external bbmodel texture path is executed.
"""
from __future__ import annotations

import argparse
import base64
from collections import Counter
import csv
from datetime import datetime
import gzip
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path('src/main/java/com/codex/blacklight')
RES = Path('src/main/resources')
FACILITY_IDS = ['field_barrier', 'sandbag_cover', 'military_crate', 'command_terminal', 'communications_antenna', 'military_searchlight']
FACTIONS = ['military', 'blackwatch']
SUFFIXES = ['soldier', 'heavy', 'team_leader', 'officer', 'commander']
RANKS = ['private', 'corporal', 'sergeant', 'lieutenant', 'captain', 'major', 'colonel', 'general']
SCREENSHOTS = {
    'codex-clipboard-6fd37604-bea0-4be4-ae2f-86741e8c69df.png': '01-持枪比例反馈.png',
    'codex-clipboard-ad8cbc00-8ce9-4eab-853b-f54584395d95.png': '02-开火遮挡反馈.png',
    'codex-clipboard-0264f90d-2067-46d4-8665-97edd5eef48e.png': '03-基地零驻军反馈.png',
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding='utf-8')


def dump(path, obj):
    write(path, json.dumps(obj, ensure_ascii=False, indent=2) + '\n')


def table(headers, rows):
    def cell(v):
        if isinstance(v, (dict, list)):
            v = json.dumps(v, ensure_ascii=False, separators=(',', ':'))
        return str(v).replace('|', '\\|').replace('\n', '<br>')
    return '\n'.join(['| ' + ' | '.join(map(cell, headers)) + ' |', '| ' + ' | '.join(['---'] * len(headers)) + ' |'] +
                     ['| ' + ' | '.join(map(cell, row)) + ' |' for row in rows]) + '\n'


def csv_file(path, records):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not records:
        return
    keys = list(dict.fromkeys(k for record in records for k in record))
    with path.open('w', encoding='utf-8-sig', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=keys)
        writer.writeheader()
        for record in records:
            writer.writerow({k: json.dumps(v, ensure_ascii=False) if isinstance(v, (list, dict)) else v for k, v in record.items()})


def git(root):
    def run(*args):
        result = subprocess.run(['git', '-C', str(root), *args], capture_output=True, encoding='utf-8', errors='replace')
        return result.stdout.strip() if result.returncode == 0 else 'UNKNOWN'
    return {'path': str(root), 'branch': run('branch', '--show-current'), 'head': run('rev-parse', 'HEAD'),
            'dirty': run('status', '--porcelain') != '', 'status_porcelain': run('status', '--porcelain'),
            'origin': run('remote', 'get-url', 'origin')}


class NbtReader:
    """All 12 Java NBT payload tags, with length/EOF checks; read only, no commands."""
    def __init__(self, data):
        self.data, self.offset = data, 0

    def take(self, count):
        if count < 0 or self.offset + count > len(self.data):
            raise ValueError('NBT length/EOF')
        result = self.data[self.offset:self.offset + count]
        self.offset += count
        return result

    def number(self, fmt):
        return struct.unpack('>' + fmt, self.take(struct.calcsize('>' + fmt)))[0]

    def length(self):
        n = self.number('i')
        if n < 0 or n > 20_000_000:
            raise ValueError('NBT array/list bound')
        return n

    def string(self):
        # Minecraft's modified UTF-8 ASCII/BMP names in these templates are valid UTF-8.
        return self.take(self.number('H')).decode('utf-8')

    def payload(self, tag, depth=0):
        if depth > 128:
            raise ValueError('NBT depth bound')
        if tag in {1, 2, 3, 4, 5, 6}:
            return self.number({1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[tag])
        if tag == 7:
            return [self.number('b') for _ in range(self.length())]
        if tag == 8:
            return self.string()
        if tag == 9:
            element, n = self.number('B'), self.length()
            return [self.payload(element, depth + 1) for _ in range(n)]
        if tag == 10:
            result = {}
            while (element := self.number('B')) != 0:
                key = self.string()
                result[key] = self.payload(element, depth + 1)
            return result
        if tag in (11, 12):
            return [self.number('i' if tag == 11 else 'q') for _ in range(self.length())]
        raise ValueError(f'Unsupported NBT tag {tag}')


def read_nbt(path):
    raw = path.read_bytes()
    r = NbtReader(gzip.decompress(raw) if raw.startswith(b'\x1f\x8b') else raw)
    if r.number('B') != 10:
        raise ValueError('Expected root compound')
    r.string()
    data = r.payload(10)
    if r.offset != len(r.data):
        raise ValueError('Unexpected bytes after NBT root')
    palette = data.get('palette') or data.get('palettes', [[]])[0]
    blocks = {tuple(b['pos']): palette[b['state']] for b in data['blocks'] if palette[b['state']]['Name'] != 'minecraft:air'}
    return data, blocks


def split_args(text):
    parts, start, depth, quoted = [], 0, 0, False
    for i, ch in enumerate(text):
        if ch == '"' and (i == 0 or text[i - 1] != '\\'):
            quoted = not quoted
        if not quoted:
            depth += ch in '([{'
            depth -= ch in ')]}'
            if ch == ',' and depth == 0:
                parts.append(text[start:i].strip())
                start = i + 1
    return parts + [text[start:].strip()]


def number(text):
    return float(text.strip().rstrip('fFdD'))


def profile_value(expression, heavy):
    if '?' in expression:
        match = re.fullmatch(r'heavy\s*\?\s*([^:]+):\s*(.+)', expression)
        if not match:
            raise ValueError('Profile schema changed: ' + expression)
        expression = match.group(1 if heavy else 2)
    return number(expression)


def creature_number(expression, kind, hunter_constants):
    """Evaluate only the numeric/enum ternaries actually used in attributes, never Java."""
    expression = expression.strip()
    if expression.startswith('HunterBalance.'):
        return hunter_constants[expression.split('.')[1]]
    if '?' in expression:
        condition, rest = expression.split('?', 1)
        true, false = rest.split(':', 1)
        match = re.fullmatch(r'k\s*==\s*Kind\.(\w+)', condition.strip())
        if not match:
            raise ValueError('Unsupported creature attribute expression: ' + expression)
        return creature_number(true if kind == match[1] else false, kind, hunter_constants)
    return number(expression)


def render_json_model(resources, model, size=(900, 650), yaw=315):
    """Single-model orthographic cuboids with actual per-face UV, no GL/display animation."""
    import numpy as np
    from PIL import Image, ImageDraw
    angle = math.radians(yaw)
    c, s = math.cos(angle), math.sin(angle)
    camera = np.array([s, 1.8, c])
    normals = [('north', (0, 0, -1), (0, 1, 2, 3)), ('south', (0, 0, 1), (4, 7, 6, 5)),
               ('down', (0, -1, 0), (0, 4, 5, 1)), ('up', (0, 1, 0), (3, 2, 6, 7)),
               ('west', (-1, 0, 0), (0, 3, 7, 4)), ('east', (1, 0, 0), (1, 5, 6, 2))]
    texture_cache, polygons = {}, []
    for element in model.get('elements', []):
        x1, y1, z1 = element['from']
        x2, y2, z2 = element['to']
        points = np.array([(x1, y1, z1), (x2, y1, z1), (x2, y2, z1), (x1, y2, z1),
                           (x1, y1, z2), (x2, y1, z2), (x2, y2, z2), (x1, y2, z2)], dtype=float)
        rotation = np.eye(3)
        if (r := element.get('rotation')):
            a = math.radians(r['angle'])
            cs, sn = math.cos(a), math.sin(a)
            rotation = {'x': [[1, 0, 0], [0, cs, -sn], [0, sn, cs]],
                        'y': [[cs, 0, sn], [0, 1, 0], [-sn, 0, cs]],
                        'z': [[cs, -sn, 0], [sn, cs, 0], [0, 0, 1]]}[r['axis']]
            rotation = np.array(rotation)
            local = points - np.array(r['origin'])
            if r.get('rescale'):
                axis = 'xyz'.index(r['axis'])
                for i in range(3):
                    if i != axis:
                        local[:, i] /= max(abs(cs), .001)
            points = local @ rotation.T + np.array(r['origin'])
        for direction, normal, indices in normals:
            face = element.get('faces', {}).get(direction)
            if face is None:
                continue
            n = rotation @ np.array(normal)
            if n @ camera <= 0:
                continue
            texture = face.get('texture', '#particle')
            for _ in range(16):
                if not texture.startswith('#'):
                    break
                texture = model['textures'][texture[1:]]
            namespace, texture_path = texture.split(':', 1)
            if namespace != 'blacklight' or '..' in Path(texture_path).parts:
                raise ValueError('Offline model preview only reads blacklight production textures')
            path = resources / 'assets' / namespace / 'textures' / (texture_path + '.png')
            if path not in texture_cache:
                texture_cache[path] = Image.open(path).convert('RGB')
            texture_image = texture_cache[path]
            uv = face.get('uv', [0, 0, 16, 16])
            extent = [uv[0] * texture_image.width / 16, uv[1] * texture_image.height / 16,
                      uv[2] * texture_image.width / 16, uv[3] * texture_image.height / 16]
            samples = texture_image.transform((4, 4), Image.Transform.EXTENT, extent, Image.Resampling.NEAREST)
            if face.get('rotation'):
                samples = samples.rotate(-face['rotation'])
            shade = .72 + .28 * max(0., float(n @ (camera / np.linalg.norm(camera))))
            colors = [tuple(int(v * shade) for v in color) for color in samples.get_flattened_data()]
            pts = points[list(indices)]
            projected = [(p[0] * c - p[2] * s, (p[0] * s + p[2] * c) * .5 - p[1]) for p in pts]
            depth = sum(p[0] * s + p[2] * c + p[1] * .7 for p in pts) / 4
            polygons.append((depth, projected, colors))
    if not polygons:
        raise ValueError('No visible model cuboids')
    xs = [x for _, points, _ in polygons for x, _ in points]
    ys = [y for _, points, _ in polygons for _, y in points]
    width, height = size
    scale = min((width - 80) / (max(xs) - min(xs)), (height - 80) / (max(ys) - min(ys)))
    origin = ((width - (max(xs) + min(xs)) * scale) / 2, (height - (max(ys) + min(ys)) * scale) / 2)
    output = Image.new('RGB', size, (229, 233, 226))
    draw = ImageDraw.Draw(output)
    for _, points, samples in sorted(polygons, key=lambda p: p[0]):
        p0, p1, p2, p3 = [(origin[0] + x * scale, origin[1] + y * scale) for x, y in points]
        for j in range(4):
            for i in range(4):
                quad = []
                for u, v in [(i / 4, j / 4), ((i + 1) / 4, j / 4), ((i + 1) / 4, (j + 1) / 4), (i / 4, (j + 1) / 4)]:
                    quad.append(((1-v)*((1-u)*p0[0]+u*p1[0])+v*((1-u)*p3[0]+u*p2[0]),
                                 (1-v)*((1-u)*p0[1]+u*p1[1])+v*((1-u)*p3[1]+u*p2[1])))
                draw.polygon(quad, fill=samples[j * 4 + i])
    return output


class Archive:
    def __init__(self, output, forge):
        self.output, self.forge = output, forge
        self.sources, self.source_keys = [], set()
        self.preview_records = []

    def source(self, root, relative):
        relative = Path(relative)
        path = root / relative
        if not path.is_file():
            raise FileNotFoundError(path)
        platform = 'neoforge' if root == ROOT else 'forge'
        key = (platform, relative.as_posix())
        if key not in self.source_keys:
            dest = Path('sources') / platform / relative
            target = self.output / dest
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, target)
            self.sources.append({'platform': platform, 'path': relative.as_posix(), 'snapshot': dest.as_posix(),
                                 'sha256': sha(target), 'bytes': target.stat().st_size})
            self.source_keys.add(key)
        return path

    def text(self, relative, root=ROOT):
        return self.source(root, relative).read_text(encoding='utf-8-sig')

    def json(self, relative, root=ROOT):
        return json.loads(self.text(relative, root))

    def image_record(self, path, inputs, label):
        self.preview_records.append({'path': path.relative_to(self.output).as_posix(), 'sha256': sha(path),
                                     'kind': label, 'sources': [{'path': str(p.relative_to(ROOT)).replace('\\', '/'), 'sha256': sha(p)} for p in inputs]})

    def weapon_data(self, root):
        text = self.text(JAVA / 'firearms/WeaponCatalog.java', root)
        result = []
        for match in re.finditer(r'\b(\w+)\s*=\s*new WeaponDefinition\(([^;]+)\);', text):
            args = split_args(match[2])
            if len(args) != 9:
                raise ValueError('WeaponDefinition schema changed')
            result.append(dict(symbol=match[1], id=args[0].strip('"'), ammo_id=args[1].strip('"'),
                               capacity=int(args[2]), cooldown_ticks=int(args[3]), reload_ticks=int(args[4]),
                               player_base_damage=number(args[5]), range_blocks=number(args[6]),
                               kind=args[7].split('.')[-1], radius_blocks=number(args[8])))
        if len(result) != 8 or len({w['ammo_id'] for w in result}) != 7:
            raise ValueError('Expected eight weapons and seven ammo types; review catalogue schema')
        return result

    def soldiers(self, weapons, lang):
        source = self.text(JAVA / 'military/MilitaryCombatProfile.java')
        calls = re.findall(r'new MilitaryCombatProfile\((.*?)\);', source, re.S)
        if len(calls) != 2:
            raise ValueError('Military profile schema changed')
        role_source = self.text(JAVA / 'military/MilitaryRole.java')
        defaults = {role.lower(): rank.lower() for role, rank in re.findall(r'(TROOPER|TEAM_LEADER|OFFICER|COMMANDER)\(MilitaryRank\.(\w+)\)', role_source)}
        registry = self.text(JAVA / 'registry/ModEntities.java')
        weapon_map = {w['id']: w for w in weapons}
        result = []
        for faction in FACTIONS:
            for suffix in SUFFIXES:
                id_ = faction + '_' + suffix
                role = suffix if suffix in ['team_leader', 'officer', 'commander'] else 'trooper'
                heavy = suffix == 'heavy'
                values = [profile_value(x, heavy) for x in split_args(calls[FACTIONS.index(faction)])]
                if len(values) != 7:
                    raise ValueError('Profile fields changed')
                registered = re.search(r'ENTITIES.register\("' + id_ + r'"[^\n]+\.sized\(([^,]+),([^\)]+)\)', registry)
                if not registered:
                    raise ValueError('Entity registration changed: ' + id_)
                gun = 'military_carbine' if role in ['officer', 'commander'] else 'military_machine_gun' if heavy else 'military_rifle'
                w = weapon_map[gun]
                result.append(dict(id='blacklight:' + id_, name=lang.get('entity.blacklight.' + id_, id_), faction=faction,
                                   role=role, spawn_default_rank=defaults[role], heavy=heavy,
                                   health=values[0], armor=values[1], npc_effective_bullet_damage=values[2],
                                   movement_speed=values[3], knockback_resistance=values[4], aim_ticks=int(values[5]), cycle_ticks=int(values[6]),
                                   burst_shot_ages=[int(values[5]) + x for x in [0, 8, 16]], burst_end_ticks=int(values[5]) + 17,
                                   attack_attribute=4 if heavy else 2, follow_range=28,
                                   registered_width=number(registered[1]), registered_height=number(registered[2]),
                                   initial_weapon=gun, initial_magazine=w['capacity'], initial_reserve=w['capacity'] * 3,
                                   npc_scale=values[2] / w['player_base_damage'],
                                   texture='src/main/resources/assets/blacklight/textures/entity/' + faction + '_soldier_layered.png',
                                   model='src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java',
                                   preview='assets/兵种/' + id_ + '.png'))
        return result

    def structures(self, root=ROOT):
        metadata = self.json(RES / 'data/blacklight/military/large_bases.json', root)
        bases = {b['name']: b for b in metadata['bases']}
        rows = []
        platform = 'neoforge' if root == ROOT else 'forge'
        directory = 'structure' if platform == 'neoforge' else 'structures'
        for path in sorted((root / RES / 'data/blacklight' / directory).glob('*.nbt')):
            self.source(root, path.relative_to(root))
            nbt, blocks = read_nbt(path)
            palette = nbt.get('palette') or nbt['palettes'][0]
            chests, block_entities = [], []
            for block in nbt['blocks']:
                state = palette[block['state']]
                if 'nbt' in block:
                    record = {'pos': block['pos'], 'block': state, 'nbt': block['nbt']}
                    block_entities.append(record)
                    if state['Name'] in ('minecraft:chest', 'minecraft:trapped_chest'):
                        chests.append(record)
            embedded = [{'pos': e.get('pos'), 'blockPos': e.get('blockPos'), 'id': e.get('nbt', {}).get('id'),
                         'nbt': e.get('nbt', {})} for e in nbt.get('entities', [])]
            base = bases.get(path.stem)
            guards = []
            if base:
                for i, original in enumerate(base['guards']):
                    g = dict(original)
                    role = 'team_leader' if i == 0 else 'officer' if g['role'] == 'command' else 'commander' if g['role'] == 'operations' else 'trooper'
                    rank = {'team_leader': 'sergeant', 'officer': 'lieutenant', 'commander': 'major'}.get(role, 'corporal' if g['heavy'] else 'private')
                    suffix = role if role != 'trooper' else 'heavy' if g['heavy'] else 'soldier'
                    guards.append(dict(g, slot=i, effective_role=role, effective_rank=rank,
                                       registered_entity='blacklight:' + base['faction'] + '_' + suffix,
                                       checkpoint_override=i < 2,
                                       effective_weapon='military_carbine' if role in ('officer', 'commander') else 'military_machine_gun' if g['heavy'] else 'military_rifle'))
            row = dict(id=path.stem, platform=platform, size=nbt['size'], data_version=nbt.get('DataVersion'), serialized_blocks=len(nbt['blocks']),
                       non_air_blocks=len(blocks), palette_size=len(palette), embedded_entity_count=len(embedded),
                       embedded_entities=embedded, chests=chests, block_entities=block_entities,
                       runtime_slots=len(guards), runtime_guards=guards, large_base_metadata=base,
                       source=path.relative_to(root).as_posix(), sha256=sha(path),
                       preview='assets/建筑/' + path.stem + '.png')
            data_folder = self.output / 'data/nbt' if root == ROOT else self.output / 'data/nbt/forge'
            dump(data_folder / (path.stem + '.json'), dict(row, palette=palette,
                 block_counts=dict(Counter(state['Name'] for state in blocks.values()))))
            rows.append(row)
        if len(rows) != 7:
            raise ValueError('Expected seven production templates')
        return rows

    def facilities(self, lang):
        src = self.text(JAVA / 'registry/MilitaryFacilityBlocks.java')
        strength = re.search(r'\.strength\(([^,]+),\s*([^\)]+)\)', src)
        if not strength:
            raise ValueError('Facility strength schema changed')
        return [dict(id='blacklight:' + m[1], name=lang.get('block.blacklight.' + m[1], m[1]), light=int(m[2]),
                     north_collision_box=[number(n) for n in split_args(m[3])], strength=number(strength[1]), explosion_resistance=number(strength[2]),
                     functional='固定光照' if int(m[2]) else '碰撞/掩体/装饰',
                     model='src/main/resources/assets/blacklight/models/block/' + m[1] + '.json',
                     preview='assets/场景块/' + m[1] + '.png')
                for m in re.finditer(r'prop\("([^"]+)",\s*(\d+),\s*Block.box\(([^)]+)\)\)', src)]

    def raw_resources(self, weapons):
        recipes, loot, worldgen = [], [], []
        for root in [ROOT, self.forge]:
            platform = 'neoforge' if root == ROOT else 'forge'
            data = root / RES / 'data/blacklight'
            for directory in ['recipe', 'recipes', 'loot_table', 'loot_tables', 'worldgen']:
                for path in sorted((data / directory).rglob('*.json')):
                    obj = self.json(path.relative_to(root), root)
                    item = {'platform': platform, 'id': path.relative_to(data / directory).with_suffix('').as_posix(),
                            'source': path.relative_to(root).as_posix(), 'sha256': sha(path), 'raw': obj}
                    if directory.startswith('recipe'):
                        item['result'] = obj.get('result')
                        recipes.append(item)
                    elif directory.startswith('loot'):
                        loot.append(item)
                    else:
                        worldgen.append(item)
            structure_dir = 'structure' if platform == 'neoforge' else 'structures'
            for path in sorted((data / structure_dir).glob('*.nbt')):
                self.source(root, path.relative_to(root))
            for relative in ['data/blacklight/military/large_bases.json', 'assets/blacklight/lang/zh_cn.json']:
                self.source(root, RES / relative)
            for directory in ['entity', 'military', 'firearms', 'city']:
                for path in sorted((root / JAVA / directory).glob('*.java')):
                    self.source(root, path.relative_to(root))
            for relative in ['registry/ModEntities.java', 'registry/MilitaryFacilityBlocks.java', 'command/MilitaryCommands.java',
                             'command/CityMilitaryCommands.java', 'command/BlacklightCommands.java', 'client/model/BlackwatchModel.java',
                             'client/renderer/BlackwatchRenderer.java', 'combat/HunterBalance.java', 'world/ColossalHiveLayout.java',
                             'world/ColossalHivePiece.java', 'world/ColossalHiveStructure.java']:
                self.source(root, JAVA / relative)
            for path in (root / JAVA / 'client').glob('*.java'):
                if any(key in path.stem.lower() for key in ['firearm', 'firstperson']):
                    self.source(root, path.relative_to(root))
            asset_ids = [w['id'] for w in weapons] + list(dict.fromkeys(w['ammo_id'] for w in weapons))
            for id_ in asset_ids:
                self.source(root, RES / 'assets/blacklight/models/item' / (id_ + '.json'))
            for folder in ['models/block', 'blockstates', 'textures/block']:
                for id_ in FACILITY_IDS:
                    ext = '.png' if folder.startswith('textures') else '.json'
                    p = RES / 'assets/blacklight' / folder / (id_ + ext)
                    if (root / p).is_file():
                        self.source(root, p)
            # Follow only production blacklight texture IDs, never external filesystem paths.
            for folder, ids in [('block', FACILITY_IDS), ('item', asset_ids)]:
                for id_ in ids:
                    model = self.json(RES / 'assets/blacklight/models' / folder / (id_ + '.json'), root)
                    for value in model.get('textures', {}).values():
                        if not value.startswith('blacklight:'):
                            continue
                        texture = value.split(':', 1)[1]
                        if '..' in Path(texture).parts or Path(texture).is_absolute():
                            raise ValueError('Unsafe production texture ID')
                        self.source(root, RES / 'assets/blacklight/textures' / (texture + '.png'))
            for pattern in ['textures/item/*round.png', 'textures/item/*palette.png', 'textures/entity/*soldier_layered.png']:
                for p in (root / RES / 'assets/blacklight').glob(pattern):
                    self.source(root, p.relative_to(root))
        for path in sorted((ROOT / 'src/main/blueprints').glob('*.json')):
            self.source(ROOT, path.relative_to(ROOT))
        for filename in ['preview_large_military_bases.py', 'build_large_military_bases.py', 'archive_military_catalog.py']:
            self.source(ROOT, Path('tools') / filename)
        for path in sorted((ROOT / 'art/military2').glob('*')):
            if path.is_file() and path.suffix in ['.py', '.java', '.png', '.json', '.md']:
                self.source(ROOT, path.relative_to(ROOT))
        return recipes, loot, worldgen

    def creatures(self, lang):
        simple = [
            ('infected_host', 'BlacklightInfectedHost', '宿主数据可重设属性/尺度；这些是空宿主初始属性，非所有宿主固定值'),
            ('blacklight_stalker', 'BlacklightMutant', 'STALKER 分支'),
            ('blacklight_spitter', 'BlacklightMutant', 'SPITTER 分支；ATTACK_DAMAGE并非唾液伤害'),
            ('blacklight_brute', 'BlacklightMutant', 'BRUTE 分支'),
            ('blacklight_hunter', 'BlacklightHunter', 'HunterBalance 基础值；动作/难度与防御再缩放'),
            ('blacklight_hive', 'BlacklightHive', '常规伤害受 HiveDefense 芯片伤害/间隔限制；有限增援波'),
            ('blacklight_hydra', 'BlacklightHydra', '固定炮台式海德拉；动作命中另读实体源码'),
            ('biomass_husk', 'BiomassHusk', '生物质残骸实体，非持枪兵种'),
        ]
        registry = self.text(JAVA / 'registry/ModEntities.java')
        hunter_source = self.text(JAVA / 'combat/HunterBalance.java')
        hunter_constants = {key: number(value) for key, value in re.findall(r'\b(HEALTH|DAMAGE|SPEED|KNOCKBACK_RESISTANCE)\s*=\s*([.\d]+)', hunter_source)}
        rows = []
        for id_, cls, note in simple:
            path = JAVA / 'entity' / (cls + '.java')
            text = self.text(path)
            attributes = re.search(r'public static AttributeSupplier.Builder attributes\([^)]*\)\s*\{(.*?)\}', text, re.S)
            if not attributes:
                raise ValueError('Creature attributes schema changed: ' + cls)
            kind = id_.removeprefix('blacklight_').upper()
            extracted = {key: creature_number(value, kind, hunter_constants) for key, value in
                         re.findall(r'\.add\(Attributes\.([A-Z_]+),([^)]*)\)', attributes[1])}
            dims = re.search(r'ENTITIES.register\("' + id_ + r'".*?\.sized\(([^,]+),([^\)]+)\)', registry, re.S)
            if id_ in ('blacklight_stalker', 'blacklight_spitter', 'blacklight_brute'):
                dims = re.search(r'mutant\("' + id_ + r'",[^,]+,([^,]+),([^\)]+)\)', registry)
            rows.append(dict(id='blacklight:' + id_, name=lang.get('entity.blacklight.' + id_, id_),
                             base_health=extracted['MAX_HEALTH'], attack_attribute=extracted.get('ATTACK_DAMAGE'),
                             movement_speed=extracted['MOVEMENT_SPEED'], knockback_resistance=extracted['KNOCKBACK_RESISTANCE'],
                             all_registered_attributes=extracted,
                             registered_width=number(dims[1]) if dims else None, registered_height=number(dims[2]) if dims else None,
                             attributes_source=attributes[1].strip() if attributes else None, source=path.as_posix(), note=note))
            resource_ids = []
            if id_ in ('blacklight_stalker', 'blacklight_spitter', 'blacklight_brute', 'blacklight_hive'):
                resource_ids = ['geo/' + id_ + '.geo.json', 'animations/' + id_ + '.animation.json', 'textures/entity/infected_species.png']
            if id_ == 'blacklight_hunter':
                resource_ids = ['geo/blacklight_hunter_authored.geo.json', 'animations/blacklight_hunter.animation.json',
                                'models/hunter_connected.json', 'textures/entity/hunter_connected.png', 'textures/entity/hunter_authored.png']
            if id_ == 'blacklight_hydra':
                resource_ids = ['geo/blacklight_hydra.geo.json', 'animations/blacklight_hydra.animation.json',
                                'models/hydra_connected.json', 'textures/entity/hydra_connected.png', 'textures/entity/hydra_authored.png']
            rows[-1]['resource_paths'] = [(RES / 'assets/blacklight' / relative).as_posix() for relative in resource_ids]
            rows[-1]['appearance_note'] = '按宿主来源物种、作者mesh与形变选择外观' if id_ == 'infected_host' else '使用所有者/默认玩家皮肤并由残骸渲染器处理' if id_ == 'biomass_husk' else '见具体资源路径；有geo骨架不代表最终只有geo表面，hunter/hydra另绘connected网格'
            for relative in rows[-1]['resource_paths']:
                for project in [ROOT, self.forge]:
                    self.source(project, relative)
        # Known creature overview is intentionally smaller than a complete attack-system spec.
        text = self.text(JAVA / 'entity/InfectedBossProfile.java')
        bosses = []
        for m in re.finditer(r'(\w+) = new InfectedBossProfile\(\s*"([^"]+)","([^"]+)",(.*?),List.of\(', text, re.S):
            args = [number(a) for a in split_args(m[4])]
            if len(args) != 8:
                raise ValueError('Boss profile schema changed')
            bounds = re.search(r'case "' + re.escape(m[2]) + r'" -> new AABB\(([^)]+)\)', text)
            bosses.append(dict(profile=m[1], source_species=m[2], family=m[3], locomotion_width=args[0], locomotion_height=args[1],
                               visual_width=args[2], visual_depth=args[3], visual_height=args[4],
                               base_health=args[5], attack_attribute=args[6], movement_speed=args[7],
                               local_visual_bounds=[number(a) for a in split_args(bounds[1])],
                               note='infected_host 的资料档，不是单独注册ID；巨型时bodyScale=1，紧凑型依宿主/成长缩放'))
            resource = RES / 'assets/blacklight/infected_models' / (m[2].split(':')[1] + '.json')
            bosses[-1]['resource_path'] = resource.as_posix()
            for project in [ROOT, self.forge]:
                self.source(project, resource)
        for relative in ['client/model/BlacklightCreatureModel.java', 'client/model/BlacklightHunterModel.java', 'client/model/BlacklightHydraModel.java',
                         'client/model/InfectedSpeciesMesh.java', 'client/model/ConnectedCreatureMesh.java',
                         'client/renderer/BlacklightCreatureRenderer.java', 'client/renderer/BlacklightHunterRenderer.java',
                         'client/renderer/BlacklightHydraRenderer.java', 'client/renderer/BiomassHuskRenderer.java', 'client/renderer/InfectedHostRenderer.java']:
            for project in [ROOT, self.forge]:
                self.source(project, JAVA / relative)
        return rows, bosses

    def organization_data(self, lang):
        rank_src = self.text(JAVA / 'military/MilitaryRank.java')
        enum_body = re.search(r'public enum MilitaryRank\s*\{([^;]+);', rank_src, re.S)[1]
        ranks = [s.strip().lower() for s in enum_body.split(',')]
        if ranks != RANKS:
            raise ValueError('Military ranks changed; update visual rank framing before refreshing')
        rank_rows = [dict(id=id_, ordinal=i, name=lang.get('rank.blacklight.' + id_, id_)) for i, id_ in enumerate(ranks)]
        unit_src = self.text(JAVA / 'military/MilitaryUnitType.java')
        unit_rows = [dict(id=name.lower(), level=int(level), leaf=int(level) == 0,
                          military_default_size=int(size), blackwatch_default_size=72 if name == 'BATTALION' else int(size),
                          name=lang.get('unit.blacklight.' + name.lower(), name.lower()))
                     for name, level, size in re.findall(r'(TACTICAL_SQUAD|SECTION|PLATOON|COMPANY|SQUADRON|BATTALION)\((\d+),\s*(\d+)\)', unit_src)]
        if not re.search(r'this == BATTALION && faction == MilitaryFaction.BLACKWATCH \? 72 : size', unit_src):
            raise ValueError('Blackwatch battalion override changed; update extractor')
        org_src = self.text(JAVA / 'military/MilitaryOrganization.java')
        limits = {key: int(value) for key, value in re.findall(r'\b(MAX_LEAF|MAX_CHILDREN|MAX_DEPTH|MAX_TREE_MEMBERS|MAX_UNITS)\s*=\s*(\d+)', org_src)}
        if len(limits) != 5:
            raise ValueError('Organization limit schema changed')
        role_src = self.text(JAVA / 'military/MilitaryRole.java')
        roles = [dict(id=role.lower(), default_rank=rank.lower()) for role, rank in re.findall(r'(TROOPER|TEAM_LEADER|OFFICER|COMMANDER)\(MilitaryRank\.(\w+)\)', role_src)]
        return dict(ranks=rank_rows, unit_types=unit_rows, roles=roles, hard_limits=limits)

    def block_damage_data(self, weapons):
        result = {'status': '正在接入，最终冻结后刷新；与对生物damage/radius分开', 'platforms': {}}
        for platform, project in [('neoforge', ROOT), ('forge', self.forge)]:
            folder = JAVA / 'firearms'
            record = {'available_files': [], 'configuration': [], 'protected_tags': []}
            for filename in ['BlockDamagePolicy.java', 'BlockDamageLedger.java', 'FirearmBlockDamage.java', 'FirearmsWorldConfig.java']:
                if (project / folder / filename).is_file():
                    self.source(project, folder / filename)
                    record['available_files'].append(filename)
            policy = project / folder / 'BlockDamagePolicy.java'
            if policy.is_file():
                source = self.text(folder / policy.name, project)
                for method in ['durability', 'bulletImpact', 'blastRadius', 'blastImpact']:
                    match = re.search(r'public static float ' + method + r'\([^)]*\)\s*\{(.*?)\n    }', source, re.S)
                    if not match:
                        raise ValueError('Block damage policy method changed: ' + method)
                    cases = {key.strip(): value for key, value in re.findall(r'case ([A-Z_, ]+) -> ([^;]+);', match[1])}
                    if (default := re.search(r'default -> ([^;]+);', match[1])):
                        cases['default'] = default[1]
                    record[method] = {'body': match[1].strip(), 'switch_cases': cases}
                record['weapon_block_blast_radius'] = [dict(id=w['id'], kind=w['kind'], entity_effect_radius=w['radius_blocks'],
                    policy_block_radius=number(record['blastRadius']['switch_cases'].get(w['kind'], '0')),
                    block_blast_radius=min(w['radius_blocks'], number(record['blastRadius']['switch_cases'].get(w['kind'], '0'))))
                    for w in weapons]
            cfg = project / folder / 'FirearmsWorldConfig.java'
            if cfg.is_file():
                source = self.text(folder / cfg.name, project)
                record['configuration'] = [dict(name=name, default=number(default), minimum=number(minimum), maximum=number(maximum))
                    for name, default, minimum, maximum in re.findall(r'\.defineInRange\("([^"]+)",\s*([.\d]+),\s*([.\d]+),\s*([.\d]+)\)', source)]
                record['configuration'] += [dict(name=name, default=value == 'true') for name, value in re.findall(r'\.define\("([^"]+)",\s*(true|false)\)', source)]
            runtime = project / folder / 'FirearmBlockDamage.java'
            if runtime.is_file():
                source = self.text(folder / runtime.name, project)
                record['ledger_constructors'] = re.findall(r'new BlockDamageLedger\(([^)]+)\)', source)
                record['runtime_source'] = (folder / runtime.name).as_posix()
                for signature in ['private static BlockDamagePolicy.Material material', 'private static boolean allowed', 'public static int blast', 'public static void placed']:
                    method = re.search(re.escape(signature) + r'\([^)]*\)\s*\{(.*?)\n    }', source, re.S)
                    record[signature.split()[-1] + '_raw'] = method[1].strip() if method else None
            for directory in ['block', 'blocks']:
                for path in (project / RES / 'data/blacklight/tags' / directory).glob('*.json'):
                    # Preserve all local block tags; identify firearm policy tags without executing them.
                    obj = self.json(path.relative_to(project), project)
                    record['protected_tags'].append({'source': path.relative_to(project).as_posix(), 'raw': obj})
            result['platforms'][platform] = record
        if all(len(p['available_files']) == 4 for p in result['platforms'].values()):
            result['status'] = '已提取四个策略/适配文件；运行通过状态仍由项目验证记录确认'
        return result

    def client_feedback_data(self, weapons):
        presentation = {'weapon_models': []}
        for w in weapons:
            model = self.json(RES / 'assets/blacklight/models/item' / (w['id'] + '.json'))
            presentation['weapon_models'].append(dict(id=w['id'], elements=len(model.get('elements', [])), display=model.get('display')))
        for filename in ['FirearmViewEvents.java', 'FirearmViewState.java']:
            path = JAVA / 'firearms' / filename
            if (ROOT / path).is_file():
                source = self.text(path)
                presentation[filename] = {'source': path.as_posix(), 'sha256': sha(ROOT / path)}
                if filename == 'FirearmViewState.java':
                    match = re.search(r'public Frame frame\(double now\)\s*\{(.*?)\n    }', source, re.S)
                    presentation['frame_formula_raw'] = match[1].strip() if match else None
                if filename == 'FirearmViewEvents.java':
                    presentation['first_person_transforms_raw'] = re.findall(r'pose\.(?:translate|mulPose)\([^;]+;', source)
        combat = self.text(JAVA / 'firearms/FirearmsCombat.java')
        presentation['muzzle_distance'] = re.findall(r'Vec3 muzzle=start.add\(direction.normalize\(\).scale\(([^)]+)\)', combat)
        presentation['tracer_loop_raw'] = re.findall(r'for\(double t=[^\{]+', combat)
        return presentation

    def previews(self, soldiers, structures, weapons, facilities):
        from PIL import Image, ImageDraw, ImageFont
        sys.path.insert(0, str(ROOT / 'art/military2'))
        import preview_soldiers as soldier_renderer
        spec = importlib.util.spec_from_file_location('archive_base_renderer', ROOT / 'tools/preview_large_military_bases.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        renderer = module.Renderer(ROOT / RES)
        source = ROOT / JAVA / 'client/model/BlackwatchModel.java'
        pieces = soldier_renderer.read_geometry(source.read_text(encoding='utf-8'))
        textures = {f: Image.open(ROOT / RES / 'assets/blacklight/textures/entity' / (f + '_soldier_layered.png')).convert('RGBA') for f in FACTIONS}
        for row in soldiers:
            target = self.output / row['preview']
            target.parent.mkdir(parents=True, exist_ok=True)
            result = Image.new('RGB', (1920, 900), (237, 240, 236))
            draw = ImageDraw.Draw(result)
            draw.text((25, 12), row['name'] + ' / ' + row['id'], font=soldier_renderer.font(25), fill=(34, 47, 48))
            draw.text((25, 50), '生产 Java + PNG 离线示意 / 非实机 / 正面、背面、脱装、持枪', font=soldier_renderer.font(19), fill=(62, 79, 77))
            for i, (yaw, equipped, gun) in enumerate([(0, True, None), (180, True, None), (-25, False, None), (-28, True, 'weapon_machine' if row['heavy'] else 'weapon_rifle')]):
                result.paste(soldier_renderer.render(pieces, textures[row['faction']], row['faction'], row['heavy'], equipped, gun,
                                                    'aim' if gun else 'ready', yaw, width=480, height=800,
                                                    role=row['role'], rank=RANKS.index(row['spawn_default_rank'])), (480 * i, 90))
            result.save(target)
            self.image_record(target, [source, ROOT / row['texture']], '生产分层几何+原生纹理离线预览，非GL/非实机')
        for row in structures:
            target = self.output / row['preview']
            target.parent.mkdir(parents=True, exist_ok=True)
            raw, blocks = read_nbt(ROOT / row['source'])
            renderer.render(blocks, 315, size=(1400, 1000)).save(target)
            self.image_record(target, [ROOT / row['source'], ROOT / 'tools/preview_large_military_bases.py'], '实际NBT几何，原版材质近似；不渲染嵌入人员/箱内物品，非实机')
            if row['large_base_metadata']:
                for suffix in ['floorplan', 'cutaway', 'fourviews']:
                    existing = ROOT / 'docs/previews/large-military-bases' / (row['id'] + '-' + suffix + '.png')
                    if existing.is_file():
                        dest = target.with_name(row['id'] + '-' + suffix + '.png')
                        shutil.copyfile(existing, dest)
                        self.image_record(dest, [existing], '已有离线制作图，历史来源以原目录manifest为准')
        for row in [*weapons, *facilities]:
            id_ = row['id'].split(':')[-1]
            kind = 'block' if id_ in FACILITY_IDS else 'item'
            model_path = ROOT / RES / 'assets/blacklight/models' / kind / (id_ + '.json')
            model = json.loads(model_path.read_text(encoding='utf-8'))
            dest = self.output / (row.get('preview') or ('assets/枪械/' + id_ + '.png'))
            dest.parent.mkdir(parents=True, exist_ok=True)
            render_json_model(ROOT / RES, model).save(dest)
            row['preview'] = dest.relative_to(self.output).as_posix()
            source_textures = []
            for value in model.get('textures', {}).values():
                if value.startswith('blacklight:'):
                    source_textures.append(ROOT / RES / 'assets/blacklight/textures' / (value.split(':')[1] + '.png'))
            self.image_record(dest, [model_path, ROOT / 'tools/archive_military_catalog.py', *dict.fromkeys(source_textures)], '实际JSON立方体/逐面UV纹理采样；不含display/透视/GL光影与运行时反馈，非GL')
        for ammo_id in dict.fromkeys(w['ammo_id'] for w in weapons):
            source = ROOT / RES / 'assets/blacklight/textures/item' / (ammo_id + '.png')
            dest = self.output / 'assets/弹药' / (ammo_id + '.png')
            dest.parent.mkdir(parents=True, exist_ok=True)
            with Image.open(source) as image:
                image.convert('RGBA').resize((256, 256), Image.Resampling.NEAREST).save(dest)
            self.image_record(dest, [source], '实际弹药物品纹理像素放大，非实机')
        # Existing bespoke creature artwork is useful, but explicitly remains an authoring preview.
        for pattern in ['hunter-*.png', 'hydra-*.png']:
            for path in (ROOT / 'art/creature-connected').glob(pattern):
                dest = self.output / 'assets/感染生物' / path.name
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, dest)
                self.image_record(dest, [path], '既有作者制作预览；非实机，未在此刷新几何')

    def preserve_evidence(self, temp):
        from PIL import Image
        evidence = []
        for name, dest_name in SCREENSHOTS.items():
            dest = self.output / '实机截图' / dest_name
            original = temp / name
            if not dest.exists() and original.is_file():
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(original, dest)
            evidence.append(dict(source_name=name, file='实机截图/' + dest_name, present=dest.is_file(),
                                 sha256=sha(dest) if dest.is_file() else None, status='用户失败反馈，不能视作修复通过'))
        dump(self.output / '实机截图/index.json', evidence)
        write(self.output / '实机截图/README.md', '''# 用户实机失败反馈

这三张是用户在本轮提供的原始反馈，保留原图且刷新脚本永不覆盖已存在文件。它们不是 military.4 修复通过证据。

- [01 持枪比例](01-持枪比例反馈.png)：第一人称粗大枪身；远处已有其他军事实体不证明大型基地槽位已发放。
- [02 开火遮挡](02-开火遮挡反馈.png)：开火时枪体离开正常持枪位置，贴脸粒子遮挡准星；画面HUD显示 13/30。
- [03 基地零驻军](03-基地零驻军反馈.png)：画面文本 Blackwatch [114,-61,130] issued 0/20、loaded 0、lost 0、unloaded 0、MUSTERING。周边有史莱姆；原因与修复结果应读本轮测试记录，不能从一张截图推定已修好。

日期为用户本轮 2026-10-24 提供；截图拍摄时区、完整客户端配置和精确JAR哈希没有从图中确认。源码档案与屏幕上的版本不能自动等同。
''')
        model = ROOT.parent / 'blackwatch_two_hand_sneak.bbmodel'
        dest = self.output / '参考模型/blackwatch_two_hand_sneak.bbmodel'
        if model.is_file() and not dest.exists():
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(model, dest)
        if not dest.is_file():
            return {'present': False}
        if model.is_file() and sha(model) != sha(dest):
            raise ValueError('Reference bbmodel differs from preserved original; archive a new revision manually')
        obj = json.loads(dest.read_text(encoding='utf-8-sig'))
        groups = []
        def group_tree(node, parent=None):
            if isinstance(node, str):
                return 1
            if not isinstance(node, dict):
                return 0
            group = dict(name=node.get('name'), uuid=node.get('uuid'), parent=parent, origin=node.get('origin'),
                         rotation=node.get('rotation'), children=len(node.get('children', [])))
            groups.append(group)
            group['element_references_below'] = sum(group_tree(child, node.get('uuid')) for child in node.get('children', []))
            return group['element_references_below']
        for node in obj.get('outliner', []):
            group_tree(node)
        group_names = {g['uuid']: g['name'] for g in groups}
        textures = []
        for i, tex in enumerate(obj.get('textures', [])):
            t = {k: tex.get(k) for k in ['name', 'id', 'width', 'height', 'uv_width', 'uv_height']}
            source = tex.get('source', '')
            if isinstance(source, str) and source.startswith('data:image/png;base64,'):
                raw = base64.b64decode(source.split(',', 1)[1], validate=True)
                tex_path = self.output / '参考模型/内嵌贴图' / (f'{i:02d}.png')
                tex_path.parent.mkdir(parents=True, exist_ok=True)
                if not tex_path.exists():
                    tex_path.write_bytes(raw)
                with Image.open(tex_path) as im:
                    t.update(size=list(im.size), file=tex_path.relative_to(self.output).as_posix(), sha256=sha(tex_path))
            else:
                t['note'] = '无内嵌PNG或外部引用；不读取该路径、不联网'
            textures.append(t)
        animations = [dict(name=a.get('name'), length_seconds=a.get('length'), loop=a.get('loop'),
                           animators=len(a.get('animators', {})), keyframes=sum(len(v.get('keyframes', [])) for v in a.get('animators', {}).values()))
                      for a in obj.get('animations', [])]
        for item, authored in zip(animations, obj.get('animations', [])):
            item['targets'] = [dict(uuid=id_, group=group_names.get(id_), type=animator.get('type'), name=animator.get('name'))
                               for id_, animator in authored.get('animators', {}).items()]
        index = dict(file='参考模型/blackwatch_two_hand_sneak.bbmodel', sha256=sha(dest), bytes=dest.stat().st_size,
                     meta=obj.get('meta'), elements=len(obj.get('elements', [])),
                     element_types=dict(Counter(e.get('type', 'cube') for e in obj.get('elements', []))),
                     textures=textures, animations=animations, outliner_groups=groups, use_status='仅参考；未直接整体导入运行资源或JAR',
                     author='1145homo（用户提供截图署名）', permission_evidence='截图中作者回复“可以直接用”；仅记录本次提供的许可事实，不扩展为独立许可证')
        dump(self.output / '参考模型/model-index.json', index)
        # Preserve only the relevant authored-model / permission portions, not unrelated group conversation.
        for name, out, crop in [
            ('codex-clipboard-14169190-9043-468d-86e1-053ed32e6d44.png', '来源署名.png', (0, 342, 700, 656)),
            ('codex-clipboard-7ca91dba-94dc-4efd-bffe-75389e5eb1b9.png', '允许使用.png', (0, 0, 1450, 470)),
        ]:
            path = temp / name
            target = self.output / '参考模型/来源证据' / out
            if path.is_file() and not target.exists():
                target.parent.mkdir(parents=True, exist_ok=True)
                with Image.open(path) as image:
                    image.crop(crop).save(target)
                dump(target.with_suffix('.json'), {'original_filename': name, 'original_sha256': sha(path), 'crop_box': crop,
                                                   'derived_sha256': sha(target), 'purpose': '只保留模型提供/许可相关区域'})
        write(self.output / '参考模型/README.md', '# 用户提供的 Blackwatch 参考模型\n\n'
              '原件只读复制归档；当前仅供枪械握持/动作比例参考，未将整套模型直接导入 JAR。现有 242 部件分层兵模、军衔和脱装规则继续由项目生产源码管理。\n\n'
              f'原文件 `{model.name}`，{index["bytes"]:,} 字节，SHA-256 `{index["sha256"]}`；meta `{json.dumps(index["meta"], ensure_ascii=False)}`。\n\n'
              f'元素 {index["elements"]}，贴图 {len(textures)}，动画 {len(animations)}。5,365 立方体在多人场景的开销尚未实机测量，不能当作现有 242 部件的无成本替换。\n\n'
              '来源：用户本轮提供文件；截图署名 1145homo，并在询问是否可以参考后回复“可以直接用”。[署名证据](来源证据/来源署名.png)、[允许使用证据](来源证据/允许使用.png)为仅保留相关区域的裁剪，旁列JSON记录原截图hash与裁剪框。没有导入其余私人聊天。\n\n'
              + table(['动作', '长度 秒', 'loop', '动画节点', '关键帧'], [[a['name'], a['length_seconds'], a['loop'], a['animators'], a['keyframes']] for a in animations])
              + '\n## 原件分组结构\n\n' + table(['分组', '父组UUID', '直接子节点', '下属元素引用数', '原点'], [[g['name'], g['parent'], g['children'], g['element_references_below'], g['origin']] for g in groups])
              + '\n内嵌PNG按索引输出到 `内嵌贴图/`。外部纹理路径不自动读取、不联网。结构/贴图完整索引见 [model-index.json](model-index.json)。刷新不覆盖原件、来源证据与内嵌贴图；新原件需以新版本另存。\n')
        return index


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--forge-root', type=Path, default=ROOT.parent / 'blacklight-evolution-forge-1.20.1')
    parser.add_argument('--output', type=Path, default=ROOT / 'docs/制作资料')
    parser.add_argument('--temp-dir', type=Path, default=Path.home() / 'AppData/Local/Temp')
    parser.add_argument('--skip-render', action='store_true', help='Refresh data/docs only; do not claim image inputs are current')
    parser.add_argument('--verify', action='store_true', help='Read-only: validate hashes, PNGs, generated documentation links and counts')
    parser.add_argument('--final-source-freeze', action='store_true', help='Only use after the owner confirms both runtime source trees are frozen; not a gameplay pass')
    args = parser.parse_args()
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    if args.verify:
        verify_archive(args.output.resolve(), args.forge_root.resolve())
        return
    archive = Archive(args.output.resolve(), args.forge_root.resolve())
    now = datetime.now().astimezone().isoformat(timespec='seconds')
    # Capture baseline before output mutates git status; dirty working trees are intentional and explicit.
    state = {'generated_at': now, 'neoforge': git(ROOT), 'forge': git(archive.forge), 'final_source_freeze': args.final_source_freeze,
             'note': '读取工作树，不等同HEAD构建；阶段/最终验收以项目任务记录为准'}
    lang = archive.json(RES / 'assets/blacklight/lang/zh_cn.json')
    weapons = archive.weapon_data(ROOT)
    forge_weapons = archive.weapon_data(archive.forge)
    soldiers = archive.soldiers(weapons, lang)
    structures = archive.structures()
    forge_structures = archive.structures(archive.forge)
    facilities = archive.facilities(lang)
    recipes, loot, worldgen = archive.raw_resources(weapons)
    creatures, bosses = archive.creatures(lang)
    organization = archive.organization_data(lang)
    block_damage = archive.block_damage_data(weapons)
    client_feedback = archive.client_feedback_data(weapons)
    if args.final_source_freeze and any(len(p['available_files']) != 4 for p in block_damage['platforms'].values()):
        raise ValueError('Cannot mark final source freeze while block damage implementation files are incomplete')
    configuration = {}
    for platform, project in [('neoforge', ROOT), ('forge', archive.forge)]:
        source = archive.text(JAVA / 'city/CityConfig.java', project)
        configuration[platform] = [dict(name=name, default=int(default), minimum=int(minimum), maximum=int(maximum))
                                   for name, default, minimum, maximum in re.findall(r'\.defineInRange\("([^"]+)",\s*(\d+),\s*(\d+),\s*(\d+)\)', source)]
        configuration[platform] += [dict(name=name, default=value == 'true') for name, value in re.findall(r'\.define\("([^"]+)",\s*(true|false)\)', source)]
    reference = archive.preserve_evidence(args.temp_dir)
    if not args.skip_render:
        archive.previews(soldiers, structures, weapons, facilities)
        dump(archive.output / 'data/preview-manifest.json', archive.preview_records)
    data = dict(baseline=state, soldiers=soldiers, weapons=weapons, ammo=[dict(id=id_, name=lang.get('item.blacklight.' + id_, id_),
                weapons=[w['id'] for w in weapons if w['ammo_id'] == id_], recipe=id_) for id_ in dict.fromkeys(w['ammo_id'] for w in weapons)],
                structures=structures, forge_structures=forge_structures, facilities=facilities, creatures=creatures, infected_boss_profiles=bosses,
                recipes=recipes, loot_tables=loot, worldgen=worldgen, organization=organization,
                city_configuration=configuration, block_damage=block_damage, client_presentation=client_feedback, reference_model=reference)
    for key, records in [('soldiers', soldiers), ('weapons', weapons), ('ammo', data['ammo']), ('structures', structures),
                         ('forge_structures', forge_structures), ('facilities', facilities), ('creatures', creatures), ('infected_boss_profiles', bosses),
                         ('recipes', recipes), ('loot_tables', loot), ('worldgen', worldgen)]:
        dump(archive.output / 'data' / (key + '.json'), records)
        csv_file(archive.output / 'data' / (key + '.csv'), records)
    dump(archive.output / 'data/catalog.json', data)
    dump(archive.output / 'data/organization.json', organization)
    dump(archive.output / 'data/city-configuration.json', configuration)
    dump(archive.output / 'data/block-damage.json', block_damage)
    dump(archive.output / 'data/client-presentation.json', client_feedback)
    for key in ['ranks', 'unit_types', 'roles']:
        csv_file(archive.output / 'data' / (key + '.csv'), organization[key])
    dump(archive.output / 'data/baseline.json', state)
    write_documents(archive, data, forge_weapons)
    # After all source reads, hash exact retained snapshots. No manual source data are overwritten.
    dump(archive.output / 'data/source-manifest.json', sorted(archive.sources, key=lambda s: (s['platform'], s['path'])))
    csv_file(archive.output / 'data/source-manifest.csv', sorted(archive.sources, key=lambda s: (s['platform'], s['path'])))
    print(json.dumps({'output': str(archive.output), 'generated_at': now,
                      'counts': {k: len(data[k]) for k in ['soldiers', 'weapons', 'ammo', 'structures', 'facilities', 'recipes', 'loot_tables', 'worldgen']},
                      'snapshots': len(archive.sources), 'rendered_previews': len(archive.preview_records), 'reference_model': reference.get('sha256')}, ensure_ascii=False))


def write_documents(a, d, forge_weapons):
    """Readable cards and maintenance notes are derived beside machine-readable exports."""
    root = a.output
    baseline = d['baseline']
    count = lambda key: len(d[key])
    readme = f'''# 黑光模组制作资料

本档案记录制作时实际源码、资源和外观，主工程为 NeoForge 1.21.1；Forge 1.20.1 的指针和差异在 [两端差异](两端差异.md)。本次生成时间：{baseline['generated_at']}。读取的是含未提交修改的工作树；不是宣称 HEAD 对应发行 JAR。详细分支、HEAD、dirty 与路径见 [baseline.json](data/baseline.json)。

| 查找内容 | 入口 |
| --- | --- |
| 两方普通兵/重装/队长/军官/指挥官，10种 | [兵种与编制](兵种与编制.md)、[逐兵种卡](兵种卡/README.md) |
| 八枪七弹、玩家伤害与NPC缩放 | [枪械与弹药](枪械与弹药.md) |
| 第一人称模型、后坐/装填与开火视觉 | [第一人称反馈](第一人称反馈.md) |
| 枪械破坏方块、材质阈值与保护配置 | [方块破坏规则](方块破坏规则.md) |
| 7个NBT、真实箱与人员、基地岗位/模块 | [建筑与生成](建筑与生成.md)、[逐建筑卡](建筑卡/README.md) |
| 六种场景方块及功能边界 | [场景方块](场景方块.md) |
| 原感染体、巨型宿主、程序蜂巢 | [感染生物与巨型单位](感染生物与巨型单位.md) |
| 实机故障与未验证项目 | [用户失败反馈](实机截图/README.md)、[覆盖与验证](覆盖与验证.md) |
| 用户授权提供的BB参考文件 | [参考模型](参考模型/README.md) |
| 机器可读数据 | [catalog.json](data/catalog.json)，分表 JSON 与 UTF-8 BOM CSV 均位于 data/ |
| 原文与哈希追溯 | [source-manifest.json](data/source-manifest.json)、[CSV](data/source-manifest.csv)，原文在 sources/ |

当前输出 {count('soldiers')} 兵种、{count('weapons')} 枪械、{count('ammo')} 弹种、{count('structures')} NBT、{count('facilities')} 场景块。配方 {count('recipes')} 与 loot {count('loot_tables')} 包含两端原文，并非只记录名字；NBT 逐卡列出真实箱 LootTable/Items、嵌入实体与运行时驻军的区别。

所有档案图片使用相对路径，可把本目录整体复制到别处阅读。`assets/` 是源码几何/纹理的离线预览，原版材质近似且不代表GL实机；`实机截图/` 是用户失败反馈；两者不混作验收。参考模型只是参考文件，不因存在于文档就进入 JAR。

## 维护

在主工程执行：

```powershell
python tools/archive_military_catalog.py
```

需要 Python 3、Pillow、NumPy。双端根目录可用 `--forge-root` 指定。脚本只读取生产源码/NBT/JSON/PNG，写本资料目录；不会执行 NBT 内命令、联网、运行 Gradle、改存档或发行包。不要在生产文件仍被写入时把本次快照当最终冻结。完成生产改动后运行完整命令，再核对 `data/source-manifest.json`、`data/preview-manifest.json` 与项目验收记录。

`--skip-render` 仅快速更新文字/数据，图片 manifest 会保留上一次输入哈希，不能声称图片已刷新。生成文档/data/sources/assets 可被刷新覆盖；人工实机截图、参考原件和来源证据只在缺失时复制，绝不覆盖。人工备注/新实机截图请放 `人工补充/` 或 `实机截图/`，不要编辑生成表格。

若源码结构变化导致严格提取检查报错，应先修提取器并核对生产源码，不能用旧数字填充。旧制作技能/全部玩家进化能力不在本次完整重写范围，当前缺口见覆盖说明。
'''
    write(root / 'README.md', readme)
    soldier_rows = [[f"[{s['name']}](兵种卡/{s['id'].split(':')[1]}.md)", s['id'], s['role'], s['spawn_default_rank'],
                     s['health'], s['armor'], s['npc_effective_bullet_damage'], s['movement_speed'], s['knockback_resistance'], s['aim_ticks'], s['cycle_ticks'],
                     f"{s['initial_weapon']} {s['initial_magazine']}+{s['initial_reserve']}"] for s in d['soldiers']]
    military = '# 兵种、身份、军衔与组织\n\n' + table(['兵种', '注册ID', '出生角色', '出生军衔', 'HP', '护甲', 'NPC基础枪伤', '速度', '抗击退', '瞄准tick', '周期tick', '首次枪/弹匣+备用'], soldier_rows)
    military += '''
表中是基础值，不是实际受害者最终扣血：护甲、效果、难度/本模组其他战斗缩放仍可能影响结果。十种注册碰撞均由 ModEntities 提取为 0.6×1.95 方块，外观装备不会扩大注册碰撞。队长、军官、指挥官使用本阵营普通档；军衔与组织任命不进入 MilitaryCombatProfile。直接出生的重装兵默认 private；大型基地槽位可将重装兵提为 corporal。不得把基地岗位军衔误写成所有 `/summon` 的默认军衔。

NPC 使用实际主手 FirearmItem：`FirearmsCombat.fire(this,d,aim,profile().damage()/d.damage())`；默认子弹命中基础值为 `d.damage * scale = profile.damage`。例如军方步枪玩家基础 4，普通 NPC 1.4，scale .35；军方军官卡宾玩家基础 3.5，但 NPC 仍为1.4。玩家武器 cooldown 和 NPC战术 cycle 是不同层次。NPC战术在 aim、aim+8、aim+16 三个年龄尝试射击，burst结束 aim+17，readyAt设为开始+cycle；视线、友军、地域、弹药与冷却可以阻止射击，不能当作每秒固定射速。20 tick通常为1秒，服务器卡顿会改变实际时间。

首次装备只发一把满弹匣枪和3匣备用：步枪30+90，机枪60+180，卡宾24+72。`weaponInitialized` 防重复发枪；改role不重发装备，不补弹。脱装把同一实际枪栈移到 stowedWeapon，护甲与抗击退归零，隐藏护具/军衔/枪模并停止战术；HP和基础制服保留。重新装备移回原枪与原弹量。补给从真实弹药箱有限库存扣除，不无限生成。

## 八级军衔与四种身份

'''
    military += table(['顺序', '保存ID', '中文'], [[r['ordinal'], r['id'], r['name']] for r in d['organization']['ranks']])
    military += '\n身份默认军衔：trooper→private，team_leader→sergeant，officer→lieutenant，commander→major。设置身份仅在当前军衔较低时抬到默认值，已有较高军衔不会降级；手工设置 rank 和实际 unit commander 任命相互独立。缺旧role的存档以保存军衔推断，取推断角色与注册角色较高者。\n\n'
    military += '## 编制默认与硬上限\n\n' + table(['预设ID', '层级', '军方默认人数', 'BW默认人数', '叶节点'],
        [[u['id'], u['level'], u['military_default_size'], u['blackwatch_default_size'], u['leaf']] for u in d['organization']['unit_types']])
    military += '\n硬上限原文提取：' + '；'.join(k + '=' + str(v) for k, v in d['organization']['hard_limits'].items()) + '。结构化数据见 [organization.json](data/organization.json)，军衔/角色/编制各有CSV。\n'
    military += '''
这些是本模组编制标签与默认人数，不是对《虐杀原形》原作或现实军制的断言。硬上限为上文源码提取值。叶节点直接装成员，父节点挂同阵营低层级单位；默认人数不覆盖硬上限。批量编辑通过草稿transaction原子校验，失败不提交部分名册。

## 命令与持久化

需要权限等级2；以下都以 `/blacklight military` 为根。单位名可用引号，亦可传已存在单位UUID。具体参数原文在 sources/neoforge/src/main/java/com/codex/blacklight/command/MilitaryCommands.java 与 CityMilitaryCommands.java。

```mcfunction
/blacklight military unit create military tactical_squad "一班" 8
/blacklight military unit assign "一班" @e[type=blacklight:military_soldier,distance=..20]
/blacklight military unit unassign "一班" <entities>
/blacklight military unit resize "一班" 12
/blacklight military unit attach <parent> <child>
/blacklight military unit detach <unit>
/blacklight military unit remove <unit>
/blacklight military unit commander <unit> <entity>
/blacklight military rank <entities> sergeant
/blacklight military role <entities> team_leader
/blacklight military organize military platoon <entities>
/blacklight military order <unit> guard
/blacklight military order <unit> patrol
/blacklight military order <unit> move <x> <y> <z>
/blacklight military order <unit> regroup
/blacklight military order <unit> return
/blacklight military order <unit> inherit
/blacklight military inspect <unit>
/blacklight military outfit <entities> uniform
/blacklight military outfit <entities> equipped
/blacklight military base place military [x y z] [0..3]
/blacklight military base place blackwatch [x y z] [0..3]
/blacklight military base status
```

`unit commander` 是手工任命，不要求等同最高军衔；manual任命标记保存后不会被日常最高军衔自动挑选覆盖。实体保存 MilitaryRole、MilitaryRank、MilitaryEquipped、MilitaryWeaponInitialized、MilitaryAmmoReserve、MilitaryStowedWeapon、MilitaryUnit、MilitaryAutomaticJoin、部署/驻地字段；组织按维度保存为 `blacklight_military` SavedData，基地槽/损失/补给元数据为 `blacklight_city_military` SavedData。真实枪的弹匣/重装时间保存在枪栈，不能只存备用数。

用户曾反馈“找不到实体”：role转换不会改变注册 EntityType。把 `military_soldier` 变为team_leader后，选择器仍需 `type=blacklight:military_soldier`，而不是 `military_team_leader`。下例是可执行复现方法，不能写成用户已经成功：

```mcfunction
/summon blacklight:military_team_leader ~ ~ ~
/summon blacklight:military_officer ~2 ~ ~
/summon blacklight:blackwatch_commander ~4 ~ ~
/blacklight military role @e[type=blacklight:military_soldier,distance=..12] team_leader
/blacklight military rank @e[type=blacklight:military_soldier,distance=..12] sergeant
```

role、rank、注册实体ID、unit commander是四件独立数据；重装判定仍来自原EntityType。变更身份不会凭空把普通兵变成重装兵。
'''
    write(root / '兵种与编制.md', military)
    write(root / '兵种卡/README.md', '# 十种军事实体索引\n\n' + '\n'.join(f"- [{s['name']} / {s['id']}]({s['id'].split(':')[1]}.md)" for s in d['soldiers']) + '\n\n规则与命令见[兵种与编制](../兵种与编制.md)。\n')
    for s in d['soldiers']:
        write(root / '兵种卡' / (s['id'].split(':')[1] + '.md'), '# ' + s['name'] + '\n\n'
              + f"`{s['id']}`；{s['faction']}；出生role `{s['role']}`，默认rank `{s['spawn_default_rank']}`。\n\n"
              + table(['项目', '当前值'], [[k, s[k]] for k in ['health', 'armor', 'npc_effective_bullet_damage', 'movement_speed', 'knockback_resistance', 'aim_ticks', 'cycle_ticks', 'burst_shot_ages', 'burst_end_ticks', 'registered_width', 'registered_height', 'initial_weapon', 'initial_magazine', 'initial_reserve', 'npc_scale']])
              + f"\n![{s['name']}离线四视图](../{s['preview']})\n\n生产几何：`{s['model']}`；纹理：`{s['texture']}`。正面、背面、脱装、持枪均离线渲染，不是实机截图。\n\n"
              + '[完整结构数据](../data/soldiers.json)；[军衔/编制/弹药/命令规则](../兵种与编制.md)。实体与资源原文及SHA在[来源清单](../data/source-manifest.json)。\n')
    weapon_md = '# 八枪七弹\n\n这里是项目原创玩法平衡，不是真实枪械性能或原作枪械型号考据。damage为玩家基础值；NPC默认实际枪伤读[兵种档](兵种与编制.md)，不能直接照抄此列。range与radius单位方块，容量单位发，cooldown/reload单位tick。\n\n'
    weapon_md += table(['物品ID', '弹种', '容量', '冷却tick', '装填tick', '玩家基础伤害', '射程', '类别', '半径'], [[w['id'], w['ammo_id'], w['capacity'], w['cooldown_ticks'], w['reload_ticks'], w['player_base_damage'], w['range_blocks'], w['kind'], w['radius_blocks']] for w in d['weapons']])
    weapon_md += '\n子弹为射线命中，非BULLET类型生成OrdnanceProjectile。感染弹基础damage=0不等于没有感染效果；爆炸/感染/阵营保护/防御行为原文读 FirearmsCombat 与 OrdnanceProjectile，数值表不声称等同最终扣血。玩家右键/装填输入和服务端状态见 FirearmItem/FirearmsClient。\n\n'
    for w in d['weapons']:
        weapon_md += f"## {lang_name(a, w['id'])} / {w['id']}\n\n![JSON枪模示意]({w.get('preview', 'assets/枪械/' + w['id'] + '.png')})\n\n"
        weapon_md += recipe_section(d, w['id'])
    weapon_md += '\n## 七种弹药\n\n' + table(['ID', '中文', '使用武器'], [[x['id'], x['name'], x['weapons']] for x in d['ammo']])
    for ammo in d['ammo']:
        weapon_md += '\n### ' + ammo['name'] + ' / ' + ammo['id'] + '\n\n' + f"![原生物品纹理像素放大](assets/弹药/{ammo['id']}.png)\n\n" + recipe_section(d, ammo['id'])
    weapon_md += '\n所有枪模图是实际生产JSON的立方体/纹理采样几何示意，不含第一人称display、透视、动画、动态后坐或GL光影。炮口、枪线视觉和命中射线分别由运行源码管理；静态图不能代替左右手实机验证。枪物品最多堆叠1；弹药按普通 Item注册，未单独定义stacksTo。七弹均有独立物品和配方。\n\n配方两端原文全集见[recipes.json](data/recipes.json)，包括 Neo的result.id 与 Forge的result.item 差异；loot原文全集见[loot_tables.json](data/loot_tables.json)。不把装饰军用箱当库存。\n'
    write(root / '枪械与弹药.md', weapon_md)
    write_client_feedback(a, d)
    write_block_damage(a, d)
    write_buildings(a, d)
    write(root / '场景方块.md', '# 六种军事场景块\n\n' + table(['ID', '中文', '光级', '北向碰撞盒0..16', '用途'], [[f['id'], f['name'], f['light'], f['north_collision_box'], f['functional']] for f in d['facilities']])
          + '\n共同硬度2.5、爆炸抗性6、金属声音、noOcclusion；朝向随摆放方向取反，东西向交换X/Z盒子。源 getShape 供默认碰撞形状使用，没有额外beam或交通AI。军用箱没有Container库存，指挥终端没有Blacknet/任务功能，天线没有网络通信，探照灯只发固定光级12，终端光级4；物资实际存在原版箱。\n\n'
          + '\n'.join(f"## {f['name']}\n\n![JSON示意]({f['preview']})\n\n模型 `{f['model']}`。实际JSON立方体与纹理采样离线示意，非实机。\n" for f in d['facilities'])
          + '\n原文见 [MilitaryFacilityBlocks.java](sources/neoforge/src/main/java/com/codex/blacklight/registry/MilitaryFacilityBlocks.java)，模型/方块状态/贴图原文列在来源manifest。\n')
    write_creatures(a, d)
    weapon_fields = ['id', 'ammo_id', 'capacity', 'cooldown_ticks', 'reload_ticks', 'player_base_damage', 'range_blocks', 'kind', 'radius_blocks']
    differences = {'weapon_values_equal': all({k: w[k] for k in weapon_fields} == {k: f[k] for k in weapon_fields} for w, f in zip(d['weapons'], forge_weapons)),
                   'structure_hashes': []}
    for s in d['structures']:
        forge_path = a.forge / RES / 'data/blacklight/structures' / (s['id'] + '.nbt')
        f = next(f for f in d['forge_structures'] if f['id'] == s['id'])
        differences['structure_hashes'].append(dict(id=s['id'], neo_sha256=s['sha256'], forge_sha256=sha(forge_path), equal=s['sha256'] == sha(forge_path),
                                                   neo_data_version=s['data_version'], forge_data_version=f['data_version'],
                                                   size_equal=s['size'] == f['size'], embedded_entities_equal=s['embedded_entities'] == f['embedded_entities'],
                                                   chest_count_equal=len(s['chests']) == len(f['chests']),
                                                   runtime_guards_equal=s['runtime_guards'] == f['runtime_guards']))
    dump(root / 'data/loader-differences.json', differences)
    write(root / '两端差异.md', '# NeoForge / Forge 对照\n\n'
          + table(['项目', 'NeoForge主工程', 'Forge对应工程'], [
              ['游戏/Java', 'Minecraft 1.21.1 / Java21', 'Minecraft 1.20.1 / Java17'],
              ['现有模组维度', 'blacklight:world 大世界及 blacklight:city 城市', 'blacklight:city 城市；本工程没有Neo大世界world'],
              ['维度内军事设施', '两维度启用city_military_bases；大型基地及安全位置的附属检查站/补给模块', '城市维度启用city_military_bases；复用同类基地/检查站模块'],
              ['资源目录', 'data/blacklight/structure、recipe、loot_table', 'data/blacklight/structures、recipes、loot_tables'],
              ['配方结果字段', 'result.id', 'result.item'],
              ['保存/注册/事件API', 'NeoForge1.21.1 API、HolderLookup/组件', 'Forge1.20.1 API及NBT物品保存'],
              ['玩家枪械数值完全相同', differences['weapon_values_equal'], differences['weapon_values_equal']],
          ]) + '\n' + table(['NBT', '字节hash完全一致', 'Neo/Forge DataVersion', '尺寸一致', '嵌入人员一致', '真实箱数量一致', '运行槽定义一致'],
                             [[x['id'], x['equal'], [x['neo_data_version'], x['forge_data_version']], x['size_equal'], x['embedded_entities_equal'], x['chest_count_equal'], x['runtime_guards_equal']] for x in differences['structure_hashes']])
          + '\nNBT字节不同不直接证明布局不同：DataVersion、物品count/Count、实体保存API可能有差异；请用各端原始快照核对。[Forge解析数据](data/forge_structures.json)与data/nbt/forge保存独立结果。两端完整NBT、配方、loot、军事与客户端源码均各自保存，不用Neo原文替代Forge。来源manifest记录每端路径/字节/hash，baseline各记分支/HEAD/dirty。\n\n'
          + '独立小哨站继续使用原版群系jigsaw；维度内自然设施是大型基地及附属模块，不能把每一个小模板都说成独立维度随机结构。自然生成只影响新生成区块，不自动覆盖旧区块或玩家建筑。\n\n'
          + '本档案不会自动构建或宣布任一端实机通过。城市驻军预算与第一人称反馈正在本轮修改时，最终以刷新后的来源快照和主任务验证记录为准。\n')
    # Forge gets a pointer, not an inconsistent second editable data source.
    write(a.forge / 'docs/制作资料/README.md', '# 黑光模组制作资料（Forge指针）\n\n'
          + '完整主档案位于 [NeoForge工程 docs/制作资料](../../../blacklight-evolution/docs/制作资料/README.md)。本工程 Minecraft1.20.1 / Java17；主工程 Minecraft1.21.1 / Java21。\n\n'
          + 'Forge资源使用 `structures/recipes/loot_tables`，Neo使用 `structure/recipe/loot_table`；配方结果分别为 `item` / `id`，保存与事件API不同。[逐项对照](../../../blacklight-evolution/docs/制作资料/两端差异.md) 与主档案 sources/forge 保存了本工程原文及SHA。不要把主档案里的Neo命名路径直接复制进Forge资源目录。\n\n'
          + f'最后生成：{baseline["generated_at"]}，本工程分支 `{baseline["forge"]["branch"]}`，HEAD `{baseline["forge"]["head"]}`，dirty={baseline["forge"]["dirty"]}。正式验证结果读本工程当前任务记录，档案生成不等于游戏通过。\n')
    write(ROOT.parent / '黑光模组制作资料.md', '# 黑光模组制作资料入口\n\n'
          + '完整档案：[blacklight-evolution/docs/制作资料/README.md](blacklight-evolution/docs/制作资料/README.md)。\n\n'
          + '内含十兵种卡、八枪七弹数值与配方、军衔和编制、七个建筑NBT卡与真实箱/人员/模块、六种场景块、感染生物及巨型宿主概览、离线外观图、用户实机失败反馈和BB参考模型。JSON/Excel可读CSV、原始源码快照及SHA用于后续持续制作。\n\n'
          + 'Forge入口：[blacklight-evolution-forge-1.20.1/docs/制作资料/README.md](blacklight-evolution-forge-1.20.1/docs/制作资料/README.md)。离线预览与失败截图不等于实机验收通过。\n')
    write(root / '覆盖与验证.md', '# 覆盖、边界与复查\n\n'
          + f'生成时间 {baseline["generated_at"]}；当前工作树详情 [baseline.json](data/baseline.json)。\n\n'
          + '- 已提取：10军事实体，8枪/7弹全部目录数值与两端原始配方；两端loot原文；7NBT尺寸、箱/方块实体与嵌入实体；大型基地16/20运行槽及模块bounds/entry/floors；6场景块；所有结构集/模板池原文。\n'
          + '- 图片：十兵种由生产Java+PNG重绘，七建筑由实际NBT重绘，八枪与六场景块由实际JSON离线示意；猎手/海德拉保留已有作者预览。材质与光照近似，建筑图不含NBT人员，缺少这些不能推断无驻军。\n'
          + '- 用户三张原始实机截图均保留为失败反馈；来源署名/许可截图仅存相关裁剪。参考bbmodel完整只读复制、内嵌PNG、结构/动作清单单独保存，当前未直接整体导入JAR。\n'
          + '- 感染生物攻击系统/成长/全部玩家技能未在本次完整重写；普通宿主属性随物种和保存数据变化。没有可靠新图的感染体/巨型单位/程序蜂巢明确缺图，不生成假实机图。\n'
          + '- selector找不到实体的用户反馈未标记成功；role转换保持原注册类型，示例与解释见兵种档。\n'
          + '- 生成器执行与PNG目视抽查是资料验收，不能替代Gradle/GameTest/普通服/GL实机。最新运行验收只读主工程现有 military.4 记录。\n\n'
          + '生产改动完成后的唯一步骤：运行 `python tools/archive_military_catalog.py` 完整刷新，再核对manifest、图片与本轮实际测试记录。资料生成不自动提交、推送、打包、覆盖手工实机证据或用户存档。\n')


def lang_name(a, id_):
    return a.json(RES / 'assets/blacklight/lang/zh_cn.json').get('item.blacklight.' + id_, id_)


def recipe_section(d, id_):
    selected = [r for r in d['recipes'] if r['id'] == id_ and r['platform'] == 'neoforge']
    if not selected:
        return '没有提取到同名配方，待核对；不能推定为可合成。\n\n'
    r = selected[0]
    raw = r['raw']
    return ('原文：[' + r['source'] + '](sources/neoforge/' + r['source'] + ')；SHA-256 `' + r['sha256'] + '`。\n\n'
            + '```json\n' + json.dumps(raw, ensure_ascii=False, indent=2) + '\n```\n\n')


def write_buildings(a, d):
    root = a.output
    rows = [[f"[{s['id']}](建筑卡/{s['id']}.md)", '×'.join(map(str, s['size'])), len(s['chests']), s['embedded_entity_count'], s['runtime_slots']] for s in d['structures']]
    md = '# 建筑、库存与生成\n\n' + table(['NBT模板', '尺寸X×Y×Z', '真实箱', '嵌入实体', '运行时驻军槽'], rows)
    md += '''
大型基地NBT的 entities 为空，这是由CityMilitaryService按持久槽延迟发放16/20名驻军的设计，不等于“无驻军”。旧小型模板按NBT嵌入实体摆放，具体军衔/role字段若缺失由加载迁移决定，档案不凭空补成新军官。`runtime_slots=0`只表示该模板没有大型基地槽系统，不能覆盖NBT实体。

大型基地首槽有效role为team_leader；岗位role=command变为officer；operations变为commander；其余trooper。槽0至少sergeant，普通槽private，重装trooper至少corporal；军官/指挥官注册出生的lieutenant/major不会被低rank降级。role=某建筑岗位名并不等于 MilitaryRole 枚举。前两槽若生成附属checkpoint，则定位转到checkpoint局部(3,1,6)/(13,1,6)，按checkpoint旋转；否则使用原guard位置。guards精确列表在逐卡和JSON。

基地持久槽记录UUID/损失，维护每10tick、每次最多2人。军事驻军使用独立 `militaryGarrisonLimit`（当前默认80、范围0..200），每维度只统计被SavedData基地slots引用、已加载且存活的MilitarySoldier；自然基地和管理员放置基地都受它约束。史莱姆等无关Mob、未登记基地槽的兵不占该额度；`cityMobLimit`继续单独限制城市常规人口。额度0暂停新发放，保留已有槽/UUID与人员。已发放成员死亡不重复发放；卸载不当成死亡。补给检查真实原版箱库存、消耗实际匹配弹药，不依装饰 military_crate。模块中医疗/研究/指挥名称是布局语义，不能当成已实现治疗、研究任务或Blacknet界面。

`base status`除 issued/loaded/lost/unloaded 和状态外，追加 muster原因及 loaded_garrison 当前/上限。原因包括 COMPLETE、CONFIG_POPULATION_DISABLED、CITY_POPULATION_DISABLED、GARRISON_LIMIT、NO_PLAYERS、NO_NEARBY_PLAYER、POST_CHUNK_UNLOADED、POST_NOT_TICKING、POST_BLOCKED、POST_COLLISION、READY；状态检查不会为了说明原因强载区块或创建实体。用户原失败截图仍保留为MUSTERING/0issued，不能把新增原因文本当作该旧截图已通过。

## 世界生成与城市

普通结构使用 structure_set + structure + template_pool；城市大型基地由自定义 CityMilitaryStructure/Plan/Placement 选择地块并落模板，命令 `/blacklight military base place` 也走受控放置。`/place template`只摆NBT，不等价于登记运行时基地槽。结构集spacing/separation单位chunk，通常1chunk=16方块，不能当作准确两座直线距离。

'''
    sets = [w for w in d['worldgen'] if w['platform'] == 'neoforge' and w['id'].startswith('structure_set/')]
    md += table(['结构集', 'placement原文', 'structures原文'], [[w['id'], w['raw'].get('placement'), w['raw'].get('structures')] for w in sets])
    md += '\n全部结构设置/模板池原文在 [worldgen.json](data/worldgen.json)，精确两端源文件位于sources。CityMilitaryPlan最大4环候选、四种旋转、地块高差不超过3、避开水/道路/阻塞；城市要求入口16格以内接路。军方模板65×18×65，BW65×18×81，奇数旋转会交换占地宽深。蓝图、生产large_bases元数据、NBT各自保存，不把作者草稿当生产真值。\n'
    md += '\n## 当前配置默认值\n\n配置默认值从各端 CityConfig.java 提取；实际存档服务器配置可能覆盖默认，未读取用户配置文件。CityMobLimit与军事专用额度若并存，必须按服务源码分别解释，不能把数值相同当作同一预算。\n\n'
    md += table(['端', '配置名', '默认', '最小', '最大'], [[platform, cfg['name'], cfg['default'], cfg.get('minimum', ''), cfg.get('maximum', '')] for platform, configs in d['city_configuration'].items() for cfg in configs])
    md += '\n[配置JSON](data/city-configuration.json)。本次读取时的维护/阻塞说明以 [CityMilitaryService.java](sources/neoforge/src/main/java/com/codex/blacklight/city/CityMilitaryService.java) 为准。\n'
    write(root / '建筑与生成.md', md)
    write(root / '建筑卡/README.md', '# 七个NBT模板索引\n\n' + '\n'.join(f"- [{s['id']}]({s['id']}.md)" for s in d['structures']) + '\n\n[总体生成/岗位规则](../建筑与生成.md)\n')
    for s in d['structures']:
        meta = s['large_base_metadata']
        content = '# ' + s['id'] + '\n\n'
        content += table(['字段', '值'], [[k, s[k]] for k in ['size', 'data_version', 'serialized_blocks', 'non_air_blocks', 'palette_size', 'embedded_entity_count', 'runtime_slots', 'source', 'sha256']])
        content += f"\n![实际NBT离线几何图](../{s['preview']})\n\n按实际NBT块生成，原版材质近似，不渲染实体/箱中物资，非实机截图。\n\n"
        if meta:
            content += f"入口局部坐标 `{meta.get('entrance')}`，入口宽 `{meta.get('entrance_width')}`；弹药箱局部坐标 `{meta.get('ammo_chests')}`。所有坐标是模板局部坐标，旋转/放置地面由服务端转换。\n\n"
            content += '## 模块原文\n\n'
            modules = meta.get('modules', [])
            content += '```json\n' + json.dumps(modules, ensure_ascii=False, indent=2) + '\n```\n\n'
            content += '每个模块的bounds、entry、floors保留原始数据；字段若不存在就不补造。完整原文见[data/structures.json](../data/structures.json)。\n\n'
            content += '## 运行槽有效身份\n\n' + table(['槽', '原岗位', '局部x,y,z', 'heavy', '有效角色', '注册ID', '军衔', '枪', '可重定位哨卡'],
                [[g['slot'], g['role'], [g['x'], g['y'], g['z']], g['heavy'], g['effective_role'], g['registered_entity'], g['effective_rank'], g['effective_weapon'], g['checkpoint_override']] for g in s['runtime_guards']])
            content += f"\n![平面旧制作图](../assets/建筑/{s['id']}-floorplan.png)\n\n![剖面旧制作图](../assets/建筑/{s['id']}-cutaway.png)\n\n这些补充图复制自已有制作预览，独立来源hash见preview-manifest；本次实际NBT新渲染图在页首。\n\n"
        content += '## 真实箱原始库存与LootTable\n\n'
        content += table(['局部pos', '方块', 'NBT原文'], [[c['pos'], c['block']['Name'], c['nbt']] for c in s['chests']]) if s['chests'] else '实际NBT中没有原版箱块记录。\n'
        content += '\nLootTable是延迟战利品表引用，不是已开箱随机结果；Items是模板固定物品。配方/loot原文分别在 [recipes](../data/recipes.json)、[loot_tables](../data/loot_tables.json)，原文件快照保留JSON。\n\n'
        content += '## NBT嵌入实体\n\n'
        content += table(['ID', '位置', 'NBT完整原文'], [[e['id'], e['pos'], e['nbt']] for e in s['embedded_entities']]) if s['embedded_entities'] else 'NBT没有嵌入实体。大型基地的驻军由运行槽服务发放，详见上文，不能由此断言没有人员。\n'
        content += '\n[解析JSON（含palette/块计数/箱与实体原文）](../data/nbt/' + s['id'] + '.json)；[规则总览](../建筑与生成.md)。\n'
        write(root / '建筑卡' / (s['id'] + '.md'), content)


def write_creatures(a, d):
    md = '# 感染生物与巨型单位概览\n\n这部分为现有生物制作索引，记录已核对基础属性和源码入口，不重写全部玩家进化/攻击系统。未单独登记的护甲/属性不填0，未产出可靠外观图明确待补。\n\n'
    md += table(['ID', '名称', '基础HP', '攻击属性', '速度', '抗击退', '注册宽×高', '说明'], [[x['id'], x['name'], x['base_health'], x['attack_attribute'] if x['attack_attribute'] is not None else '未单独登记', x['movement_speed'], x['knockback_resistance'], f"{x['registered_width']}×{x['registered_height']}", x['note']] for x in d['creatures']])
    md += '\n[creatures.json](data/creatures.json)保留当前attributes方法原文，数值是基础值；Hunter动作倍率、感染伤害预算和难度、宿主物种数据、HiveDefense会再改变伤害/生命行为。感染宿主infected_host会保存来源物种、躯体和成长字段；0.9×1.2是注册默认值，实际dimensions按宿主/尺度重算，不能用它代表巨型体型。\n\n'
    md += '## 原生资源入口\n\n' + table(['ID', '真实资源路径', '外观选择'], [[x['id'], x['resource_paths'] or '由渲染器选择来源物种/玩家皮肤', x['appearance_note']] for x in d['creatures']])
    md += '\n这些模型/纹理/动画及相应模型、渲染源码均进入两端sources快照，SHA由manifest索引。猎手/海德拉还使用connected网格，不把geo骨架误写成最终完整表面。\n\n'
    for name in ['hunter-front.png', 'hunter-three-quarter.png', 'hydra-front.png', 'hydra-three-quarter.png']:
        if (a.output / 'assets/感染生物' / name).is_file():
            md += f'![已有作者制作预览 {name}](assets/感染生物/{name})\n\n'
    md += '这些是已有作者预览，不是本轮实机通过截图。宿主/潜伏者/喷吐者/暴徒/蜂巢/残骸没有在本次重绘外观，资源仍在生产assets/blacklight的geo、textures/entity、animations中，详查源码模型选择；本档案不以相似图冒充。\n\n## 巨型宿主资料档\n\n'
    md += table(['来源物种', 'family', '物理宽×高', '标称视觉宽×深×高', 'HP', '攻击属性', '速度', '实际作者视觉bounds'], [[b['source_species'], b['family'], f"{b['locomotion_width']}×{b['locomotion_height']}", [b['visual_width'], b['visual_depth'], b['visual_height']], b['base_health'], b['attack_attribute'], b['movement_speed'], b['local_visual_bounds']] for b in d['infected_boss_profiles']])
    md += '\n' + table(['来源物种', '实际mesh资源路径'], [[b['source_species'], b['resource_path']] for b in d['infected_boss_profiles']])
    md += '''
这四项是 `blacklight:infected_host` 的 InfectedBossProfile，不是四个新的注册实体ID。物理locomotion碰撞、各身体part命中盒与完整视觉bounds分开：视觉bounds用于高度/边界检查，不直接当成地形碰撞。visualWidth/Depth/Height是作者标称尺寸，实际不对称手爪/尾巴由localVisualBounds精确边界定义。巨型体bodyScale=1；紧凑体根据宿主Height/BaseBodyScale/成长进度缩放，不能给所有感染warden写死30格物理高。

巨型/紧凑判定、18000tick成长、保存迁移见 BlacklightInfectedHost.java；精确身体part数据在 [InfectedBossProfile.java](sources/neoforge/src/main/java/com/codex/blacklight/entity/InfectedBossProfile.java)。本次未新增这些巨型单位实机外观图，待补；不把军事实体图套用。

## 程序型 colossal_hive

它不是第八个NBT：`ColossalHiveLayout` 在代码里生成。RADIUS=56，MIN_Y=-10，MAX_Y=64；CHAMBER_CLEAR_RADIUS=23、CHAMBER_CLEAR_HEIGHT=42；核心局部(28,1,8)，4个营养点(-26,1,0)、(26,1,0)、(0,1,-26)、(0,1,26)。KEEP表示不改原地形，不应把113×75×113计算范围称为填满的方块建筑。

来源：[ColossalHiveLayout](sources/neoforge/src/main/java/com/codex/blacklight/world/ColossalHiveLayout.java)、ColossalHivePiece/Structure与worldgen/structure_set/colossal_hives.json。生成布局与实体蜂巢 `blacklight_hive` 是不同概念。本次没有制作该程序建筑的新离线图或实机图，缺图明确保留。旧玩家能力、宿主全物种纹理考据与原版生物属性继承不在本次完整归档范围。
'''
    write(a.output / '感染生物与巨型单位.md', md)


def write_client_feedback(a, d):
    p = d['client_presentation']
    md = '# 第一人称枪械与开火反馈\n\n'
    md += '这是 military.4 的生产资产/规则记录。客户端第一人称通过 FirearmViewEvents保留原版物品渲染器，拦截该持枪手的原版挥手/切入摆动；左右手分别维护状态。只在本玩家第一人称、有效可见本体持FirearmItem时处理；第三人称物品display由模型原文保留。当前资料不宣称GL实机已通过，用户先前遮挡失败图仍在实机截图目录。\n\n'
    md += table(['武器', 'JSON元素数', '右手第一人称display原文', '左手第一人称display原文'],
        [[w['id'], w['elements'], (w['display'] or {}).get('firstperson_righthand'), (w['display'] or {}).get('firstperson_lefthand')] for w in p['weapon_models']])
    md += '\n反馈由服务端同步的弹匣和WeaponReadyAt变化驱动，不根据按键推断开火。同武器同槽、时间未倒退，弹匣下降且readyAt前进才可能生成shot；弹匣增加且readyAt仍在装填窗口才生成reload。切枪/切槽、换世界/玩家、菜单/暂停/第三人称、变身/伪装/吞噬控制等会重置或隐藏状态；空仓、冷却失败不应凭空产生新shot。具体包延迟窗口和重装条件以FirearmViewState原文为准。\n\n'
    md += '两手在同一观察帧完整交换不同快照时，同时重建观察基线，避免同型号不同弹量被误判为shot；单手真实射击恰好匹配另一手快照不抑制。跨tick分裂且与真实射击完全等价的数据仍无法无歧义区分；该有限视觉边界不修改库存、伤害或射击授权。\n\n'
    md += '## 当前反馈公式原文\n\n```java\n' + str(p.get('frame_formula_raw')) + '\n```\n\n'
    md += '## 当前第一人称附加变换原文\n\n```java\n' + '\n'.join(p.get('first_person_transforms_raw', [])) + '\n```\n\n'
    md += '视觉枪口与命中射线分离：命中仍从原始eye-.18射线起点求交，视觉枪口向方向前移的源码参数为 `' + str(p.get('muzzle_distance')) + '`；示踪粒子loop原文 `' + str(p.get('tracer_loop_raw')) + '`。避免贴脸大粒子和原版挥手遮挡的实际效果仍需GL实机核对。\n\n'
    md += '完整源与display：[client-presentation.json](data/client-presentation.json)，FirearmViewEvents/State、FirearmItem、FirearmsCombat和八个物品模型均有sources快照/hash。静态枪模图只画原JSON几何及面UV，不套第一人称display，不能拿它判断不同FOV/左右手视角。\n'
    write(a.output / '第一人称反馈.md', md)


def write_block_damage(a, d):
    block = d['block_damage']
    md = '# 枪械方块破坏规则\n\n'
    md += '**与对生物伤害分开。** WeaponCatalog的damage/radius描述原武器平衡；以下是新方块材质耐久与命中冲击点，不是真实枪械穿深/材质强度，也不能把对生物爆破半径照搬为方块破坏半径。\n\n'
    md += '提取状态：' + block['status'] + '。资料生成不等于测试或实机通过。\n\n'
    for platform, record in block['platforms'].items():
        md += '## ' + platform + '\n\n已存在源码：`' + '`, `'.join(record['available_files']) + '`。\n\n'
        md += '### 服务端配置默认值\n\n'
        md += table(['配置', '默认', '最小', '最大'], [[c['name'], c['default'], c.get('minimum', ''), c.get('maximum', '')] for c in record['configuration']]) if record['configuration'] else '配置适配文件尚未提取到，最终冻结后刷新，不能推定已启用。\n'
        if 'durability' in record:
            md += '\n### 材质耐久公式\n\n' + table(['材质', '耐久冲击点表达式'], record['durability']['switch_cases'].items())
            md += '\n### 子弹冲击公式\n\n' + table(['材质', '每次命中表达式'], record['bulletImpact']['switch_cases'].items())
            md += '\n`hardness`是运行方块破坏硬度，`weaponDamage`是传入玩法值，`machineGun`是机枪类别判断。PROTECTED、不合法硬度/参数与负硬度不可破坏，METAL不接受普通子弹冲击。适配器给子弹传入的是 `definition.damage()`：默认NPC对生物缩放后的profile.damage并未被当成方块冲击点，这两列必须分开。具体分类优先级、玩家/生物破坏权限与gamerule检查由适配器决定，不能仅凭策略表推定所有块都能打碎。\n\n'
            md += '### 爆破方块半径\n\n' + table(['武器', '类型', '原实体效果半径', '方块破坏半径'], [[w['id'], w['kind'], w['entity_effect_radius'], w['block_blast_radius']] for w in record['weapon_block_blast_radius']])
            md += '\n### 爆破冲击原文\n\n```java\n' + record['blastImpact']['body'] + '\n```\n\n'
        if record.get('material_raw'):
            md += '### 实际材质分类原文\n\n```java\n' + record['material_raw'] + '\n```\n\n'
        if record.get('allowed_raw'):
            md += '### 实际破坏权限原文\n\n```java\n' + record['allowed_raw'] + '\n```\n\n'
        if record.get('blast_raw'):
            md += '实际爆破半径取 `min(WeaponDefinition.radius, BlockDamagePolicy.blastRadius)`；先在已加载、世界边界和高度合法范围内枚举，再按距离和位置排序。超过中心1.5格的候选需碰撞射线无遮挡；逐个受配置上限限制，不承诺穿墙炸穿整座建筑。完整工作循环原文在block-damage.json的blast_raw。\n\n'
        if record.get('ledger_constructors'):
            md += '当前适配器ledger构造参数原文：`' + '`, `'.join(record['ledger_constructors']) + '`。\n\n'
        md += 'BlockDamageLedger是有界、临时缓存：相同位置且state一致才累计，state更换从新命中计算；过期/倒退时间清除，容量满淘汰最近最久未用条目，达到耐久阈值才返回broken，未碎裂纹stage范围0..9。不是永久存档中的建筑血量系统。具体缓存容量/寿命以适配器构造值或配置为准。\n\n'
        if record.get('placed_raw'):
            md += '实际单块/多块放置事件清除旧位置裂痕，覆盖玩家拆换相同状态木板的修理场景；不监听每次射击权限探测都会发出的BreakEvent。任意命令、活塞、其他模组不发放置事件的替换没有统一世代标记，不能宣称全面识别。\n\n```java\n' + record['placed_raw'] + '\n```\n\n'
        md += '爆破适配器显式接收命中世界，只允许存活、未移除、仍在同一世界的射手；旧世界弹体不会根据已换维度的射手到新世界同坐标破坏。移除/死亡/跨维度射手只保留弹体消失视觉，不产生建筑和实体伤害。\n\n'
        md += '### 方块标签原文\n\n' + table(['原始路径', 'JSON'], [[t['source'], t['raw']] for t in record['protected_tags']])
        md += '\n这里同时保留通用抓取标签以便追溯；只有适配器引用的 firearm_* 标签才参与这套材质/保护分类。不能把throwable/ungrabbable标签直接当枪械可破坏白名单。\n\n'
    md += '\n完整公式与当前源码可追溯于 [block-damage.json](data/block-damage.json)、sources两端firearms目录及data/source-manifest.json。方块适配器、配置或标签仍在修改时必须重新生成；最终保护规则、掉落、视线与工作量边界以冻结源和项目验收记录为准。\n'
    write(a.output / '方块破坏规则.md', md)


def verify_archive(root, forge):
    """Read-only evidence check, separate from runtime or GL claims."""
    from PIL import Image
    errors = []
    manifest = json.loads((root / 'data/source-manifest.json').read_text(encoding='utf-8'))
    for item in manifest:
        path = root / item['snapshot']
        if not path.is_file() or sha(path) != item['sha256']:
            errors.append('snapshot hash: ' + item['snapshot'])
        current = (ROOT if item['platform'] == 'neoforge' else forge) / item['path']
        if not current.is_file() or sha(current) != item['sha256']:
            errors.append('current source changed: ' + item['platform'] + '/' + item['path'])
    previews = json.loads((root / 'data/preview-manifest.json').read_text(encoding='utf-8'))
    for item in previews:
        path = root / item['path']
        if not path.is_file() or sha(path) != item['sha256']:
            errors.append('preview hash: ' + item['path'])
        for source in item['sources']:
            current = ROOT / source['path']
            if not current.is_file() or sha(current) != source['sha256']:
                errors.append('preview source changed: ' + source['path'])
    pngs = list((root / 'assets').rglob('*.png')) + list((root / '实机截图').glob('*.png')) + list((root / '参考模型').rglob('*.png'))
    for path in pngs:
        with Image.open(path) as im:
            im.verify()
    docs = list(root.glob('*.md'))
    for directory in ['兵种卡', '建筑卡', '实机截图', '参考模型']:
        docs += list((root / directory).glob('*.md'))
    links = 0
    for path in docs:
        for link in re.findall(r'\]\(([^)]+)\)', path.read_text(encoding='utf-8')):
            if link.startswith(('http:', 'https:', '#', 'data:')):
                continue
            target = (path.parent / link.split('#')[0]).resolve()
            links += 1
            if not target.exists():
                errors.append('broken link: ' + path.name + ' -> ' + link)
    data = json.loads((root / 'data/catalog.json').read_text(encoding='utf-8'))
    for key, expected in [('soldiers', 10), ('weapons', 8), ('ammo', 7), ('structures', 7), ('forge_structures', 7), ('facilities', 6)]:
        if len(data[key]) != expected:
            errors.append('count: ' + key)
    for item in json.loads((root / '实机截图/index.json').read_text(encoding='utf-8')):
        path = root / item['file']
        if not path.is_file() or sha(path) != item['sha256']:
            errors.append('manual screenshot: ' + item['file'])
    model = data['reference_model']
    if not (root / model['file']).is_file() or sha(root / model['file']) != model['sha256']:
        errors.append('reference model hash')
    result = dict(status='PASS' if not errors else 'FAIL', source_snapshots=len(manifest), previews=len(previews),
                  pngs_checked=len(pngs), generated_documents=len(docs), relative_links_checked=links, errors=errors)
    print(json.dumps(result, ensure_ascii=False))
    if errors:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
