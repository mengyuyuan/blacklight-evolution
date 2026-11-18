import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const roots=['D:/blacklight-evolution/','D:/blacklight-evolution-forge-1.20.1/'];
const read=p=>fs.readFileSync(p,'utf8'),json=p=>JSON.parse(read(p));
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const log=p=>{const b=fs.readFileSync(p);return b[0]===255&&b[1]===254?b.toString('utf16le'):b.toString('utf8');};
const report={status:'animated_source_pose_receiver_validated_physical_takeover_pending',scope:'Native picking, arrows, ordinary sword and server Prototype weapon sessions against authored flyer receiving geometry. Projectile-driven native visual matrix. This is not full species acceptance.',platforms:[]};
const files=['combat/AnimatedHurtBody.java','combat/FlyerContact.java','combat/HydraHurtContact.java','combat/CombatTargeting.java','combat/WeaponCutContact.java','combat/WeaponCombatSystem.java','combat/WeaponImpact.java','combat/HeavyBlow.java','combat/HitStop.java','combat/ClawCombatSystem.java','entity/EncounterMob.java','entity/FlyerPart.java','entity/BlacklightHydra.java','mixin/HydraProjectileContactMixin.java','infection/InfectedAttackEffects.java','client/renderer/HydraWoundAnchor.java','client/renderer/InfectedImpactVisuals.java'];
for(const [i,platform] of ['neo','forge'].entries()){
 const root=roots[i],folder=`D:/CodexCache/creature-combat-qa/${platform}-flyer-hurt-frames/`,e=json(folder+'evidence.json');
 assert.match(log(`D:/CodexCache/creature-combat-${platform}-encounterServer.log`),/All 97 required tests passed/);
 for(const stage of ['hydraUnit','flyerHurtVisual','flyerVisual'])assert.match(log(`D:/CodexCache/creature-combat-${platform}-${stage}.log`),/BUILD SUCCESSFUL/);
 assert(read(folder+'status.txt').startsWith('Captured')||read(folder+'status.txt').startsWith('Validated'));
 assert(e.frames.length>=320);assert.equal(e.hits.length,3);assert.equal(e.presentations.length,3);
 assert(Math.max(...e.frames.map(f=>f.poseError))<.001);
 for(let scene=0;scene<6;scene++){
  const frames=e.frames.filter(f=>f.scene===scene);assert(frames.length>=50);
  const hits=e.hits.filter(f=>f.scene===scene),fx=e.presentations.filter(f=>f.scene===scene);
  assert.equal(hits.length,scene<3?1:0);assert.equal(fx.length,scene<3?1:0);
  if(scene<3){assert.equal(hits[0].damage,60);assert(hits[0].surfaceError<.0001);assert(fx[0].shown>=fx[0].tick&&fx[0].shown-fx[0].tick<=6);assert(fx[0].soundCount===1);assert(frames.some(f=>f.effects.some(v=>v.anchor!=null)));}
  assert(frames.at(-1).health>0);for(const f of frames)assert(fs.existsSync(folder+f.image));
  assert(fs.existsSync(folder+`incoming-review-${scene}.jpg`));
 }
 const outgoing=json(`D:/CodexCache/creature-combat-qa/${platform}-flyer-frames/evidence.json`);
 assert(outgoing.frames.length>=750&&Math.max(...outgoing.frames.map(f=>f.boneError))<.001);
 for(let scene=0;scene<6;scene++){
  const end=outgoing.events.find(v=>v.scene===scene&&v.name==='end');assert(end);
  assert.equal(end.hits,scene===0||scene===4?1:0);assert.equal(end.collisions,scene===1||scene===5?1:0);assert.equal(end.stage,0);
 }
 for(const p of outgoing.presentations){assert(p.shown>=p.tick&&p.shown-p.tick<=6.5);if(p.kind===1)assert(outgoing.frames.find(f=>f.image===p.firstImage).contactDistance<=.22);}
 const build=`D:/CodexCache/creature-combat-qa/${platform==='neo'?'neo-build':'forge-render-build'}/test-results/test/`;
 const tests=fs.readdirSync(build).filter(n=>n.startsWith('TEST-')&&n.endsWith('.xml')).map(n=>{const s=read(build+n);return {name:n,tests:Number(/<testsuite[^>]* tests="(\d+)"/.exec(s)?.[1]),failures:Number(/<testsuite[^>]* failures="(\d+)"/.exec(s)?.[1]),errors:Number(/<testsuite[^>]* errors="(\d+)"/.exec(s)?.[1])};});
 assert(tests.every(t=>t.failures===0&&t.errors===0));assert.equal(tests.reduce((n,t)=>n+t.tests,0),35);
 report.platforms.push({platform,gameTests:97,focusedUnitTests:tests,receiverFrames:e.frames.length,outgoingRegressionFrames:outgoing.frames.length,maxReceiverBoneError:Math.max(...e.frames.map(f=>f.poseError)),maxOutgoingBoneError:Math.max(...outgoing.frames.map(f=>f.boneError)),hits:e.hits,receipts:e.presentations,maxEffectDelayTicks:Math.max(...e.presentations.map(p=>p.shown-p.tick)),nativeManifestSha256:hash(folder+'evidence.json'),outgoingManifestSha256:hash(`D:/CodexCache/creature-combat-qa/${platform}-flyer-frames/evidence.json`),reviewSheets:Array.from({length:6},(_,n)=>folder+`incoming-review-${n}.jpg`),files:files.map(f=>({file:'src/main/java/com/codex/blacklight/'+f,sha256:hash(root+'src/main/java/com/codex/blacklight/'+f)}))});
}
report.sourceBoundary='The receiving surfaces are the existing authored Minecraft mesh driven by verified P2 flight/hurt curves. They are not a decoded original P2 regional-health table. Every region shares parent health, armor, immunity and death.';
report.remaining=['Paired native frames for real claw/blade/hammer impacts, especially the transition from source flight to generic live ragdoll/knockdown. The server weapon sessions are tested; this visual combination is not yet accepted.','Server/client contact agreement during physical takeover, grab, consume and lethal mesh cutting remains to be completed. Added proxies alone do not prove these matrices.','Multiplayer latency, crowded-flight performance, legacy weapon path and mod-body matrices still require acceptance.','Original incoming sound-patch identification, waveform and timbre; current proof covers contact dispatch timing only.'];
for(const root of roots)fs.writeFileSync(root+'art/encounters/flyer-receiver-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,server:p.gameTests,unit:35,receiverFrames:p.receiverFrames,outgoingFrames:p.outgoingRegressionFrames,error:p.maxReceiverBoneError,delay:p.maxEffectDelayTicks}))));
