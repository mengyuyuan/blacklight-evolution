import fs from 'node:fs';
import path from 'node:path';
const neo='D:/blacklight-evolution/',forge='D:/blacklight-evolution-forge-1.20.1/',java='src/main/java/com/codex/blacklight/';
const read=(root,file)=>fs.readFileSync(root+file,'utf8');
const write=(file,s)=>{fs.mkdirSync(path.dirname(forge+file),{recursive:true});fs.writeFileSync(forge+file,s);};
function adapt(s){return s.replaceAll('net.neoforged.neoforge.','net.minecraftforge.').replaceAll('net.neoforged.bus.api.','net.minecraftforge.eventbus.api.').replaceAll('net.neoforged.api.distmarker.','net.minecraftforge.api.distmarker.')
 .replaceAll('software.bernie.geckolib.animatable.instance.','software.bernie.geckolib.core.animatable.instance.').replaceAll('software.bernie.geckolib.animation.','software.bernie.geckolib.core.animation.')
 .replaceAll('DeferredHolder<EntityType<?>,EntityType<EncounterMob>>','RegistryObject<EntityType<EncounterMob>>').replaceAll('DeferredHolder<EntityType<?>,EntityType<MilitaryUav>>','RegistryObject<EntityType<MilitaryUav>>').replaceAll('DeferredHolder<EntityType<?>,EntityType<EncounterProjectile>>','RegistryObject<EntityType<EncounterProjectile>>')
 .replaceAll('DeferredHolder<EntityType<?>,EntityType<BlacklightHunter>>','RegistryObject<EntityType<BlacklightHunter>>')
 .replaceAll('DeferredHolder<Item,Item>','RegistryObject<Item>').replaceAll('DeferredHolder<CreativeModeTab,CreativeModeTab>','RegistryObject<CreativeModeTab>')
 .replace(/protected void defineSynchedData\(SynchedEntityData.Builder (b|builder)\)\{([^\n]*)\}/g,(_,v,body)=>'protected void defineSynchedData(){'+body.replaceAll('super.defineSynchedData('+v+')','super.defineSynchedData()').replaceAll(v+'.define(','entityData.define(')+'}')
 .replace('protected double getDefaultGravity(){return .03;}','protected float getGravity(){return .03F;}')
 .replaceAll('SoundEvents.GENERIC_EXPLODE.value()','SoundEvents.GENERIC_EXPLODE').replaceAll('net.minecraftforge.common.DeferredSpawnEggItem','net.minecraftforge.common.ForgeSpawnEggItem')
 .replaceAll('SpawnEggItem.byId(', 'net.minecraftforge.common.ForgeSpawnEggItem.fromEntityType(')
 .replaceAll('net.minecraftforge.fml.common.EventBusSubscriber','net.minecraftforge.fml.common.Mod.EventBusSubscriber')
 .replaceAll('net.neoforged.fml.common.EventBusSubscriber','net.minecraftforge.fml.common.Mod.EventBusSubscriber')
 .replaceAll('net.minecraftforge.network.PacketDistributor','com.codex.blacklight.forge.PacketDistributor')
 .replaceAll('net.minecraftforge.event.entity.living.LivingDamageEvent.Post','com.codex.blacklight.combat.ForgeDamageEvent.Post');}
