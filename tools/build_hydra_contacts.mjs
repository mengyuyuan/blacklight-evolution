import fs from 'node:fs';import crypto from 'node:crypto';import zlib from 'node:zlib';
const root='D:/blacklight-evolution/',assets=root+'src/main/resources/assets/blacklight/';
const packed=fs.readFileSync('D:/PrototypeReference/p1/input/comparison/Hydra_fig.p3d.rz'),raw=zlib.inflateSync(packed.subarray(16));
const audit=JSON.parse(fs.readFileSync('D:/PrototypeReference/p1/creature-fig-audit.json')).records;
const motion=JSON.parse(fs.readFileSync(assets+'models/hydra_motion.json')),animation=JSON.parse(fs.readFileSync(assets+'animations/blacklight_hydra.animation.json'));
const geometry=JSON.parse(fs.readFileSync(assets+'geo/blacklight_hydra_mc.geo.json'))['minecraft:geometry'][0].bones;
const names={3:'hydra/opening/outside/attacks/#7131b068b337a862/#7131b068b337a862',9:'hydra/opening/outside/attacks/sweep',10:'hydra/opening/outside/attacks/#4dd80e0880e3c95f'};
const actions={};const records=[];const round=x=>Math.round(x*30)/30;
for(const [action,owner] of Object.entries(names)){
 const selected=audit.filter(r=>r.source==='comparison/Hydra_fig.p3d.rz'&&r.owner===owner&&['PuppetAnimation','Hit','AutoTarget','Sound'].includes(r.type));
 for(const r of selected){if(!r.fields)throw Error('Undecoded Hydra record '+r.offset);
  const start=r.fields.BeginTime??r.fields.TimeBegin;if(Math.abs(raw.readFloatLE(r.payload+4)-start)>1e-6)throw Error('Raw FIG time mismatch '+r.offset);
 }
 records.push(...selected);const source={3:'slam',9:'sweep',10:'sidesweep'}[action],frames=motion.clips['contact_'+source];
 const segments=selected.filter(r=>r.type==='PuppetAnimation').map(r=>{const f=r.fields;let from=f.StartFrame,to=f.EndFrame<0?frames.length-1:f.EndFrame,at=round(f.TimeBegin),end=f.TimeEnd<0?at+(to-from)/30:round(f.TimeEnd);return {at,end,from,to};});
 const duration=Math.max(...segments.map(s=>s.end)),clip='combat_'+source;
 actions[action]={clip,duration:Math.ceil(duration*20),segments,
  windows:selected.filter(r=>r.type==='Hit').map(r=>{const f=r.fields;return {offset:r.offset,from:round(f.BeginTime)*20,to:round(f.EndTime)*20,joint:f.jointName.name,sourceOffset:[f.OffsetX,f.OffsetY,f.OffsetZ],sourceRadius:f.Radius,damage:f.DamageMin,kind:f.DamageType.name,shapecast:f.UseShapecast};}),
  turning:selected.filter(r=>r.type==='AutoTarget').map(r=>({from:round(r.fields.TimeBegin)*20,to:round(r.fields.TimeEnd)*20,speed:r.fields.TurningVelocity})),
  sounds:selected.filter(r=>r.type==='Sound').map(r=>({at:round(r.fields.TimeBegin)*20,patch:r.fields.Patch,trigger:r.fields.Trigger?.name}))};
 const out=[];
 for(let frame=0;frame<=Math.ceil(duration*30);frame++){
  const t=Math.min(duration,frame/30),s=segments.find(s=>t<=s.end+1e-6)||segments.at(-1),f=s.from+(s.to-s.from)*Math.max(0,Math.min(1,(t-s.at)/(s.end-s.at)));
  const lo=Math.floor(f),hi=Math.min(frames.length-1,lo+1),part=f-lo;
  out.push(frames[lo].map((v,b)=>v.map((x,k)=>x+(frames[hi][b][k]-x)*part)));
 }
 motion.clips[clip]=out;const bones={};
 for(let b=0;b<motion.bones.length;b++){const rotation={},position={};for(let f=0;f<out.length;f++){const key=(f/30).toFixed(6),v=out[f][b];rotation[key]=v.slice(0,3).map((x,k)=>x*180/Math.PI*(k<2?-1:1));position[key]=v.slice(3).map(x=>x*16);}bones[motion.bones[b]]={rotation,position};}
 animation.animations[clip]={loop:false,animation_length:duration,bones};
}
// The active block model rounds its authored pivots. Contact and grip use those exact pivots.
motion.pivots=motion.bones.map(name=>{const p=geometry.find(b=>b.name===name).pivot;return [-p[0]/16,p[1]/16,p[2]/16];});
fs.writeFileSync(assets+'models/hydra_motion.json',JSON.stringify(motion));
fs.writeFileSync(assets+'animations/blacklight_hydra.animation.json',JSON.stringify(animation));
fs.writeFileSync(assets+'models/hydra_combat.json',JSON.stringify({actions},null,2)+'\n');
fs.writeFileSync(root+'art/hydra/contact-source-provenance.json',JSON.stringify({source:'Hydra_fig.p3d.rz',sha256:crypto.createHash('sha256').update(packed).digest('hex'),rawLength:raw.length,records,actions,
 boundary:'P1 source animation slices, hit windows, joints, source radii/damage and turn windows verified against decoded FIG times in the original binary. Rendered block-model contact shapes, block units, damage budget and supported-ground propagation are MC adaptations. Unknown branch/exit conditions and sound patch waveforms are not claimed as decoded engine behavior.',
 correction:'Sweep previously played the unsliced animation while applying a one-tick owner-centred radius. The actual FIG sweeps both claws from 1.2 to 1.63333 seconds, with a slow 0-16 windup and faster 16-30 release. Slam uses Head and full_rig_body3 during descent, followed by three expanding Head events, not circles around the rooted base.'},null,2)+'\n');
console.log(JSON.stringify(Object.fromEntries(Object.entries(actions).map(([k,a])=>[k,{clip:a.clip,duration:a.duration,windows:a.windows.length}]))));
