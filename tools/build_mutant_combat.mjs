// Repository-authored mutant motions. These are MC adaptations, not original-game extracted clips.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),assets=path.join(root,'src/main/resources/assets/blacklight');
const coreBones=['torso','head','right_arm','left_arm','right_leg','left_leg'],zero=[0,0,0],one=[1,1,1];
function sample(track,t,fallback=zero){
 if(!track)return fallback;if(Array.isArray(track))return track;
 const keys=Object.entries(track).map(([k,v])=>[+k,v]).sort((a,b)=>a[0]-b[0]);
 if(t<=keys[0][0])return keys[0][1];
 for(let i=1;i<keys.length;i++)if(t<=keys[i][0]){const a=keys[i-1],b=keys[i],w=(t-a[0])/(b[0]-a[0]);return a[1].map((v,j)=>v+(b[1][j]-v)*w);}
 return keys.at(-1)[1];
}
function retime(source,segments,mirror=false){
 const clip={loop:false,animation_length:segments.at(-1)[0]/20,bones:{}};
 for(const [name,channels] of Object.entries(source.bones)){
  const target=mirror?name.replace('left_','TEMP_').replace('right_','left_').replace('TEMP_','right_'):name;clip.bones[target]={};
  for(const [channel,track] of Object.entries(channels)){
   const out=clip.bones[target][channel]={};
   for(let f=0;f<=Math.round(clip.animation_length*30);f++){
    const tick=f/1.5;let hi=segments.findIndex(p=>p[0]>=tick);if(hi<0)hi=segments.length-1;
    const a=segments[Math.max(0,hi-1)],b=segments[hi],w=a[0]===b[0]?0:(tick-a[0])/(b[0]-a[0]);
    let value=sample(track,(a[1]+(b[1]-a[1])*w)/20,channel==='scale'?one:zero);
    if(mirror&&channel==='rotation')value=[value[0],-value[1],-value[2]];
    out[(f/30).toFixed(6)]=value;
   }
  }
 }
 return clip;
}
function rotation(clip,bone,points){clip.bones[bone]??={};clip.bones[bone].rotation=Object.fromEntries(points.map(([t,v])=>[String(t/20),v]));}
const provenance={source:'Existing authored attack poses; retimed contacts and new authored transition/locomotion keyframes.',clips:{}};
for(const species of ['stalker','spitter','brute']){
 const bones=species==='brute'?[...coreBones,'right_forearm','left_forearm','root','right_shin','left_shin']:coreBones;
 const file=path.join(assets,`animations/blacklight_${species}.animation.json`),doc=JSON.parse(fs.readFileSync(file,'utf8')),base=doc.animations.attack,moves={};
 if(species==='stalker'){
  moves.claw=retime(base,[[0,0],[10,10],[14,14],[24,24]]);
  moves.pounce=retime(base,[[0,0],[9,10],[18,14],[26,18],[36,24]]);
  rotation(moves.pounce,'torso',[[0,zero],[9,[-22,0,0]],[18,[32,0,0]],[27,[16,0,0]],[36,zero]]);
  for(const side of ['right','left'])rotation(moves.pounce,`${side}_leg`,[[0,zero],[9,[-30,0,0]],[18,[28,0,0]],[28,[-12,0,0]],[36,zero]]);
  const a=retime(base,[[0,0],[8,10],[12,14],[18,24]]),b=retime(base,[[0,0],[2,10],[6,14],[20,24]],true);
  moves.double_claw={loop:false,animation_length:1.9,bones:{}};
  for(const bone of bones){const out=moves.double_claw.bones[bone]={rotation:{}};for(let f=0;f<=57;f++){const t=f/30,c=t<=.9?a:b;out.rotation[t.toFixed(6)]=sample(c.bones[bone]?.rotation,t<=.9?t:t-.9);}}
  // Authored right/left slashes extend the actual short digits through the target.
  // The old raised-then-lowered arms never reached the victims of its broad cone.
  rotation(moves.claw,'torso',[[0,zero],[8,[-8,-12,0]],[14,[12,14,0]],[18,[8,10,0]],[24,zero]]);
  rotation(moves.claw,'right_arm',[[0,zero],[8,[-50,48,-20]],[10,[-65,40,-12]],[14,[-70,-45,16]],[18,[-15,-40,10]],[24,zero]]);
  rotation(moves.claw,'left_arm',[[0,zero],[8,[-12,0,12]],[14,[-15,0,8]],[24,zero]]);
  rotation(moves.double_claw,'torso',[[0,zero],[7,[-8,-12,0]],[12,[12,14,0]],[17,zero],[20,[-8,12,0]],[25,[12,-14,0]],[31,[6,-8,0]],[38,zero]]);
  rotation(moves.double_claw,'right_arm',[[0,zero],[6,[-50,48,-20]],[8,[-65,40,-12]],[12,[-70,-45,16]],[16,[-15,-40,10]],[20,[-8,0,0]],[30,[-10,0,-8]],[38,zero]]);
  rotation(moves.double_claw,'left_arm',[[0,zero],[12,[-12,0,8]],[17,[-10,0,0]],[20,[-50,-48,20]],[22,[-65,-40,12]],[26,[-70,45,-16]],[31,[-15,40,-10]],[38,zero]]);
  for(const side of ['right','left'])rotation(moves.pounce,`${side}_arm`,[[0,zero],[8,[-45,0,side==='right'?-16:16]],[13,[-68,0,0]],[19,[-68,0,0]],[24,[-12,0,0]],[36,zero]]);
 }else if(species==='spitter'){
  moves.spit=retime(base,[[0,0],[10,10],[14,14],[30,24]]);
  moves.volley=retime(base,[[0,0],[10,10],[14,14],[20,10],[24,14],[30,10],[34,14],[46,24]]);
  for(const clip of Object.values(moves))for(const arm of ['left_arm','right_arm'])for(const [t,v] of Object.entries(clip.bones[arm].rotation))clip.bones[arm].rotation[t]=v.map(x=>x*.28);
  moves.retreat={loop:false,animation_length:1.3,bones:{}};
  rotation(moves.retreat,'torso',[[0,zero],[5,[-12,0,0]],[14,[-8,0,0]],[26,zero]]);
  rotation(moves.retreat,'head',[[0,zero],[5,[14,0,0]],[18,[14,0,0]],[26,zero]]);
  for(const [i,side] of ['right','left'].entries()){
   rotation(moves.retreat,`${side}_arm`,[[0,zero],[5,[-45,0,i?25:-25]],[16,[-30,0,i?15:-15]],[26,zero]]);
   rotation(moves.retreat,`${side}_leg`,[[0,zero],[5,[i?25:-25,0,0]],[10,[i?-25:25,0,0]],[16,[i?20:-20,0,0]],[26,zero]]);
  }
 }else{
  moves.hammer=retime(base,[[0,0],[13,10],[18,14],[34,24]]);
  rotation(moves.hammer,'torso',[[0,zero],[12,[-12,-10,0]],[18,[20,-8,0]],[23,[24,-8,0]],[34,zero]]);
  rotation(moves.hammer,'right_arm',[[0,zero],[12,[-150,0,-18]],[16,[-100,0,-18]],[18,[-85,0,-18]],[22,[-55,0,-12]],[27,[-10,0,0]],[34,zero]]);
  rotation(moves.hammer,'left_arm',[[0,zero],[12,[-20,0,12]],[20,[-30,0,10]],[34,zero]]);
  moves.ground_slam=retime(base,[[0,0],[18,10],[24,14],[32,17],[46,24]]);
  rotation(moves.ground_slam,'torso',[[0,zero],[18,[-25,0,0]],[25,[35,0,0]],[32,[35,0,0]],[46,zero]]);
  // Lower the shoulder chain with the crouch so the fist bottoms actually meet the floor.
  moves.ground_slam.bones.torso.position={'0':zero,'0.9':zero,'1.25':[0,-5.5,0],'1.6':[0,-5.5,0],'2.3':zero};
  for(const side of ['right','left'])rotation(moves.ground_slam,`${side}_arm`,[[0,zero],[18,[-155,0,side==='right'?-12:12]],[25,[-65,0,0]],[32,[-65,0,0]],[46,zero]]);
  moves.charge={loop:false,animation_length:2.3,bones:{}};
  rotation(moves.charge,'torso',[[0,zero],[10,[-15,-20,0]],[12,[28,12,0]],[26,[28,12,0]],[34,[35,-12,0]],[46,zero]]);
  rotation(moves.charge,'head',[[0,zero],[12,[-20,0,0]],[26,[-20,0,0]],[46,zero]]);
  for(const [i,side] of ['right','left'].entries()){
   rotation(moves.charge,`${side}_arm`,[[0,zero],[10,[-60,0,i?25:-25]],[26,[-55,0,i?20:-20]],[34,[-10,0,0]],[46,zero]]);
   rotation(moves.charge,`${side}_leg`,[[0,zero],[12,[i?-30:30,0,0]],[16,[i?30:-30,0,0]],[20,[i?-30:30,0,0]],[24,[i?30:-30,0,0]],[30,zero],[46,zero]]);
  }
 }
 Object.assign(doc.animations,moves);
 if(species==='brute')for(const clip of Object.values(doc.animations))for(const side of ['right','left'])clip.bones[side+'_forearm']??={rotation:zero};
 if(species==='brute')for(const clip of Object.values(doc.animations))clip.bones.root={rotation:zero,position:zero,scale:one};
 if(species==='brute')for(const clip of Object.values(doc.animations))for(const side of ['right','left'])clip.bones[side+'_shin']={rotation:zero,position:zero,scale:one};
 fs.writeFileSync(file,JSON.stringify(doc,null,2)+'\n');
 const table={fps:30,bones,clips:{}};
 for(const [name,clip] of Object.entries({idle:doc.animations.idle,...moves})){
  table.clips[name]=Array.from({length:Math.round(clip.animation_length*30)+1},(_,f)=>bones.map(bone=>{
   const c=clip.bones[bone]??{},r=sample(c.rotation,f/30).map((v,i)=>v*Math.PI/180*(i<2?-1:1));
   return [...r,...sample(c.position,f/30),...sample(c.scale,f/30,one)].map(v=>+v.toFixed(7));
  }));
  if(name!=='idle')provenance.clips[`${species}/${name}`]={seconds:clip.animation_length,frames:table.clips[name].length};
 }
 fs.writeFileSync(path.join(assets,`models/${species}_combat_motion.json`),JSON.stringify(table)+'\n');
}
fs.mkdirSync(path.join(root,'art/mutant-combat'),{recursive:true});
fs.writeFileSync(path.join(root,'art/mutant-combat/manifest.json'),JSON.stringify(provenance,null,2)+'\n');
console.log('Built 9 authored clips and 3 authoritative pose tables.');
