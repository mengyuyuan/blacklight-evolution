// Build a bounded, reviewable comparison index from the user's local unpacked resources.
// No raw meshes, textures, sound banks or opaque FIG payloads are copied into this report.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const root=path.resolve(import.meta.dirname,'..'),reference='D:/PrototypeReference';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const index=read(reference+'/p1/work/creature-source-index.json');
const p1=read(reference+'/p1/creature-fig-audit.json'),p2=read(reference+'/p2/creature-fig-structure.json');
if(p2.trees.some(t=>t.selected&&!t.complete))throw Error('P2 structural audit incomplete');
const sourceFor=n=>{const row=index.find(r=>path.basename(r.source.replaceAll('\\','/'))===n);if(!row)throw Error(n);return row;};
const clipsFor=(file,pattern)=>sourceFor(file).animations.filter(a=>pattern.test(a.name)).map(a=>({name:a.name,frames:a.frames,fps:a.fps,offset:a.offset}));
const species=[
 {id:'common_infected',sources:['infected2_all.p3d'],sourceMapping:'Candidate reference only: infected2 grab/reorient and jump attacks are not yet proven to be the same role as the current ordinary civilian. P2 infected1 shared behavior also indexed.',runtime:'Authored segmented humanoid locomotion and three attacks. Source curves not yet bound.',next:'Resolve common infected versus infected2 behavior, then retarget and test each hand against actual rendered wrists.'},
 {id:'infected_host',sources:[],sourceMapping:'Minecraft host-species adaptation; no one-to-one original creature established.',runtime:'Existing host behavior and attached organ attacks.',next:'Audit organ attachment during hit, grab, consume and death on biped, quadruped, flying and representative mod models.'},
 ...['stalker','spitter','brute'].map(id=>({id:'blacklight_'+id,sources:[],sourceMapping:'No verified one-to-one original species in this audit; do not label the authored animation as extracted.',runtime:'Existing authored per-species attacks and physics.',next:id==='spitter'?'Verify mouth socket, wall interception, each projectile release and retreat.':id==='brute'?'Verify fist and terrain contact, guard/impact response and charge obstruction.':'Verify leap landing, individual claw contact and recovery on a moving target.'})),
 {id:'blacklight_hunter',sources:['Brawler.p3d'],sourceMapping:'Original P1 Brawler source; canonical existing Hunter, 2500 actual HP.',runtime:'Source frame schedules and wrist windows shared by server/render; contact and ground checks implemented.',next:'Paired player combat, grappling/consumption and audible mixing still need their own acceptance.'},
 {id:'blacklight_hydra',sources:['Hydra.p3d'],sourceMapping:'Original P1 Hydra source and FIG.',runtime:'Existing grab system and segmented slam retained.',next:'Complete sweep/side-sweep contact and giver/receiver synchronization against actual ground and varying body scale.'},
 {id:'juggernaut',sources:['infected3.p3d'],sourceMapping:'P2 infected3, with hammerer/slammer geometry variants indexed separately.',runtime:'Own source two-hit combo, two-hand slam, block, block-hit and stun retargeted to existing mesh. Shared rendered fist contact, directional guard, poise break and actual terrain impact. Original oversized secondary hit events excluded from primary fist damage.',next:'CHARGE still uses prior authored body-contact behavior. Original transition conditions, locomotion root travel, attack-type-specific armor sounds and player-facing balance require further work; no complete-original-AI claim.'},
 {id:'flyer',sources:['flyer.p3d'],sourceMapping:'P2 flyer. Dive controller references idle, attackspeed and air_ram; collision has its own clip.',runtime:'Existing authored flight/dive and spit adaptation.',next:'Resolve dynamic dive-controller fields. Current spit lacks original-source confirmation and must not be presented as original behavior.'},
 {id:'elizabeth_greene',sources:['mother.p3d'],sourceMapping:'P1 mother FIG and skeleton.',runtime:'Partial source attacks and collapse implemented.',next:'Complete separate appendage damage and down-state conditional loop, then validate projectile socket and wave cover.'},
 {id:'goliath',sources:['behemoth.p3d'],sourceMapping:'P2 behemoth. Biped, prone and crawl branches structurally read; conditional field meanings remain partial.',runtime:'Source curves cached; complete crawl phase not active.',next:'Bind distinct biped slam, leg-break transition, crawl attacks and recovery. No arbitrary health-threshold substitution.'},
 {id:'blacklight_hive',sources:[],sourceMapping:'Existing Minecraft hive adaptation.',runtime:'Existing hatch and player interactions retained.',next:'Verify opening/release/closing and preserve Supreme Hunter summon entry.'},
 {id:'blacklight_supreme_hunter',sources:[],sourceMapping:'Protected pre-existing completed creature; reference only.',runtime:'Keep original summon, model, attacks, boss bar, absorption and rewards.',next:'Regression only; no replacement by leader_hunter or new generic boss logic.'},
];
const motionRecords=[];
for(const file of ['infected3','flyer','behemoth']){
 const source=sourceFor(file+'.p3d'),known=new Map(source.animations.map(a=>[a.name,a]));
 for(const r of p2.records.filter(r=>r.file===`input/p2/${file}.p3d`&&['PuppetAnimation','AnimationP2'].includes(r.type))){
  const name=r.references.find(s=>s.at===16)?.name;
  if(r.length!==132||!known.has(name))continue;
  const v=at=>r.words.find(w=>w.at===at)?.f32;
  const [begin,end,speed,from,to]=[8,12,24,36,40].map(v),clip=known.get(name);
  if(![begin,end,speed,from,to].every(Number.isFinite))throw Error('Non-finite P2 animation prefix '+r.offset);
  motionRecords.push({source:file+'.p3d',offset:r.offset,branch:r.owner,animation:name,begin,end,speed,from,to,cyclic:r.references.find(s=>s.at===44)?.name,
   requiresFrameBoundReview:from>clip.frames-1||to>clip.frames-1||from<0,
   confidence:'Animation hash matches the actual clip; scalar prefix interpreted from repeated 132-byte P2 records. State transition conditions and dynamic overrides are not fully decoded.'});
 }
}
const report={date:'2026-11-14',scope:'Existing basic infected, Elizabeth Greene and Goliath. Supreme Hunter protected. Other draft bosses retired.',
 boundary:'An animation index and bounded FIG parse are not proof of complete original AI, audio, contact or gameplay reproduction.',
 sources:index.map(({source,sha256,skeletons,animations})=>({file:path.basename(source.replaceAll('\\','/')),sha256,skeletons,animationCount:animations.length})),
 p1:{trees:p1.trees,fullyConsumed:p1.allTreesFullyConsumed,records:p1.records.length,decoded:p1.records.filter(r=>r.fields).length},
 p2:{selectedTrees:p2.trees.filter(t=>t.selected),structuralRecords:p2.records.length,
  sharedFigArchive:{archive:'boot.rcf',entry:'art/startup_fig.p3d.rz',offset:40562688,bytes:1735045,sha256:crypto.createHash('sha256').update(fs.readFileSync(reference+'/p2/input/startup_fig.p3d.rz')).digest('hex')},
  limitation:'Opaque conditions, TrackGroup internals and dynamic animation controllers remain. P1 payload field offsets must not be reused for P2.'},
 species,
 representativeClips:{hydra:clipsFor('Hydra.p3d',/^hyd_/),infected3:clipsFor('infected3.p3d',/^infected3_(com_(2|block|stuned|belly)|gethit)/),flyer:clipsFor('flyer.p3d',/^flyer_/),behemoth:clipsFor('behemoth.p3d',/^behemoth_(com_(biped_leg|crawl|prone)|loco_)/)},
 p2AnimationRecords:motionRecords};
const output=path.join(root,'art/encounters/creature-source-comparison.json');fs.writeFileSync(output,JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({species:species.length,packages:index.length,animationRecords:index.reduce((n,r)=>n+r.animations.length,0),p2BoundedTrees:report.p2.selectedTrees.length,p2MatchedAnimationRecords:motionRecords.length,output}));
