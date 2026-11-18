import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';

const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const read=file=>JSON.parse(fs.readFileSync(file,'utf8'));
const hash=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const evidence=file=>({file,sha256:hash(file)});
const sources=[
 'src/main/java/com/codex/blacklight/combat/HammerGroundPose.java',
 'src/main/java/com/codex/blacklight/combat/HammerQuakeContact.java',
 'src/main/java/com/codex/blacklight/client/animation/HammerGroundContact.java',
 'src/main/java/com/codex/blacklight/client/animation/BlacklightRenderPose.java',
 'src/main/java/com/codex/blacklight/client/renderer/WeaponScaleRenderer.java',
 'src/main/java/com/codex/blacklight/combat/WeaponAttackScales.java',
 'src/main/resources/assets/blacklight/models/form_hammerfists.json',
 'art/motion-profile/hammer_quake.json','art/motion-profile/hammer_quake_charged.json',
 'src/test/java/com/codex/blacklight/client/animation/HammerGroundContactTest.java',
 'src/test/java/com/codex/blacklight/client/animation/HammerGroundPoseTest.java',
 'src/bruteTest/java/com/codex/blacklight/gametest/HammerQuakeGroundGameTest.java',
 'src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAudit.java',
 'src/ragdollVisualTest/java/com/codex/blacklight/client/RagdollVisualAuditActorFloor.java'
];
const baseline='D:/CodexCache/creature-combat-qa/neo-hammer-floor-before/hammer_brute_survive.json';
function measures(rows){
 const released=rows.filter(r=>['hammer_quake','hammer_quake_charged'].includes(r.actorClip)&&r.actorHammerLowest?.['0']!==undefined);
 const recovery=released.filter(r=>r.actorClipSeconds>=1.65);
 assert(released.length>50&&recovery.length>25);
 return {releasedFrames:released.length,minimumFistClearance:Math.min(...released.flatMap(r=>Object.values(r.actorHammerLowest))),
  minimumRecoveryHeadClearance:Math.min(...recovery.map(r=>r.actorSkinLowest['1'])),
  minimumRecoveryFistClearance:Math.min(...recovery.flatMap(r=>Object.values(r.actorHammerLowest))),
  entityHeightRange:[Math.min(...released.map(r=>r.actorPosition[1])),Math.max(...released.map(r=>r.actorPosition[1]))],
  clips:[...new Set(released.map(r=>r.actorClip))]};
}
const report={date:new Date().toISOString(),
 scope:'Shared grounded hammer pose and actual projected fist contact. Native released charged-quake on flat ground, with Brute survival and subsequent jab kill; ordinary quake and slab contact checked on the server. This is not full creature or arbitrary-terrain acceptance.',
 baseline:{...evidence(baseline),measurements:measures(read(baseline))},platforms:[],
 pending:[
  'Support probes sample solid top faces within feet +/-1.25 blocks, using the uncorrected fist footprint. Partial ledges, front steps, changed footprint, walls and constrained spaces need further verification.',
  'Native capture covers the released charged quake; ordinary quake is covered by pose sampling and server contact tests, not a dedicated native recording.',
  'Charge, other attacks and the existing lower-leg/foot mesh below the entity origin are outside the released-quake constraint.',
  'Modded skins, multiplayer latency, audible mixing, final choreography and all remaining creature and military requirements remain open.'
 ]};
