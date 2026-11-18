import fs from 'node:fs';
const neo='D:/blacklight-evolution',forge='D:/blacklight-evolution-forge-1.20.1';
const base='/src/main/java/com/codex/blacklight/client/animation/';
const adapt=s=>s.replace(/\b(\w+)\.getData\(ModAttachments\.(\w+)\)/g,'ModAttachments.getData($1,ModAttachments.$2)').replaceAll('software.bernie.geckolib.util.RenderUtil.','software.bernie.geckolib.util.RenderUtils.');
fs.writeFileSync(forge+base+'FlyerCarryPose.java',adapt(fs.readFileSync(neo+base+'FlyerCarryPose.java','utf8')));
const combat='/src/main/java/com/codex/blacklight/combat/';
fs.copyFileSync(neo+combat+'FlyerReceiverPose.java',forge+combat+'FlyerReceiverPose.java');
{
 const path=combat+'ConsumePlacementTrack.java';const n=fs.readFileSync(neo+path,'utf8');let f=fs.readFileSync(forge+path,'utf8');
 const key='    private static Vec3 carryContact(LivingEntity victim,String key,float t,double[] frame) {';
 const a=n.indexOf(key)+key.length,b=n.indexOf('        double scale=',a);const block=n.slice(a,b);
 const fa=f.indexOf(key)+key.length,fb=f.indexOf('        double scale=',fa);f=f.slice(0,fa)+block+f.slice(fb);
 for(const key of ['            Vec3 center=new Vec3(p[10],p[11],p[12]).scale(scale);','            double distance=gap.horizontalDistance(),minimum=.34+victim.getBbWidth()*.5;']){
  const line=n.slice(n.indexOf(key)+key.length).split('\n')[1];
  if(!f.includes(line))f=f.replace(key,key+'\n'+line);
 }
 fs.writeFileSync(forge+path,f);
}
for(const [name,start,end] of [
 ['NativeGeckoConsume.java','        if(entity instanceof com.codex.blacklight.entity.EncounterMob mob&&mob.kind()', '        var form='],
 ['GrabHoldPose.java','        if(mob instanceof com.codex.blacklight.entity.EncounterMob flyer&&flyer.kind()', '        var desired='],
 ['GrabHoldPose.java','        // The blade is rigidly drawn on Hand_R;', '\n    }'],
 ['BlacklightBoneIk.java','    /** Fit a rigid forearm mesh,', '    /** Exact two-segment contact.']
]) {
 const n=fs.readFileSync(neo+base+name,'utf8');let f=fs.readFileSync(forge+base+name,'utf8');
 const addition=n.slice(n.indexOf(start),n.indexOf(end,n.indexOf(start)));
 if(f.includes(start))f=f.slice(0,f.indexOf(start))+adapt(addition)+f.slice(f.indexOf(end,f.indexOf(start)));
 else f=f.replace(end,adapt(addition)+end);
 fs.writeFileSync(forge+base+name,f);
}
const path='/src/flyerHandoffVisual/java/com/codex/blacklight/client/FlyerHandoffVisual.java';
const n=fs.readFileSync(neo+path,'utf8');let f=fs.readFileSync(forge+path,'utf8');
for(const [start,end] of [['    private static void lethalStance(', '    private static void stance('],['    private static JsonObject authoredSkin(', '    private static JsonObject physicalSnapshot(']]){
 const addition=adapt(n.slice(n.indexOf(start),n.indexOf(end,n.indexOf(start))));
 if(f.includes(start))f=f.slice(0,f.indexOf(start))+addition+f.slice(f.indexOf(end,f.indexOf(start)));
 else f=f.replace(end,addition+end);
}
if(!f.includes('                lethalStance(actor,p);'))f=f.replace('            if(index==4){','            if(index==4){\n                lethalStance(actor,p);');
f=f.replaceAll('index==4?160:280','index==4?220:280').replace('if(age==68||index==4&&age==105){','if(age==68){');
f=f.replace('actor.setHealth(300);','actor.setHealth(index==4?50:300);').replace('if(index==4)actor.setHealth(20);','if(index==4&&!ConsumeLastChance.used(actor))throw new IllegalStateException("Hammer must naturally spend the last-chance save before the lethal blade");');
f=f.replaceAll('ModAttachments.getData(actor,ModAttachments.CONSUME_VISUAL)','ModAttachments.peekData(actor,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY)').replaceAll('ModAttachments.getData(actor,ModAttachments.HIT_REACTION)','ModAttachments.peekData(actor,ModAttachments.HIT_REACTION,HitReaction.EMPTY)').replace('"stunned",ImpactControl.stunned(actor)','"stunned",!actor.isRemoved()&&ImpactControl.stunned(actor)');
fs.writeFileSync(forge+path,f);
const probeBase='/src/flyerHandoffVisual/';
for(const file of ['java/com/codex/blacklight/client/NativeGripProbe.java','java/com/codex/blacklight/client/qagrip/NativeGripProbeMixin.java','resources/blacklight.grip-qa.mixins.json']){
 const dest=forge+probeBase+file;fs.mkdirSync(dest.slice(0,dest.lastIndexOf('/')),{recursive:true});
 let source=adapt(fs.readFileSync(neo+probeBase+file,'utf8')).replace('JAVA_21','JAVA_17');
 if(file.endsWith('/NativeGripProbe.java'))source=source.replace('        var camera=Minecraft.getInstance()',
  '        // Forge entity stacks contain the view rotation; return both probes to world space.\n        var inverseView=com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix();inverseView.transform(drawn);inverseView.transform(direct);\n        var camera=Minecraft.getInstance()');
 fs.writeFileSync(dest,source);
}
f=fs.readFileSync(forge+path,'utf8');if(!f.includes('NativeGripProbe.reset()'))f=f.replace('ParticleProbe.reset();','ParticleProbe.reset();NativeGripProbe.reset();');
if(!f.includes('out.addProperty("hurtFlash"'))f=f.replace('var out=new JsonObject();var f=com.codex.blacklight.client.renderer.AdaptiveRagdoll.class','var out=new JsonObject();out.addProperty("hurtFlash",com.codex.blacklight.client.animation.RagdollDrive.hurtFlash(actor));var f=com.codex.blacklight.client.renderer.AdaptiveRagdoll.class');
fs.writeFileSync(forge+path,f);
fs.copyFileSync(neo+'/tools/review_flyer_handoff.py',forge+'/tools/review_flyer_handoff.py');
for(const file of ['tools/flyer-handoff-test.init.gradle','src/main/java/com/codex/blacklight/client/renderer/RagdollPoseHistory.java','src/test/java/com/codex/blacklight/client/renderer/RagdollPoseHistoryTest.java','src/test/java/com/codex/blacklight/client/animation/BlacklightBoneIkTest.java','src/main/java/com/codex/blacklight/client/animation/HurtFlashWindow.java','src/test/java/com/codex/blacklight/client/animation/HurtFlashWindowTest.java']){
 let s=fs.readFileSync(neo+'/'+file,'utf8');if(file.endsWith('.java'))s=s.replace(/poses\.getLast\(\)/g,'poses.get(poses.size()-1)').replace(/poses\.getFirst\(\)/g,'poses.get(0)').replace(/poses\.removeFirst\(\)/g,'poses.remove(0)');
 fs.writeFileSync(forge+'/'+file,s);
}
console.log('Synchronized flyer receiver/grip and native audit changes.');
