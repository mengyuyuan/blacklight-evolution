import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const names=['hammer_brute_survive','hammer_brute_lethal','blade_brute_lethal','muscle_brute_consume','claws_brute_consume'];
const read=f=>JSON.parse(fs.readFileSync(f,'utf8'));
const hash=f=>crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');
const runtime=['src/main/java/com/codex/blacklight/client/animation/BruteHitPose.java','src/main/java/com/codex/blacklight/client/renderer/BlacklightCreatureRenderer.java','src/main/java/com/codex/blacklight/client/model/BlacklightCreatureModel.java','src/main/java/com/codex/blacklight/client/renderer/AdaptiveRagdoll.java','src/main/java/com/codex/blacklight/combat/WeaponImpact.java','src/main/java/com/codex/blacklight/combat/ConsumeSequence.java','src/main/resources/assets/blacklight/geo/blacklight_brute_mc.geo.json'];
const probe='src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAudit.java';
const report={date:new Date().toISOString(),scope:'Brute physical-body entry, blunt/edged death and mounted-consume completion through production input packets; visual issues remain open.',platforms:[],pending:[
 'Mounted claws/strength consume completes, but rider/back contact differs between views/platforms and remains visually unaccepted. No hand, knee or seat contact assertion exists yet.',
 'The hammer charged-attack recovery shows the player sunk deeply near the floor. Victim mesh checks do not accept the attacker animation or root motion.',
 'Live knockdown, DOWN/GETUP control-to-physics timing, terrain, all damage sources, network latency and full species-specific consume choreography remain unverified.',
 'No audible mixing review or release package. Other creatures, military AI, helicopters and detailed vehicle items remain in the full goal.'
]};
for(const [i,platform] of ['neo','forge'].entries()){
 const root=roots[i],dir=`D:/CodexCache/creature-combat-qa/${platform}-brute-lifecycle-frames/`;
 const latest=Math.max(...[...runtime,probe].map(f=>fs.statSync(root+'/'+f).mtimeMs));
 const checks=read(dir+'case-checks.json');assert.deepEqual(checks.map(c=>c.case),names);
 assert(fs.readFileSync(dir+'capture-status.txt','utf8').startsWith('Completed 5 cases. Dropped frames=0.'));
 const log=`D:/CodexCache/creature-combat-${platform}-genericRagdollVisual.log`;assert(fs.readFileSync(log,'utf8').includes('BUILD SUCCESSFUL'));
 const cases=[];
 for(const check of checks){
  const file=dir+check.case+'.json',rows=read(file),sheet=dir+check.case+'-review.jpg';
  assert(check.passed,platform+'/'+check.case);assert(rows.length>=150);assert(fs.statSync(file).mtimeMs>latest);
  assert(rows.filter(r=>'noAi' in r).length>=50&&rows.filter(r=>'noAi' in r).every(r=>r.noAi===false));
  for(const r of rows)assert(r.imageQueued&&fs.existsSync(dir+r.image));
  assert(fs.statSync(sheet).mtimeMs>=fs.statSync(file).mtimeMs);
  let entryDifference=null;
  if(check.case.endsWith('_lethal')){
   assert.equal(check.minHealth,0);assert.equal(check.bodyInstances,1);assert(check.jointSamples>15);
   assert(check.maxRenderJointGap<.001&&check.renderedMeshSamples>15&&check.renderedMeshPenetrations===0);
   const n=rows.findIndex(r=>r.brutePhysical===true);assert(n>0);
   const before=rows[n-1].brutePose,after=rows[n].brutePose;assert(before?.length===8&&after?.length===8);
   entryDifference=Math.max(...after.flatMap((p,b)=>p.map((v,k)=>Math.abs(v-before[b][k]))));assert(entryDifference<.00001,'No inserted generic flinch at physical entry');
   if(check.case.startsWith('hammer_'))assert.equal(check.maxComponents,1);else assert(check.maxCuts>0&&check.maxComponents>1);
  }else if(check.case.endsWith('_consume')){
   assert(check.mounted&&check.consumeCommitted&&check.minHealth===0&&check.expectedSplit===false&&check.consumeFrames>25);
   const stages=new Set(rows.map(r=>r.grab));for(const s of [7,8,9])assert(stages.has(s));assert.equal(rows.at(-1).grab,0);
  }else assert(check.minHealth>0&&check.minHealth<400&&check.maxComponents===0);
  cases.push({name:check.case,frames:rows.length,check,entryDifference,file,sha256:hash(file),contact:{file:dir+check.case+'-contact.json',sha256:hash(dir+check.case+'-contact.json')},sheet:{file:sheet,sha256:hash(sheet)}});
 }
 report.platforms.push({platform,frames:cases.reduce((n,c)=>n+c.frames,0),cases,log:{file:log,sha256:hash(log)},sources:[...runtime,probe].map(file=>({file,sha256:hash(root+'/'+file)}))});
}
report.review={status:process.argv.includes('--reviewed')?'ten_native_keyframe_sheets_reviewed_with_open_actor_and_mount_issues':'pending',sheets:process.argv.includes('--reviewed')?10:0,everyFrameManuallyReviewed:false,fullChoreographyAccepted:false};
for(const root of roots)fs.writeFileSync(root+'/art/mutant-combat/brute-lifecycle-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,frames:p.frames,cases:p.cases.length,physicalEntry:p.cases.filter(c=>c.entryDifference!==null).map(c=>({case:c.name,maxDifference:c.entryDifference}))}))));
