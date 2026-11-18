"""Record source boundaries and written integration; never execute Java/tests."""
from pathlib import Path
import hashlib
import json
import re

ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
paths=[
    *['src/main/java/com/codex/blacklight/combat/'+n+'.java' for n in
      ['GreeneEmergenceClock','GreeneEmergenceShape','GreeneEmergenceHit','GreeneBattleCycle']],
    'src/main/java/com/codex/blacklight/entity/EncounterMob.java',
    'src/test/java/com/codex/blacklight/combat/GreeneEmergenceClockTest.java',
    'src/test/java/com/codex/blacklight/combat/GreeneEmergenceShapeTest.java',
    'src/greeneTest/java/com/codex/blacklight/gametest/GreeneEmergenceGameTest.java',
]
record={
    'schema':1,'date':'2026-11-17','status':'Recurring emergence Hit wired; compilation and runtime/player validation deferred',
    'sourceRecord':{'path':'art/encounters/greene-emergence-hit-source.json','sha256':sha(ROOT/'art/encounters/greene-emergence-hit-source.json')},
    'integration':{
        'clock':'Entry activates R3 at0 without a collision. Each observed update snapshots poll1 in authored R20/R3 order, subtracts stored float priorDelta and ends only when remaining<0, then adds .05f and activates the next poll. Age alone does not decide the last hit.',
        'geometry':'Both tracks use actual root. Authored Neck_1 belongs to the Hydra skeleton; lookup in mother_core_skeleton fails and P1 leaves the root matrix. No invented neck replacement. Raw radii20/3 convert once using preserved model scale.',
        'sweep':'Native discrete sphere strategy and max(1,ceil(distance/radius),ceil(relativeAngle/1rad)) count retained. Previous basis identity and current root rotation determine angle. The original candidate-derived fraction bracket/endpoints are not closed: fixed i/N, i=1..N is an explicit MC sampling adapter, not a claimed exact native solver.',
        'receipts':'R3 may consume a victim before R20 begins. UUID receipt is written before dispatch, including a receiver that rejects hurt. Tracks share receipts, expire only after >5 observed seconds, and clear after the final still-current frame. No strongest-window merge or accepted-only debit.',
        'body':'Animated victims use posed cuboids; other living targets use their actual hitboxes; mechanical hulls use articulated boxes; aircraft retain their current yaw-dependent envelope. The earliest MC sample is placed first; remaining spatial-query order is not globally sorted.',
        'world':'Only loaded XZ chunks and border are allowed. Underground roots may lie below minBuildHeight. There is no extra eye-to-target terrain ray; native overlap from a buried root would otherwise be suppressed. No block destruction or fabricated Crater damage.',
        'lifecycle':'Cancellation, collapse, natural completion, owner disposal and same-object load invalidate in-flight Frames. Checks before/after damage callbacks stop stale work. No skipped-tick catch-up or >8block teleport corridor.',
        'persistence':'CycleVersion3 stores Clock and shared receipts only during EMERGING. First update after load starts at loaded root; old snapshots without Hit state resume their pose without retroactive collisions. A complete future emergence initializes a new session.',
    },
    'explicitMinecraftAdapters':{
        'categories':'LivingEntity and physical hulls represent eligible Health/HitReaction receivers. No claim of complete P1 Prop/Ped/Vehicle membership or receiver semantics; ordinary blocks are not props.',
        'damage':'Living source800/100 use existing Greene .06 balance (48/6 baseline before recipient health units). Mechanical receivers keep source800/100 and existing hull armor; aircraft use raw values. This is balance policy, not an original unit conversion.',
        'friendly':'Dispatch to natural owner allies uses source .01/.2 factors; existing recipient hooks may still reject friendly damage after consuming the receipt. Explicit scoreboard allies and creative/spectator players remain protected; crew protections on mechanical hulls are retained. Disguise does not suppress area contact.',
        'impulse':'Original impulse vectors, mass/force transfer, transformation multiplier and specialized receiver reactions are NOT reproduced. Existing Minecraft damage/physical feedback remains; no native-force fidelity is claimed.',
    },
    'validation':{'staticOnly':True,'compilation':'not run','unitTests':'written, not run','gameTests':'written, not run','client':'not launched',
        'gameTestMethodsWritten':len(re.findall(r'@GameTest\(', (ROOT/paths[-1]).read_text(encoding='utf8')))},
    'remaining':['Opening mission emergence, Crater presentation/lifetime, full native impulse/receiver/filter/sampling behavior and actual player acceptance remain incomplete.'],
    'snapshots':{p:sha(ROOT/p) for p in paths},'fullGoalComplete':False,
}
out=ROOT/'art/encounters/greene-emergence-hit-integration-20261117.json'
out.write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Recorded emergence integration; Java, tests and game were not run.')
