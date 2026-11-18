import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const names=['muscle_brute_consume','claws_brute_consume'];
const read=f=>JSON.parse(fs.readFileSync(f,'utf8'));
const hash=f=>crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');
const artifact=file=>({file,sha256:hash(file)});
const sources=[
 'src/main/java/com/codex/blacklight/client/animation/BruteRiderPose.java',
 'src/main/java/com/codex/blacklight/combat/BruteMountedBody.java',
 'src/main/java/com/codex/blacklight/client/animation/NativeConsumePose.java',
 'src/main/java/com/codex/blacklight/client/animation/ConsumeVictimPose.java',
 'src/main/java/com/codex/blacklight/client/model/BlacklightCreatureModel.java',
 'src/main/java/com/codex/blacklight/client/animation/BlacklightBoneIk.java',
 'src/main/java/com/codex/blacklight/combat/ConsumeSequence.java',
 'src/main/java/com/codex/blacklight/combat/ConsumeSystem.java',
 'src/main/java/com/codex/blacklight/combat/BruteContact.java',
 'src/main/resources/assets/blacklight/geo/blacklight_brute_mc.geo.json',
 'src/main/resources/assets/blacklight/models/form_muscle_mass.json',
 'src/main/resources/assets/blacklight/models/claw_blender.json',
 'src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAudit.java',
 'src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAuditRider.java',
 'src/test/java/com/codex/blacklight/client/animation/BruteRiderPoseTest.java',
 'tools/brute-test.init.gradle'
];
const report={date:new Date().toISOString(),scope:'Brute planted mounted body and fitted rider support for stable claws/muscle forms; complete hold/consume/release flows in native side and rear views.',platforms:[],baseline:[],limits:[
 'Native distances reference the rendered torso cuboids only; they do not prove clearance from Brute head, arms or legs.',
 'Grip checks use the minimum signed distance of emitted hand mesh vertices, not a particular palm landmark or articulated fingers. Skin penetration is bounded to 0.02 blocks, not mathematically zero.',
 'The right strike arm is intentionally free during the authored attack. Its penetration, exact tissue contact/damage timing and complete consume choreography remain unaccepted.',
 'Hold entry is tested after the real client PhaseEntryBlend reaches one, not a fixed count from the first network-stage row. Initial mount, seat/body fit, all triangle interiors, alternate skins/scales, weapon morph transitions and network latency need broader review.',
 'Live Brute DOWN/GETUP and physics handoff, remaining hammer idle/charge/jab overlap, other species, military AI, helicopters, fine vehicle/item models and audible mixing remain in the active goal. No release package.'
]};
for(const [index,platform] of ['neo','forge'].entries()){
 // Headless test fixtures may be refined after capture; native evidence must postdate runtime and probe code.
 const root=roots[index];const latest=Math.max(...sources.filter(f=>f.startsWith('src/main/')||f.startsWith('src/ragdollVisualTest/')).map(f=>fs.statSync(root+'/'+f).mtimeMs));
 const cases=[];
 for(const view of ['side','rear']){
  const dir=`D:/CodexCache/creature-combat-qa/${platform}-brute-rider-final-${view}/`;
  const checks=read(dir+'case-checks.json');assert.deepEqual(checks.map(c=>c.case),names);
  assert(fs.readFileSync(dir+'capture-status.txt','utf8').startsWith('Completed 2 cases. Dropped frames=0.'));
  assert(fs.readFileSync(dir+'native-build.log','utf8').includes('BUILD SUCCESSFUL'));
  for(const check of checks){
   const file=dir+check.case+'.json',rows=read(file),sheet=dir+check.case+'-review.jpg';
   assert(check.passed,platform+'/'+view+'/'+check.case);assert(rows.length>150);assert(fs.statSync(file).mtimeMs>latest);
   assert(check.consumeCommitted&&check.minHealth===0&&check.expectedSplit===false);
   assert(check.riderMeshSamples>60&&check.riderGripSamples>80);
   assert(check.riderSupportDepth<.005&&check.riderSkinDepth<=.02&&check.riderGripMinGap>=-.005&&check.riderGripMaxGap<.03);
   assert(check.mountBodyYawError<.01&&check.mountActorYawError<.01&&check.mountAnchorError<.35);
   assert.equal(rows.at(-1).grab,0);for(const r of rows)assert(r.imageQueued&&fs.existsSync(dir+r.image));
   assert(fs.statSync(sheet).mtimeMs>=fs.statSync(file).mtimeMs);
   const missing=new Set([7,8,9]);for(const r of rows)missing.delete(r.grab);assert.equal(missing.size,0);
   const meshRows=rows.filter(r=>r.riderMeshTorso&&r.mountStage===9);
   const prefix=check.case.startsWith('claws')?'claws':'muscle_mass';
   const freeStrikeMaxDepth=Math.max(0,...meshRows.map(r=>r.riderMeshTorso[prefix+'_Hand_R']?.maxVertexPenetration??0));
   cases.push({name:check.case,view,frames:rows.length,check,freeStrikeMaxDepth,data:artifact(file),sheet:artifact(sheet),log:artifact(dir+'native-build.log')});
  }
 }
 const dir=`D:/CodexCache/creature-combat-qa/${platform}-brute-rider-final-side/`;
 const server=dir+'server-check.log',unit=dir+'unit-check.log';
 assert(fs.readFileSync(server,'utf8').includes('All 28 required tests passed'));
 assert(fs.readFileSync(unit,'utf8').includes('BUILD SUCCESSFUL'));
 const xml=fs.readdirSync(dir).filter(f=>f.startsWith('TEST-')&&f.endsWith('.xml')).map(f=>dir+f);
 let tests=0;for(const f of xml){const s=fs.readFileSync(f,'utf8').match(/<testsuite\b[^>]+>/)?.[0];assert(s);assert(s.includes('failures="0"')&&s.includes('errors="0"'));tests+=Number(s.match(/\btests="(\d+)"/)[1]);}assert.equal(tests,39);
 report.platforms.push({platform,frames:cases.reduce((n,c)=>n+c.frames,0),cases,unitTests:tests,serverTests:28,unitLog:artifact(unit),serverLog:artifact(server),unitXml:xml.map(artifact),sources:sources.map(file=>({file,sha256:hash(root+'/'+file)}))});
 const before=`D:/CodexCache/creature-combat-qa/${platform}-brute-rider-before-${platform==='neo'?'side':'rear'}/`;
 for(const name of names){const file=before+name+'.json';const rows=read(file).filter(r=>r.grab===8&&r.tick>=55&&r.riderJointTorsoGap);assert(rows.length>10);
  report.baseline.push({platform,name,data:artifact(file),sampledHoldMaxGap:Object.fromEntries(['Tool_L','Tool_R','Leg_L','Leg_R'].map(j=>[j,Math.max(...rows.map(r=>r.riderJointTorsoGap[j]))]))});
 }
}
report.review={status:process.argv.includes('--reviewed')?'eight_native_side_rear_keyframe_sheets_reviewed':'pending',sheets:process.argv.includes('--reviewed')?8:0,everyFrameManuallyReviewed:false,fullChoreographyAccepted:false};
for(const root of roots)fs.writeFileSync(root+'/art/mutant-combat/brute-rider-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,frames:p.frames,cases:p.cases.length,unitTests:p.unitTests,serverTests:p.serverTests,maxSupportDepth:Math.max(...p.cases.map(c=>c.check.riderSupportDepth)),maxSkinDepth:Math.max(...p.cases.map(c=>c.check.riderSkinDepth)),maxGripGap:Math.max(...p.cases.map(c=>c.check.riderGripMaxGap))}))));
