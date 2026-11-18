// Draft cuboid models. These have not met the user's existing model style/detail standard.
// These are authored models, not extracted commercial meshes or character-specific source rigs.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
const root=path.resolve(import.meta.dirname,'..'),assets=path.join(root,'src/main/resources/assets/blacklight');
const manifest=JSON.parse(fs.readFileSync(path.join(root,'art/encounters/manifest.json'),'utf8'));
if(manifest.modelAudit)throw Error('Run build_encounters.mjs to rebuild the base geometry before adding detail.');
const size=256;
const base=[[48,39,37],[111,77,65],[174,131,98],[24,26,29],[153,35,33],[75,88,80],[155,139,111],[48,23,22]];
function crc(bytes){let c=0xffffffff;for(const b of bytes){c^=b;for(let i=0;i<8;i++)c=c&1?0xedb88320^(c>>>1):c>>>1;}return(c^0xffffffff)>>>0;}
function png(pixels){const raw=Buffer.alloc(size*(size*4+1));for(let y=0;y<size;y++)pixels.copy(raw,y*(size*4+1)+1,y*size*4,(y+1)*size*4);const chunk=(name,b)=>{const t=Buffer.from(name),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(b.length);c.writeUInt32BE(crc(Buffer.concat([t,b])));return Buffer.concat([n,t,b,c]);};const h=Buffer.alloc(13);h.writeUInt32BE(size);h.writeUInt32BE(size,4);h[8]=8;h[9]=6;return Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',h),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);}
const audits=[];
for(const entry of manifest.entities){
 if(entry.id==='brawler')continue; // Existing Hunter model is reused by restore_existing_encounter_models.mjs.
 const {id,width,height}=entry,W=width*16,H=height*16,file=path.join(assets,'geo',id+'_mc.geo.json'),data=JSON.parse(fs.readFileSync(file,'utf8')),geo=data['minecraft:geometry'][0],bones=geo.bones;
 const find=n=>bones.find(b=>b.name===n);
 const bone=(name,parent,pivot)=>{const b={name,parent,pivot,cubes:[]};bones.push(b);return b;};
 const box=(b,origin,dimensions,material=1,rotation)=>{if(!b)return;const c={origin,size:dimensions,uv:{north:{uv:[material*8,0],uv_size:[8,8]}}};if(rotation){c.rotation=rotation;c.pivot=origin.map((n,i)=>n+dimensions[i]/2);}b.cubes.push(c);};
 const centered=(b,p,s,m=1,r)=>box(b,p.map((n,i)=>n-s[i]/2),s,m,r);
 const human=['alex_mercer','roland','archer','galloway','koenig','captain_cross','super_soldier'].includes(id),upright=id!=='flyer'&&id!=='elizabeth_greene'&&id!=='recon_uav';
 if(upright){
  const body=find('torso'),head=find('head');
  // Separate elbows, wrists, fingers and knees; detail follows the same articulated bones.
  for(const [side,sign]of [['right',-1],['left',1]]){
   const arm=find(side+'_arm'),leg=find(side+'_leg'),x=sign*W*.43,elbow=H*.50,wrist=H*.30;
   const fore=bone(side+'_forearm',side+'_arm',[x,elbow,0]),hand=bone(side+'_hand',side+'_forearm',[x,wrist,0]),shin=bone(side+'_shin',side+'_leg',[sign*W*.2,H*.22,0]);
   const armBody=arm.cubes[0];if(armBody){const bottom=armBody.origin[1],split=Math.min(elbow,armBody.origin[1]+armBody.size[1]);const lower=structuredClone(armBody);lower.size[1]=split-bottom;fore.cubes.push(lower);armBody.origin[1]=split;armBody.size[1]-=lower.size[1];}
   for(const c of [...arm.cubes].slice(1)){if(c.origin[1]+c.size[1]/2<elbow){arm.cubes.splice(arm.cubes.indexOf(c),1);(c.origin[1]+c.size[1]/2<wrist?hand:fore).cubes.push(c);}}
   const thigh=leg.cubes[0];if(thigh){const lower=structuredClone(thigh);lower.size[1]=H*.22;shin.cubes.push(lower);thigh.origin[1]=H*.22;thigh.size[1]-=H*.22;}
   for(const c of [...leg.cubes].slice(1)){if(c.origin[1]<H*.1){leg.cubes.splice(leg.cubes.indexOf(c),1);shin.cubes.push(c);}}
   // Keep Goliath weak armor attached to the upper leg: its server hitbox follows that pivot.
   centered(arm,[x,H*.69,0],[W*.33,H*.09,W*.35],human?3:0,[0,0,sign*8]);
   centered(arm,[x,H*.58,-W*.145],[W*.18,H*.13,W*.06],human?3:1,[0,0,sign*4]);
   centered(fore,[x,elbow,-W*.10],[W*.22,H*.055,W*.15],human?0:0);
   centered(fore,[x,H*.40,-W*.15],[W*.11,H*.15,W*.045],human?5:1);
   centered(hand,[x,wrist,-W*.01],[W*.24,H*.07,W*.25],human?0:0);
   for(let f=0;f<4;f++){const fx=x+(f-1.5)*W*.057,knuckle=bone(side+'_finger_'+f,side+'_hand',[fx,wrist-H*.025,-W*.09]);centered(knuckle,[fx,wrist-H*.05,-W*.1],[W*.045,H*.065,W*.075],human?0:0);if(!human)centered(knuckle,[fx,wrist-H*.085,-W*.13],[W*.03,H*.04,W*.05],6,[15,0,0]);}
   centered(shin,[sign*W*.2,H*.21,-W*.14],[W*.24,H*.07,W*.08],human?0:0);
   centered(shin,[sign*W*.2,H*.11,-W*.13],[W*.13,H*.15,W*.04],human?3:1);
   if(!human){centered(body,[sign*W*.19,H*.67,-W*.25],[W*.31,H*.10,W*.10],1,[0,0,sign*5]);centered(body,[sign*W*.27,H*.55,-W*.18],[W*.10,H*.13,W*.10],0,[0,0,sign*14]);
    for(let rib=0;rib<3;rib++)centered(body,[sign*W*.20,H*(.60-rib*.045),-W*.235],[W*.29,H*.028,W*.075],rib%2?7:1,[0,0,sign*9]);
    centered(body,[sign*W*.22,H*.73,W*.21],[W*.25,H*.10,W*.10],0,[0,0,sign*12]);
   }
  }
  for(const name of ['claws','hammer','blade']){const form=find(name);if(form)form.parent='right_hand';}
  if(id==='alex_mercer'){
   const x=-W*.43,y=H*.30,blade=find('blade'),claws=find('claws'),hammer=find('hammer');
   blade.cubes=[];centered(blade,[x,y-4,-1],[4,9,7],0,[0,0,5]);centered(blade,[x+.5,y-11,-1],[2.7,7,5],0,[0,0,8]);centered(blade,[x+1.4,y-16,-1],[1,5,2.5],6,[0,0,12]);centered(blade,[x-1.5,y-6,-4.1],[.7,11,.6],6,[0,0,5]);centered(blade,[x+.2,y-3,-4.2],[.5,5,.5],4,[0,0,5]);
   claws.cubes=[];centered(claws,[x,y,-1],[5,4,5],0);for(let finger=0;finger<4;finger++){let fx=x+(finger-1.5)*1.3;centered(claws,[fx,y-4.5,-3.2],[1,7,1.4],0,[14,0,(finger-1.5)*4]);centered(claws,[fx,y-8.5,-4.8],[.65,3.5,1],6,[28,0,(finger-1.5)*4]);}
   hammer.cubes=[];centered(hammer,[x,y+1,-.5],[9,7,9],0);centered(hammer,[x,y+1,-5],[8,5,1.4],3);centered(hammer,[x,y+4,-.5],[7,2,7],0);for(let n=0;n<3;n++)centered(hammer,[x-2.5+n*2.5,y+1,-5.8],[.5,5,.5],4);
  }
  if(human){
   // Actual facial features, collars, seams and boots, instead of an unmarked skin-colored cuboid.
   for(const sign of [-1,1]){centered(head,[sign*W*.105,H*.873,-W*.258],[W*.1,H*.018,W*.022],7);centered(head,[sign*W*.105,H*.878,-W*.272],[W*.025,H*.012,W*.012],0);centered(head,[sign*W*.235,H*.85,-W*.03],[W*.035,H*.06,W*.09],2);centered(body,[sign*W*.19,H*.75,-W*.25],[W*.19,H*.075,W*.04],3,[0,0,sign*20]);}
   centered(head,[0,H*.849,-W*.272],[W*.055,H*.045,W*.055],2);centered(head,[0,H*.813,-W*.265],[W*.16,H*.015,W*.02],7);
   centered(body,[0,H*.59,-W*.255],[W*.023,H*.28,W*.023],6);centered(body,[0,H*.466,-W*.257],[W*.12,H*.032,W*.035],5);
   for(const s of [-1,1]){centered(body,[s*W*.23,H*.55,-W*.25],[W*.14,H*.045,W*.035],0);centered(body,[s*W*.24,H*.62,-W*.25],[W*.14,H*.015,W*.04],6);}
   if(id==='alex_mercer'){centered(head,[0,H*.985,0],[W*.48,H*.055,W*.46],3);centered(body,[0,H*.65,W*.235],[W*.1,H*.12,W*.025],4);for(const s of [-1,1])centered(body,[s*W*.18,H*.52,W*.24],[W*.18,H*.11,W*.035],3);}
   if(id==='super_soldier'||id==='captain_cross'){for(const s of [-1,1]){centered(body,[s*W*.18,H*.66,-W*.27],[W*.25,H*.13,W*.10],5);centered(body,[s*W*.21,H*.53,-W*.28],[W*.16,H*.08,W*.11],5);}centered(head,[0,H*.831,-W*.31],[W*.25,H*.08,W*.10],0);for(const s of [-1,1])centered(head,[s*W*.15,H*.82,-W*.29],[W*.10,H*.06,W*.1],5);}
   if(['galloway','koenig','archer','roland'].includes(id)){for(const s of [-1,1])centered(body,[s*W*.20,H*.39,W*.07],[W*.29,H*.15,W*.38],3,[0,0,-s*4]);}
  }else{
   const jaw=bone('jaw','head',[0,H*.80,-W*.10]);centered(head,[0,H*.83,-W*.27],[W*.34,H*.055,W*.10],7);centered(jaw,[0,H*.795,-W*.25],[W*.36,H*.055,W*.25],0);centered(head,[0,H*.967,-W*.04],[W*.42,H*.05,W*.38],0);
   for(const s of [-1,1]){centered(head,[s*W*.20,H*.855,-W*.20],[W*.08,H*.10,W*.22],1);for(let f=0;f<3;f++){centered(head,[s*W*(.035+f*.05),H*.837,-W*.315],[W*.025,H*.035,W*.04],6);centered(jaw,[s*W*(.04+f*.06),H*.821,-W*.32],[W*.025,H*.028,W*.035],6);}}
   for(let n=0;n<4;n++)centered(body,[0,H*(.48+n*.065),W*.24],[W*.13,H*.045,W*.09],0);
   if(id==='leader_hunter'||id==='brawler'){centered(body,[0,H*.74,W*.15],[W*.63,H*.15,W*.39],0,[12,0,0]);centered(head,[0,H*.90,-W*.27],[W*.42,H*.11,W*.20],1);}
   if(id==='juggernaut'||id==='goliath'){for(const s of [-1,1])for(let n=0;n<3;n++)centered(body,[s*W*.24,H*(.54+n*.065),-W*.26],[W*.34,H*.055,W*.12],0,[0,0,s*9]);}
   const anchor=[0,H*.78,0],scale=id==='goliath'?.48:id==='juggernaut'?.58:.68,advance=id==='leader_hunter'||id==='brawler'?-W*.12:0;
   const headPoint=p=>p.map((n,i)=>anchor[i]+(n-anchor[i])*scale+(i===2?advance:0));
   for(const b of [head,jaw]){b.pivot=headPoint(b.pivot);for(const c of b.cubes){c.origin=headPoint(c.origin);c.size=c.size.map(n=>n*scale);if(c.pivot)c.pivot=headPoint(c.pivot);}}
  }
 }
 if(id==='elizabeth_greene'){
  const body=find('torso'),head=find('head');
  centered(find('root'),[0,H*.14,0],[W*.44,H*.28,W*.44],0);
  for(let row=0;row<5;row++)for(let n=0;n<9;n++){let a=n*Math.PI*2/9,r=W*(.27+row*.014);centered(body,[Math.sin(a)*r,H*(.22+row*.09),Math.cos(a)*r],[W*.11,H*.06,W*.09],n%3===0?4:0,[12,-a*180/Math.PI,0]);}
  centered(head,[0,H*.84,-W*.20],[W*.18,H*.08,W*.04],7);for(let n=0;n<7;n++)centered(head,[(n-3)*W*.023,H*.825,-W*.23],[W*.015,H*.04,W*.03],6);
  for(let n=0;n<3;n++){const weak=find('weak_'+n),[x,,z]=weak.pivot;centered(weak,[x*.5,16,z*.5],[8,10,Math.hypot(x,z)+6],1,[0,Math.atan2(x,z)*180/Math.PI,0]);for(let ring=0;ring<3;ring++)centered(weak,[x,7+ring*8,z],[26-ring*2,3,26-ring*2],ring===1?4:0);}
 }
 if(id==='flyer'){
  const body=find('torso'),head=find('head');for(const s of [-1,1]){for(let rib=0;rib<5;rib++)centered(find(s<0?'right_arm':'left_arm'),[s*(10+rib*8),11,-6+rib*2],[1.4,2.4,18-rib*2],0,[0,s*12,0]);centered(head,[s*3.7,14,-19.5],[2,2,1],4);centered(head,[s*4.2,10,-17],[1.5,4,2],6,[15,0,s*12]);}for(let n=0;n<5;n++)centered(body,[0,15-n*.4,-8+n*4],[3,3,2],0);
 }
 if(id==='recon_uav'){const body=find('torso');for(let i=0;i<5;i++)centered(body,[-4+i*2,8.1,0],[.7,.5,5],0);for(const s of [-1,1]){centered(body,[s*6,1,2],[1,7,1],5);centered(body,[s*6,-2,0],[1,1,12],0);centered(body,[s*6,7,-3],[1,.5,2],s<0?4:6);}centered(find('head'),[0,2,-8.5],[2,2,.4],4);}
 // Independent UV islands, directional face shading and small tendon/plate/cloth pixels.
 const pixels=Buffer.alloc(size*size*4);for(let i=0;i<pixels.length;i+=4){pixels[i]=30;pixels[i+1]=24;pixels[i+2]=23;pixels[i+3]=255;}
 const palette=base.map(c=>[...c]);if(id==='archer')palette[3]=[55,31,27];if(id==='roland')palette[3]=[34,38,47];if(id==='galloway')palette[3]=[80,57,40];if(id==='koenig')palette[3]=[64,63,63];
 let x=1,y=1,rowHeight=0,faces=0;
 function tile(w,h,mat,face,seed){w=Math.max(2,Math.min(14,Math.ceil(w)));h=Math.max(2,Math.min(14,Math.ceil(h)));if(x+w+1>=size){x=1;y+=rowHeight+2;rowHeight=0;}if(y+h+1>=size)throw Error('Atlas overflow '+id);const uv={uv:[x,y],uv_size:[w,h]},color=palette[mat];
  for(let j=-1;j<=h;j++)for(let i=-1;i<=w;i++){const u=Math.max(0,Math.min(w-1,i)),v=Math.max(0,Math.min(h-1,j));let shade=(face==='up'?6:face==='down'?-7:0)+((u*13+v*7+seed*11)%5-2);if(u===0||v===0||u===w-1||v===h-1)shade-=5;if(mat===1&&(u+Math.floor(v/3)+seed)%6===0)shade-=17;if(mat===0&&(v===h-2||u===1))shade+=6;if(mat===3&&v%5===4)shade-=4;if(mat===4&&(u+v+seed)%6===0)shade+=18;if(mat===6&&u===Math.floor(w/2))shade+=12;const p=((y+j)*size+x+i)*4;for(let c=0;c<3;c++)pixels[p+c]=Math.max(0,Math.min(255,color[c]+shade));pixels[p+3]=255;}
  x+=w+2;rowHeight=Math.max(rowHeight,h);faces++;return uv;
 }
 for(const b of bones)for(const c of b.cubes){const mat=Math.max(0,Math.min(7,Math.floor((c.uv?.north?.uv?.[0]??0)/8))),[w,h,d]=c.size;c.uv=Object.fromEntries([['north',w,h],['south',w,h],['east',d,h],['west',d,h],['up',w,d],['down',w,d]].map(([f,a,b])=>[f,tile(a*32/H,b*32/H,mat,f,faces)]));}
 geo.description.texture_width=size;geo.description.texture_height=size;
 fs.writeFileSync(file,JSON.stringify(data,null,2)+'\n');fs.writeFileSync(path.join(assets,'textures/entity',id+'_mc.png'),png(pixels));
 audits.push({id,bones:bones.length,cuboids:bones.reduce((n,b)=>n+b.cubes.length,0),textureSize:size,uvFaces:faces,atlasUsedHeight:y+rowHeight+1});
}
manifest.styleReference='Existing Hunter and Supreme Hunter are the visual standard; these generated drafts are not approved as matching their style or detail.';
manifest.modelAudit=audits;manifest.styleReferenceSha256=crypto.createHash('sha256').update(fs.readFileSync(path.join(assets,'models/supreme_hunter_block.json'))).digest('hex');
manifest.motionReference={source:'Existing source-derived supreme_hunter_block.json and art/hunter/supreme-block-provenance.json',targets:['leader_hunter','juggernaut','goliath'],scope:'Retargeted Supreme Hunter reference clips, not decoded native animations for these characters. Goliath legs retain the authoritative armor contact clock. Brawler reuses its own existing motion, other profiles retain authored draft motions.'};
fs.writeFileSync(path.join(root,'art/encounters/manifest.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(JSON.stringify(audits));
