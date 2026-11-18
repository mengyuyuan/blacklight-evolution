import fs from 'node:fs';
const m=JSON.parse(fs.readFileSync('D:/blacklight-evolution/src/main/resources/assets/blacklight/models/hydra_motion.json'));
function point(clip,t,bone,p){
 const frames=m.clips[clip],f=Math.min(frames.length-1,t*1.5),lo=Math.floor(f),hi=Math.min(frames.length-1,lo+1);
 for(let i=m.bones.indexOf(bone);i>=0;i=m.parents[i]){const v=frames[lo][i].map((x,k)=>x+(frames[hi][i][k]-x)*(f-lo)),q=m.pivots[i];let [x,y,z]=p.map((x,k)=>x-q[k]);
  let c=Math.cos(v[0]),s=Math.sin(v[0]);[y,z]=[y*c-z*s,y*s+z*c];c=Math.cos(v[1]);s=Math.sin(v[1]);[x,z]=[x*c+z*s,-x*s+z*c];c=Math.cos(v[2]);s=Math.sin(v[2]);[x,y]=[x*c-y*s,x*s+y*c];p=[x+q[0]-v[3],y+q[1]+v[4],z+q[2]+v[5]];
 }return [-p[0]*2,p[1]*2,-p[2]*2].map(v=>+v.toFixed(3));
}
for(const [clip,times] of [['combat_slam',[0,40,44,48,50,52,54,56,60,64,70]],['combat_sweep',[0,20,24,26,28,30,32,34]],['combat_sidesweep',[0,38,40,44,48,52,56,60]]])for(const t of times)console.log(JSON.stringify({clip,t,head:point(clip,t,'head',[0,93/16,-28/16]),claw:point(clip,t,'claw_left',[-25/16,110/16,-44/16]),body:point(clip,t,'neck_2',[0,43/16,1/16])}));
