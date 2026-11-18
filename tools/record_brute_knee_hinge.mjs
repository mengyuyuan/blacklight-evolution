import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';

const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const qa='D:/CodexCache/creature-combat-qa';
const cases=['thrown_brute_connected_survive','thrown_brute_step_connected_survive','hammer_brute_survive','blade_brute_lethal','muscle_brute_consume','claws_brute_consume'];
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const artifact=file=>({file,sha256:sha(file)});
const sources=[
 'src/main/java/com/codex/blacklight/client/renderer/AdaptiveRagdoll.java',
 'src/main/java/com/codex/blacklight/client/renderer/RagdollHinge.java',
 'src/main/java/com/codex/blacklight/client/renderer/RagdollRecovery.java',
 'src/main/java/com/codex/blacklight/combat/BruteKnockdown.java',
 'src/main/java/com/codex/blacklight/combat/BruteContact.java',
 'src/main/java/com/codex/blacklight/client/animation/BruteHitPose.java',
 'src/main/resources/assets/blacklight/geo/blacklight_brute_mc.geo.json',
 'src/main/resources/assets/blacklight/models/brute_combat_motion.json',
 'src/main/resources/assets/blacklight/animations/blacklight_brute.animation.json',
 'src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAudit.java',
 'src/test/java/com/codex/blacklight/client/renderer/RagdollHingeTest.java',
 'src/test/java/com/codex/blacklight/combat/BruteKnockdownTest.java',
 'tools/brute-test.init.gradle'
];
function entryMotion(rows){
 const early=rows.filter(r=>r.hitKind===6&&r.gameTime+r.partial-r.hitStarted>=0&&r.gameTime+r.partial-r.hitStarted<8&&r.pieces?.some(p=>p.bone==='torso'));
 assert(early.length>5);const torso=r=>r.pieces.find(p=>p.bone==='torso');const distance=(a,b)=>Math.hypot(a[0]-b[0],a[2]-b[2]);
 return {frames:early.length,initialTorsoHorizontalError:distance(torso(early[0]).renderPos,torso(early[0]).target),torsoHorizontalTravel:early.slice(1).reduce((n,r,i)=>n+distance(torso(r).renderPos,torso(early[i]).renderPos),0)};
}
function knees(rows){
 const measured=rows.filter(r=>r.kneeJoints);assert(measured.length>25);
 return {frames:measured.length,minAngle:Math.min(...measured.map(r=>r.kneeMinAngle)),maxAngle:Math.max(...measured.map(r=>r.kneeMaxAngle)),maxSwing:Math.max(...measured.map(r=>r.kneeSwing))};
}
const report={date:new Date().toISOString(),scope:'Brute knee hinge constraints during physical fall/death, plus pelvis-centered horizontal get-up placement. This reduces a measured recovery slide and rejects final choreography acceptance.',changes:[
 'Each directly connected Brute thigh/shin pair uses an anatomical X hinge with -155 to 0 degree flexion. Constraint math accounts for the captured bone orientations and current parent rotation, preserves the joint pivot and projects relative angular velocity onto the permitted axis.',
 'The generic solver remains in use for other joint types and species. Severed connections do not receive hinge constraints. Render QA independently reconstructs each measured knee from captured bone frames.',
 'The get-up compensates the feet-origin root rotation around the actual model hip pivots. Horizontal pelvis position remains over its idle position throughout the authored rise; mesh support still determines height.',
 'Five new hinge tests cover arbitrary capture/body rotations, backward bend, sideways swing, quaternion sign/singular cases, angular velocity at stops and preserved joint pivots. One additional get-up test samples the horizontal pelvis position across the full rise.'
],diagnostics:[],platforms:[],limits:[
 'The measured early slide is reduced, not eliminated. Physical bodies can still move away from the authoritative entity; this change does not synchronize client ragdoll placement back into gameplay coordinates.',
 'Only Brute knees receive anatomical hinge limits. Hips, shoulders, elbows and other species retain their existing physics constraints; final body weight, self-contact, ankle/finger articulation and palm support remain open.',
 'Native knee-angle acceptance covers the recorded connected living knockdowns and attached knees in the death fixture. Standing flinch and the two mounted consume recordings have no physical knee samples and are only visual regression fixtures.',
 'Terrain acceptance is bounded to the specified flat and slab/wall cases and sampled mesh vertices. Arbitrary terrain, side/rear impacts, native death during GETUP, offscreen recovery and multiplayer latency remain unverified.',
 'The slab/wall recording still shows a brief whole-body lift during early recovery. Zero sampled penetration does not establish planted limbs or natural weight on that terrain.',
 'Only selected contact sheets were manually reviewed, not every captured frame. Silent QA does not establish audible mix quality.',
 'No new server tests were required for this client physics/pose change. The prior 33-case server result remains a historical checkpoint; it is not reported as a fresh run here. All remaining species, military AI/helicopters, fine vehicle/item models and audio remain in the active goal. No release or commit.'
]};
for(const name of ['thrown_brute_connected_survive','blade_brute_lethal']){
 const file=`${qa}/neo-brute-knee-before/${name}.json`;
 report.diagnostics.push({case:name,beforeKnees:knees(read(file)),rows:artifact(file)});
}
report.diagnostics.push({scope:'Hinge-only intermediate run did not reduce the early slide; this motivated the separate hip-pivot correction.',rows:artifact(`${qa}/neo-brute-knee-draft/thrown_brute_connected_survive.json`),entry:entryMotion(read(`${qa}/neo-brute-knee-draft/thrown_brute_connected_survive.json`))});
for(const [index,platform] of ['neo','forge'].entries()){
 const root=roots[index],dir=`${qa}/${platform}-brute-knee-final`;
 const checks=read(dir+'/case-checks.json');assert.deepEqual(checks.map(c=>c.case),cases);
 assert(fs.readFileSync(dir+'/capture-status.txt','utf8').startsWith('Completed 6 cases. Dropped frames=0.'));
 for(const file of ['native-build.log','unit-check.log'])assert(fs.readFileSync(dir+'/'+file,'utf8').includes('BUILD SUCCESSFUL'));
 const review=read(dir+'/reviewed-sheets.json');assert.deepEqual(review.cases,cases);assert.equal(review.everyCapturedFrameManuallyReviewed,false);
 const xmls=fs.readdirSync(dir).filter(f=>/^TEST-.*\.xml$/.test(f));let unitCount=0;
 for(const name of xmls){const tag=fs.readFileSync(dir+'/'+name,'utf8').match(/<testsuite\b[^>]+>/)[0];unitCount+=Number(tag.match(/\btests="(\d+)"/)[1]);assert.equal(Number(tag.match(/\bfailures="(\d+)"/)[1]),0);assert.equal(Number(tag.match(/\berrors="(\d+)"/)[1]),0);}
 assert.equal(unitCount,57);
 const latest=Math.max(...sources.filter(s=>s.startsWith('src/main/')||s.startsWith('src/ragdollVisualTest/')).map(s=>fs.statSync(root+'/'+s).mtimeMs));
 const flows=[];let frames=0;
 for(const check of checks){
  const file=dir+'/'+check.case+'.json',rows=read(file),sheet=dir+'/'+check.case+'-review.jpg';
  assert(check.passed,platform+'/'+check.case);assert(rows.length>150);assert(fs.statSync(file).mtimeMs>latest);assert.equal(sha(sheet),review.sheets[check.case]);
  for(const row of rows)assert(row.imageQueued&&fs.existsSync(dir+'/'+row.image));
  if(check.case.startsWith('thrown_brute')||check.case==='blade_brute_lethal'){
   assert(check.kneeFrames>25);assert(check.minKneeAngle>=-155.5*Math.PI/180&&check.maxKneeAngle<=.02&&check.maxKneeSwing<.03);
  }
  if(check.case.startsWith('thrown_brute')){
   assert.equal(check.getupClockErrors,0);assert.equal(check.downClockErrors,0);assert(check.getupAuthoredShapeGap<.005&&check.getupAuthoredAngleError<.03);
   assert(check.afterControlPositionError<.025&&check.afterControlAngleError<.03);
  }
  if(check.case==='thrown_brute_connected_survive')assert(check.getupPlantedSamples>12&&check.getupHandFloorGap<.02&&check.getupLowerBodyFloorGap<.02);
  if(check.case.includes('_step_'))assert.equal(check.maxTerrainPenetration,0);
  frames+=rows.length;flows.push({case:check.case,frames:rows.length,checks:check,rows:artifact(file),sheet:{...artifact(sheet),selectedFrames:20,reviewed:true}});
 }
 const baseline=`${qa}/${platform}-brute-ground-rise-final/thrown_brute_connected_survive.json`;
 const before=entryMotion(read(baseline)),after=entryMotion(read(dir+'/thrown_brute_connected_survive.json'));
 assert(after.initialTorsoHorizontalError<before.initialTorsoHorizontalError*.75);assert(after.torsoHorizontalTravel<before.torsoHorizontalTravel*.75);
 report.platforms.push({platform,frames,unitTests:unitCount,flows,earlyGetup:{scope:'Actual torso center, horizontal components, sampled GETUP age 0–8; frame times differ slightly between runs.',before,after,baselineRows:artifact(baseline)},sources:sources.map(s=>artifact(root+'/'+s)),evidence:['native-build.log','native-debug.log','unit-check.log','capture-status.txt','reviewed-sheets.json',...xmls].map(s=>artifact(dir+'/'+s))});
}
report.totalFrames=report.platforms.reduce((n,p)=>n+p.frames,0);report.reviewedSheets=12;report.finalGetupChoreographyAccepted=false;
for(const root of roots)fs.writeFileSync(root+'/art/mutant-combat/brute-knee-hinge-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({frames:report.totalFrames,platforms:report.platforms.map(p=>({platform:p.platform,frames:p.frames,unitTests:p.unitTests,earlyGetup:p.earlyGetup}))}));
