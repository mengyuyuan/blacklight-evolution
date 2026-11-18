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
 for(const name of ['combat.JuggernautContactTest','combat.EncounterMoveTest','combat.HunterContactTest','combat.ContactEffectsQueueTest','network.ContactEffectsPayloadTest']){
  const xml=fs.readFileSync(build+'test-results/test/TEST-com.codex.blacklight.'+name+'.xml','utf8');
  const header=xml.match(/<testsuite\s+[^>]+>/)[0];
  for(const field of ['failures','errors','skipped'])check(Number(header.match(new RegExp(field+'="(\\d+)"'))?.[1]||0)===0,platform+' '+name+' '+field);
  unit+=Number(header.match(/tests="(\d+)"/)[1]);
 }
 check(unit>=45,'Contact clock or packet regression missing');
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
  check(!e.frames.some(r=>r.scene===s.scene&&r.serverAge<43&&(r.groundBursts||r.terrainCells||r.debris)),'Effect before authoritative terrain contact');
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
check(e.presentations?.length>0,'Missing frame-clock receipts');
check(e.presentations.every(p=>p.shown>=p.tick),'Contact layers displayed before the confirmed world clock');
check(e.presentations.filter(p=>p.kind===0).length===2,'Missing displayed ground receipts');
check(e.presentations.filter(p=>p.kind===0).every(p=>p.surfaceCount>20&&p.soundCount===2),'Missing ground material or contact audio cues');
const earlyBody=e.presentations.filter(p=>[0,1,5,6].includes(p.scene)&&p.firstObservedPoseAge+.001<p.contactAge);
check(earlyBody.length===0,'Body or ground receipt leads the displayed pose');
const hunterDir=cache+'creature-combat-qa/hunter-source-frames/',hunter=JSON.parse(fs.readFileSync(hunterDir+'evidence.json','utf8'));
check(hunter.scenes===6&&hunter.frames.length>=240&&hunter.presentations?.length>=7,'Hunter synchronization visual audit incomplete');
check(hunter.presentations.every(p=>p.shown>=p.tick&&p.firstObservedPoseAge+.001>=p.contactAge),'Hunter receipt before displayed contact');
check(hunter.groundHits.length===1&&hunter.groundHits[0].age===15,'Hunter terrain contact changed');
check(hunter.presentations.filter(p=>p.kind===0).length===1&&hunter.presentations.filter(p=>p.kind===0).every(p=>p.scene===2&&p.contactAge===15&&p.soundCount===2),'Hunter material/ground sound mismatch');
check(!hunter.presentations.some(p=>[4,5].includes(p.scene))&&!hunter.hits.some(h=>[4,5].includes(h.scene)),'Hunter miss/dodge emitted contact feedback');
check(hunter.frames.filter(f=>f.scene===2).some(f=>f.debris===8)&&!hunter.frames.some(f=>f.scene===2&&f.age<15&&f.debris>0),'Hunter debris missing or premature');
const hunterError=Math.max(...hunter.frames.flatMap(f=>[f.leftError,f.rightError]));check(hunterError<.002,'Hunter pose/contact mismatch');
for(const row of hunter.frames)check(fs.existsSync(hunterDir+row.image),'Missing Hunter image '+row.image);
const native=[['terrain','nativeTerrainVisual',e,480],['hunter','nativeHunterVisual',hunter,240]].map(([kind,stage,reference,min])=>{
 const directory=cache+'creature-combat-qa/neo-'+kind+'-frames/',data=JSON.parse(fs.readFileSync(directory+'evidence.json','utf8'));
 check(fs.readFileSync(cache+'creature-combat-neo-'+stage+'.log','utf16le').includes('BUILD SUCCESSFUL'),'Native '+kind+' client did not finish');
 check(data.scenes===reference.scenes&&data.frames.length>=min&&data.presentations.length===reference.presentations.length,'Native '+kind+' scenes/receipts incomplete');
 check(data.presentations.every(p=>p.shown>=p.tick&&p.firstObservedPoseAge+.001>=p.contactAge),'Native '+kind+' premature contact effect');
 const signature=x=>JSON.stringify(x.map(h=>[h.scene,h.move,h.age,h.damage]));
 check(signature(data.hits)===signature(reference.hits),'Version damage/timing mismatch '+kind);
 check(JSON.stringify(data.groundHits.map(h=>[h.scene,h.age]))===JSON.stringify(reference.groundHits.map(h=>[h.scene,h.age])),'Version ground timing mismatch '+kind);
 const error=Math.max(...data.frames.flatMap(f=>[f.leftError||0,f.rightError||0]));check(error<.002,'Native '+kind+' render/contact mismatch');
 for(const row of data.frames)check(fs.existsSync(directory+row.image),'Missing native '+row.image);
 if(kind==='terrain'){
  check(!data.frames.some(f=>[1,6].includes(f.scene)&&f.age<43&&(f.groundBursts||f.terrainCells||f.debris)),'Native early ground visuals');
  check(!data.frames.some(f=>f.scene===6&&f.behindWallCells>0),'Native wave passed through wall');
  for(const scene of [4,7])check(!data.frames.some(f=>f.scene===scene&&(f.bodyBursts||f.infectedImpacts||f.groundBursts)),'Native miss/immunity flash');
 }
 for(const p of data.presentations.filter(p=>p.kind===0))check(p.soundCount===2&&data.frames.some(f=>f.scene===p.scene&&f.debris===8),'Native material/audio/debris layers missing');
 return {platform:'NeoForge 1.21.1',kind,directory,frames:data.frames.length,maxPoseErrorBlocks:error,maxPresentationDelayTicks:Math.max(...data.presentations.map(p=>p.shown-p.tick)),presentations:data.presentations,evidenceSha256:sha(directory+'evidence.json')};
});
const report={date:'2026-11-14',status:maxVisualLeadTicks>0?'visual_timing_pending':'focused_checks_passed',tests,changes:['Player heavy punches, Hunter slam and Juggernaut slam share the same terrain response.','Surface-block rise, cracks, local material fragments and supported loose-drop impulse.','Accepted CRUSH hits receive pressure flash plus the existing directional tissue effect; frontal guard flashes follow the fists.'],
 visual:{platform:'Forge 1.20.1',directory:dir,frames:e.frames.length,scenes,maxFistErrorBlocks:error,maxVisualLeadTicks,review:'Contact sheets inspected around combo hits, slam, guard/break, dodge, wall/slab terrain and invulnerable target; sub-tick captures around fist contacts. Receipts independently audit simultaneous custom visual/audio release.',earlyEffectFrames:early.map(r=>({image:r.image,renderClock:r.age,serverAge:r.serverAge})),presentations:e.presentations,hits:e.hits,groundHits:e.groundHits,evidenceSha256:sha(dir+'evidence.json')},
 hunter:{directory:hunterDir,frames:hunter.frames.length,maxPalmErrorBlocks:hunterError,presentations:hunter.presentations,groundHits:hunter.groundHits,hits:hunter.hits,evidenceSha256:sha(hunterDir+'evidence.json')},
 native,
 implementation:roots.map(root=>({root,files:Object.fromEntries(['combat/TerrainImpactEffects.java','combat/CombatImpactEffects.java','combat/ContactEffects.java','combat/ContactEffectsQueue.java','network/ContactEffectsPayload.java','network/ModNetwork.java','registry/ModInfectedSounds.java','entity/EncounterMob.java','entity/BlacklightHunter.java','infection/InfectedAttackEffects.java','client/renderer/TerrainRipple.java','client/renderer/ContactTerrainDebris.java','client/renderer/ContactEffectsPresentation.java','client/renderer/CombatImpactRenderer.java','client/renderer/InfectedImpactVisuals.java'].map(f=>[f,sha(root+'src/main/java/com/codex/blacklight/'+f)]))})),
 limitations:[...(maxVisualLeadTicks>0?['Ground feedback still leads interpolated contact; visual timing remains OPEN.']:[]),'Custom contact layers share a render-clock receipt. Network/frame scheduling can delay presentation; the native audit records the maximum observed delay. Vanilla hurt-red/velocity and authoritative damage keep their engine timing and were not retimed.','This is the existing player-style visual terrain displacement, not persistent removal or deformation of world blocks.','Only verified Hunter/Juggernaut ground contacts are connected so far; other creatures still require individual contact calibration.','Other creature moves, player consumption combinations, audio listening and full goal acceptance remain pending.']};
for(const root of roots)fs.writeFileSync(root+'art/encounters/terrain-feedback-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({status:report.status,tests,frames:e.frames.length,scenes,maxFistErrorBlocks:error,maxVisualLeadTicks}));
