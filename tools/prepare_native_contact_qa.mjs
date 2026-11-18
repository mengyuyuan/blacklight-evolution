import fs from 'node:fs';
const forge='D:/blacklight-evolution-forge-1.20.1/',neo='D:/blacklight-evolution/';
function write(p,s){fs.mkdirSync(p.substring(0,p.lastIndexOf('/')),{recursive:true});fs.writeFileSync(p,s);}
for(const name of ['Juggernaut','Hunter']){
 const dir=name==='Hunter'?'hunterContactVisual':'juggernautVisual',f=`src/${dir}/java/com/codex/blacklight/client/${name}ContactVisual.java`;
 let s=fs.readFileSync(forge+f,'utf8').replaceAll('net.minecraftforge.api.distmarker.','net.neoforged.api.distmarker.').replaceAll('net.minecraftforge.eventbus.api.','net.neoforged.bus.api.').replaceAll('net.minecraftforge.fml.common.Mod.EventBusSubscriber','net.neoforged.fml.common.EventBusSubscriber').replace('import net.minecraftforge.event.TickEvent;','import net.neoforged.neoforge.client.event.ClientTickEvent;\nimport net.neoforged.neoforge.client.event.RenderFrameEvent;\nimport net.neoforged.neoforge.event.tick.LevelTickEvent;')
 .replaceAll('TickEvent.ClientTickEvent e','ClientTickEvent.Post e').replaceAll('TickEvent.LevelTickEvent e','LevelTickEvent.Post e').replaceAll('TickEvent.RenderTickEvent e','RenderFrameEvent.Post e').replaceAll('e.phase!=TickEvent.Phase.END||','').replaceAll('e.level','e.getLevel()').replaceAll('ForgeDamageEvent.Post','net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post')
 .replaceAll('mc.getFrameTime()','mc.getTimer().getGameTimeDeltaPartialTick(false)').replaceAll('creature-combat-qa/client','creature-combat-qa/neo-client').replace('Gecko 4.8.4','Gecko 4.9.2')
 .replace('private static boolean initialized,queued,done;','private static boolean initialized,queued,done;\n    private static int bootstrapTicks;')
 .replace('var server=mc.getSingleplayerServer();if(mc.level==null',`if(!initialized&&++bootstrapTicks%200==0)com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Native contact QA bootstrap: {}",mc.screen==null?"no screen":mc.screen.getClass().getName()+" / "+mc.screen.getTitle().getString());
            if(!initialized&&bootstrapTicks>2400)throw new IllegalStateException("Native QA world startup timed out");
            var server=mc.getSingleplayerServer();if(mc.level==null`);
 write(neo+f,s);
}
for(const f of ['HiddenWindowMixin.java','MutedListenerMixin.java','NoMouseCaptureMixin.java']){
 const p='src/silentQa/java/com/codex/blacklight/silentqa/'+f;
 write(neo+p,fs.readFileSync(forge+p,'utf8').replaceAll('net/minecraftforge/fml/loading/','net/neoforged/fml/loading/').replace('Forge earlyWindowControl','NeoForge earlyWindowControl'));
}
write(neo+'src/silentQa/resources/blacklight.silent-qa.mixins.json',fs.readFileSync(forge+'src/silentQa/resources/blacklight.silent-qa.mixins.json','utf8').replace('JAVA_17','JAVA_21'));
const client='D:/CodexCache/creature-combat-qa/neo-client/',world=client+'saves/Creature Skills QA Final/';
// Only metadata from the already disposable native 1.21.1 world. Never upgrade the Forge QA save.
if(!fs.existsSync(world+'level.dat')){fs.mkdirSync(world,{recursive:true});fs.copyFileSync(neo+'run-ragdoll-qa/saves/Blacklight Ragdoll QA/level.dat',world+'level.dat');}
write(client+'config/fml.toml','earlyWindowControl=false\nversionCheck=false\nearlyWindowLogHelpMessage=false\n');
write(client+'options.txt','version:3955\nfullscreen:false\nrenderDistance:4\nsimulationDistance:5\npauseOnLostFocus:false\nmaxFps:60\nsoundCategory_master:0.0\nonboardAccessibility:false\n');
console.log('Prepared isolated native 1.21.1 hidden/muted QA sources and world metadata.');
