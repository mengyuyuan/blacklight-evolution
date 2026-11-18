// Reuse the user's existing model verbatim. Do not synthesize a replacement from cube counts.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const root=path.resolve(import.meta.dirname,'..'),assets=path.join(root,'src/main/resources/assets/blacklight');
const file=path.join(root,'art/encounters/manifest.json'),manifest=JSON.parse(fs.readFileSync(file,'utf8'));
const aliases=[
 ['geo/brawler_mc.geo.json','geo/blacklight_hunter_mc.geo.json'],
 ['textures/entity/brawler_mc.png','textures/entity/hunter_mc.png'],
 ['animations/brawler.animation.json','animations/blacklight_hunter.animation.json']
];
const hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
for(const [target,source]of aliases)fs.copyFileSync(path.join(assets,source),path.join(assets,target));
const geo=JSON.parse(fs.readFileSync(path.join(assets,aliases[0][1]),'utf8'))['minecraft:geometry'][0];
const audit={id:'brawler',bones:geo.bones.length,cuboids:geo.bones.reduce((n,b)=>n+(b.cubes?.length??0),0),textureSize:geo.description.texture_width,reusedExistingModel:true};
manifest.modelAudit=(manifest.modelAudit??[]).filter(row=>row.id!=='brawler');manifest.modelAudit.push(audit);
manifest.styleReference='Use the existing Hunter and Supreme Hunter geometry, silhouette, UV work and materials as the visual standard. Matching cube counts or texture dimensions alone does not meet that standard.';
manifest.modelStatus=['existing-style-r2','extracted-reference-r3'].includes(manifest.authoredRevision)?'Brawler retains existing Hunter resources byte-for-byte. Supreme Hunter unchanged. New models have individually authored silhouettes and existing material patterns; runtime validation and user aesthetic review remain distinct.':'Brawler restored to the existing Hunter resources byte-for-byte. Supreme Hunter unchanged. Other newly authored encounter models remain drafts; they are not accepted as matching the existing models in style or detail.';
manifest.resourceAliases=aliases.map(([target,source])=>({target,source,sha256:hash(fs.readFileSync(path.join(assets,source)))}));
if(manifest.greeneSurface)manifest.modelStatus='Brawler/Hunter and Supreme Hunter remain unchanged. Greene uses the separately authored Supreme-style cuboid model; other encounter models remain at their documented revision and review status.';
manifest.existingModelBaselines=['geo/blacklight_hunter_mc.geo.json','textures/entity/hunter_mc.png','animations/blacklight_hunter.animation.json','models/hunter_combat_motion.json','models/supreme_hunter_block.json','textures/entity/supreme_hunter_block.png'].map(source=>({source,sha256:hash(fs.readFileSync(path.join(assets,source)))}));
manifest.motionReference={source:'Authored encounter locomotion and combat; existing calibrated Brawler/Hunter clips for Brawler',targets:['leader_hunter','juggernaut','goliath'],brawler:'Uses hunter_combat_motion.json without another retarget. Double attack repeats the original swipe at the existing two contact times. Slam and leap use the existing Brawler clips.',scope:'Monster anatomy uses its authored combat poses. Locomotion follows actual displacement. These are not original per-species decoded clips.'};
fs.writeFileSync(file,JSON.stringify(manifest,null,2)+'\n');
console.log(JSON.stringify({restored:'brawler',resources:manifest.resourceAliases,originalSupremeHunter:'unchanged',otherModels:manifest.modelStatus}));

