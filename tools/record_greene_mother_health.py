"""Static provenance only. Does not run Java, tests, original binaries or game code."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
REF = Path('D:/PrototypeReference/p1')
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

record = {
    'schema': 1, 'date': '2026-11-17',
    'state': 'Core-to-appendage routing integrated in both loaders; not compiled or runtime accepted',
    'sources': {str(p.relative_to(REF)): sha(p) for p in [
        REF/'input/prototypeenginef.dll', REF/'input/comparison/mother_tod.p3d.rz',
        REF/'input/remote-metadata/art/missions/e08m04/e08m04_fig.p3d.rz',
        REF/'private/greene-mother-health-proof.json', REF/'private/greene-mission-lua.json',
    ]},
    'receiver': {
        'order': 'MotherHealth102c0940 calls redirect102c07a0 before own core hurt10834ad0. Core immunity does not suppress child dispatch once the event reaches this receiver.',
        'share': 'Compute raw incoming / initial valid handle count * .6000000238418579 using float arithmetic, in that order. Rejected or now-deleted recipients do not redistribute their share.',
        'ownDamage': 'MotherCore alwaysDoOwn=true. Its own path receives full raw incoming damage, not the remaining40%. Native core immunity/floor still apply separately.',
        'coreImmunity': 'Mission initialization and underground entry disable core damageability. Stunned entry77396 enables it after native collapse125ticks, not on collapse event9688.',
        'sourceScope': 'Receiver semantics are decoded. Not every original weapon/search dispatch to an undamageable Mother has been proven.',
    },
    'integration': {
        'entry': 'EncounterMob.hurtGreeneCore runs for ordinary core/body hits. Direct appendage contacts still use hurtPart and never recurse into core redirection.',
        'children': 'Snapshot attached weak slots before dispatch; keep cooling slots in that divisor. Each actual hurtPart retains its own10tick cooldown, protection/faction checks, break state and saved armor.',
        'core': 'Guarded/collapsing core rejects own damage while attached limbs may accept their shares. DOWN/RISING calls vanilla super.hurt with the full original amount. Accepted child harm can make the MC hurt call succeed even if the core is immune.',
        'removedShortcut': 'The old .08 body chip multiplier and ordinary incoming>=5*maxHealth shortcut no longer bypass appendage/collapse progression.',
        'commands': 'Minecraft BYPASSES_INVULNERABILITY damage retains its ordinary superclass path; it is an explicit administrative/game-rule adapter.',
        'persistence': 'GreeneBattle owns synced/saved cycle, health policy and wave identity. Partial armor and individual cooldown remain authoritative; legacy90 armor is migrated proportionally to the source-scaled segment maximum.',
        'physicalFeedback': 'Redirected children update their health without inventing separate physical impacts. Accepted core redirect sends one original-contact effect; direct limb strikes keep one limb effect. Rejected shares do not emit an impact.',
        'healthUnits': 'HealthBasis preserves partial armor, minimumHealth and pre-stun snapshot proportions across actual max-health changes. Saved armor is clamped using saved basis before current-max rebasing; ordinary raw damage multiplies health units only once, and the scripted actual-HP debit divides units before hurt.',
        'visibleChildren': 'Original Mother_Segment_1/2/3 render corresponding Slug skins with skeletons matching MotherCore. Spawn/restore attach then SharePose. Legacy independent weakpoint-ring geometry/clocks have been replaced by actual Slug-joint region cuboids; three logical health-child identities remain.',
    },
    'adaptations': [
        'Virtual Minecraft appendages detach immediately when broken. The native denominator checks handle generation/object validity, not health; native dead-handle retirement delay is not reproduced.',
        'Minecraft configured core maximum remains the balance anchor (default1400). Original6000 defines the scale for500/700/900/1000 segments,8.5% stun, source50 margin and500 low-health release.',
        'Source damageable is a signed counter. MC uses existing phase edges/state to gate core hurt, not repeated non-idempotent counter writes.',
        'Protection, team filtering, damage hooks and child10tick cooldown are existing MC policies, not complete native damage receiver behavior.',
    ],
    'deferredGameTests': [
        'coreHitSplitsAcrossAttachedLimbsBeforeItsOwnImmunity',
        'aCoolingLimbStillCountsInTheSameIncomingDamageDivisor',
        'detachedLimbLeavesTheMinecraftHealthChildSet',
        'evenHugeCoreHitsMustFinishCollapseBeforeOwnDamage',
        'redirectedHealthIsOnePhysicalContactRatherThanThreeFakeImpacts',
    ],
    'remaining': [
        'Normal-path scripted8.5% damage,26% early recovery and outer floors are connected to the recurring lifecycle; runtime ordering and player experience remain unverified.',
        'Native death-handle retirement, every source immunity-counter consumer, opening mission choreography, original blended Slug skin and original impulse/effect receivers remain incomplete.',
        'No compilation, unit/GameTests, player validation, original binary execution, playback or user-save changes.',
    ],
}
out = ROOT/'art/encounters/greene-mother-health-integration-20261117.json'
out.write_text(json.dumps(record, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
print(str(out))
