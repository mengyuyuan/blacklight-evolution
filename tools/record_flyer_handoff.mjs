import fs from 'node:fs';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import zlib from 'node:zlib';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const edgeGap=(lines,point)=>Math.min(...lines.flatMap(line=>line.points.slice(1).map((b,i)=>{
 const a=line.points[i],ab=[b.x-a.x,b.y-a.y,b.z-a.z],ap=[point.x-a.x,point.y-a.y,point.z-a.z];
 const t=Math.max(0,Math.min(1,ab.reduce((s,v,j)=>s+v*ap[j],0)/ab.reduce((s,v)=>s+v*v,0)));
 return Math.hypot(...ap.map((v,j)=>v-t*ab[j]));
})));
const report={date:'2026-11-15',status:'partial_native_flyer_handoff_checkpoint',scope:'Downed native flyer only: capture, hold, claws sweep absorption, blade absorption, claws head absorption, live release, and a last-chance save followed by a real lethal blade session. Transition frames remain unresolved; this is not full creature/mod/terrain acceptance.',fixes:['Shared native receiver pose and actual head/thorax surface grip','Torso-sized carry clearance instead of the entire wing span','Original flyer_com_idle wing fold retained instead of human arm projection; horizontal body faces its carrier','Ragdoll pose history resets on a backwards client world-clock correction instead of crashing or reusing future skin snapshots'],platforms:[]};
for(const [i,platform] of ['neo','forge'].entries()){
 const dir=`D:/CodexCache/creature-combat-qa/${platform}-flyer-handoff-frames/`,e=JSON.parse(fs.readFileSync(dir+'evidence.json'));
 const testDir=`D:/CodexCache/creature-combat-qa/${platform==='neo'?'neo-build':'forge-render-build'}/test-results/test/`;
 const units=fs.readdirSync(testDir).filter(f=>/^TEST-.*(RagdollPoseHistory|SweptBladeSurface|BladeCutSequence|RagdollJointProjection|BlacklightBoneIk|HurtFlashWindow|BladeConsumePose|ContactSection|RagdollRootSelection|RagdollTerrainSupport|RagdollRecovery)Test\.xml$/.test(f)).map(file=>{
  const xml=fs.readFileSync(testDir+file,'utf8'),attr=n=>xml.match(new RegExp(n+'="([^"]+)"'))?.[1];
  assert.equal(+attr('failures'),0);assert.equal(+attr('errors'),0);return {file:testDir+file,tests:+attr('tests'),sha256:hash(testDir+file)};
 });assert.equal(units.length,12);assert.equal(units.reduce((s,t)=>s+t.tests,0),56);
 assert(e.frames.length>=1200,platform+' native frame coverage');
 const cases=[];
 for(let scene=0;scene<5;scene++){
  const frames=e.frames.filter(f=>f.scene===scene),timeline=e.timeline.filter(t=>t.scene===scene),last=timeline.at(-1),held=frames.filter(f=>f.visual.stage===2),pre=frames.filter(f=>f.visual.stage===1||f.visual.stage===2);
  assert(frames.length>150);assert(pre.every(f=>!f.ragdoll&&(f.physical.parts||0)===0),'Live held receiver must remain connected');
  const cuts=Math.max(...frames.map(f=>f.physical.cuts||0)),components=Math.max(...frames.map(f=>f.physical.components||0));
  if(scene<4){assert(held.length>=8);assert(timeline.some(t=>t.physical&&t.age<65));}
  if(scene<3){assert(last.removed&&!last.session);assert(timeline.some(t=>t.visual.stage===3));}
  if(scene<2)assert(cuts>0,'Sharp absorption must produce actual mesh sections');
  let bladeSection=null;
  if(scene===1){
   const first=frames.find(f=>f.physical.cuts>0),torso=first.physical.sectionAudit.filter(p=>p.bone==='torso');
   const events=first.physical.sectionEvents;
   assert(frames.filter(f=>f.physical.parts>0).every(f=>f.physical.rootBone==="torso"),"The wing must not own its torso in the physical skeleton");
   assert.equal(events.length,1,'The two sides of one blade must not create two authored sections');
   assert.equal(torso.length,2,'The actual thorax must have two resulting sides, not only a wing-tip chip');
   assert.deepEqual(torso.flatMap(p=>p.boundaries.filter(b=>b.key===events[0]).map(b=>b.sign)).sort(),[-1,1]);
   const seconds=(first.renderTime-first.visual.started)/20;
   assert(seconds>=.833333-1e-5&&seconds<1.1,'Section must follow the authored marker promptly');
   assert(frames.filter(f=>f.physical.sectionEvents).every(f=>f.physical.sectionEvents.length<=1),'No duplicate blade section in recovery');
   bladeSection={firstFrame:first.image,seconds,event:events[0],thoraxPieces:torso.length,partSplits:first.physical.cuts,components:first.physical.components};
   const before=frames.filter(f=>!f.physical.cuts&&f.physical.drawnBlades?.length).at(-1);
   bladeSection.preSeverEdgeToThoraxCenter=before?edgeGap(before.physical.drawnBlades,before.physical.bladeTarget):null;
   const blood=frames.flatMap(f=>(f.physical.sectionBlood||[]).map(b=>({...b,image:f.image}))).filter(b=>b.emitted&&b.anchors>0);
   assert(blood.length>=12,'Confirmed cuts must sustain their physical cap emitters');
   assert.deepEqual([...new Set(blood.map(b=>b.side))].sort(),[-1,1],'Both resulting wound faces need blood');
   assert(Math.max(...blood.map(b=>b.capDistance))<1e-4,'Sever blood must stay on the physical cap triangles');
   bladeSection.blood={samples:blood.length,sides:[-1,1],maxCapDistance:Math.max(...blood.map(b=>b.capDistance)),firstEmittedFrame:blood[0].image};
  }
  if(scene===2)assert.equal(cuts,0,'Head compression must not invent blade cuts');
  if(scene===3){assert(!last.removed&&last.health>0&&!last.session);assert.equal(cuts,0);assert(!last.physical);}
  const grip=held.map(f=>f.authoredSkin.drawnGripSurfaceDistance).filter(Number.isFinite);
  const stable=held.filter(f=>f.renderTime-f.visual.started>=1).map(f=>f.authoredSkin.drawnGripSurfaceDistance).filter(Number.isFinite);
  assert.equal(grip.length,held.length,'Every held frame requires the actual weapon draw probe');
  assert(held.every(f=>Math.abs(f.authoredSkin.drawnGrip.time-f.renderTime)<.001),'No stale player draw poses');
  assert(held.every(f=>f.authoredSkin.drawnGrip.growth>.999&&!f.authoredSkin.drawnGrip.liquid),'This grip audit requires the mature rigid weapon');
  if(scene<4){assert(grip.length>=8);assert(stable.length>=8);assert(Math.max(...stable)<.1,platform+' stable native grip surface');}
  if(scene===4){assert.equal(e.hits.filter(h=>h.scene===scene).length,2);assert(timeline.some(t=>t.age>33&&t.age<68&&t.health===1));assert.equal(last.health,0);assert(cuts>0);assert(frames.filter(f=>f.health>0).every(f=>!f.ragdoll&&(f.physical.parts||0)===0));}
  if(scene===4){assert(frames.some(f=>f.health<=0&&f.physical.hurtFlash),'Lethal contact must visibly flash');assert(frames.filter(f=>f.age>=100).every(f=>f.physical.hurtFlash===false),'Corpse flash must retire after the hit, including world-clock corrections');}
  const query=frames.filter(f=>f.physical.cutQueryChecked>0);
  if(scene<3){assert(query.length>15,platform+' same-frame skin query coverage');assert(Math.max(...query.map(f=>f.physical.cutQueryToDrawMax))<1e-4,platform+' cut queries must use the rendered connected skin');}
  const flashed=frames.filter(f=>f.physical.hurtFlash);
  cases.push({scene,cutQueryFrames:query.length,cutQueryToDrawMax:query.length?Math.max(...query.map(f=>f.physical.cutQueryToDrawMax)):null,uncorrectedPoseToDrawMax:query.length?Math.max(...query.map(f=>f.physical.rawPoseToDrawMax)):null,frames:frames.length,heldFrames:held.length,gripMax:grip.length?Math.max(...grip):null,stableGripMax:stable.length?Math.max(...stable):null,gripOutliers:held.filter(f=>f.authoredSkin.drawnGripSurfaceDistance>=.1).map(f=>({image:f.image,timeIntoHold:f.renderTime-f.visual.started,distance:f.authoredSkin.drawnGripSurfaceDistance})),resampleDifferenceMax:held.length?Math.max(...held.map(f=>f.authoredSkin.resamplePalmDifference)):null,heldLowest:held.length?Math.min(...held.map(f=>f.authoredSkin.lowest)):null,cuts,components,finalHealth:last.health,removed:last.removed,hurtFlashFrames:flashed.length,lastHurtFlashAge:flashed.length?Math.max(...flashed.map(f=>f.age)):null,hits:e.hits.filter(h=>h.scene===scene)});
  cases.at(-1).bladeSection=bladeSection;
 }
 const files=['src/main/java/com/codex/blacklight/combat/FlyerReceiverPose.java','src/main/java/com/codex/blacklight/combat/ConsumePlacementTrack.java','src/main/java/com/codex/blacklight/client/animation/FlyerCarryPose.java','src/main/java/com/codex/blacklight/client/animation/NativeGeckoConsume.java','src/main/java/com/codex/blacklight/client/animation/GrabHoldPose.java','src/main/java/com/codex/blacklight/client/animation/BlacklightRenderPose.java','src/main/java/com/codex/blacklight/client/renderer/RagdollPoseHistory.java','src/test/java/com/codex/blacklight/client/renderer/RagdollPoseHistoryTest.java','src/flyerHandoffVisual/java/com/codex/blacklight/client/FlyerHandoffVisual.java'];
 files.push('src/main/java/com/codex/blacklight/client/animation/BlacklightBoneIk.java','src/main/java/com/codex/blacklight/client/animation/RagdollDrive.java','src/test/java/com/codex/blacklight/client/animation/BlacklightBoneIkTest.java','src/flyerHandoffVisual/java/com/codex/blacklight/client/NativeGripProbe.java','src/flyerHandoffVisual/java/com/codex/blacklight/client/qagrip/NativeGripProbeMixin.java','src/flyerHandoffVisual/resources/blacklight.grip-qa.mixins.json','tools/flyer-handoff-visual.init.gradle');
 files.push('src/main/java/com/codex/blacklight/client/renderer/AdaptiveRagdoll.java','src/test/java/com/codex/blacklight/client/renderer/RagdollJointProjectionTest.java');
 files.push('src/main/java/com/codex/blacklight/client/animation/HurtFlashWindow.java','src/test/java/com/codex/blacklight/client/animation/HurtFlashWindowTest.java');
 files.push('src/main/java/com/codex/blacklight/client/animation/BladeConsumePose.java','src/main/java/com/codex/blacklight/client/animation/ConsumeGripPose.java','src/test/java/com/codex/blacklight/client/animation/BladeConsumePoseTest.java','src/main/java/com/codex/blacklight/client/renderer/ContactSection.java','src/test/java/com/codex/blacklight/client/renderer/ContactSectionTest.java','tools/flyer-handoff-test.init.gradle');
 files.push('src/main/java/com/codex/blacklight/client/renderer/RagdollRootSelection.java','src/test/java/com/codex/blacklight/client/renderer/RagdollRootSelectionTest.java');
 files.push('src/main/java/com/codex/blacklight/client/renderer/RagdollRecovery.java','src/test/java/com/codex/blacklight/client/renderer/RagdollRecoveryTest.java');
 files.push('src/main/java/com/codex/blacklight/client/renderer/RagdollTerrainSupport.java','src/test/java/com/codex/blacklight/client/renderer/RagdollTerrainSupportTest.java');
 files.push('src/main/java/com/codex/blacklight/client/renderer/ConsumeBladeContact.java','src/main/java/com/codex/blacklight/client/renderer/WoundBlood.java');
 report.platforms.push({platform,frames:e.frames.length,manifest:dir+'evidence.json',manifestSha256:hash(dir+'evidence.json'),cases,units,sheets:Array.from({length:5},(_,s)=>({file:dir+`handoff-review-${s}.jpg`,sha256:hash(dir+`handoff-review-${s}.jpg`)})),files:files.map(f=>({file:f,sha256:hash(roots[i]+'/'+f)}))});
}
const source='D:/PrototypeReference/p1/input/alex_fig.p3d.rz',original=JSON.parse(fs.readFileSync('D:/PrototypeReference/p1/audit.json')),raw=fs.readFileSync(source),inflated=zlib.inflateSync(raw.subarray(16));
assert.equal(hash(source),original.sourceFiles['alex_fig.p3d.rz'].sha256);assert.equal(inflated.length,original.sourceFiles['alex_fig.p3d.rz'].inflatedBytes);
const markers=[498548,498668].map(offset=>({offset,payloadLength:inflated.readUInt32LE(offset+8),seconds:inflated.readFloatLE(offset+16)}));
for(const marker of markers){assert.equal(marker.payloadLength,100);assert(Math.abs(marker.seconds-.833333)<1e-6);}
report.originalBladeSource={file:source,sha256:hash(source),inflatedBytes:inflated.length,markers,boundary:'TimeBegin bytes were re-read directly. SpawnGib ownership/names use the existing reverse-engineered audit schema. Two source spawn events do not imply two full-body cutting planes or arbitrary mesh slicing.'};
report.sourceBoundary='Native P2 flyer idle wing-fold curves are reused on its existing MC rig. The P1 blade receiver split marker is verified at 0.833333 seconds. Horizontal-body carry orientation, human receiver retargeting, blade arm fitting and runtime mesh sections are MC adaptations, not a claim that an original flyer absorption sequence has been recovered.';
report.fixes.push('Rigid blade grip uses the actual forearm-bound mesh anchor, preserving the original anchor before wrist-space conversion','Native receiver facing follows the paired scene before entity yaw interpolation catches up','Removed victims cannot access invalidated Forge capabilities through RagdollDrive.captured');
report.fixes.push('Finite hurt-flash duration survives a client world-clock rewind without extending a corpse red overlay until the old absolute deadline');
report.fixes.push('Consume cutting uses the same connected-joint projection and component support lift as the rendered skin, instead of independently interpolated bone positions');
report.fixes.push('Fit the right arm using actual sharp-profile points near the authored blade marker; preserve arm length, wrist, left arm and root','One authored blade slice can be authorized by either sharp side, with independent sampled paths but a shared completion event; separate claws retain their own sections','Prefer confirmed central tissue contact over a broad wing bounding volume');
report.fixes.push('Choose the physical root through mesh/bone ancestry, then prefer the trunk among sibling roots; broad child wings no longer invert their skeleton');
report.fixes.push('Evaluate all visible sharp profiles before selecting the shared blade section','Use exact two-bone fitting across the actual authored slicing poses; the previous CCD candidate search failed a reachable source pose','Bind both sever blood emitters to actual cap triangles and their component motion; sharp absorption no longer substitutes a timed body-center spray');
report.cutCountMeaning='cuts counts intersected model parts split by confirmed sections; it is not the number of blade strokes or unique cutting planes.';
report.firstHoldWithinBound=report.platforms.every(p=>p.cases.every(c=>(c.gripMax||0)<.1));
report.pending=['The bounded native blade cases cut the actual thorax into two sides without duplicate blade-side sections; arbitrary receiver shapes and final section appearance still need a broader matrix','Capture/hold/consume transition continuity and skin/terrain clearance through the whole sequence','High-speed thin obstacles, uneven floors and multiplayer/crowd/mod matrix','Remaining creatures, audio and original-game combat acceptance; whole active goal remains incomplete'];
report.pending.unshift('Wrong wing-root ownership is fixed in these native cases; preSeverEdgeToThoraxCenter records the improved proximity. Retained real skin contacts still authorize sections, and continuous contact timing across varied receivers remains unaccepted.');
if(!report.firstHoldWithinBound)report.pending.unshift('First HOLD frame still exceeds the grip bound in this run; all outliers retained in cases');
report.measurementBoundary='The cut-query audit compares the production connectedPose result with each same-frame rendered part position (including support lift), and reports the difference from raw interpolated physics as a diagnostic. It is not an acceptance of the receiver choreography or final topology. Receiver triangles come from native baked-mesh world matrices. A QA-only mixin records the mature weapon anchor inside BlacklightClawLayer.renderMesh using the exact draw PoseStack and blended wrist deformation; it does not re-run animation. Render-time equality and mature rigid material are asserted for every held frame. Independent post-frame pose samples remain as a diagnostic, not acceptance authority. Distances can include an anchor embedded inside the receiver surface. This does not measure liquid/growing weapons or certify every hand/receiver contact throughout consumption.';
report.validation='Both native clients compiled and ran five scripted gameplay scenes. Successful damage, last-chance ownership, live-body integrity, actual drawn grip-to-native-triangle distances, two-sided thorax sections, one event per authored blade slice, short hurt overlays and completion were checked. Twelve focused unit suites (56 cases per platform) cover backwards-clock invalidation, finite blade/skin intersection, delayed cut generations, connected joint projection, actual sharp-profile fitting without changing arm lengths, original split timing, hierarchy-aware root choice across sparse/forest/cyclic rigs, actual skin support on slabs/steps, connected side-wall separation, finite recovery/current joint anchors and native-renderer offset settling, and finite hit flashes across clock rewinds. Prior broad server/unit regression checkpoints are not represented as rerun by this report.';
report.visualReview=process.argv.includes('--reviewed')?{status:'keyframe_sheets_reviewed',sheets:10,framesPerSheet:16,boundary:'All frame metadata were checked; visual inspection covers the ten selected keyframe sheets, not a manual pixel review of every captured frame.'}:{status:'pending_independent_image_review'};
for(const root of roots)fs.writeFileSync(root+'/art/encounters/flyer-handoff-validation.json',JSON.stringify(report,null,2)+'\n');
// Retain the reviewed run only. No recursive deletion, directory removal or backups.
for(const p of report.platforms){
 const dir=p.manifest.slice(0,-'evidence.json'.length),real=fs.realpathSync(dir).replaceAll('\\','/').toLowerCase();
 assert(real===`d:/codexcache/creature-combat-qa/${p.platform}-flyer-handoff-frames`);
 const keep=new Set(JSON.parse(fs.readFileSync(p.manifest)).frames.map(f=>f.image));
 for(const name of fs.readdirSync(dir))if(/^[0-4]-(claws_absorb|blade_absorb|claws_head_absorb|release_alive|lethal_blade)-\d{3}(-sub[12])?\.png$/.test(name)&&!keep.has(name))fs.unlinkSync(dir+name);
}
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,frames:p.frames,cases:p.cases.map(c=>({scene:c.scene,grip:c.gripMax,lowest:c.heldLowest,cuts:c.cuts}))}))));
