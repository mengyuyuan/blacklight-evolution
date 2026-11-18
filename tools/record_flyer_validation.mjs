import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const roots=['D:/blacklight-evolution/','D:/blacklight-evolution-forge-1.20.1/'];
const read=p=>fs.readFileSync(p,'utf8');const json=p=>JSON.parse(read(p));
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const log=p=>{const b=fs.readFileSync(p);return b[0]===255&&b[1]===254?b.toString('utf16le'):b.toString('utf8');};
const report={status:'flyer_dive_and_contact_presentation_validated',scope:'Outgoing dive, source flight/hurt curves, terrain contact and accepted-hit presentation. Not a full original-game or whole-species completion claim.',platforms:[]};
const files=['src/main/java/com/codex/blacklight/entity/EncounterMob.java','src/main/java/com/codex/blacklight/combat/FlyerFlight.java','src/main/java/com/codex/blacklight/combat/FlyerMotion.java','src/main/java/com/codex/blacklight/combat/FlyerContact.java','src/main/java/com/codex/blacklight/client/model/FlyerPose.java','src/main/java/com/codex/blacklight/client/renderer/FlyerContactPresentation.java','src/main/java/com/codex/blacklight/combat/ContactEffectsQueue.java','src/main/java/com/codex/blacklight/client/renderer/ContactEffectsPresentation.java','src/main/resources/assets/blacklight/models/encounter_flyer_source.json','src/main/resources/assets/blacklight/geo/flyer_mc.geo.json'];
for(const [i,platform] of ['neo','forge'].entries()){
 const root=roots[i],folder=`D:/CodexCache/creature-combat-qa/${platform}-flyer-frames/`,e=json(folder+'evidence.json');
 const server=log(`D:/CodexCache/creature-combat-${platform}-encounterServer.log`);assert.match(server,/89 GAME TESTS COMPLETE/);assert.match(server,/All 89 required tests passed/);
 for(const stage of ['encounterUnit','hydraUnit','flyerVisual'])assert.match(log(`D:/CodexCache/creature-combat-${platform}-${stage}.log`),/BUILD SUCCESSFUL/);
 assert(e.frames.length>=750);assert(Math.max(...e.frames.map(f=>f.boneError))<.001);
 for(let scene=0;scene<6;scene++){
  const frames=e.frames.filter(f=>f.scene===scene),end=e.events.find(x=>x.scene===scene&&x.name==='end');assert(frames.length>=90);assert(end);
  assert.equal(end.hits,scene===0||scene===4?1:0);assert(Math.abs(end.health-(scene===0||scene===4?1991.95:2000))<.03);
  assert.equal(end.collisions,scene===1||scene===5?1:0);assert.equal(end.stage,0);
  for(const f of frames)assert(fs.existsSync(folder+f.image));
  assert(fs.existsSync(folder+`flyer-review-${scene}.jpg`));
 }
 assert.equal(e.hits.length,2);
 for(const p of e.presentations){assert(p.shown>=p.tick);assert(p.shown-p.tick<=6.5);assert(p.soundCount>0);
  if(p.kind===1){const f=e.frames.find(f=>f.image===p.firstImage);assert(f.contactDistance<=.22,`${platform} presentation precedes mesh contact: ${f.contactDistance}`);}
 }
 assert(e.presentations.some(p=>p.scene===1&&p.kind===5));assert(e.presentations.some(p=>p.scene===5&&p.kind===0&&p.surfaceCount===48));
 assert(!e.presentations.some(p=>p.scene===2||p.scene===3));
 const build=`D:/CodexCache/creature-combat-qa/${platform==='neo'?'neo-build':'forge-render-build'}/test-results/test/`;
 const results=fs.readdirSync(build).filter(n=>n.startsWith('TEST-')&&n.endsWith('.xml')).map(n=>{let s=read(build+n);return {name:n,tests:Number(/<testsuite[^>]* tests="(\d+)"/.exec(s)?.[1]),failures:Number(/<testsuite[^>]* failures="(\d+)"/.exec(s)?.[1]),errors:Number(/<testsuite[^>]* errors="(\d+)"/.exec(s)?.[1])};});
 assert(results.every(r=>r.failures===0&&r.errors===0));
 report.platforms.push({platform,nativeFrames:e.frames.length,maxNativeBoneError:Math.max(...e.frames.map(f=>f.boneError)),maxPresentationDelayTicks:Math.max(...e.presentations.map(p=>p.shown-p.tick)),maxBodyContactPresentationDistance:Math.max(...e.presentations.filter(p=>p.kind===1).map(p=>e.frames.find(f=>f.image===p.firstImage).contactDistance)),encounterGameTests:89,encounterUnitCases:24,focusedUnit:results,hits:e.hits,receipts:e.presentations,frameManifestSha256:hash(folder+'evidence.json'),reviewSheets:Array.from({length:6},(_,n)=>folder+`flyer-review-${n}.jpg`),files:files.map(f=>({file:f,sha256:hash(root+f)}))});
}
const source=json(roots[0]+'art/encounters/flyer-source-motion.json');assert.equal(hash(source.source),source.sourceSha256);
const fig=json('D:/PrototypeReference/p2/creature-fig-structure.json');
report.source={...source,controllerRecords:fig.records.filter(r=>r.file==='input/p2/flyer.p3d'&&source.controllerOffsets.includes(r.offset)).map(r=>({offset:r.offset,type:r.type,kind:r.kind,owner:r.owner,length:r.length,references:r.references.filter(x=>x.name.startsWith('flyer_'))}))};
assert.equal(report.source.controllerRecords.length,3);
report.visualReview={reviewedSheets:12,imagesPerSheet:16,scenes:['accepted body hit','wall blocks target','side dodge after commitment','incoming damage interrupts dive','ground spawn takeoff','actual terrain impact'],observations:['Body contact has a continuous approach, hit and pullout; tissue and pressure effects appear at the reached target.','Wall collision interrupts movement and uses a local material effect; the protected target keeps its health.','Dodged and interrupted dives produce no successful outgoing contact effect.','Ground-spawn flight lifts into the attack; raised terrain produces grounded debris before pullout.'],scope:'Manual temporal contact-sheet review plus numeric bone/contact checks on every retained frame. Not an audit of every 60-FPS output frame or audio timbre.'};
report.boundaries=['Opaque original dive-controller fields remain unresolved; speed, commitment, range, damage multiplier, orbit height and recovery are MC tuning.','Birth-from-pustule clip is exported but not used for ordinary egg spawning.','Incoming wing-edge picking/weapon contact, flying ragdoll/consume frame matrix, multiplayer latency and large mod-model matrix still require dedicated acceptance.','Presentation queue verifies dispatch timing and geometry; it does not replace audio waveform/timbre listening. Vanilla hurt tint can arrive before the additional gated contact effect.','Native frames sample 1–2 images per game tick. Shared bone transforms are checked in every retained frame, with reviewed contact sheets; this is not a claim of viewing every 60-FPS display frame.'];
for(const root of roots)fs.writeFileSync(root+'art/encounters/flyer-dive-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,frames:p.nativeFrames,boneError:p.maxNativeBoneError,delay:p.maxPresentationDelayTicks,distance:p.maxBodyContactPresentationDistance,server:p.encounterGameTests,unit:p.encounterUnitCases,focused:p.focusedUnit.reduce((n,r)=>n+r.tests,0)}))));
