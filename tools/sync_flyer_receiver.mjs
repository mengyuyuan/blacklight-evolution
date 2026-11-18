import fs from 'node:fs';
const neo='D:/blacklight-evolution/',forge='D:/blacklight-evolution-forge-1.20.1/',java='src/main/java/com/codex/blacklight/';
const adapt=s=>s.replaceAll('net.neoforged.neoforge.','net.minecraftforge.').replaceAll('net.neoforged.bus.api.','net.minecraftforge.eventbus.api.').replaceAll('net.neoforged.fml.common.EventBusSubscriber','net.minecraftforge.fml.common.Mod.EventBusSubscriber').replaceAll('net.neoforged.api.distmarker.','net.minecraftforge.api.distmarker.').replace('protected void defineSynchedData(SynchedEntityData.Builder b){}','protected void defineSynchedData(){}');
for(const file of ['combat/AnimatedHurtBody.java','combat/FlyerContact.java','entity/FlyerPart.java'])fs.writeFileSync(forge+java+file,adapt(fs.readFileSync(neo+java+file,'utf8')));
for(const root of [neo,forge]){
 for(const file of ['combat/CombatTargeting.java','combat/WeaponCutContact.java','combat/WeaponCombatSystem.java','combat/WeaponImpact.java','client/renderer/InfectedImpactVisuals.java']){
  const p=root+java+file;let s=fs.readFileSync(p,'utf8');
  s=s.replace(/(?:com\.codex\.blacklight\.combat\.)?AnimatedHurtBody.of\((target|victim|v)\) instanceof ((?:com\.codex\.blacklight\.combat\.)?AnimatedHurtBody) hydra/g,'$1 instanceof $2 hydra&&hydra.animatedHurtBody()');fs.writeFileSync(p,s);
 }
 if(root===forge){const p=root+java+'entity/EncounterMob.java';fs.writeFileSync(p,adapt(fs.readFileSync(p,'utf8')));}
}
console.log('Flyer receiver shared files synchronized; existing platform adapters retained.');
