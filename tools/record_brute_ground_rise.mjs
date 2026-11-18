import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';

const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const qa='D:/CodexCache/creature-combat-qa';
const cases=['thrown_brute_connected_survive','thrown_brute_step_connected_survive','hammer_brute_survive','blade_brute_lethal','muscle_brute_consume','claws_brute_consume'];
const read=file=>JSON.parse(fs.readFileSync(file,'utf8'));
const sha=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const artifact=file=>({file,sha256:sha(file)});
const sources=[
 'src/main/java/com/codex/blacklight/combat/BruteKnockdown.java',
 'src/main/java/com/codex/blacklight/combat/BruteContact.java',
 'src/main/java/com/codex/blacklight/combat/BruteFlinch.java',
 'src/main/java/com/codex/blacklight/combat/CreaturePoseTable.java',
 'src/main/java/com/codex/blacklight/combat/ImpactControl.java',
 'src/main/java/com/codex/blacklight/client/animation/BruteHitPose.java',
 'src/main/java/com/codex/blacklight/client/renderer/AdaptiveRagdoll.java',
 'src/main/java/com/codex/blacklight/client/renderer/RagdollRecovery.java',
 'src/main/java/com/codex/blacklight/client/model/BlacklightCreatureModel.java',
 'src/main/resources/assets/blacklight/models/brute_combat_motion.json',
 'src/main/resources/assets/blacklight/animations/blacklight_brute.animation.json',
 'src/main/resources/assets/blacklight/geo/blacklight_brute_mc.geo.json',
 'src/main/resources/assets/blacklight/textures/entity/blacklight_brute_mc.png',
 'src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAudit.java',
 'src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAuditRider.java',
 'src/bruteTest/java/com/codex/blacklight/gametest/BruteKnockdownGameTest.java',
 'src/test/java/com/codex/blacklight/combat/BruteKnockdownTest.java',
 'src/test/java/com/codex/blacklight/client/renderer/RagdollRecoveryTest.java',
 'tools/brute-test.init.gradle','tools/build_mutant_combat.mjs','tools/detail_brute_model.mjs',
 'art/mutant-combat/brute-model.json'
];
function lateRise(rows){
 const selected=rows.filter(r=>r.hitDuration===32&&r.gameTime+r.partial-r.hitStarted>=8&&r.gameTime+r.partial-r.hitStarted<=32&&r.renderTargetGap!==undefined);
 assert(selected.length>25);
 return {frames:selected.length,maxPositionGap:Math.max(...selected.map(r=>r.renderTargetGap)),maxAngleError:Math.max(...selected.map(r=>r.renderTargetAngle))};
}
const report={date:new Date().toISOString(),scope:'Brute knee articulation, grounded push/crouch rise, and eight-tick physical-to-authored handoff within the existing 32-tick GETUP phase.',changes:[
 'Two knee hinges move the existing lower-leg geometry. The rig has 11 bones and 152 cuboids; upper-body damage contact retains 120 cuboids. The installation asserted every flattened cube, including UVs, unchanged.',
 'The authored pose rests on lower-body geometry, solves rigid elbow flexion against a support plane, pushes the chest up and unfolds the knees. Bone lengths and fist geometry are not scaled.',
 'Live GETUP hands control to the authored rig after eight ticks; physical skin and terrain correction stay active until the original 32-tick control phase ends. Death and captured-body paths retain their separate behavior.',
 'Native QA measures actual rendered forearm and lower-body vertices on flat ground. Shape error removes only the shared translation used for terrain support, and independently checks orientation.',
 'Regression recording covers actual pig throws on flat and specified step/wall terrain, standing hammer hit, lethal blade hit and two mounted consume flows.'
],platforms:[],limits:[
 'This is bounded grounded-rise acceptance, not final choreography or overall model polish. Sliding toward the server position, initial prone support, body weight and self-contact still need refinement.',
 'Independent knees and elbows are present. Ankles and fingers remain rigid; the lowest forearm surface is not proof of anatomical palm/finger contact.',
 'Exact hand and lower-body support is accepted only on the recorded flat fixture during GETUP progress 0.4–0.78. The step/wall fixture checks terrain penetration and rig handoff, not per-limb inverse kinematics on arbitrary uneven terrain.',
 'Native terrain checks sample rendered vertices rather than every triangle interior. Twenty selected frames per flow are reviewed; not every captured frame is manually reviewed.',
 'Death during GETUP and interruption have server coverage from the previous checkpoint, but this recording does not visually accept those transitions. Side/rear impact, offscreen recovery, ceilings, water, falls and network latency remain open.',
 'Mounted support, initial mount, seat/knee surfaces, attack contact, alternate skins/scales and full consume choreography remain open. Static hammer overlap, other species, military soldiers/helicopters, fine vehicle/item models and audible mix remain in the active goal. No release package or commit.'
]};
for(const [index,platform] of ['neo','forge'].entries()){
 const root=roots[index],dir=`${qa}/${platform}-brute-ground-rise-final`;
 const checks=read(dir+'/case-checks.json');assert.deepEqual(checks.map(c=>c.case),cases);
 const review=read(dir+'/reviewed-sheets.json');assert.deepEqual(review.cases,cases);
 assert.equal(review.everyCapturedFrameManuallyReviewed,false);
 assert(fs.readFileSync(dir+'/capture-status.txt','utf8').startsWith('Completed 6 cases. Dropped frames=0.'));
 for(const name of ['native-build.log','unit-check.log','server-check.log'])assert(fs.readFileSync(dir+'/'+name,'utf8').includes('BUILD SUCCESSFUL'),platform+'/'+name);
 const serverCount=Number(fs.readFileSync(dir+'/server-check.log','utf8').match(/All (\d+) required tests passed/)[1]);assert.equal(serverCount,33);
 const xmls=fs.readdirSync(dir).filter(f=>/^TEST-.*\.xml$/.test(f));let unitCount=0;
 for(const name of xmls){const tag=fs.readFileSync(dir+'/'+name,'utf8').match(/<testsuite\b[^>]+>/)[0];unitCount+=Number(tag.match(/\btests="(\d+)"/)[1]);assert.equal(Number(tag.match(/\bfailures="(\d+)"/)[1]),0);assert.equal(Number(tag.match(/\berrors="(\d+)"/)[1]),0);}
 assert.equal(unitCount,51);
 const model=read(root+'/art/mutant-combat/brute-model.json');assert.equal(model.bones,11);assert.equal(model.cuboids,152);assert.equal(model.contactCuboids,120);
 assert.equal(model.geometry,sha(root+'/src/main/resources/assets/blacklight/geo/blacklight_brute_mc.geo.json'));
 assert.equal(model.texture,sha(root+'/src/main/resources/assets/blacklight/textures/entity/blacklight_brute_mc.png'));
 const latest=Math.max(...sources.filter(s=>s.startsWith('src/main/')||s.startsWith('src/ragdollVisualTest/')).map(s=>fs.statSync(root+'/'+s).mtimeMs));
 let frameCount=0;const flows=[];
 for(const check of checks){
  const file=dir+'/'+check.case+'.json',rows=read(file),sheet=dir+'/'+check.case+'-review.jpg';
  assert(check.passed,platform+'/'+check.case);assert(rows.length>150);assert(fs.statSync(file).mtimeMs>latest);assert(fs.statSync(sheet).mtimeMs>=fs.statSync(file).mtimeMs);
  assert.equal(review.sheets[check.case],sha(sheet));
  for(const r of rows)assert(r.imageQueued&&fs.existsSync(dir+'/'+r.image));
  if(check.case.startsWith('thrown_brute')){
   assert.equal(check.bodyInstances,1);assert.equal(check.getupClockErrors,0);assert.equal(check.downClockErrors,0);
   assert(check.getupAuthoredFrames>25&&check.getupAuthoredShapeGap<.005&&check.getupAuthoredAngleError<.03);
   assert(check.afterControlPositionError<.025&&check.afterControlAngleError<.03&&check.minRenderedMeshClearance>=-.002);
  }
  if(check.case==='thrown_brute_connected_survive')assert(check.getupPlantedSamples>12&&check.getupHandFloorGap<.02&&check.getupLowerBodyFloorGap<.02);
  if(check.case.includes('_step_'))assert.equal(check.maxTerrainPenetration,0);
  frameCount+=rows.length;flows.push({case:check.case,frames:rows.length,checks:check,rows:artifact(file),sheet:{...artifact(sheet),selectedFrames:20,reviewed:true}});
 }
 const baseline=`${qa}/${platform}-brute-recovery-final/thrown_brute_connected_survive.json`;
 report.platforms.push({platform,frames:frameCount,unitTests:unitCount,serverTests:serverCount,model:{bones:11,cuboids:152,contactCuboids:120,texture:model.texture},flows,
  baselineComparison:{scope:'Same flat throw fixture, GETUP age 8–32. Raw position gap includes uniform terrain translation; new checks separately report translation-independent shape error.',before:lateRise(read(baseline)),after:lateRise(read(dir+'/thrown_brute_connected_survive.json')),baselineRows:artifact(baseline)},
  sources:sources.map(s=>artifact(root+'/'+s)),evidence:['native-build.log','native-debug.log','unit-check.log','server-check.log','capture-status.txt','reviewed-sheets.json',...xmls].map(f=>artifact(dir+'/'+f))});
}
report.totalFrames=report.platforms.reduce((sum,p)=>sum+p.frames,0);report.reviewedSheets=12;report.everyCapturedFrameManuallyReviewed=false;
for(const root of roots)fs.writeFileSync(root+'/art/mutant-combat/brute-ground-rise-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({frames:report.totalFrames,reviewedSheets:12,platforms:report.platforms.map(p=>({platform:p.platform,frames:p.frames,unitTests:p.unitTests,serverTests:p.serverTests,baseline:p.baselineComparison}))}));
