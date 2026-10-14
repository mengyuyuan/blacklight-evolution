"""Author the Blackwatch outpost. Python 3 standard library only.

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
    entities.append({'pos':(9,(6,[x+.5,float(y),z+.5])),'blockPos':(9,(3,[x,y,z])),
                     'nbt':(10,{'id':(8,'blacklight:blackwatch_'+kind),'PersistenceRequired':(1,1),'Rotation':(9,(5,[180.,0.]))})})
palette=[];lookup={};data=[]
for pos,(name,props,nbt) in sorted(blocks.items()):
    key=(name,tuple(sorted(props.items())))
    if key not in lookup:
        lookup[key]=len(palette);state={'Name':(8,name)}
        if props:state['Properties']=(10,{k:(8,v) for k,v in props.items()})
        palette.append(state)
    entry={'pos':(9,(3,list(pos))),'state':(3,lookup[key])}
    if nbt:entry['nbt']=(10,nbt)
    data.append(entry)
forge='minecraft_version=1.20.1' in (ROOT.parents[2]/'gradle.properties').read_text(encoding='utf8')
template={'DataVersion':(3,3465 if forge else 3955),'size':(9,(3,[25,9,25])), 'palette':(9,(10,palette)),'blocks':(9,(10,data)),'entities':(9,(10,entities))}
destination=ROOT/('data/blacklight/structures' if forge else 'data/blacklight/structure')/'blackwatch_outpost.nbt'
destination.parent.mkdir(parents=True,exist_ok=True)
destination.write_bytes(gzip.compress(b'\x0a\0\0'+payload(10,template),mtime=0))
print(f'Wrote {destination.name}: {len(data)} blocks, {len(entities)} soldiers')
