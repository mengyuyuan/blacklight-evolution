"""Author the original frozen research station. Python 3 standard library only.

The NBT is deterministic; rerun from any directory after changing the layout.
No game assets, worlds, UUIDs, or external templates are imported.
"""
from pathlib import Path
import gzip
import json
import struct

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'


def string(value):
    raw = value.encode('utf8')
    return struct.pack('>H', len(raw)) + raw


def payload(kind, value):
    if kind == 1:
        return struct.pack('>b', value)
    if kind == 3:
        return struct.pack('>i', value)
    if kind == 5:
        return struct.pack('>f', value)
    if kind == 6:
        return struct.pack('>d', value)
    if kind == 8:
        return string(value)
    if kind == 9:
        element, items = value
        return bytes([element]) + struct.pack('>i', len(items)) + b''.join(payload(element, item) for item in items)
    if kind == 10:
        return b''.join(bytes([t]) + string(name) + payload(t, v) for name, (t, v) in value.items()) + b'\0'
    raise ValueError(kind)


blocks = {}


def put(x, y, z, name, props=None, nbt=None):
    blocks[x, y, z] = (name if ':' in name else 'minecraft:' + name, props or {}, nbt)


def fill(x1, y1, z1, x2, y2, z2, name, props=None):
    for x in range(x1, x2 + 1):
        for y in range(y1, y2 + 1):
            for z in range(z1, z2 + 1):
                put(x, y, z, name, props)


def sign(x, y, z, key, facing='north'):
    messages = [json.dumps({'translate': f'sign.blacklight.{key}.{i}'}, separators=(',', ':')) for i in range(1, 5)]
    text = {'messages': (9, (8, messages)), 'color': (8, 'black'), 'has_glowing_text': (1, 0)}
    put(x, y, z, 'birch_wall_sign', {'facing': facing, 'waterlogged': 'false'}, {
        'id': (8, 'minecraft:sign'), 'is_waxed': (1, 1), 'front_text': (10, text)})


def chest(x, y, z, loot):
    put(x, y, z, 'chest', {'facing': 'north', 'type': 'single', 'waterlogged': 'false'}, {
        'id': (8, 'minecraft:chest'), 'LootTable': (8, 'blacklight:chests/' + loot)})


# Buried 23 x 23 basement: all air is explicit so terrain cannot fill the rooms.
fill(0, 0, 0, 22, 6, 22, 'stone_bricks')
fill(1, 1, 1, 21, 5, 21, 'air')
fill(1, 0, 1, 21, 0, 21, 'smooth_stone')
fill(1, 6, 1, 21, 6, 21, 'polished_andesite')
for z in (1, 21):
    fill(1, 4, z, 21, 4, z, 'cyan_concrete')
for x in (1, 21):
    fill(x, 4, 1, x, 4, 21, 'cyan_concrete')

# West laboratory, east quarantine, rear cold storage. Openings are two blocks high.
for x in (7, 15):
    fill(x, 1, 1, x, 5, 16, 'white_concrete')
    fill(x, 2, 4, x, 3, 8, 'light_blue_stained_glass')
    fill(x, 1, 12, x, 3, 13, 'air')
fill(1, 1, 17, 21, 5, 17, 'white_concrete')
fill(10, 1, 17, 12, 3, 17, 'air')
fill(2, 1, 3, 5, 1, 3, 'polished_andesite')
for x in (2, 4):
    put(x, 2, 3, 'brewing_stand', {'has_bottle_0': 'false', 'has_bottle_1': 'false', 'has_bottle_2': 'false'})
fill(2, 1, 8, 5, 1, 9, 'quartz_slab', {'type': 'bottom', 'waterlogged': 'false'})
put(3, 2, 9, 'red_carpet')
# Exposed service wiring powers a local indicator. A survivor can strip the run
# for crafting dust, and the dark lamp shows the circuit has been disconnected.
fill(2, 1, 6, 5, 1, 6, 'polished_andesite')
put(1, 2, 6, 'redstone_block')
for x in range(2, 5):
    put(x, 2, 6, 'redstone_wire', {'north': 'none', 'south': 'none', 'east': 'side', 'west': 'side', 'power': str(17-x)})
