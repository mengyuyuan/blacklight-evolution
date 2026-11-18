import fs from 'node:fs';
import crypto from 'node:crypto';
const neo='D:/blacklight-evolution/',forge='D:/blacklight-evolution-forge-1.20.1/',cache='D:/CodexCache/';
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
if(fs.readFileSync(cache+'juggernaut-queue-status.txt','utf8')!=='ALL PASSED')throw Error('Final verification queue has not finished');
const tests=[];
for(const [platform,root] of [['neo',neo],['forge',forge]]){
 let count=0;
 for(const name of ['JuggernautContactTest','EncounterMoveTest','HunterContactTest']){
  const xml=fs.readFileSync(root+'build/test-results/test/TEST-com.codex.blacklight.combat.'+name+'.xml','utf8');
  const header=xml.match(/<testsuite\s+[^>]+>/)[0];
  for(const field of ['failures','errors','skipped'])if(Number(header.match(new RegExp(field+'="(\\d+)"'))?.[1]||0)!==0)throw Error(platform+' '+name+' '+field);
  count+=Number(header.match(/tests="(\d+)"/)[1]);
 }
 const log=fs.readFileSync(cache+'creature-combat-'+platform+'-encounterServer.log','utf16le');
 if(!log.includes('All 52 required tests passed')||!log.includes('BUILD SUCCESSFUL'))throw Error(platform+' GameTests not complete');
 tests.push({platform,unit:count,gameTest:52});
}
const directory=cache+'creature-combat-qa/juggernaut-source-frames/',e=JSON.parse(fs.readFileSync(directory+'evidence.json'));
if(e.scenes!==6||e.frames.length<400)throw Error('Incomplete frame capture');
for(const row of e.frames)if(!fs.existsSync(directory+row.image))throw Error('Missing image '+row.image);
const error=Math.max(...e.frames.flatMap(r=>[r.leftError||0,r.rightError||0]));if(error>.002)throw Error('Render/contact mismatch '+error);
if(e.hits.filter(h=>h.scene===0).length!==2||e.hits.some(h=>h.scene>=2))throw Error('Unexpected hit sequence');
if(e.groundHits.length!==1||e.groundHits[0].scene!==1||e.groundHits[0].age!==43)throw Error('Ground event mismatch');
if(!e.hits.some(h=>h.scene===1&&h.age===43))throw Error('Ground shock did not affect the nearby victim');
const paths=['models/encounter_juggernaut_source.json','models/encounter_source_actions.json','geo/juggernaut_mc.geo.json','textures/entity/juggernaut_mc.png'];
const assets=paths.map(path=>{const f='src/main/resources/assets/blacklight/'+path;const hash=sha(neo+f);if(hash!==sha(forge+f))throw Error('Port asset mismatch '+path);return {path,sha256:hash};});
const report={date:'2026-11-14',species:'juggernaut',tests,assets,
 source:'art/encounters/juggernaut-combat-provenance.json',
 visual:{platform:'Forge 1.20.1',directory,scenes:6,frames:e.frames.length,maxFistErrorBlocks:error,hits:e.hits,groundHits:e.groundHits,evidenceSha256:sha(directory+'evidence.json'),
 review:'Reviewed all game-tick contact sheets for telegraph, separate fists, recovery, guard reaction, bounded stun, dodge and counterhit. Diagnostics compare rendered matrix with server pose; this is not frame equality against the original game.'},
 limitations:['CHARGE remains the previous authored body-contact move.','Original state-transition conditions and root travel are not fully ported.','Sound event timing is wired; muted QA does not validate original timbre or audible balance.','Native NeoForge rendered frames, multiplayer load and mod-pack compatibility need separate acceptance.']};
for(const root of [neo,forge])fs.writeFileSync(root+'art/encounters/juggernaut-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({tests,frames:e.frames.length,maxFistErrorBlocks:error,hits:e.hits.map(h=>[h.scene,h.age,h.damage]),groundHits:e.groundHits}));
