// Assert the bounded native scenarios, retaining the exact source/evidence hashes.
import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const distance=(a,b)=>Math.hypot(a.x-b.x,a.y-b.y,a.z-b.z);
const report={date:new Date().toISOString(),scope:'Authored Minecraft spitter: animated mouth, committed physical shots, wall interception, spatial splash audio and safe retreat.',platforms:[],pending:['Species-specific hurt/death/consume choreography, silhouette/detail and audible balance in crowds remain open','Player/network/mod receiver matrix and original-game equivalence are not accepted','Other creatures, soldiers, helicopters and detailed vehicle items remain part of the active goal']};
const files=['src/main/java/com/codex/blacklight/combat/SpitterMouth.java','src/main/java/com/codex/blacklight/entity/BlacklightMutant.java','src/main/java/com/codex/blacklight/entity/BlacklightSpit.java','src/main/java/com/codex/blacklight/infection/InfectedAttackEffects.java','src/main/java/com/codex/blacklight/registry/ModInfectedSounds.java','src/main/resources/assets/blacklight/geo/blacklight_spitter_mc.geo.json','src/main/resources/assets/blacklight/models/spitter_combat_motion.json','src/test/java/com/codex/blacklight/combat/SpitterMouthTest.java','src/spitterTest/java/com/codex/blacklight/gametest/SpitterContactGameTest.java','src/spitterVisual/java/com/codex/blacklight/client/SpitterVisual.java'];
for(const [i,platform] of ['neo','forge'].entries()){
 const root=roots[i],dir=`D:/CodexCache/creature-combat-qa/${platform}-spitter-frames/`,manifest=dir+'evidence.json',e=read(manifest);
 assert(fs.statSync(manifest).mtimeMs>Math.max(...files.map(f=>fs.statSync(root+'/'+f).mtimeMs)),'Capture predates sources');
 const serverLog=`D:/CodexCache/creature-combat-${platform}-spitterServer.log`,log=fs.readFileSync(serverLog,'utf8');
 assert(log.includes('All 10 required tests passed'));assert(log.includes('BUILD SUCCESSFUL'));
 const build=platform==='neo'?'neo-build':'forge-render-build';let units=0;
 for(const suite of ['SpitterMouthTest','MutantCombatTest','BlockThrowBallisticsTest']){
  const xml=fs.readFileSync(`D:/CodexCache/creature-combat-qa/${build}/test-results/test/TEST-com.codex.blacklight.combat.${suite}.xml`,'utf8');
  assert(/failures="0"/.test(xml)&&/errors="0"/.test(xml)&&/skipped="0"/.test(xml));units+=+xml.match(/tests="(\d+)"/)[1];
 }assert.equal(units,14);
 const cases=[];
 for(let scene=0;scene<7;scene++){
  const frames=e.frames.filter(f=>f.scene===scene),shots=e.shots.filter(s=>s.scene===scene),sounds=e.sounds.filter(s=>s.scene===scene),timeline=e.timeline.filter(t=>t.scene===scene);
  assert(frames.length>=55);assert(timeline.at(-1).age>=62);
  for(const f of frames)assert(fs.existsSync(dir+f.image));
  const socket=frames.filter(f=>Number.isFinite(f.mouthError));assert(socket.length>=20);const mouthError=Math.max(...socket.map(f=>f.mouthError));assert(mouthError<.0001,`${platform}/${scene} native socket ${mouthError}`);
  const expected=scene<1||scene===3?[14]:scene<3?[14,24,34]:[];assert.deepEqual(shots.map(s=>s.age),expected);assert(shots.every(s=>s.mouthError<1e-5));
  const launch=sounds.filter(s=>s.event==='blacklight:infection.launch'),burst=sounds.filter(s=>s.event==='blacklight:infection.burst');assert.deepEqual(launch.map(s=>s.age),expected);
  launch.forEach((s,i)=>assert(distance(s.at,shots[i].at)<1e-6));
  assert.equal(sounds.filter(s=>s.event==='blacklight:infection.tension').length,1);
  assert.equal(burst.length,scene===4?1:shots.length,'one splash per actual physical impact, no duplicate accepted-hit audio');
  if(scene<3){assert(timeline.at(-1).health<1000);for(const s of burst){assert(s.at.y>81.1);assert(Math.abs((scene===2?s.at.x:s.at.z)-(scene===2?-8.05:9.05))<.02,'splash on target front face');}}
  else assert.equal(timeline.at(-1).health,1000);
  if(scene===3)assert(Math.abs(burst[0].at.z-4)<1e-6,'wall face splash');
  if(scene===4){assert.equal(burst[0].age,14);assert(Math.abs(burst[0].at.z-1)<1e-6,'obstructed nozzle splash');}
  if(scene>=5){assert(timeline.every(t=>Math.abs(t.actor.y-81)<.01));if(scene===5)assert(timeline.at(-1).actor.z< -1);else assert(timeline.at(-1).actor.z>=0&&timeline.at(-1).actor.z<.5);}
  cases.push({scene,frames:frames.length,socketSamples:socket.length,maxNativeSocketError:mouthError,releaseTicks:shots.map(s=>s.age),sounds: sounds.map(s=>({event:s.event,age:s.age,at:s.at})),last:timeline.at(-1),sheet:{file:dir+`spitter-review-${scene}.jpg`,sha256:hash(dir+`spitter-review-${scene}.jpg`)}});
 }
 const bank=read(root+'/src/main/resources/assets/blacklight/sounds.json'),audio=[];
 for(const name of ['tension','launch','burst']){
  const entries=bank['infection.'+name].sounds;assert.equal(entries.length,3);
  for(const s of entries){const path=root+'/src/main/resources/assets/blacklight/sounds/'+s.name.replace('blacklight:','')+'.ogg';assert(fs.statSync(path).size>1000);audio.push({file:path.slice(root.length+1),sha256:hash(path)});}
 }
 report.platforms.push({platform,frames:e.frames.length,units,serverTests:10,cases,audio,manifest:{file:manifest,sha256:hash(manifest)},server:{file:serverLog,sha256:hash(serverLog)},sources:files.map(file=>({file,sha256:hash(root+'/'+file)}))});
}
report.measurementBoundary='Socket points use the current authored head/torso table and two locators on the existing nozzle. Native head matrices are sampled after rendering, correcting Gecko RenderUtil matrix addition and its current-position origin to the interpolated draw origin. Entity impacts intersect the native target bounding box, not a claim of arbitrary skin-triangle collision. Sound events verify spatial dispatch/timing/duplication; hidden clients were muted and this is not a listening acceptance.';
report.visualReview=process.argv.includes('--reviewed')?{status:'fourteen_keyframe_sheets_reviewed',framesPerSheet:8,everyCapturedFrameManuallyReviewed:false}:{status:'pending'};
for(const root of roots)fs.writeFileSync(root+'/art/mutant-combat/spitter-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,frames:p.frames,units:p.units,server:p.serverTests,maxNativeSocketError:Math.max(...p.cases.map(c=>c.maxNativeSocketError))}))));
