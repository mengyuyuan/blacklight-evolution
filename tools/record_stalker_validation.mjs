// Bounded acceptance record. Source, native evidence and sheets are retained by hash.
import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const files=[
 'src/main/java/com/codex/blacklight/combat/StalkerContact.java',
 'src/main/java/com/codex/blacklight/combat/MutantMove.java',
 'src/main/java/com/codex/blacklight/entity/BlacklightMutant.java',
 'src/main/java/com/codex/blacklight/infection/InfectedAttackEffects.java',
 'src/main/java/com/codex/blacklight/registry/ModEntities.java',
 'src/main/java/com/codex/blacklight/client/renderer/ContactEffectsPresentation.java',
 'src/main/java/com/codex/blacklight/client/renderer/StalkerContactPresentation.java',
 'src/main/resources/assets/blacklight/geo/blacklight_stalker_mc.geo.json',
 'src/main/resources/assets/blacklight/models/stalker_combat_motion.json',
 'src/main/resources/assets/blacklight/animations/blacklight_stalker.animation.json',
 'src/stalkerVisual/java/com/codex/blacklight/client/StalkerVisual.java'
];
const tests=['src/test/java/com/codex/blacklight/combat/StalkerContactTest.java','src/stalkerTest/java/com/codex/blacklight/gametest/StalkerContactGameTest.java','src/creatureCombatTest/java/com/codex/blacklight/gametest/CreatureSkillsGameTest.java'];
const report={date:new Date().toISOString(),scope:'Authored Minecraft stalker: swept sharp digits, separate left/right blows, real pounce flight, collision/landing, spatial audio and bounded client presentation.',platforms:[],pending:[
 'Existing low-detail mesh is retained. Species silhouette/detail, hurt/death/consume choreography and full combat pacing remain open. Wall stopping uses the entity hull and damage uses line traces; visible claw/limb recoil against cover is still open.',
 'Muted native clients verify dispatch, positions and counts, not listening acceptance or crowd mix.',
 'An earlier Forge broad Brute charge-wall check failed without detail; an instrumented rerun passed. This intermittent check remains a Brute follow-up, not evidence of complete Brute acceptance.',
 'Multiplayer latency, player/mod target matrix, vanilla hurt-flash timing and all-terrain fidelity are not accepted. The NoAI fixture tests attack cancellation; vanilla NoAI freezes subsequent motion and it does not validate a falling/recovery animation.',
 'Other creatures, Blackwatch soldiers/helicopters and detailed vehicle items remain in the active goal.'
]};
for(const [i,platform] of ['neo','forge'].entries()){
 const root=roots[i],dir=`D:/CodexCache/creature-combat-qa/${platform}-stalker-frames/`,manifest=dir+'evidence.json',e=read(manifest);
 assert(fs.statSync(manifest).mtimeMs>Math.max(...files.map(f=>fs.statSync(root+'/'+f).mtimeMs)),'Native capture predates runtime/probe');
 assert(fs.readFileSync(dir+'status.txt','utf8').startsWith('Captured'));
 const logs=[];
 for(const [stage,count] of [['stalkerServer',11],['server',19],['spitterServer',10]]){
  const file=`D:/CodexCache/creature-combat-${platform}-${stage}.log`,log=fs.readFileSync(file,'utf8');
  assert(log.includes(`All ${count} required tests passed`)&&log.includes('BUILD SUCCESSFUL'),platform+'/'+stage);
  logs.push({stage,count,file,sha256:hash(file)});
 }
 const build=platform==='neo'?'neo-build':'forge-render-build';let units=0;const unitEvidence=[];
 for(const suite of ['StalkerContactTest','MutantCombatTest','SpitterMouthTest','ContactEffectsQueueTest']){
  const file=`D:/CodexCache/creature-combat-qa/${build}/test-results/test/TEST-com.codex.blacklight.combat.${suite}.xml`,xml=fs.readFileSync(file,'utf8');
  assert(/failures="0"/.test(xml)&&/errors="0"/.test(xml)&&/skipped="0"/.test(xml));units+=+xml.match(/tests="(\d+)"/)[1];unitEvidence.push({file,sha256:hash(file)});
 }assert.equal(units,25);
 const cases=[];
 for(let scene=0;scene<8;scene++){
  const frames=e.frames.filter(f=>f.scene===scene),hits=e.hits.filter(h=>h.scene===scene),sounds=e.sounds.filter(s=>s.scene===scene),timeline=e.timeline.filter(t=>t.scene===scene),receipts=e.presentations.filter(p=>p.scene===scene);
  assert(frames.length>=55);assert(timeline.at(-1).age>=62);for(const f of frames)assert(fs.existsSync(dir+f.image));
  const sharp=frames.filter(f=>Number.isFinite(f.clawError));assert(sharp.length>=(scene===7?12:20),'Native pose samples before cancellation');const error=Math.max(...sharp.map(f=>f.clawError));assert(error<.0001);
  assert.equal(hits.length,[1,2,1,0,0,0,0,0][scene]);
  if(scene===0)assert(hits[0].bone==='right_arm'&&hits[0].contactAge>=11&&hits[0].contactAge<=15);
  if(scene===1){assert.deepEqual(hits.map(h=>h.bone),['right_arm','left_arm']);assert(hits[0].contactAge>=9&&hits[0].contactAge<=13);assert(hits[1].contactAge>=23&&hits[1].contactAge<=27);}
  if(scene===2)assert(hits[0].contactAge>=14&&hits[0].contactAge<=22);
  assert.equal(sounds.filter(s=>s.event==='blacklight:infection.tension').length,1);
  assert.deepEqual(sounds.filter(s=>s.event==='blacklight:infection.swing').map(s=>s.age),scene===1?[7,21]:[2,3,4,7].includes(scene)?[12]:[9]);
  const tissue=receipts.filter(p=>p.kind===2),ground=receipts.filter(p=>p.kind===0),obstacle=receipts.filter(p=>p.kind===5);
  assert.equal(tissue.length,hits.length);assert(tissue.every(p=>p.soundCount===1));
  if(scene===2){assert(tissue[0].clawGapAtPresentation<=.205,'Pounce feedback meets displayed claw');assert(tissue[0].shown-tissue[0].tick<6,'No timeout fallback in this capture');}
  if(scene<2)assert(tissue.every(p=>p.shown-p.tick<2),'Stationary swipe feedback must not wait for a missed past pose');
  assert.equal(ground.length,[2,3,4].includes(scene)?1:0);assert.equal(obstacle.length,scene===4?1:0);
  if([2,3,4,7].includes(scene))assert(Math.max(...timeline.map(t=>t.actor.y))>82,'Real flight');
  if(ground.length){assert(ground[0].surfaceCount>0&&ground[0].soundCount===2);const landing=timeline.find(t=>t.groundImpact);assert(landing.onGround&&Math.abs(landing.actor.y-landing.groundImpact.y)<.001);}
  if(scene>=3)assert.equal(timeline.at(-1).health,1000);
  if(scene===4)assert(timeline.every(t=>t.actor.z+.45<=3.001),'No wall tunnelling');
  if(scene===7)assert(timeline.filter(t=>t.age>13).every(t=>t.move==='NONE'),'NoAI cancels future contacts');
  cases.push({scene,frames:frames.length,sharpSamples:sharp.length,maxNativeSharpError:error,hits,receipts,sounds,last:timeline.at(-1),sheet:{file:dir+`stalker-review-${scene}.jpg`,sha256:hash(dir+`stalker-review-${scene}.jpg`)}});
 }
 const audio=[],bank=read(root+'/src/main/resources/assets/blacklight/sounds.json');
 for(const name of ['tension','swing','tear','ground','crush'])for(const entry of bank['infection.'+name].sounds){const file='src/main/resources/assets/blacklight/sounds/'+entry.name.replace('blacklight:','')+'.ogg';assert(fs.statSync(root+'/'+file).size>1000);audio.push({file,sha256:hash(root+'/'+file)});}
 report.platforms.push({platform,frames:e.frames.length,units,logs,unitEvidence,cases,audio,manifest:{file:manifest,sha256:hash(manifest)},sources:[...files,...tests].map(file=>({file,sha256:hash(root+'/'+file)}))});
}
report.boundary='Four sharp digit cuboids are transformed from the existing authored rig and swept against native target bounding boxes. This is not arbitrary skin-triangle collision or verified original-game species mapping. Native bone matrices are independently sampled after rendering, correcting Gecko matrix addition/current-position origin. Pounce tissue/audio waits at most six ticks for the visible claws; server damage and vanilla hurt flash are unchanged. No release package was produced.';
report.visualReview=process.argv.includes('--reviewed')?{status:'sixteen_keyframe_sheets_reviewed',framesPerSheet:8,everyCapturedFrameManuallyReviewed:false}:{status:'pending'};
for(const root of roots)fs.writeFileSync(root+'/art/mutant-combat/stalker-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,frames:p.frames,units:p.units,serverTests:p.logs.map(l=>l.count),maxNativeSharpError:Math.max(...p.cases.map(c=>c.maxNativeSharpError))}))));
