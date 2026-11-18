// Original P1 FIG events, with byte offsets retained. Minecraft damage/impulse scales are separate.
import fs from 'node:fs';
const root='D:/blacklight-evolution';
const audit=JSON.parse(fs.readFileSync('D:/PrototypeReference/p1/boss-fig-audit.json','utf8'));
const rows=audit.records;
const find=(source,owner,type)=>rows.filter(r=>r.source===source&&r.owner===owner&&r.type===type&&r.fields);
const clip=(kind,key)=>JSON.parse(fs.readFileSync(`${root}/src/main/resources/assets/blacklight/models/encounter_${kind}_source.json`)).clips[key];
const segment=(r,offset=0)=>({at:offset+r.fields.TimeBegin,end:r.fields.TimeEnd<0?-1:offset+r.fields.TimeEnd,
 from:r.fields.StartFrame,to:r.fields.EndFrame,speed:r.fields.Speed,blendIn:r.fields.BlendInTime,blendOut:r.fields.BlendOutTime,offset:r.offset,owner:r.owner});
const mother='comparison/mother_fig.p3d.rz',leader='comparison/LeaderHunter_fig.p3d.rz';
const roar='mother_core/opening/outside/attacks/#523cabf028eb90/pose';
const wind=find(mother,roar+'/windup','PuppetAnimation');
const start=Math.max(...wind.map(r=>r.fields.TimeEnd));
const scream=[...wind.map(r=>segment(r)),...find(mother,roar+'/roar','PuppetAnimation').map(r=>segment(r,start))];
const waves=find(mother,roar+'/roar','Hit').map(r=>({begin:start+r.fields.BeginTime,end:start+r.fields.EndTime,
 radius:r.fields.Radius,damage:r.fields.DamageMin,impulse:[r.fields.ImpulseMinX,r.fields.ImpulseMinY,r.fields.ImpulseMinZ],localOffset:[r.fields.OffsetX,r.fields.OffsetY,r.fields.OffsetZ],offset:r.offset}));
const records={};
function save(kind,move,source,segments,windows,range,extra={}){
 const c=clip(kind,extra.clip??move);const last=segments.at(-1);
 const end=last.end>=0?last.end:last.at+((last.to<0?c.frames.length-1:last.to)-last.from)/c.fps/last.speed;
 records[kind+':'+move]={source,clip:move,animation:c.source,fps:c.fps,seconds:end,segments,windows,range,...extra};
}
save('elizabeth_greene','HOWL',mother,scream,waves,40,{mode:'radial_wave',spatialUnits:'source_model',spatialModel:'elizabeth_greene',units:'Original range, radii and local offsets remain in P1 source units. World geometry multiplies by the preserved Greene model pixelsPerSourceUnit/16, shared with root Move. Damage and impulses remain Minecraft tuning.'});
for(const [move,owner] of [['VOLLEY','mother_core/opening/outside/attacks/#fc7f04ceefd03173/shoot/#685d5070bf562e9c'],['ORBS','mother_core/opening/outside/attacks/#6a161751d18c9d89']]){
 const animations=find(mother,owner,'PuppetAnimation');const execute=find(mother,owner,'Execute')[0];
 if(!execute||!animations.length)throw Error('Missing native shot branch '+move);
 save('elizabeth_greene',move,mother,animations.map(r=>segment(r)),[{begin:execute.fields.TimeBegin,end:execute.fields.TimeBegin,offset:execute.offset}],32,{mode:'projectile',rangeSource:'Minecraft acquisition bound; original NPC acquisition not yet decoded'});
}
const attack='leader_hunter/opening/surface/ground/attacks/#e433f908f0cd27b2/#e433f908f0cd27b2/#d16ecd93eedfb80d/pose';
const charge=find(leader,attack+'/charge','PuppetAnimation')[0],release=find(leader,attack+'/release','PuppetAnimation')[0];
const recovery=find(leader,attack+'/#ad1c5658a265ccb8','PuppetAnimation')[0];
const releaseAt=charge.fields.TimeEnd,recoverAt=releaseAt+release.fields.TimeEnd;
const hits=find(leader,attack+'/release','Hit').slice(0,2).map(r=>({begin:releaseAt+r.fields.BeginTime,end:releaseAt+r.fields.EndTime,radius:r.fields.Radius,
 joint:r.fields.jointName.name,offset:r.offset,localOffset:[r.fields.OffsetX,r.fields.OffsetY,r.fields.OffsetZ]}));
const turning=find(leader,attack+'/charge','AutoTarget').map(r=>({begin:r.fields.TimeBegin,end:r.fields.TimeEnd,degreesPerSecond:r.fields.TurningVelocity,checkDistance:r.fields.CheckDistance,maxDistance:r.fields.MaxDistance,offset:r.offset}));
save('leader_hunter','CLAW',leader,[segment(charge),segment(release,releaseAt),segment(recovery,recoverAt)],hits,3.75,{mode:'joint_sweep',rangeMode:'horizontal',turning,rangeSource:'TargetDistance condition: XZ < 3.75',recoverySourceNote:'Negative TimeEnd retains remaining clip frames; terminal branch scheduling still needs original runtime comparison'});
const expose='mother_core/opening/outside/#b8e759eedc70d6c';
const collapse=find(mother,expose+'/#500a97569e7430ce','PuppetAnimation')[0],down=find(mother,expose+'/down','PuppetAnimation')[0],rise=find(mother,expose+'/#49a9c35dda28ad17','PuppetAnimation')[0];
const exposeFps=clip('elizabeth_greene','exposed').fps;
const collapseEnd=(collapse.fields.EndFrame-collapse.fields.StartFrame)/exposeFps/collapse.fields.Speed;
const downEnd=collapseEnd+(down.fields.EndFrame-down.fields.StartFrame)/exposeFps/down.fields.Speed;
save('elizabeth_greene','EXPOSED',mother,[segment(collapse),segment(down,collapseEnd),segment(rise,downEnd)],[],0,{clip:'exposed',mode:'recovery',rangeSource:'Not an attack',schedulingNote:'Preserves source frame ranges and speed. MC advances each uncapped branch after one clip span; original dynamic down-state exit conditions are not yet ported.'});
// This P1 exporter does not own the separately audited P2 Juggernaut schedules.
const priorPath=`${root}/src/main/resources/assets/blacklight/models/encounter_source_actions.json`;
if(fs.existsSync(priorPath))for(const [key,value] of Object.entries(JSON.parse(fs.readFileSync(priorPath)).actions))if(key.startsWith('juggernaut:'))records[key]=value;
const report={schema:1,notice:'Decoded source events. FIG node scheduling and Minecraft units are adaptations, not an original-engine port.',actions:records};
fs.writeFileSync(`${root}/src/main/resources/assets/blacklight/models/encounter_source_actions.json`,JSON.stringify(report,null,2)+'\n');
console.log(Object.entries(records).map(([k,v])=>({action:k,seconds:v.seconds,windows:v.windows.length})));
