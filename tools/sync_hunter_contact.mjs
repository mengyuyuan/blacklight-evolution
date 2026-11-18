import fs from 'node:fs';
import path from 'node:path';
const neo='D:/blacklight-evolution/',forge='D:/blacklight-evolution-forge-1.20.1/',java='src/main/java/com/codex/blacklight/';
const read=(r,f)=>fs.readFileSync(r+f,'utf8');
const write=(f,s)=>{fs.mkdirSync(path.dirname(forge+f),{recursive:true});fs.writeFileSync(forge+f,s);};
function adapt(s){return s.replaceAll('Math.clamp(','net.minecraft.util.Mth.clamp(')
 .replaceAll('ResourceLocation.fromNamespaceAndPath(','new ResourceLocation(')
 .replaceAll('software.bernie.geckolib.animation.','software.bernie.geckolib.core.animation.')
 .replace(/entity\.getData\(com.codex.blacklight.registry.ModAttachments\./g,'com.codex.blacklight.registry.ModAttachments.getData(entity,com.codex.blacklight.registry.ModAttachments.')
 .replaceAll('net.neoforged.bus.api.','net.minecraftforge.eventbus.api.')
 .replaceAll('net.neoforged.fml.common.EventBusSubscriber','net.minecraftforge.fml.common.Mod.EventBusSubscriber')
 .replace('import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;','')
 .replaceAll('LivingDamageEvent.Post','com.codex.blacklight.combat.ForgeDamageEvent.Post')
 .replaceAll('net.neoforged.neoforge.network.PacketDistributor','com.codex.blacklight.forge.PacketDistributor');}
for(const f of ['combat/CreatureAttack.java','combat/CreaturePoseTable.java','combat/HunterContactPose.java','client/model/BlacklightHunterModel.java','infection/InfectedAttackEffects.java'])write(java+f,adapt(read(neo,java+f)));
// Only the reviewed attack sections; preserve the port's attachments, attributes and save/consume code.
const file=java+'entity/BlacklightHunter.java';let source=read(neo,file),port=read(forge,file);
if(!port.includes('import com.codex.blacklight.combat.HunterContactPose;'))port=port.replace('import com.codex.blacklight.combat.CreatureAttack;','$&\nimport com.codex.blacklight.combat.HunterContactPose;');
function block(start,end){let a=source.indexOf(start),b=source.indexOf(end,a);let c=port.indexOf(start),d=port.indexOf(end,c);if([a,b,c,d].some(n=>n<0))throw Error(start);port=port.slice(0,c)+adapt(source.slice(a,b))+port.slice(d);}
block('    private long nextLightStagger','    public BlacklightHunter(');
block('        int attack = entityData.get(ATTACK);','    public boolean isBeingConsumed()');
block('    public boolean beginAttack(','    public int consumerId()');
block('    private final class SwipeGoal','        @Override public void stop()');
write(file,port);
const render=java+'client/renderer/BlacklightHunterRenderer.java';let text=read(forge,render).replace('else if(!com.codex.blacklight.client.animation.NativeReceiverPose.available(entity,partial))','else if(!entity.committedAttack()&&!com.codex.blacklight.client.animation.NativeReceiverPose.available(entity,partial))');write(render,text);
for(const f of ['src/main/resources/assets/blacklight/animations/blacklight_hunter.animation.json','src/main/resources/assets/blacklight/models/hunter_combat_motion.json','src/test/java/com/codex/blacklight/combat/HunterContactTest.java','src/test/java/com/codex/blacklight/combat/CreatureCombatTest.java','tools/build_creature_combat.py'])write(f,read(neo,f));
const test='src/encounterTest/java/com/codex/blacklight/gametest/HunterContactGameTest.java';if(fs.existsSync(neo+test))write(test,adapt(read(neo,test)).replaceAll('net.neoforged.neoforge.gametest.','net.minecraftforge.gametest.'));
console.log('Synced Hunter contact, source clocks and pose sampling; preserved original model and consumption.');
for(const f of ['src/main/resources/assets/blacklight/animations/brawler.animation.json','src/test/java/com/codex/blacklight/combat/EncounterMoveTest.java','art/hydra/combat-schedule-provenance.json','art/hunter/combat-contact-provenance.json','tools/hunter-contact-test.init.gradle'])write(f,read(neo,f));
