"""Record implementation and source boundaries without executing Java or game code."""
from pathlib import Path
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[1]
REF = Path('D:/PrototypeReference/p1')

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

files = [
    'src/main/java/com/codex/blacklight/entity/EncounterMob.java',
    'src/main/java/com/codex/blacklight/entity/GreenePart.java',
    'src/main/java/com/codex/blacklight/entity/BlacklightHydra.java',
    'src/main/java/com/codex/blacklight/client/model/SourceBossPose.java',
    'src/main/java/com/codex/blacklight/mixin/GreeneHealthFloorMixin.java',
    'src/main/resources/blacklight.mixins.json',
    'src/main/resources/assets/blacklight/models/encounter_elizabeth_greene_source.json',
    'src/main/resources/assets/blacklight/geo/elizabeth_greene_mc.geo.json',
    'src/test/resources/greene-skin-reference.json',
    'src/main/java/com/codex/blacklight/network/ModNetwork.java',
    *['src/main/java/com/codex/blacklight/combat/'+name+'.java' for name in
      ['GreeneBattleCycle', 'GreeneBattleHealth', 'GreeneHealthEvents', 'GreeneBrood', 'GreeneMotherHealth', 'GreeneMotion', 'GreeneContact', 'SourceBossMotion', 'SourceBossAction', 'SourceRadialWave', 'GreeneEmergenceClock', 'GreeneEmergenceShape', 'GreeneEmergenceHit']],
    'src/greeneTest/java/com/codex/blacklight/gametest/GreeneCycleGameTest.java',
    'src/greeneTest/java/com/codex/blacklight/gametest/GreeneEmergenceGameTest.java',
    'src/greeneTest/java/com/codex/blacklight/gametest/NativeBossBodyControlGameTest.java',
    'src/encounterTest/java/com/codex/blacklight/gametest/EncounterGameTest.java',
    'src/greeneVisual/java/com/codex/blacklight/client/GreeneCycleVisual.java',
    'src/test/java/com/codex/blacklight/combat/GreeneBattleHealthTest.java',
    'src/test/java/com/codex/blacklight/combat/GreeneMotionTest.java',
    'art/encounters/greene-segment-death-integration-20261117.json',
    'art/encounters/greene-opening-integration-20261117.json',
]
proofs = [
    'greene-mother-health-proof.json', 'greene-mission-stage-reachability-proof.json',
    'greene-submerge-emerge-lifecycle-proof.json', 'greene-brood-source-proof.json',
]
record = {
    'schema': 1, 'date': '2026-11-17',
    'state': 'Initial rise and recurring standalone Greene lifecycle integrated in both loaders; no build or runtime acceptance',
    'userConstraint': 'Continue code first; testing deferred. No Java/Gradle, game/client, original executable, media playback or user-save changes.',
    'sources': {'private/'+p: sha(REF/'private'/p) for p in proofs},
    'phases': [
        {'id': 0, 'name': 'GUARDED', 'behavior': 'Completed first or repeat emergence. Fresh constructor has a pending first-rise marker that blocks attacks/targetability until final placed entry. Core immune; attached appendages receive their source share.'},
        {'id': 1, 'name': 'COLLAPSING', 'ticks': 125, 'behavior': 'Sample recovery timer once; wait for controlling player Devastator before source floor work. Native exposed frames15..60 at24fps*.3, no invented idle blend.'},
        {'id': 2, 'name': 'DOWN', 'behavior': 'Increase wave count and capture health once before waiting for Devastator. Enable core own damage then ordinary8.5% scripted debit (or half current below source50 margin). Native frames60..150 loop75ticks.'},
        {'id': 3, 'name': 'RISING', 'ticks': 50, 'behavior': 'Timer expiry or26% loss request exits DOWN. Source frame150..209, four-tick blend from actual previous DOWN age. Core remains damageable; appendages stay broken.'},
        {'id': 4, 'name': 'SUBMERGING', 'clipTicks': 59, 'behavior': 'Core immune. Begin400tick underground timer and three-Hunter wave. Actual root moves down20 source units from.8..2.86667seconds with constant-speed, remaining-distance cap. Targetability turns off at16ticks.'},
        {'id': 5, 'name': 'UNDERGROUND', 'behavior': 'Requires both sink clip completion and actual Y within.75 source units of goal. Native idle frame0 hold; initial four-tick blend from completed sink. Wait remaining underground timer.'},
        {'id': 6, 'name': 'EMERGE_WAIT', 'minimumTicks': 6, 'behavior': 'Restore scaled appendages at repeat entry; re-sample current same-XZ ground and place root20 source units below it. Wait for controlling player Devastator before rise.'},
        {'id': 7, 'name': 'EMERGING', 'clipTicks': 163, 'behavior': 'Native enter0..244 at30fps; actual root rises20 units over1.33333seconds. Targetable at27ticks; valid attack can take over at80ticks when spatially Outside, otherwise complete163. Four-tick outgoing blend.'},
    ],
    'health': {
        'normalPath': 'Outer mission10->19->30->38. Initial floor=max; first collapse.655*max; second normal collapse-.035*max (not stage16 .31); later collapse waits Devastator once then60ticks before floor0.',
        'entryLock': 'Repeat armour waves2/3 lock floor to current health at entry, with source<=500 low-health release. The final phase does not add an invented reset on every further wave.',
        'scale': 'Source6000 core is scaled to configured MC maximum. Segments500/700/900/1000 saturate at last tier; no four-round cap. Legacy90 segment values migrate by percentage.',
        'healthUnits': 'Persist HealthBasis, rescale armor/floor/pre-stun snapshot proportionally when actual max changes, and load armor against its stored basis before rebasing. Direct raw part damage converts once to recipient CombatScale units; scripted actual-HP8.5% debit divides units before ordinary damage hooks. Loading/rebasing itself never heals.',
        'earlyRecovery': 'Compare loss from pre-stun health to.25999999046325684*max; stun counts. Latch request after damage, never cap the triggering hit at26%.',
        'writeBoundary': 'Neo final ABSORPTION modifier / Forge final damage hook preserve normal accounting. actuallyHurt->setHealth ModifyArg guards the final write only while alive and in own damage. Nested bypass resets depth, direct administrative health writes remain effective.',
    },
    'brood': {
        'hunters': 'Source UndergroundBrawler min/max/total3 per wave. Unused WAVEONETOTAL6 is not a spawn count. Current MC adaptation surface-spawns Hunters at collision-checked loaded terrain.',
        'hydras': 'Four task-owned slots from source wave2, staggered raise (60ticks then20 apart for wave2;20 then20 apart for later waves).20second task respawn uses persisted world-time death receipts, including deaths while mother is unloaded.',
        'loading': 'Unknown UUID is never death. Desired raised state persists in ledger and is reapplied on child join. Repeated raise cannot reset emergence or interrupt surface attack.',
        'disposal': 'Accepted owner death/removal retires Hydras and releases surviving Hunters; chunk unload does not dispose. Legacy replacement retires old task leases, excluding RETIRING identities from replacement waves.',
        'callbacks': 'Owner death in addFreshEntity stops remaining spawn work, cleans up the newly registered child and cannot invalidate iteration of Hunter waves.',
        'firstDeath': 'Original exact4-to3 notification retained as receipt. No invented all-Hydras-dead exposure shield gate.',
    },
    'persistence': {
        'cycle': 'Version4 additionally saves OpeningPending. Version3/legacy records are existing encounters and never replay initial rise. HealthBasis, observed local ages, remaining delays, one-shot flags, wave/root state and emergence Hit receipts persist without unloaded-time catch-up. Old snapshots without Hit state do not retroactively damage targets.',
        'rootLease': 'EncounterMob.load releases old cycle root ownership before vanilla reads incoming NoGravity. Cycle.load also releases it for direct readAdditionalSaveData calls. Stored original noGravity/noPhysics return at release.',
        'legacy': 'Reset old cycle pending work, retire previous brood, then migrate only saved collapse/down/rise age. Existing DOWN/RISING snapshots do not receive retroactive scripted damage.',
        'handoff': 'Save the remaining part of the four-tick emergence blend relatively. Existing entity load policy cancels attacks; this does not claim restoration of the old weapon session.',
        'network': 'Synced CompoundTag has initial pending gate, stage-local clock, exposure, core damageability, armour wave and outgoing blend. Protocol standalone84 / forge44 requires matching clients.',
    },
    'adaptationsAndRemaining': [
        'Fresh construction and minimal summon data now start the source initial rise from final placed terrain, without repeat-wave Hydras/Hunters. Original opening ForceThunderousRoar/attack flag selection and full mission cinematic context remain incomplete.',
        'TimesSquare cameras/dialogue waits are omitted in standalone flow; original locators map to loaded collision-checked MC terrain. Full mission flags/attack unlocks and external/checkpoint branches remain incomplete.',
        'Recurring R20/R3 root Hits now use separate source float clocks, shared pre-dispatch receipts and actual posed/body geometry. The i/N sphere sample grid, category mapping and damage balance remain MC adapters; native impulse, complete receiver semantics, Crater props/lifetime, Hunter underground birth and Hydra death sink are not completed.',
        'HOWL sphere radii and all local offset axes now share the preserved mesh scale. RawFIG values remain auditable. HOWL source0x42 is SimulatedObject|Prop; complete native category membership is not proven by the living-target MC adapter.',
        'The legacy three-spike ring is replaced by three original Slug skin-region cuboid groups using matching mother/segment skeletons and shared pose. Original blended skin is still approximated by rigid cuboids and existing authored materials; player visual acceptance is deferred.',
        'Source Y units use decoded pixelsPerSourceUnit/16 from the preserved mesh; root is a real entity displacement. Player camera, hit readability, subterranean clipping and changed-terrain behavior still need in-game observation.',
        'Independent segment death now copies the break pose and uses the original20..66..95 death track for63 observed ticks. Original death FX/audio, native corpse physics root and handle retirement remain incomplete. Existing local hit overlay, child cooldown, immediate health-slot detach and configured core health remain explicit MC adapters.',
    ],
    'validation': {
        'performed': 'Static source/asset provenance, platform API inspection, schema and cross-loader parity only.',
        'compilation': 'not run', 'unitTests': 'not run', 'gameTests': 'not run', 'clientPlayerValidation': 'not run', 'installedRelease': 'none',
        'deferredGreeneGameTestCount': len(re.findall(r'@GameTest\(', (ROOT/'src/greeneTest/java/com/codex/blacklight/gametest/GreeneCycleGameTest.java').read_text(encoding='utf8'))),
        'playerChecks': [
            'Break three visible appendages, observe collapse/head accessibility and actual early recovery without repeated stun damage.',
            'Observe full sink, no targetable invisible body underground, terrain-height emergence and restored appendages only at emergence.',
            'Exercise repeat cycles and safe overkill: first source floor survives, second source floor permits lethal damage; no nested callback resurrection.',
            'Load during each of eight phases and short handoff, and reload the same instance; no floating/noPhysics residue, duplicate reinforcements or clock catch-up.',
            'Observe3 Hunters and4 Hydras, child/owner chunk unload independently, retirement,20second respawn and owner death during spawn callbacks.',
        ],
    },
    'fullGoalComplete': False,
    'snapshots': {p: sha(ROOT/p) for p in files},
}
out = ROOT/'art/encounters/greene-battle-cycle-integration-20261117.json'
out.write_text(json.dumps(record, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
print('Recorded', len(files), 'Greene lifecycle files; Java and game execution remain deferred')
