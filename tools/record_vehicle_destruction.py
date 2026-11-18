"""Record verified destruction receipts, native screenshots and bounded source comparisons."""
from pathlib import Path
import hashlib,json,re,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
CACHE=Path('D:/CodexCache/vehicle-destruction-qa')
SOURCE=Path('D:/PrototypeReference/p1/blackwatch-fig-audit.json')
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
audit=json.loads(SOURCE.read_text(encoding='utf8'))
offsets={24140,24960,25164,25352,422912,423572,438916,439004}
evidence=[{k:r[k] for k in ['source','offset','type','owner','fields'] if k in r} for r in audit['records'] if r['offset'] in offsets]
report={
 'schemaVersion':1,'date':'2026-11-16','overallGoalComplete':False,
 'scope':'Authored ground-vehicle and helicopter destruction presentation, emitted once by server destruction and rendered as bounded local wrecks',
 'behavior':{'chassis':['truck','apc','tank','helicopter'],'fireTicks':120,'smokeEmissionTicks':240,'wreckTicks':360,'finalFadeTicks':40,'maxActiveWrecks':8,'receiptRangeBlocks':128,
 'nativeMeshes':'Burnt tint, collapsed hull height, detached doors/wheels/rotors, displaced turret and opened hatches; authored parts reused',
 'sounds':'Existing firearm blast or native helicopter blast, near metal break, timed chain debris and fire ambience; no newly authored recordings',
 'submerged':'Bubbles and wreck, no fire surfaces, flame particles, fire ambience or shock ring',
 'authority':'No new damage, block editing, inventory, salvage or collision introduced; original helicopter radius-three damage retained',
 'lifecycle':'Server accepted lethal transition only, helicopter impact/final crash only, client receipt deduplication, cap, expiry and world unload cleanup'},
 'sourceComparison':{'audit':str(SOURCE),'sha256':digest(SOURCE),'records':evidence,
 'supported':'Helicopter disabled branches contain WaitOnCollision and Explosion AIMessage tracks. This supports disabled/collision/final explosion ordering, not exact FX reconstruction.',
 'notDecoded':'Original comparison hash meanings and exact flame/debris/audio lifetimes are not decoded; all 120/240/360 timings are explicit Minecraft adaptations.',
 'uav':'UAV ClassName appears under callStrikeTeam movement; shared RequestCallStrikeTeam MinActorAge=15 is decoded. This does not establish the complete original viral detector or summon conditions.',
 'currentUav':'Existing code performs sustained 60-tick visible infection scan, breaks player disguise, and reports to nearby soldiers. Autonomous UAV dispatch of new reinforcements is not yet implemented; nearby officers still require their own radio eligibility and visibility.'},
 'visualReview':{'reviewedSelectedFrames':40,'finding':'Explosion burst and animated fire are visible; detached authored parts settle; burnt wreck remains after fire/smoke stop; underwater samples show no fire.',
 'firstPassCorrection':'Moved fire/smoke above the hull, added animated native fire surfaces independent of particle settings, and made initial explosion emission receipt-driven instead of assuming the first client tick age is zero or one.'},
 'regressionFixture':'A fresh combined Forge suite exposed an old airSite fixture that added a guard outside the queryable entity area: alive=true, UUID lookup=false, native query=false. The fixture now forces its one chunk, keeps the guard persistent, waits for actual native query visibility, asserts occupied landing rejection, then removes the guard and restores the prior chunk flag. Production landing safety is unchanged.',
 'protocol':{'neo':'standalone-66-vehicle-destruction','forge':'forge-1.20.1-26-vehicle-destruction'},
 'limits':['Client presentation only: late joiners and save reloads do not reconstruct old wrecks; no persistent lootable wreck entity.',
 'Wreck root probes support vertically; debris arcs use a local support plane, not full wall/stair/complex-terrain rigid-body collision.',
 'Native QA uses one connected integrated-server client on each loader, not a multi-client dedicated-server soak.',
 'Audio cue dispatch verified in muted QA; subjective listening and source-game frame-by-frame equivalence remain open.'],
 'platforms':{}
}
for platform,repo,build in [('neo',Path('D:/blacklight-evolution'),'neo-build'),('forge',Path('D:/blacklight-evolution-forge-1.20.1'),'forge-render-build')]:
 review=json.loads((CACHE/f'{platform}-review.json').read_text(encoding='utf8'))
 native=CACHE/f'{platform}-frames/evidence.json';data=json.loads(native.read_text(encoding='utf8'));assert data.get('error') is None
 assert (native.parent/'status.txt').read_text(encoding='utf8').startswith('PASS:')
 assert len(data['results'])==5 and all(r['receipts']==1 and r['expired'] for r in data['results'])
 log=Path(f'D:/CodexCache/creature-combat-{platform}-vehicleDestructionRegression.log');content=log.read_text(encoding='utf8',errors='replace')
 passed=re.findall(r'All (\d+) required tests passed',content);assert passed and 'BUILD SUCCESSFUL' in content
 units=[]
 for path in (Path('D:/CodexCache/creature-combat-qa')/build/'test-results/vehicle-destruction').glob('TEST-*.xml'):
  suite=ET.parse(path).getroot();assert suite.get('failures')=='0' and suite.get('errors')=='0';units.append({'suite':suite.get('name'),'count':int(suite.get('tests'))})
 assert sum(r['count'] for r in units)==4
 production=['military/vehicle/VehicleDestruction.java','military/vehicle/VehicleDestructionEffects.java','network/VehicleDestructionPayload.java','network/ModNetwork.java','entity/MilitaryVehicle.java','entity/BlackwatchHelicopter.java','client/model/VehicleWreckPose.java','client/model/MilitaryVehicleModel.java','client/model/HelicopterModel.java','client/renderer/VehicleDestructionVisuals.java']
 report['platforms'][platform]={'unitTests':units,'regressionTests':int(passed[-1]),'regressionLog':str(log),'regressionLogSha256':digest(log),'nativeEvidence':str(native),'nativeEvidenceSha256':digest(native),'frames':review['frames'],'selected':review['selected'],'sheet':review['sheet'],'lowestRenderedY':review['lowestRenderedY'],'floorY':81,'results':data['results'],'observedSoundEvents':data['sounds'],'productionSha256':{s:digest(repo/'src/main/java/com/codex/blacklight'/s) for s in production}}
target=ROOT/'art/military-vehicles/vehicle-destruction-validation.json';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(str(target));print(digest(target))
