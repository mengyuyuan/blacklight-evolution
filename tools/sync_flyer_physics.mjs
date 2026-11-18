import fs from 'node:fs';
const a='D:/blacklight-evolution',b='D:/blacklight-evolution-forge-1.20.1',prefix='/src/main/java/com/codex/blacklight/';
function edit(path,fn){const file=b+prefix+path;fs.writeFileSync(file,fn(fs.readFileSync(file,'utf8')));}
function replace(s,from,to){if(!s.includes(from))throw new Error('Missing port anchor '+from.slice(0,90));return s.replace(from,to);}
for(const name of ['combat/FlyerPhysics.java','combat/FlyerContact.java','combat/HydraContact.java'])fs.copyFileSync(a+prefix+name,b+prefix+name);
const mob=fs.readFileSync(a+prefix+'entity/EncounterMob.java','utf8');
edit('entity/EncounterMob.java',s=>{
 s=s.replace('Vec3 origin=level().isClientSide?getPosition(partial):before.lerp(position(),alpha);','Vec3 origin=level().isClientSide?FlyerPhysics.origin(this,partial):before.lerp(position(),alpha);');
 if(!s.includes('if(FlyerPhysics.hasPose(this))origin='))s=replace(s,'        Vec3 origin=level().isClientSide?FlyerPhysics.origin(this,partial):before.lerp(position(),alpha);','        Vec3 origin=level().isClientSide?FlyerPhysics.origin(this,partial):before.lerp(position(),alpha);\n        if(FlyerPhysics.hasPose(this))origin=FlyerPhysics.origin(this,partial);');
 if(!s.includes('FLIGHT_BODY='))s=replace(s,'    private float oldFlightPitch;',mob.slice(mob.indexOf('    private float oldFlightPitch;'),mob.indexOf('    private final FlyerFlight flight=')) .trimEnd());
 if(!s.includes('flyerSourcePose('))s=replace(s,s.split('\n').find(x=>x.includes('public float[][] flyerPose(')),mob.split('\n').filter(x=>x.includes('public float[][] flyerSourcePose(')||x.includes('public float[][] flyerPose(')).join('\n'));
 s=replace(s,s.split('\n').find(x=>x.includes('public boolean sourcePoseCommitted()')),mob.split('\n').find(x=>x.includes('public boolean sourcePoseCommitted()')));
 if(!s.includes('entityData.define(FLIGHT_BODY'))s=replace(s,'entityData.define(FLIGHT_PITCH,0F);}','entityData.define(FLIGHT_PITCH,0F);entityData.define(FLIGHT_BODY,new CompoundTag());}');
 if(!s.includes('void travel(Vec3 input)'))s=replace(s,'    @Override public void tick(){',mob.slice(mob.indexOf('    @Override public void travel(Vec3 input)'),mob.indexOf('    @Override public void tick(){'))+'    @Override public void tick(){');
 if(!s.includes('float bodyYaw='))s=replace(s,'        super.tick();updateParts();if(level().isClientSide)return;',mob.slice(mob.indexOf('        super.tick();\n'),mob.indexOf('        updateBossBars();')).trimEnd());
 if(!s.includes('&&flyerPhysics.tick()'))s=replace(s,'        updateBossBars();','        updateBossBars();\n        if(kind==EncounterKind.FLYER&&flyerPhysics.tick()){flight.interruptedTick();updateParts();return;}');
 return s;
});
edit('client/renderer/BlacklightCreatureRenderer.java',s=>{if(s.includes('FlyerPhysics.origin'))return s;const n=fs.readFileSync(a+prefix+'client/renderer/BlacklightCreatureRenderer.java','utf8');const block=n.slice(n.indexOf('        if(!visual.active()&&entity instanceof'),n.indexOf('        if(entity instanceof com.codex.blacklight.entity.BlacklightHive'));return replace(s,'        if(entity instanceof com.codex.blacklight.entity.BlacklightHive',block+'        if(entity instanceof com.codex.blacklight.entity.BlacklightHive');});
edit('combat/RagdollImpact.java',s=>{if(s.includes('FlyerPhysics.ownsLive'))return s;const src=fs.readFileSync(a+prefix+'combat/RagdollImpact.java','utf8');return replace(s,'        var impulses=new java.util.ArrayList<RagdollImpactPayload.CutImpulse>();',src.slice(src.indexOf('        if(!refinement&&FlyerPhysics.ownsLive'),src.indexOf('        var impulses='))+'        var impulses=new java.util.ArrayList<RagdollImpactPayload.CutImpulse>();');});
edit('client/renderer/AdaptiveRagdoll.java',s=>{
 if(s.includes('FlyerPhysics.ownsLive'))return s;
 s=replace(s,'        var owner=renderOwner(e);if(owner!=e)return active(owner);','        var owner=renderOwner(e);if(owner!=e)return active(owner);\n        if(com.codex.blacklight.combat.FlyerPhysics.ownsLive(e))return false;');
 s=replace(s,'        reset();if(scene==null||!supports(e)||e.isInvisible())return;','        reset();if(scene==null||!supports(e)||e.isInvisible())return;\n        if(com.codex.blacklight.combat.FlyerPhysics.ownsLive(e))return;');
 return replace(s,'        if(!(entity instanceof LivingEntity e)||!supports(e)||e.isInvisible()||scene==null)return null;','        if(!(entity instanceof LivingEntity e)||!supports(e)||e.isInvisible()||scene==null)return null;\n        if(com.codex.blacklight.combat.FlyerPhysics.ownsLive(e)){BODIES.remove(e);PENDING.remove(e);}');
});
const test='/src/encounterTest/java/com/codex/blacklight/gametest/FlyerHurtGameTest.java';
let t=fs.readFileSync(b+test,'utf8');const source=fs.readFileSync(a+test,'utf8');
if(!t.includes('heavyBodyFallsWithReceiverAndAcceptsFollowup')){const start=source.indexOf('    @GameTest(template="blackwatch_facility_arena",batch="flyer_physical_receiver"'),end=source.indexOf('    @GameTest(template="blackwatch_facility_arena",batch="flyer_incoming_pose"');t=replace(t,'    @GameTest(template="blackwatch_facility_arena",batch="flyer_incoming_pose"',source.slice(start,end)+'    @GameTest(template="blackwatch_facility_arena",batch="flyer_incoming_pose"');fs.writeFileSync(b+test,t);}
{const mark='    @GameTest(template="blackwatch_facility_arena",batch="flyer_physical_receiver"',end='    @GameTest(template="blackwatch_facility_arena",batch="flyer_incoming_pose"';t=t.slice(0,t.indexOf(mark))+source.slice(source.indexOf(mark),source.indexOf(end))+t.slice(t.indexOf(end));fs.writeFileSync(b+test,t);}
fs.copyFileSync(a+'/src/test/java/com/codex/blacklight/combat/HydraContactTest.java',b+'/src/test/java/com/codex/blacklight/combat/HydraContactTest.java');
const qa='/src/flyerWeaponVisual/java/com/codex/blacklight/client/FlyerWeaponVisual.java';let q=fs.readFileSync(b+qa,'utf8');const native=fs.readFileSync(a+qa,'utf8');
if(!q.includes('private static JsonObject authoredSkin(')){q=replace(q,'            row.add("effects",effectSnapshot());frames.add(row);','            row.add("authoredSkin",authoredSkin(actor,model,partial));row.addProperty("serverBody",FlyerPhysics.hasPose(actor));\n            row.add("effects",effectSnapshot());frames.add(row);');const start=native.indexOf('    /** Native baked cube'),end=native.indexOf('    private static JsonObject physicalSnapshot');q=replace(q,'    private static JsonObject physicalSnapshot',native.slice(start,end)+'    private static JsonObject physicalSnapshot');fs.writeFileSync(b+qa,q);}
q=q.replace('if(ready&&age>100)','if(ready&&age>(index==4?130:100))');
q=q.replace('                if(FlyerPhysics.hasPose(actor))expected=expected.add(FlyerPhysics.origin(actor,partial).subtract(actor.getPosition(partial)));\n','');
const skinStart=native.indexOf('    /** Native baked cube'),skinEnd=native.indexOf('    private static JsonObject physicalSnapshot');q=q.slice(0,q.indexOf('    /** Native baked cube'))+native.slice(skinStart,skinEnd)+q.slice(q.indexOf('    private static JsonObject physicalSnapshot'));
q=q.replaceAll('software.bernie.geckolib.util.RenderUtil.','software.bernie.geckolib.util.RenderUtils.');fs.writeFileSync(b+qa,q);
console.log('Ported shared body, receiver hooks and regression additions without replacing platform APIs');
