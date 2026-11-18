import fs from 'node:fs';
import crypto from 'node:crypto';
const root=new URL('../',import.meta.url),file=new URL('src/main/resources/assets/blacklight/models/encounter_source_actions.json',root);
const fig=JSON.parse(fs.readFileSync('D:/PrototypeReference/p2/creature-fig-structure.json'));
const raw=fs.readFileSync('D:/PrototypeReference/p2/input/p2/infected3.p3d');
const offsets=[3979052,3979660,3980088,3980560,3981816,3983044,3983608,3984012,3984516,3985780,3985932,3987780,3988536,3988988,3989240,3989640,3990872,4043292,4048372,4047872,4050508,4120572];
const records=offsets.map(offset=>{const r=fig.records.find(r=>r.offset===offset&&r.owner?.startsWith('infected3/'));if(!r)throw Error('Missing FIG '+offset);return r;});
const data=JSON.parse(fs.readFileSync(file)),s=(at,end,from,to,blendIn=0,blendOut=0)=>({at,end,from,to,speed:1,blendIn,blendOut});
const window=(begin,end,joint)=>({begin,end,radius:.55,joint,localOffset:[0,0,0],damage:350});
const base=(clip,animation,seconds,segments,windows,turning=[])=>({clip,animation,seconds,fps:30,range:4.5,rangeMode:'contact_preview',mode:'juggernaut_fists',segments,windows,turning});
// Natural playback until each source slice finishes; explicit source recovery
// holds retained. The undocumented engine transition conditions are NOT inferred.
data.actions['juggernaut:HAMMER']=base('HAMMER','infected3_com_2hit_combo',103/30,
 [s(0,1,0,30,.1),s(1,4/3,30,34),s(4/3,1.6,34,42),s(1.6,26/15,42,46),s(26/15,34/15,46,62),s(34/15,2.6,62,65),s(2.6,103/30,65,90,0,1/6)],
 [window(17/30+.13,17/30+.33,'Wrist_L'),window(26/15+.13,26/15+.13+8/30,'Wrist_R')],
 [{begin:0,end:.4,degreesPerSecond:180,checkDistance:true,maxDistance:5},{begin:4/3,end:1.6,degreesPerSecond:180,checkDistance:true,maxDistance:5}]);
data.actions['juggernaut:SLAM']=base('SLAM','infected3_com_2hand_slam',113/30,
 [s(0,1.8,0,54,.1),s(1.8,59/30,54,59),s(59/30,80/30,59,80),s(80/30,113/30,80,113,0,.2)],
 [window(62/30,66/30,'Wrist_L'),window(62/30,66/30,'Wrist_R')],
 [{begin:0,end:1,degreesPerSecond:90,checkDistance:true,maxDistance:5}]);
data.actions['juggernaut:GUARD']=base('GUARD','infected3_com_block',8/3,
 [s(0,2/3,0,15,.1),s(2/3,5/3,15,45),s(5/3,8/3,45,75,0,.1)],[]);
data.actions['juggernaut:EXPOSED']=base('exposed','infected3_com_stuned',3.5,
 [s(0,4/3,0,40,.15),s(4/3,8/3,0,40,.1),s(8/3,3.5,0,25,.1,.2)],[]);
fs.writeFileSync(file,JSON.stringify(data,null,2)+'\n');
const nestedHits=[3980560,3984516].flatMap(offset=>{const r=records.find(r=>r.offset===offset);return [68,468].map(start=>({trackGroup:offset,payloadOffset:start,
 begin:raw.readFloatLE(r.payload+start+8),end:raw.readFloatLE(r.payload+start+12),radius:raw.readFloatLE(r.payload+start+56),
 localOffset:[44,48,52].map(n=>raw.readFloatLE(r.payload+start+n)),damageMin:raw.readFloatLE(r.payload+start+104),damageMax:raw.readFloatLE(r.payload+start+108),
 runtime:start===68?'primary contact window':'secondary 1-damage large-radius event excluded from primary fist damage'}));});
const report={schema:1,entity:'juggernaut',source:'infected3.p3d',sha256:crypto.createHash('sha256').update(raw).digest('hex'),nestedHits,
 boundary:'Decoded P2 animation slices and wrist hit payload prefixes; state transition conditions and full damage schema remain unverified. Minecraft joins, bounded defense/stun durations, geometry-fit fist contact, ground shock, damage and audio are adaptations. CHARGE retains the existing authored body-contact behavior.',
 mapping:'HAMMER = source left/right two-hit combo. SLAM = source two-hand slam, both visible fists tested. GUARD = block start/cycle/end; block_gethit sampled only on accepted frontal guard contact. EXPOSED = bounded source stun loop.',
 fit:'Original authored mesh retained. Two-joint CCD keeps the oversized fists above the support plane; no original root travel is applied to the skin.',
 records:records.map(r=>({offset:r.offset,type:r.type,owner:r.owner,length:r.length,references:r.references,words:r.words}))};
fs.writeFileSync(new URL('art/encounters/juggernaut-combat-provenance.json',root),JSON.stringify(report,null,2)+'\n');
console.log('Juggernaut: 4 source schedules, '+records.length+' FIG records retained.');
