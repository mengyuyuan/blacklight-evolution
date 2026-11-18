// Project-authored diesel/rolling synthesis and edits of the existing firearm cues.
// No extracted commercial-game audio. Run with node and an explicit D: FFmpeg path.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const ffmpeg=process.argv[2];
if(!/^d:/i.test(root)||!ffmpeg||!/^d:/i.test(ffmpeg))throw Error('Repository and FFmpeg must be on D:');
const assets=path.join(root,'src/main/resources/assets/blacklight');
const dest=path.join(assets,'sounds/vehicles');fs.mkdirSync(dest,{recursive:true});
const rate=44100,seconds=4,n=rate*seconds,TAU=Math.PI*2;
let seed=771127;
function random(){seed^=seed<<13;seed^=seed>>>17;seed^=seed<<5;return (seed>>>0)/2147483648-1;}
function noise(cutoff){
  const raw=Float32Array.from({length:n},random),out=new Float32Array(n),a=1-Math.exp(-TAU*cutoff/rate);let s=0;
  for(let pass=0;pass<2;pass++)for(let i=0;i<n;i++){s+=a*(raw[i]-s);out[i]=s;}
  return out;
}
const low=noise(210),mid=noise(1200),high=noise(6200),reports=[];
function run(args,input){const p=spawnSync(ffmpeg,['-hide_banner','-loglevel','error','-nostdin',...args],{input,maxBuffer:24*1024*1024});if(p.status!==0)throw Error(String(p.stderr));return p.stdout;}
function write(name,samples,loop,source){
  let mean=0;for(const s of samples)mean+=s;mean/=samples.length;
  let peak=0;for(let i=0;i<samples.length;i++){samples[i]-=mean;peak=Math.max(peak,Math.abs(samples[i]));}
  const pcm=Buffer.alloc(samples.length*4);let energy=0;
  for(let i=0;i<samples.length;i++){let s=samples[i]/Math.max(peak,.001)*(loop?.72:.9);if(!loop)s*=Math.min(1,i/100,(samples.length-1-i)/700);energy+=s*s;pcm.writeFloatLE(s,i*4);}
  const file=path.join(dest,name+'.ogg');
  run(['-y','-f','f32le','-ar',String(rate),'-ac','1','-i','pipe:0','-c:a','libvorbis','-q:a','5',file],pcm);
  const decoded=run(['-i',file,'-f','f32le','-ar',String(rate),'-ac','1','pipe:1']);let decodedPeak=0;
  for(let i=0;i<decoded.length;i+=4)decodedPeak=Math.max(decodedPeak,Math.abs(decoded.readFloatLE(i)));
  if(decodedPeak>=1)throw Error('Clipped decoded cue: '+name);
  reports.push({name,loop,seconds:samples.length/rate,channels:1,decodedPeak,rms:Math.sqrt(energy/samples.length),source,sha256:createHash('sha256').update(fs.readFileSync(file)).digest('hex')});
}
for(const [chassis,hz,weight] of [['truck',42,1],['apc',38,1.15],['tank',34,1.4]]){
  const samples=new Float32Array(n);
  for(let i=0;i<n;i++){
    const t=i/rate,phase=TAU*hz*t+.04*Math.sin(TAU*3*t),pulse=Math.exp((Math.sin(phase)-1)*6);
    const rumble=.30*Math.sin(phase)+.13*Math.sin(phase*2+.5)+.07*Math.sin(phase*3+.9);
    const rattle=(mid[i]-.4*low[i])*(.20+.85*pulse),exhaust=low[i]*(.8+.6*pulse);
    samples[i]=rumble*weight+exhaust+rattle+.02*Math.sin(TAU*hz*7*t)*(.5+.5*pulse);
  }
  write(chassis+'_engine',samples,true,'Project-authored deterministic diesel pulses, exhaust and mechanical noise');
}
for(const kind of ['wheels','tracks']){
  const samples=new Float32Array(n);
  for(let i=0;i<n;i++){
    const t=i/rate,phase=(t*12)%1,clack=Math.exp(-phase*19),cycle=Math.sin(TAU*2*t);
    samples[i]=kind==='tracks'?.33*low[i]+.3*mid[i]+clack*(.13*Math.sin(TAU*720*t)+.08*Math.sin(TAU*1440*t)+.23*high[i])
      :low[i]*.8+mid[i]*(.6+.08*cycle)+high[i]*.08;
  }
  write(kind,samples,true,'Project-authored deterministic tire roll / metallic track links');
}
function source(file,length,filter){
  const bytes=run(['-i',path.join(assets,'sounds/firearms',file+'.ogg'),'-af',filter,'-t',String(length),'-ar',String(rate),'-ac','1','-f','f32le','pipe:1']);
  const a=new Float32Array(Math.round(length*rate));for(let i=0;i<Math.min(a.length,bytes.length/4);i++)a[i]=bytes.readFloatLE(i*4);return a;
}
for(let variant=1;variant<=2;variant++){
  const cannon=source('cannon_'+variant,.65,'highpass=f=50,lowpass=f=2200');
  const rifle=source('rifle_'+variant,.65,'highpass=f=150,lowpass=f=6000');
  for(let i=0;i<cannon.length;i++)cannon[i]=(cannon[i]*.6+rifle[i]*.7)*Math.exp(-i/rate*3.5);
  write('autocannon_'+variant,cannon,false,'Remix of existing project firearms/cannon_'+variant+' and rifle_'+variant);
  write('coaxial_'+variant,source('rifle_'+variant,.32,'asetrate=40572,aresample=44100,highpass=f=110,lowpass=f=5200'),false,'Edited existing project firearms/rifle_'+variant);
}
const rocket=new Float32Array(Math.round(rate*.95));
for(let i=0;i<rocket.length;i++){const t=i/rate;rocket[i]=(mid[i]*.55+low[i]*.9+high[i]*.1)*Math.exp(-t*4)*(1-Math.exp(-t*180))+.12*Math.sin(TAU*(100*t-30*t*t))*Math.exp(-t*9);}
write('rocket',rocket,false,'Project-authored launch pressure burst and exhaust');
const soundsFile=path.join(assets,'sounds.json'),sounds=JSON.parse(fs.readFileSync(soundsFile,'utf8'));
const subtitles={truck_engine:['卡车发动机运转','Truck engine runs'],apc_engine:['装甲车发动机运转','APC engine runs'],tank_engine:['坦克发动机运转','Tank engine runs'],wheels:['轮胎滚动','Tires roll'],tracks:['履带转动','Tracks clatter'],autocannon:['机炮开火','Autocannon fires'],coaxial:['并列机枪开火','Coaxial machine gun fires'],cannon:['坦克主炮开火','Tank cannon fires'],rocket:['火箭发射','Rocket launches']};
for(const cue of Object.keys(subtitles)){
  const names=cue==='cannon'?['firearms/cannon_1','firearms/cannon_2']:cue==='autocannon'||cue==='coaxial'?['vehicles/'+cue+'_1','vehicles/'+cue+'_2']:['vehicles/'+cue];
  sounds['vehicle.'+cue]={subtitle:'subtitles.blacklight.vehicle.'+cue,sounds:names.map(name=>({name:'blacklight:'+name,attenuation_distance:cue==='cannon'?64:cue==='autocannon'?48:32,preload:true}))};
}
sounds['firearm.launcher'].sounds=[{name:'blacklight:vehicles/rocket',volume:.85,attenuation_distance:32}];
fs.writeFileSync(soundsFile,JSON.stringify(sounds,null,2)+'\n');
for(const [lang,index] of [['zh_cn',0],['en_us',1]]){
  const file=path.join(assets,'lang',lang+'.json'),data=JSON.parse(fs.readFileSync(file,'utf8'));
  for(const [cue,text] of Object.entries(subtitles))data['subtitles.blacklight.vehicle.'+cue]=text[index];
  fs.writeFileSync(file,JSON.stringify(data,null,2)+'\n');
}
const manifest=path.join(root,'art/vehicle-audio');fs.mkdirSync(manifest,{recursive:true});
fs.writeFileSync(path.join(manifest,'manifest.json'),JSON.stringify({generator:'tools/build-vehicle-audio.mjs',rate,cues:reports},null,2)+'\n');
console.log(JSON.stringify(reports.map(({name,seconds,decodedPeak})=>({name,seconds,decodedPeak})),null,2));
