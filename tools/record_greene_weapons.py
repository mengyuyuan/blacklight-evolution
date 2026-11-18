"""Record existing local FIG/META evidence without executing the original engine.

The native Mouth supplies live origin/direction. All thirteen Shoot tracks now
drive six programs; explicit MC adapters and remaining original-engine gaps stay visible.
"""
from pathlib import Path
import hashlib
import json

ROOT=Path(__file__).resolve().parents[1]
REF=Path('D:/PrototypeReference/p1')
def read(path):return json.loads(path.read_text(encoding='utf8'))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

rows=[r for r in read(REF/'boss-fig-audit.json')['records']
      if r['source']=='comparison/mother_fig.p3d.rz' and r['owner'].startswith('mother_core/')]
shots=[r for r in rows if r['type']=='Shoot' and 'fields' in r]
ammo=[r for r in read(REF/'private/missile-profiles-decoded.json') if r['name'].startswith('motherVomit')]
users=[r for r in read(REF/'private/weapon-users-decoded.json') if r['name'].startswith('motherVomit')]
motion=read(ROOT/'src/main/resources/assets/blacklight/models/encounter_elizabeth_greene_source.json')
records={r['offset']:r for r in rows}
report={
 'schema':2,'date':'2026-11-17','state':'six source weapon programs connected; compilation and gameplay deferred',
 'userConstraint':'Continue code only. Compilation, tests and native gameplay are deferred.',
 'sources':[{ 'path':str(p),'sha256':sha(p)} for p in [REF/'input/prototypeenginef.dll',REF/'input/comparison/mother_tod.p3d.rz',REF/'input/comparison/mother_fig.p3d.rz',REF/'private/comparison/mother.p3d']],
 'sockets':motion['sockets'],'socketProvenance':motion['socketProvenance'],
 'sourceSkeleton':{'name':'mother_core_skeleton','offset':0x41167e,'mouthIndex':15,'mouthParent':9,'headIndex':9,'headParent':8,'headParentName':'Spine_4'},
 'retargetBoundary':{
  'reference':motion['reference'],
  'finding':'Original skin palettes distinguish outer Head/Mouth/Horn from the embedded human Body_Root/Body/Chest branches. The authored cubes now follow these native joint owners, with local ROT/TRAN/SCL and verified P1 parent-scale compensation.',
  'decision':'The live six-program controller uses all thirteen original Shoot tracks and their actual VomitGun slots. Animation, hurt skin and full-scale Mouth position share one program pose; direction uses the proven attachment orthonormalization. Anti programs use slot10 and require the matching occupied hostile hull.',
  'meshAdaptation':'All 150 authored cubes and UV values are retained. Rigid cuboid ownership approximates original blended vertex weights; client visual acceptance is deferred.',
  'records':['art/encounters/greene-native-bind-correction.json','art/encounters/greene-native-scale-source.json']},
 'ammo':ammo,'users':users,'shootTracks':shots,
 'targetGates':[records[n] for n in [13184,13384]],
 'executeTracks':[r for r in rows if r['type']=='Execute' and r.get('fields') and any(s['owner'].startswith(r['owner']+'/') for s in shots)],
 'nativeStatic':{
  'GrabAutoSpawn':'0x102c84c0 orders grabSlot, spawnTemplate, parentJoint, parentPositionOffset, parentRotationOffset, childJoint, childPositionOffset, childRotationOffset, aICharacter, initialPlaybackNode.',
  'ShootAction':'0x101122e0 converts FixedDirectionDirection degrees with constant 0x10d93e54, calls 0x107bb870, negates matrix third row, resolves requested space with 0x10259660, normalizes, then sends a weapon firing event. FixedDirection=false takes a separate targeting event path.',
  'EulerForward':'For x,y,z radians, negated third row is [-(cos(z)*cos(x)*sin(y)+sin(z)*sin(x)), -(sin(z)*cos(x)*sin(y)-cos(z)*sin(x)), -cos(y)*cos(x)].',
  'ShootEnd':'0x10111b00 sends weapon stop and restores saved ammo/user profiles.',
  'closedThisPass':'Weapon-context space mapping, empty-joint fallback, final attachment orthonormalization, one-attempt-per-native-update cadence, and six MissileBehaviour flight profiles now have instruction-level evidence in the linked records.',
  'remaining':'Original AI emotional-state producer, accuracy/error distribution, projectile-template final-action bodies, spin/visual assets and force delivery are not fully reconstructed.'},
 'implementationRecords':['art/encounters/greene-missile-flight-source.json','art/encounters/greene-mouth-attachment-source.json','art/encounters/greene-program-integration-20261117.json','art/encounters/projectile-sphere-contact-20261117.json'],
 'knownLiveGaps':[
  'All four on-foot states are reachable through an explicitly identified MC round-robin adapter, not the unported original emotional-state producer.',
  'MC samples source Shoot windows at 20 Hz with one attempt per socket/update; source shotgun rate150 is frame-limited, not replaced by an invented multi-pellet loop.',
  'Original accuracy/error sampling and projectile-template bodies are missing: no invented blast radius, Euler spread, spin or original audiovisual claim.',
  'Living damage preserves source player/NPC ratios through explicit .06 MC balance; mechanical hits use source NPC damage with the existing armor/protection adapter. Source impulse/friendly-fire semantics are not transplanted.',
  'Proximity ends the missile; direct single-target damage is delivered only to a visible actual target surface, a documented MC fallback until original final-action template bodies are found.',
  'Aircraft use their current body envelope; animated-body collision samples the current pose, not a continuous moving-target sweep. Client visuals, controls and balance still require gameplay validation.'],
 'verification':'Static file/byte/transform review only. No Java compilation, unit/GameTests, original-DLL execution or game/client launch.'
}
destination=ROOT/'art/encounters/greene-weapon-source-20261117.json'
destination.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print('Recorded',len(motion['sockets']),'sockets,',len(ammo),'ammo profiles,',len(users),'users,',len(shots),'Shoot tracks')
