// node tools/verify_vehicle_acceptance.mjs <disposable QA output directory>
import fs from 'node:fs';
import path from 'node:path';
const root=process.argv[2];if(!root)throw new Error('QA directory required');
const json=p=>JSON.parse(fs.readFileSync(path.join(root,p),'utf8'));
const results=[];
for(const faction of ['military','blackwatch'])for(const type of ['truck','apc','tank']){
  const id=faction+'_'+type,frames=json('audio-frames/'+id+'-frames.json'),server=json('audio-frames/'+id+'-server.json'),sounds=json('audio-frames/'+id+'-sounds.json');
  const expected=['vehicle.'+type+'_engine',type==='truck'?'vehicle.wheels':'vehicle.tracks',...(type==='tank'?['vehicle.cannon','vehicle.coaxial']:type==='apc'?['vehicle.autocannon','vehicle.rocket']:[])];
  const running=frames.filter(r=>r.engineActive);
  const row={case:id,engineSamples:running.length,enginePitchRange:Math.max(...running.map(r=>r.enginePitch))-Math.min(...running.map(r=>r.enginePitch)),
    rollingAudible:frames.some(r=>r.rollingActive&&r.rollingVolume>.01),maxLoopPairs:Math.max(...frames.map(r=>r.loopPairs)),
    stopped:frames.filter(r=>r.tick>=100).every(r=>!r.engineRunning&&r.loopPairs===0),acceptedShots:server.filter(r=>r.fired).length,
    expectedSounds:expected,missingSounds:expected.filter(s=>!sounds.includes(s))};
  row.passed=row.engineSamples>5&&row.enginePitchRange>.05&&row.rollingAudible&&row.maxLoopPairs===1&&row.stopped&&!row.missingSounds.length&&(type==='truck'||row.acceptedShots>0);
  results.push(row);
}
for(const id of ['dent_tank_side','dent_truck_front','dent_apc_side','dent_tank_turret','dent_tank_roof','dent_repair']){
  const frames=json('dent-frames/'+id+'-frames.json'),server=json('dent-frames/'+id+'-server.json');
  const row={case:id,acceptedHits:server.filter(r=>r.action).length,initialHealth:server[0].health,minimumHealth:Math.min(...server.map(r=>r.health)),
    maxServerDepth:Math.max(...server.map(r=>r.depth)),maxClientDepth:Math.max(...frames.map(r=>r.depth)),lastDepth:server.at(-1).depth,lastHealth:server.at(-1).health,
    persistentClientDepth:frames.filter(r=>r.tick>=50).some(r=>r.depth>0)};
  row.passed=row.acceptedHits>0&&row.minimumHealth<row.initialHealth&&row.maxServerDepth>0&&row.maxClientDepth>0&&(id==='dent_repair'?row.lastDepth===0&&row.lastHealth===row.initialHealth:row.lastDepth>0&&row.persistentClientDepth);
  results.push(row);
}
fs.writeFileSync(path.join(root,'vehicle-checks.json'),JSON.stringify(results,null,2));
console.log(JSON.stringify(results,null,2));
process.exitCode=results.every(r=>r.passed)?0:1;
