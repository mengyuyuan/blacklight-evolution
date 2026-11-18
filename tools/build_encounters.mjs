import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
const root=path.resolve(import.meta.dirname,'..'),assets=path.join(root,'src/main/resources/assets/blacklight');
const write=(p,v)=>{fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,typeof v==='string'||Buffer.isBuffer(v)?v:JSON.stringify(v,null,2)+'\n');};
const kinds=[['leader_hunter','领袖猎手',1.5,3.2],['brawler','格斗者',1.3,2.5],['juggernaut','主宰',2.2,3.6],['goliath','歌利亚',4.5,8],['flyer','飞行感染体',1.6,1.2],['elizabeth_greene','伊丽莎白·格林',5.5,8],['alex_mercer','阿历克斯·墨瑟',.8,2.1],['roland','罗兰',.8,2.1],['archer','卡伦·阿彻',.75,2],['galloway','萨布丽娜·加洛韦',.8,2.1],['koenig','安东·柯尼格',.75,2],['captain_cross','罗伯特·克罗斯',.75,2],['super_soldier','超级士兵',1,2.4],['recon_uav','病毒侦察无人机',1.5,.65]];
function crc(b){let c=0xffffffff;for(const n of b){c^=n;for(let j=0;j<8;j++)c=c&1?0xedb88320^(c>>>1):c>>>1;}return (c^0xffffffff)>>>0;}
function png(colors){const n=64,raw=Buffer.alloc(n*(n*4+1));for(let y=0;y<n;y++)for(let x=0;x<n;x++){let p=y*(n*4+1)+1+x*4,c=colors[Math.floor(x/8)%colors.length],shade=((x*17+y*13)%9-4)*2;for(let i=0;i<3;i++)raw[p+i]=Math.max(0,Math.min(255,c[i]+shade));raw[p+3]=255;}
 const chunk=(name,data)=>{const t=Buffer.from(name),len=Buffer.alloc(4),check=Buffer.alloc(4);len.writeUInt32BE(data.length);check.writeUInt32BE(crc(Buffer.concat([t,data])));return Buffer.concat([len,t,data,check]);};let h=Buffer.alloc(13);h.writeUInt32BE(n);h.writeUInt32BE(n,4);h[8]=8;h[9]=6;return Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',h),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);}
const colors=[[48,39,39],[115,41,37],[174,131,98],[22,25,27],[207,47,35],[79,91,83],[226,206,166],[64,14,20]];
const protectedMonsters=fs.existsSync(path.join(root,'art/encounters/monster-style-provenance.json'))?JSON.parse(fs.readFileSync(path.join(root,'art/encounters/monster-style-provenance.json'))).models.map(m=>m.id):[];
for(const [id,zh,width,height] of kinds){
 if(protectedMonsters.includes(id))continue;
 if(id==='brawler'){write(path.join(assets,'models/item',id+'_spawn_egg.json'),{parent:'minecraft:item/template_spawn_egg'});continue;}
 if(id==='elizabeth_greene'&&fs.existsSync(path.join(root,'art/encounters/greene-surface-provenance.json')))continue;
 const bones=[],H=height*16,W=width*16;
 const bone=(name,parent,pivot)=>{const b={name,pivot,cubes:[]};if(parent)b.parent=parent;bones.push(b);return b;};
 const box=(b,x,y,z,w,h,d,mat=0,rot)=>{const c={origin:[x,y,z],size:[w,h,d],uv:Object.fromEntries(['north','east','south','west','up','down'].map(f=>[f,{uv:[mat*8,0],uv_size:[8,8]}]))};if(rot){c.pivot=[x+w/2,y+h/2,z+d/2];c.rotation=rot;}b.cubes.push(c);};
 const rootBone=bone('root',null,[0,0,0]),torso=bone('torso','root',[0,H*.43,0]);
 if(id==='recon_uav'){
   box(torso,-7,3,-5,14,5,10,5);box(torso,-4,7,-3,8,2,6,3);let head=bone('head','torso',[0,4,-5]);box(head,-3,0,-8,6,4,4,3);box(head,-1.5,1,-8.2,3,2,.4,4);
   for(let i=0;i<4;i++){let x=i%2?12:-12,z=i<2?10:-10;box(torso,-1,5,0,2,2,Math.hypot(x,z),3,[0,Math.atan2(x,z)*180/Math.PI,0]);torso.cubes.at(-1).pivot=[0,6,0];let r=bone('rotor_'+i,'torso',[x,8,z]);box(r,x-9,8,z-1,18,.6,2,0);box(r,x-1,8,z-9,2,.6,18,0);box(torso,x-2,4,z-2,4,4,4,5);}
 }else if(id==='elizabeth_greene'){
   box(torso,-W*.28,H*.2,-W*.3,W*.56,H*.55,W*.6,1);box(torso,-W*.2,H*.25,-W*.36,W*.4,H*.44,4,7);
   for(let layer=0;layer<5;layer++)for(let j=0;j<12;j++){let a=j*Math.PI/6,r=W*(.28+Math.sin(layer/4*Math.PI)*.12);box(torso,Math.sin(a)*r-6,H*(.18+layer*.1),Math.cos(a)*r-6,12,H*.16,12,j%3===0?7:1,[0,-a*180/Math.PI,12*Math.sin(a)]);}
   const head=bone('head','torso',[0,H*.8,0]);box(head,-W*.12,H*.75,-W*.17,W*.24,H*.22,W*.34,2);box(head,-W*.1,H*.79,-W*.2,W*.2,H*.14,4,4);
   for(let i=0;i<6;i++){let a=i*Math.PI/3,x=Math.sin(a)*W*.32,z=Math.cos(a)*W*.32,b=bone(i===0?'right_arm':i===3?'left_arm':'petal_'+i,'torso',[x,H*.3,z]);box(b,x-6,H*.1,z-6,12,H*.62,12,1,[0,0,(i<3?1:-1)*22]);}
   for(let i=0;i<3;i++){let a=i*2.094,x=-Math.sin(a)*W*.63,z=-Math.cos(a)*W*.63,b=bone('weak_'+i,'root',[x,3,z]);box(b,x-12,2.4,z-12,24,33.6,24,4);box(b,x-14,10,z-14,28,6,28,1);}
 }else if(id==='flyer'){
   box(torso,-9,4,-12,18,12,24,1);let head=bone('head','torso',[0,13,-11]);box(head,-6,10,-20,12,8,12,2);
   for(const [s,sign] of [['right',-1],['left',1]]){let wing=bone(s+'_arm','torso',[sign*8,12,0]);for(let j=0;j<4;j++)box(wing,sign<0?-10-j*10:8+j*10,10,-10+j*2,12,2,22-j*3,1,[0,sign*12,0]);let leg=bone(s+'_leg','torso',[sign*5,5,6]);box(leg,sign*5-2,0,5,4,6,4,6);}
 }else{
   const human=kinds.findIndex(k=>k[0]===id)>=6,bodyW=human?W*.65:W*.7,armW=human?W*.25:W*.29;
   box(torso,-bodyW/2,H*.43,-W*.22,bodyW,H*.34,W*.44,human?3:1);box(torso,-bodyW*.45,H*.43,-W*.24,bodyW*.9,H*.1,W*.49,human?0:7);
   let head=bone('head','torso',[0,H*.78,0]);box(head,-W*.23,H*.77,-W*.25,W*.46,H*.23,W*.5,human?2:1);
   if(human){box(head,-W*.24,H*.91,-W*.26,W*.48,H*.1,W*.52,3);if(['super_soldier','captain_cross'].includes(id)){box(head,-W*.25,H*.79,-W*.29,W*.5,H*.13,2,5);box(head,-W*.19,H*.84,-W*.31,W*.38,2,1,4);}}
   else for(let j=0;j<5;j++)box(torso,-bodyW*.5+j*bodyW*.2,H*.74,W*.12,3,H*.12,4,6,[25,0,0]);
   for(const [side,sign] of [['right',-1],['left',1]]){
     let arm=bone(side+'_arm','torso',[sign*W*.43,H*.72,0]);box(arm,sign*W*.43-armW/2,H*.28,-armW/2,armW,H*.45,armW,human?3:1);
     if(['juggernaut','goliath','super_soldier'].includes(id))box(arm,sign*W*.43-armW*.75,H*.27,-armW*.8,armW*1.5,H*.26,armW*1.6,id==='super_soldier'?5:6);
     let leg=bone(side+'_leg','root',[sign*W*.2,H*.43,0]);box(leg,sign*W*.2-W*.13,0,-W*.13,W*.26,H*.44,W*.28,human?0:1);box(leg,sign*W*.2-W*.14,0,-W*.22,W*.28,H*.08,W*.4,3);
     if(!human||['roland','koenig'].includes(id))for(let j=0;j<4;j++)box(arm,sign*W*.43-armW*.45+j*armW*.25,H*.18,-armW*.65,armW*.15,H*.16,armW*.2,6,[18,0,0]);
   }
   if(id==='alex_mercer'){
     box(torso,-bodyW*.46,H*.48,-W*.25,bodyW*.92,H*.25,1,2);box(torso,-bodyW*.46,H*.48,-W*.27,bodyW*.92,1,1,4);
     let claws=bone('claws','right_arm',[-W*.43,H*.28,0]);for(let i=0;i<4;i++)box(claws,-W*.56+i*2,H*.1,-5,1.3,H*.23,2,6,[15,0,0]);
     let hammer=bone('hammer','right_arm',[-W*.43,H*.3,0]);box(hammer,-W*.75,H*.22,-6,W*.65,H*.2,12,0);
     let blade=bone('blade','right_arm',[-W*.43,H*.3,0]);box(blade,-W*.50,-H*.12,-4,3,H*.5,7,6,[0,0,8]);
   }
   if(id==='goliath')for(let i=0;i<2;i++){let x=(i===0?-1:1)*W*.2,z=-W*.1,b=bone('weak_'+i,i===0?'right_leg':'left_leg',[x,3,z]);box(b,x-12,2.4,z-12,24,33.6,24,4);}
   if(id==='captain_cross'){const arm=bones.find(b=>b.name==='right_arm');box(arm,-W*.43-1,H*.13,-5,2,H*.3,2,5);box(arm,-W*.43-1.4,H*.12,-5.4,2.8,3,2.8,4);}
   if(['roland','galloway','koenig','archer'].includes(id)){
     const arm=bones.find(b=>b.name==='right_arm');
     if(id==='roland')for(let j=0;j<3;j++)box(arm,-W*.6+j*2,-H*.08,-4,2,H*.45,5,6,[0,0,6]);
     if(id==='galloway')box(arm,-W*.8,H*.15,-7,W*.75,H*.23,14,0);
     if(id==='koenig'){box(torso,-bodyW*.52,H*.42,-W*.28,bodyW*1.04,H*.27,1.4,6);for(let j=0;j<4;j++)box(arm,-W*.56+j*1.5,H*.09,-4,1,H*.24,2,1,[8,0,0]);}
     if(id==='archer'){box(head,-W*.26,H*.71,W*.10,W*.52,H*.3,W*.17,3);for(let j=0;j<5;j++)box(arm,-W*.6+j*1.1,H*.1-j*.8,-3,1,H*.28+j,1,1,[0,0,j*8-15]);}
   }
   if(id==='goliath'){let arm=bones.find(b=>b.name==='right_arm');for(let j=0;j<4;j++)box(arm,-W*.8-j,H*.15-j*7,-W*.25,W*.5,H*.21,W*.5,j%2?6:0,[0,0,8]);}
 }
 write(path.join(assets,'geo',id+'_mc.geo.json'),{format_version:'1.12.0','minecraft:geometry':[{description:{identifier:'geometry.'+id,texture_width:64,texture_height:64,visible_bounds_width:Math.max(6,width*3),visible_bounds_height:height*2,visible_bounds_offset:[0,height/2,0]},bones}]});
 const palette=colors.map(c=>[...c]);if(id==='archer'){palette[3]=[43,21,19];palette[0]=[80,42,32];}if(id==='roland')palette[0]=[35,36,49];if(id==='galloway')palette[3]=[83,53,30];if(id==='koenig')palette[3]=[60,57,63];
 write(path.join(assets,'textures/entity',id+'_mc.png'),png(palette));
 write(path.join(assets,'animations',id+'.animation.json'),{format_version:'1.8.0',animations:{idle:{loop:true,animation_length:2,bones:{torso:{scale:{'0':[1,1,1],'1':[1.015,1.008,1.015],'2':[1,1,1]}}}}}});
 write(path.join(assets,'models/item',id+'_spawn_egg.json'),{parent:'minecraft:item/template_spawn_egg'});
}
for(const lang of ['zh_cn','en_us']){const p=path.join(assets,'lang',lang+'.json'),v=JSON.parse(fs.readFileSync(p,'utf8'));v['itemGroup.blacklight.encounters']=lang==='zh_cn'?'黑光：生物与首领':'Blacklight: Encounters';for(const [id,zh]of kinds){const name=lang==='zh_cn'?zh:id.split('_').map(w=>w[0].toUpperCase()+w.slice(1)).join(' ');v['entity.blacklight.'+id]=name;v['item.blacklight.'+id+'_spawn_egg']=name+(lang==='zh_cn'?'刷怪蛋':' Spawn Egg');}write(p,v);}
const active=new Set(['juggernaut','goliath','flyer','elizabeth_greene','super_soldier','recon_uav']);
write(path.join(root,'art/encounters/manifest.json'),{source:'Original Minecraft adaptation geometry and procedural texture palette. Not extracted Prototype models or a claim of exact original-game AI.',entities:kinds.map(([id,name,width,height])=>({id,name,width,height,status:id==='brawler'?'alias_of_blacklight_hunter':active.has(id)?'active':'retired_draft'})),activeSpawnEggCount:25,animationClock:'EncounterMove server tick / EncounterPose'});
await import('./author_encounter_models.mjs');
await import('./restore_existing_encounter_models.mjs');
console.log('Authored distinct new encounter silhouettes using existing model material patterns; Brawler restored verbatim. Runtime visual review required.');
