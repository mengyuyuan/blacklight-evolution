import fs from 'node:fs';
const a='D:/blacklight-evolution/',b='D:/blacklight-evolution-forge-1.20.1/',java='src/main/java/com/codex/blacklight/';
function adapt(s){return s.replaceAll('net.neoforged.neoforge.','net.minecraftforge.').replaceAll('net.neoforged.bus.api.','net.minecraftforge.eventbus.api.')
 .replaceAll('net.neoforged.fml.common.EventBusSubscriber','net.minecraftforge.fml.common.Mod.EventBusSubscriber')
 .replaceAll('software.bernie.geckolib.animatable.instance.','software.bernie.geckolib.core.animatable.instance.').replaceAll('software.bernie.geckolib.animation.','software.bernie.geckolib.core.animation.')
 .replace(/protected void defineSynchedData\(SynchedEntityData.Builder (b|builder)\)\{([^\n]*)\}/g,(_,v,body)=>'protected void defineSynchedData(){'+body.replaceAll('super.defineSynchedData('+v+')','super.defineSynchedData()').replaceAll(v+'.define(','entityData.define(')+'}')
 .replaceAll('Math.clamp(','net.minecraft.util.Mth.clamp(').replaceAll('net.minecraftforge.network.PacketDistributor','com.codex.blacklight.forge.PacketDistributor')
 .replaceAll('p.entityInteractionRange()','p.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get())')
 .replace('import net.minecraftforge.event.entity.living.LivingDamageEvent;','').replaceAll('LivingDamageEvent.Post','com.codex.blacklight.combat.ForgeDamageEvent.Post')
 .replaceAll('getDefaultDimensions(Pose pose)','getDimensions(Pose pose)').replaceAll('super.getDefaultDimensions(pose)','super.getDimensions(pose)')
 .replaceAll('canBeLeashed()','canBeLeashed(Player player)').replaceAll('net.minecraftforge.event.EventHooks.canEntityGrief(level,this)','net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(level,this)')
 .replace(/(\w+)\.getData\((com.codex.blacklight.registry.ModAttachments\.\w+)\)/g,'com.codex.blacklight.registry.ModAttachments.getData($1,$2)')
 .replace(/(\w+)\.(setData|syncData)\((com.codex.blacklight.registry.ModAttachments\.\w+)([,\)])/g,'com.codex.blacklight.registry.ModAttachments.$2($1,$3$4');}
for(const f of ['combat/HydraThrow.java','entity/HydraDebris.java','client/renderer/HydraDebrisRenderer.java','combat/HydraCombatAction.java','combat/HydraContact.java','combat/HydraHurtContact.java','combat/HydraPicking.java','entity/HydraPart.java','mixin/HydraProjectileContactMixin.java','mixin/client/HydraPickClockMixin.java','city/HydraMotion.java','entity/BlacklightHydra.java','infection/InfectedAttackEffects.java','infection/InfectedAttackStyle.java'])fs.writeFileSync(b+java+f,adapt(fs.readFileSync(a+java+f,'utf8')));
for(const f of ['src/test/java/com/codex/blacklight/combat/HydraContactTest.java','src/main/resources/assets/blacklight/models/hydra_motion.json','src/main/resources/assets/blacklight/models/hydra_combat.json','src/main/resources/assets/blacklight/animations/blacklight_hydra.animation.json','art/hydra/combat-rig-calibration.json','art/hydra/contact-source-provenance.json','tools/hydra-contact-test.init.gradle','tools/build_hydra_contacts.mjs','tools/retarget_hydra_contacts.py'])fs.copyFileSync(a+f,b+f);
for(const f of ['src/encounterTest/java/com/codex/blacklight/gametest/HydraContactGameTest.java'])if(fs.existsSync(a+f))fs.writeFileSync(b+f,adapt(fs.readFileSync(a+f,'utf8')));
console.log('Synced Hydra combat contact; transport API adapted explicitly.');
for(const f of ['tools/retarget_hydra_throw.py','tools/audit_hydra_throw.mjs','tools/hydra-throw-test.init.gradle','art/hydra/throw-source-provenance.json','art/hydra/throw-rig-calibration.json'])fs.copyFileSync(a+f,b+f);
fs.writeFileSync(b+'src/encounterTest/java/com/codex/blacklight/gametest/HydraThrowGameTest.java',adapt(fs.readFileSync(a+'src/encounterTest/java/com/codex/blacklight/gametest/HydraThrowGameTest.java','utf8')));
for(const f of ['src/main/java/com/codex/blacklight/client/renderer/HydraWoundAnchor.java','src/test/java/com/codex/blacklight/client/renderer/HydraWoundAnchorTest.java']){fs.mkdirSync(b+f.slice(0,f.lastIndexOf('/')),{recursive:true});fs.copyFileSync(a+f,b+f);}
{
 const f='src/encounterTest/java/com/codex/blacklight/gametest/HydraHurtGameTest.java';
 let s=adapt(fs.readFileSync(a+f,'utf8')).replace(',ClientInformation.createDefault()','')
 .replaceAll('p.getData(ModAttachments.PLAYER_STATE)','ModAttachments.getData(p,ModAttachments.PLAYER_STATE)')
 .replaceAll('new net.minecraftforge.event.tick.PlayerTickEvent.Post(p)','new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p)');
 fs.writeFileSync(b+f,s);fs.copyFileSync(a+'tools/hydra-hurt-test.init.gradle',b+'tools/hydra-hurt-test.init.gradle');
}
fs.copyFileSync(a+'tools/hydra-gateway-test.init.gradle',b+'tools/hydra-gateway-test.init.gradle');
for(const visual of ['src/hydraThrowVisual/java/com/codex/blacklight/client/HydraThrowVisual.java','src/hydraContactVisual/java/com/codex/blacklight/client/HydraContactVisual.java','src/hydraHurtVisual/java/com/codex/blacklight/client/HydraHurtVisual.java']){
if(fs.existsSync(a+visual)){
 let s=adapt(fs.readFileSync(a+visual,'utf8')).replaceAll('net.neoforged.api.distmarker.Dist','net.minecraftforge.api.distmarker.Dist')
  .replace('import net.minecraftforge.client.event.*;','import net.minecraftforge.event.TickEvent;').replace('import net.minecraftforge.event.tick.*;','')
  .replace('ClientTickEvent.Post e){','TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;')
  .replace('LevelTickEvent.Post e){','TickEvent.LevelTickEvent e){if(e.phase!=TickEvent.Phase.END)return;')
  .replace('RenderFrameEvent.Post e){','TickEvent.RenderTickEvent e){if(e.phase!=TickEvent.Phase.END)return;')
  .replaceAll('e.getLevel()','e.level').replaceAll('mc.getTimer().getGameTimeDeltaPartialTick(false)','e.renderTickTime')
  .replaceAll('net.minecraftforge.event.entity.living.com.codex.blacklight.combat.ForgeDamageEvent','com.codex.blacklight.combat.ForgeDamageEvent');
 fs.mkdirSync(b+visual.slice(0,visual.lastIndexOf('/')),{recursive:true});fs.writeFileSync(b+visual,s);
}
}
