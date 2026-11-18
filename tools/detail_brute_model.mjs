// Authored Brute anatomy. Reuses the existing atlas without resampling any pixels.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const file=root+'/src/main/resources/assets/blacklight/geo/blacklight_brute_mc.geo.json';
const doc=JSON.parse(fs.readFileSync(file,'utf8')),geo=doc['minecraft:geometry'][0];
const rig=[['root',null,[0,0,0]],['torso','root',[0,26,0]],['head','torso',[0,31,-3]],
 ['right_arm','torso',[-10,29,0]],['right_forearm','right_arm',[-12.5,17,0]],
 ['left_arm','torso',[10,29,0]],['left_forearm','left_arm',[12.5,17,0]],
 ['right_leg','root',[-3,23,3]],['right_shin','right_leg',[-3.65,12.2,.4]],
 ['left_leg','root',[3,23,3]],['left_shin','left_leg',[3.65,12.2,.4]]];
const bones=rig.map(([name,parent,pivot])=>({name,...parent&&{parent},pivot,cubes:[]}));
const by=Object.fromEntries(bones.map(b=>[b.name,b]));
const materials={flesh:[0,21,16,13],muscle:[33,46,10,10],fold:[0,0,16,16],scar:[37,57,10,7],bone:[84,74,11,4]};
const parts=[];
const round=x=>Math.round(x*10000)/10000;
function box(name,bone,center,size,material='flesh'){
 const [u,v,w,h]=materials[material],uv={};
 for(const [face,a,b] of [['north',size[0],size[1]],['south',size[0],size[1]],['east',size[2],size[1]],['west',size[2],size[1]],['up',size[0],size[2]],['down',size[0],size[2]]])
  uv[face]={uv:[u,v],uv_size:[Math.min(w,Math.max(1,Math.ceil(a))),Math.min(h,Math.max(1,Math.ceil(b)))]};
 const cube={origin:center.map((c,i)=>round(c-size[i]/2)),size,uv};
 parts.push({name,bone,cube:by[bone].cubes.length,material});by[bone].cubes.push(cube);
}
// A broad shoulder girdle with separated pectorals and a narrower abdomen.
box('ribcage','torso',[0,27.5,0],[13.8,12,8.4]);
box('trapezius','torso',[0,32.5,1.7],[15.8,5,7.8]);
box('lower_abdomen','torso',[0,22.1,-.15],[11.6,3.8,7.8],'fold');
box('sternal_seam','torso',[0,28.4,-4.5],[.8,8.6,1.3],'fold');
for(const [side,s] of [['right',-1],['left',1]]){
 box(side+'_pectoral','torso',[s*4.1,30,-4.35],[7.2,5.3,3.1],'muscle');
 box(side+'_lower_pectoral','torso',[s*3.6,27.4,-4.55],[6,1.8,2.25],'flesh');
 box(side+'_clavicle','torso',[s*4.8,33.2,-3.25],[7.1,1.6,3.1],'flesh');
 box(side+'_latissimus','torso',[s*6.7,26.5,1.2],[2.8,7.7,6.5],'flesh');
 box(side+'_scapula','torso',[s*5.1,31.4,4.6],[6.6,5.6,2.8],'fold');
 box(side+'_scapular_ridge','torso',[s*5.2,33.8,4.7],[5.2,1.2,2.3],'flesh');
 for(let i=0;i<3;i++)box(side+'_abdominal_'+i,'torso',[s*(2.6-i*.2),25.9-i*1.75,-4.45+i*.15],[4.7-i*.35,1.55,2],'muscle');
}
for(let i=0;i<5;i++){
 box('spine_root_'+i,'torso',[0,23.7+i*2.6,4.4],[2.2,2,2.1],'fold');
 box('spinal_nodule_'+i,'torso',[(i%2-.5)*.4,24.2+i*2.6,5.35],[1.2,1.2,1.1],'bone');
}
// Recessed eyes and maw. Brow, cheek and mandible are actual distinct volumes.
box('cranial_vault','head',[0,31,-6.9],[5.7,5.8,5.6],'fold');
box('crown','head',[0,33.5,-6.4],[4.3,1.5,4.8],'flesh');
box('maw_recess','head',[0,29.45,-9.55],[4.6,2.6,1.3],'fold');
box('mandible','head',[0,27.8,-8.2],[4.9,1.6,4.2],'flesh');
box('chin','head',[0,27.25,-9],[3.2,.7,2.2],'fold');
box('nasal_bridge','head',[0,31.2,-9.75],[1.1,2.1,1.2],'flesh');
for(const s of [-1,1]){
 box('brow_'+s,'head',[s*1.8,32.3,-9.3],[3.1,1.35,2.4],'flesh');
 box('orbital_recess_'+s,'head',[s*1.7,31.35,-9.85],[1.8,.7,.4],'fold');
 box('eye_'+s,'head',[s*1.7,31.35,-10.08],[.65,.3,.16],'muscle');
 box('cheek_'+s,'head',[s*2.55,30,-8.7],[1.35,2.7,2.2],'flesh');
 box('jaw_hinge_'+s,'head',[s*2.2,28.65,-6.95],[1.35,2.35,2.1],'muscle');
 box('nostril_'+s,'head',[s*.4,30.55,-10.39],[.32,.3,.14],'fold');
}
for(let i=0;i<6;i++){
 box('upper_tooth_'+i,'head',[(i-2.5)*.63,30.1-(i===0||i===5?.18:0),-10.05],[.39,i===0||i===5?1.05:.55,.5],'bone');
 box('lower_tooth_'+i,'head',[(i-2.5)*.6,28.75,-9.95],[.32,.5,.45],'bone');
}
for(const [side,s] of [['right',-1],['left',1]]){
 const arm=side+'_arm',fore=side+'_forearm',leg=side+'_leg',shin=side+'_shin';
 box(side+'_humerus',arm,[s*12,23.5,.2],[6.7,13,7.2],'fold');
 box(side+'_deltoid',arm,[s*12.5,28.1,0],[8.5,5.5,9.4],'flesh');
 box(side+'_deltoid_cap',arm,[s*12.5,30.5,.15],[6.6,1.35,7.8],'flesh');
 box(side+'_outer_deltoid',arm,[s*16.1,27.8,.1],[1.5,4.1,7.5],'fold');
 box(side+'_biceps',arm,[s*12,23,-3.35],[5.4,8.6,2.2],'muscle');
 box(side+'_biceps_tendon',arm,[s*12,18.8,-3],[3,3.4,1.8],'flesh');
 box(side+'_triceps',arm,[s*12.4,22.3,3.2],[5.2,9.6,2.2],'flesh');
 box(side+'_elbow_cap',arm,[s*12.5,18.2,2.65],[4.6,2.2,2.2],'fold');
 box(side+'_shoulder_growth',arm,[s*15.2,30.65,1.3],[2.2,1.4,3.6],'bone');
 // Tapered wrist and palm, four closed digits and a separately curled thumb.
 box(side+'_forearm_core',fore,[s*12.5,12.2,.1],[7.6,9.6,7.8],'flesh');
 box(side+'_palm',fore,[s*12.5,8.1,.25],[9,7.2,8.5],'fold');
 box(side+'_ulna',fore,[s*16.2,13.1,.5],[1.7,7.6,5.8],'flesh');
 for(let i=0;i<3;i++)box(side+'_extensor_'+i,fore,[s*12.5+(i-1)*2.2,13.2,-3.65],[1.5,7.3,1.25],'muscle');
 for(let i=0;i<4;i++){
  const x=s*12.5+(i-1.5)*2.45;
  box(side+'_knuckle_'+i,fore,[x,6.0,-4.65],[2.1,4,2.7],'flesh');
  box(side+'_knuckle_plate_'+i,fore,[x,6.4+(i%2)*.35,-5.75],[1.65,2.4, .7],'bone');
  box(side+'_curled_digit_'+i,fore,[x,4.72,-1.65],[1.95,1.3,4.1],'flesh');
 }
 box(side+'_thumb_base',fore,[s*8.6,10.2,-.3],[2.8,4.6,4.2],'muscle');
 box(side+'_thumb_curl',fore,[s*8.1,8.2,-2.5],[2.2,3.2,2.7],'flesh');
 // Load-bearing haunches, kneecaps, shin tendons and three separated toes.
 box(side+'_thigh_core',leg,[s*3.65,18,1.8],[5.5,11.8,5.8],'fold');
 box(side+'_quadriceps',leg,[s*3.65,18.3,-.5],[5.4,9.5,2.8],'flesh');
 box(side+'_outer_haunch',leg,[s*5.6,20.4,2.4],[2,6.7,4.3],'flesh');
 box(side+'_knee',leg,[s*3.65,12.2,.4],[5,3.9,5.1],'fold');
 box(side+'_kneecap',leg,[s*3.65,12.3,-2],[3.5,2.5,1.2],'bone');
 box(side+'_shin',shin,[s*3.65,7.3,1.4],[3.8,7.6,4.1],'flesh');
 box(side+'_shin_tendon',shin,[s*3.65,7.6,-.7],[1.1,6.4,.65],'muscle');
 box(side+'_calf',shin,[s*3.65,9,3.4],[4.6,5.4,2.3],'fold');
 box(side+'_heel',shin,[s*3.65,2.25,2.6],[4.8,4.5,3.8],'fold');
 box(side+'_instep',shin,[s*3.65,1.85,-.15],[5.3,3.5,4.2],'flesh');
 for(let i=0;i<3;i++){
  const x=s*3.65+(i-1)*1.75;
  box(side+'_toe_'+i,shin,[x,.85,-2],[1.5,1.7,2.1],'fold');
  box(side+'_toenail_'+i,shin,[x,.65,-2.95],[1.15,.85,.7],'bone');
 }
}
// Same-facing coplanar rectangles cause flickering stripes in the native renderer.
for(const b of bones)for(let i=0;i<b.cubes.length;i++)for(let j=i+1;j<b.cubes.length;j++)for(let axis=0;axis<3;axis++)for(const side of [0,1]){
 const x=b.cubes[i],y=b.cubes[j];
 if(Math.abs(x.origin[axis]+side*x.size[axis]-y.origin[axis]-side*y.size[axis])<.0001&&[0,1,2].filter(k=>k!==axis).every(k=>Math.min(x.origin[k]+x.size[k],y.origin[k]+y.size[k])-Math.max(x.origin[k],y.origin[k])>.01))
  throw Error('Coplanar overlapping surfaces: '+[i,j].map(c=>parts.find(p=>p.bone===b.name&&p.cube===c).name).join(', '));
}
geo.bones=bones;
fs.writeFileSync(file,JSON.stringify(doc,null,1)+'\n');
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const report={revision:'authored-brute-anatomy-knees-2',geometry:hash(file),texture:hash(root+'/src/main/resources/assets/blacklight/textures/entity/blacklight_brute_mc.png'),bones:bones.length,cuboids:parts.length,contactCuboids:parts.filter(p=>!p.bone.endsWith('_leg')&&!p.bone.endsWith('_shin')).length,
 features:['Separated pectorals, abdominal bands, scapular ridges and dorsal nodules','Recessed eyes, separate teeth, cheeks and jaw volumes','Tapered forearms, individual knuckles, curled fingers and thumb','Haunches, kneecaps, calves, insteps and separated toes'],
 boundary:'Authored Minecraft anatomy; not an extracted original-game species. Existing atlas pixels reused unchanged. Elbow rig retained, independent knee channels added; no independent finger channels.',parts};
fs.writeFileSync(root+'/art/mutant-combat/brute-model.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({cuboids:report.cuboids,contactCuboids:report.contactCuboids,bones:report.bones}));
