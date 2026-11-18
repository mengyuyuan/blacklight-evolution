"""Static integration manifest. Never executes Java, tests, the client or original DLLs."""
from pathlib import Path
import hashlib
import json

ROOT=Path(__file__).resolve().parents[1]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
files=[
 'src/main/java/com/codex/blacklight/entity/EncounterMob.java',
 'src/main/java/com/codex/blacklight/entity/GreeneProjectile.java',
 'src/main/java/com/codex/blacklight/registry/EncounterEntities.java',
 'src/main/java/com/codex/blacklight/client/ClientClawEvents.java',
 'src/main/java/com/codex/blacklight/client/model/SourceBossPose.java',
 'src/main/java/com/codex/blacklight/network/ModNetwork.java',
 'src/main/java/com/codex/blacklight/military/vehicle/VehicleHullShape.java',
 *['src/main/java/com/codex/blacklight/combat/'+n+'.java' for n in ['GreeneWeaponProgram','GreeneWeaponController','GreeneWeaponTarget','GreeneMissileFlight','GreeneWeaponCadence','GreeneMouth','GreeneMotion','ProjectileSphereContact','SourceBossAction','EncounterTactics','GoliathGroundImpact']],
 'src/main/resources/assets/blacklight/models/greene_weapon_programs.json',
 'src/main/resources/assets/blacklight/models/goliath_ground_impact.json',
 'src/greeneTest/java/com/codex/blacklight/gametest/GreeneCycleGameTest.java',
 'tools/player-fix-unit.init.gradle',
 *['src/test/java/com/codex/blacklight/combat/'+n+'.java' for n in ['GreeneWeaponProgramTest','GreeneMissileFlightTest','GreeneWeaponCadenceTest','GreeneMouthTest','ProjectileSphereContactTest','GoliathGroundImpactTest']]]
programs=json.loads((ROOT/'src/main/resources/assets/blacklight/models/greene_weapon_programs.json').read_text())
report={
 'schema':1,'date':'2026-11-17','state':'code connected; uncompiled and untested at user request',
 'userConstraint':'Continue code first. No Gradle/Java compilation, tests, native DLL execution/emulation, client launch, save changes or media playback.',
 'scope':['Six Greene weapon programs / thirteen original Shoot tracks','Goliath front-slam world contacts','Common infected finite air tracking'],
 'greene':{
  'programs':[{'id':p['id'],'sourceExecute':p['executeOffset'],'slots':[s['slot'] for s in p['shots']],'seconds':p['action']['seconds']} for p in programs['programs']],
  'animation':'Synced program identity selects the same original clip slices for visible pose, receiving skin, muzzle position and direction. Existing move IDs and projectile ID are retained; greene_projectile is a separate new registration.',
  'firing':'Ten persistent per-socket cooldowns, one spawn attempt per 20 Hz update, successful spawn only consumes cooldown; original fire-rate is not an inner pellet batch. Half-open Shoot windows have a documented 20 us boundary tolerance.',
  'direction':'Actual weapon GameObject context, empty-joint fallback and attached Mouth basis established by native instruction traces. Parent scale/shear affects position; original Gram-Schmidt final attachment basis determines direction.',
  'flight':'Six native profiles: no vanilla gravity/drag; braking, acceleration, delayed tracking, lateral friction, lifetime and displacement range are retained. A synced float vector avoids the vanilla velocity-packet 3.9-block/tick cap for source 80-unit/s shots.',
  'collision':'Source thickness is a swept-sphere radius. Analytic sphere-to-affine-cuboid first contact checks block shapes, animated regions and articulated vehicle boxes. Real blocks win ties; owned body parts do not block their own missile. Entire swept volume must already be loaded and inside the border.',
  'proximity':'Strict original-target-origin trigger ends the missile. Until original projectile final-action template bodies are available, a stated MC fallback delivers single-target damage only through an unobstructed real target surface; it never creates an area blast. Mounted targets drop stale living guidance.',
  'acquisition':'Four on-foot states use an explicit MC cycle. Tank/aircraft attacks require the matching occupied hostile hull and crew protections. Empty parked hulls and unrelated on-foot targets cannot satisfy anti-vehicle gates.',
  'damage':'Living base damage = original player/NPC field * .06 (preserves previous rifle base6 at source100); mechanical hits use source NPC field through existing armor/protection rules. Native force and friendly-fire behavior are not claimed.',
  'presentation':'Existing slime item and licensed biological Foley are placeholders. One windup cue and one release cue per newly firing Shoot track, coalesced across simultaneous ports. Original missile meshes/trails/spin/audio remain unresolved.',
  'lifecycle':'Breaking defenses, disabled actor, removed or invalid target cancel remaining requests. Already released valid missiles retain their lifetime. Socket cooldowns and on-foot selector save/load separately; attacks resume idle after load.'},
 'goliath':{'integration':'GoliathGroundImpact runs after the posed body update on every committed tick, including 39/40/41 after contact38. The fake root-centered CRIT ring/golem landing cue is excluded for source front slam. A target killed by contact38 does not cancel the follow-up ground effect; actor death/disable/exposure still cancel.',
             'sourceFix':'The front-slam local damage origin now applies its required source-X to model-X mirror. Greene has a different established axis contract and is not mirrored again.',
             'remaining':'Native giant fist/support-plane penetration and other moves/AI remain unresolved.'},
 'commonInfected':'Air tracking now follows the original bounded 3D target prediction/rotation and permanently stops after the target is passed; wrist contact uses the visible yaw interpolation. Initial launch physics and missing source clips remain MC adapters.',
 'deferred':{'javaCompilation':'not run','unitTests':'written/queued only','gameTests':'18 GreeneCycle cases present, not run','clientAndPlayerAcceptance':'not run','releaseInstallation':'not done'},
 'sourceRecords':['art/encounters/greene-weapon-source-20261117.json','art/encounters/greene-missile-flight-source.json','art/encounters/greene-mouth-attachment-source.json','art/encounters/projectile-sphere-contact-20261117.json','art/encounters/goliath-ground-impact-provenance.json','art/encounters/common-infected-jumpto-source-20261117.json'],
 'fullGoalComplete':False,'snapshots':{p:sha(ROOT/p) for p in files}}
(ROOT/'art/encounters/greene-program-integration-20261117.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print('Recorded',len(files),'static integration snapshots; no Java/test execution')