for(const [i,platform] of ['neo','forge'].entries()){
 const root=roots[i],dir=`D:/CodexCache/creature-combat-qa/${platform}-hammer-contact-frames/`;
 const checks=read(dir+'case-checks.json');
 assert.deepEqual(checks.map(c=>c.case),['hammer_brute_survive','hammer_brute_lethal']);
 assert(fs.readFileSync(dir+'capture-status.txt','utf8').startsWith('Completed 2 cases. Dropped frames=0.'));
 const latest=Math.max(...sources.map(file=>fs.statSync(root+'/'+file).mtimeMs));
 const cases=checks.map(check=>{
  const file=dir+check.case+'.json',rows=read(file),sheet=dir+check.case+'-review.jpg';
  assert(check.passed&&check.damagePassed&&check.actorAttackFrames>50&&check.actorFullFistClearance>=-.02);
  assert(check.actorRecoveryFrames>25&&check.actorRecoveryHeadClearance>.25&&check.actorRecoveryFistClearance>=-.02);
  assert(fs.statSync(file).mtimeMs>latest&&fs.statSync(sheet).mtimeMs>=fs.statSync(file).mtimeMs);
  assert(rows.length>150);for(const row of rows)assert(row.imageQueued&&fs.existsSync(dir+row.image));
  const measurements=measures(rows);assert(measurements.entityHeightRange[1]-measurements.entityHeightRange[0]<.0001);
  let damageFrame;
  if(check.case.endsWith('_survive')){
   assert(check.minHealth===300);
   const row=rows.find((r,j)=>j&&r.health<rows[j-1].health);
   assert(row?.actorClip==='hammer_quake_charged'&&row.actorHammerLowest['0']<.065);
   damageFrame={tick:row.tick,clipSeconds:row.actorClipSeconds,rightFistClearance:row.actorHammerLowest['0'],image:row.image};
  }else assert(check.minHealth===0&&check.bodyInstances===1&&check.maxComponents===1&&check.renderedMeshPenetrations===0);
  return {name:check.case,frames:rows.length,check,measurements,damageFrame,...evidence(file),sheet:evidence(sheet)};
 });
 const build=platform==='neo'?'neo-build':'forge-render-build';
 const units=[['HammerGroundContactTest',3],['HammerGroundPoseTest',2]].map(([name,tests])=>{
  const file=`D:/CodexCache/creature-combat-qa/${build}/test-results/test/TEST-com.codex.blacklight.client.animation.${name}.xml`;
  const xml=fs.readFileSync(file,'utf8');assert(xml.includes(`tests="${tests}"`)&&xml.includes('failures="0"')&&xml.includes('errors="0"'));
  return {tests,...evidence(file)};
 });
 const server=`D:/CodexCache/creature-combat-${platform}-bruteServer.log`,native=`D:/CodexCache/creature-combat-${platform}-genericRagdollVisual.log`;
 const serverText=fs.readFileSync(server,'utf8'),nativeText=fs.readFileSync(native,'utf8');
 assert(serverText.includes('All 25 required tests passed')&&serverText.includes('BUILD SUCCESSFUL'));
 assert(nativeText.includes('BUILD SUCCESSFUL')&&nativeText.includes('Ragdoll QA complete; dropped=0'));
 const debug=dir+'native-debug.log';
 const contacts=fs.readFileSync(debug,'utf8').split(/\r?\n/).filter(line=>line.includes('Hammer ground contact move=hammer_quake_charged'));
 assert.equal(contacts.length,2,'one ground event per released charged quake');
 report.platforms.push({platform,frames:cases.reduce((n,c)=>n+c.frames,0),cases,unitTests:5,units,
  server:{tests:25,newGroundTests:4,...evidence(server)},native:{...evidence(native),debug:evidence(debug),groundEvents:contacts},
  sources:sources.map(file=>({file,sha256:hash(root+'/'+file)}))});
}
report.review={status:process.argv.includes('--reviewed')?'four_native_keyframe_sheets_reviewed':'pending',
 sheets:process.argv.includes('--reviewed')?4:0,everyFrameManuallyReviewed:false,flatReleasedChargedQuakeAccepted:process.argv.includes('--reviewed'),arbitraryTerrainAccepted:false};
for(const root of roots)fs.writeFileSync(root+'/art/mutant-combat/hammer-contact-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,frames:p.frames,cases:p.cases.map(c=>({name:c.name,...c.measurements,damageFrame:c.damageFrame}))}))));
