// Narrow, reviewed 1.20.1 adaptation for this creature action batch only.
import fs from 'node:fs';
import path from 'node:path';
const neo='D:/blacklight-evolution',forge='D:/blacklight-evolution-forge-1.20.1';
const java='src/main/java/com/codex/blacklight/';
const read=(root,f)=>fs.readFileSync(path.join(root,f),'utf8');
const write=(f,s)=>{const p=path.join(forge,f);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,s);};
function adapt(s){return s.replaceAll('software.bernie.geckolib.animatable.instance.','software.bernie.geckolib.core.animatable.instance.')
 .replaceAll('software.bernie.geckolib.animation.','software.bernie.geckolib.core.animation.')
 .replaceAll('Math.clamp(','net.minecraft.util.Mth.clamp(')
 .replaceAll('ResourceLocation.fromNamespaceAndPath(','new ResourceLocation(')
 .replace(/protected void defineSynchedData\((?:net\.minecraft\.network\.syncher\.)?SynchedEntityData.Builder (b|builder)\)\{super.defineSynchedData\(\1\);([^\n]+)\}/g,(_,b,body)=>'protected void defineSynchedData(){super.defineSynchedData();'+body.replaceAll(b+'.define(','entityData.define(')+'}')
 .replaceAll('getDefaultDimensions','getDimensions')
 .replace(/(player|entity|p)\.(getData|setData|syncData)\((ModAttachments\.|com\.codex\.blacklight\.registry\.ModAttachments\.)/g,(_,entity,fn,prefix)=>`com.codex.blacklight.registry.ModAttachments.${fn}(${entity},${prefix}`)
 .replace(/(?<![\w.])getData\(com.codex.blacklight.registry.ModAttachments\./g,'com.codex.blacklight.registry.ModAttachments.getData(this,com.codex.blacklight.registry.ModAttachments.');}
for(const f of ['combat/MutantMove.java','combat/CreaturePoseTable.java','combat/BlockThrowBallistics.java','infection/CommonInfectedMotion.java','entity/BlacklightSpit.java','entity/BlacklightCommonInfected.java','client/model/BlacklightCreatureModel.java'])write(java+f,adapt(read(neo,java+f)));
let mutant=adapt(read(neo,java+'entity/BlacklightMutant.java'))
 .replace('.add(Attributes.STEP_HEIGHT,1)','.add(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(),.4)')
 .replace('super.tick();if(level().isClientSide||isRemoved())return;','super.tick();if(level().isClientSide||isRemoved()||!ModAttachments.available(this))return;');
write(java+'entity/BlacklightMutant.java',mutant);
let hive=adapt(read(neo,java+'entity/BlacklightHive.java'))
 .replace('dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,DamageSource source,boolean recentlyHit)','dropCustomDeathLoot(DamageSource source,int looting,boolean recentlyHit)')
 .replace('super.dropCustomDeathLoot(level,source,recentlyHit)','super.dropCustomDeathLoot(source,looting,recentlyHit)');
write(java+'entity/BlacklightHive.java',hive);
const hydra=java+'entity/BlacklightHydra.java';
let block=read(neo,hydra).split('    private void tickWild(ServerLevel level){')[1].split('    private void spread(ServerLevel level){')[0];
write(hydra,read(forge,hydra).replace(/    private void tickWild\(ServerLevel level\)\{[\s\S]*?(?=    private void spread\(ServerLevel level\)\{)/,'    private void tickWild(ServerLevel level){'+adapt(block).replaceAll('SoundEvents.GENERIC_EXPLODE.value()','SoundEvents.GENERIC_EXPLODE')));
const consume=java+'combat/ConsumeSystem.java';let c=read(forge,consume);
if(!c.includes('BlacklightMutant mutant)mutant.cancelAttack()'))c=c.replace('if(target instanceof com.codex.blacklight.entity.BlacklightCommonInfected civilian)civilian.cancelAttack();','if(target instanceof com.codex.blacklight.entity.BlacklightCommonInfected civilian)civilian.cancelAttack();\n        if(target instanceof com.codex.blacklight.entity.BlacklightMutant mutant)mutant.cancelAttack();');
write(consume,c);
for(const f of ['src/test/java/com/codex/blacklight/combat/MutantCombatTest.java','src/test/java/com/codex/blacklight/combat/BlockThrowBallisticsTest.java','src/test/java/com/codex/blacklight/infection/CommonInfectedMotionTest.java','tools/build_mutant_combat.mjs','art/mutant-combat/manifest.json','tools/creature-combat-test.init.gradle'])write(f,adapt(read(neo,f)));
for(const name of ['stalker','spitter','brute'])for(const f of [`animations/blacklight_${name}.animation.json`,`models/${name}_combat_motion.json`])write('src/main/resources/assets/blacklight/'+f,read(neo,'src/main/resources/assets/blacklight/'+f));
const test='src/creatureCombatTest/java/com/codex/blacklight/gametest/CreatureSkillsGameTest.java';
write(test,adapt(read(neo,test)).replaceAll('net.neoforged.neoforge.','net.minecraftforge.').replaceAll('net.neoforged.fml.common.EventBusSubscriber','net.minecraftforge.fml.common.Mod.EventBusSubscriber').replaceAll('net.neoforged.bus.api.','net.minecraftforge.eventbus.api.').replace('net.minecraftforge.event.entity.living.LivingDamageEvent.Post','com.codex.blacklight.combat.ForgeDamageEvent.Post'));
console.log('Synced only creature combat source, pose assets and opt-in checks.');
