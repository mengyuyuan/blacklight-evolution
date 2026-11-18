import fs from 'node:fs';
const a='D:/blacklight-evolution/',b='D:/blacklight-evolution-forge-1.20.1/',java='src/main/java/com/codex/blacklight/';
function adapt(s){return s.replaceAll('net.neoforged.neoforge.','net.minecraftforge.').replaceAll('net.neoforged.bus.api.','net.minecraftforge.eventbus.api.')
 .replaceAll('net.neoforged.fml.common.EventBusSubscriber','net.minecraftforge.fml.common.Mod.EventBusSubscriber')
 .replaceAll('software.bernie.geckolib.animatable.instance.','software.bernie.geckolib.core.animatable.instance.').replaceAll('software.bernie.geckolib.animation.','software.bernie.geckolib.core.animation.')
 .replace(/protected void defineSynchedData\(SynchedEntityData.Builder (b|builder)\)\{([^\n]*)\}/g,(_,v,body)=>'protected void defineSynchedData(){'+body.replaceAll('super.defineSynchedData('+v+')','super.defineSynchedData()').replaceAll(v+'.define(','entityData.define(')+'}')
 .replaceAll('ResourceLocation.fromNamespaceAndPath(','new ResourceLocation(').replaceAll('Math.clamp(','net.minecraft.util.Mth.clamp(')
 .replaceAll('net.minecraftforge.network.PacketDistributor','com.codex.blacklight.forge.PacketDistributor')
 .replace('import net.minecraftforge.event.entity.living.LivingDamageEvent;','').replaceAll('LivingDamageEvent.Post','com.codex.blacklight.combat.ForgeDamageEvent.Post')
 .replace(/entity\.getData\(com.codex.blacklight.registry.ModAttachments\.([A-Z_]+)\)/g,'com.codex.blacklight.registry.ModAttachments.getData(entity,com.codex.blacklight.registry.ModAttachments.$1)')
 .replace(/entity\.getData\(ModAttachments\.([A-Z_]+)\)/g,'ModAttachments.getData(entity,ModAttachments.$1)');}
for(const f of ['combat/JuggernautContact.java','combat/SourceBossMotion.java','client/model/SourceBossPose.java','entity/EncounterMob.java','client/model/BlacklightCreatureModel.java','client/renderer/BlacklightCreatureRenderer.java','infection/InfectedAttackEffects.java','infection/InfectedAttackStyle.java'])fs.writeFileSync(b+java+f,adapt(fs.readFileSync(a+java+f,'utf8')));
for(const f of ['src/test/java/com/codex/blacklight/combat/JuggernautContactTest.java','src/main/resources/assets/blacklight/models/encounter_juggernaut_source.json','src/main/resources/assets/blacklight/models/encounter_source_actions.json','art/encounters/juggernaut-combat-provenance.json','art/encounters/source-motion-provenance.json','tools/juggernaut-test.init.gradle','tools/build_juggernaut_actions.mjs','tools/export_encounter_source_motion.py'])fs.copyFileSync(a+f,b+f);
const test='src/encounterTest/java/com/codex/blacklight/gametest/JuggernautContactGameTest.java';if(fs.existsSync(a+test))fs.writeFileSync(b+test,adapt(fs.readFileSync(a+test,'utf8')));
console.log('Synced Juggernaut source motion/contact and the required integration only.');
