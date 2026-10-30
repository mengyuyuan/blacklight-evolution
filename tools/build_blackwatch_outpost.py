"""Author original Blackwatch outpost and checkpoint. Python standard library only.

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


def bed(x, y, z):
    for dz, part in ((0, 'foot'), (-1, 'head')):
        put(x, y, z + dz, 'gray_bed', {'facing': 'north', 'part': part, 'occupied': 'false'})


def guard(x, y, z, kind='soldier', faction='blackwatch'):
    return {'pos': (9, (6, [x + .5, float(y), z + .5])), 'blockPos': (9, (3, [x, y, z])),
            'nbt': (10, {'id': (8, 'blacklight:' + faction + '_' + kind), 'PersistenceRequired': (1, 1),
                         'Rotation': (9, (5, [180., 0.]))})}


def write_template(name, width, entities):
    for index, entity in enumerate(entities):
        nbt=entity['nbt'][1]; pos=entity['pos'][1][1]
        nbt['MilitaryRank']=(8,'sergeant' if index==0 else 'corporal' if nbt['id'][1].endswith('_heavy') else 'private')
        nbt['MilitaryDeployment']=(8,name);nbt['MilitaryDeploymentCapacity']=(3,max(8,len(entities)))
        for axis,value in zip('XYZ',(width/2-pos[0],1-pos[1],width/2-pos[2])):nbt['MilitaryDeployment'+axis]=(6,float(value))
    palette, lookup, data = [], {}, []
    for pos, (block, props, nbt) in sorted(blocks.items()):
        key = (block, tuple(sorted(props.items())))
        if key not in lookup:
            lookup[key] = len(palette)
            state = {'Name': (8, block)}
            if props:
                state['Properties'] = (10, {k: (8, v) for k, v in props.items()})
            palette.append(state)
        entry = {'pos': (9, (3, list(pos))), 'state': (3, lookup[key])}
        if nbt:
            entry['nbt'] = (10, nbt)
        data.append(entry)
    forge = 'minecraft_version=1.20.1' in (ROOT.parents[2] / 'gradle.properties').read_text(encoding='utf8')
    template = {'DataVersion': (3, 3465 if forge else 3955), 'size': (9, (3, [width, 9, width])),
                'palette': (9, (10, palette)), 'blocks': (9, (10, data)), 'entities': (9, (10, entities))}
    destination = ROOT / ('data/blacklight/structures' if forge else 'data/blacklight/structure') / (name + '.nbt')
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(gzip.compress(b'\x0a\0\0' + payload(10, template), mtime=0))
    print(f'Wrote {destination.name}: {len(data)} blocks, {len(entities)} soldiers')


# 25 x 25 checkpoint: front entry, motor yard, concrete barracks, guard tower.
fill(0,0,0,24,8,24,'air')
fill(0,0,0,24,0,24,'polished_andesite')
for x in (0,24):
    fill(x,1,0,x,2,24,'gray_concrete')
    fill(x,3,0,x,3,24,'iron_bars')
for z in (0,24):
    fill(0,1,z,24,2,z,'gray_concrete')
    fill(0,3,z,24,3,z,'iron_bars')
fill(9,1,0,15,3,0,'air')
for x in (8,16):
    fill(x,1,0,x,4,0,'black_concrete');put(x,4,1,'sea_lantern')
fill(8,4,0,16,4,0,'black_concrete');fill(10,4,0,14,4,0,'red_concrete')
for z in range(1,12):
    for x in (9,15):put(x,0,z,'yellow_concrete' if z%2==0 else 'black_concrete')
# Barracks and armoury, with a wide open door and slit windows.
fill(4,1,13,20,5,22,'gray_concrete')
fill(5,1,14,19,4,21,'air')
fill(11,1,13,13,3,13,'air')
for x in (6,7,8,16,17,18):put(x,3,13,'black_stained_glass')
fill(4,5,13,20,5,22,'black_concrete')
for x in (7,17):put(x,4,17,'sea_lantern')
fill(5,1,20,9,1,21,'polished_blackstone')
chest(6,2,20,'blackwatch_technology');chest(18,1,20,'blackwatch_technology')
chest(13,1,21,'blackwatch_supplies')
for x,z in ((6,16),(8,16),(16,19),(18,19)):bed(x,1,z)
put(10,1,21,'crafting_table');put(11,1,21,'cauldron')
sign(10,2,12,'military_warning');sign(14,2,12,'military_technology');sign(13,2,21,'military_supplies')
fill(15,1,16,18,1,16,'smooth_stone')
for x in (15,17):put(x,2,16,'black_concrete');put(x,2,15,'lime_stained_glass')
# Cover in the yard, red identification stripes and a climbable watchtower.
for x,z in ((4,5),(19,6),(5,10),(19,10)):
    fill(x,1,z,x+1,1,z+1,'sandstone');put(x,2,z,'sandstone_slab',{'type':'bottom','waterlogged':'false'})
for x,z in ((1,17),(3,17),(1,22),(3,22)):fill(x,1,z,x,6,z,'polished_blackstone')
fill(1,6,17,3,6,22,'gray_concrete')
fill(1,7,17,3,7,17,'iron_bars');fill(1,7,22,3,7,22,'iron_bars')
for y in range(1,7):put(2,y,18,'ladder',{'facing':'south','waterlogged':'false'});put(2,y,17,'polished_blackstone')
fill(6,4,13,9,4,13,'red_concrete');fill(15,4,13,18,4,13,'red_concrete')
put(2,7,20,'sea_lantern')
entities=[]
for x,y,z,kind in ((7,1,7,'soldier'),(17,1,7,'soldier'),(9,1,17,'soldier'),(12,1,19,'heavy'),(2,7,19,'soldier')):
    entities.append(guard(x,y,z,kind))
write_template('blackwatch_outpost',25,entities)

# 17 x 17 checkpoint: a five-block road, two roofed guard posts, field supplies.
blocks.clear()
fill(0,0,0,16,8,16,'air');fill(0,0,0,16,0,16,'polished_andesite')
for x in (0,16):fill(x,1,0,x,2,16,'iron_bars')
for z in (0,16):
    fill(0,1,z,16,2,z,'iron_bars');fill(6,1,z,10,2,z,'air')
fill(6,0,0,10,0,16,'gray_concrete')
for z in range(17):
    for x in (6,10):put(x,0,z,'yellow_concrete' if z%2==0 else 'black_concrete')
for x,z in ((2,3),(12,3),(4,8),(11,8)):fill(x,1,z,x+1,1,z+1,'sandstone')
for x1,x2,z1 in ((1,5,10),(12,15,11)):
    fill(x1,1,z1,x2,4,15,'gray_concrete');fill(x1+1,1,z1+1,x2-1,3,14,'air')
    door=(x1+x2)//2
    fill(door,1,z1,door,2,z1,'air');fill(x1,4,z1,x2,4,15,'black_concrete')
    put(x1+1,3,z1+2,'sea_lantern')
chest(2,1,13,'blackwatch_supplies');chest(4,1,13,'blackwatch_technology')
put(13,1,14,'crafting_table')
sign(2,2,9,'military_checkpoint');sign(4,2,9,'military_technology')
for x in (5,11):
    fill(x,1,1,x,4,1,'polished_blackstone');put(x,4,2,'sea_lantern');put(x,3,1,'red_concrete')
entities=[guard(3,1,6),guard(13,1,6),guard(8,1,12,'heavy')]
write_template('blackwatch_checkpoint',17,entities)

# Conventional military: olive identification, open vehicle lane, barracks and command shelter.
blocks.clear()
fill(0,0,0,20,8,20,'air');fill(0,0,0,20,0,20,'polished_andesite')
for x in (0,20):fill(x,1,0,x,2,20,'sandstone');fill(x,3,0,x,3,20,'iron_bars')
for z in (0,20):
    fill(0,1,z,20,2,z,'sandstone');fill(9,1,z,11,2,z,'air')
fill(9,0,0,11,0,20,'gray_concrete')
for x1,x2 in ((2,8),(13,18)):
    fill(x1,1,12,x2,5,18,'green_terracotta');fill(x1+1,1,13,x2-1,4,17,'air')
    door=(x1+x2)//2;fill(door,1,12,door+1,3,12,'air');fill(x1,5,12,x2,5,18,'green_concrete')
    put(x1+1,4,15,'sea_lantern');put(x1+1,3,12,'glass_pane',{'north':'false','south':'false','east':'true','west':'true','waterlogged':'false'})
bed(4,1,16);bed(6,1,15);chest(6,1,17,'blackwatch_technology');chest(16,1,17,'blackwatch_supplies');put(17,1,14,'crafting_table')
sign(3,2,11,'army_camp');sign(7,2,11,'military_technology');sign(14,2,11,'military_supplies')
for x,z in ((2,2),(4,2),(2,5),(4,5)):fill(x,1,z,x,6,z,'oak_log')
fill(2,6,2,4,6,5,'green_concrete');fill(2,7,2,4,7,2,'iron_bars');fill(2,7,5,4,7,5,'iron_bars')
for y in range(1,7):put(3,y,2,'oak_log');put(3,y,3,'ladder',{'facing':'south','waterlogged':'false'})
for x,z in ((5,7),(15,7),(4,10),(16,10)):fill(x,1,z,x+1,1,z+1,'sandstone')
for x in (8,12):fill(x,1,0,x,4,0,'green_concrete');put(x,4,1,'sea_lantern')
entities=[guard(x,y,z,kind,'military') for x,y,z,kind in ((6,1,5,'soldier'),(14,1,5,'soldier'),(10,1,10,'soldier'),(6,1,16,'soldier'),(14,1,16,'heavy'),(17,1,8,'soldier'),(3,7,4,'soldier'),(10,1,17,'soldier'))]
write_template('military_camp',21,entities)

# Smaller army road checkpoint, independent from the Blackwatch pool.
blocks.clear();fill(0,0,0,16,8,16,'air');fill(0,0,0,16,0,16,'polished_andesite')
for x in (0,16):fill(x,1,0,x,2,16,'sandstone')
for z in (0,16):fill(0,1,z,16,2,z,'iron_bars');fill(6,1,z,10,2,z,'air')
fill(6,0,0,10,0,16,'gray_concrete')
for x1,x2 in ((1,5),(11,15)):
    fill(x1,1,10,x2,4,15,'green_terracotta');fill(x1+1,1,11,x2-1,3,14,'air');fill(x1+2,1,10,x1+2,2,10,'air');fill(x1,4,10,x2,4,15,'green_concrete');put(x1+1,3,13,'sea_lantern')
chest(2,1,13,'blackwatch_technology');chest(12,1,13,'blackwatch_supplies');sign(2,2,9,'army_checkpoint');sign(12,2,9,'military_supplies')
for x,z in ((2,4),(12,4),(4,7),(11,7)):fill(x,1,z,x+1,1,z+1,'sandstone')
entities=[guard(3,1,6,'soldier','military'),guard(13,1,6,'soldier','military'),guard(8,1,12,'heavy','military'),guard(8,1,5,'soldier','military')]
write_template('military_checkpoint',17,entities)
