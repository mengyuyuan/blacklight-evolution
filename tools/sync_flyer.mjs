// Narrow synchronization: only reviewed flyer integrations and their shared source files.
import fs from 'node:fs';
import path from 'node:path';
const neo='D:/blacklight-evolution/',forge='D:/blacklight-evolution-forge-1.20.1/';
const java='src/main/java/com/codex/blacklight/';
function adapt(s){return s.replaceAll('net.neoforged.neoforge.','net.minecraftforge.').replaceAll('net.neoforged.bus.api.','net.minecraftforge.eventbus.api.').replaceAll('net.neoforged.api.distmarker.','net.minecraftforge.api.distmarker.')
 .replaceAll('net.neoforged.fml.common.EventBusSubscriber','net.minecraftforge.fml.common.Mod.EventBusSubscriber')
 .replaceAll('software.bernie.geckolib.animatable.instance.','software.bernie.geckolib.core.animatable.instance.').replaceAll('software.bernie.geckolib.animation.','software.bernie.geckolib.core.animation.')
 .replace(/protected void defineSynchedData\(SynchedEntityData.Builder b\)\{([^\n]*)\}/g,(_,body)=>'protected void defineSynchedData(){'+body.replaceAll('super.defineSynchedData(b)','super.defineSynchedData()').replaceAll('b.define(','entityData.define(')+'}')
 .replace('void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){super.lerpTo(x,y,z,yaw,pitch,kind==EncounterKind.FLYER?1:steps);}', 'void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport){super.lerpTo(x,y,z,yaw,pitch,kind==EncounterKind.FLYER?1:steps,teleport);}')
 .replaceAll('s.a.setData(ModAttachments.CONSUME_VISUAL,','ModAttachments.setData(s.a,ModAttachments.CONSUME_VISUAL,');}
function write(file,s){fs.mkdirSync(path.dirname(forge+file),{recursive:true});fs.writeFileSync(forge+file,s);}
for(const f of ['combat/FlyerMotion.java','combat/FlyerContact.java','combat/FlyerFlight.java','combat/SourceBossMotion.java','combat/EncounterTactics.java','entity/EncounterMob.java','entity/EncounterKind.java','client/model/FlyerPose.java','client/model/EncounterPose.java'])write(java+f,adapt(fs.readFileSync(neo+java+f,'utf8')));
const style=java+'infection/InfectedAttackStyle.java';write(style,fs.readFileSync(forge+style,'utf8').replace('mob.kind()==EncounterKind.JUGGERNAUT)return CRUSH;','(mob.kind()==EncounterKind.JUGGERNAUT||mob.kind()==EncounterKind.FLYER))return CRUSH;'));
for(const f of ['src/test/java/com/codex/blacklight/combat/EncounterMoveTest.java','src/encounterTest/java/com/codex/blacklight/gametest/EncounterGameTest.java','src/encounterTest/java/com/codex/blacklight/gametest/FlyerFlightGameTest.java','tools/flyer-test.init.gradle','tools/export_flyer_motion.py','art/encounters/flyer-source-motion.json','src/main/resources/assets/blacklight/geo/flyer_mc.geo.json','src/main/resources/assets/blacklight/models/encounter_flyer_source.json'])write(f,adapt(fs.readFileSync(neo+f,'utf8')));
const visual='src/flyerVisual/java/com/codex/blacklight/client/FlyerVisual.java';
if(fs.existsSync(neo+visual))write(visual,adapt(fs.readFileSync(neo+visual,'utf8'))
 .replaceAll('import net.minecraftforge.event.tick.*;','import net.minecraftforge.event.TickEvent;')
 .replace('ClientTickEvent.Post e){','TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;')
 .replace('LevelTickEvent.Post e){','TickEvent.LevelTickEvent e){if(e.phase!=TickEvent.Phase.END)return;').replaceAll('e.getLevel()','e.level')
 .replace('RenderFrameEvent.Post e){','TickEvent.RenderTickEvent e){if(e.phase!=TickEvent.Phase.END)return;')
 .replaceAll('mc.getTimer().getGameTimeDeltaPartialTick(false)','mc.getFrameTime()')
 .replace('net.minecraftforge.event.entity.living.LivingDamageEvent.Post e){','com.codex.blacklight.combat.ForgeDamageEvent.Post e){')
 .replace('screen.writeToFile(output.resolve(name))','screen.writeToFile(output.resolve(name))'));
console.log('Flyer source, controller, pose and tests synchronized.');
for(const f of [java+'combat/ContactEffectsQueue.java',java+'client/renderer/FlyerContactPresentation.java','src/test/java/com/codex/blacklight/combat/ContactEffectsQueueTest.java'])write(f,fs.readFileSync(neo+f,'utf8'));
const presentation=java+'client/renderer/ContactEffectsPresentation.java';write(presentation,fs.readFileSync(forge+presentation,'utf8').replace('QUEUE.drain(time,p->show(p,time));','QUEUE.drain(time,p->FlyerContactPresentation.ready(p,time,e.getPartialTick()),p->show(p,time));'));
