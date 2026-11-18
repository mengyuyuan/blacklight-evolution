"""Record statically decoded source evidence; never execute Lua, Java or game code."""
from pathlib import Path
import hashlib
import json

ROOT=Path(__file__).resolve().parents[1]
REF=Path('D:/PrototypeReference/p1')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf8'))
motion=read(REF/'private/greene-ai-complete.json')['records']
lua=read(REF/'private/greene-mission-lua.json')['records']
def track(offset):
    r=next(r for r in motion if r['source']=='comparison/mother_fig.p3d.rz' and r['offset']==offset)
    return {k:r[k] for k in ['offset','type','owner','fields']}
def script(offset):
    r=next(r for r in lua if r['offset']==offset)
    return {k:r[k] for k in ['offset','owner','sha256','luaBytes']}

record={
    'schema':1,'date':'2026-11-17',
    'state':'Recurring battle lifecycle, source health policy and brood ownership integrated; initial intro and full mission remain incomplete; not compiled or runtime accepted',
    'sources':{str(p.relative_to(REF)):sha(p) for p in [
        REF/'input/comparison/mother_fig.p3d.rz',
        REF/'input/remote-metadata/art/missions/e08m04/e08m04_fig.p3d.rz',
        REF/'input/prototypeenginef.dll',
        REF/'private/greene-mission-lua.json',REF/'private/greene-mission-fig-complete.json',
        REF/'private/greene-mission-state-graph.json',REF/'private/greene-mother-health-proof.json',
        REF/'private/greene-puppet-animation-exit-proof.json',
        REF/'private/greene-mission-stage-reachability-proof.json',
        REF/'private/greene-submerge-emerge-lifecycle-proof.json',
    ]},
    'collapseTimer':{
        'track':track(9728),
        'registration':'101c78c0: time getter/setter10015393->101c5460 stores +0x1c; randomRange1003ec48->101c5490 stores +0x18.',
        'runtime':'101290f0 loads +0x1c as center and +0x18 as amplitude, calls10127eb0, then posts decrementing timer event0x23/subtype5.',
        'distribution':'10127eb0 builds float bits0x40000000|(rng&0x7fffff), subtracts3.0 at10d94f28, multiplies range and adds time. Thus signed uniform [-1,1), not [0,1) and not +/-26.6667.',
        'decrement':'Timer state10066c80 subtracts dt and clamps at zero; recovery Node10632 accepts timer<=0 OR UndergroundState.',
        'origin':'Timer starts at collapse Node9436 entry, not at down Node11420 entry.',
        'quantization':'Server generates23 random bits with its own RNG, evaluates source float formula, rounds deadline up to20Hz. Native RNG sequence is not reproduced.',
        'sourceRecoveryStartTicks':[514,554],
        'sourceDownTicks':[389,429],
        'collapse':track(9560),'down':track(11484),'rise':track(10784),
    },
    'missionEvidence':{
        'constants':{'stunDamageFraction':.08500000089406967,'vulnerableDamageLimit':.25999999046325684,
                     'submergeWaitSeconds':20,'segmentHealthPerWave':[500,700,900,1000],
                     'hydraRespawnSeconds':20,'segmentToMotherDamageScale':.75,
                     'stageMinHealthFractions':[.655,.31,-.035],'lastStageMinHealth':500},
        'stunnedEntry':{'script':script(77396),'behavior':'Increment waveCount; snapshot initialStunnedHealth BEFORE stun damage; wait while player Devastator; make core damageable. Apply8.5%max if that damage+50<current health, otherwise halve current health.'},
        'stunnedDamage':{'script':script(78348),'behavior':'If core survives and initialStunnedHealth/max-currentHealth/max>=.26, wait for Devastator completion then set Underground=true. The automatic8.5% is included in the26% comparison, not additional. No hard damage clamp appears in this handler.'},
        'nativeRecovery':'Rise Node10632 sequences to11168; Lua11232 sets Underground=true and event11364 enters mission underground state78972. Submerge event38400 has the same hash but does not restart an already-completed transition.',
        'undergroundEntry':{'script':script(79012),'behavior':'MotherSubmerge disables core damageability; start20-second emerge timer at state entry, including the native sinking interval.'},
        'interimWave':{'script':script(79660),'behavior':'Wave min/max/total3 UndergroundBrawler at authored TimesSquare locators with underground spawn branch. An unrelated WAVEONETOTAL6 variable is not this wave count.'},
        'emerge':{'script':script(80964),'behavior':'Spawn segments, wait one frame, spawn wave-indexed hydras, MotherEmerge(justCore); powerLevel increases up to3. Segment health table is bounded to its final entry. Re-enable lethal core health floor when health<=500.'},
        'sourceMotion':{'submerge':track(37520),'sink':track(37692),'emerge':track(40876),'raise':track(41004)},
        'outerHealthFloors':'Mission Lua18400/24208/36032/41392 sets minHealth to current/max during gates. Lua23740/26440/37112 sets stage floors;42120 releases final floor. These cannot be replaced by the damage fraction alone.',
    },
    'integration':{
        'newCycles':'useSourceExposureTimer defaults true. Existing custom exposedHoldTicks remains available only when it is explicitly false.',
        'save':'GreeneBattle saves phase-local observed age, pending floor/stun/recovery, root flags, wave identity and relative handoff blend. Legacy DOWN/RISING is migrated without retroactive stun debit. Direct and complete entity reloads release old root ownership before applying incoming state.',
        'contact':'Ordinary damage does not reset or redraw the timer. Live collapse/down/rise/sink/underground/emerge sampling is shared by rendering and receiving geometry; root movement is actual entity Y, not a visual offset.',
        'tests':'Timer/health/motion unit checks and full-cycle, floor, nested bypass and reload GameTests are written only; see battle-cycle and GameTest review records.',
        'coreRouting':'Per-event .6 child redirect precedes core immunity. DOWN entry owns one ordinary scripted debit; accepted own damage can request early recovery. Platform final-damage hooks and the actuallyHurt health-write mixin enforce source floors before fatal handling.',
        'cycle':'GreeneBattleCycle connects collapse125, DOWN, rise50, sink59, underground timer400 including sink, emerge wait6 and rise clip163. Actual root travels20 source units. Targetability returns at27 ticks and a valid attack may take over at80.',
        'brood':'Three total Hunters per underground wave; four mission-owned Hydras at repeat emergence. Desired buried/raised state, death receipts, respawn and accepted owner disposal persist independently of chunk loading.',
    },
    'healthReceiverEvidence':{
        'native':'MotherHealth102c0940 invokes child redirect102c07a0 before parent core hurt10834ad0. Child dispatch ignores the core immunity counter and floor. The parent applies both afterward.',
        'templates':'Core initial/max/current health6000. Segment templates650 are overwritten by mission500/700/900/1000.',
        'redirect':'Raw incoming damage / valid child-handle count * .60000002384, delivered with the original extended damage type/origin. Count is handle validity, not positive child health. alwaysDoOwn=true then gives original full incoming damage to the core own-damage path, not remaining40%.',
        'damageable':'go_SetDamageable(false) increments and true decrements signed immunity counter+0x20; it is not an idempotent boolean setter.',
        'floor':'SetMinHealth writes member+0x18 without changing current health; later own hurt applies max(old-modifiedDamage*damageScale,minHealth). No floor clamp to0 is inferred.',
        'mission':'Stunned entry restores damageability, clears child handles then sends ordinary scripted damage. RestoreSegments reattaches existing segments without adding health children or refilling health.',
        'unusedHelper':'ApplyDamageToSegments uses declared .75 and fixed divisor3, but no invocation was found in this mission; do not make it a permanent reflection rule.',
        'scope':'Receiver ordering, bounded core-to-child route and normal-path script health policy are integrated. Every upstream weapon/search dispatch to an undamageable actor and native death-handle retirement remain incomplete.',
    },
    'exitClipEvidence':{
        'nativeClamp':'Puppet config108da7fb->10941ca0->10941680 clamps EndFrame49 to frameCount-1=44. Actual source chunk4028702 has45 frames, fps30, loop0.',
        'completion':'10941920/10941da0 advance fps*speed*dt;10941b40 marks complete at current>=44;Puppet tick108daca0 ends when that bit is observed.',
        'clock':'Nominal44/(30*.5)=2.933333seconds, proposed20Hz boundary59ticks. Positive blendIn3 does not add3seconds to the frame clock.',
        'scope':'Source node37456..38504 has no Sequence. Clip completion is not proof of complete node lifetime or a fixed jump to the next serialized underground bank.',
    },
    'outerMissionReachability':{
        'privateProof':'private/greene-mission-stage-reachability-proof.json',
        'normalPath':'10 -> 19 -> 30 -> 38. First collapse releases floor to .655*max. The first repeated MotherEmerges belongs to19/cavalry camera; the later30 state cannot replay it. Second collapse uses STAGE3=-.035*max/checkHealth=false. The following emerge enters38; its collapse waits3seconds before minHealth0.',
        'unusedStage':'16 uses floor.31 but no initial MotherEmerges producer was found on the normal new-mission path. Do not force it into the second round. External events/checkpoint restoration remain explicit exceptions.',
        'eventScope':'scenario_PostEventString and motion ScenarioStringAction broadcast to scenario-tagged receivers. ScenarioStringCondition reads the current queued event, not a latched history. fsm_PostEventString is local to its FSM.',
        'stagesConstant':'STAGES=4 occurs in the HUD CalculateMotherHealth formula, not as a four-round exposure quota.',
    },
    'submergeEmergeEvidence':{
        'privateProof':'private/greene-submerge-emerge-lifecycle-proof.json',
        'timer':'20seconds begins at mission underground state89 entry, including sinking. It is independent of animation completion and spatial underground state.',
        'exit':'Exit44/(30*.5)=2.933333seconds. Track low2 flag bits make ManageLifetime(t3), StopSound(t4) and TemporaryTargetable background actions; they do not force a4second node hold. ManageLifetime registers the Crater prop, not Mother destruction.',
        'branchSelection':'Mother MotionTreeBehaviour normally reselects from root after its ActionList finishes. The next serialized bank is not an implicit Sequence.',
        'spatialState':'TunnelingBehaviour goes Under=3 toward ground-20; actualY-goalY<.75 enters Underground=1. Starting emergence samples ground and places the actor at ground-20, then approaches ground+offset0; actualY-goalY>-.75 enters Outside=0.',
        'targetable':'Sink t.8 marks NonTargetable, End=DontChange. Emerge action0..1.33333 restores targetability on its end callback, including interruption. It does not wait for the full clip.',
        'emerge':'Spawn-prop Opportunity starts at.266667 and waits while PlayerDevastator. Clip0..244@30fps lasts8.133333seconds; +20Y movement ends1.33333, heightmap refresh2.83333. At4seconds an event opportunity may hand off to a valid attack before the clip completes.',
        'alignment':'ai_AdjustPositionOverground(true) only aligns already-Outside state; it does not force an unfinished ComingOut state to complete.',
        'implementationBoundary':'First and recurring rises use these source gates. Appendages restore at repeat emergence using500/700/900/1000 scaled by actual maxHealth, not at the end of RISING. Emergence Hit is integrated with explicit MC sampling/receiver adapters. Full opening mission cinematics/forced-roar flags, Crater and complete task choreography remain absent.',
    },
    'remaining':[
        'Initial source rise is integrated. Original opening cinematic/forced-roar context, complete native emergence Hit solver/impulses/receivers, Crater visuals/lifetime and original Hunter underground birth remain incomplete.',
        'Full TimesSquare mission cameras, dialogue, locator placement, AI unlock flags and external/checkpoint branches are not ported. Standalone recurring path omits camera waits explicitly.',
        'Complete upstream hit dispatch, native segment-handle replacement/death retirement and signed immunity-counter consumers remain incomplete.',
        'Independent source segment death animation is integrated; original death FX/audio and native corpse physical root remain incomplete. This recurring standalone battle is not a complete e08m04 recreation.',
        'No compilation, unit/GameTest execution, game/client launch, original binary execution or media playback.',
    ],
    'privateDisassembly':{name:sha(REF/'private'/name) for name in [
        'rapid-runtime-10127eb0.txt','rapid-runtime-101290f0.txt',
        'rapid-runtime-101c5460.txt','rapid-runtime-101c5490.txt','rapid-refs-11254b8c.txt',
    ]},
}
path=ROOT/'art/encounters/greene-recovery-source-20261117.json'
path.write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
print('Recorded source timer and mission lifecycle boundaries; no Java/Lua/game execution')
