import fs from 'node:fs';
const platform=process.argv[2]||'neo';
if(!['neo','forge'].includes(platform))throw Error('Known QA platform required');
const root=`D:/CodexCache/creature-combat-qa/${platform}-flyer-handoff-frames`;
const e=JSON.parse(fs.readFileSync(root+'/evidence.json'));
console.log(platform,e.frames.length,fs.statSync(root+'/evidence.json').mtime.toISOString());
for(let scene=0;scene<4;scene++){
 const held=e.frames.filter(f=>f.scene===scene&&f.visual.stage===2);
 console.log(JSON.stringify({scene,count:held.length,drawn:held.filter(f=>f.authoredSkin.drawnGrip).length,
  resampleMax:Math.max(...held.map(f=>f.authoredSkin.gripSurfaceDistance||0)),
  drawnMax:Math.max(...held.map(f=>f.authoredSkin.drawnGripSurfaceDistance||0)),
  stableDrawnMax:Math.max(...held.filter(f=>f.renderTime-f.visual.started>=1).map(f=>f.authoredSkin.drawnGripSurfaceDistance||0)),
  samples:held.slice(0,3).map(f=>({image:f.image,time:f.renderTime-f.visual.started,resample:f.authoredSkin.gripSurfaceDistance,drawn:f.authoredSkin.drawnGripSurfaceDistance,
   difference:f.authoredSkin.resamplePalmDifference,draw:f.authoredSkin.drawnGrip}))}));
}
