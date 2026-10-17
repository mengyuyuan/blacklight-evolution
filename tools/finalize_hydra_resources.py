from pathlib import Path
import json
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/blacklight'
translations={
'hydra.blacklight.outside':('请在黑光维度之外培育组织；成熟后将建立可往返的九头蛇通道。','Grow tissue outside Blacklight to establish a two-way Hydra passage.'),
'entity.blacklight.blacklight_hydra':('九头蛇','Hydra'),
'item.blacklight.blacklight_hydra_spawn_egg':('九头蛇刷怪蛋','Hydra Spawn Egg'),
'item.blacklight.living_hive_tissue':('活性蜂巢组织','Living Hive Tissue'),
'hydra.blacklight.planted':('活性组织已扎根。将生物引到根部喂养；成熟后靠近进入黑光维度，潜行可避免被卷入。','Tissue planted. Bring creatures to its roots. Approach when mature to enter Blacklight; sneak to avoid capture.'),
'hydra.blacklight.progress':('喂养：%s / %s · 成熟度：%s%%','Feeding: %s / %s · Maturity: %s%%'),
'hydra.blacklight.arrived':('通道已连通。返回时靠近此处的九头蛇；潜行可避免被夹起。','Passage linked. Approach this Hydra to return; sneak to avoid capture.'),
'hydra.blacklight.wait':('入口尚未准备完成，请稍后再靠近。','Destination is not ready. Approach again shortly.')}
for i,lang in enumerate(['zh_cn','en_us']):
    p=A/'lang'/f'{lang}.json';d=json.loads(p.read_text(encoding='utf-8-sig'));d.update({k:v[i] for k,v in translations.items()});p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
for name,model in [('blacklight_hydra_spawn_egg',{'parent':'minecraft:item/template_spawn_egg'}),('living_hive_tissue',{'parent':'minecraft:item/generated','textures':{'layer0':'blacklight:block/hive_sac'}})]:
    (A/'models/item'/f'{name}.json').write_text(json.dumps(model,indent=2),encoding='utf8')
