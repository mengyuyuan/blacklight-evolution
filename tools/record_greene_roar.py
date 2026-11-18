"""Static source evidence and implementation snapshot; never executes game code."""
from pathlib import Path
import hashlib
import json
import re

ROOT=Path(__file__).resolve().parents[1]
REF=Path('D:/PrototypeReference/p1')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
records=json.loads((REF/'private/greene-ai-complete.json').read_text(encoding='utf8'))['records']
source=[r for r in records if r['source']=='startup_fig.p3d.rz']
by={r['offset']:r for r in source}
offsets=[266204,266244,266840,266900,267012,267152,267192,267232,267272,267356,
         267448,267580,267640,267688,273040,273100,273296,273604,273644,273740,274040,274172]
assert by[267152]['fields']['Distance']==35
assert by[267192]['fields']['Time']==6
assert by[267688]['fields']['Time']==15
assert by[273040]['fields']['Distance']==36
chunk=next(r for r in source if r['type']=='Chunk' and r['offset']<266112<r['end'])
branches=[r for r in source if r['kind']=='BranchHash' and chunk['offset']<=r['offset']<chunk['end']]
refs={i:branches[i]['offset'] for i in [8,13,34,39]}
assert refs=={8:266112,13:267516,34:272948,39:273540}
opportunity=REF/'private/rapid-runtime-10a8e9c0.txt'
assert '10a8ea4e: cmp eax, dword ptr [ecx + 8]' in opportunity.read_text()
lua=json.loads((REF/'private/greene-mission-lua.json').read_text(encoding='utf8'))['records']
cinema=next(r for r in lua if r['offset']==12384)['bytecode']['instructions']
assert cinema[153]['constant']==cinema[394]['constant']=='ForceThunderousRoar'
assert cinema[154]['constant']==1 and cinema[395]['constant']==0
files=[
    'src/main/java/com/codex/blacklight/combat/GreeneRoarTiming.java',
    'src/main/java/com/codex/blacklight/combat/EncounterTactics.java',
    'src/main/java/com/codex/blacklight/entity/EncounterMob.java',
    'src/test/java/com/codex/blacklight/combat/GreeneRoarTimingTest.java',
    'src/test/java/com/codex/blacklight/combat/GreeneWeaponProgramTest.java',
    'src/greeneTest/java/com/codex/blacklight/gametest/GreeneRoarGameTest.java',
    'tools/record_greene_roar.py',
]
record={
    'schema':1,'date':'2026-11-17','state':'Dual-loader source/code integration; not built, installed or player-accepted',
    'source':{
        'figSha256':sha(REF/'input/startup_fig.p3d.rz'),
        'decodedSha256':sha(REF/'private/greene-ai-complete.json'),
        'nativeSha256':sha(REF/'input/prototypeenginef.dll'),
        'opportunityProofSha256':sha(opportunity),
        'branchReferences':refs,
        'naturalGate':'Ordered attack bank first prefers helicopter/tank, then ForceThunderousRoar, then natural roar. Natural: XZ<35, fd0e timer>=6, 242e timer<=0, ThunderousRoarForbidden=0, target not Devastator.',
        'concurrentDwell':'Close node273040 uses XZ<=36 and sets increasing fd0e=0 on entry; far node273296 resets stopped fd0e=0. Opportunity10a8ea4b..51 compares candidate to current branch and does not re-enter the same node every update.',
        'entry':'Execute ref13 is node267516. It spawns ref39 roar request273540, resets increasing fd0e=0 and sets decreasing242e=15 immediately. It does not wait for the7.166667second pose to finish before starting recharge.',
        'forcedLimit':'Cinematic Lua12384 sets ForceThunderousRoar=1 at pc151..155 and clears it at pc392..396. Entry75836 also sets1. There is no proof this is a self-consuming one-shot; no invented first-spawn single roar is added.',
        'records':[by[o] for o in offsets],
    },
    'implementation':{
        'selection':'Replaces the d<12 immediate-roar shortcut. Real FightGoal supplies dwell, distance, mechanical priority and current target Devastator gates; ordinary weapons remain eligible while the concurrent timer accrues.',
        'space':'This roar adapter divides Minecraft XZ distance by the existing Greene source-model blocks/unit exactly once, matching the already converted radial-wave geometry. It does not change the separately tuned1:1 on-foot shooting thresholds.',
        'timing':'Six seconds is120 observed20Hz updates; fifteen seconds is300 updates from accepted roar entry. Duplicate observations in one game tick and absent unloaded updates cannot advance the timers. No variable-frame-rate numerical equality is claimed.',
        'engagement':'The MC observer starts with a valid hostile target and a cycle eligible to attack; inactive cycle or missing target resets dwell. NoAI freezes the observer. Exact full native concurrent AI/task lifecycle is outside this adapter.',
        'save':'Version1 GreeneRoar stores clamped relative dwell/recharge, near/far state and forbidden flag. Legacy EncounterRecharge.HOWL migrates with300tick cap and no fabricated accrued dwell. Same-object load replaces prior state.',
        'actionBoundary':'Explicit begin(HOWL,target) remains a low-level scripted action request like the other moves; only autonomous FightGoal imposes dwell/priority. It still checks live action ownership, cooldown, enemy, sight, cycle, restriction and source-scaled35 distance. The existing post-action MC recovery remains separate.',
    },
    'remaining':[
        'Full cinematic forced-roar lifecycle and scripted attack unlocks are unported; standalone Forbidden defaults false unless saved state supplies it.',
        'Original roar pose/hit/audio/category and native variable-dt limits remain those of the existing source-action integration; this change does not claim full original mechanics.',
        'Real first-entry roar frequency, visuals, input and player avoidance still require later gameplay observation.',
    ],
    'validation':{'staticOnly':True,'compilation':'not run','tests':'written, not run','client':'not launched','release':'not built or installed',
        'unitMethodsWritten':len(re.findall(r'@Test\b',(ROOT/files[3]).read_text(encoding='utf8'))),
        'gameTestMethodsWritten':len(re.findall(r'@GameTest\(',(ROOT/files[5]).read_text(encoding='utf8')))},
    'snapshots':{p:sha(ROOT/p) for p in files},'fullGoalComplete':False,
}
out=ROOT/'art/encounters/greene-natural-roar-integration-20261117.json'
out.write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Recorded original natural-roar gates, native branch retention and pending code/tests.')