for(const f of ['combat/EncounterImpact.java','combat/SourceBossMotion.java','combat/SourceBossAction.java','client/model/SourceBossPose.java','combat/EncounterMove.java','combat/EncounterAnatomyPose.java','combat/EncounterTactics.java','entity/EncounterKind.java','entity/EncounterMob.java','entity/EncounterPart.java','entity/EncounterProjectile.java','entity/MilitaryUav.java','registry/EncounterEntities.java','client/model/EncounterPose.java','client/model/EncounterSourceMotion.java','client/model/EncounterBrawlerMotion.java','military/MilitaryTactics.java'])write(java+f,adapt(read(neo,java+f)));
// Apply only the reviewed insertion anchors in each checkout; retain the rest of the Forge port.
let integration=read(neo,'tools/integrate_encounters.mjs').replace("const root='D:/blacklight-evolution/';","const root='D:/blacklight-evolution-forge-1.20.1/';");
integration=integration.replaceAll("builder.define(MELEE_STARTED,-1L);builder.define(RADIO_STARTED,-1L);","entityData.define(MELEE_STARTED,-1L);entityData.define(RADIO_STARTED,-1L);").replaceAll("'builder.define(MELEE_STARTED,-1L);'","'entityData.define(MELEE_STARTED,-1L);'");
write('tools/integrate_encounters.mjs',integration);
await import('file:///D:/blacklight-evolution-forge-1.20.1/tools/integrate_encounters.mjs?'+Date.now());
const manifest=JSON.parse(read(neo,'art/encounters/manifest.json'));
// Greene uses ordinary authored Geo cubes, with no runtime mesh override.
for(const file of ['art/encounters/greene-surface-provenance.json','tools/author_greene_model.py','art/encounters/monster-style-provenance.json','tools/encounter_cuboid_style.py','tools/author_monster_models.py'])fs.copyFileSync(neo+file,forge+file);
for(const {id}of manifest.entities)for(const p of [`geo/${id}_mc.geo.json`,`textures/entity/${id}_mc.png`,`animations/${id}.animation.json`,`models/item/${id}_spawn_egg.json`]){const f='src/main/resources/assets/blacklight/'+p;fs.copyFileSync(neo+f,forge+f);}
for(const lang of ['en_us','zh_cn']){let f='src/main/resources/assets/blacklight/lang/'+lang+'.json',a=JSON.parse(read(neo,f)),b=JSON.parse(read(forge,f));for(const k of Object.keys(a))if(k==='itemGroup.blacklight.encounters'||manifest.entities.some(e=>k==='entity.blacklight.'+e.id||k==='item.blacklight.'+e.id+'_spawn_egg'))b[k]=a[k];write(f,JSON.stringify(b,null,2)+'\n');}
write('art/encounters/manifest.json',read(neo,'art/encounters/manifest.json'));
for(const name of ['unconsumable','ragdoll_disabled'])write('src/main/resources/data/blacklight/tags/entity_types/'+name+'.json',read(neo,'src/main/resources/data/blacklight/tags/entity_type/'+name+'.json'));
for(const f of ['src/test/java/com/codex/blacklight/combat/EncounterMoveTest.java','src/encounterTest/java/com/codex/blacklight/gametest/EncounterGameTest.java','src/encounterTest/java/com/codex/blacklight/gametest/HunterAliasGameTest.java','tools/encounter-test.init.gradle'])if(fs.existsSync(neo+f))write(f,adapt(read(neo,f)));
for(const id of ["common_infected","infected_host","blacklight_stalker","blacklight_spitter","blacklight_brute","blacklight_hive"]){
 const f='src/main/resources/assets/blacklight/models/item/'+id+'_spawn_egg.json';fs.copyFileSync(neo+f,forge+f);
 for(const lang of ['zh_cn','en_us']){const langFile='src/main/resources/assets/blacklight/lang/'+lang+'.json';const a=JSON.parse(read(neo,langFile)),b=JSON.parse(read(forge,langFile));b['item.blacklight.'+id+'_spawn_egg']=a['item.blacklight.'+id+'_spawn_egg'];write(langFile,JSON.stringify(b,null,2)+'\n');}
}
console.log('Synced encounter and military tactical changes, preserving the Forge port.');
await import('./integrate_hunter_alias.mjs?'+Date.now());
write(java+'client/SupremeHunterBossBar.java',adapt(read(neo,java+'client/SupremeHunterBossBar.java')));
fs.copyFileSync(neo+'tools/rig_greene_spine.py',forge+'tools/rig_greene_spine.py');

for(const id of ['goliath','elizabeth_greene','leader_hunter']){const f='src/main/resources/assets/blacklight/models/encounter_'+id+'_source.json';fs.copyFileSync(neo+f,forge+f);}
for(const f of ['src/main/resources/assets/blacklight/models/encounter_source_actions.json','art/encounters/source-motion-provenance.json','tools/export_encounter_source_actions.mjs','tools/export_encounter_source_motion.py'])fs.copyFileSync(neo+f,forge+f);
for(const lang of ['en_us','zh_cn']){const f='src/main/resources/assets/blacklight/lang/'+lang+'.json',a=JSON.parse(read(neo,f)),b=JSON.parse(read(forge,f));for(const k of Object.keys(a))if(k.startsWith('boss.blacklight.'))b[k]=a[k];write(f,JSON.stringify(b,null,2)+'\n');}
