import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';
const root=path.resolve(import.meta.dirname,'..'),forge='D:/blacklight-evolution-forge-1.20.1';
const frames='D:/CodexCache/creature-combat-qa/hunter-source-frames';
const evidence=JSON.parse(fs.readFileSync(frames+'/evidence.json','utf8'));
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const checks=[];
for(const [platform,repo] of [['neo',root],['forge',forge]]){
 let tests=0,failures=0,errors=0,skipped=0;
 for(const name of ['HunterContactTest','CreatureCombatTest','EncounterMoveTest']){
  const xml=fs.readFileSync(repo+'/build/test-results/test/TEST-com.codex.blacklight.combat.'+name+'.xml','utf8');
  for(const field of ['tests','failures','errors','skipped']){const value=Number(xml.match(new RegExp('\\b'+field+'="(\\d+)"'))?.[1]);if(!Number.isFinite(value))throw Error(name+' '+field);if(field==='tests')tests+=value;else if(field==='failures')failures+=value;else if(field==='errors')errors+=value;else skipped+=value;}
 }
 if(failures||errors||skipped)throw Error(platform+' tests incomplete');
 const log=fs.readFileSync('D:/CodexCache/creature-combat-'+platform+'-encounterServer.log','utf16le');
 if(!log.includes('All 44 required tests passed'))throw Error(platform+' server tests incomplete');
 checks.push({platform,unit:{tests,failures,errors,skipped},gameTests:44});
}
const maxPalmError=Math.max(...evidence.frames.flatMap(f=>[f.rightError,f.leftError]));
if(maxPalmError>.002||evidence.frames.length<240)throw Error('Render/contact audit incomplete');
if(evidence.hits.some(h=>h.scene===4||h.scene===5))throw Error('Dodge or out-of-reach damage');
if(evidence.groundHits.length!==1||evidence.groundHits[0].scene!==2||evidence.groundHits[0].age<14)throw Error('Early/duplicate ground impact');
const assets=['animations/blacklight_hunter.animation.json','animations/brawler.animation.json','models/hunter_combat_motion.json','geo/blacklight_hunter_mc.geo.json','textures/entity/hunter_mc.png'];
const hashes=Object.fromEntries(assets.map(f=>{const p='/src/main/resources/assets/blacklight/'+f,h=hash(root+p);if(hash(forge+p)!==h)throw Error('Port asset mismatch '+f);return [f,h];}));
const report={date:'2026-11-14',status:'Hunter focused regression passed; overall creature reproduction remains incomplete.',checks,
 visual:{platform:'Forge 1.20.1',scenes:evidence.scenes,capturedImages:evidence.frames.length,maximumRenderToServerPalmErrorBlocks:maxPalmError,
  acceptedHits:evidence.hits.map(({scene,move,age,damage})=>({scene,move,age,damage})),groundContacts:evidence.groundHits,
  reviewedSheets:fs.readdirSync(frames).filter(n=>/^review-\d+-\d+\.jpg$/.test(n)).sort(),directory:frames,evidenceSha256:hash(frames+'/evidence.json'),
  boundary:'Game-tick screenshots and shared-pose checks, not a capture of every rendered frame or side-by-side original-engine footage. Gecko 4.8.4 diagnostic matrix identity additions are removed only in the measurement copy.'},
 sound:'Swing/contact/ground sound events wired; original bank extraction and audible mix acceptance are not complete.',
 remaining:['Player-versus-Hunter multi-angle and consumption visual regression','NeoForge native renderer frame audit','All other creatures need their own contact, behavior, effects and sound acceptance'],assets:hashes};
fs.writeFileSync(root+'/art/hunter/contact-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({checks,images:evidence.frames.length,maxPalmError,groundTick:evidence.groundHits[0].age}));
