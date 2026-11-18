import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const directory='D:/CodexCache/encounter-qa/frames';
const filter=process.argv.find(arg=>arg.startsWith('--filter='))?.slice(9)??'';
if(filter&&!['elizabeth_greene','monster_style'].includes(filter))throw Error('Unsupported filtered audit: '+filter);
const evidence=JSON.parse(fs.readFileSync(path.join(directory,'evidence.json'),'utf8'));
const selectedFrames=filter?evidence.frames.filter(row=>filter==='monster_style'?/^(leader_hunter|juggernaut|goliath|flyer)_/.test(row.scene):row.scene.startsWith(filter+'_')):evidence.frames;
const groups=Map.groupBy(selectedFrames,row=>row.scene);
const contacts={claw:[12],double:[12,25],pounce:[22],hammer:[23],slam:[30],charge:[17],howl:[32],orbs:[22],volley:[20,30,40],dive:[25],blade:[17],whip:[24],eruption:[42],baton:[14],grenade:[26]};
const failed=[];const hashes=crypto.createHash('sha256');
for(const [scene,frames] of groups){
 const ages=new Set(frames.map(frame=>frame.age));
 const move=scene.match(/_([a-z]+)_p\d+$/)?.[1];
 if(frames.length<(['guard','dodge'].includes(move)?3:5))failed.push(scene+': too few frames');
 for(const contact of contacts[move]??[])for(let age=contact-2;age<=contact+2;age++)if(!ages.has(age))failed.push(scene+': missing contact tick '+age);
 for(const frame of frames){const file=path.join(directory,frame.image);const bytes=fs.readFileSync(file);if(!bytes.subarray(0,8).equals(Buffer.from('89504e470d0a1a0a','hex')))failed.push(frame.image+': not PNG');hashes.update(frame.image+'\0');hashes.update(bytes);}
}
if(filter){
 const expected=filter==='monster_style'?[
  'leader_hunter_howl','leader_hunter_claw','leader_hunter_pounce','leader_hunter_double',
  'juggernaut_guard','juggernaut_charge','juggernaut_hammer','juggernaut_slam',
  'goliath_eruption','goliath_charge','goliath_slam','goliath_hammer','goliath_none',
  'flyer_volley','flyer_dive'].map(name=>name+'_p0'):['eruption','howl','none','orbs','volley'].map(move=>filter+'_'+move+'_p0');
 if(groups.size!==expected.length||evidence.lastRerun!==filter||expected.some(name=>!groups.has(name)))failed.push('Expected every freshly recaptured '+filter+' scenario');
}else if(groups.size!==51||evidence.scenes!==51)failed.push('Expected all 51 visual scenarios');
const result={passed:failed.length===0,scenes:groups.size,frames:selectedFrames.length,contactWindow:'Five consecutive 20 Hz game ticks around every authored strike/release, including repeated strikes',digest:hashes.digest('hex'),failed};
fs.writeFileSync('D:/CodexCache/encounter-qa/frame-check'+(filter?'-'+filter:'')+'.json',JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result));
process.exitCode=failed.length?1:0;
