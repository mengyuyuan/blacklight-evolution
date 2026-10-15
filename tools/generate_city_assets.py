"""Project-authored pixel textures and data for the city prototype; no extracted game assets."""
import json, math, random, struct, zlib
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
def write(path, value):
    p=ROOT/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def png(name,kind):
    n=32;r=random.Random(1789+kind);rows=[]
    for y in range(n):
        row=bytearray([0])
        for x in range(n):
            noise=r.randrange(-8,9);wave=math.sin(x*.65+math.sin(y*.35)*1.6)
            c=[68+noise+int(wave*14),20+noise//2,26+noise//2]
            if kind==1: c=[36+noise,25+noise//2,28+noise//2] if (x+int(math.sin(y*.3)*3))%8<3 else [88+noise,30+noise//2,35+noise//2]
            if kind==2:
                vein=abs((x-int(12+5*math.sin(y*.22)))%32)<3 or (y%16==5 and x>14)
                c=[138+noise,34+noise//2,40+noise//2] if vein else [42+noise,18+noise//2,23+noise//2]
            if kind==3:
                seam=x%12==0 or (y+(x//12)*6)%13<2;c=[22+noise//2,20+noise//3,23+noise//3] if seam else [52+noise,41+noise//2,43+noise//2]
            if kind==4:c=[38+noise,34+noise,31+noise] if wave<.6 else [64+noise,24+noise//2,26+noise//2]
            if kind in (5,6):
                radius=((x-15.5)/12)**2+((y-15.5)/13)**2
                c=[100+noise,28+noise//2,35+noise//2] if .65<radius<1.1 else [35+noise,15+noise//2,20+noise//2]
                if radius<.65:c=([7,5,8] if kind==6 else [60+noise,16+noise//2,22+noise//2])
                if .7<radius<.8:c=[135+noise,43+noise//2,44+noise//2]
            if kind==7:
                rad=((x%16-8)/7)**2+((y%16-8)/7)**2;c=[122+noise,43+noise//2,48+noise//2] if rad<.8 else [41+noise,18+noise//2,23+noise//2]
            row.extend(max(0,min(255,v)) for v in c);row.append(255)
        rows.append(row)
    def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
    data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',n,n,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b'')
    p=ROOT/f'assets/blacklight/textures/block/{name}.png';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)

for i,name in enumerate(['city_flesh','city_fiber','city_vein','city_shell','city_wounded','city_hatch','city_hatch_open','city_sac']):png(name,i)
names=['city_flesh','city_fiber','city_vein','city_shell','city_wounded','city_tissue_layer','city_hatch','city_sac']
for name in names:
    tex='blacklight:block/'+('city_flesh' if name=='city_tissue_layer' else name)
    model={'parent':'minecraft:block/cube_all','textures':{'all':tex}}
    variants={'':{'model':'blacklight:block/'+name}}
    if name in ('city_fiber','city_vein'):
        model={'parent':'minecraft:block/cube_column','textures':{'end':tex,'side':tex}}
        variants={ 'axis=y':{'model':'blacklight:block/'+name},'axis=x':{'model':'blacklight:block/'+name,'x':90,'y':90},'axis=z':{'model':'blacklight:block/'+name,'x':90} }
    if name=='city_tissue_layer':
        textures={'bottom':tex,'top':tex,'side':tex};model={'parent':'minecraft:block/slab','textures':textures}
        write('assets/blacklight/models/block/city_tissue_layer_top.json',{'parent':'minecraft:block/slab_top','textures':textures})
        variants={'type=bottom':{'model':'blacklight:block/'+name},'type=top':{'model':'blacklight:block/'+name+'_top'},'type=double':{'model':'blacklight:block/city_flesh'}}
    if name=='city_hatch':
        model={'parent':'minecraft:block/orientable','textures':{'front':tex,'side':'blacklight:block/city_shell','top':'blacklight:block/city_flesh'}}
        opened=json.loads(json.dumps(model));opened['textures']['front']='blacklight:block/city_hatch_open'
        write('assets/blacklight/models/block/city_hatch_open.json',opened)
        variants={f'facing={face},open={str(op).lower()}':{'model':'blacklight:block/city_hatch'+('_open' if op else ''),'y':rot} for face,rot in [('north',0),('east',90),('south',180),('west',270)] for op in (False,True)}
    write(f'assets/blacklight/models/block/{name}.json',model)
    write(f'assets/blacklight/blockstates/{name}.json',{'variants':variants})
    write(f'assets/blacklight/models/item/{name}.json',{'parent':'blacklight:block/'+name})
    functions=[]
    if name=='city_tissue_layer':functions=[{'function':'minecraft:set_count','count':2,'conditions':[{'condition':'minecraft:block_state_property','block':'blacklight:city_tissue_layer','properties':{'type':'double'}}]}]
    write(f'data/blacklight/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'blacklight:'+name,'functions':functions}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write('assets/blacklight/models/item/city_transit.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/compass_16'}})
write('data/blacklight/recipe/city_transit.json',{'type':'minecraft:crafting_shaped','pattern':[' I ','ICI',' R '],'key':{'I':{'item':'minecraft:iron_ingot'},'C':{'item':'minecraft:compass'},'R':{'item':'minecraft:redstone'}},'result':{'id':'blacklight:city_transit','count':1}})
write('data/blacklight/dimension_type/city.json',{'ultrawarm':False,'natural':True,'coordinate_scale':1,'has_skylight':True,'has_ceiling':False,'ambient_light':0,'piglin_safe':False,'bed_works':True,'respawn_anchor_works':False,'has_raids':False,'logical_height':320,'min_y':0,'height':320,'infiniburn':'#minecraft:infiniburn_overworld','effects':'minecraft:overworld','monster_spawn_light_level':{'type':'minecraft:uniform','min_inclusive':0,'max_inclusive':7},'monster_spawn_block_light_limit':0})
write('data/blacklight/worldgen/biome/city.json',{'has_precipitation':True,'temperature':.8,'downfall':.4,'effects':{'fog_color':12638463,'water_color':4159204,'water_fog_color':329011,'sky_color':7907327},'spawners':{},'spawn_costs':{},'carvers':{},'features':[]})
write('data/blacklight/dimension/city.json',{'type':'blacklight:city','generator':{'type':'blacklight:city','futian':True,'settings':{'biome':'blacklight:city','layers':[{'height':1,'block':'minecraft:bedrock'}],'structure_overrides':[],'lakes':False,'features':False}}})
translations={
 'city.blacklight.tab':('黑光 · 城市','Blacklight · City'),
 'item.blacklight.city_transit':('城市通行信标','City Transit Beacon'),
 'city.blacklight.welcome':('已进入深圳福田市民中心周边。使用通行信标或 /blacklightcity travel 返回；摧毁母巢外墙的七个孵化口可停止其生产。','City prototype: use the beacon or /blacklightcity travel to return. Destroy all seven exterior hatcheries to disable a hive.'),
 'city.blacklight.status':('城市母巢已摧毁：%s / 2；新增感染街区：%s','Hives destroyed: %s / 2; additional infected districts: %s'),
 'city.blacklight.hive_destroyed':('母巢已失活：停止生产与扩散，周围感染者仍然存在。','Hive disabled: production and spread stopped. Existing infected remain.'),
 'city.blacklight.unavailable':('城市维度尚未加载，请重启服务器后重试。','City dimension unavailable. Restart the server and try again.'),
 'city.blacklight.no_safe_exit':('返回点附近没有安全落脚处，请先使用其它传送方式返回。','No safe ground near the return point. Use another travel method.'),
}
for name,zh,en in [('city_flesh','血肉覆盖层','Biomass Cover'),('city_fiber','束状组织','Tissue Bundle'),('city_vein','血肉脉管','Biomass Vein'),('city_shell','硬化肉壳','Hardened Carapace'),('city_wounded','坏死组织','Necrotic Tissue'),('city_tissue_layer','蔓延组织薄层','Spreading Tissue Slab'),('city_hatch','母巢孵化口','Hive Hatchery'),('city_sac','感染囊泡','Infected Sac')]:translations['block.blacklight.'+name]=(zh,en)
for lang,index in [('zh_cn',0),('en_us',1)]:
    p=ROOT/f'assets/blacklight/lang/{lang}.json';values=json.loads(p.read_text(encoding='utf-8-sig'));values.update({k:v[index] for k,v in translations.items()});write(str(p.relative_to(ROOT)),values)
write('data/minecraft/tags/block/mineable/axe.json',{'replace':False,'values':['blacklight:'+n for n in names]})
print('City textures, block models, loot, translation, dimension and biome generated.')
