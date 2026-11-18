import fs from 'node:fs';import crypto from 'node:crypto';
const check=(v,m)=>{if(!v)throw Error(m);},sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const report={status:'object_throw_contact_checks_passed',date:new Date().toISOString(),scope:'Hydra concrete pickup, held telegraph, throw, oriented-cube impacts; gateway and prior encounter regression. This is not full creature-system acceptance.',platforms:{}};
for(const platform of ['neo','forge']){
 const root=platform==='neo'?'D:/blacklight-evolution':'D:/blacklight-evolution-forge-1.20.1';
 const folder=`D:/CodexCache/creature-combat-qa/${platform}-hydra-throw-frames`,e=JSON.parse(fs.readFileSync(folder+'/evidence.json','utf8'));
 check(e.frames.length>=400,'insufficient native frames '+platform);
 check(e.frames.every(f=>Number.isFinite(f.gripError)&&f.gripError<.003&&fs.existsSync(folder+'/'+f.image)),'native grip mismatch or missing PNG '+platform);
 for(let scene=0;scene<3;scene++){
  const events=e.events.filter(r=>r.scene===scene),frames=e.frames.filter(r=>r.scene===scene),receipts=e.presentations.filter(r=>r.scene===scene);
  check(frames.length>=100,'scene frame count');
  const held=frames.filter(f=>f.cargoGripError!==undefined);check(held.length>20&&held.every(f=>f.cargoGripError<.003),'carried prop detached from rendered claws');
  check(events.filter(r=>r.name==='pickup_ground').length===1&&events.find(r=>r.name==='pickup_ground').age<54,'pickup support contact');
  check(events.filter(r=>r.name==='attach').length===1&&events.find(r=>r.name==='attach').age===54,'source spawn time');
  check(events.filter(r=>r.name==='release').length===(scene===2?0:1),'release count');
  if(scene!==2)check(events.find(r=>r.name==='release').actionAge===26,'release clock');
  check(Math.abs(events.find(r=>r.name==='end').health-(scene===0?1980.4:2000))<.02,'accepted damage count');
  check(receipts.filter(r=>r.kind===0).length===1&&receipts.find(r=>r.kind===0).surfaceCount>0,'material ground feedback');
  check(receipts.filter(r=>r.kind===1).length===(scene===0?1:0)&&receipts.every(r=>r.kind!==2),'heavy object must use crush feedback');
  check(receipts.filter(r=>r.kind===5).length===(scene===1?1:0),'wall impact receipt');
  check(receipts.every(r=>r.soundCount>0&&r.shown>=r.tick&&r.shown-r.tick<=2),'early/late/silent contact receipt');
 }
 check(e.hits.length===1&&e.hits[0].scene===0&&Math.abs(e.hits[0].damage-19.6)<.01,'damage report');
 const tests={};for(const [stage,count]of [['encounterServer',80],['hydraGateway',11]]){
  const bytes=fs.readFileSync(`D:/CodexCache/creature-combat-${platform}-${stage}.log`),log=bytes.toString(bytes[1]===0||bytes[0]===255&&bytes[1]===254?'utf16le':'utf8');
  check(log.includes(`All ${count} required tests passed`),stage+' failed '+platform);tests[stage]=count;
 }
 const build=`D:/CodexCache/creature-combat-qa/${platform==='neo'?'neo-build':'forge-render-build'}/test-results/test`;let unit=0;
 for(const name of ['HydraContactTest','HydraMotionTest','HydraGrowthTest','ContactEffectsQueueTest','ContactEffectsPayloadTest','HydraWoundAnchorTest']){
  const file=fs.readdirSync(build).find(f=>f.endsWith('.'+name+'.xml'));check(file,'unit report '+name);const xml=fs.readFileSync(build+'/'+file,'utf8'),tag=xml.match(/<testsuite[^>]*>/)[0];check(/failures="0"/.test(tag)&&/errors="0"/.test(tag)&&/skipped="0"/.test(tag),'unit failed '+name);unit+=Number(tag.match(/tests="(\d+)"/)[1]);
 }check(unit===33,'unit count');tests.unit=unit;
 const code={};for(const name of ['combat/HydraThrow.java','entity/HydraDebris.java','entity/BlacklightHydra.java','city/HydraMotion.java','client/renderer/HydraDebrisRenderer.java','infection/InfectedAttackStyle.java'])code[name]=sha(root+'/src/main/java/com/codex/blacklight/'+name);
 report.platforms[platform]={tests,frameFolder:folder,frames:e.frames.length,maxGripError:Math.max(...e.frames.map(f=>f.gripError)),maxPresentationDelay:Math.max(...e.presentations.map(r=>r.shown-r.tick)),evidenceSha256:sha(folder+'/evidence.json'),events:e.events,hits:e.hits,receipts:e.presentations,code};
 for(const f of ['models/hydra_motion.json','animations/blacklight_hydra.animation.json'])check(sha('D:/blacklight-evolution/src/main/resources/assets/blacklight/'+f)===sha('D:/blacklight-evolution-forge-1.20.1/src/main/resources/assets/blacklight/'+f),'resource parity '+f);
}
report.boundaries=['Throw release at 1.26667 s uses a source Spawn cue whose branch index resolution is still inferred.','Original prop templates, collision engine, target-selection tree and sound waveforms are not claimed identical.','MC uses a supported-ground visual prop, no block removal/items, 32-block range and attribute-scaled damage.','Native pose metrics cover every captured frame; review sheets sample pickup, carry, release, impact and cancellation. Full mod roster and full game-system acceptance remain pending.'];
for(const root of ['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'])fs.writeFileSync(root+'/art/hydra/throw-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(Object.fromEntries(Object.entries(report.platforms).map(([p,r])=>[p,{tests:r.tests,frames:r.frames,maxGripError:r.maxGripError,maxPresentationDelay:r.maxPresentationDelay}]))));
