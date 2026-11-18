"""Record the independent segment death integration without executing Java."""
from pathlib import Path
import hashlib
import json
import re

ROOT=Path(__file__).resolve().parents[1]
REF=Path('D:/PrototypeReference/p1')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
paths=[
    'src/main/java/com/codex/blacklight/combat/GreeneSegmentDeath.java',
    'src/main/java/com/codex/blacklight/entity/GreeneSegmentCorpse.java',
    'src/main/java/com/codex/blacklight/entity/EncounterMob.java',
    'src/main/java/com/codex/blacklight/client/model/GreeneSegmentCorpseModel.java',
    'src/main/java/com/codex/blacklight/client/renderer/GreeneSegmentCorpseRenderer.java',
    'src/main/java/com/codex/blacklight/registry/EncounterEntities.java',
    'src/main/java/com/codex/blacklight/client/ClientClawEvents.java',
    'src/main/java/com/codex/blacklight/network/ModNetwork.java',
    'src/main/resources/assets/blacklight/geo/elizabeth_greene_segment_corpse.geo.json',
    'src/main/resources/assets/blacklight/models/encounter_elizabeth_greene_source.json',
    'src/test/resources/greene-skin-reference.json',
    'src/test/java/com/codex/blacklight/combat/GreeneSegmentDeathTest.java',
    'src/greeneTest/java/com/codex/blacklight/gametest/GreeneSegmentDeathGameTest.java',
    'tools/build_greene_segment_corpse.py','tools/rebind_greene_source.py',
    'art/encounters/greene-segment-death-source.json',
]
record={
    'schema':1,'date':'2026-11-17','state':'Code and assets integrated in both loaders; no compilation or runtime acceptance',
    'source':{
        'record':'art/encounters/greene-segment-death-source.json',
        'privateProofSha256':sha(REF/'private/greene-segment-death-proof.json'),
        'geckoStaticProofSha256':sha(REF/'private/greene-segment-corpse-gecko-static.json'),
        'death':'mother_fig Health<=0 -> GrabbedDetach -> ref40 -> per-template Death with CopyDrawable=true -> segment_corpse_death ref46 -> final empty-template Death.',
        'animation':'mother_segment_1_die has96frames at30fps. First20..66 fits1.533329963684082s with.2666670083999634s blend-in; second66..95 uses.6000000238418579 speed. Total3.1444410108s maps to63 observed MC ticks.',
        'retraction':'Slug bones retract in the authored clip. No Motion_Root channel exists and the main death chain does not reach the optional ragdoll branch.',
        'crater':'ManageLifetime slot314aa6574a562515 belongs to Crater, absent from segment templates. It is not used as corpse lifetime.',
    },
    'implementation':{
        'break':'Capture named pose, mother position and body yaw before BROKEN and final collapse. Commit broken ownership before addFreshEntity; old damage work stops after reload/restoration/removal callbacks.',
        'independence':'New nonliving entity has no owner reference, movement, collision response, loot or XP. Later mother movement, pose, collapse and sink cannot drive it.',
        'pose':'Deep copy107bone rows. Blend from actual break pose into the source death track; recompute reciprocal parent scales after blending. Render only the matching ten Slug cube leaves.',
        'geometry':'Separate Gecko model resource retains every helper/bind and exactly the30 source-bound segment cubes. It prevents corpse visibility edits from contaminating the live mother baked model.',
        'facing':'Override both loader-specific GeoEntityRenderer.applyRotations signatures to use saved Entity yaw. The library otherwise supplies zero bodyYaw for non-LivingEntity.',
        'controllers':'Static inspection of both cached Gecko jars confirms setCustomAnimations still runs with an empty controller set; no fabricated default controller was added.',
        'culling':'Use current/next posed segment world bounds and explicit16chunk visual distance; the noncolliding.1block registration box must not cull the full skin.',
        'save':'Persist segment, observed DeathAge and named float-bit channels. Load validates every row, rebuilds relative client start time and resumes remaining observed ticks without unloaded-time catch-up.',
        'protocol':'Initially added in83/43; current standalone-85-hydra-thermal / forge-1.20.1-45-hydra-thermal includes the registration and renderer. Matching clients required.',
    },
    'adaptationsAndRemaining':[
        'Fixed copied world root is an explicit MC presentation adapter. Native corpse physics actor displacement and exact CopyDrawable/share-pose unlink internals are not reproduced.',
        'Health ownership still leaves the MC attached set immediately on break; exact native handle-generation retirement timing is not proven by zero health or visual death.',
        'Geometry remains rigid cuboids following original joints, with existing authored materials; original blended skin is not reconstructed.',
        'Source death emitter and audio metadata is retained but original effects, waveforms and late-spawn scheduling are not ported. No invented ballistic throw or ragdoll replaces the authored death curve.',
        'Real-client transition continuity, multiple corpses, full-screen presentation and save/unload behavior require later player observation.',
    ],
    'validation':{
        'staticOnly':True,'compilation':'not run','unitTests':'written, not run','gameTests':'written, not run','client':'not launched','installedRelease':'none',
        'unitMethodsWritten':len(re.findall(r'@Test\b',(ROOT/paths[11]).read_text(encoding='utf8'))),
        'gameTestMethodsWritten':len(re.findall(r'@GameTest\(',(ROOT/paths[12]).read_text(encoding='utf8'))),
        'fixtureBoundary':'The submerge-independence case explicitly loads an artificial active sink. Natural63tick corpse death finishes before125tick collapse, so this is not claimed as normal simultaneous playback.',
    },
    'snapshots':{p:sha(ROOT/p) for p in paths},'fullGoalComplete':False,
}
out=ROOT/'art/encounters/greene-segment-death-integration-20261117.json'
out.write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Recorded independent segment death, source/adapter boundaries and pending checks; no Java executed.')
