import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const cases=['thrown_brute_connected_survive','thrown_brute_step_connected_survive','hammer_brute_survive','blade_brute_lethal','muscle_brute_consume','claws_brute_consume'];
const read=file=>JSON.parse(fs.readFileSync(file,'utf8'));
const sha=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const artifact=file=>({file,sha256:sha(file)});
const sources=[
 'src/main/java/com/codex/blacklight/combat/BruteKnockdown.java',
 'src/main/java/com/codex/blacklight/combat/BruteContact.java',
 'src/main/java/com/codex/blacklight/combat/BruteFlinch.java',
 'src/main/java/com/codex/blacklight/combat/BruteMountedBody.java',
 'src/main/java/com/codex/blacklight/combat/ImpactControl.java',
 'src/main/java/com/codex/blacklight/combat/CreaturePoseTable.java',
 'src/main/java/com/codex/blacklight/client/animation/BruteHitPose.java',
 'src/main/java/com/codex/blacklight/client/animation/BruteRiderPose.java',
 'src/main/java/com/codex/blacklight/client/renderer/AdaptiveRagdoll.java',
 'src/main/java/com/codex/blacklight/client/renderer/RagdollRecovery.java',
 'src/main/java/com/codex/blacklight/client/model/BlacklightCreatureModel.java',
 'src/main/resources/assets/blacklight/models/brute_combat_motion.json',
 'src/main/resources/assets/blacklight/animations/blacklight_brute.animation.json',
 'src/main/resources/assets/blacklight/geo/blacklight_brute_mc.geo.json',
 'src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAudit.java',
 'src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAuditRider.java',
 'src/bruteTest/java/com/codex/blacklight/gametest/BruteKnockdownGameTest.java',
 'src/test/java/com/codex/blacklight/combat/BruteKnockdownTest.java',
 'src/test/java/com/codex/blacklight/client/renderer/RagdollRecoveryTest.java',
 'tools/brute-test.init.gradle','tools/build_mutant_combat.mjs'
];
const report={date:new Date().toISOString(),scope:'Brute live down/getup timing and finite native physics handoff; authored prone recovery pose; flat/step-wall actual grab-throw inputs, plus standing-hit/death/mounted regression.',changes:[
 'The actual server DOWN phase keeps the live Brute recovery deadline beyond the active frame. GETUP sets its own 32 tick deadline; every post-control native frame must match the rendered pose.',
 'An authoritative control release within two ticks of the expected deadline wins over the client clock; genuinely early interruption retains the bounded 16 tick recovery. Hitstop QA reads the completed solver step time, avoiding false positives after clock rewind.',
 'Brute full-knockdown safety limit increases from 80 to 96 ticks to preserve the previous travel/down allowance with the extra 16 tick rise. Other species and standing-hit limits retain their defaults.',
 'A neutral root channel is appended to the eight existing channels, including ordinary Gecko clips. The authored recovery samples all 152 cuboids for floor support; damage contact remains the original 120 upper cuboids.',
 'The prone/side/chest-rise/stand sequence is repository-authored. No claim of extracted original-game animation or complete articulated ground support.'
],baseline:[],platforms:[],limits:[
 'This accepts phase timing, finite release and bounded terrain checks in recorded fixtures, not final get-up choreography. Physics still blends back to the server position; sliding, ground weight and limb self-contact need further refinement.',
 'The current rig has elbows but no independent knee or finger joints. Floor projection does not prove exact palm support or knee placement.',
 'Native throw fixtures use a frontal pig throw on flat ground and the specified slab/wall layout. Rear/side impacts, arbitrary slopes, ceiling/water, long falls, latency and unseen/offscreen recovery need broader coverage.',
 'Five new server cases test full control, standing hit while down, getup interruption/cancel, death during getup and late landed recovery. Death during getup is not a native visual acceptance claim.',
 'Native collision checks sample rendered mesh vertices, not every triangle interior. Twenty selected frames per flow are reviewed, not every captured frame manually.',
 'Mounted support remains bounded by the earlier torso distance tests. Initial mount, seat/knees, right attack contact, alternate skins/scales and full consume choreography remain open.',
 'Hammer idle/charge/jab overlap, remaining species, military soldiers/helicopters, fine vehicle/item models and audible mix remain in the active goal. No release package or commit.'
]};
const before='D:/CodexCache/creature-combat-qa/neo-brute-recovery-before/';
const oldChecks=read(before+'case-checks.json');
report.baseline.push({platform:'neo',checks:oldChecks,rows:artifact(before+'thrown_brute_connected_survive.json'),log:artifact(before+'native-build.log')});
report.clockFailureEvidence=artifact('D:/CodexCache/creature-combat-qa/neo-brute-recovery-draft/release-clock-failures.json');
for(const [index,platform] of ['neo','forge'].entries()){
 const root=roots[index],dir=`D:/CodexCache/creature-combat-qa/${platform}-brute-recovery-final/`;
 const latest=Math.max(...sources.filter(s=>s.startsWith('src/main/')||s.startsWith('src/ragdollVisualTest/')).map(s=>fs.statSync(root+'/'+s).mtimeMs));
 const checks=read(dir+'case-checks.json');assert.deepEqual(checks.map(c=>c.case),cases);
 assert(fs.readFileSync(dir+'capture-status.txt','utf8').startsWith('Completed 6 cases. Dropped frames=0.'));
 for(const name of ['native-build.log','unit-check.log','server-check.log'])assert(fs.readFileSync(dir+name,'utf8').includes('BUILD SUCCESSFUL'),platform+'/'+name);
 const serverCount=Number(fs.readFileSync(dir+'server-check.log','utf8').match(/All (\d+) required tests passed/)[1]);assert.equal(serverCount,33);
 const xmls=fs.readdirSync(dir).filter(f=>/^TEST-.*\.xml$/.test(f));let unitCount=0;
 for(const name of xmls){const line=fs.readFileSync(dir+name,'utf8').match(/<testsuite\b[^>]+>/)[0];unitCount+=Number(line.match(/\btests="(\d+)"/)[1]);assert.equal(Number(line.match(/\bfailures="(\d+)"/)[1]),0);assert.equal(Number(line.match(/\berrors="(\d+)"/)[1]),0);}
 assert.equal(unitCount,49);
 const flows=[];let frameCount=0;
 for(const check of checks){
  const file=dir+check.case+'.json',rows=read(file),sheet=dir+check.case+'-review.jpg';assert(check.passed,platform+'/'+check.case);assert(rows.length>150);assert(fs.statSync(file).mtimeMs>latest);assert(fs.statSync(sheet).mtimeMs>=fs.statSync(file).mtimeMs);
  for(const r of rows)assert(r.imageQueued&&fs.existsSync(dir+r.image));
  if(check.case.startsWith('thrown_brute')){
   assert.equal(check.bodyInstances,1);assert.equal(check.getupClockErrors,0);assert.equal(check.downClockErrors,0);assert(check.getupClockSamples>30);
   assert(check.afterControlPositionError<.025&&check.afterControlAngleError<.03);assert(check.minRenderedMeshClearance>=-.002);
  }
  if(check.case.includes('_step_'))assert.equal(check.maxTerrainPenetration,0);
  frameCount+=rows.length;flows.push({case:check.case,frames:rows.length,checks:check,rows:artifact(file),sheet:{...artifact(sheet),selectedFrames:20,reviewed:true,everyFrameManuallyReviewed:false}});
 }
 report.platforms.push({platform,frames:frameCount,unitTests:unitCount,serverTests:serverCount,flows,sources:sources.map(s=>artifact(root+'/'+s)),evidence:['native-build.log','native-debug.log','unit-check.log','server-check.log','capture-status.txt',...xmls].map(f=>artifact(dir+f))});
}
report.totalFrames=report.platforms.reduce((sum,p)=>sum+p.frames,0);report.reviewedSheets=12;
for(const root of roots)fs.writeFileSync(root+'/art/mutant-combat/brute-recovery-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({frames:report.totalFrames,flows:12,reviewedSheets:12,platforms:report.platforms.map(p=>({platform:p.platform,frames:p.frames,unitTests:p.unitTests,serverTests:p.serverTests}))}));
