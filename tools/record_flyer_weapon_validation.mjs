import fs from 'node:fs';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const read=p=>fs.readFileSync(p,'utf8');
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const log=p=>{const b=fs.readFileSync(p);return b[0]===255&&b[1]===254?b.toString('utf16le'):b.toString('utf8');};
const out={status:'ordinary_weapon_reactions_validated_heavy_physical_pose_failed',scope:'Real WeaponCombatSystem sessions with native player weapons and authored receiver animation. Heavy physical takeover was reproduced but has NOT passed.',platforms:[]};
for(const [i,platform] of ['neo','forge'].entries()){
 const root=roots[i],dir=`D:/CodexCache/creature-combat-qa/${platform}-flyer-weapon-frames/`,e=JSON.parse(read(dir+'evidence.json'));
 assert.match(log(`D:/CodexCache/creature-combat-${platform}-encounterServer.log`),/All 98 required tests passed/);
 for(const stage of ['hydraUnit','flyerWeaponVisual'])assert.match(log(`D:/CodexCache/creature-combat-${platform}-${stage}.log`),/BUILD SUCCESSFUL/);
 assert(e.frames.length>=490);assert.equal(e.hits.length,4);assert.equal(e.presentations.length,4);
 const scenes=[];
 for(let scene=0;scene<5;scene++){
  const frames=e.frames.filter(f=>f.scene===scene),hits=e.hits.filter(f=>f.scene===scene),receipts=e.presentations.filter(f=>f.scene===scene),timeline=e.timeline.filter(f=>f.scene===scene);
  assert(frames.length>=95);assert.equal(hits.length,scene===3?0:1);assert.equal(receipts.length,hits.length);
  assert(frames.every(f=>fs.existsSync(dir+f.image)));assert(fs.existsSync(dir+`weapon-review-${scene}.jpg`));
  assert.equal(timeline.at(-1).stage,0);assert(!timeline.at(-1).stunned);assert(frames.at(-1).health>0);
  if(scene!==3){
   assert.equal(hits[0].damage,[40,45,55,0,70][scene]);
   // Blunt contact uses a swept surface; the final-tick mesh may be up to .08
   // blocks beyond the actual fractional-time impact. Sharp cases are exact.
   assert(hits[0].surfaceError<(scene<2?.0001:.081));
   assert(receipts[0].shown>=receipts[0].tick&&receipts[0].shown-receipts[0].tick<3);
   assert.equal(receipts[0].soundCount,1);
   assert(timeline.filter(t=>t.stunned&&(t.stage===6||t.stage===7)).length>=5);
   assert(timeline.some(t=>t.stage===4));
   if(scene<3)assert(frames.some(f=>f.effects.some(v=>v.anchor!=null)));
  }
  if(scene<4){assert(frames.every(f=>!f.ragdoll));assert(Math.max(...frames.map(f=>f.poseError))<.001);}
  const physical=frames.filter(f=>f.ragdoll&&f.physical.vertices>0);
  if(scene===4){assert(physical.length>=20);assert(physical.every(f=>f.physical.cuts===0&&f.physical.components===1));}
  scenes.push({scene,hits,receipts,frames:frames.length,sourceBoneError:Math.max(...frames.map(f=>f.poseError)),physicalFrames:physical.length,maxSkinToReceiverDistance:Math.max(0,...physical.map(f=>f.physical.skinToReceiverMax)),manualSheet:dir+`weapon-review-${scene}.jpg`});
 }
 const files=['src/main/java/com/codex/blacklight/entity/EncounterMob.java','src/main/java/com/codex/blacklight/combat/FlyerFlight.java','src/main/java/com/codex/blacklight/combat/FlyerMotion.java','src/encounterTest/java/com/codex/blacklight/gametest/FlyerHurtGameTest.java','src/encounterTest/java/com/codex/blacklight/gametest/JuggernautContactGameTest.java','src/flyerWeaponVisual/java/com/codex/blacklight/client/FlyerWeaponVisual.java'];
 out.platforms.push({platform,gameTests:98,contactUnitTests:35,totalFrames:e.frames.length,manifestSha256:hash(dir+'evidence.json'),scenes,files:files.map(file=>({file,sha256:hash(root+'/'+file)}))});
 fs.writeFileSync(dir+'status.txt','Ordinary claw/blade/hammer reactions reviewed; heavy physical receiver mismatch FAILED, pending repair');
 // Remove obsolete captures from earlier attempts without traversing directories.
 const resolved=fs.realpathSync(dir).replaceAll('\\','/').toLowerCase();assert(resolved.startsWith('d:/codexcache/creature-combat-qa/'));
 const keep=new Set(e.frames.map(f=>f.image));for(const f of fs.readdirSync(dir,{withFileTypes:true}))if(f.isFile()&&/^\d+-.*\.png$/.test(f.name)&&!keep.has(f.name))fs.unlinkSync(dir+f.name);
}
out.reproduced='Before the fix, every actual claw/blade/hammer victim-control tick reset the flight stage to IDLE. Goal.stop and the disabled branch both called cancelAttack, which discarded the accepted source hurt animation. Current snapshots preserve the hit, recovery and idle sequence; repeat accepted damage restarts its curve.';
out.unresolved='Hammer finisher activates the connected client ragdoll (zero cuts, one component) while the server carrier retains no-gravity flight and source receiving cuboids. This produces a large skin/receiver separation and a visible snap back. The physical and authoritative receiving bodies must share a pose before heavy hits, follow-up contacts, grab, consume and lethal cutting can pass.';
out.testBoundary='The first added stun fixture failed because its nonpersistent mob could despawn far from the fake player; persistence was corrected. A separate pre-existing airborne Juggernaut test failed to start at world delay 25; it now waits for 25 actual entity ticks. No Juggernaut production behavior was changed. The native camera initially faced away; corrected ComputeCameraAngles and fresh native captures were used for review.';
for(const root of roots)fs.writeFileSync(root+'/art/encounters/flyer-weapon-validation.json',JSON.stringify(out,null,2)+'\n');
console.log(JSON.stringify(out.platforms.map(p=>({platform:p.platform,frames:p.totalFrames,heavyMismatch:p.scenes[4].maxSkinToReceiverDistance}))));
