// Authored encounter silhouettes plus preservation of Greene's separately authored Supreme-style model.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import {shapeFromExtractedReference} from './shape_encounters_from_reference.mjs';
const root=path.resolve(import.meta.dirname,'..'),assets=path.join(root,'src/main/resources/assets/blacklight');
const manifestFile=path.join(root,'art/encounters/manifest.json'),manifest=JSON.parse(fs.readFileSync(manifestFile));
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
function decode(file){const b=fs.readFileSync(file),w=b.readUInt32BE(16),h=b.readUInt32BE(20),channels=b[25]===6?4:3;let at=8,parts=[];while(at<b.length){const n=b.readUInt32BE(at),t=b.toString('ascii',at+4,at+8);if(t==='IDAT')parts.push(b.subarray(at+8,at+8+n));at+=12+n;}const raw=zlib.inflateSync(Buffer.concat(parts)),out=Buffer.alloc(w*h*channels);let p=0;for(let y=0;y<h;y++){const f=raw[p++];for(let x=0;x<w*channels;x++){const k=y*w*channels+x,a=x>=channels?out[k-channels]:0,c=y?out[k-w*channels]:0,d=y&&x>=channels?out[k-w*channels-channels]:0;let v=0;if(f===1)v=a;if(f===2)v=c;if(f===3)v=Math.floor((a+c)/2);if(f===4){const t=a+c-d,aa=Math.abs(t-a),cc=Math.abs(t-c),dd=Math.abs(t-d);v=aa<=cc&&aa<=dd?a:cc<=dd?c:d;}out[k]=(raw[p++]+v)&255;}}return{w,h,channels,out};}
const hunter=decode(path.join(assets,'textures/entity/hunter_mc.png')),supreme=decode(path.join(assets,'textures/entity/supreme_hunter_block.png'));
const hunterGeo=JSON.parse(fs.readFileSync(path.join(assets,'geo/blacklight_hunter_mc.geo.json')))['minecraft:geometry'][0];
function patch(atlas,rect){const [x,y,w,h]=rect,data=[];for(let j=0;j<h;j++)for(let i=0;i<w;i++){const k=((y+j)*atlas.w+x+i)*atlas.channels;data.push([...atlas.out.subarray(k,k+3)]);}const mean=[0,1,2].map(c=>data.reduce((n,p)=>n+p[c],0)/data.length);return{w,h,data,mean};}
const facePatch=(bone,cube,face='north')=>{const uv=hunterGeo.bones.find(b=>b.name===bone).cubes[cube].uv[face];return patch(hunter,[...uv.uv,...uv.uv_size]);};
const patches={flesh:facePatch('torso',0),hide:facePatch('hips',0),bone:facePatch('jaw',1)};
const sq=JSON.parse(fs.readFileSync(path.join(assets,'models/supreme_hunter_block.json'))).quads;
let best;
for(const q of sq){let us=[],vs=[];for(let i=4;i<q.length;i+=5){us.push(q[i+3]*256);vs.push(q[i+4]*256);}const x=Math.round(Math.min(...us)),y=Math.round(Math.min(...vs)),w=Math.round(Math.max(...us))-x,h=Math.round(Math.max(...vs))-y;if(w<4||h<4||x+w>256||y+h>256)continue;const p=patch(supreme,[x,y,w,h]),[r,g,b]=p.mean;const score=Math.abs(r-g)+Math.abs(b-g)+Math.abs(g-95)*.2;if(!best||score<best.score)best={score,p};}
patches.plate=best.p;
function crc(b){let c=0xffffffff;for(const n of b){c^=n;for(let j=0;j<8;j++)c=c&1?0xedb88320^(c>>>1):c>>>1;}return(c^0xffffffff)>>>0;}
function png(pixels,size){const raw=Buffer.alloc(size*(size*4+1));for(let y=0;y<size;y++)pixels.copy(raw,y*(size*4+1)+1,y*size*4,(y+1)*size*4);const chunk=(s,b)=>{const t=Buffer.from(s),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(b.length);c.writeUInt32BE(crc(Buffer.concat([t,b])));return Buffer.concat([n,t,b,c]);};const h=Buffer.alloc(13);h.writeUInt32BE(size);h.writeUInt32BE(size,4);h[8]=8;h[9]=6;return Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',h),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);}
const palette={flesh:[112,36,32],sinew:[157,46,36],hide:[61,29,30],plate:[86,79,77],black:[27,25,28],bone:[155,142,119],wound:[181,45,26],gold:[215,142,39],pupil:[12,9,11],skin:[173,137,113],hair:[37,29,27],cloth:[30,34,39],coat:[32,30,33],shirt:[157,153,144],metal:[75,84,85],eye:[163,172,165],blue:[63,145,173],red:[122,27,29]};
class Model{
 constructor(entry){this.id=entry.id;this.entry=entry;this.scale=entry.height/2;this.bones=[];this.by=new Map();this.colors=structuredClone(palette);this.notes=[];this.bone('root',null,[0,0,0]);}
 bone(name,parent,p){const b={name,pivot:p.map(n=>n*this.scale),cubes:[]};if(parent)b.parent=parent;this.bones.push(b);this.by.set(name,b);return name;}
 box(name,p,s,mat='flesh',rotation){const b=this.by.get(name);if(!b)throw Error(name);const c={origin:p.map((n,i)=>(n-s[i]/2)*this.scale),size:s.map(n=>n*this.scale),material:mat};if(rotation){c.pivot=p.map(n=>n*this.scale);c.rotation=rotation;}b.cubes.push(c);return c;}
 beam(name,a,b,w,d,mat='flesh'){const v=b.map((n,i)=>n-a[i]),l=Math.hypot(...v),c=a.map((n,i)=>(n+b[i])/2);return this.box(name,c,[w,l,d],mat,[-Math.atan2(Math.hypot(v[0],v[2]),v[1])*180/Math.PI,Math.atan2(v[0],v[2])*180/Math.PI,0]);}
 plates(name,p,s,mat='plate',count=3){for(let i=0;i<count;i++)this.box(name,[p[0],p[1]+i*s[1]*.62,p[2]+(i%2)*.15],[s[0]*(1-i*.07),s[1],s[2]],mat,[0,0,(i%2?1:-1)*3]);}
 tooth(name,p,s=[.65,1.1,.7],r=[0,0,0]){this.box(name,p,s,'bone',r);}
 fingers(side,x,y,z,big=1){for(let i=0;i<4;i++){const fx=x+(i-1.5)*.92*big,n=this.bone(side+'_finger_'+i,side+'_hand',[fx,y,z]);this.box(n,[fx,y-.7*big,z-.25],[.76*big,1.6*big,1.05*big],'hide',[8,0,0]);this.box(n,[fx,y-1.6*big,z-.7],[.55*big,.9*big,.75*big],'bone',[24,0,0]);}}
 export(){const size=512,pixels=Buffer.alloc(size*size*4),faces=[];for(let i=3;i<pixels.length;i+=4)pixels[i]=255;
  for(const b of this.bones)for(const c of b.cubes){const [w,h,d]=c.size.map(n=>n/this.scale);c.uv={};for(const [face,u,v]of [['north',w,h],['south',w,h],['east',d,h],['west',d,h],['up',w,d],['down',w,d]])faces.push({c,face,w:Math.max(1,Math.ceil(u*2)),h:Math.max(1,Math.ceil(v*2)),mat:c.material,seed:faces.length});}
  faces.sort((a,b)=>b.h-a.h||b.w-a.w);let x=1,y=1,row=0;
  for(const f of faces){if(x+f.w+1>=size){x=1;y+=row+2;row=0;}if(y+f.h+1>=size)throw Error('Atlas overflow '+this.id+' '+y);f.c.uv[f.face]={uv:[x,y],uv_size:[f.w,f.h]};const base=this.colors[f.mat],organic=['flesh','sinew','hide','wound','gold'].includes(f.mat),patch=patches[organic?(f.mat==='hide'?'hide':'flesh'):f.mat==='bone'?'bone':'plate'];
   for(let j=-1;j<=f.h;j++)for(let i=-1;i<=f.w;i++){const u=Math.max(0,Math.min(f.w-1,i)),v=Math.max(0,Math.min(f.h-1,j)),sp=patch.data[((v+f.seed*3)%patch.h)*patch.w+(u+f.seed)%patch.w],lum=(sp[0]+sp[1]+sp[2]-patch.mean.reduce((a,b)=>a+b))/3;
    let shade=lum*(organic?.32:f.mat==='plate'?.55:.45)+(f.face==='up'?8:f.face==='down'?-10:0);if((u===0||v===0)&&f.w>2&&f.h>2)shade-=6;if(f.mat==='plate'&&u===f.w-2)shade+=8;if(f.mat==='cloth'||f.mat==='coat'||f.mat==='shirt')shade+=(v%5===4?-4:0)+(u===1?3:0);
    const at=((y+j)*size+x+i)*4;for(let k=0;k<3;k++)pixels[at+k]=Math.max(0,Math.min(255,Math.round(base[k]+shade)));pixels[at+3]=255;
    if(this.id==='elizabeth_greene'&&organic&&f.w>5&&f.h>5){
     // Deliberate tissue markings: muted purple branching veins and uneven
     // red membranes. Avoid regular wood-like bands across the whole shell.
     const vein=Math.round(f.w*(.3+.12*Math.sin(v*.12+f.seed))),branch=vein+Math.round((v-f.h*.4)*.46),line=Math.abs(u-vein)<1||(v>f.h*.4&&v<f.h*.77&&Math.abs(u-branch)<1);
     const dx=(u-f.w*(.36+(f.seed%3)*.12))/(f.w*.26),dy=(v-f.h*.55)/(f.h*.32),spot=dx*dx+dy*dy;
     if(line&&f.mat!=='sinew'){pixels[at]=49;pixels[at+1]=30;pixels[at+2]=43;}
     else if(spot<1&&f.mat==='flesh'){pixels[at]=Math.min(190,pixels[at]+Math.round((1-spot)*34));pixels[at+1]=Math.max(20,pixels[at+1]-Math.round((1-spot)*8));pixels[at+2]=Math.max(27,pixels[at+2]-Math.round((1-spot)*7));}
    }
   }x+=f.w+2;row=Math.max(row,f.h);
  }
  for(const b of this.bones)for(const c of b.cubes)delete c.material;
  const bounds=this.entry.id==='flyer'?10:Math.max(6,this.entry.width*3);const data={format_version:'1.12.0','minecraft:geometry':[{description:{identifier:'geometry.blacklight.'+this.id,texture_width:size,texture_height:size,visible_bounds_width:bounds,visible_bounds_height:Math.max(5,this.entry.height*2),visible_bounds_offset:[0,this.entry.height/2,0]},bones:this.bones}]};
  fs.writeFileSync(path.join(assets,'geo',this.id+'_mc.geo.json'),JSON.stringify(data,null,2)+'\n');fs.writeFileSync(path.join(assets,'textures/entity',this.id+'_mc.png'),png(pixels,size));
  return{id:this.id,bones:this.bones.length,cuboids:this.bones.reduce((n,b)=>n+b.cubes.length,0),textureSize:size,atlasUsedHeight:y+row+1,features:this.notes,geometrySha256:hash(Buffer.from(JSON.stringify(data)))};
 }
}
function monster(m){const id=m.id,jug=id==='juggernaut',goli=id==='goliath',leader=id==='leader_hunter';
 const pelvis=jug?11.5:leader?13:14,hip=goli?32*.43:pelvis,wide=jug?10.5:goli?10:8.7;
 m.bone('torso','root',[0,pelvis,1]);m.box('torso',[0,pelvis+4,1],[wide*1.6,11,12],'hide');m.box('torso',[0,pelvis+10,-1],[wide*2.1,10,14],jug?'plate':'flesh');m.box('torso',[0,pelvis+12,2],[wide*2.2,5,12],jug?'plate':'hide');
 for(const s of [-1,1]){m.box('torso',[s*wide*.51,pelvis+9,-7],[wide*.95,4,3],jug?'plate':'flesh',[0,s*5,s*8]);for(let i=0;i<4;i++){m.box('torso',[s*wide*.45,pelvis+2+i*1.5,-6.4],[wide*.72,1.7,2.2],i%2?'hide':jug?'plate':'flesh',[0,0,s*8]);}m.box('torso',[s*wide*.82,pelvis+8,5],[4.8,8,3],'plate',[0,0,s*-12]);}
 for(let i=0;i<5;i++)m.box('torso',[0,pelvis+1+i*2.8,6.5],[2.2,2.1,1.8],'plate');
 const hy=jug?pelvis+10:pelvis+9;m.bone('head','torso',[0,hy,-7]);m.box('head',[0,hy,-8.6],[6.5,5.5,7],jug?'plate':'hide');m.box('head',[0,hy+2,-10],[7.3,1.8,4],'plate');m.box('head',[0,hy-.5,-12.2],[5.5,2.5,.7],'pupil');m.bone('jaw','head',[0,hy-2,-9]);m.box('jaw',[0,hy-2.3,-11],[6.2,2.2,4.5],'flesh');for(let i=0;i<7;i++){const x=(i-3)*.69;m.tooth('head',[x,hy-.5,-12.7],[.38,.5+(i%3)*.15,.45]);m.tooth('jaw',[x+.12,hy-1.3,-12.85],[.34,.55,.45]);}
 for(const [side,s]of [['right',-1],['left',1]]){const shoulder=[s*(wide+1.2),pelvis+11,0],elbow=[s*(wide+2.8),pelvis+2,-1],wrist=[s*(wide+3),4.5,-5],big=goli?(s<0?1.9:.85):jug?1.65:1;
  m.bone(side+'_arm','torso',shoulder);m.beam(side+'_arm',shoulder,elbow,5.5*big,6.5*big,jug?'plate':'flesh');m.box(side+'_arm',[shoulder[0],shoulder[1]+1,1],[8*big,5.8,9*big],'plate',[0,0,s*13]);
  m.bone(side+'_forearm',side+'_arm',elbow);m.beam(side+'_forearm',elbow,wrist,5.6*big,6.6*big,'hide');m.plates(side+'_forearm',[wrist[0],wrist[1]+2,wrist[2]-2],[6.7*big,3.6,3.7*big],'plate',3);m.beam(side+'_forearm',[elbow[0],elbow[1],elbow[2]-3.5],[wrist[0],wrist[1]+1,wrist[2]-3.5],.7,.5,'sinew');
  const faceZ=wrist[2]-2-1.85*big-.16;m.beam(side+'_forearm',[wrist[0]-.8,wrist[1]+1,faceZ],[wrist[0]+.6,wrist[1]+7,faceZ],.48,.38,'sinew');
  m.bone(side+'_hand',side+'_forearm',wrist);m.box(side+'_hand',[wrist[0],wrist[1]-.2,wrist[2]-1],[6*big,4.2,6*big],jug?'plate':'flesh');m.fingers(side,wrist[0],wrist[1]-1.2,wrist[2]-3,big);
  if(jug||goli){for(let k=0;k<3;k++)m.box(side+'_forearm',[wrist[0]+s*big*(1+k*.8),wrist[1]+4+k*2,wrist[2]+1],[1.4*big,6.5,2.4*big],'plate',[0,0,s*-18]);}
  const lx=goli?s*(m.entry.width*16*.2/m.scale):s*(jug?5.2:4),knee=[lx,6.5,1],ankle=[lx,2.2,3];m.bone(side+'_leg','root',[lx,hip,0]);m.beam(side+'_leg',[lx,hip,0],knee,jug?6.4:5.3,6.2,'flesh');m.bone(side+'_shin',side+'_leg',knee);m.beam(side+'_shin',knee,ankle,4.3,4.5,'hide');m.box(side+'_shin',[lx,1.6,-.3],[6.2,3.2,8],'plate');for(let t=0;t<3;t++)m.tooth(side+'_shin',[lx+(t-1)*1.4,1.2,-4.4],[1.1,1.4,2.2],[0,0,0]);
  if(!goli)m.box(side+'_leg',[lx,8,-2.7],[6,5.5,2],'plate');
 }
 for(const s of [-1,1])m.beam('torso',[s*wide*.3,pelvis+7,-8.7],[s*wide*.63,pelvis+10,-8.7],.48,.35,'sinew');
 if(leader){for(const s of [-1,1])for(let i=0;i<3;i++)m.box('torso',[s*(7+i*1.8),26+i*.8,3],[3.2,6+i,3.3],'plate',[14,0,s*-20]);m.notes=['Low recessed head, wide thorax and long articulated forearms','Red muscle planes and stepped shoulder carapace in existing Hunter materials'];}
 if(jug){m.box('torso',[0,24,4],[17,7,8],'plate');for(let i=0;i<4;i++)m.box('torso',[0,14+i*2.4,-6.5],[12-i*1.4,2.4,2.5],'plate');m.notes=['Low center of mass, broad plated belly and neckless small head','Paired massive shield forearms, distinct from the lean Hunter'];}
 if(goli){for(let i=0;i<4;i++)m.box('right_forearm',[-15-i*.6,10+i*3.5,-2],[3.5,9.5,5],'plate',[0,0,-18]);for(let i=0;i<2;i++){const x=(i===0?-1:1)*m.entry.width*16*.2/m.scale,z=-m.entry.width*16*.1/m.scale,b=m.bone('weak_'+i,i===0?'right_leg':'left_leg',[x,3/m.scale,z]);m.box(b,[x,19.2/m.scale,z],[24/m.scale,33.6/m.scale,24/m.scale],'plate');m.box(b,[x,19.2/m.scale,z-12.1/m.scale],[9/m.scale,24/m.scale,.7/m.scale],'sinew');}m.notes=['Asymmetric giant plated right arm, exposed red shoulder tissue','Separate leg armor remains inside existing authoritative weakpoint volumes'];}
}
function flyer(m){m.scale=1;m.bone('torso','root',[0,10,0]);m.box('torso',[0,11,1],[10,11,16],'flesh',[18,0,0]);m.box('torso',[0,15,4],[12,5,13],'black',[12,0,0]);m.bone('head','torso',[0,16,-5]);m.box('head',[0,16,-7.5],[6,5,7],'black');m.box('head',[0,16,-12],[3.8,2.1,7],'plate',[10,0,0]);m.box('head',[0,15.2,-16],[2.1,1.5,4],'plate',[22,0,0]);m.bone('jaw','head',[0,14,-7]);m.box('jaw',[0,12.9,-11.5],[3.1,1.5,8],'plate',[-12,0,0]);for(const s of [-1,1]){m.box('head',[s*2.9,17,-9],[.6,.8,1.2],'wound');for(let n=0;n<4;n++)m.box('torso',[s*3.5,16-n,5+n*1.7],[2.5,3.8,5],'black',[20,0,s*22]);const side=s<0?'right':'left',wing=m.bone(side+'_arm','torso',[s*4,14,1]);m.beam(wing,[s*4,14,1],[s*16,14,3],3,3,'flesh');m.beam(wing,[s*16,14,3],[s*30,10,5],2.1,2.5,'plate');for(let i=0;i<5;i++){let x=s*(10+i*4.3),len=21-i*2.5;m.box(wing,[x,11-i*.65,6+len*.26],[4.8,.9,len],'hide',[6,s*(8+i*5),0]);m.box(wing,[x,11.6-i*.65,6+len*.15],[.8,1.5,len*.9],'plate',[6,s*(8+i*5),0]);m.box(wing,[x+s*2,10-i*.65,6+len*.75],[1.7,.8,7],'black',[8,s*(12+i*5),0]);}const leg=m.bone(side+'_leg','torso',[s*3,6,5]);m.beam(leg,[s*3,6,5],[s*4,1,2],2.3,2.5,'plate');for(let t=0;t<3;t++)m.box(leg,[s*4+(t-1)*1.1,-.3,-1],[.8,2.2,4.5],'bone',[20,0,0]);}m.bone('tail','torso',[0,8,8]);for(let i=0;i<4;i++)m.box('tail',[(i-1.5)*1.5,7-i*.5,14+i*2],[1.8,1.5,11],'black',[10,(i-1.5)*12,0]);m.notes=['Split hooked beak, sinewy bird torso, five tapered wing spars per side','Layered membrane/keratin wing surfaces and articulated talons'];}
function greene(m){const H=32; m.bone('torso','root',[0,9,1]);m.box('root',[0,4,3],[20,8,19],'hide');m.box('torso',[0,15,1],[21,21,21],'hide');m.box('torso',[0,24,2],[18,10,18],'flesh');m.box('torso',[0,29,2],[12,5,13],'flesh');m.bone('head','torso',[0,18,-10]);
 for(let layer=0;layer<4;layer++){const radius=11.5-layer*1.65;for(let i=0;i<14;i++){const a=i*Math.PI*2/14,x=Math.cos(a)*radius,y=18+Math.sin(a)*radius;m.box('head',[x,y,-10.5-layer*.5],[radius*.48,2.5,2.5],layer===0?'flesh':layer===1?'hide':'gold',[0,0,a*180/Math.PI+90]);}}
 m.box('head',[0,18,-11],[12,19,1.5],'gold');m.box('head',[0,16.5,-12.2],[4.1,12,.8],'pupil');m.box('head',[0,22,-12.1],[3,4,.9],'gold');
 for(let r=0;r<5;r++)for(const s of [-1,1])m.box('head',[s*(4.1+r*.9),15+r*1.8,-12.2+r*.15],[2.9,1.2,.9],r%2?'sinew':'gold',[0,0,s*35]);
 for(const s of [-1,1]){const n=s<0?'right_arm':'left_arm';m.bone(n,'torso',[s*8,22,3]);for(let k=0;k<4;k++)m.beam(n,[s*(8+k*2),22-k*2,3+k],[s*(10+k*2),20-k*2,4+k],3-k*.3,3-k*.3,'hide');}
 for(let i=0;i<3;i++){const a=i*Math.PI*2/3,x=-Math.sin(a)*m.entry.width*16*.63/m.scale,z=-Math.cos(a)*m.entry.width*16*.63/m.scale,b=m.bone('weak_'+i,'root',[x,3/m.scale,z]);m.beam(b,[Math.sin(a)*-7,5,Math.cos(a)*-7],[x,6,z],3,3,'hide');m.beam(b,[Math.sin(a)*-7,6,Math.cos(a)*-7],[x,7,z],.7,.7,'sinew');m.box(b,[x,19.2/m.scale,z],[24/m.scale,33.6/m.scale,24/m.scale],'flesh');for(let k=0;k<3;k++)m.box(b,[x,(8+k*10)/m.scale,z],[26/m.scale,3/m.scale,26/m.scale],k===1?'sinew':'hide');}
 for(let row=0;row<4;row++)for(let i=0;i<8;i++){const a=i*Math.PI/4;m.box('torso',[Math.sin(a)*10,9+row*5,2+Math.cos(a)*9],[5,5.8,4],'flesh',[8,-a*180/Math.PI,8*(i%2?1:-1)]);}
 m.notes=['Large rooted ocular biomass with layered amber membrane and dark vertical opening','Three connected destructible tendrils; no humanoid head or standing body'];}
function human(m){const id=m.id,female=id==='archer'||id==='galloway',mil=id==='captain_cross'||id==='super_soldier',superman=id==='super_soldier',lab=id==='koenig'||id==='archer',suit=id==='galloway',alex=id==='alex_mercer';
 if(lab)m.colors.coat=[183,182,167];if(suit)m.colors.coat=[36,34,40];if(id==='roland')m.colors.coat=[64,68,63];if(mil){m.colors.coat=[49,56,48];m.colors.cloth=[35,40,37];}if(alex)m.colors.cloth=[40,48,59];if(id==='koenig'){m.colors.hair=[113,109,100];m.colors.skin=[159,129,108];}if(female)m.colors.skin=[184,146,126];if(id==='archer')m.colors.hair=[74,52,31];if(id==='galloway')m.colors.hair=[68,32,27];
 const shoulder=female?4.6:superman?6.7:5.4,bodyW=female?7.4:superman?11:8.7;
 m.bone('torso','root',[0,13.6,0]);m.box('torso',[0,18.4,0],[bodyW,10.2,4.7],'coat');m.box('torso',[0,22,0],[bodyW+.5,3.6,5.1],'coat');m.box('torso',[0,14.2,0],[bodyW-.8,2.7,4.5],'cloth');m.box('torso',[0,20.3,-2.4],[bodyW*.54,6.4,.55],alex?'shirt':lab?'cloth':'shirt');
 for(const s of [-1,1]){m.box('torso',[s*bodyW*.28,22.5,-2.8],[2.2,4.5,.5],lab?'coat':'black',[0,0,s*23]);m.box('torso',[s*bodyW*.37,16.7,-2.6],[1.8,2,.65],'coat');m.box('torso',[s*bodyW*.37,17.5,-3],[1.8,.3,.25],'metal');}
 m.box('torso',[0,14,-2.5],[bodyW-.2,.8,.5],'black');m.box('torso',[0,14,-2.9],[1.4,1.1,.3],'metal');m.box('torso',[0,18.3,-2.9],[.25,7.6,.25],'metal');
 m.bone('head','torso',[0,24.1,0]);m.box('head',[0,27.6,0],[6.5,6.8,6.3],'skin');m.box('head',[0,24.6,-.4],[5.6,1.3,5.5],'skin');m.box('head',[0,30.6,.2],[6.9,1.7,6.8],'hair');
 for(const s of [-1,1]){m.box('head',[s*3.25,28,.4],[.45,3.5,5.8],'hair');m.box('head',[s*1.4,28.1,-3.2],[1.8,.5,.25],'hair');m.box('head',[s*1.4,27.55,-3.21],[1.7,.55,.25],'eye');m.box('head',[s*1.45,27.55,-3.38],[.55,.65,.14],'black');m.box('head',[s*3.35,26.7,0],[.55,1.6,1.2],'skin');}m.box('head',[0,26.8,-3.35],[.8,1.5,.9],'skin');m.box('head',[0,25.5,-3.25],[2.5,.35,.2],'hide');
 if(female){m.box('head',[0,28,3.25],[6.6,6.7,1.3],'hair');m.box('head',[1.8,26.5,4],[1.7,3.5,1.6],'hair');m.box('head',[1.8,23.4,4.4],[1.4,3.5,1.4],'hair',[-12,0,0]);}
 if(id==='koenig'){m.box('head',[0,24.9,-2.7],[4.8,1.5,1.8],'hair');for(const s of [-1,1])m.box('head',[s*2.5,26,-2.5],[.9,2.4,1.7],'hair');m.box('torso',[0,21.3,-3],[1.1,3.8,.35],'black');m.box('torso',[-2.7,20.3,-3],[1.8,2.6,.25],'shirt');m.box('torso',[-2.7,21.2,-3.2],[1.4,.4,.18],'blue');}
 if(alex){m.box('head',[0,31.1,.5],[7.7,1.7,7.6],'black');for(const s of [-1,1])m.box('head',[s*3.65,27.8,.5],[.9,6.2,6.7],'black');m.box('head',[0,28,3.8],[7.2,6.2,.9],'black');m.box('torso',[0,17.2,-2.85],[4.4,1,.3],'red');for(const s of [-1,1])m.box('torso',[s*3.5,16,2.7],[2.2,5.5,.6],'black',[0,0,-s*7]);}
 for(const [side,s]of [['right',-1],['left',1]]){const x=s*shoulder,y=22.4;m.bone(side+'_arm','torso',[x,y,0]);m.box(side+'_arm',[x,19.9,0],[superman?4.7:3,6,3.7],'coat');m.bone(side+'_forearm',side+'_arm',[x,17.2,0]);m.box(side+'_forearm',[x,15,0],[superman?4.3:2.8,5.1,3.4],'coat');m.box(side+'_forearm',[x,13,-.2],[superman?4.8:3.1,1,3.8],mil?'plate':'black');m.bone(side+'_hand',side+'_forearm',[x,12,0]);m.box(side+'_hand',[x,11.7,-.2],[2.8,2,2.8],mil?'black':'skin');for(let i=0;i<4;i++){const fx=x+(i-1.5)*.63,n=m.bone(side+'_finger_'+i,side+'_hand',[fx,11,-.6]);m.box(n,[fx,10.5,-.9],[.52,1.3,1.2],mil?'black':'skin');}
  const lx=s*(female?1.9:2.3);m.bone(side+'_leg','root',[lx,13.6,0]);m.box(side+'_leg',[lx,10,0],[female?3.2:4,7.5,4],'cloth');m.bone(side+'_shin',side+'_leg',[lx,6,0]);m.box(side+'_shin',[lx,3.5,.2],[female?2.9:3.6,6,3.7],'cloth');m.box(side+'_shin',[lx,1,-1],[female?3.3:4,2,6],'black');m.box(side+'_shin',[lx,2,-1.9],[2.7,1,.5],'metal');
  if(lab||suit){m.box(side+'_leg',[lx,11,2.3],[female?3.5:4.3,6.2,.9],'coat');m.box(side+'_leg',[lx+s*1.6,11,0],[.6,5.8,4.8],'coat');}
  if(mil){m.box(side+'_arm',[x,22.1,-.3],[superman?6:4.5,3.8,5],'plate',[0,0,s*8]);m.box(side+'_forearm',[x,15.4,-1.8],[3.3,3.6,1.1],'plate');m.box(side+'_leg',[lx,6.4,-2.1],[3.6,2.4,1.2],'plate');}
 }
 if(mil){m.plates('torso',[0,17,-3.1],[bodyW*.85,2.8,1.2],'plate',3);for(const s of [-1,1]){m.box('torso',[s*bodyW*.37,16.1,-3.8],[2.5,2.7,1.8],'cloth');m.box('torso',[s*bodyW*.31,21,-3.9],[.65,5.5,.5],'black');}m.box('torso',[0,20,3],[bodyW*.7,6.8,2.5],'cloth');m.box('head',[0,28.9,-3.4],[6.9,1.8,1],'black');m.box('head',[0,28.4,-4],[5.1,.8,.3],superman?'blue':'black');if(superman){m.box('head',[0,26.1,-3.6],[4.5,2.5,1.3],'plate');for(const s of [-1,1])m.box('head',[s*2.5,26,-3.4],[1.3,1.8,1.4],'metal');}}
 if(id==='captain_cross'){m.box('right_hand',[-shoulder,7,-2.1],[1.2,10,1.2],'metal',[0,0,-8]);m.box('right_hand',[-shoulder,2,-2.1],[1.6,2.3,1.6],'blue');m.notes=['Segmented tactical vest, straps, pouches, gloves and shock baton'];}
 if(superman)m.notes=['Broad armored silhouette, reinforced shoulder/forearm/knee plates and sealed mask'];
 function power(name,type){const x=-shoulder,y=12;m.bone(name,'right_hand',[x,y,0]);m.box(name,[x,y+.3,-.1],[5.2,4.3,5.2],'black');m.box(name,[x,y+.7,-2.8],[.6,4,.5],'wound');if(type==='blade'){m.box(name,[x+.4,y-5,-1.3],[3.2,11,5.8],'black',[0,0,6]);m.box(name,[x+1.2,y-12,-1.1],[2.1,5.5,4],'plate',[0,0,12]);m.box(name,[x+2,y-15.5,-1],[.8,3,2.3],'bone',[0,0,16]);m.box(name,[x-1.3,y-4,-4.3],[.6,9,.6],'sinew',[0,0,6]);}else if(type==='hammer'){m.box(name,[x,y-1,-.5],[9.5,8,9],'black');m.plates(name,[x,y-3,-5],[7.5,2.4,1.3],'plate',3);for(let i=0;i<3;i++)m.box(name,[x-2.8+i*2.8,y-1,-5.8],[.5,6,.4],'sinew');}else for(let i=0;i<4;i++){const fx=x+(i-1.5)*1.2;m.box(name,[fx,y-4,-3],[.9,7,1.3],'plate',[12,0,(i-1.5)*4]);m.box(name,[fx,y-8.6,-4.5],[.6,3,1],'bone',[24,0,(i-1.5)*6]);}}
 if(female){for(const sign of [-1,1])m.box('head',[sign*2.8,26.4,2.5],[1.5,4.8,1.8],'hair');}
 if(alex){power('claws','claws');power('hammer','hammer');power('blade','blade');m.notes=['Layered black hood/jacket, pale shirt, red lining and separate denim legs','Hand-rooted four-claw, plated hammer and tapered blade variants'];}
 if(id==='roland'||id==='koenig'||id==='archer'){power('evolved_blade','blade');if(id==='koenig')m.notes=['Grey hair and beard, lab-coat skirts, tie and ID badge'];else if(id==='archer')m.notes=['Female scientist silhouette, tied hair and split lab coat with articulated blade arm'];else m.notes=['Evolved operative clothing with separate cuffs, pockets and blade growth'];}
 for(const name of ['blade','evolved_blade']){const b=m.by.get(name);if(b)for(const c of b.cubes.slice(2)){const y=12*m.scale;c.origin[1]=y+(c.origin[1]-y)*.72;c.size[1]*=.72;if(c.pivot)c.pivot[1]=y+(c.pivot[1]-y)*.72;}}
 if(id==='galloway'){power('evolved_hammer','hammer');m.notes=['Female tailored jacket, blouse, skirt panels and tied auburn hair','Heavy arm retained for the current mod move set; not a claim of her canonical weapon'];}
}
function drone(m){m.scale=1;m.bone('torso','root',[0,6,0]);m.box('torso',[0,6,0],[12,5,10],'metal');m.box('torso',[0,8,0],[9,2,8],'plate');m.box('torso',[0,4,0],[9,2,8],'black');for(let i=0;i<5;i++)m.box('torso',[-4+i*2,9.1,0],[.8,.4,5.5],'black');for(let i=0;i<4;i++){const x=i%2?12:-12,z=i<2?10:-10;m.beam('torso',[x*.25,6,z*.25],[x,6,z],1.8,2,'metal');m.box('torso',[x,6.5,z],[4,5,4],'plate');m.bone('rotor_'+i,'torso',[x,9,z]);m.box('rotor_'+i,[x,9,z],[16,.6,1.6],'black');m.box('rotor_'+i,[x,9.2,z],[1.6,.6,16],'black');m.box('rotor_'+i,[x,9.3,z],[2.2,1.2,2.2],'metal');}m.bone('head','torso',[0,3,-4]);m.box('head',[0,2,-5.6],[5,4,5],'plate');m.box('head',[0,2,-8.1],[3,2.8,.6],'black');m.box('head',[0,2,-8.5],[1.6,1.6,.3],'blue');for(const s of [-1,1]){m.beam('torso',[s*4,4,1],[s*7,-1,1],1,1,'metal');m.box('torso',[s*7,-1,0],[1.5,1,12],'black');m.box('torso',[s*5,8,-4.9],[1,.7,.5],s<0?'red':'blue');}m.notes=['Recessed camera gimbal, separate motors/rotors, landing skids, vents and navigation lights'];}
const before=manifest.existingModelBaselines??[];
const surfaceFile=path.join(root,'art/encounters/greene-surface-provenance.json');
const surface=fs.existsSync(surfaceFile)?JSON.parse(fs.readFileSync(surfaceFile)):null;
const protectedMonsters=fs.existsSync(path.join(root,'art/encounters/monster-style-provenance.json'))?JSON.parse(fs.readFileSync(path.join(root,'art/encounters/monster-style-provenance.json'))).models:[];
const audit=[];for(const entry of manifest.entities){
 const authored=protectedMonsters.find(row=>row.id===entry.id);
 if(authored){
  if(hash(fs.readFileSync(path.join(assets,'geo',entry.id+'_mc.geo.json')))!==authored.geometrySha256)throw Error('Authored monster changed: '+entry.id);
  const {parts,...summary}=authored;audit.push(summary);continue;
 }
 if(entry.id==='brawler')continue;
 if(entry.id==='elizabeth_greene'&&surface){
  const file=path.join(assets,'geo/elizabeth_greene_mc.geo.json');
  if(hash(fs.readFileSync(file))!==surface.geometrySha256)throw Error('Greene rig changed; regenerate with tools/author_greene_model.py');
  const geo=JSON.parse(fs.readFileSync(file))['minecraft:geometry'][0];
  audit.push({id:entry.id,bones:geo.bones.length,cuboids:surface.cuboids,textureSize:surface.atlasSize,atlasUsedHeight:surface.atlasUsedHeight,features:['Authored anatomical cuboids and existing Supreme Hunter materials; replaces rejected r3/r4/r5 drafts'],geometrySha256:surface.geometrySha256});
  continue;
 }
 const m=new Model(entry);if(['leader_hunter','juggernaut','goliath'].includes(entry.id))monster(m);else if(entry.id==='flyer')flyer(m);else if(entry.id==='elizabeth_greene')greene(m);else if(entry.id==='recon_uav')drone(m);else human(m);shapeFromExtractedReference(m);audit.push(m.export());
}
for(const item of before)if(hash(fs.readFileSync(path.join(assets,item.source)))!==item.sha256)throw Error('Existing model changed '+item.source);
manifest.modelAudit=audit;manifest.authoredRevision='extracted-reference-r3';manifest.modelStatus='Silhouettes revised against extracted P1/P2 model previews. Prior r2 rejected by user for poor recognizability. Original Hunter/Supreme preserved. Runtime and visual review required.';
if(surface){manifest.greeneSurface=surface;manifest.authoredRevision=surface.revision;manifest.source='Existing project models and authored encounter drafts; Greene r6 follows the existing Supreme Hunter cuboid/material style. Original P1 assets are proportion references only.';manifest.modelStatus='Greene r3/r4/r5 rejected. Authored cuboid r6 requires in-game style comparison; other encounter drafts remain unapproved.';}
manifest.references=[{url:'https://d-conceptart.blogspot.com/2012/07/prototype-2.html',scope:'Dejan Ostojic concept silhouettes, visual reference only; no downloaded textures/meshes shipped'},{url:'https://www.trueachievements.com/n7229/meet-the-important-prototype-2-npcs',scope:'Koenig lab-coat character reference'},{url:'https://prototype.fandom.com/wiki/Sabrina_Galloway',scope:'Correct Sabrina Galloway identity, not the previous invented male name'},{url:'https://prototype.fandom.com/wiki/Gentek',scope:'Karen Archer identity'}];
const sourceAudit=path.join(root,'art/encounters/source-model-audit.json');
if(fs.existsSync(sourceAudit))manifest.extractedReferenceAudit=JSON.parse(fs.readFileSync(sourceAudit));
manifest.materialProvenance=['textures/entity/hunter_mc.png','textures/entity/supreme_hunter_block.png'].map(source=>({source,sha256:hash(fs.readFileSync(path.join(assets,source)))}));
fs.writeFileSync(manifestFile,JSON.stringify(manifest,null,2)+'\n');console.log(JSON.stringify(audit));



