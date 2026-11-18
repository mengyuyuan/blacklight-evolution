import fs from 'node:fs';
import crypto from 'node:crypto';
const roots={neo:'D:/blacklight-evolution',forge:'D:/blacklight-evolution-forge-1.20.1'};
const report={status:'incoming_contact_checks_passed',scope:'Hydra current-model receivers, native picking/arrows/sword and claws/blade/hammer sessions; not full creature or original-engine acceptance',platforms:{},limitations:[
 'Receiver cuboids follow the approved Minecraft model; original regional health, armor multipliers and receiver volumes remain unverified.',
 'Visual audit covers native arrows at two scales and yaw, horn, miss, wall, immunity; weapon sessions use server integration checks. Full weapon/consume/death screenshot combinations remain pending.',
 'Does not complete Hydra object throwing, all original transitions, native audio identity, multiplayer latency or other creatures.'
]};
for(const [platform,root] of Object.entries(roots)){
 const folder=`D:/CodexCache/creature-combat-qa/${platform}-hydra-hurt-frames`,e=JSON.parse(fs.readFileSync(folder+'/evidence.json','utf8'));
 function check(v,m){if(!v)throw Error(platform+': '+m)}
 check(e.frames.length>=300,'insufficient captured frames');
 for(let scene=0;scene<6;scene++){
  const frames=e.frames.filter(f=>f.scene===scene),hits=e.hits.filter(h=>h.scene===scene),receipts=e.presentations.filter(r=>r.scene===scene);
  check(frames.length>=40,'scene frame coverage '+scene);check(hits.length===(scene<3?1:0),'hit count '+scene);check(receipts.length===(scene<3?1:0),'feedback count '+scene);
  if(scene<3){check(hits[0].damage===15&&hits[0].surfaceError<.0001,'actual body damage/point '+scene);check(receipts[0].tick===hits[0].tick&&receipts[0].kind===2&&receipts[0].soundCount===1,'one timestamped local tissue/audio receipt '+scene);}
  check(frames.every(f=>Number.isFinite(f.renderTime)&&Array.isArray(f.effects)),'render clock and visual state capture '+scene);
  const effects=frames.flatMap(f=>f.effects.map(effect=>({time:f.renderTime-effect.at,...effect})));
  if(scene>=3)check(effects.length===0,'unexpected cosmetic hit in rejected scene '+scene);
  else {
   check(effects.length>=8&&effects.every(e=>e.anchor&&e.time>=0),'missing/early bone-attached spray '+scene);
   const released=effects.filter(e=>e.time>=4);check(released.length>=3,'missing free spray tail '+scene);
   const samePoint=(a,b)=>Math.hypot(a.x-b.x,a.y-b.y,a.z-b.z)<1e-6;
   check(released.every(e=>samePoint(e.anchor.point,released[0].anchor.point)),'detached spray still follows body '+scene);
   const attached=effects.filter(e=>e.time<4);check(attached.length>=2&&attached.some(e=>!samePoint(e.anchor.point,attached[0].anchor.point)),'spray nozzle fails to follow moving receiver '+scene);
  }
 }
 check(e.frames.every(f=>Number.isFinite(f.poseError)&&f.poseError<.002),'rendered geometry mismatch');
 check(e.frames.every(f=>fs.existsSync(folder+'/'+f.image)),'missing PNG');
 check(e.presentations.every(r=>r.shown>=r.tick&&r.shown-r.tick<=2),'early/delayed feedback');
 const tests={};for(const [stage,count] of [['encounterServer',74],['hydraGateway',11]]){
  const bytes=fs.readFileSync(`D:/CodexCache/creature-combat-${platform}-${stage}.log`);const log=bytes.toString(bytes[0]===255&&bytes[1]===254||bytes[1]===0?'utf16le':'utf8');check(log.includes(`All ${count} required tests passed`),'test stage '+stage);tests[stage]=count;
 }
 const build=`D:/CodexCache/creature-combat-qa/${platform==='neo'?'neo-build':'forge-render-build'}/test-results/test`;
 let unit=0;for(const name of ['HydraContactTest','HydraMotionTest','HydraGrowthTest','ContactEffectsQueueTest','ContactEffectsPayloadTest','HydraWoundAnchorTest']){
  const file=fs.readdirSync(build).find(f=>f.endsWith('.'+name+'.xml'));check(file,'unit report '+name);const xml=fs.readFileSync(build+'/'+file,'utf8');const tag=xml.match(/<testsuite[^>]*>/)[0];check(/failures="0"/.test(tag)&&/errors="0"/.test(tag)&&/skipped="0"/.test(tag),'failed unit '+name);unit+=Number(tag.match(/tests="(\d+)"/)[1]);
 }check(unit===33,'expected 33 focused unit tests');tests.unit=unit;
 const code={};for(const file of ['combat/HydraContact.java','combat/HydraHurtContact.java','combat/HydraPicking.java','entity/HydraPart.java','entity/BlacklightHydra.java','mixin/HydraProjectileContactMixin.java','mixin/client/HydraPickClockMixin.java','combat/WeaponCutContact.java','combat/WeaponCombatSystem.java','combat/CombatTargeting.java','combat/WeaponImpact.java','infection/InfectedAttackEffects.java','client/renderer/HydraWoundAnchor.java','client/renderer/InfectedImpactVisuals.java']){
  code[file]=crypto.createHash('sha256').update(fs.readFileSync(root+'/src/main/java/com/codex/blacklight/'+file)).digest('hex');
 }
 report.platforms[platform]={tests,frames:e.frames.length,frameFolder:folder,evidenceSha256:crypto.createHash('sha256').update(fs.readFileSync(folder+'/evidence.json')).digest('hex'),maxPoseError:Math.max(...e.frames.map(f=>f.poseError)),maxSurfaceError:Math.max(...e.hits.map(h=>h.surfaceError)),maxPresentationDelay:Math.max(...e.presentations.map(r=>r.shown-r.tick)),hits:e.hits,receipts:e.presentations,code};
}
for(let scene=0;scene<3;scene++){
 const n=report.platforms.neo.hits.find(h=>h.scene===scene),f=report.platforms.forge.hits.find(h=>h.scene===scene);
 if(n.damage!==f.damage||n.bone!==f.bone)throw Error('cross-version receiver mismatch '+scene);
}
report.totalFrames=Object.values(report.platforms).reduce((a,p)=>a+p.frames,0);
for(const root of Object.values(roots))fs.writeFileSync(root+'/art/hydra/incoming-contact-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({status:report.status,frames:report.totalFrames,platforms:Object.fromEntries(Object.entries(report.platforms).map(([k,v])=>[k,{tests:v.tests,frames:v.frames,poseError:v.maxPoseError,delay:v.maxPresentationDelay}]))}));
