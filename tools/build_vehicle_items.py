"""Generate original 32px inventory artwork, vehicle names and Minecraft-only recipes."""
from pathlib import Path
import argparse
import io
import json

from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]

def json_bytes(value):
    return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')


def inventory_icon(item, palette):
    image = Image.new('RGBA', (32, 32))
    draw = ImageDraw.Draw(image)
    outline = '#142022'
    armor, light, dark = (palette[key] for key in ('armor', 'armor_light', 'armor_dark'))
    if item.startswith('blackwatch_'):
        armor, light, dark = '#536662', '#82938D', '#263E40'
    metal, rubber, glass = (palette[key] for key in ('metal', 'rubber', 'glass'))
    bright, brass = '#DCE3CB', '#C19754'
    def polygon(points, color):
        draw.polygon(points, fill=color, outline=outline)
    def box(bounds, color):
        draw.rectangle(bounds, fill=color, outline=outline)
    def badge(x, y):
        if item.startswith('blackwatch_'):
            draw.line([(x, y+3), (x, y), (x+3, y)], fill=bright)
            draw.line([(x+1, y+3), (x+3, y+1)], fill='#BE6451')
        else:
            for offset in (0, 2):
                draw.line((x, y+offset, x+3, y+offset), fill=bright)
    if item.endswith('_chassis'):
        kind = item.split('_')[1]
        if kind == 'truck':
            box((2, 19, 28, 23), metal)
            polygon([(2, 9), (6, 6), (18, 7), (20, 11), (19, 20), (2, 21)], palette['canvas'])
            draw.polygon([(3, 10), (7, 8), (11, 9), (9, 14), (15, 17), (13, 20), (4, 20)], fill=dark)
            draw.line((3, 10, 17, 10), fill=light)
            for x in (5, 11, 17):
                draw.line((x, 11, x, 20), fill=light)
            polygon([(20, 9), (25, 8), (29, 14), (29, 21), (20, 22)], armor)
            polygon([(21, 10), (24, 10), (27, 14), (21, 14)], glass)
            draw.line((22, 10, 25, 13), fill='#7FADB0')
            box((27, 16, 30, 21), dark)
            draw.point((28, 17), fill='#FAE8B4')
            draw.line((21, 17, 24, 17), fill=bright)
            for x in (6, 16, 25):
                draw.ellipse((x-3, 21, x+3, 27), fill=rubber, outline=outline)
                draw.ellipse((x-1, 23, x+1, 25), fill=light)
                draw.point((x, 23), fill=bright)
            badge(7, 14)
        else:
            polygon([(3, 20), (25, 19), (29, 22), (27, 27), (4, 27), (1, 24)], rubber)
            for x in (5, 9, 13, 17, 21, 25):
                draw.ellipse((x-1, 22, x+2, 25), fill=metal, outline=outline)
                draw.point((x, 23), fill=light)
            draw.line((4, 26, 26, 26), fill='#56615A')
            if kind == 'apc':
                polygon([(3, 16), (8, 13), (22, 13), (28, 18), (27, 22), (3, 23)], armor)
                draw.polygon([(4, 17), (9, 15), (13, 16), (12, 19), (19, 20), (16, 22), (4, 22)], fill=dark)
                polygon([(12, 12), (15, 10), (20, 11), (21, 14), (13, 15)], light)
                box((20, 11, 26, 12), metal)
                draw.line((21, 11, 25, 11), fill=light)
                box((12, 6, 18, 10), dark)
                for x in (13, 17):
                    draw.line((x, 7, x, 9), fill=brass)
                for x in (15, 21):
                    draw.line((x, 17, x+2, 17), fill='#7FADB0')
                draw.line((24, 17, 27, 19), fill=bright)
                draw.line((5, 14, 9, 14), fill=light)
                badge(7, 18)
            else:
                polygon([(2, 18), (8, 15), (23, 16), (29, 20), (26, 23), (3, 24)], armor)
                draw.polygon([(3, 19), (10, 17), (13, 18), (10, 21), (18, 21), (16, 23), (4, 23)], fill=dark)
                polygon([(8, 12), (13, 9), (22, 10), (25, 15), (19, 18), (8, 17)], light)
                draw.polygon([(9, 13), (13, 11), (16, 12), (15, 16), (9, 16)], fill=armor)
                box((21, 11, 30, 13), metal)
                draw.line((22, 11, 29, 11), fill=bright)
                box((12, 6, 18, 8), dark)
                draw.line((17, 6, 23, 6), fill=metal, width=2)
                box((27, 21, 30, 24), metal)
                draw.line((28, 22, 29, 22), fill=brass)
                badge(10, 13)
        if item.startswith('blackwatch_'):
            # A pale broken flank stripe accompanies the angular shield mark.
            draw.line((3, 18, 5, 16), fill=bright, width=2)
            draw.point((3, 19), fill='#BE6451')
    elif item == 'military_fuel_can':
        polygon([(7, 10), (11, 7), (22, 7), (26, 11), (26, 27), (22, 29), (7, 28)], dark)
        box((7, 10, 22, 28), armor)
        box((10, 3, 21, 9), metal)
        draw.rectangle((13, 5, 18, 7), fill=(0, 0, 0, 0))
        polygon([(22, 6), (26, 5), (28, 8), (24, 10)], brass)
        draw.line((9, 13, 20, 25), fill=light, width=2)
        draw.line((20, 13, 9, 25), fill=light, width=2)
        box((12, 16, 18, 22), dark)
        draw.polygon([(15, 16), (17, 20), (15, 22), (13, 20)], fill='#E8BA59')
        draw.line((8, 11, 8, 26), fill=light)
    elif item == 'tank_he_shell':
        polygon([(10, 14), (11, 8), (16, 2), (21, 8), (22, 14)], '#9B4338')
        draw.line((15, 5, 12, 10), fill='#EA9871', width=2)
        box((10, 13, 22, 28), '#A5743E')
        draw.rectangle((12, 14, 14, 26), fill='#DCB778')
        draw.rectangle((20, 14, 21, 26), fill='#70482B')
        box((9, 26, 23, 29), brass)
        draw.line((11, 16, 21, 16), fill='#E5B765', width=2)
        draw.line((11, 20, 21, 20), fill='#A54436', width=2)
    elif item == 'tank_ap_shell':
        polygon([(4, 25), (9, 29), (24, 10), (27, 2), (20, 6)], '#97A7A5')
        draw.line((8, 24, 23, 7), fill='#DFE6DD', width=2)
        polygon([(4, 25), (9, 29), (16, 21), (11, 17)], '#B49257')
        draw.line((7, 24, 12, 18), fill='#E4C98B', width=2)
        polygon([(13, 17), (17, 20), (20, 16), (16, 13)], '#455E70')
        draw.line((16, 14, 19, 16), fill='#D7DEC8')
        draw.line((4, 26, 8, 29), fill='#E2B76A', width=2)
    elif item == 'autocannon_round':
        draw.line((5, 22, 26, 24), fill=metal, width=5)
        for x, y in ((5, 10), (13, 7), (21, 10)):
            polygon([(x, y+5), (x+2, y), (x+4, y+5), (x+5, y+15), (x, y+15)], '#BD8A4B')
            draw.polygon([(x, y+5), (x+2, y), (x+4, y+5)], fill='#739276')
            draw.line((x+1, y+6, x+1, y+13), fill='#E8C992')
            box((x-1, y+11, x+5, y+13), '#506065')
            draw.line((x, y+15, x+4, y+15), fill=brass)
    else:
        polygon([(4, 10), (23, 8), (28, 11), (27, 18), (4, 18)], '#536366')
        draw.line((6, 11, 23, 10), fill='#ADBBB4')
        polygon([(6, 18), (10, 14), (13, 17), (24, 5), (28, 6), (28, 10), (25, 12), (15, 23)], '#ACB8AC')
        draw.line((12, 19, 25, 7), fill='#E3E5CD')
        polygon([(3, 19), (24, 18), (28, 21), (27, 28), (4, 28)], '#525E61')
        box((4, 21, 24, 28), '#75837D')
        box((12, 20, 17, 23), brass)
        draw.line((6, 26, 22, 26), fill='#C4CDBF')
    data = io.BytesIO()
    image.save(data, format='PNG', optimize=False)
    return data.getvalue()


