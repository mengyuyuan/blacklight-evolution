import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';

const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const qa='D:/CodexCache/creature-combat-qa';
const read=file=>JSON.parse(fs.readFileSync(file,'utf8'));
const sha=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const artifact=file=>({file,sha256:sha(file)});
const brute=['thrown_brute_connected_survive','thrown_brute_step_connected_survive','hammer_brute_survive','blade_brute_lethal','muscle_brute_consume','claws_brute_consume'];
const generic=['cow','infected','tissue_host_pig'];
const sources=[
 'src/main/java/com/codex/blacklight/client/renderer/AdaptiveRagdoll.java',
 'src/main/java/com/codex/blacklight/client/renderer/RagdollTerrainSupport.java',
 'src/main/java/com/codex/blacklight/client/renderer/RagdollHinge.java',
 'src/main/java/com/codex/blacklight/client/renderer/RagdollRecovery.java',
 'src/main/java/com/codex/blacklight/combat/BruteKnockdown.java',
 'src/main/java/com/codex/blacklight/combat/BruteContact.java',
 'src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAudit.java',
 'src/test/java/com/codex/blacklight/client/renderer/RagdollTerrainSupportTest.java',
 'tools/brute-test.init.gradle'
];
function rise(rows){
 let samples=0,lift=0,side=0,planted=0,handGap=0;
 for(const r of rows){
  if(r.hitKind!==6||!r.pieces?.length)continue;
  const age=r.gameTime+r.partial-r.hitStarted;
  if(age>=8){const p=r.pieces[0];samples++;lift=Math.max(lift,p.renderPos[1]-p.target[1]);side=Math.max(side,Math.hypot(p.renderPos[0]-p.target[0],p.renderPos[2]-p.target[2]));}
  const progress=age/r.hitDuration;
  if(progress>=.4&&progress<=.78){const hands=r.pieces.filter(p=>p.bone?.endsWith('_forearm'));if(hands.length===2){planted++;handGap=Math.max(handGap,...hands.map(p=>Math.abs(p.renderLowestY-251)));}}
 }
 assert(samples>25&&planted>12);
 return {authoredSamples:samples,maxSharedLift:lift,maxSharedHorizontalShift:side,plantedSamples:planted,maxHandFloorGap:handGap};
}
const report={recordedAt:new Date().toISOString(),status:'bounded-native-regression',wholeGoalComplete:false,releasePackaged:false,
 scope:'Connected ragdoll skin support at a slab edge, including Brute get-up. This is not final animation or model acceptance.',
 changes:[
  'Resolve shallow sideways skin overlap before vertical support, preserving existing lower-floor contact when a bounded sideways exit exists. Build actual connected skin before applying a shared component translation; oversized/budget-limited components retain their conservative support fallback.',
  'Compute each escape from the skin clipped to the obstacle footprint perpendicular to that axis. Raised limbs or low limbs away from a step no longer enlarge the escape.',
  'Merge exactly rectangular adjacent coplanar floor/slab tiles before solving. Internal block seams cannot serve as false exits; nonrectangular unions retain empty corners.',
  'Once pose recovery completes, apply the same support projection to the physical native-follow pose before rendering and handoff. This preserves the existing render-to-native release offset without leaving raw parts below the floor.',
  'Native Brute checks cover shared lift after the eight-tick pose blend and both hands plus lower-body support during the planted rise window. New generic step cases use actual grab/throw packets and a pig projectile, followed by native recovery.'
 ],platforms:[],diagnostics:[],limits:[
  'The correction can still translate the whole connected body sideways. It is not per-limb terrain IK and does not eliminate early recovery sliding or establish natural body weight.',
  'Arbitrary terrain, enclosed/trapped rigs, large-component fallback, network latency and full multi-species combat remain unverified. Native penetration assertions sample mesh vertices; unit tests exercise clipped faces.',
  'The previous charged-hammer stair fixture did not enter ragdoll because the attack encountered terrain before damaging the stationary victim. Those failed recordings remain diagnostic evidence. This change does not repair or accept charged-hammer terrain choreography; new throw fixtures isolate shared ragdoll support.',
  'Selected twenty-frame contact sheets are reviewed, not every captured frame. The clients are muted; no audible mix acceptance is claimed.',
  'No new server regression is claimed for this client-only production change. Historical server reports describe their own earlier source snapshots.',
  'The larger active goal still includes species models and choreography, soldier AI/helicopters, fine vehicle/item modeling and sounds. No commit or release.'
 ]};
