import fs from 'node:fs';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>fs.readFileSync(p,'utf8');
const text=p=>{const b=fs.readFileSync(p);return b[0]===255&&b[1]===254?b.toString('utf16le'):b.toString('utf8');};
const report={status:'ordinary_and_hammer_finisher_receiving_verified_capture_and_extreme_contacts_pending',date:'2026-11-15',scope:'Connected live flyer heavy fall, actual player weapon hit, authored skin receiving surfaces, terrain landing, interpolated recovery. This is a bounded checkpoint, not whole-species acceptance.',platforms:[]};
for(const [i,p] of ['neo','forge'].entries()){
 const root=roots[i],dir=`D:/CodexCache/creature-combat-qa/${p}-flyer-weapon-frames/`,e=JSON.parse(read(dir+'evidence.json'));
 assert.match(text(`D:/CodexCache/creature-combat-${p}-encounterServer.log`),/All 100 required tests passed/);
 for(const stage of ['hydraUnit','flyerWeaponVisual'])assert.match(text(`D:/CodexCache/creature-combat-${p}-${stage}.log`),/BUILD SUCCESSFUL/);
 const unitDir=`D:/CodexCache/creature-combat-qa/${p==='neo'?'neo-build':'forge-render-build'}/test-results/test/`;
 const suites=fs.readdirSync(unitDir).filter(n=>n.startsWith('TEST-')&&n.endsWith('.xml')).map(n=>{const xml=read(unitDir+n),head=xml.match(/<testsuite\b[^>]*>/)[0];return {file:n,tests:+head.match(/\btests="(\d+)"/)[1],failures:+head.match(/\bfailures="(\d+)"/)[1],errors:+head.match(/\berrors="(\d+)"/)[1],sha256:hash(unitDir+n)};});
 assert.equal(suites.reduce((s,x)=>s+x.tests,0),37);assert(suites.every(s=>s.failures===0&&s.errors===0));
 assert(e.frames.length>=510);assert.equal(e.hits.length,4);assert.equal(e.presentations.length,5);
 const scenes=[];
 for(let scene=0;scene<5;scene++){
  const f=e.frames.filter(x=>x.scene===scene),h=e.hits.filter(x=>x.scene===scene),r=e.presentations.filter(x=>x.scene===scene),t=e.timeline.filter(x=>x.scene===scene);
  assert(f.length>=90);assert.equal(h.length,scene===3?0:1);assert.equal(r.length,scene===3?0:scene===4?2:1);
  assert(f.every(x=>fs.existsSync(dir+x.image)&&x.authoredSkin.vertices===1512&&x.authoredSkin.cubes===63));
  assert(f.every(x=>!x.ragdoll));assert(Math.max(...f.map(x=>x.poseError))<.001);assert(Math.max(...f.map(x=>x.authoredSkin.skinToReceiverMax))<.001);
  assert.equal(t.at(-1).stage,0);assert(!t.at(-1).stunned);assert(f.at(-1).health>0);
  if(scene!==3){assert.equal(h[0].damage,[40,45,55,0,70][scene]);assert(h[0].surfaceError<(scene<2?.0001:.081));const hit=r.find(x=>x.kind!==0);assert(hit&&hit.shown>=hit.tick&&hit.shown-hit.tick<3&&hit.soundCount===1);if(scene<3)assert(f.some(x=>x.effects.some(v=>v.anchor!=null)));}
  if(scene<4)assert(f.every(x=>!x.serverBody));
  else {
   assert(f.filter(x=>x.serverBody).length>=45);assert(f.at(-1).age>=125&&!f.at(-1).serverBody);
   assert(Math.min(...f.map(x=>x.authoredSkin.lowest))>=80.995,'interpolated visible skin cannot enter floor');
   assert(t.some(x=>x.physical&&x.position.y<82),'server carrier follows falling body');
   assert(t.some(x=>x.physical&&x.onGround&&x.skinLowest>=80.995&&x.skinLowest<81.05),'real floor contact');
   assert(r.some(x=>x.kind===0&&x.tick>h[0].tick&&x.soundCount>=1),'landing feedback occurs after the weapon contact');
  }
  assert(fs.existsSync(dir+`weapon-review-${scene}.jpg`));
  scenes.push({scene,frames:f.length,hits:h,receipts:r,serverBodyFrames:f.filter(x=>x.serverBody).length,skinToReceiverMax:Math.max(...f.map(x=>x.authoredSkin.skinToReceiverMax)),lowestDrawnY:Math.min(...f.map(x=>x.authoredSkin.lowest)),manualSheet:dir+`weapon-review-${scene}.jpg`});
 }
 assert(fs.existsSync(dir+'heavy-recovery-review.jpg'));
 const files=['combat/FlyerPhysics.java','combat/FlyerContact.java','combat/HydraContact.java','combat/RagdollImpact.java','entity/EncounterMob.java','client/renderer/AdaptiveRagdoll.java','client/renderer/BlacklightCreatureRenderer.java'].map(f=>'src/main/java/com/codex/blacklight/'+f);
 files.push('src/encounterTest/java/com/codex/blacklight/gametest/FlyerHurtGameTest.java','src/encounterTest/java/com/codex/blacklight/gametest/EncounterGameTest.java','src/flyerWeaponVisual/java/com/codex/blacklight/client/FlyerWeaponVisual.java');
 report.platforms.push({platform:p,gameTests:100,unitTests:37,unitSuites:suites,frames:e.frames.length,manifestSha256:hash(dir+'evidence.json'),recoverySheet:dir+'heavy-recovery-review.jpg',scenes,files:files.map(f=>({file:f,sha256:hash(root+'/'+f)}))});
 fs.writeFileSync(dir+'status.txt','Reviewed ordinary hits and connected server-owned hammer finisher, landing and recovery; capture/consume/lethal native handoff and extreme contacts remain pending.');
 const resolved=fs.realpathSync(dir).replaceAll('\\','/').toLowerCase();assert(resolved.startsWith('d:/codexcache/creature-combat-qa/'));
 const keep=new Set(e.frames.map(f=>f.image));for(const f of fs.readdirSync(dir,{withFileTypes:true}))if(f.isFile()&&/^\d+-.*\.png$/.test(f.name)&&!keep.has(f.name))fs.unlinkSync(dir+f.name);
}
report.latestParticleEvidence='contact-particles-validation.json';
report.serverRegressionBoundary='100 server and 37 unit checks are the preceding physics checkpoint. The particle-only delta reran both native clients and frame/skin/receipt checks; see contact-particles-validation.json.';
report.fixes=['Server-owned connected rigid parts drive both Gecko source bones and native receiving surfaces; the server carrier follows the physical root.','Local-coordinate physics preserves precision far from the world origin.','Contact effects remain localized; a real supporting block contact emits terrain debris/sound through the shared player-hammer system.','A single snapshot interpolates body pose and root; intermediate rotating skin is projected out of actual terrain for rendering and fractional contact queries.','Hitstop preserves exact endpoint snapshots; repeated impulses retain the body; server lethal handoff retains the last pose.'];
report.evidenceBoundary='Native skin metrics reconstruct baked Gecko cube vertices using tracked renderer matrices and the explicit outer root translation (Gecko tracking strips entityRenderTranslations). Screenshots provide the separate visible check. Server lethal handoff is tested, but native corpse/cut/grab/consume handoff is not yet accepted.';
report.pending=['Native grab/hold/consume and lethal-cut transitions from the downed flyer','High-speed impacts against thin slabs, stairs and narrow walls; continuous collision coverage','Multiplayer snapshot ordering, multiple simultaneous bodies and broader mod/legacy matrix','Remaining original audio validation and full creature-goal scope'];
for(const root of roots)fs.writeFileSync(root+'/art/encounters/flyer-physics-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,frames:p.frames,skinMax:Math.max(...p.scenes.map(s=>s.skinToReceiverMax)),minY:p.scenes[4].lowestDrawnY}))));
