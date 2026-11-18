// Authored cuboid silhouettes after inspecting the extracted P1/P2 bind-pose
// meshes. No original mesh, UV or bitmap data is imported into these assets.
const sides=[['right',-1],['left',1]];
function reset(m){m.bones=[];m.by=new Map();m.bone('root',null,[0,0,0]);}
function bevel(m,b,p,s,c='flesh',r){
 m.box(b,p,[s[0]*.73,s[1],s[2]*.8],c,r);
 m.box(b,p,[s[0],s[1]*.73,s[2]*.78],c,r);
 m.box(b,p,[s[0]*.84,s[1]*.78,s[2]],c,r);
}
function taper(m,b,a,z,width,depth,c='flesh',tip=.45){
 for(let i=0;i<3;i++){const t=i/3,u=(i+1.08)/3,lerp=t=>a.map((v,k)=>v+(z[k]-v)*t);m.beam(b,lerp(t),lerp(u),width*(1-(1-tip)*t),depth*(1-(1-tip)*t),c);}
}
function maw(m,y,z,w=4,h=5){
 m.bone('head','torso',[0,y,z+2]);
 bevel(m,'head',[0,y+.8,z],[w+2,h*.7,5.6],'hide');
 m.box('head',[0,y-.6,z-2.95],[w,h,.65],'pupil');
 for(const s of [-1,1]){
  m.box('head',[s*(w*.5+.1),y-.5,z-2.3],[1.1,h+1.1,1.5],'flesh',[0,0,s*9]);
  m.box('head',[s*w*.29,y+h*.32,z-3.1],[w*.5,1.2,1.2],'hide',[0,0,s*-18]);
  m.box('head',[s*w*.25,y+h*.13,z-3.2],[.55,.35,.25],'bone');
 }
 m.bone('jaw','head',[0,y-1.3,z]);
 bevel(m,'jaw',[0,y-h*.48,z-1],[w+1.8,1.6,3.8],'hide');
 for(let i=0;i<7;i++){
  const x=(i-3)*w/7;
  m.tooth('head',[x,y+h*.23,z-3.3],[.3,i===1||i===5?1.3:.65,.55],[0,0,x*4]);
  m.tooth('jaw',[x,y-h*.31,z-3.3],[.3,i===0||i===6?1.2:.5,.5],[0,0,-x*4]);
 }
}
function arm(m,side,sh,el,wr,upper=5,fore=4,c='flesh'){
 m.bone(side+'_arm','torso',sh);taper(m,side+'_arm',sh,el,upper,upper*1.12,c,.78);
 m.bone(side+'_forearm',side+'_arm',el);taper(m,side+'_forearm',el,wr,fore,fore*1.1,c,.66);
 m.bone(side+'_hand',side+'_forearm',wr);
}
function leg(m,side,hip,knee,ankle,width=4){
 m.bone(side+'_leg','root',hip);taper(m,side+'_leg',hip,knee,width,width*1.15,'flesh',.68);
 m.bone(side+'_shin',side+'_leg',knee);taper(m,side+'_shin',knee,ankle,width*.65,width*.72,'hide',.6);
 bevel(m,side+'_shin',[ankle[0],1.2,ankle[2]-1.5],[width+1.2,2.2,5.8],'hide');
 for(let j=0;j<3;j++)m.tooth(side+'_shin',[ankle[0]+(j-1)*1.15,.8,ankle[2]-4.5],[.7,.7,1.7],[0,0,0]);
}
function leader(m){
 reset(m);m.colors.flesh=[84,78,75];m.colors.hide=[61,56,55];m.colors.plate=[66,62,60];m.colors.bone=[136,126,105];
 m.bone('torso','root',[0,17,8]);
 bevel(m,'torso',[0,20,3],[16,12,21],'flesh',[-12,0,0]);
 bevel(m,'torso',[0,18.5,10],[11,9,11],'hide');
 for(const s of [-1,1]){
  bevel(m,'torso',[s*5.6,23,-3],[9,9,11],'flesh',[0,0,s*17]);
  m.box('torso',[s*4,19,-8.3],[7.4,2.3,1.7],'hide',[0,0,s*12]);
  for(let i=0;i<3;i++)m.box('torso',[s*3.1,17.4-i*1.5,-7+i],[5.2,1.3,1.3],'flesh',[0,0,s*8]);
 }
 maw(m,24.5,-9.5,4.3,4.8);
 for(let i=0;i<4;i++)m.box('head',[(i-1.5)*1.1,27.1,-8.4],[.7,.75,4.6],'plate',[-12,0,0]);
 for(const [side,s]of sides){
  arm(m,side,[s*8.1,24,-2],[s*11.1,13,-5],[s*12.1,3.8,-10],6.2,4.3);
  bevel(m,side+'_arm',[s*9,21.5,-2],[7.6,8.5,7.5],'flesh',[0,0,s*10]);
  m.beam(side+'_forearm',[s*11.2,13,-7.2],[s*12.1,4.6,-12.1],.6,.55,'plate');
  bevel(m,side+'_hand',[s*12.1,2.7,-11.7],[5.3,2.6,5.8],'hide');
  for(let i=0;i<4;i++){const x=s*12.1+(i-1.5)*1.35,n=m.bone(side+'_finger_'+i,side+'_hand',[x,2.7,-12.8]);
   taper(m,n,[x,2.7,-12.8],[x+s*.4,1,-16.4],1,1.1,'hide',.7);m.tooth(n,[x+s*.4,.7,-17.2],[.55,.65,2.1],[12,0,0]);}
  leg(m,side,[s*4.3,17,10],[s*7.5,8,8],[s*8.5,2.3,14],5.6);
 }
 m.notes=['P1 LeaderHunter.p3d.rz: grey eyeless ape-like muzzle, low long torso, forward knuckles and bent rear legs','No invented shoulder armor; stepped muscle volumes and old Hunter pixel materials'];
}
function juggernaut(m){
 reset(m);m.colors.flesh=[89,66,63];m.colors.hide=[54,44,44];m.colors.plate=[72,66,62];m.colors.bone=[129,117,94];
 m.bone('torso','root',[0,13,1]);
 bevel(m,'torso',[0,15,1],[18,15,15],'flesh');
 bevel(m,'torso',[0,11,-2.4],[18.7,10,15.4],'flesh');
 bevel(m,'torso',[0,22,2],[19,10,13],'hide');
 for(const s of [-1,1]){
  bevel(m,'torso',[s*5.2,21.3,-5.4],[9.8,8,6.2],'flesh',[0,0,s*24]);
  for(let i=0;i<4;i++)m.beam('torso',[s*1.8,23-i*1.25,-8.7],[s*7.8,20-i*1.2,-7.5],.55,.45,'bone');
  taper(m,'torso',[s*8,25,6],[s*8.8,14,8],2,2,'hide');
 }
 for(let i=0;i<5;i++)bevel(m,'torso',[0,16+i*2.7,7.7],[9-i*.6,4.5,5.2],'plate',[10+i*2,0,i%2?10:-10]);
 maw(m,27,-5,4.5,5.8);
 for(const [side,s]of sides){
  arm(m,side,[s*10,23,0],[s*13,15,1],[s*14,7,-2.5],6.8,7,'hide');
  bevel(m,side+'_arm',[s*10.4,23,1.6],[8,8,9],'hide',[0,0,s*14]);
  bevel(m,side+'_forearm',[s*14,9,-3],[9.5,10.5,9],'hide',[0,0,s*10]);
  for(let i=0;i<4;i++)bevel(m,side+'_forearm',[s*(12+(i%2)*3),7+Math.floor(i/2)*4,-6.5],[4.5,5,4],'plate',[0,0,s*(i%2?15:-12)]);
  m.box(side+'_forearm',[s*14,15.8,-1],[3.4,6,3.4],'plate',[0,0,s*22]);
  bevel(m,side+'_hand',[s*14,5.8,-3.2],[8.5,6,7.5],'plate');
  for(let i=0;i<4;i++){const x=s*14+(i-1.5)*1.7,n=m.bone(side+'_finger_'+i,side+'_hand',[x,4,-4]);taper(m,n,[x,4,-4],[x,1.2,-6.1],1.3,1.6,'black',.3);}
  leg(m,side,[s*5.7,11,1],[s*6.5,5.5,-.5],[s*6.4,1.9,1],6.1);
 }
 m.notes=['P2 infected3_hammerer: protruding belly, exposed chest, raised narrow head and open vertical maw','Faceted knuckle masses, hooked digits and stacked dorsal growths; no flat armor wall'];
}
function goliath(m){
 reset(m);m.colors.flesh=[114,75,63];m.colors.hide=[70,46,40];m.colors.plate=[49,44,40];m.colors.bone=[141,127,103];
 m.bone('torso','root',[0,14,1]);bevel(m,'torso',[0,18,1],[17,17,13],'flesh');
 bevel(m,'torso',[0,25,1],[23,12,15],'flesh');
 for(const s of [-1,1]){
  bevel(m,'torso',[s*5.7,25,-5],[11.5,7,6.5],'flesh',[0,0,s*12]);
  for(let i=0;i<4;i++)bevel(m,'torso',[s*(3.9-i*.45),20-i*2.3,-6.3+i*.25],[5.9-i*.55,3,3],'hide',[0,0,s*-8]);
  taper(m,'torso',[s*8,27,-5],[s*4,14,-6],.7,.7,'sinew');
 }
 maw(m,29,-4.8,2.7,3.7);
 for(const [side,s]of sides){
  const huge=side==='right',sh=[s*11.3,26,0],el=[s*(huge?17:17.5),19,0],wr=[s*(huge?19:20.5),9,-3];
  arm(m,side,sh,el,wr,huge?9:4.7,huge?11:3.9,'sinew');
  bevel(m,side+'_arm',[s*12.7,25,1],[huge?13:6,10,11],'flesh',[0,0,s*20]);
  if(huge){
   bevel(m,side+'_forearm',[s*19,12,-1.8],[14,14,13],'plate');
   for(let i=0;i<5;i++){const a=i*Math.PI*2/5;bevel(m,side+'_forearm',[s*19+Math.cos(a)*5,12+Math.sin(a)*5,-7],[5,6.5,6],'plate',[10,0,a*180/Math.PI]);}
   bevel(m,side+'_hand',[s*19,6.5,-3],[12,8,11],'black');
   for(let i=0;i<4;i++){let x=s*19+(i-1.5)*2.5,n=m.bone(side+'_finger_'+i,side+'_hand',[x,5,-5]);taper(m,n,[x,5,-5],[x,2,-8],2.6,3.2,'plate',.48);}
  }else{
   bevel(m,side+'_hand',[wr[0],8,-4],[5.7,4,5.7],'hide');
   for(let i=0;i<4;i++){let x=wr[0]+(i-1.5)*1.5,n=m.bone(side+'_finger_'+i,side+'_hand',[x,7,-5]);taper(m,n,[x,7,-5],[x+s*.8,1.7,-10+i*.45],1.2,1.4,'bone',.18);}
  }
  const x=s*m.entry.width*16*.2/m.scale;
  leg(m,side,[x,13.76,0],[x,7.4,1],[x,2.2,1.4],4.6);
 }
 for(let i=0;i<2;i++){
  const x=(i===0?-1:1)*m.entry.width*16*.2/m.scale,z=-m.entry.width*16*.1/m.scale,b=m.bone('weak_'+i,i===0?'right_leg':'left_leg',[x,3/m.scale,z]);
  // First cube deliberately preserves the server's established hittable envelope.
  m.box(b,[x,19.2/m.scale,z],[24/m.scale,33.6/m.scale,24/m.scale],'hide');
  m.box(b,[x,19.2/m.scale,z-12.1/m.scale],[9/m.scale,24/m.scale,.7/m.scale],'sinew');
 }
 m.notes=['P2 behemoth: small high head, oversized pectorals, narrow abdomen and giant lobed right fist','Opposite elongated claw hand; leg weakpoint positions preserved'];
}
function flyer(m){
 reset(m);m.scale=1;m.colors.flesh=[80,49,46];m.colors.hide=[42,35,36];m.colors.bone=[157,146,126];m.colors.plate=[58,55,52];
 m.bone('torso','root',[0,8,1]);bevel(m,'torso',[0,10,1],[8,7,13],'flesh',[-18,0,0]);
 for(const s of [-1,1])m.beam('torso',[s*3.1,12,-2],[s*1.3,7,-5],1.2,1.1,'bone');
 m.bone('head','torso',[0,9,-5]);bevel(m,'head',[0,8,-8.2],[4.4,4.4,7],'bone',[-20,0,0]);
 taper(m,'head',[0,7,-10],[0,2,-14],2.4,2.1,'bone',.23);m.box('head',[0,5.7,-11.3],[1.9,.65,4.2],'black',[-22,0,0]);
 for(const s of [-1,1])m.box('head',[s*1.8,8.8,-10.4],[.55,1,1.4],'pupil',[0,s*18,0]);
 m.bone('jaw','head',[0,6.4,-7]);taper(m,'jaw',[0,5.8,-8],[0,3.2,-12.3],1.5,1.6,'bone',.4);
 for(const [side,s]of sides){
  m.bone(side+'_arm','torso',[s*3,11,0]);taper(m,side+'_arm',[s*3,11,0],[s*14,15,0],3.1,3,'flesh',.65);taper(m,side+'_arm',[s*14,15,0],[s*24,14,4],1.9,2,'bone',.6);
  for(let i=0;i<5;i++){
   const start=[s*(13+i*2.1),14.5-i*.25,1+i*.7],end=[s*(25+i*4.7),11-i*.6,14-i*1.5];
   taper(m,side+'_arm',start,end,1.15,1.25,'black',.15);
   const mid=start.map((v,k)=>(v+end[k])/2);m.box(side+'_arm',[mid[0],mid[1]-.6,mid[2]-1.2],[5.2,.45,8-i*.8],'hide',[0,s*(28+i*7),s*4]);
   if(i<3)m.beam(side+'_arm',start,[s*(20+i*4.5),12,8+i*.7],.65,.7,'sinew');
  }
  m.bone(side+'_leg','torso',[s*2.5,6,4]);taper(m,side+'_leg',[s*2.5,6,4],[s*3.2,1.5,6],1.4,1.7,'hide');
  for(let i=0;i<3;i++)taper(m,side+'_leg',[s*3.2+(i-1)*.7,1.5,6],[s*3.2+(i-1)*.9,.4,2.7],.5,.65,'bone',.18);
 }
 m.bone('tail','torso',[0,8,6]);for(let i=0;i<3;i++)taper(m,'tail',[(i-1),8,6],[(i-1)*2.1,6,16],1,1.4,'black',.12);
 m.notes=['P2 flyer: pale hooked skull, compact horizontal ribcage, long splayed wing fingers and small trailing talons'];
}
function mother(m){
 reset(m);m.colors.flesh=[112,49,61];m.colors.hide=[57,29,40];m.colors.sinew=[150,80,88];m.colors.bone=[113,86,80];m.colors.skin=[178,127,125];
 // Rounded stepped tissue volumes rather than overlapping rectangular panels.
 // Every slice rotates about the same lobe centre, keeping the silhouette joined.
 const tissue=(b,p,s,mat='flesh',roll=0)=>{
  const radii=[.55,.85,1,.89,.62];
  for(let i=0;i<5;i++){
   const c=m.box(b,[p[0]+Math.sin(i*1.1)*s[0]*.025,p[1]+(i-2)*s[1]/5,p[2]],
    [s[0]*radii[i],s[1]/5+.13,s[2]*(.63+.37*radii[i])],mat,[0,0,roll]);
   c.pivot=p.map(v=>v*m.scale);
  }
 };
 m.bone('torso','root',[0,14,2]);
 for(let i=0;i<8;i++){bevel(m,'root',[Math.sin(i*.4)*.6,2+i*1.8,2],[5.5,2.4,5.6],'hide');for(const s of [-1,1])m.box('root',[s*2.9,2+i*1.8,2.3],[1.8,1.2,2.8],'bone',[0,0,s*15]);}
 // Asymmetric three-dimensional tissue shell. No circular iris, painted pupil,
 // gold concentric rings or flat front plate. The pocket is an actual recess.
 tissue('torso',[0,24.3,4.6],[16,18,9.5],'hide');
 const lobes=[[-5.5,29.6,1.3,9.2,8,11,-12],[-6.6,24.2,.2,8.4,8.1,12,-6],[-5.2,19.2,1,7.5,6.9,9.5,15],[5.5,27.6,2.2,8.6,8.7,11.5,15],[6.3,22.4,1.8,8,8,12,8],[4.2,17.9,2,7,6,9,-14],[0,32.4,3.2,9.6,4.4,9.5,-8]];
 for(const [x,y,z,w,h,d,roll]of lobes)tissue('torso',[x,y,z],[w,h,d],'flesh',roll);
 // Short curved folds vary in size and placement; avoid a symmetrical rib ladder.
 for(const [x,y,z,w,h,roll]of [[-5.1,30,-4.3,4.4,4.3,-20],[-6.7,23,-4.9,3.6,5.1,12],[-4.7,18.7,-3.8,3.5,3.2,-24],[5.2,27.7,-3.4,3.8,4.7,24],[6.5,21.1,-4,3.4,4.4,-14],[2.9,32.2,-1.8,4,2.8,17]])
  tissue('torso',[x,y,z],[w,h,2.8],'sinew',roll);
 for(const [x,y,z,w,h]of [[-4.2,29.2,8.8,6.2,6.1],[4.3,25.8,9.1,5.9,7.2],[-4.3,20,8,5,5.8]])
  tissue('torso',[x,y,z],[w,h,2.8],'flesh',x*2);
 m.bone('head','torso',[.6,25,-6]);
 m.box('head',[.6,28,-7.3],[1.75,2.2,1.8],'skin');
 m.box('head',[.6,28.8,-7],[1.9,1.2,1.8],'hide');
 bevel(m,'head',[.6,25.5,-7.1],[2.4,3.1,1.8],'skin');
 for(const s of [-1,1]){
  taper(m,'head',[.6+s*.75,24.4,-7.2],[.6+s*.85,21.3,-6.8],.8,.9,'skin',.6);
  taper(m,'head',[.6+s*1.3,26.5,-7.1],[.6+s*2.7,27.6,-6.8],.65,.8,'skin',.65);
 }
 for(const [side,s]of sides){m.bone(side+'_arm','torso',[s*7,27,2]);for(let j=0;j<(s<0?3:2);j++){const a=[s*7,27-j*3,3],b=[s*(13+j),29-j*3,-1],c=[s*(17+j),26-j*3,-4];taper(m,side+'_arm',a,b,1.7,1.7,'hide');taper(m,side+'_arm',b,c,1.1,1.1,'hide',.16);}}
 for(let i=0;i<3;i++){
  const a=i*Math.PI*2/3,x=-Math.sin(a)*m.entry.width*16*.63/m.scale,z=-Math.cos(a)*m.entry.width*16*.63/m.scale,b=m.bone('weak_'+i,'root',[x,3/m.scale,z]);
  taper(m,b,[0,5,1],[x,6,z],4.5,4.5,'hide',.7);
  // Slug-like weak tendrils taper toward a split crown inside the same hit region.
  for(let k=0;k<4;k++)bevel(m,b,[x+(k%2?.25:-.25),(6+k*7)/m.scale,z],[ (24-k*2)/m.scale,10/m.scale,(24-k*2)/m.scale],k%2?'hide':'flesh');
  for(const s of [-1,1])m.box(b,[x+s*5/m.scale,30/m.scale,z],[6/m.scale,8/m.scale,11/m.scale],'hide',[0,0,s*14]);
 }
 m.notes=['P1 mother_core and mother_segment_1: asymmetric deep tissue shell, folded ridges and small visible host in a recessed pocket','Eyeball design removed: no iris, pupil or concentric gold rings; three weakpoint roots retained'];
}
function drone(m){
 reset(m);m.scale=1;m.colors.metal=[81,86,81];m.colors.plate=[105,109,102];
 m.bone('torso','root',[0,3,0]);bevel(m,'torso',[0,6,0],[5,16,5],'metal');m.box('torso',[0,3,0],[7,4,7],'plate');
 for(let i=0;i<3;i++){
  const a=i*Math.PI*2/3,x=Math.sin(a)*10,z=-Math.cos(a)*10;
  m.beam('torso',[0,10,0],[x,5,z],1,1.4,'metal');m.beam('torso',[0,3,0],[x,3,z],2,2,'metal');
  for(let j=0;j<12;j++){const t=j*Math.PI/6;m.box('torso',[x+Math.sin(t)*6.2,4,z+Math.cos(t)*6.2],[3.5,3.3,1.2],'plate',[0,t*180/Math.PI,0]);}
  m.bone('rotor_'+i,'torso',[x,4,z]);for(let j=0;j<3;j++)m.box('rotor_'+i,[x,4,z],[10,.45,.8],'black',[0,j*120,0]);m.box('rotor_'+i,[x,4.4,z],[2,1,2],'metal');
  m.beam('torso',[x,2,z],[x*1.15,-2,z*1.15],.7,.7,'metal');m.box('torso',[x*1.15,-2,z*1.15],[3,.6,2],'black');
 }
 m.bone('head','torso',[0,-1,0]);bevel(m,'head',[0,-2.4,-1],[5,5,6],'metal');m.box('head',[0,-2.4,-4.3],[3.5,2.7,1],'black');m.box('head',[0,-2.4,-4.9],[2,1.5,.3],'red');
 m.box('torso',[0,14,0],[2.8,1.8,2.8],'plate');m.notes=['P2 uav001: three ducted fans around a vertical center post, triangular struts and underslung sensor','Three independently animated rotor groups, replacing the invented quadcopter'];
}
function humanDetails(m){
 const id=m.id;
 if(id==='captain_cross'){
  m.colors.coat=[38,40,43];m.colors.cloth=[41,43,46];m.colors.hair=[64,61,53];m.colors.plate=[43,45,47];
  // Remove the invented visor and oversized shoulder armor; retain the existing face.
  const head=m.by.get('head');head.cubes=head.cubes.filter(c=>!(c.origin[2]+c.size[2]<-3.35*m.scale));
  for(const s of [-1,1]){m.box('torso',[s*2.9,24.1,0],[1.2,3.2,4.4],'coat',[0,0,s*15]);for(let i=0;i<3;i++)m.box('torso',[s*(1.3+i*1.3),14.6,-3.5],[1.1,2.4,1.4],'black');}
  taper(m,'torso',[-3.5,23,-3.2],[2.2,16,-3.2],.4,.3,'metal');
  bevel(m,'right_forearm',[-5.4,16.2,-2],[4.2,7,3.1],'black');for(let i=0;i<2;i++)m.box('right_forearm',[-6.2+i*1.6,19.7,-2],[.8,2,.8],'metal');
  m.notes=['P1 specialist: uncovered swept hair, high dark collar, diagonal zipper, belt pouches and right-arm launcher'];
 }
 if(id==='super_soldier'){
  m.colors.plate=[53,57,56];m.colors.coat=[77,81,75];m.colors.cloth=[66,69,60];m.colors.blue=[26,28,28];
  for(const s of [-1,1]){m.box('torso',[s*3.5,23,-2.8],[3.7,2.4,.8],'shirt');m.box('torso',[s*4,20,-3.6],[.75,7.4,.8],'black');m.box('head',[s*2.5,26,-4],[1.6,1.7,1.9],'black');}
  m.box('head',[0,28.3,-4.3],[4.6,3,.7],'black');m.box('head',[0,25.4,-4.7],[1.6,1.9,1.3],'metal');
  for(let i=0;i<3;i++)m.box('head',[(i-1)*1.1,28.2,-4.75],[.55,1.6,.2],'metal');
  m.notes=['P1 SuperSoldier: sealed cage-like respirator, pale shoulder panels, reinforced webbing and broad torso'];
 }
 if(id==='koenig'||id==='archer'){
  const female=id==='archer',shoulder=female?4.6:5.4;
  m.colors.hair=female?[28,22,20]:[117,110,103];
  if(female){const b=m.by.get('head');b.cubes=b.cubes.filter(c=>!(c.material==='hair'&&c.origin[1]<26*m.scale&&c.origin[2]>1*m.scale));m.box('head',[-1.2,29.8,-3.15],[4.9,1.8,.9],'hair',[0,0,-14]);m.box('torso',[0,22.3,-2.8],[2.2,2.8,.6],'blue');}
  else{m.box('head',[0,30.6,-.3],[3.7,1.8,5.2],'skin');m.box('head',[0,24.9,-3.1],[4.1,1.1,.8],'hair');}
  for(const side of ['right','left']){
   const s=side==='right'?-1:1,x=s*shoulder;
   if(side==='right'){const b=m.by.get('evolved_blade');if(b)b.cubes=[];}
   for(const bname of [side+'_arm',side+'_forearm'])for(const c of m.by.get(bname).cubes)c.material='black';
   bevel(m,side+'_arm',[x,22.4,0],[5.3,4.1,5.2],female?'black':'shirt');
   for(let j=0;j<3;j++)m.box(side+'_arm',[x+s*.7,21-j*1.3,2],[1.4,2.5,2.8],'black',[12,0,s*25]);
   const b=side==='right'?'evolved_blade':'left_evolved_blade';if(!m.by.has(b))m.bone(b,side+'_hand',[x,12,0]);
   // Long thin cleaver silhouette with stepped cutting edge and a hooked tip.
   m.box(b,[x,8,-1.7],[1.5,8.4,6.8],'black',[0,0,s*-7]);m.box(b,[x+s*.7,4,-1],[1.3,4.8,9.5],'black',[0,0,s*-13]);
   m.box(b,[x+s*1.1,1.8,-3.4],[.65,2.3,5.8],'plate',[0,0,s*-25]);
   m.box(side+'_leg',[s*1.9,11, -2.1],[3.4,female?7.1:5.5,.9],'shirt');
  }
  m.box('torso',[-2.1,20.5,-3.3],[1.4,2.5,.35],'shirt');m.box('torso',[-2.1,21.2,-3.55],[1.2,.6,.15],'blue');
  m.notes=[female?'P2 evolved_e3 / Archer: short dark hair, long white coat, blue collar/ID and paired cleavers':'P2 evolved_m1 / Koenig: balding grey beard, short lab coat, ID and paired cleavers'];
 }
}
export function shapeFromExtractedReference(m){
 const fn={leader_hunter:leader,juggernaut,goliath,flyer,elizabeth_greene:mother,recon_uav:drone}[m.id];
 if(fn)fn(m);else humanDetails(m);
}
