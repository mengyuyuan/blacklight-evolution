// Compare a local 1.21.1 reference with this Forge checkout. No network or source writes.
// node tools/verify_forge_parity.mjs <reference> <forge> <report.json>
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const [source,target,output]=process.argv.slice(2);
if(!source||!target||!output)throw new Error('Expected reference, Forge checkout and report path');
const walk=p=>fs.readdirSync(p,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(p,e.name)):[path.join(p,e.name)]);
const read=p=>fs.readFileSync(p,'utf8');
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const canonical=x=>JSON.stringify(Array.isArray(x)?x.map(v=>JSON.parse(canonical(v))):x&&typeof x==='object'?Object.fromEntries(Object.keys(x).sort().map(k=>[k,JSON.parse(canonical(x[k]))])):x);
const result={exactResources:0,adaptedResources:[],unresolved:[],motionFiles:0,inventories:{},referenceDigest:''};
const digest=crypto.createHash('sha256');
const mapped=p=>p.replace(/(data\/[^/]+\/)(recipe|loot_table|advancement|structure)\//g,'$1$2s/').replace(/\/tags\/(item|block|entity_type|fluid|game_event)\//g,'/tags/$1s/').replace('/neoforge/biome_modifier/','/forge/biome_modifier/');
function normalizeJson(v,p,forge){
  v=structuredClone(v);
  if(p.includes('/lang/'))return v;
  if(p.includes('/recipe/')&&v.result?.id){v.result.item=v.result.id;delete v.result.id;}
  if(p.includes('/advancement/')&&v.display?.icon?.id){v.display.icon.item=v.display.icon.id;delete v.display.icon.id;}
  if(p.includes('/advancement/'))for(const criterion of Object.values(v.criteria??{}))for(const predicate of criterion.conditions?.items??[])if(typeof predicate.items==='string')predicate.items=[predicate.items];
  if(p.includes('/dimension_type/')&&v.monster_spawn_light_level?.value){let t=v.monster_spawn_light_level;v.monster_spawn_light_level={type:t.type,...t.value};}
  if(p.includes('/shaders/core/')&&p.endsWith('.json')&&forge){delete v.blend;delete v.attributes;}
  if(p.endsWith('pack.mcmeta'))v.pack.pack_format=15;
  if(p.includes('/tags/'))v.values=v.values.map(t=>typeof t==='object'&&t.required===false&&t.id==='minecraft:armadillo'?t.id:t);
  return JSON.parse(JSON.stringify(v).replaceAll('neoforge:add_features','forge:add_features').replaceAll('minecraft:short_grass','minecraft:grass')
    .replaceAll('minecraft:waxed_exposed_chiseled_copper','minecraft:waxed_exposed_cut_copper'));
}
for(const file of walk(path.join(source,'src/main/resources')).sort()){
  const rel=path.relative(source,file).replaceAll('\\','/'),dest=path.join(target,mapped(rel));
  digest.update(rel+'\0'+hash(file)+'\n');
  if(!fs.existsSync(dest)){result.unresolved.push({path:rel,reason:'missing'});continue;}
  if(hash(file)===hash(dest)){result.exactResources++;continue;}
  let equal=false,reason='';
  if(rel.endsWith('.json')||rel.endsWith('pack.mcmeta')){
    const a=normalizeJson(JSON.parse(read(file)),rel,false),b=normalizeJson(JSON.parse(read(dest)),rel,true);
    if(rel.includes('/lang/'))equal=Object.entries(a).every(([k,v])=>b[k]===v);
    else equal=canonical(a)===canonical(b);
    reason='1.20 data format / namespace / optional vanilla entry';
    if(rel.endsWith('blacklight.mixins.json')){
      const equivalents={AdaptivePlayerDamageMixin:'ForgeCriticalMassDamageMixin',OptionalEpicFightModeMixin:null,SaddleSteeringAccess:'PigSteeringAccess',TheTitansNeoDiveMixin:null,TheTitansNeoTagsMixin:null};
      const all=new Set([...(b.mixins??[]),...(b.client??[])]);
      const absent=[...(a.mixins??[]),...(a.client??[])].filter(n=>!(n in equivalents)&&!all.has(n)||equivalents[n]&&!all.has(equivalents[n]));
      equal=absent.length===0;reason='Forge event bridges, split saddle accessors, Neo-only mod hooks';
      if(!equal)result.unresolved.push({path:rel,missingMixins:absent});
    }
    if(!equal&&rel.includes('/city/')&&rel.endsWith('/map.json')){
      // Report the individual palette substitutions; do not silently whitelist the map.
      const diffs=[];
      const compare=(x,y,k)=>{if(canonical(x)===canonical(y))return;if(typeof x==='object'&&x&&typeof y==='object'&&y){for(const key of new Set([...Object.keys(x),...Object.keys(y)]))compare(x[key],y[key],k+'.'+key);}else diffs.push({key:k,source:x,target:y});};
      compare(a,b,'');result.unresolved.push({path:rel,paletteDiffs:diffs});continue;
    }
  }else if(rel.endsWith('.vsh')){
    equal=read(dest).replace(/fog_distance\(ModelViewMat,\s*/g,'fog_distance(').replace(/\s/g,'')===read(file).replace(/\s/g,'');reason='1.20 fog_distance signature';
  }
  if(equal)result.adaptedResources.push({path:rel,reason});else result.unresolved.push({path:rel,reason:'content differs'});
}
for(const file of walk(path.join(source,'art/motion-profile')).sort()){
  const rel=path.relative(source,file).replaceAll('\\','/'),dest=path.join(target,rel);digest.update(rel+'\0'+hash(file)+'\n');
  if(!fs.existsSync(dest)||hash(file)!==hash(dest))result.unresolved.push({path:rel,reason:'motion differs'});else result.motionFiles++;
}
// ID inventory catches omitted entities/items, even when registration APIs are different.
for(const name of ['ModItems','ModEntities','ModBlocks','ModEffects','ModBlockEntities','ModSounds']){
  const rel='src/main/java/com/codex/blacklight/registry/'+name+'.java';if(!fs.existsSync(path.join(source,rel)))continue;
  const ids=s=>new Set([...s.matchAll(/(?:register\w*|id)\(\s*"([a-z0-9_]+)"/g)].map(m=>m[1]));
  const a=ids(read(path.join(source,rel))),b=ids(read(path.join(target,rel)));const missing=[...a].filter(k=>!b.has(k));
  result.inventories[name]={reference:a.size,forge:b.size,missing};
  const fields=s=>new Set([...s.matchAll(/public\s+static\s+final\s+[^;=]+?\s+([A-Z][A-Z0-9_]+)\s*=/g)].map(m=>m[1]));
  const sourceFields=fields(read(path.join(source,rel))),targetFields=fields(read(path.join(target,rel)));
  const missingFields=[...sourceFields].filter(k=>!targetFields.has(k));
  result.inventories[name].referenceFields=sourceFields.size;result.inventories[name].forgeFields=targetFields.size;
  if(missingFields.length)result.unresolved.push({path:rel,missingFields});
  if(missing.length)result.unresolved.push({path:rel,missingIds:missing});
}
result.referenceDigest=digest.digest('hex');
for(const file of walk(path.join(source,'src/main/java')).filter(p=>p.endsWith('Commands.java')||p.endsWith('Config.java'))){
  const rel=path.relative(source,file),dest=path.join(target,rel);
  if(!fs.existsSync(dest)){result.unresolved.push({path:rel,reason:'missing command/config class'});continue;}
  const pattern=file.endsWith('Commands.java')?/Commands\.literal\("([^"]+)"\)/g:/\.define(?:InRange|List|ListAllowEmpty|Enum)?\(\s*"([^"]+)"/g;
  const names=s=>new Set([...s.matchAll(new RegExp(pattern.source,'g'))].map(m=>m[1]));
  const a=names(read(file)),b=names(read(dest)),missing=[...a].filter(k=>!b.has(k));
  result.inventories[path.basename(file,'.java')]={reference:a.size,forge:b.size,missing};
  if(missing.length)result.unresolved.push({path:rel,missingNames:missing});
}
fs.writeFileSync(output,JSON.stringify(result,null,2));
console.log(JSON.stringify(result,null,2));
process.exitCode=result.unresolved.length?1:0;
