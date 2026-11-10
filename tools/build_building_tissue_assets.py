"""Connected surface tissue, authored in block-model JSON; existing pixel textures are retained."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = ROOT / "assets/blacklight"
TEXTURES = {"flesh": "blacklight:block/hive_flesh", "shell": "blacklight:block/hive_carapace",
            "vein": "blacklight:block/hive_vein", "particle": "blacklight:block/hive_flesh"}

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def box(lo, hi, texture, omit=()):
    return {"from": lo, "to": hi, "faces": {face: {"texture": "#" + texture}
            for face in ("up", "down", "north", "south", "east", "west") if face not in omit}}

def surface_model(age, variant):
    """Closed volume with shared internal faces removed, rather than intersecting paper-thin plates."""
    scale = (.08, .24, .55, 1)[age]
    base = max(.75, (1.2, 4.8, 10.5)[variant] * scale)
    margin = (4, 2, 0, 0)[age]
    if variant == 0:
        columns = [(margin, 16-margin, 0, "flesh")]
    elif variant == 1:
        columns = [(margin, 6, max(.35, .65*scale), "vein"), (6, 16-margin, 0, "flesh")]
    else:
        columns = [(margin, 6, max(.35, 1.2*scale), "shell"), (6, 10, 0, "flesh"),
                   (10, 16-margin, max(.35, .7*scale), "vein")]
    parts = []
    for index, (left, right, rise, material) in enumerate(columns):
        omitted = []
        if index > 0:
            omitted.append("west")
        if index < len(columns)-1:
            omitted.append("east")
        if rise:
            omitted.append("up")
        parts.append(box([left, 0, margin], [right, base, 16-margin], "flesh", omitted))
        if rise:
            # Meet the base exactly. Its covered top and this rib's underside are internal.
            parts.append(box([left, base, margin], [right, base+rise, 16-margin], material, ("down",)))
    return {"textures": TEXTURES, "elements": parts}


# The server still owns spread, growth and support orientation. Geometry stays inside its cell.
for age in range(4):
    for variant in range(3):
        write(ASSETS/f"models/block/tissue_attachment_{age}_{variant}.json", surface_model(age, variant))

# Resource-only repairs should not regenerate recipes, translations or the incubation core.
if "--surface-only" in sys.argv:
    raise SystemExit(0)

states = {}
for age in range(4):
    # Broad anchored base, overlapping organic rings around a recessed incubation aperture.
    # Topology stays restrained: 8 elements rather than a field of tiny spikes.
    d = (4,6,9,12)[age]
    parts = [box([1,1,12],[15,15,16],"flesh"),
             box([2,2,16-d],[14,14,15],"flesh"),
             box([3,3,15-d],[13,13,16-d],"vein"),
             box([2,2,14-d],[5,14,17-d],"shell"),
             box([11,2,14-d],[14,14,17-d],"shell"),
             box([5,2,14-d],[11,5,17-d],"shell"),
             box([5,11,14-d],[11,14,17-d],"shell"),
             box([5,5,16-d],[11,11,16-d+.15],"flesh")]
    write(ASSETS/f"models/block/building_core_{age}.json",{"textures":TEXTURES,"elements":parts})
    for direction, rot in (("north",0),("east",90),("south",180),("west",270)):
        states[f"age={age},facing={direction}"]={"model":f"blacklight:block/building_core_{age}","y":rot}
write(ASSETS/"blockstates/building_infection_core.json",{"variants":states})
write(ASSETS/"models/item/building_infection_core.json",{"parent":"blacklight:block/building_core_3"})
write(ROOT/"data/blacklight/loot_table/blocks/building_infection_core.json",{"type":"minecraft:block","pools":[]})
write(ROOT/"data/blacklight/recipe/building_infection_core.json",{
    "type":"minecraft:crafting_shapeless",
    "ingredients":[{"item":"blacklight:living_hive_tissue"},{"item":"minecraft:rotten_flesh"},
                   {"item":"minecraft:rotten_flesh"},{"item":"minecraft:rotten_flesh"}],
    "result":{"id":"blacklight:building_infection_core","count":1}})
for lang, name in (("zh_cn","黑光建筑感染核心"),("en_us","Blacklight Building Infection Core")):
    path=ASSETS/f"lang/{lang}.json"
    data=json.loads(path.read_text(encoding="utf-8"))
    data["block.blacklight.building_infection_core"]=name
    data["building.blacklight.site_unavailable"]=("此处已有感染领地、已被清除或达到服务器上限，无法建立新核心。" if lang=="zh_cn"
        else "This site is occupied, cleared, protected, or the server colony limit was reached.")
    data["building.blacklight.status"]=("感染阶段 %s/4 · 生长时间 %s%% · 已附着 %s 格" if lang=="zh_cn"
        else "Infection stage %s/4 · Active growth %s%% · %s attached blocks")
    write(path,data)

