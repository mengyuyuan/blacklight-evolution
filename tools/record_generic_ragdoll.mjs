import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const roots={neo:'D:/blacklight-evolution',forge:'D:/blacklight-evolution-forge-1.20.1'};
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const common=['hammer_cow_connected_survive','blade_tissue_host_pig_lethal','claws_tissue_host_consume','blade_pig_side','blade_skeleton','claws_cow','blade_consume','blade_cow_consume','claws_cow_consume','claws_head_consume'];
const steps=['hammer_cow_connected_survive','blade_pig_side','claws_cow','blade_cow_consume'];
const report={status:'bounded_generic_model_and_step_support_checkpoint',completedAt:new Date().toISOString(),wholeGoalComplete:false,releasePackaged:false,platforms:{}};
const sourceFiles=['src/main/java/com/codex/blacklight/client/renderer/AdaptiveRagdoll.java','src/main/java/com/codex/blacklight/client/renderer/RagdollTerrainSupport.java','src/main/java/com/codex/blacklight/client/renderer/RagdollRootSelection.java','src/main/java/com/codex/blacklight/client/renderer/WoundBlood.java','src/main/java/com/codex/blacklight/client/animation/BladeConsumePose.java','src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAudit.java','src/test/java/com/codex/blacklight/client/renderer/RagdollTerrainSupportTest.java','tools/generic-ragdoll-visual.init.gradle','tools/review_generic_ragdoll.py'];
for(const [platform,repo] of Object.entries(roots)){
 const productionModified=Math.max(...sourceFiles.slice(0,5).map(p=>fs.statSync(repo+'/'+p).mtimeMs));
 const runs={};
 for(const terrain of ['flat','steps']){
  const directory=`D:/CodexCache/creature-combat-qa/${platform}-generic-ragdoll-frames`+(terrain==='steps'?'/steps':'');
  const checks=read(directory+'/case-checks.json');
  const expected=terrain==='steps'?steps:platform==='neo'?[...common,'blade_wilden_lethal','claws_wilden_consume','blade_drygmy_lethal']:common;
  assert.deepEqual(checks.map(r=>r.case).sort(),[...expected].sort());
  assert(checks.every(r=>r.passed),`${platform} ${terrain} failed assertions`);
  assert(fs.statSync(directory+'/case-checks.json').mtimeMs>=productionModified,'Native run predates the production fix');
  const imageNames=new Set(),sequences=[];
  for(const check of checks){
   const rows=read(directory+'/'+check.case+'.json');assert(rows.length>150);
   for(const row of rows){assert(row.imageQueued,`${check.case}: dropped frame`);assert(fs.existsSync(directory+'/'+row.image));imageNames.add(row.image);}
   assert.equal(check.bodyInstances,1);assert(check.cutsPreserved);assert.equal(check.prematureGrabBodies,0);
   if(terrain==='steps'){assert(check.terrainFrames>10);assert.equal(check.terrainPenetratingFrames,0);}
   const sheet=directory+'/'+check.case+'-review.jpg';assert(fs.existsSync(sheet));
   sequences.push({...check,frames:rows.length,evidenceSha256:hash(directory+'/'+check.case+'.json'),reviewSheet:sheet,reviewSha256:hash(sheet)});
  }
  // Remove only superseded PNG frame names in this exact D: audit directory, never saves or subdirectories.
  for(const name of fs.readdirSync(directory))if(expected.some(c=>name.startsWith(c+'_'))&&/_[0-9]{4}\.png$/.test(name)&&!imageNames.has(name))fs.unlinkSync(directory+'/'+name);
  runs[terrain]={directory,frames:imageNames.size,checkSha256:hash(directory+'/case-checks.json'),sequences};
 }
 const testDir=`D:/CodexCache/creature-combat-qa/${platform==='neo'?'neo-build':'forge-render-build'}/test-results/test`;
 const units=fs.readdirSync(testDir).filter(f=>/^TEST-.*(RagdollPoseHistory|SweptBladeSurface|BladeCutSequence|RagdollJointProjection|BlacklightBoneIk|HurtFlashWindow|BladeConsumePose|ContactSection|RagdollRootSelection|RagdollTerrainSupport)Test\.xml$/.test(f)).map(name=>{
  const xml=fs.readFileSync(testDir+'/'+name,'utf8'),count=Number(xml.match(/tests="(\d+)"/)[1]);assert.match(xml,/failures="0"/);assert.match(xml,/errors="0"/);return {file:name,count,sha256:hash(testDir+'/'+name)};
 });assert.equal(units.length,11);assert.equal(units.reduce((n,u)=>n+u.count,0),47);
 report.platforms[platform]={runs,units,sources:Object.fromEntries(sourceFiles.map(p=>[p,hash(repo+'/'+p)]))};
}
report.regressionBeforeFix=read('D:/CodexCache/ragdoll-terrain-before.json');delete report.regressionBeforeFix.source;
report.fix='Clip each native mesh quad to the horizontal footprint of nearby actual voxel collision boxes. Compute the lowest covered skin height, then lift each connected component by its greatest required support correction. This covers slab and step edges missed by a center ray without treating empty mesh/AABB corners as solid skin.';
report.measurement='All saved-frame metadata and gameplay assertions were checked. Penetration measures the shortest exit distance for actual drawn mesh vertices inside actual collision boxes, with a 0.02-block rejection threshold. This is not a swept face/edge collision proof. Contact topology and physical dynamics can produce different fragment counts between runs.';
report.visualReview=process.argv.includes('--reviewed')?{status:'keyframe_sheets_reviewed',sheets:31,selectedFramesPerSheet:20,manualPixelReviewOfEveryFrame:false}:{status:'pending'};
report.coverage={vanilla:['pig','cow','villager','skeleton'],infection:['pig host with mature growth','villager host with mature growth'],externalMods:{neo:['Ars Nouveau 5.13.3 wilden_hunter','Ars Nouveau 5.13.3 drygmy'],forge:[]},terrain:['flat concrete','half-height smooth-stone slabs','stacked slab/full-block steps beside a wall']};
report.remaining=['The live hammer-hit cow still visibly snaps during the return from its physics pose; the Forge wall fixture also shows an upward pop during recovery. This is a distinct recovery/collision-continuity defect, not accepted as complete physics.','Forge external-mod runtime coverage has not been rerun with an installed external mod in this checkpoint.','Arbitrary side-wall, ceiling, thin face/edge collisions, water, very large models, multiplayer and full per-species choreography are not accepted here.','Volumes above the bounded local scan budget retain the existing large-body fallback.','The full creature AI/action/mechanism/audio/VFX goal and release acceptance remain active.'];
for(const root of Object.values(roots))fs.writeFileSync(root+'/art/encounters/generic-ragdoll-terrain-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({platforms:Object.fromEntries(Object.entries(report.platforms).map(([p,r])=>[p,{frames:Object.values(r.runs).reduce((n,r)=>n+r.frames,0),scenes:Object.values(r.runs).reduce((n,r)=>n+r.sequences.length,0),units:47}])),visual:report.visualReview.status,wholeGoalComplete:false}));
