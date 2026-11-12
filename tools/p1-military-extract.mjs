// Independent P1 metadata/curve reader. Binary layout reference: EditP3D
// 6966de776f7650e4f5a9150ebe4cf9377f7933d3/Pure3D (reverse-engineered format).
// Input/output are user-local files; generated commercial-game curves must stay out of Git.
import fs from 'node:fs';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import path from 'node:path';
const [source,output]=process.argv.slice(2);
if(!source||!output)throw Error('Usage: node tools/p1-military-extract.mjs Soldier.p3d.rz D:/private/motion.json');
if(!/^d:\//i.test(path.resolve(output).replaceAll('\\','/')))throw Error('Output must be on D:');
const raw=fs.readFileSync(source),b=zlib.inflateSync(raw.subarray(16));
if(BigInt(b.length)!==raw.readBigUInt64LE(8))throw Error('RZ size mismatch');
const chunks=[];
function walk(start,end,parent=null){for(let o=start;o<end;){const id=b.readUInt32LE(o),h=b.readUInt32LE(o+4),size=b.readUInt32LE(o+8);if(h<12||size<h||o+size>end)throw Error(`Invalid chunk ${o}`);const c={id,o,h,size,parent};chunks.push(c);walk(o+h,o+size,c);o+=size;}}
walk(12,b.length);
function reader(c){let p=c.o+12;return {u8:()=>b[p++],u16:()=>{const x=b.readUInt16LE(p);p+=2;return x;},u32:()=>{const x=b.readUInt32LE(p);p+=4;return x;},f:()=>{const x=b.readFloatLE(p);p+=4;if(!Number.isFinite(x))throw Error(`Nonfinite ${p}`);return x;},s:()=>{const n=b[p++],s=b.toString('utf8',p,p+n).replace(/\0+$/,'');p+=n;return s;},four:()=>{const s=b.toString('ascii',p,p+4);p+=4;return s;},q16:()=>{const x=b.readInt16LE(p)/32767;p+=2;return x;},q8:()=>b.readInt8(p++)/127,half:()=>{const n=b.readUInt16LE(p);p+=2;const sign=n&32768?-1:1,e=(n>>10)&31,m=n&1023;if(e===31)throw Error('Nonfinite half');return sign*(e===0?2**-14*m/1024:2**(e-15)*(1+m/1024));},end:()=>{if(p!==c.o+c.h)throw Error(`Header size ${c.id.toString(16)} ${p-c.o}/${c.h}`);}};}
const sk=chunks.find(c=>c.id===0x23000&&reader(c).s()==='bw_trooper_2008_skeleton');if(!sk)throw Error('Blackwatch skeleton missing');
const joints=chunks.filter(c=>c.parent===sk&&c.id===0x23001).map(c=>{const r=reader(c),name=r.s(),parent=r.u32(),matrix=Array.from({length:15},()=>r.f());return {name,parent,rotation:[matrix[0],matrix[4],matrix[8],matrix[1],matrix[5],matrix[9],matrix[2],matrix[6],matrix[10]],position:matrix.slice(12,15)};});
if(joints.length!==67)throw Error('Unexpected Blackwatch skeleton');
const choices={aim:'sldr_rif_weap_amb_cmb',fire:'sldr_rif_amb_stand_fire',reload:'sol_act_reload_quick_001',reload_kneel:'sol_act_reload_quick_kneel_001',melee:'sol_rifle_attacks_head_002',walk:'sldr_loco_rifle_walk_n',run:'sldr_loco_rifle_run_n',radio:'sol_act_radio_gun_up_shooting_001_b',hit:'sol_rifle_gethit_head_001',crouch:'sol_cover_crouch2_001_b',fire_kneel:'sldr_rif_amb_kneel_fire'};
const clips={};let decoded=0;
for(const c of chunks.filter(c=>c.id===0x121000)){
 const r=reader(c);r.u32();const name=r.s(),type=r.four(),frames=r.f(),fps=r.f(),cyclic=r.u32();r.end();const key=Object.keys(choices).find(k=>choices[k]===name);if(!key)continue;
 const bones={};
 for(const bone of chunks.filter(x=>x.id===0x121001&&x.o>c.o&&x.o<c.o+c.size)){
  const br=reader(bone);br.u32();const joint=br.s();br.u32();br.u32();br.end();const channels={};
  for(const channel of chunks.filter(x=>x.o>bone.o&&x.o<bone.o+bone.size&&[0x121112,0x121114,0x121119,0x121118,0x121104,0x121103,0x121102].includes(x.id))){
   const cr=reader(channel);cr.u32();const kind=cr.four();let mapping=-1,base=[0,0,0];
   const dims=channel.id===0x121118||channel.id===0x121103?2:channel.id===0x121102?1:3;
   if(dims<3){mapping=cr.u16();base=Array.from({length:3},()=>cr.f());}
   const n=cr.u32();if(n<1||n>2000)throw Error(`Unsupported external/large channel ${joint}`);
   const times=Array.from({length:n},()=>cr.u16());const quaternion=channel.id===0x121112||channel.id===0x121114;
   const values=times.map(()=>{const v=Array.from({length:dims},()=>channel.id===0x121112?cr.q16():channel.id===0x121114?cr.q8():[0x121119,0x121118].includes(channel.id)?cr.half():cr.f());if(quaternion){v.push(Math.sqrt(Math.max(0,1-v.reduce((s,x)=>s+x*x,0))));const len=Math.hypot(...v);return v.map(x=>x/len);}if(dims===3)return v;const result=[...base],indices=dims===2?[[1,2],[0,2],[0,1]][mapping]:[mapping];if(!indices)throw Error('Bad mapping');indices.forEach((axis,i)=>result[axis]=v[i]);return result;});
   cr.end();if(!['ROT\0','TRAN'].includes(kind))throw Error(`Unknown channel ${kind}`);
   if(channels[quaternion?'rotation':'position'])throw Error(`Duplicate channel ${joint}`);
   channels[quaternion?'rotation':'position']={times,values};decoded++;
  }
  bones[joint]=channels;
 }
 clips[key]={source:name,frames,fps,cyclic:!!cyclic,bones};
}
if(Object.keys(clips).length!==Object.keys(choices).length)throw Error('Missing clips');
const result={schema:1,sourceSha256:crypto.createHash('sha256').update(raw).digest('hex'),joints,clips};
fs.mkdirSync(path.dirname(output),{recursive:true});fs.writeFileSync(output,JSON.stringify(result));
console.log(JSON.stringify({joints:joints.length,clips:Object.keys(clips),channels:decoded,bytes:fs.statSync(output).size,output}));
