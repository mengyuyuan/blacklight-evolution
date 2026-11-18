import fs from 'node:fs';
import crypto from 'node:crypto';
const roots=['D:/blacklight-evolution/','D:/blacklight-evolution-forge-1.20.1/'];
const cache='D:/CodexCache/',dir=cache+'creature-combat-qa/terrain-impact-frames/';
const check=(condition,message)=>{if(!condition)throw Error(message);};
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
check(fs.readFileSync(cache+'terrain-queue-status.txt','utf8')==='ALL PASSED','Verification queue unfinished');
const tests=['neo','forge'].map(platform=>{
 const log=fs.readFileSync(cache+`creature-combat-${platform}-encounterServer.log`,'utf16le');
 check(log.includes('All 56 required tests passed')&&log.includes('BUILD SUCCESSFUL'),platform+' server regression incomplete');
 const build=cache+'creature-combat-qa/'+(platform==='forge'?'forge-render-build':'neo-build')+'/';let unit=0;
 for(const name of ['JuggernautContactTest','EncounterMoveTest','HunterContactTest']){
  const xml=fs.readFileSync(build+'test-results/test/TEST-com.codex.blacklight.combat.'+name+'.xml','utf8');
  const header=xml.match(/<testsuite\s+[^>]+>/)[0];
  for(const field of ['failures','errors','skipped'])check(Number(header.match(new RegExp(field+'="(\\d+)"'))?.[1]||0)===0,platform+' '+name+' '+field);
  unit+=Number(header.match(/tests="(\d+)"/)[1]);
 }
 check(unit>=36,'Guard surface regression missing');
 return {platform,unitTests:unit,requiredGameTests:56};
});
const e=JSON.parse(fs.readFileSync(dir+'evidence.json','utf8'));
check(e.scenes===8&&e.frames.length>=480,'Incomplete frame scenes');
for(const row of e.frames)check(fs.existsSync(dir+row.image),'Missing '+row.image);
const scenes=Array.from({length:8},(_,scene)=>{
 const rows=e.frames.filter(r=>r.scene===scene);check(rows.length>40,'Missing scene '+scene);
 return {scene,frames:rows.length,maxGround:Math.max(...rows.map(r=>r.groundBursts)),maxBody:Math.max(...rows.map(r=>r.bodyBursts)),maxTissue:Math.max(...rows.map(r=>r.infectedImpacts)),maxTerrain:Math.max(...rows.map(r=>r.terrainCells)),maxDebris:Math.max(...rows.map(r=>r.debris))};
});
check(e.groundHits.length===2&&e.groundHits.every(h=>[1,6].includes(h.scene)&&h.age===43),'Ground contact changed');
for(const s of scenes){
 if([1,6].includes(s.scene)){
  check(s.maxGround===1&&s.maxTerrain>30&&s.maxDebris>=4,'Missing rendered ground response '+s.scene);
  check(!e.frames.some(r=>r.scene===s.scene&&Number(r.image.slice(-7,-4))<43&&(r.groundBursts||r.terrainCells||r.debris)),'Effect before authoritative terrain contact');
 }else check(s.maxGround===0&&s.maxTerrain===0,'False ground wave '+s.scene);
}
for(const scene of [0,2,3])check(scenes[scene].maxBody>0,'Missing accepted body/guard flash '+scene);
for(const scene of [4,7])check(scenes[scene].maxBody===0&&scenes[scene].maxTissue===0&&!e.hits.some(h=>h.scene===scene),'Miss or immunity emitted success feedback');
check(!e.frames.some(r=>r.scene===6&&r.behindWallCells>0),'Ground wave passed through wall');
const error=Math.max(...e.frames.flatMap(r=>[r.leftError||0,r.rightError||0]));check(error<.002,'Render/server fist mismatch');
// Packet receipt and the interpolated actor clock are separate observations. Do not
// hide a render lead by calling the successful server-timing test visual acceptance.
const early=e.frames.filter(r=>[1,6].includes(r.scene)&&r.age<43&&(r.groundBursts||r.terrainCells||r.debris));
const maxVisualLeadTicks=Math.max(0,...early.map(r=>43-r.age));
const report={date:'2026-11-14',status:maxVisualLeadTicks>0?'visual_timing_pending':'focused_checks_passed',tests,changes:['Player heavy punches, Hunter slam and Juggernaut slam share the same terrain response.','Surface-block rise, cracks, local material fragments and supported loose-drop impulse.','Accepted CRUSH hits receive pressure flash plus the existing directional tissue effect; frontal guard flashes follow the fists.'],
 visual:{platform:'Forge 1.20.1',directory:dir,frames:e.frames.length,scenes,maxFistErrorBlocks:error,maxVisualLeadTicks,review:'Inspected 12 contact sheets covering each game tick around combo hits, slam, guard/break, dodge, wall/slab terrain and invulnerable target. The clock-lead finding remains open.',earlyEffectFrames:early.map(r=>({image:r.image,renderClock:r.age,serverAge:Number(r.image.slice(-7,-4))})),hits:e.hits,groundHits:e.groundHits,evidenceSha256:sha(dir+'evidence.json')},
 implementation:roots.map(root=>({root,files:Object.fromEntries(['combat/TerrainImpactEffects.java','combat/CombatImpactEffects.java','entity/EncounterMob.java','entity/BlacklightHunter.java','infection/InfectedAttackEffects.java','client/renderer/TerrainRipple.java'].map(f=>[f,sha(root+'src/main/java/com/codex/blacklight/'+f)]))})),
 limitations:['A ground effect can arrive before the interpolated fist reaches the server contact clock; the recorded sub-tick lead remains an OPEN synchronization issue, not a passed visual timing gate.','This is the existing player-style visual terrain displacement, not persistent removal or deformation of world blocks.','Only verified Hunter/Juggernaut ground contacts are connected so far; other creatures still require individual contact calibration.','Native NeoForge rendered-frame QA, other creature moves, audio listening and full goal acceptance remain pending.']};
for(const root of roots)fs.writeFileSync(root+'art/encounters/terrain-feedback-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({status:report.status,tests,frames:e.frames.length,scenes,maxFistErrorBlocks:error,maxVisualLeadTicks}));