for(const [index,platform] of ['neo','forge'].entries()){
 const root=roots[index],dir=`${qa}/${platform}-ragdoll-edge-contact-final`;
 const bodies=index===0?[...generic,'wilden']:generic;
 const expected=[...brute,...bodies.map(n=>`hammer_${n}_connected_survive`),...bodies.map(n=>`thrown_${n}_step_connected_survive`)];
 const checks=read(dir+'/case-checks.json');assert.deepEqual(checks.map(c=>c.case).sort(),expected.sort());
 assert(fs.readFileSync(dir+'/capture-status.txt','utf8').startsWith(`Completed ${expected.length} cases. Dropped frames=0.`));
 for(const f of ['unit-check.log','native-build.log'])assert(fs.readFileSync(dir+'/'+f,'utf8').includes('BUILD SUCCESSFUL'));
 const xmls=fs.readdirSync(dir).filter(f=>/^TEST-.*\.xml$/.test(f));let units=0;
 for(const f of xmls){const tag=fs.readFileSync(dir+'/'+f,'utf8').match(/<testsuite\b[^>]+>/)[0];units+=Number(tag.match(/\btests="(\d+)"/)[1]);for(const attr of ['failures','errors'])assert.equal(Number(tag.match(new RegExp(`\\b${attr}="(\\d+)"`))[1]),0);}
 assert.equal(units,82);
 const reviewed=read(dir+'/reviewed-sheets.json');assert.deepEqual([...reviewed.cases].sort(),expected);assert.equal(reviewed.everyCapturedFrameManuallyReviewed,false);
 const latest=Math.max(...sources.filter(s=>s.startsWith('src/main/')||s.startsWith('src/ragdollVisualTest/')).map(s=>fs.statSync(root+'/'+s).mtimeMs));
 let frames=0;const flows=[];
 for(const check of checks){
  assert(check.passed,platform+'/'+check.case);const file=dir+'/'+check.case+'.json',rows=read(file),sheet=dir+'/'+check.case+'-review.jpg';
  assert(rows.length>150);assert(fs.statSync(file).mtimeMs>latest);assert.equal(sha(sheet),reviewed.sheets[check.case]);
  for(const r of rows)assert(r.imageQueued&&fs.existsSync(dir+'/'+r.image));
  if(check.case.includes('_connected_'))assert(check.jointSamples>10&&check.recoveryPassed&&check.maxRenderJointGap<.001);
  if(check.case.includes('_step_'))assert(check.terrainFrames>10&&check.maxTerrainPenetration<=.02);
  if(check.case.startsWith('thrown_brute'))assert(check.getupTerrainLift<.025&&check.getupHandFloorGap<.02&&check.getupLowerBodyFloorGap<.02);
  frames+=rows.length;flows.push({case:check.case,frames:rows.length,checks:check,rows:artifact(file),sheet:{...artifact(sheet),selectedFrames:20,reviewed:true}});
 }
 const before=`${qa}/${platform}-brute-knee-final/thrown_brute_step_connected_survive.json`;
 const after=dir+'/thrown_brute_step_connected_survive.json';const baseline=rise(read(before)),current=rise(read(after));
 assert(current.maxSharedLift<.025&&current.maxHandFloorGap<.02&&current.maxSharedLift<baseline.maxSharedLift*.1);
 report.platforms.push({platform,frames,unitTests:units,flows,bruteStepRise:{before:baseline,after:current,baselineRows:artifact(before)},sources:sources.map(f=>artifact(root+'/'+f)),evidence:['unit-check.log','native-build.log','native-debug.log','capture-status.txt','reviewed-sheets.json',...xmls].map(f=>artifact(dir+'/'+f))});
 const first=`${qa}/${platform}-ragdoll-edge-final`;
 report.diagnostics.push({platform,scope:'Initial implementation before projected escapes and tile merging; not final acceptance.',checks:artifact(first+'/case-checks.json'),blockedHammerSteps:artifact(first+'/steps/case-checks.json')});
}
const wildenBefore=qa+'/neo-ragdoll-edge-contact-draft/wilden-handoff-before.json';
const wildenRows=read(wildenBefore);
report.diagnostics.push({scope:'Wilden physical native-follow pose before support projection; rendered skin was already above the floor.',rows:artifact(wildenBefore),floorPenetratingFrames:wildenRows.filter(r=>r.floorClearance<-.02).length,minFloorClearance:Math.min(...wildenRows.filter(r=>Number.isFinite(r.floorClearance)).map(r=>r.floorClearance))});
report.totalFrames=report.platforms.reduce((n,p)=>n+p.frames,0);report.reviewedSheets=report.platforms.reduce((n,p)=>n+p.flows.length,0);report.finalGetupChoreographyAccepted=false;
for(const root of roots)fs.writeFileSync(root+'/art/encounters/ragdoll-edge-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({frames:report.totalFrames,sheets:report.reviewedSheets,platforms:report.platforms.map(p=>({platform:p.platform,frames:p.frames,unitTests:p.unitTests,rise:p.bruteStepRise.after}))}));
