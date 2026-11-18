"""Static initial encounter-entry record. Never runs Java/native game code."""
from pathlib import Path
import hashlib
import json
import re

ROOT=Path(__file__).resolve().parents[1]
REF=Path('D:/PrototypeReference/p1')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
lua=json.loads((REF/'private/greene-mission-lua.json').read_text(encoding='utf8'))
entry=next(r for r in lua['records'] if r['offset']==75836)
functions=next(r for r in lua['records'] if r['offset']==66452)
assert entry['bytecode']['instructions'][54]['constant']=='MotherEmerge'
assert entry['bytecode']['instructions'][55]['constant']=='justCore'
assert entry['bytecode']['instructions'][76]['constant']=='Wait'
assert entry['bytecode']['instructions'][77]['constant']==15
assert functions['bytecode']['prototypes'][6]['instructions'][8]['constant']=='ai_SetUnderground'
constructor=REF/'private/rapid-refs-10d96564.txt'
assert '10079133: mov dword ptr [esi + 0xc], 1' in constructor.read_text(encoding='utf8')
fixtures=[
    'src/greeneTest/java/com/codex/blacklight/gametest/GreeneCycleGameTest.java',
    'src/greeneTest/java/com/codex/blacklight/gametest/GreeneSegmentDeathGameTest.java',
    'src/greeneTest/java/com/codex/blacklight/gametest/GreeneEmergenceGameTest.java',
    'src/greeneTest/java/com/codex/blacklight/gametest/NativeBossBodyControlGameTest.java',
    'src/blackwatchTest/java/com/codex/blacklight/gametest/MilitaryBulletMultipartGameTest.java',
    'src/helicopterTest/java/com/codex/blacklight/gametest/VehicleOrdnanceBodyGameTest.java',
    'src/helicopterTest/java/com/codex/blacklight/gametest/HelicopterManualBodyGameTest.java',
    'src/vehicleTest/java/com/codex/blacklight/gametest/vehicle/VehicleMountedBodyGameTest.java',
    'src/encounterTest/java/com/codex/blacklight/gametest/EncounterGameTest.java',
    'src/greeneVisual/java/com/codex/blacklight/client/GreeneCycleVisual.java',
]
files=[
    'src/main/java/com/codex/blacklight/combat/GreeneBattleCycle.java',
    'src/main/java/com/codex/blacklight/entity/EncounterMob.java',
    'src/main/java/com/codex/blacklight/network/ModNetwork.java',
    'src/greeneTest/java/com/codex/blacklight/gametest/GreeneOpeningGameTest.java',
    *fixtures,
]
record={
    'schema':1,'date':'2026-11-17','state':'Initial standalone source rise wired; complete mission opening remains partial',
    'source':{
        'missionFile':lua['source'],'missionSha256':lua['sha256'],
        'decodedLuaSha256':sha(REF/'private/greene-mission-lua.json'),
        'nativeConstructorProofSha256':sha(constructor),
        'nativeConstructor':'TunnelingBehaviour10079120 sets state+0xc=1 (Underground) at10079133. Existing100794f0 proof shows ComeOut from this state samples surface, places actor ground-depth and enters ComingOut.',
        'entryScriptOffset':75836,
        'entryOrder':'MotherSpawn atpc11..14; fixed PLAYER_1 target15..18; MotherSpawnSegments19..20; attack flag setup39..53; MotherEmerge(justCore)54..56; core Damageable=false57..60; ToCombat65..67; HUD marker68..75; Wait15 only76..78 before DisplaySegHealth79..80.',
        'emergeFunction':'Lua66452 prototype6 pc8..12 calls ai_SetUnderground(gMotherCore,false,false). Its justCore argument skips Hydra emergence in the subsequent loop. The first entry script does not call MotherSpawnHydras.',
        'sharedRiseProof':'private/greene-submerge-emerge-lifecycle-proof.json',
        'sharedRiseProofSha256':sha(REF/'private/greene-submerge-emerge-lifecycle-proof.json'),
        'entryInstructions':[{k:v for k,v in i.items() if k in ('pc','opcode','A','B','C','constant','Bconstant','Cconstant')}
                             for i in entry['bytecode']['instructions'][39:81]],
    },
    'integration':{
        'firstPlacement':'Constructor initializes a pending marker and health ownership only. First actual server tick samples down from final feet+.001, excluding overhead ceilings, and begins EMERGE_WAIT from ground minus20 source units using preserved mesh scale; no constructor-origin teleport.',
        'time':'Same source spawn-prop gate6ticks, enter0..244/30fps, constant upward20units/1.33333s, targetability27ticks, optional eligible attack handoff80ticks and163tick uninterrupted clip as repeat rise. No added15second gameplay delay.',
        'health':'Initial core stays guarded, all three current armor children remain at wave1, and first entry does not regenerate armor or increment waveCount. No Hunter/Hydra repeat scheduling starts.',
        'spawnNbt':'Minimal Entity.load summon/spawn data without a GreeneBattle or any prior encounter marker schedules fresh entry. Old EncounterPhase/Broken/Armor/Cycle/Exposed records retain legacy migration and do not replay opening.',
        'persistence':'CycleVersion4 persists OpeningPending. Version3 and older/missing markers mean an existing encounter. Active entry reuses relative phase/Hit receipts and saved root-physics lease; unloaded time does not replay first placement.',
        'network':'standalone-85-hydra-thermal / forge-1.20.1-45-hydra-thermal; pending targetability is synchronized.',
        'existingFixtures':'Ten damage/pose/ray fixtures explicitly load a standing existing state. They do not claim to test initial spawn. Six independent opening GameTests exercise fresh construction, real EntityType.loadEntityRecursive paths and low-ceiling floor choice.',
    },
    'remainingAndAdaptations':[
        'Standalone final spawn position substitutes for the TimesSquare locator. Initial cinematics, dialogue/camera gating and the full mission FSM remain unported.',
        'Initial ForceThunderousRoar=1 and scripted attack-unlock variable progression are recorded but not integrated by this entry change; full original opening combat selection is not claimed.',
        'The selected Minecraft target supplies Devastator context when available, rather than a globally bound native PLAYER_1 handle.',
        'Source Crater props/audio/effects, complete native actor/physics timing, original Hit sampling and receiver semantics retain existing stated gaps.',
        'Fresh real-client appearance, oversized skin intersecting overhead geometry, target acquisition and player-facing readability have not been observed. The low-ceiling regression checks root floor choice, not that a giant boss fits a tiny room.',
    ],
    'validation':{'type':'static source/code/schema only','compilation':'not run','unitTests':'not run','gameTests':'not run','client':'not launched','installedRelease':'none',
                  'gameTestMethodsWritten':len(re.findall(r'@GameTest\(',(ROOT/files[3]).read_text(encoding='utf8')))},
    'snapshots':{p:sha(ROOT/p) for p in files},'fullGoalComplete':False,
}
(ROOT/'art/encounters/greene-opening-integration-20261117.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Recorded first Greene rise, fresh/legacy ownership, six pending tests and full-opening limits.')
