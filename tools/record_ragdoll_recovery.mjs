import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const roots={neo:'D:/blacklight-evolution',forge:'D:/blacklight-evolution-forge-1.20.1'};
const sources=['src/main/java/com/codex/blacklight/client/renderer/AdaptiveRagdoll.java','src/main/java/com/codex/blacklight/client/renderer/RagdollRecovery.java','src/main/java/com/codex/blacklight/client/renderer/RagdollTerrainSupport.java','src/test/java/com/codex/blacklight/client/renderer/RagdollRecoveryTest.java','src/test/java/com/codex/blacklight/client/renderer/RagdollTerrainSupportTest.java','src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAudit.java'];
const common=['hammer_cow_connected_survive','hammer_infected_connected_survive','hammer_tissue_host_pig_connected_survive'];
const report={status:'bounded_live_recovery_and_connected_wall_contact_checkpoint',wholeGoalComplete:false,releasePackaged:false,recordedAt:new Date().toISOString(),platforms:{},totalFrames:0};
for(const [platform,repo] of Object.entries(roots)){
 const modified=Math.max(...sources.slice(0,3).map(p=>fs.statSync(repo+'/'+p).mtimeMs));
 const expected=platform==='neo'?[...common,'hammer_wilden_connected_survive']:common;
 const runs={};
 for(const terrain of ['flat','steps']){
  const directory=`D:/CodexCache/creature-combat-qa/recovery-after-${platform}`+(terrain==='steps'?'/steps':'');
  const checks=JSON.parse(fs.readFileSync(directory+'/case-checks.json'));
  assert.deepEqual(checks.map(r=>r.case).sort(),[...expected].sort(),platform+' '+terrain+' cases');
  assert(fs.statSync(directory+'/case-checks.json').mtimeMs>=modified,'run predates current production code');
  assert(checks.every(r=>r.passed&&r.recoveryPassed),platform+' '+terrain+' has failed checks');
  runs[terrain]={directory,cases:checks.map(check=>{
   const file=directory+'/'+check.case+'.json',rows=JSON.parse(fs.readFileSync(file));
   assert(rows.every(r=>r.imageQueued&&fs.existsSync(directory+'/'+r.image)),'missing native frames');
   assert(rows.at(-1).recoveryRenderOffset.every(v=>Math.abs(v)<1e-8),'native recovery offset did not settle');
   const nativeDepth=Math.max(0,...rows.map(r=>r.nativeTerrainPenetration||0));
   report.totalFrames+=rows.length;
   return {...check,frames:rows.length,nativePoseMaxTerrainPenetration:nativeDepth,evidenceSha256:hash(file)};
  })};
 }
 const testDirectory=`D:/CodexCache/creature-combat-qa/${platform==='neo'?'neo-build':'forge-render-build'}/test-results/test`;
 const units=fs.readdirSync(testDirectory).filter(n=>n.startsWith('TEST-')&&n.endsWith('.xml')).map(n=>{
  const file=testDirectory+'/'+n,s=fs.readFileSync(file,'utf8');
  assert(fs.statSync(file).mtimeMs>=modified,'unit results predate code');
  assert(/failures="0"/.test(s)&&/errors="0"/.test(s),'failed unit suite');
  return {file:n,tests:+s.match(/<testsuite[^>]+tests="(\d+)"/)[1],sha256:hash(file)};
 });
 assert(units.some(u=>u.file.includes('RagdollRecoveryTest')));
 report.platforms[platform]={runs,units,sources:Object.fromEntries(sources.map(p=>[p,hash(repo+'/'+p)]))};
}
report.visualReview={status:process.argv.includes('--reviewed')?'transition_keyframes_reviewed':'pending',manualReviewOfEveryFrame:false};
report.before={directory:'D:/CodexCache/creature-combat-qa/recovery-before-forge/steps',lastPoseErrorBlocks:.14067891956834577,lastPoseAngleRadians:.14800474185676132};
report.fix=['Finite recovery follows current animated joint anchors.','Small support offsets pass to the native renderer and decay over eight ticks.','Connected component skin is checked after joint projection to correct side-wall penetration.'];
report.measurement='Ground checks use actual simulated and drawn skin vertices, not empty corners of rotated bounding boxes. Handoff error compares the last physical pose to the first native pose including the transferred support offset. Native-pose terrain overlap is retained separately; a third-party model can itself extend into a slab.';
report.remaining=['Native Wilden idle geometry overlaps the fixture slab; the handoff smooths its support offset without changing the external mod or server position.','Component separation is bounded to 0.75 blocks per projection, six iterations, 256 collision boxes and a local volume budget. This is not continuous swept collision proof or acceptance of every enclosed/large/fast model.','Full creature behavior, paired combat, multiplayer, military AI, refined models and audio acceptance remain active.'];
for(const root of Object.values(roots))fs.writeFileSync(root+'/art/encounters/ragdoll-recovery-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({frames:report.totalFrames,platforms:Object.fromEntries(Object.entries(report.platforms).map(([p,r])=>[p,{cases:Object.values(r.runs).reduce((a,b)=>a+b.cases.length,0),units:r.units.reduce((a,b)=>a+b.tests,0)}])),visualReview:report.visualReview}));
