"""Record both loaders' distance-driven gait, radio gesture and standing damage evidence."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

QA=Path('D:/CodexCache/military-motion-qa')
ROOTS={'neo':Path('D:/blacklight-evolution'),'forge':Path('D:/blacklight-evolution-forge-1.20.1')}
CASES=('walk','run','radio','hit','seat_interrupt')
CLIENT=['src/main/java/com/codex/blacklight/'+s for s in (
    'animation/P1MilitaryMotion.java','client/model/BlackwatchGait.java','client/model/BlackwatchRigPose.java',
    'client/model/BlackwatchLegPose.java','client/model/BlackwatchModel.java','client/model/BlackwatchReferenceProfile.java')]
SERVER=['src/main/java/com/codex/blacklight/'+s for s in (
    'entity/MilitarySoldier.java','military/MilitaryTactics.java','military/MilitaryCover.java',
    'military/MilitaryFireControl.java','military/MilitaryGroundNavigation.java','military/MilitaryFormationSystem.java',
    'military/MilitaryWorkBudget.java','city/MilitaryHomeSystem.java','entity/BlackwatchHelicopter.java')]
CHECKS=['src/test/java/com/codex/blacklight/client/model/'+s+'.java' for s in (
    'P1MilitaryMotionTest','BlackwatchRigPoseTest','BlackwatchReferenceProfileTest','BlackwatchGaitTest')]+[
    'src/motionTest/java/com/codex/blacklight/gametest/MilitaryMotionGameTest.java',
    'src/motionVisual/java/com/codex/blacklight/client/MilitaryMotionVisual.java',
    'tools/military-motion-test.init.gradle','tools/military-motion-server.init.gradle','tools/military-motion-visual.init.gradle']

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def evidence(path):
    return {'path':str(path),'sha256':sha(path)}

def load(path):
    return json.loads(path.read_text('utf-8-sig'))

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reviewed',action='store_true')
    args=parser.parse_args()
    if not args.reviewed:
        parser.error('Inspect all three selected-frame sheets for each loader and each source/fallback mode first')
    private=Path('D:/PrototypeReference/p1/private/military-motion.json')
    rejected=load(QA/'rejected-hit.json')
    report={'schemaVersion':1,'recordedAt':datetime.datetime.now().astimezone().isoformat(),'overallGoalComplete':False,
        'scope':'Actual horizontal travel drives per-actor walk/run phase; radio keeps the firearm and reaches the earpiece; accepted damage starts a bounded standing reaction; vehicle seats take priority.',
        'privateSource':{**evidence(private),'clips':len(load(private)['clips']),'copiedToRepositoryOrPackagedByThisCheckpoint':False},
        'baseline':load(QA/'before.json'),'baselineSha256':sha(QA/'before.json'),
        'rejectedNativeHit':{**evidence(QA/'rejected-hit.json'),'contactSheet':evidence(QA/'rejected-hit.jpg'),
            'beforeHeadY':{p:rejected[p]['rejectedFrame']['head'][1] for p in ROOTS},
            'reason':rejected['reason'],'resolution':rejected['resolution']},
        'sourceCatalog':evidence(QA/'source-catalog.jsonl'),
        'correctedFallbackSole':{'beforeMaximumSoleY':load(QA/'rejected-fallback-sole.json'),'groundPlaneY':24.05,
            'resolution':'Lift a penetrating ankle by the complete rotated boot sole depth and solve the existing 6+5 leg chain; hip sockets, upper body and boot orientation remain fixed.'},
        'correctedProjectileFixture':load(QA/'missed-arrow-fixture.json'),
        'correctedAttackerMemoryFixture':{p:load(QA/f'{p}-expired-attacker-fixture.json') for p in ROOTS},
        'limits':[
            'Source walk/run/radio are retargeted onto the native rig, not a complete 67-joint skin or original-game frame-for-frame reproduction.',
            'The decoded source head-hit clip ends in a fall and is deliberately not used for standing damage. This checkpoint uses an authored standing flinch, not source knockdown.',
            'Locomotion removes source root travel from the feet and derives phase from actual client-interpolated horizontal movement. It does not change production navigation urgency or movement speed.',
            'Native walk/run scenarios use real MOVE orders with movement attributes 0.25 and 0.80, not fixed client bone scripts. The faster actor reaches its target and stops during the run case.',
            'Five native scenarios use an 80-health soldier, temporary setup NoAI and stationary high-health enemies. The hit actor remains NoAI until its first real accepted hit; three arrows come from Skeleton.performRangedAttack, with fixture-only spread removed before vanilla collision/damage. Production AI and hurt logic are unchanged by this fixture correction.',
            'Server observations are unique sampled game ticks; client/server scheduling may skip ticks. Captures require the actor to have rendered in the same client tick, not every render frame.',
            'Eight selected frames per case are reviewed. Leg checks use the actor support plane, not per-foot terrain IK or exhaustive collision/self-intersection verification.',
            'The vehicle obscures the seated actor in the exterior camera. Actual actor render events verify seat priority and state reset; these images do not prove an unobscured cockpit pose.',
            'Muted disposable QA records sound events; it is not a subjective sound-quality or volume review.',
            'Server regressions predate the final client-only hit correction; freshness is checked against server behavior files. Native and unit checks cover the final client files.',
            'No player world, release JAR, installation or publication in this checkpoint. Task-created private configs are removed from both disposable clients after source captures.'
        ],
        'remaining':['Full-body source knockdown and recovery','Per-foot terrain support and body-weight refinement',
            'Broader moving-target, terrain and multiplayer choreography','Fear/retreat and alert-search behavior',
            'Remaining creatures, detailed models and sound listening in the active overall goal'],
        'platforms':{}}
    for platform,root in ROOTS.items():
        client_time=max((root/p).stat().st_mtime for p in CLIENT)
        server_time=max((root/p).stat().st_mtime for p in SERVER)
        sources={p:sha(root/p) for p in CLIENT+SERVER+CHECKS}
        build=Path('D:/CodexCache/creature-combat-qa')/('neo-build' if platform=='neo' else 'forge-render-build')
        units={}
        for cls,count in (('P1MilitaryMotionTest',10),('BlackwatchRigPoseTest',9),('BlackwatchReferenceProfileTest',6),('BlackwatchGaitTest',9)):
            path=build/'test-results/military-motion'/f'TEST-com.codex.blacklight.client.model.{cls}.xml'
            result=ET.parse(path).getroot()
            assert int(result.get('tests'))==count and all(result.get(k)=='0' for k in ('failures','errors','skipped')),(platform,cls)
            assert path.stat().st_mtime>=max(client_time,(root/f'src/test/java/com/codex/blacklight/client/model/{cls}.java').stat().st_mtime)
            units[cls]={'tests':count,**evidence(path)}
        logs={}
        for stage,count in (('motionServer',2),('militaryServer',18 if platform=='neo' else 19),('coverServer',9),('helicopterServer',32)):
            path=Path(f'D:/CodexCache/creature-combat-{platform}-{stage}.log');text=path.read_text('utf8',errors='replace')
            match=re.search(r'All (\d+) required tests passed',text)
            assert 'BUILD SUCCESSFUL' in text and match and int(match[1])==count,(platform,stage)
            assert path.stat().st_mtime>=server_time,(platform,stage,'stale server behavior')
            logs[stage]={'requiredScenarios':count,**evidence(path)}
        fixture=root/'src/motionVisual/java/com/codex/blacklight/client/MilitaryMotionVisual.java'
        native={}
        for mode in ('source','fallback'):
            folder=QA/f'{platform}-{mode}'
            assert (folder/'status.txt').read_text().startswith('PASS:')
            path=Path(f'D:/CodexCache/creature-combat-{platform}-motionVisual{mode.title()}.log')
            assert 'BUILD SUCCESSFUL' in path.read_text('utf8',errors='replace')
            assert min((folder/n).stat().st_mtime for n in ('frames.json','server.json','status.txt'))>=max(client_time,server_time,fixture.stat().st_mtime)
            frames=load(folder/'frames.json');rows=load(folder/'server.json');manifest=load(folder/'review-manifest.json')
            assert all(b['worldTick']>a['worldTick'] for a,b in zip(rows,rows[1:])),(platform,mode,'duplicate server ticks')
            cases={};identities=set()
            for case in CASES:
                server=[r for r in rows if r['case']==case];images=[r for r in frames if r['case']==case]
                ids={r['id'] for r in server};assert len(ids)==1 and not identities.intersection(ids);identities.update(ids)
                assert len(images)>=8 and {r['actor'] for r in images}==ids
                assert len(set(manifest[case]))==8 and all(any(r['image']==n for r in images) for n in manifest[case])
                standing=[r for r in images if not r['seated'] and r['gunVisible']];assert standing
                grip=max(r['mainGripError'] for r in standing);sole=max(max(r['right_leg_lowestSole'],r['left_leg_lowestSole']) for r in standing)
                assert grip<.15 and sole<24.065,(platform,mode,case,grip,sole)
                data={'actor':next(iter(ids)),'observedServerTicks':len(server),'worldTickSpan':server[-1]['worldTick']-server[0]['worldTick'],
                    'renderedCaptures':len(images),'maximumRunBlend':max(r['running'] for r in images),
                    'maximumMainGripErrorPixels':grip,'maximumSoleY':sole,
                    'reviewedImages':[{**evidence(folder/n)} for n in manifest[case]]}
                if case in ('walk','run'):
                    distance=((server[-1]['x']-server[0]['x'])**2+(server[-1]['z']-server[0]['z'])**2)**.5
                    assert distance>3;assert max(r['movementWeight'] for r in images)>.5
                    data['horizontalDisplacementBlocks']=distance
                if case=='radio':
                    assert any(r['radioStarted']>=0 for r in server) and server[-1]['radioStarted']<0
                    ear=min(r['earDistance'] for r in images if r['radioStarted']>=0)
                    assert ear<1.5,(platform,mode,'support hand never reached earpiece',ear)
                    data['minimumHandEarpiecePixels']=ear
                if case=='hit':
                    assert min(r['health'] for r in server)<80 and any(r['hitStarted']>=0 for r in server)
                    assert server[-1]['firedArrows']==3 and any(r['hitFromFixtureTarget'] for r in server)
                    reacted=[r for r in images if r['hitStarted']>=0];assert reacted
                    head=max(r['head'][1] for r in reacted);assert head<8
                    data.update({'maximumStandingHitHeadY':head,'minimumHealth':min(r['health'] for r in server),
                        'distinctAcceptedHitTimes':sorted({r['hitStarted'] for r in server if r['hitStarted']>=0})})
                if case=='seat_interrupt':
                    assert any(r['radioStarted']>=0 for r in server) and any(r['seated'] for r in server)
                    seats=[r for r in images if r['seated']];assert seats
                    assert all(r['movementWeight']==0 and not r['gunVisible'] for r in seats)
                    data['renderedSeatFrames']=len(seats)
                cases[case]=data
            native[mode]={'cases':cases,'buildLog':evidence(path),'serverSamples':evidence(folder/'server.json'),
                'renderedSamples':evidence(folder/'frames.json'),'reviewManifest':evidence(folder/'review-manifest.json'),
                'reviewSheets':[evidence(folder/f'review-{i}.jpg') for i in (1,2,3)],'soundEvents':load(folder/'sounds.json')}
        qa_client=Path('D:/CodexCache/creature-combat-qa')/('neo-client' if platform=='neo' else 'client')
        assert not (qa_client/'config/blacklight/p1-military-motion.json').exists()
        report['platforms'][platform]={'sourceSha256':sources,'unitTests':units,'serverRegressions':logs,'native':native}
    encoded=json.dumps(report,ensure_ascii=False,indent=2)+'\n'
    for root in ROOTS.values():
        output=root/'art/military2/soldier-motion-validation.json';output.write_text(encoded,encoding='utf8')
        print(str(output),sha(output))

if __name__=='__main__':main()
