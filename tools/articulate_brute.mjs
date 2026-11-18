// Preserve the authored rest mesh/UVs while giving each heavy forearm its own elbow.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const file=root+'/src/main/resources/assets/blacklight/geo/blacklight_brute_mc.geo.json';
const doc=JSON.parse(fs.readFileSync(file,'utf8')),bones=doc['minecraft:geometry'][0].bones;
for(const [side,sign] of [['right',-1],['left',1]]){
 const arm=bones.find(b=>b.name===side+'_arm');
 if(bones.some(b=>b.name===side+'_forearm')){if(!arm.cubes.length)throw Error('Empty articulated upper arm');continue;}
 if(arm.cubes.length!==4)throw Error('Unexpected Brute arm layout');
 const forearm={name:side+'_forearm',parent:arm.name,pivot:[sign*12.5,17,0],cubes:arm.cubes.splice(2)};
 bones.splice(bones.indexOf(arm)+1,0,forearm);
}
fs.writeFileSync(file,JSON.stringify(doc,null,1)+'\n');
console.log('Articulated both Brute elbows; rest geometry and UV coordinates preserved.');
