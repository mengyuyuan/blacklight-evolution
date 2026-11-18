import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const read=file=>JSON.parse(fs.readFileSync(file,'utf8'));
const sha=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const evidence=file=>({file,sha256:sha(file)});
const previous=read(roots[0]+'/art/mutant-combat/hammer-contact-validation.json');
const sources=previous.platforms[0].sources.map(s=>s.file);
const expected=['hammer_brute_survive','hammer_brute_plain_survive','hammer_brute_frontslab_survive','hammer_brute_sideblock_miss','hammer_brute_ledge_survive','hammer_brute_lethal'];
const report={date:new Date().toISOString(),scope:'Released ordinary/charged hammer quake on flat ground, a front slab, a side block and a ledge. Vertex-to-collision-shape clearance is checked independently in native rendering; complete wall, charge, idle, body and multiplayer acceptance remains open.',
 baseline:{failureExcerpts:evidence('D:/CodexCache/work/hammer-terrain-before.txt'),source:previous.platforms[0].sources.find(s=>s.file.endsWith('/HammerGroundPose.java')),frontSlabPenetration:.14422976970672607,sideBlockPenetration:.3650587201118469},
 platforms:[],pending:[
  'The support solve samples solid top faces within feet +/-1.25 blocks. Tall walls, ceilings, thin diagonal surfaces, triangle interiors between sampled vertices and arbitrary terrain are not fully covered.',
  'Unkeyed/idle, charge and jab poses are outside this released-quake constraint; their measured terrain overlap is retained per case and is not accepted.',
  'Native samples use the default player rig. Modded skins, scale combinations, multiplayer delay and audible mixing remain open.',
  'Brute rider mesh contact, live DOWN/GETUP handoff, final anatomy/materials and the remaining creature and military goal remain open.'
 ]};
for(const [i,platform] of ['neo','forge'].entries()){
 const root=roots[i],dir=`D:/CodexCache/creature-combat-qa/${platform}-hammer-terrain-final/`;
 const checks=read(dir+'case-checks.json');assert.deepEqual(checks.map(c=>c.case),expected);
 assert(fs.readFileSync(dir+'capture-status.txt','utf8').startsWith('Completed 6 cases. Dropped frames=0.'));
 const latest=Math.max(...sources.map(file=>fs.statSync(root+'/'+file).mtimeMs));
 const cases=checks.map(check=>{
  const file=dir+check.case+'.json',rows=read(file),sheet=dir+check.case+'-review.jpg';
  assert(check.passed&&check.damagePassed&&check.actorTerrainFrames>50&&check.actorTerrainPenetration<=.02);
  assert(fs.statSync(file).mtimeMs>latest&&fs.statSync(sheet).mtimeMs>=fs.statSync(file).mtimeMs);
  assert(rows.length>150);for(const row of rows)assert(row.imageQueued&&fs.existsSync(dir+row.image));
  const released=rows.filter(r=>['hammer_quake','hammer_quake_charged'].includes(r.actorClip));
  assert(released.length>50);const clips=[...new Set(released.map(r=>r.actorClip))];
  assert.deepEqual(clips,[check.case.includes('_plain_')?'hammer_quake':'hammer_quake_charged']);
  const excluded={};for(const row of rows)if(!['hammer_quake','hammer_quake_charged'].includes(row.actorClip)&&row.actorHammerTerrainVertices>0){
   const key=row.actorClip??'unkeyed_or_idle';excluded[key]=Math.max(excluded[key]??0,row.actorHammerTerrainPenetration);
  }
  if(check.case.endsWith('_survive'))assert.equal(check.minHealth,300);
  else if(check.case.endsWith('_miss'))assert.equal(check.minHealth,8);
  else assert(check.minHealth===0&&check.bodyInstances===1&&check.maxComponents===1&&check.renderedMeshPenetrations===0);
  return {name:check.case,frames:rows.length,check,measurements:{releasedFrames:released.length,clips,
   maximumTerrainPenetration:Math.max(...released.map(r=>r.actorHammerTerrainPenetration)),
   minimumFistRelativeToFeet:Math.min(...released.flatMap(r=>Object.values(r.actorHammerLowest))),
   entityHeightRange:[Math.min(...released.map(r=>r.actorPosition[1])),Math.max(...released.map(r=>r.actorPosition[1]))],
   excludedPosePenetration:excluded},...evidence(file),sheet:evidence(sheet)};
 });
 const units=[['HammerGroundContactTest',3],['HammerGroundPoseTest',2]].map(([name,tests])=>{
  const file=dir+name+'.xml',xml=fs.readFileSync(file,'utf8');
  assert(xml.includes(`tests="${tests}"`)&&xml.includes('failures="0"')&&xml.includes('errors="0"'));
  return {tests,...evidence(file)};
 });
 const server=dir+'server-check.log',native=dir+'native-build.log',debug=dir+'native-debug.log';
 const serverText=fs.readFileSync(server,'utf8'),nativeText=fs.readFileSync(native,'utf8');
 assert(serverText.includes('All 28 required tests passed')&&serverText.includes('BUILD SUCCESSFUL'));
 assert(nativeText.includes('BUILD SUCCESSFUL')&&nativeText.includes('Ragdoll QA complete; dropped=0'));
 const ground=fs.readFileSync(debug,'utf8').split(/\r?\n/).filter(line=>line.includes('Hammer ground contact move='));
 assert.equal(ground.length,6);assert.equal(ground.filter(line=>line.includes('move=hammer_quake ')).length,1);
 report.platforms.push({platform,frames:cases.reduce((n,c)=>n+c.frames,0),cases,unitTests:5,units,
  server:{tests:28,groundTests:7,...evidence(server)},native:{...evidence(native),debug:evidence(debug),groundEvents:ground},
  sources:sources.map(file=>({file,sha256:sha(root+'/'+file)}))});
}
const reviewed=process.argv.includes('--reviewed');
report.review={status:reviewed?'twelve_native_keyframe_sheets_reviewed':'pending',sheets:reviewed?12:0,everyFrameManuallyReviewed:false,listedReleasedQuakeScenariosAccepted:reviewed,arbitraryTerrainAccepted:false};
for(const root of roots)fs.writeFileSync(root+'/art/mutant-combat/hammer-terrain-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,frames:p.frames,cases:p.cases.map(c=>({name:c.name,...c.measurements}))}))));
