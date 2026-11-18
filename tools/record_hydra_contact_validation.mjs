import fs from 'node:fs';import crypto from 'node:crypto';
const roots=['D:/blacklight-evolution/','D:/blacklight-evolution-forge-1.20.1/'],cache='D:/CodexCache/';
const check=(ok,message)=>{if(!ok)throw Error(message);};
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
function log(platform,stage){let b=fs.readFileSync(cache+`creature-combat-${platform}-${stage}.log`);return b.toString(b[0]===255&&b[1]===254?'utf16le':'utf8');}
const tests=['neo','forge'].map(platform=>{
 const build=cache+'creature-combat-qa/'+(platform==='neo'?'neo-build':'forge-render-build')+'/';let unit=0;
 for(const name of ['combat.HydraContactTest','city.HydraMotionTest','city.HydraGrowthTest','combat.ContactEffectsQueueTest','network.ContactEffectsPayloadTest']){
  let xml=fs.readFileSync(build+'test-results/test/TEST-com.codex.blacklight.'+name+'.xml','utf8'),head=xml.match(/<testsuite\s+[^>]+>/)[0];
  for(const field of ['failures','errors','skipped'])check(Number(head.match(new RegExp(field+'="(\\d+)"'))?.[1]||0)===0,platform+' '+name+' '+field);
  unit+=Number(head.match(/tests="(\d+)"/)[1]);
 }
 check(unit===27&&log(platform,'hydraUnit').includes('BUILD SUCCESSFUL'),platform+' focused unit suite incomplete');
 check(log(platform,'encounterServer').includes('All 67 required tests passed')&&log(platform,'encounterServer').includes('BUILD SUCCESSFUL'),platform+' encounter integration incomplete');
 check(log(platform,'hydraGateway').includes('All 11 required tests passed')&&log(platform,'hydraGateway').includes('BUILD SUCCESSFUL'),platform+' gateway regression incomplete');
 return {platform,unitTests:unit,combatGameTests:67,gatewayGameTests:11};
});
const visual=['neo','forge'].map(platform=>{
 const dir=cache+`creature-combat-qa/${platform}-hydra-frames/`,e=read(dir+'evidence.json');
 check(log(platform,'hydraVisual').includes('BUILD SUCCESSFUL'),platform+' hidden client unfinished');
 check(e.scenes===8&&e.frames.length>950,platform+' incomplete frame coverage');
 for(const f of e.frames)check(fs.statSync(dir+f.image).size>10000,'Missing screenshot '+f.image);
 check(e.presentations.every(p=>p.shown>=p.tick&&p.firstObservedPoseAge+.001>=p.contactAge),platform+' effect precedes confirmed frame');
 check(!e.hits.some(h=>[3,5].includes(h.scene))&&!e.presentations.some(p=>[3,7].includes(p.scene)),platform+' miss or wall produced false contact');
 check(e.hits.length===5,platform+' expected exactly one hit in each intersecting scene');
 check(!e.ground.some(g=>[1,2,3,4].includes(g.scene)),platform+' sweep/air false ground strike');
 check(!e.frames.some(f=>f.scene===4&&f.debris>0),platform+' air slam has ground fragments');
 const scenes=Array.from({length:8},(_,scene)=>{
  const f=e.frames.filter(f=>f.scene===scene),p=e.presentations.filter(p=>p.scene===scene),g=e.ground.filter(g=>g.scene===scene);
  check(f.length>=48,platform+' missing scene '+scene);
  let error=Math.max(...f.flatMap(f=>[f.HeadError,f.Claw_LError,f.full_rig_body3Error]));
  check(Number.isFinite(error)&&error<.002,platform+' bone/render mismatch '+scene+': '+error);
  if([0,6].includes(scene)){
   check(g.length===2&&g[1].age>g[0].age,platform+' head contact suppressed by first body contact');
   check(p.filter(p=>p.kind===0).length===5,platform+' missing body/head/three wave receipts');
  }
  if(scene===5){
   check(g.length===0&&p.length===1&&p[0].kind===5,platform+' wall stops the slam before ground contact and emits one material strike');
   check(e.obstacles.length===1&&e.obstacles[0].scene===5,platform+' expected one authoritative wall impact');
   check(f.filter(f=>f.age>=36).every(f=>f.wallOverlap===0),platform+' moving or resting mesh penetrates wall');
   check(f.some(f=>f.unconstrainedWallOverlap>0),platform+' wall fixture must intersect the uncorrected mesh');
   check(f.some(f=>f.age>100&&f.action===0),platform+' missing settled idle after obstacle recovery');
  }
  if(scene===7){check(p.length===0&&g.length===0&&!e.hits.some(h=>h.scene===7),platform+' interruption leaks effects or damage');check(f.some(f=>f.age>90&&f.action===0),platform+' interrupted pose never returns to idle');}
  if(g.length)check(!f.some(f=>f.age<g[0].age&&f.debris>0),platform+' fragments before actual terrain contact');
  check(p.every(p=>p.soundCount>0),platform+' missing displayed contact sound');
  return {scene,frames:f.length,maxPoseErrorBlocks:error,maxDebris:Math.max(...f.map(f=>f.debris)),hits:e.hits.filter(h=>h.scene===scene),ground:g,presentations:p,obstacles:e.obstacles.filter(o=>o.scene===scene),maxWallOverlap:Math.max(0,...f.map(f=>f.wallOverlap||0))};
 });
 const pages=read(dir+'review-pages.json');check(pages.length>=9&&pages.every(p=>fs.existsSync(dir+p)),platform+' continuous contact review sheets missing');
 return {platform,directory:dir,frames:e.frames.length,maxPoseErrorBlocks:Math.max(...scenes.map(s=>s.maxPoseErrorBlocks)),maxPresentationDelayTicks:Math.max(...e.presentations.map(p=>p.shown-p.tick)),scenes,evidenceSha256:sha(dir+'evidence.json'),reviewPages:pages};
});
const signature=v=>JSON.stringify(v.scenes.map(s=>[s.scene,s.hits.map(h=>[h.age,h.damage,h.style]),s.ground.map(g=>g.age),s.presentations.map(p=>[p.contactAge,p.kind])]));
check(signature(visual[0])===signature(visual[1]),'Version contact/damage/event timing mismatch');
const files=['src/main/java/com/codex/blacklight/combat/ContactEffects.java','src/main/java/com/codex/blacklight/network/ContactEffectsPayload.java','src/main/java/com/codex/blacklight/client/renderer/ContactEffectsPresentation.java','src/main/java/com/codex/blacklight/combat/HydraContact.java','src/main/java/com/codex/blacklight/combat/HydraCombatAction.java','src/main/java/com/codex/blacklight/city/HydraMotion.java','src/main/java/com/codex/blacklight/entity/BlacklightHydra.java','src/main/resources/assets/blacklight/models/hydra_motion.json','src/main/resources/assets/blacklight/models/hydra_combat.json','src/main/resources/assets/blacklight/animations/blacklight_hydra.animation.json'];
for(const f of files.filter(f=>f.includes('/resources/')))check(sha(roots[0]+f)===sha(roots[1]+f),'Version resource mismatch '+f);
const report={date:'2026-11-14',status:'contact_obstacle_checks_passed',tests,visual,
 openVisualFindings:[],
 resolvedVisualFindings:[{scene:5,issue:'The old attack crossed the wall despite damage occlusion. A one-frame network overshoot and idle horn penetration were also found in the first obstacle QA run.',fix:'Server geometry reserves the next tick; client geometry forecasts two draw ticks until authoritative recoil metadata arrives. Recoil reverses the accepted source path, cancels remaining attack events, and a bounded neck bend keeps the final resting horns clear.',evidence:'Each captured wall frame is checked against the wall collision shape; unconstrained source poses intersect the same fixture. Review includes approach, impact, recoil and settled idle.'}],
 source:read(roots[0]+'art/hydra/combat-rig-calibration.json'),fig: {file:'art/hydra/contact-source-provenance.json',sha256:sha(roots[0]+'art/hydra/contact-source-provenance.json')},
 changes:['Corrected source-to-authored-rig rest axes for combat only; the previously airborne horns now follow the source ground sweep.','Retained FIG segmented slam and sweep playback, all seven sidesweep joints and early turning windows.','Exact authored cuboid contacts sampled at 1/16 tick; active windows, wall occlusion, damage vetoes and one shared victim ledger.','Body scrape and articulated head/jaw ground contact each release once; three source expansion events propagate from the actual support, with actual material fragments and item impulses.','Timestamped accepted-hit tissue/crush effects and spatial audio; no success feedback on a miss.','Head, neck, jaw and horn cuboids participate in obstacle checks. Confirmed wall impact produces material chips and sound, without ground ripples or victim blood.','Ordinary cancellation retracts the accepted pose and retires all pending hit, ground and wave events.'],
 regressionNotes:['The older autonomous Hydra test expected damage at the rooted owner radius. It now places the target in the visible slam reach and verifies autonomous choice, windup, one accepted blow and recovery.','Hydra GameTests use a 32-block arena to keep their enlarged reach out of adjacent encounter fixtures.','Gecko diagnostic world matrices cancel the outer body-size transform; QA restores that known outer render transform, the .01 local draw lift, interpolated body yaw and six-tick entry blend before comparison.'],
 limitations:['Source joints, curves and FIG fields are verified; exact original transition conditions, target strategy, procedural controllers and sound patch waveforms remain incomplete.','Fixed-length block-model retargeting, cuboid collision, source damage ratios against the MC attack attribute, and support-based waves are explicit MC adaptations.','Obstacle rewind, two-tick visual prediction and at most 0.6 radians of backwards rest bending are MC adaptations, not recovered original procedural controllers. Enclosed obstacles that cannot be cleared by this bounded bend still require a broader solver.',
 'Terrain displacement is visual with physical loose-item response, not persistent excavation or world block deformation.','Hidden muted QA verifies cue scheduling and resources, not subjective listening or original audio identity.','Incoming multipart hit detection, original victim reaction clips, player absorb combinations and other creatures still need the full-goal review. No release package is asserted by this report.'],
 implementation:roots.map(root=>({root,files:Object.fromEntries(files.map(f=>[f,sha(root+f)]))}))};
for(const root of roots)fs.writeFileSync(root+'art/hydra/contact-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({status:report.status,tests,frames:visual.map(v=>[v.platform,v.frames]),maxError:Math.max(...visual.map(v=>v.maxPoseErrorBlocks)),delay:Math.max(...visual.map(v=>v.maxPresentationDelayTicks))}));
