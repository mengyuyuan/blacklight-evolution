import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const read=f=>JSON.parse(fs.readFileSync(f,'utf8'));
const sha=f=>crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');
const sources=['src/main/java/com/codex/blacklight/client/animation/HammerGroundContact.java','src/main/java/com/codex/blacklight/client/animation/BlacklightRenderPose.java','src/main/java/com/codex/blacklight/client/renderer/WeaponScaleRenderer.java','src/main/java/com/codex/blacklight/combat/WeaponAttackScales.java','art/motion-profile/hammer_quake.json','art/motion-profile/hammer_quake_charged.json','src/test/java/com/codex/blacklight/client/animation/HammerGroundContactTest.java','src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAudit.java','src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAuditActorFloor.java'];
const baseline='D:/CodexCache/creature-combat-qa/neo-hammer-floor-before/hammer_brute_survive.json';
function measures(rows){
 const quake=rows.filter(r=>r.actorClip==='hammer_quake_charged'&&r.actorHammerLowest?.['0']!==undefined);
 const recovery=quake.filter(r=>r.actorClipSeconds>=1.65);
 assert(recovery.length>25);
 return {samples:recovery.length,fistLow:Math.min(...recovery.flatMap(r=>Object.values(r.actorHammerLowest))),headLow:Math.min(...recovery.map(r=>r.actorSkinLowest['1'])),strikeFistLow:Math.min(...quake.filter(r=>r.actorClipSeconds<=1.45).flatMap(r=>Object.values(r.actorHammerLowest))),entityHeightRange:[Math.min(...quake.map(r=>r.actorPosition[1])),Math.max(...quake.map(r=>r.actorPosition[1]))]};
}
const before=measures(read(baseline));assert(before.fistLow< -1);
const report={date:new Date().toISOString(),scope:'Grounded hammer-quake recovery using scaled mesh envelopes. Strike timing, hip and leg pose are preserved; full attack ground contact and the complete creature goal are not accepted.',baseline:{file:baseline,sha256:sha(baseline),measurements:before},platforms:[],pending:[
 'Contact/strike frames precede the recovery constraint and remain unchanged; any measured below-floor strike geometry requires a shared server/client contact solution.',
 'Existing native skin feet can extend below their entity origin; the correction intentionally does not translate or alter the planted lower-body animation.',
 'Steps, slopes, multiplayer latency, modded player skins, audible mixing and all other creature/model/military requirements remain outside this focused recovery check.'
]};
for(const [i,platform] of ['neo','forge'].entries()){
 const root=roots[i],dir=`D:/CodexCache/creature-combat-qa/${platform}-hammer-floor-after/`;
 const checks=read(dir+'case-checks.json');assert.deepEqual(checks.map(c=>c.case),['hammer_brute_survive','hammer_brute_lethal']);
 assert(fs.readFileSync(dir+'capture-status.txt','utf8').startsWith('Completed 2 cases. Dropped frames=0.'));
 const latest=Math.max(...sources.map(p=>fs.statSync(root+'/'+p).mtimeMs));
 const cases=checks.map(check=>{
  const path=dir+check.case+'.json',rows=read(path),sheet=dir+check.case+'-review.jpg';
  assert(check.passed&&check.actorRecoveryFrames>25&&check.actorRecoveryHeadClearance>.25&&check.actorRecoveryFistClearance>=-.02);
  assert(fs.statSync(path).mtimeMs>latest&&fs.statSync(sheet).mtimeMs>=fs.statSync(path).mtimeMs);
  assert(rows.length>150);for(const r of rows)assert(r.imageQueued&&fs.existsSync(dir+r.image));
  const measured=measures(rows);assert(measured.entityHeightRange[1]-measured.entityHeightRange[0]<.0001);
  if(check.case.endsWith('_lethal'))assert(check.minHealth===0&&check.bodyInstances===1&&check.maxComponents===1&&check.renderedMeshPenetrations===0);
  else assert(check.minHealth>0&&check.minHealth<400);
  return {name:check.case,frames:rows.length,check,measurements:measured,file:path,sha256:sha(path),sheet:{file:sheet,sha256:sha(sheet)}};
 });
 const unit=`D:/CodexCache/creature-combat-qa/${platform==='neo'?'neo-build':'forge-render-build'}/test-results/test/TEST-com.codex.blacklight.client.animation.HammerGroundContactTest.xml`;
 const xml=fs.readFileSync(unit,'utf8');assert(xml.includes('tests="3"')&&xml.includes('failures="0"')&&xml.includes('errors="0"'));
 const log=`D:/CodexCache/creature-combat-${platform}-genericRagdollVisual.log`;assert(fs.readFileSync(log,'utf8').includes('BUILD SUCCESSFUL'));
 report.platforms.push({platform,frames:cases.reduce((n,c)=>n+c.frames,0),cases,unit:{tests:3,file:unit,sha256:sha(unit)},log:{file:log,sha256:sha(log)},sources:sources.map(file=>({file,sha256:sha(root+'/'+file)}))});
}
report.review={status:process.argv.includes('--reviewed')?'four_native_keyframe_sheets_reviewed':'pending',sheets:process.argv.includes('--reviewed')?4:0,everyFrameManuallyReviewed:false,fullAttackGroundContactAccepted:false};
for(const root of roots)fs.writeFileSync(root+'/art/mutant-combat/hammer-floor-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,frames:p.frames,cases:p.cases.map(c=>({name:c.name,...c.measurements}))}))));
