// Bounded Brute acceptance: current runtime, native matrices, contact events and reviewed sheets.
import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const runtime=[
 'src/main/java/com/codex/blacklight/combat/BruteContact.java',
 'src/main/java/com/codex/blacklight/combat/BruteRecovery.java',
 'src/main/java/com/codex/blacklight/combat/BruteFlinch.java',
 'src/main/java/com/codex/blacklight/combat/ImpactControl.java',
 'src/main/java/com/codex/blacklight/client/animation/BruteHitPose.java',
 'src/main/java/com/codex/blacklight/combat/MutantMove.java',
 'src/main/java/com/codex/blacklight/combat/HydraContact.java',
 'src/main/java/com/codex/blacklight/combat/CreaturePoseTable.java',
 'src/main/java/com/codex/blacklight/entity/BlacklightMutant.java',
 'src/main/java/com/codex/blacklight/infection/InfectedAttackEffects.java',
 'src/main/java/com/codex/blacklight/registry/ModEntities.java',
 'src/main/java/com/codex/blacklight/client/model/BlacklightCreatureModel.java',
 'src/main/java/com/codex/blacklight/client/renderer/ContactEffectsPresentation.java',
 'src/main/java/com/codex/blacklight/client/renderer/BruteContactPresentation.java',
 'src/main/resources/assets/blacklight/geo/blacklight_brute_mc.geo.json',
 'src/main/resources/assets/blacklight/models/brute_combat_motion.json',
 'src/main/resources/assets/blacklight/animations/blacklight_brute.animation.json'
];
const tests=[
 'src/test/java/com/codex/blacklight/combat/BruteContactTest.java',
 'src/test/java/com/codex/blacklight/combat/BruteFlinchTest.java',
 'src/test/java/com/codex/blacklight/combat/MutantCombatTest.java',
 'src/bruteTest/java/com/codex/blacklight/gametest/BruteContactGameTest.java',
 'src/bruteVisual/java/com/codex/blacklight/client/BruteVisual.java',
 'src/creatureCombatTest/java/com/codex/blacklight/gametest/CreatureSkillsGameTest.java',
 'tools/brute-test.init.gradle','tools/brute-visual.init.gradle','tools/review_brute.py','tools/build_mutant_combat.mjs','tools/articulate_brute.mjs','tools/detail_brute_model.mjs'
];
const report={date:new Date().toISOString(),scope:'Authored Minecraft Brute: swept right-fist hammer, supported two-fist slam and ground wave, committed upper-body charge, terrain interruption/retraction, standing weapon-hit reactions and immediate attack cancellation, existing spatial recorded cues.',platforms:[],pending:[
 'The authored anatomy now has separate pectorals, scapulae, maw, fingers, thumbs, knees and toes using the existing atlas. The block silhouette and material palette still need final visual acceptance; fingers and knees have no independent animation channels. Guard/knockdown/death/consume choreography, narrow-space recovery and complete combat pacing remain open.',
 'Muted captures verify sound dispatch, position and deduplication, not listening acceptance or crowd mixing.',
 'Standing Brute weapon reactions and attack interruption are covered separately. Knockdowns, lethal impacts, consumption, directional camera/attacker combinations and every constrained-space transition remain outside this acceptance.',
 'The fixed first-person review camera crops the displaced target in dodge cases and the top of one airborne windup. Their event/matrix checks remain available; these sheets do not accept full-shot framing or complete aerial choreography.',
 'Earlier captures showed the spectator observer head after delayed Prototype-mode sync changed the QA camera to third person. QA now holds first person and records the detached-camera flag. These checks do not establish general rendering acceptance.',
 'Moving-player/shield/mod targets, multiplayer latency and every terrain configuration are not accepted. Other creatures, Blackwatch soldiers/helicopters and detailed vehicle items remain in the active goal.'
]};
for(const [i,platform] of ['neo','forge'].entries()){
 const root=roots[i],dir=`D:/CodexCache/creature-combat-qa/${platform}-brute-frames/`,manifest=dir+'evidence.json',e=read(manifest);
 const model=read(root+'/art/mutant-combat/brute-model.json'),geometry=read(root+'/src/main/resources/assets/blacklight/geo/blacklight_brute_mc.geo.json')['minecraft:geometry'][0];
 assert.equal(model.geometry,hash(root+'/src/main/resources/assets/blacklight/geo/blacklight_brute_mc.geo.json'));
 assert.equal(model.texture,hash(root+'/src/main/resources/assets/blacklight/textures/entity/blacklight_brute_mc.png'));
 const contactParts=geometry.bones.filter(b=>['torso','head','right_arm','left_arm','right_forearm','left_forearm'].includes(b.name)).flatMap(b=>b.cubes.map((c,cube)=>`${b.name}:${cube}`));
 assert.equal(contactParts.length,model.contactCuboids);
 const latestRuntime=Math.max(...runtime.map(f=>fs.statSync(root+'/'+f).mtimeMs));
 const latestServer=Math.max(...runtime.filter(f=>!f.includes('/client/')).map(f=>fs.statSync(root+'/'+f).mtimeMs));
 assert(fs.statSync(manifest).mtimeMs>Math.max(latestRuntime,fs.statSync(root+'/src/bruteVisual/java/com/codex/blacklight/client/BruteVisual.java').mtimeMs),'Native capture predates runtime/probe');
 assert(fs.readFileSync(dir+'status.txt','utf8').startsWith('Captured'));
 const logs=[];
 for(const [stage,count] of [['bruteServer',19],['server',19],['stalkerServer',11],['spitterServer',10]]){
  const file=`D:/CodexCache/creature-combat-${platform}-${stage}.log`,log=fs.readFileSync(file,'utf8');
  assert(log.includes(`All ${count} required tests passed`)&&log.includes('BUILD SUCCESSFUL'),platform+'/'+stage);
  assert(fs.statSync(file).mtimeMs>latestServer,platform+'/'+stage+' predates server source');
  logs.push({stage,count,file,sha256:hash(file)});
 }
 const build=platform==='neo'?'neo-build':'forge-render-build';let units=0;const unitEvidence=[];
 for(const suite of ['BruteContactTest','BruteFlinchTest','StalkerContactTest','MutantCombatTest','SpitterMouthTest','ContactEffectsQueueTest']){
  const file=`D:/CodexCache/creature-combat-qa/${build}/test-results/test/TEST-com.codex.blacklight.combat.${suite}.xml`,xml=fs.readFileSync(file,'utf8');
  assert(/failures="0"/.test(xml)&&/errors="0"/.test(xml)&&/skipped="0"/.test(xml));
  assert(fs.statSync(file).mtimeMs>latestServer,'Unit evidence predates tested common code');
  units+=+xml.match(/tests="(\d+)"/)[1];unitEvidence.push({file,sha256:hash(file)});
 }assert.equal(units,37);
 const cases=[];
 for(let scene=0;scene<10;scene++){
  const frames=e.frames.filter(f=>f.scene===scene),hits=e.hits.filter(h=>h.scene===scene),sounds=e.sounds.filter(s=>s.scene===scene),timeline=e.timeline.filter(t=>t.scene===scene),receipts=e.presentations.filter(p=>p.scene===scene);
  assert(frames.length>=65);assert(frames.every(f=>f.cameraDetached===false),'QA observer stays first person');assert(timeline.at(-1).age>=82);for(const f of frames)assert(fs.existsSync(dir+f.image));
  const samples=frames.filter(f=>Number.isFinite(f.bodyError));assert(samples.length>=20,'Native pose samples');
  for(const f of samples)assert.deepEqual(f.parts.map(p=>`${p.bone}:${p.cube}`),contactParts,'Every new upper-body cube has native pose evidence');const error=Math.max(...samples.map(f=>f.bodyError));assert(error<.0001);
  assert.equal(hits.length,[1,1,1,0,0,0,1,0,0,0][scene]);
  if(scene===0)assert(hits[0].bone==='right_forearm'&&hits[0].contactAge>=16&&hits[0].contactAge<=21);
  if(scene===2)assert(hits[0].contactAge>=12&&hits[0].contactAge<=26);
  if(scene===1||scene===6)assert(hits[0].bone==='ground_wave');
  assert.equal(sounds.filter(s=>s.event==='blacklight:infection.tension').length,1);
  assert.deepEqual(sounds.filter(s=>s.event==='blacklight:infection.swing').map(s=>s.age),scene===5?[]:[scene===0||scene===8?14:scene>=2&&scene<=4?10:18]);
  const tissue=receipts.filter(p=>p.kind===1),ground=receipts.filter(p=>p.kind===0),obstacle=receipts.filter(p=>p.kind===5);
  assert.equal(tissue.length,hits.length);assert(tissue.every(p=>p.soundCount===1));
  if(scene===0||scene===2){assert(tissue[0].bodyGapAtPresentation<=.205);assert(tissue[0].shown-tissue[0].tick<6,'No presentation timeout');}
  assert.equal(ground.length,scene===1||scene===6?1:0);assert.equal(obstacle.length,scene===4||scene===5?1:0);
  if(ground.length){
   assert(ground[0].surfaceCount>0&&ground[0].soundCount===2);const first=timeline.find(t=>t.groundImpact);
   assert.equal(first.age,scene===1?26:24);assert(Math.abs(first.groundImpact.y-(scene===1?81:81.5))<.0001);assert(first.onGround);
   const recovery=samples.filter(f=>f.groundRecovery);assert(recovery.length>=20,'Native ground recovery samples');
   assert(samples.every(f=>f.floorOverlap===false),'No upper-body floor penetration');
   if(scene===6)assert(samples.filter(f=>f.age>=12).every(f=>f.slabOverlap===false),'No upper-body slab penetration');
   assert(recovery.some(f=>f.recoveryElapsed>20),'Recovery completes the lowering phase');
   assert(recovery.flatMap(f=>f.parts).filter(p=>p.bone.endsWith('_forearm')).every(p=>p.drawn.y<83.3),'No repeated overhead windup');
  }
  if(obstacle.length){assert(obstacle[0].surfaceCount>0&&obstacle[0].soundCount===2);assert.equal(timeline.at(-1).walls,1);}
  if(scene===4){assert(samples.every(f=>f.wallOverlap===false),'Visible upper body remains outside wall');assert(timeline.every(t=>t.actor.z+.45<=4.001));}
  if(scene===7)assert(Math.max(...timeline.map(t=>t.actor.y))>84,'Airborne slam fixture');
  if(scene===9)assert(timeline.filter(t=>t.age>20).every(t=>t.move==='NONE'),'NoAI cancels damage');
  assert.equal(timeline.at(-1).health,1000-hits.reduce((n,h)=>n+h.damage,0));
  const sheet=dir+`brute-review-${scene}.jpg`;assert(fs.statSync(sheet).mtimeMs>=fs.statSync(manifest).mtimeMs,'Regenerate sheets for latest capture');
  cases.push({scene,frames:frames.length,bodySamples:samples.length,recoverySamples:samples.filter(f=>f.groundRecovery).length,maxNativeBodyError:error,hits,receipts,sounds,last:timeline.at(-1),sheet:{file:sheet,sha256:hash(sheet)}});
 }
 const audio=[],bank=read(root+'/src/main/resources/assets/blacklight/sounds.json');
 for(const name of ['tension','swing','ground','crush'])for(const entry of bank['infection.'+name].sounds){const file='src/main/resources/assets/blacklight/sounds/'+entry.name.replace('blacklight:','')+'.ogg';assert(fs.statSync(root+'/'+file).size>1000);audio.push({file,sha256:hash(root+'/'+file)});}
 const gallery=[];for(let scene=10;scene<15;scene++){
  const f=e.frames.find(f=>f.scene===scene);assert(f&&f.cameraDetached===false&&f.age===20,'Current model gallery viewpoint');
  gallery.push({scene,file:dir+f.image,sha256:hash(dir+f.image)});
 }
 const incomingCases=[];
 for(let scene=15;scene<20;scene++){
  const frames=e.frames.filter(f=>f.scene===scene),timeline=e.timeline.filter(t=>t.scene===scene),hits=e.hits.filter(h=>h.scene===scene),first=[12,18,16,25,12][scene-15];
  assert(frames.length>=65&&frames.every(f=>!f.cameraDetached));
  const samples=frames.filter(f=>Number.isFinite(f.bodyError));assert(samples.length>=60);
  assert(samples.every(f=>f.floorOverlap===false),'Incoming-hit recovery clears the floor');
  if(scene===18)assert(samples.filter(f=>f.age>=12).every(f=>f.slabOverlap===false),platform+'/incoming: interrupted ground recovery clears the slab');
  for(const f of samples)assert.deepEqual(f.parts.map(p=>`${p.bone}:${p.cube}`),contactParts);
  const error=Math.max(...samples.map(f=>f.bodyError));assert(error<.0001,'Native standing reaction matches rendered geometry');
  assert(samples.filter(f=>f.hitWeight>0).length>=4,'Visible accepted incoming-hit window');
  assert(timeline.every(t=>t.noAi===false),'Real control, not NoAI fixture');
  assert(timeline.filter(t=>t.age>=first).every(t=>t.move==='NONE'),'Immediate attack cancellation');
  assert(timeline.at(-1).actorHealth<timeline[0].actorHealth,'Actual incoming damage');
  assert(timeline.at(-1).stunned===false,'Control expires');
  assert.equal(hits.length,scene===18?1:0);if(hits.length)assert(hits[0].age<first&&hits[0].bone==='ground_wave');
  const sheet=dir+`brute-review-${scene}.jpg`;assert(fs.statSync(sheet).mtimeMs>=fs.statSync(manifest).mtimeMs);
  incomingCases.push({scene,frames:frames.length,nativeSamples:samples.length,maxNativeBodyError:error,healthBefore:timeline[0].actorHealth,healthAfter:timeline.at(-1).actorHealth,hits,sheet:{file:sheet,sha256:hash(sheet)}});
 }
 report.platforms.push({platform,frames:e.frames.length,units,logs,unitEvidence,cases,incomingCases,gallery,model:{file:'art/mutant-combat/brute-model.json',sha256:hash(root+'/art/mutant-combat/brute-model.json'),cuboids:model.cuboids,contactCuboids:model.contactCuboids},audio,manifest:{file:manifest,sha256:hash(manifest)},sources:[...runtime,...tests].map(file=>({file,sha256:hash(root+'/'+file)}))});
}
report.boundary='Existing rig cuboids are transformed with the same pose table as the renderer and swept against native target bounding boxes. Slam waves use supported feet, actual surface height and line-of-sight, sharing the fist hit ledger. This is not arbitrary skin-triangle collision or verified original-game species mapping. Native bone centers are independently sampled after rendering. Only charge tissue/audio waits at most six ticks for the visible body; server damage and vanilla hurt flash are unchanged. No release package was produced.';
report.visualReview=process.argv.includes('--reviewed')?{status:'current_combat_and_incoming_hit_keyframes_reviewed_with_open_visual_issues',reviewedScenes:[0,1,2,4,6,9,15,16,17,18,19],reviewedSheets:22,framesPerSheet:8,modelGeometryUnchangedFromPriorReview:true,everyCapturedFrameManuallyReviewed:false}:{status:'pending'};
for(const root of roots)fs.writeFileSync(root+'/art/mutant-combat/brute-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,frames:p.frames,units:p.units,serverTests:p.logs.map(l=>l.count),maxNativeBodyError:Math.max(...p.cases.map(c=>c.maxNativeBodyError))}))));
