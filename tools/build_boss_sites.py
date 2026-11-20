"""Build the two original survival encounter sites for this loader (standard library only)."""
import gzip
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/data/blacklight"
NEO = (ROOT / "advancement").is_dir()


def string(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def nbt(kind, value):
    if kind == 1:
        return struct.pack(">b", value)
    if kind == 3:
        return struct.pack(">i", value)
    if kind == 8:
        return string(value)
    if kind == 9:
        child, items = value
        return bytes([child]) + struct.pack(">i", len(items)) + b"".join(nbt(child, item) for item in items)
    return b"".join(bytes([child]) + string(key) + nbt(child, item)
                    for key, (child, item) in value.items()) + b"\0"


def site_sign(site):
    messages = [json.dumps({"translate": f"survival.blacklight.sign.{site}.{i}"}, separators=(",", ":")) for i in range(4)]
    return {
        "id": (8, "minecraft:sign"), "is_waxed": (1, 1),
        "front_text": (10, {"messages": (9, (8, messages)), "color": (8, "black"), "has_glowing_text": (1, 0)})}



def quarantine_post(put, fill):
    # Records shack at the west edge, a peeled gate and a track pointing out of the pen.
    fill(0, 0, 0, 16, 0, 21, "coarse_dirt")
    fill(0, 1, 0, 16, 6, 21, "air")
    fill(1, 0, 1, 7, 0, 8, "stone_bricks")
    for x in (1, 7):
        fill(x, 1, 1, x, 3, 8, "light_gray_concrete")
    for z in (1, 8):
        fill(1, 1, z, 7, 3, z, "light_gray_concrete")
    fill(3, 1, 8, 4, 2, 8, "air")
    fill(7, 2, 3, 7, 2, 6, "glass_pane")
    fill(0, 4, 0, 8, 4, 9, "weathered_cut_copper_slab")
    put(2, 2, 3, "lantern")
    put(4, 1, 3, "barrel", {"facing":"up"}, {
        "id":(8, "minecraft:barrel"), "LootTable":(8, "blacklight:chests/quarantine_records")})
    fill(9, 1, 1, 15, 2, 1, "iron_bars")
    fill(15, 1, 1, 15, 2, 9, "iron_bars")
    fill(8, 1, 9, 10, 2, 9, "iron_bars")
    put(12, 1, 9, "blacklight:field_post", {"facing":"south","searched":"false"})
    put(14, 1, 10, "iron_trapdoor", {"open":"true","facing":"east"})
    for i in range(10, 21):
        put(12 + (i//3)%2, 0, i, "rooted_dirt" if i%3 else "red_terracotta")
    put(11, 1, 8, "birch_sign", {"rotation":"8"}, site_sign("quarantine_post"))


def dispatch_approach(put, fill):
    # The delivery street bends behind a long workshop. Its roof overlooks the entire trip.
    fill(0, 0, 33, 32, 0, 76, "smooth_stone")
    fill(0, 1, 33, 32, 10, 76, "air")
    for z in range(33, 77):
        centre = 21 if z >= 53 else max(11, 21-(53-z))
        for x in range(centre-5, centre+6):
            put(x, 0, z, "gray_concrete")
        if z%6 in (0, 1):
            put(centre, 0, z, "yellow_terracotta")
        if z%7 == 3:
            put(centre-3, 0, z, "black_terracotta")
    fill(1, 1, 37, 6, 7, 60, "stone_bricks")
    fill(2, 1, 38, 5, 6, 59, "air")
    fill(0, 8, 36, 7, 8, 61, "smooth_stone_slab")
    for z in range(39, 60, 4):
        fill(6, 3, z, 6, 5, z+1, "gray_stained_glass")
    for y in range(1, 10):
        put(3, y, 61, "ladder", {"facing":"south"})
        if y < 9: put(3, y, 60, "stone_bricks")
    fill(6, 1, 64, 13, 4, 72, "polished_andesite")
    fill(7, 1, 65, 12, 3, 71, "air")
    fill(13, 1, 66, 13, 2, 67, "air")
    fill(13, 2, 69, 13, 3, 70, "glass_pane")
    fill(5, 5, 63, 14, 5, 73, "dark_prismarine_slab")
    put(8, 1, 68, "blacklight:field_post", {"facing":"east","searched":"false"})
    put(8, 3, 67, "lantern", {"hanging":"true"})
    for z in (38, 53, 72):
        for x in (27, 28, 29):
            put(x, 1, z, "yellow_concrete" if x%2 else "black_concrete")
        put(28, 2, z, "lantern")
    put(15, 1, 69, "birch_sign", {"rotation":"0"}, site_sign("dispatch"))
    # A powered iron door opens a service cut through the western frontage.
    fill(1, 1, 25, 3, 3, 28, "air")
    fill(1, 1, 28, 3, 3, 28, "stone_bricks")
    for y, half in ((1,"lower"),(2,"upper")):
        put(2, y, 28, "iron_door", {"facing":"north","half":half,"open":"false","hinge":"left"})
    put(2, 3, 28, "sea_lantern")


def city_plaza(put, fill):
    """South entrance and pump face the patient across the closed intersection."""
    for x in range(33):
        for z in range(33):
            pavement = "gray_concrete" if 7 <= x <= 25 else "smooth_stone"
            if x in (6, 26):
                pavement = "polished_andesite"
            if z in (5, 24) and 7 <= x <= 25 and x % 3 != 0:
                pavement = "light_gray_concrete"
            if x in (15, 17) and z in (1, 2, 29, 30, 31, 32):
                pavement = "yellow_terracotta"
            put(x, 0, z, pavement)
    # Two narrow frontages at the edge of the intersection. Shop bays double as lateral escape routes.
    for east in (False, True):
        front = 28 if east else 4
        rear = 32 if east else 0
        lo, hi = sorted((front, rear))
        wall = "bricks" if east else "stone_bricks"
        trim = "polished_blackstone_bricks" if east else "polished_andesite"
        for a,b,height in ((1,12,12 if east else 15), (15,28,15 if east else 11)):
            fill(lo,1,a,hi,height,b,wall)
            fill(lo+1,1,a+1,hi-1,height-1,b-1,"air")
            for y in range(4,height,4):
                fill(lo,y,a,hi,y,b,trim)
            fill(lo,height,a,hi,height,b,trim)
            for z in range(a+1,b-1,4):
                # Upper windows in recessed pairs, with lintels and deep sills.
                for y in range(5,height-1,4):
                    for w in (z,z+1):
                        put(front,y,w,"gray_stained_glass_pane")
                        put(front,y+1,w,"gray_stained_glass_pane")
                        put(front+(1 if not east else -1),y-1,w,"polished_andesite_slab")
            for z in range(a+1,b-1,5):
                fill(front,1,z,front,3,z+2,"air")
                fill(front,3,z,front,3,z+2,"iron_bars")
                for w in (z,z+1,z+2):
                    put(front+(1 if not east else -1),4,w,"dark_prismarine_slab" if not east else "brick_slab")
                put(rear+(1 if not east else -1),2,z+1,"ochre_froglight")
            put(lo+2,height+1,a+2,"polished_blackstone")
            put(lo+2,height+2,a+2,"iron_trapdoor")
        # The covered service passage opens into the square rather than onto a blank wall.
    fill(1,1,25,3,3,28,"air")
    fill(2,1,26,4,3,27,"air")
    for z in (22,27):
        put(2,4,z,"ochre_froglight")
    # Across the far street a breached cordon leaves both sidewalks open.
    for x in (7,13,21,24):
        for dx in range(2):
            put(x+dx,1,3,"light_gray_concrete")
            put(x+dx,2,3,"polished_andesite_slab")
        put(x,2,3,"yellow_concrete")
    for x,z in ((7,9),(25,18)):
        fill(x,1,z,x,4,z,"polished_blackstone_wall")
        tip = 1 if x<16 else -1
        put(x,5,z,"smooth_stone_slab")
        put(x+tip,5,z,"sea_lantern")
        put(x+tip,6,z,"polished_blackstone_slab")
    # Shallow subsidence, drainage grilles and two branching seams at the emergence point.
    for x,z in ((14,16),(15,17),(16,17),(17,18),(18,19),(18,20),(19,20),(14,15),(13,14),(12,14)):
        put(x,0,z,"blacklight:hive_vein")
    for x,z in ((13,17),(16,16),(17,17),(14,14),(12,15),(18,18),(19,21)):
        put(x,0,z,"cracked_deepslate_bricks")
    for z in (8,15,22,30):
        put(7,0,z,"polished_blackstone")
        put(25,0,z,"polished_blackstone")
    # Physical volumes stay inside the parked truck's hull; the hose and mirror cast no invisible walls.
    for x in range(11,17):
        put(x,1,27,"polished_blackstone_slab",{"type":"top"})
    for x in range(11,13):
        put(x,2,27,"black_terracotta")
    for x in range(13,16):
        put(x,2,27,"light_gray_concrete")
    # Freestanding indicator behind and to the side of the control stand.
    put(21,1,29,"redstone_block")
    put(21,2,29,"redstone_lamp",{"lit":"true"})
    put(21,2,30,"birch_wall_sign",{"facing":"south"},site_sign("mother_plaza"))


def build(site):
    plaza = site == "mother_plaza"
    cells = {}
    def put(x, y, z, block, props=None, entity=None):
        name = block if ":" in block else "minecraft:" + block
        cells[x, y, z] = (name, tuple(sorted((props or {}).items())), entity)

    def fill(x1, y1, z1, x2, y2, z2, block):
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1):
                    put(x, y, z, block)

    if site == "quarantine_post":
        quarantine_post(put, fill)
        size = [17, 7, 22]
    else:
        # Explicit air keeps the marked pad and the two escape lanes accessible on uneven terrain.
        fill(0, 0, 0, 32, 0, 32, "stone_bricks")
        fill(0, 1, 0, 32, 7, 32, "air")
        fill(9, 8, 9, 23, 18, 23, "air")
        for x in range(33):
            for z in range(33):
                paving = "smooth_stone" if plaza else "gray_concrete"
                if plaza and (x % 8 == 0 or z % 8 == 0):
                    paving = "polished_andesite"
                if not plaza and x in (11, 21) and z % 6 < 3:
                    paving = "yellow_concrete"
                put(x, 0, z, paving)

        if plaza:
            city_plaza(put, fill)
        else:
            # Overturned containment truck, stripped axle, broken barricades and drag marks.
            fill(2, 1, 5, 6, 4, 14, "green_terracotta")
            fill(2, 2, 6, 2, 3, 12, "iron_trapdoor")
            fill(3, 2, 13, 5, 3, 13, "air")
            fill(3, 1, 14, 5, 3, 14, "air")
            for z in (6, 12):
                fill(6, 2, z, 6, 3, z, "black_concrete")
                put(7, 2, z, "polished_blackstone_button", {"face": "wall", "facing": "east"})
            fill(2, 1, 2, 5, 3, 4, "gray_concrete")
            fill(2, 3, 2, 4, 3, 2, "light_blue_stained_glass")
            for z in (4, 21, 25):
                fill(24, 1, z, 28, 1, z, "polished_andesite")
                put(24, 2, z, "yellow_concrete")
                put(28, 2, z, "yellow_concrete")
            for x, z in ((6, 15), (7, 16), (9, 16), (10, 17), (13, 17), (15, 17)):
                put(x, 0, z, "cracked_deepslate_bricks")

        put(16, 1, 28, "blacklight:encounter_console", {"facing": "south", "mother": str(plaza).lower()},
            {"id": (8,"blacklight:pump_station"),"FieldRig": (1,1)} if plaza else None)
        if not plaza:
            put(18, 1, 28, "yellow_concrete")
            put(18, 2, 28, "redstone_lamp", {"lit": "false"})
            put(18, 3, 28, "redstone_block")
            put(18,2,29,"birch_wall_sign",{"facing":"south"},site_sign(site))

        if not plaza:
            for z in (4, 21, 25):
                fill(24, 1, z, 28, 4, z, "blacklight:convoy_rail")
            for x in range(12, 21):
                fill(x, 1, 21, x, 4, 21, "blacklight:convoy_rail")
            for x in (10,22):
                fill(x, 1, 11, x, 5, 20, "green_terracotta")
                fill(x, 5, 11, x, 5, 20, "cut_copper")
                for z in (12,18): put(x, 1, z, "black_concrete")
            for z in range(21,32):
                for x in (12,20): put(x+(z-21)//5,0,z,"black_terracotta")
            for x,z in ((8,21),(9,24),(21,30)): put(x,1,z,"barrel",{"facing":"east"})
        if plaza:
            dispatch_approach(put, fill)
        size = [33, 19, 77 if plaza else 33]

    palette, lookup, blocks = [], {}, []
    for pos, (name, props, entity) in sorted(cells.items()):
        key = name, props
        if key not in lookup:
            lookup[key] = len(palette)
            state = {"Name": (8, name)}
            if props:
                state["Properties"] = 10, {k: (8, v) for k, v in props}
            palette.append(state)
        block = {"pos": (9, (3, pos)), "state": (3, lookup[key])}
        if entity:
            block["nbt"] = 10, entity
        blocks.append(block)
    template = {"DataVersion": (3, 3955 if NEO else 3465), "size": (9, (3, size)),
                "palette": (9, (10, palette)), "blocks": (9, (10, blocks)), "entities": (9, (10, []))}
    dest = ROOT / ("structure" if NEO else "structures") / (site + ".nbt")
    dest.write_bytes(gzip.compress(b"\x0a\0\0" + nbt(10, template), mtime=0))


if __name__ == "__main__":
    for site_name in ("lost_convoy", "mother_plaza", "quarantine_post"):
        build(site_name)
