import fs from 'node:fs';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
function offAxis(p){
 const {x,y,z,w}=p.rotation;
 const q=[1-2*y*y-2*z*z,2*x*y+2*z*w,2*x*z-2*y*w,2*x*y-2*z*w,1-2*x*x-2*z*z,2*y*z+2*x*w,2*x*z+2*y*w,2*y*z-2*x*w,1-2*x*x-2*y*y];
 let error=0;for(let c=0;c<3;c++)for(let r=0;r<3;r++)if(c!==r)error=Math.max(error,Math.abs([0,1,2].reduce((s,k)=>s+p.view[k*4+r]*q[c*3+k],0)));
 return error;
}
const files=['src/main/java/com/codex/blacklight/client/particle/ContactDustParticle.java','src/main/resources/assets/blacklight/particles/contact_dust.json','src/main/java/com/codex/blacklight/registry/ModParticles.java','src/main/java/com/codex/blacklight/client/ClientParticleEvents.java','src/flyerWeaponVisual/java/com/codex/blacklight/client/FlyerWeaponVisual.java','src/flyerWeaponVisual/java/com/codex/blacklight/client/ParticleProbe.java','src/flyerWeaponVisual/java/com/codex/blacklight/client/qaparticle/ParticleProbeMixin.java','src/flyerWeaponVisual/resources/blacklight.particle-qa.mixins.json','tools/flyer-weapon-visual.init.gradle'];
files.push(...['combat/CombatEnvironmentEffects.java','combat/ClawPhysicalEffects.java','combat/BladeEffects.java','combat/TerrainImpactEffects.java','movement/PrototypeMovementEffects.java','client/renderer/ContactEffectsPresentation.java'].map(s=>'src/main/java/com/codex/blacklight/'+s));
const report={date:'2026-11-15',status:'native_flyer_weapon_and_ground_particles_verified',scope:'Actual native flyer claw/blade/hammer contacts, intentional miss, hammer finisher landing and recovery. Shared dust implemented at seven existing emission sites; other scenarios are not newly visually accepted by this report.',diagnosis:['Both engines generated and submitted the expected particles; the missing Forge puffs were a disposable-camera error. ComputeCameraAngles rotated the displayed world without synchronizing Camera.rotation used by billboards. Synchronizing the test observer restored visible native particles without a production render-state workaround.','A separate real port difference replaced 1.21.1 DUST_PLUME with 1.20.1 POOF. Both now use contact_dust with shared launch, drag, size, lifetime and generic sprite sequence. Existing block-state fragments, impact clocks and damage are preserved.'],sourceBoundary:'Contact dust is a Minecraft presentation adaptation using locally inspected Minecraft 1.21.1 DustPlumeParticle parameters and vanilla generic sprites, not decoded Prototype dust constants or a claim of complete source-game VFX parity.',platforms:[]};
for(const [i,p] of ['neo','forge'].entries()){
 const dir=`D:/CodexCache/creature-combat-qa/${p}-flyer-weapon-frames/`,e=JSON.parse(fs.readFileSync(dir+'evidence.json'));
 assert(e.frames.length>=510);assert.equal(e.hits.length,4);assert.equal(e.presentations.length,5);
 const scenes=[];
 for(let scene=0;scene<5;scene++){
  const f=e.frames.filter(f=>f.scene===scene),active={},drawn={},first=f.map(f=>f.particles.first).filter(Boolean);
  for(const frame of f){assert.equal(frame.particles.option,'ALL');for(const [key,n] of Object.entries(frame.particles.active))active[key]=Math.max(active[key]||0,n);for(const [key,n] of Object.entries(frame.particles.drawn))drawn[key]=Math.max(drawn[key]||0,n);}
  if(scene===2||scene===4){assert.equal(drawn.CritParticle,3);assert(drawn.ExplodeParticle>=5);assert(first.length>0);}
  if(scene===3){assert.equal(e.hits.filter(h=>h.scene===scene).length,0);assert.deepEqual(drawn,{});}
  if(scene<4)assert.equal(drawn.ContactDustParticle||0,0);
  if(scene===4){
   assert.equal(drawn.ContactDustParticle,24);assert.equal(drawn.TerrainParticle,48);assert(drawn.PlayerCloudParticle>=2);
   const ground=e.presentations.find(x=>x.kind===0),dust=f.find(x=>x.particles.drawn.ContactDustParticle>0);
   assert(dust.renderTime>=ground.shown);assert(dust.renderTime-ground.shown<=3);assert.equal(ground.soundCount,2);assert.equal(ground.surfaceCount,48);
   scenes.push({scene,activePeak:active,drawnPeak:drawn,firstDustImage:dust.image,firstDustDelay:dust.renderTime-ground.shown,ground});
  }else scenes.push({scene,activePeak:active,drawnPeak:drawn});
  if(i===1)assert(first.every(p=>offAxis(p)<.003),'Forge billboard camera must agree with the world view');
  assert(first.every(p=>p.shader==='particle'&&p.shaderColor.every(x=>Math.abs(x-1)<.0001)));
 }
 const sheets=[...Array.from({length:5},(_,i)=>`weapon-review-${i}.jpg`),'heavy-recovery-review.jpg'];
 report.platforms.push({platform:p,frames:e.frames.length,manifestSha256:hash(dir+'evidence.json'),forgeCameraOffAxisMax:i===1?Math.max(...e.frames.map(f=>f.particles.first?offAxis(f.particles.first):0)):null,scenes,sheets:sheets.map(f=>({file:dir+f,sha256:hash(dir+f)})),files:files.map(f=>({file:f,sha256:hash(roots[i]+'/'+f)}))});
}
for(const file of files.slice(0,2))assert.equal(hash(roots[0]+'/'+file),hash(roots[1]+'/'+file));
report.validation='Native builds and five actual player-weapon scenes rerun on both versions, every frame measured, twelve review sheets inspected. Prior 100 server combat and 37 contact-unit results remain the preceding physics checkpoint; they were not rerun for this particle-only delta.';
report.pending=['Native downed-flyer grab/hold/absorption and lethal-cut handoff','High-speed thin slabs, stairs and narrow walls; network/crowd/mod matrix','Full remaining creature, audio and source-game visual acceptance'];
for(const root of roots)fs.writeFileSync(root+'/art/encounters/contact-particles-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report.platforms.map(p=>({platform:p.platform,frames:p.frames,groundDust:p.scenes[4].drawnPeak.ContactDustParticle,cameraError:p.forgeCameraOffAxisMax}))));