def generate(root,forge,check,recipes_only=False):
    geometry=json.loads((root/'src/main/resources/assets/blacklight/military/vehicle-models.json').read_text(encoding='utf8'))
    results={};lang={'zh_cn':{},'en_us':{}}
    def output(path,value):results[path]=json_bytes(value)
    def shaped(pattern,keys,result,count=1):
        return {'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v} for k,v in keys.items()},'result':{'item' if forge else 'id':'blacklight:'+result,'count':count}}
    def shapeless(items,result,count=1):
        return {'type':'minecraft:crafting_shapeless','ingredients':[{'item':v} for v in items],'result':{'item' if forge else 'id':'blacklight:'+result,'count':count}}
    recipes={
        'military_truck_chassis':shaped(['ICI','IFI','ICI'],{'I':'minecraft:iron_block','C':'minecraft:copper_block','F':'minecraft:furnace'},'military_truck_chassis'),
        'military_apc_chassis':shaped(['IRI','ITI','IOI'],{'I':'minecraft:iron_block','R':'minecraft:redstone_block','T':'blacklight:military_truck_chassis','O':'minecraft:observer'},'military_apc_chassis'),
        'military_tank_chassis':shaped(['IRI','IAI','IDI'],{'I':'minecraft:iron_block','R':'minecraft:redstone_block','A':'blacklight:military_apc_chassis','D':'minecraft:dispenser'},'military_tank_chassis'),
        'rifle_round':shapeless(['minecraft:iron_ingot','minecraft:gunpowder'],'rifle_round',16),
        'machine_gun_round':shapeless(['minecraft:iron_ingot','minecraft:copper_ingot','minecraft:gunpowder'],'machine_gun_round',16),
        'grenade_round':shapeless(['minecraft:iron_ingot']*2+['minecraft:gunpowder'],'grenade_round',2),
        'rocket_round':shapeless(['minecraft:iron_ingot']*2+['minecraft:gunpowder','minecraft:paper'],'rocket_round',2),
        'missile_round':shapeless(['minecraft:iron_ingot']*2+['minecraft:gunpowder','minecraft:paper','minecraft:redstone'],'missile_round',2),
        'autocannon_round':shapeless(['minecraft:iron_ingot']*2+['minecraft:copper_ingot','minecraft:gunpowder'],'autocannon_round',8),
        'tank_he_shell':shapeless(['minecraft:iron_ingot']*2+['minecraft:gunpowder']*2+['minecraft:copper_ingot'],'tank_he_shell'),
        'tank_ap_shell':shapeless(['minecraft:iron_ingot']*3+['minecraft:gunpowder','minecraft:copper_ingot'],'tank_ap_shell'),
        'military_fuel_can':shapeless(['minecraft:coal']*4+['minecraft:iron_ingot']*2+['minecraft:glass_bottle'],'military_fuel_can'),
        'vehicle_repair_parts':shapeless(['minecraft:iron_ingot']*2+['minecraft:redstone'],'vehicle_repair_parts'),
    }
    def sprite(item, palette):
        if recipes_only:return
        output('assets/blacklight/models/item/'+item+'.json',
               {'parent':'minecraft:item/generated','textures':{'layer0':'blacklight:item/'+item}})
        results['assets/blacklight/textures/item/'+item+'.png'] = inventory_icon(item, palette)
    for faction in ('military','blackwatch'):
        for chassis,label,en in [('truck','运输卡车','Transport Truck'),('apc','装甲战斗车','Armored Fighting Vehicle'),('tank','主战坦克','Main Battle Tank')]:
            item=f'{faction}_{chassis}_chassis'
            sprite(item, geometry['palettes'][faction])
            lang['zh_cn']['item.blacklight.'+item]=('军方' if faction=='military' else '黑色守望')+label
            lang['en_us']['item.blacklight.'+item]=('Military' if faction=='military' else 'Blackwatch')+' '+en
            if faction=='blackwatch':recipes[item]=shapeless(['blacklight:military_'+chassis+'_chassis','minecraft:black_dye'],item)
    for item,label,en in [('autocannon_round','自动炮弹','Autocannon Round'),('tank_he_shell','坦克高爆弹','Tank HE Shell'),('tank_ap_shell','坦克穿甲弹','Tank AP Shell'),('military_fuel_can','军用燃料罐','Military Fuel Can'),('vehicle_repair_parts','载具维修零件','Vehicle Repair Parts')]:
        sprite(item, geometry['palettes']['military'])
        lang['zh_cn']['item.blacklight.'+item]=label;lang['en_us']['item.blacklight.'+item]=en
    for name,recipe in recipes.items():output('data/blacklight/'+('recipes' if forge else 'recipe')+'/'+name+'.json',recipe)
    for language,entries in lang.items():
        if recipes_only:continue
        entries.update({
            'tooltip.blacklight.vehicle.place': '放置完整车辆，初始无弹药，驾驶无需燃油' if language=='zh_cn' else 'Places a complete vehicle, initially without ammunition; driving requires no fuel',
            'tooltip.blacklight.vehicle.supply': '停稳下车，持对应弹药/维修件右键车辆' if language=='zh_cn' else 'Stop and dismount, then right-click the vehicle with matching ammunition or repair parts',
            'tooltip.blacklight.military_fuel_can': '保留物品，本轮未启用；驾驶无需燃油' if language=='zh_cn' else 'Reserved item, currently inactive; vehicles do not require fuel',
            'tooltip.blacklight.autocannon_round': '停稳下车，持此弹药右键装甲战斗车补给自动炮弹' if language=='zh_cn' else 'Stop and dismount, then right-click an armored vehicle with this item to load autocannon ammunition',
            'tooltip.blacklight.tank_he_shell': '停稳下车，持此弹药右键坦克补给高爆弹' if language=='zh_cn' else 'Stop and dismount, then right-click a tank with this item to load HE shells',
            'tooltip.blacklight.tank_ap_shell': '停稳下车，持此弹药右键坦克补给穿甲弹' if language=='zh_cn' else 'Stop and dismount, then right-click a tank with this item to load AP shells',
            'tooltip.blacklight.vehicle_repair_parts': '停稳下车，持维修件右键车辆维修' if language=='zh_cn' else 'Stop and dismount, then right-click a vehicle with repair parts to repair it',
            'hud.blacklight.vehicle_status': '耐久 %s/%s · %s' if language=='zh_cn' else 'HP %s/%s | %s',
            'hud.blacklight.vehicle_primary': '主武器：%s' if language=='zh_cn' else 'Primary: %s',
            'hud.blacklight.vehicle_secondary': '副武器：%s' if language=='zh_cn' else 'Secondary: %s',
            'hud.blacklight.vehicle_supply_hint': '补给：停稳下车，持对应弹药/维修件右键车体' if language=='zh_cn' else 'Supply: stop, dismount, right-click the hull with matching ammunition or repair parts',
        })
        entries['itemGroup.blacklight.military_vehicles']='军用载具与后勤' if language=='zh_cn' else 'Military Vehicles and Logistics'
        path=root/'src/main/resources/assets/blacklight/lang'/f'{language}.json'
        old=json.loads(path.read_text(encoding='utf-8'))
        if check:assert all(old.get(k)==v for k,v in entries.items()),'Missing vehicle translations'
        else:old.update(entries);path.write_bytes(json_bytes(old))
    for name,data in results.items():
        path=root/'src/main/resources'/name
        if check:assert path.is_file() and path.read_bytes()==data,'Stale generated vehicle item/recipe: '+name
        elif not path.is_file() or path.read_bytes()!=data:
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    active=sorted(name for name in recipes if name!='military_fuel_can')
    preserved=['military_fuel_can']
    models={Path(name).stem for name in results if '/models/item/' in name}
    textures={Path(name).stem for name in results if '/textures/item/' in name}
    print(json.dumps({'root':str(root),'check':check,'models':len(models),'recipes':len(recipes),'outputFiles':len(results),
        'active':{'items':active,'models':len(models-set(preserved)),'textures':len(textures-set(preserved)),'recipes':len(active)},
        'preserved':{'items':preserved,'models':len(models&set(preserved)),'textures':len(textures&set(preserved)),'recipes':1,'recipePolicy':'exclude_from_game_build'}},ensure_ascii=False))


def preview(root):
    rows = [[f'{faction}_{kind}_chassis' for kind in ('truck', 'apc', 'tank')]
            for faction in ('military', 'blackwatch')]
    rows.append(['military_fuel_can', 'tank_he_shell', 'tank_ap_shell', 'autocannon_round', 'vehicle_repair_parts'])
    sheet = Image.new('RGB', (1100, 610), '#252D2F')
    draw = ImageDraw.Draw(sheet)
    draw.text((18, 12), 'ORIGINAL INVENTORY SPRITES / 32 px (left) + 16 px (right), nearest pixel enlargement', fill='#E0E3D5')
    draw.text((18, 30), 'Active: vehicles, ammunition, repair parts. Fuel can: PRESERVED / INACTIVE. Offline asset preview.', fill='#ADB8B1')
    for row, names in enumerate(rows):
        for column, item in enumerate(names):
            x, y = column*220+12, row*180+60
            draw.rectangle((x, y+25, x+203, y+136), fill='#3A4142')
            icon = Image.open(root/'src/main/resources/assets/blacklight/textures/item'/(item+'.png')).convert('RGBA')
            sheet.paste(icon.resize((96, 96), Image.Resampling.NEAREST), (x+5, y+32), icon.resize((96, 96), Image.Resampling.NEAREST))
            small = icon.resize((16, 16), Image.Resampling.NEAREST).resize((64, 64), Image.Resampling.NEAREST)
            sheet.paste(small, (x+126, y+48), small)
            label = item.removesuffix('_chassis') + (' [preserved]' if item=='military_fuel_can' else '')
            draw.text((x+4, y+4), label, fill='#E0E3D5')
            draw.text((x+39, y+141), '32 px', fill='#ADB8B1')
            draw.text((x+142, y+141), '16 px', fill='#ADB8B1')
    path = root/'art/military-vehicles/previews/inventory-icons.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)
    print(path)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--forge-root',type=Path);parser.add_argument('--check',action='store_true');parser.add_argument('--preview',action='store_true');parser.add_argument('--recipes-only',action='store_true');args=parser.parse_args()
    if args.check and args.preview:parser.error('--check cannot write a preview')
    if args.recipes_only and args.preview:parser.error('--recipes-only cannot write an icon preview')
    generate(ROOT,'minecraft_version=1.20.1' in (ROOT/'gradle.properties').read_text(),args.check,args.recipes_only)
    if args.forge_root:generate(args.forge_root.resolve(),True,args.check,args.recipes_only)
    if args.preview:preview(ROOT)