put(5, 2, 6, 'redstone_lamp', {'lit': 'true'})
fill(16, 1, 8, 21, 4, 8, 'iron_bars')
fill(18, 1, 8, 19, 2, 8, 'air')  # breached quarantine gate
fill(16, 1, 3, 20, 1, 3, 'packed_ice')
fill(2, 1, 19, 7, 4, 21, 'packed_ice')
fill(15, 1, 19, 20, 4, 21, 'packed_ice')
fill(9, 1, 20, 13, 3, 20, 'light_blue_stained_glass')
chest(11, 1, 19, 'research_samples')
chest(3, 1, 14, 'research_supplies')
for x, z in ((4, 5), (4, 14), (11, 15), (18, 5), (18, 14), (11, 19)):
    put(x, 5, z, 'sea_lantern')
sign(11, 3, 19, 'samples')
sign(5, 2, 16, 'lab', 'north')
put(5, 2, 17, 'white_concrete')
sign(18, 2, 16, 'quarantine', 'north')

# Enclosed stairwell climbs north from basement to the surface; 3-wide clear path.
fill(8, 1, 1, 14, 11, 12, 'stone_bricks')
for z in range(1, 13):
    step = 12 - z
    fill(9, step + 1, z, 13, step + 4, z, 'air')
    fill(10, step, z, 12, step, z, 'stone_brick_stairs', {
        'facing': 'north', 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
    if z in (3, 7, 11):
        put(9, step + 3, z, 'sea_lantern')
fill(9, 1, 13, 13, 4, 14, 'air')

# Surface hut. With start_height -11 and ground delta 1, floor y11 is ground level.
fill(7, 11, 0, 15, 11, 5, 'stone_bricks')
fill(7, 12, 0, 15, 14, 5, 'white_concrete')
fill(8, 12, 1, 14, 14, 4, 'air')
fill(7, 15, 0, 15, 15, 5, 'dark_prismarine')
fill(10, 12, 0, 12, 13, 0, 'air')
fill(8, 13, 0, 9, 13, 0, 'light_blue_stained_glass')
fill(13, 13, 0, 14, 13, 0, 'light_blue_stained_glass')
fill(7, 14, 0, 15, 14, 0, 'cyan_concrete')
# Reopen stairs through the hut's back wall and its floor after decorating the hut.
for z in range(2, 6):
    step = 12 - z
    fill(10, step, z, 12, step + 4, z, 'air')
    fill(10, step, z, 12, step, z, 'stone_brick_stairs', {
        'facing': 'north', 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
put(8, 14, 2, 'sea_lantern')
put(14, 14, 2, 'sea_lantern')
sign(11, 14, 1, 'entrance', 'south')
fill(15, 16, 4, 15, 19, 4, 'iron_bars')
put(15, 20, 4, 'redstone_block')

# Two persistent hosts use existing RC16 zombie anatomy, not new placeholder models.
entities = []
for x, z in ((4, 11), (18, 6)):
    entities.append({
        'pos': (9, (6, [x + .5, 1.0, z + .5])), 'blockPos': (9, (3, [x, 1, z])),
        'nbt': (10, {
            'id': (8, 'blacklight:infected_host'), 'PersistenceRequired': (1, 1), 'Health': (5, 30.0),
            'Rotation': (9, (5, [0.0, 0.0])),
            'BlacklightHost': (10, {'Type': (8, 'minecraft:zombie'), 'Family': (8, 'ground'),
                'Humanoid': (1, 1), 'Quadruped': (1, 0), 'Width': (5, .672), 'Height': (5, 2.184), 'Visual': (10, {})})
        })
    })

palette, lookup, data = [], {}, []
for pos, (name, props, nbt) in sorted(blocks.items(), key=lambda entry: (entry[0][1], entry[0][0], entry[0][2])):
    key = (name, tuple(sorted(props.items())))
    if key not in lookup:
        lookup[key] = len(palette)
        state = {'Name': (8, name)}
        if props:
            state['Properties'] = (10, {k: (8, v) for k, v in props.items()})
        palette.append(state)
    block = {'pos': (9, (3, list(pos))), 'state': (3, lookup[key])}
    if nbt:
        block['nbt'] = (10, nbt)
    data.append(block)
template = {'DataVersion': (3, 3955), 'size': (9, (3, [23, 21, 23])),
            'palette': (9, (10, palette)), 'blocks': (9, (10, data)), 'entities': (9, (10, entities))}
destination = ROOT / 'data/blacklight/structure/frozen_research_station.nbt'
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_bytes(gzip.compress(b'\x0a\0\0' + payload(10, template), mtime=0))
print(f'Wrote {destination.name}: {len(data)} blocks, {len(palette)} states, {len(entities)} hosts')
