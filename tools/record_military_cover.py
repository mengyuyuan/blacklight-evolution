"""Record current two-loader soldier cover, actual delivery and shared navigation regressions."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

QA=Path('D:/CodexCache/military-cover-qa')
ROOTS={'neo':Path('D:/blacklight-evolution'),'forge':Path('D:/blacklight-evolution-forge-1.20.1')}
LABELS=('AIR_APPROACH','AIR_UNLOAD','TROOPS_READY','ARROWS','SEEKING_COVER','SHELTERED','WALL_REMOVED','RETURN_FIRE')

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reviewed',action='store_true')
    args=parser.parse_args()
    if not args.reviewed:
        parser.error('Inspect both native review sheets for each loader before recording this checkpoint')
    production=['src/main/java/com/codex/blacklight/'+name for name in (
        'military/MilitaryCover.java','military/MilitaryFormationSystem.java','military/MilitaryGroundNavigation.java',
        'military/MilitaryTactics.java','military/MilitaryWorkBudget.java','military/MilitaryFireControl.java',
        'entity/MilitarySoldier.java','entity/BlackwatchSquad.java','entity/BlackwatchSquadSystem.java',
        'entity/BlackwatchHelicopter.java','city/MilitaryHomeSystem.java')]
    checks=['src/coverTest/java/com/codex/blacklight/gametest/MilitaryCoverGameTest.java',
        'src/coverVisual/java/com/codex/blacklight/client/MilitaryCoverVisual.java',
        'tools/military-cover-test.init.gradle','tools/military-cover-visual.init.gradle','tools/military-squad-test.init.gradle',
        'src/blackwatchTest/java/com/codex/blacklight/gametest/BlackwatchSquadGameTest.java',
        'src/blackwatchTest/java/com/codex/blacklight/gametest/MilitaryFormationGameTest.java']
    report={'schemaVersion':1,'date':'2026-11-16','overallGoalComplete':False,
        'scope':'Real suppressed soldiers reserve nearby shielding positions and reach them through the shared bounded path queue; actual helicopter delivery resets both independent home anchors.',
        'baseline':json.loads((QA/'before.json').read_text('utf-8-sig')),
        'baselineSha256':sha(QA/'before.json'),
        'regressionFixtureCorrections':[
            'Guard-coordinate scenario waits within its existing timeout for the real 77-tick radio call, then checks injured health fraction and this soldier as actual attacker; native host maturation rescales maximum health after spawning, invalidating its original absolute-health baseline.',
            'Forge pair-cadence fixture now uses the existing Neo RECOVER assertion for nonempty magazines; the method was renamed on both loaders. This case does not claim to test a real reload.'
        ],
        'limits':[
            'Authored project tactics, not a recovered original-game cover algorithm.',
            'Native visual fixture retains the original six crew; four troops have AI disabled only until the first ground volley and have 80 health for survival during repeated real arrow attacks.',
            'A stationary vanilla skeleton fires scripted real projectiles; troop paths, damage, finite ammunition and weapon fire use production behavior.',
            'Each troop reaches cover during the encounter; this does not require all four to remain sheltered simultaneously.',
            'Eight selected native frames per loader were reviewed, not every rendered frame.',
            'Ground camera frames can crop nearby troops during return fire; complete identity, movement and ammunition checks come from the server samples rather than claiming all four are visible in every image.',
            'Hidden muted QA verifies live sound events, not subjective audio quality.',
            'No user world, release packaging, installation or publication in this checkpoint.',
            'Prior helicopter schema 4 visual report remains a historical checkpoint; this report records current server regressions and a new combined troop-delivery encounter.'
        ],
        'remaining':['Broader mixed terrain and multiplayer combat coverage','Source-derived walking, running, radio and hit choreography','Full retreat and fear behavior','Remaining creature, model and sound work in the active overall goal'],
        'platforms':{}}
    for platform,root in ROOTS.items():
        latest=max((root/path).stat().st_mtime for path in production)
        logs={}
        for stage,count in (('coverVerify',9),('militarySquadServer',34),('militaryServer',18 if platform=='neo' else 19),('helicopterVerify',32)):
            path=Path(f'D:/CodexCache/creature-combat-{platform}-{stage}.log')
            text=path.read_text('utf8',errors='replace')
            match=re.search(r'All (\d+) required tests passed',text)
            assert 'BUILD SUCCESSFUL' in text and match and int(match[1])==count,(platform,stage)
            assert path.stat().st_mtime>=latest,(platform,stage,'stale production coverage')
            logs[stage]={'requiredServerScenarios':count,'sha256':sha(path),'path':str(path)}
        assert Path(logs['coverVerify']['path']).stat().st_mtime>=(root/checks[0]).stat().st_mtime
        assert Path(logs['militarySquadServer']['path']).stat().st_mtime>=max((root/name).stat().st_mtime for name in checks[-2:])
        units={}
        for cls,count in (('entity.BlackwatchSquadTest',16),('military.MilitaryFireControlTest',4),('military.MilitaryWorkBudgetTest',4)):
            path=QA/f'{platform}-units'/f'TEST-com.codex.blacklight.{cls}.xml'
            result=ET.parse(path).getroot()
            assert int(result.get('tests'))==count and result.get('failures')=='0' and result.get('errors')=='0'
            assert path.stat().st_mtime>=latest
            units[cls]={'tests':count,'sha256':sha(path)}
        build=Path('D:/CodexCache/creature-combat-qa')/('neo-build' if platform=='neo' else 'forge-render-build')
        helicopter_units={}
        for cls,count in (('aircraft.HelicopterFlightTest',4),('aircraft.HelicopterArmamentTest',2),('aircraft.HelicopterAuthorityTest',2),('aircraft.HelicopterRouteTest',8),('vehicle.VehicleControlAuthorityTest',11)):
            path=build/'test-results/test'/f'TEST-com.codex.blacklight.military.{cls}.xml'
            result=ET.parse(path).getroot()
            assert int(result.get('tests'))==count and result.get('failures')=='0' and result.get('errors')=='0'
            assert path.stat().st_mtime>=latest
            helicopter_units[cls]={'tests':count,'sha256':sha(path)}
        folder=QA/f'{platform}-visual'
        visual_log=Path(f'D:/CodexCache/creature-combat-{platform}-coverVisual.log')
        assert 'BUILD SUCCESSFUL' in visual_log.read_text('utf8',errors='replace')
        assert (folder/'status.txt').read_text().startswith('PASS:')
        evidence=json.loads((folder/'evidence.json').read_text())
        assert all(evidence[key]==4 for key in ('shelteredOriginalTroops','hitOriginalTroops','originalTroopsReturningFire'))
        assert len(set(evidence['originalCrew']))==6 and evidence['peakPathPlans']<=8 and evidence['shortestPathInterval']>=10
        frames=evidence['frames'];assert len(frames)==evidence['observedServerTicks']
        assert all(b['worldTick']>a['worldTick'] for a,b in zip(frames,frames[1:]))
        at_removal=next(row for row in frames if row['tick']==evidence['wallRemovedAt'])
        assert frames[-1]['enemyHealth']<at_removal['enemyHealth'],'Actual enemy health must fall after cover is removed'
        opening={m['id']:m['ammunition'] for m in at_removal['members']}
        assert all(m['ammunition']<opening[m['id']] for m in frames[-1]['members']),'Each original troop must spend its combined real inventory'
        assert all(len({m['goal'] for m in row['members'] if 'goal' in m})==sum('goal' in m for m in row['members']) for row in frames)
        sounds=json.loads((folder/'sounds.json').read_text())
        assert set(sounds)>={'entity.skeleton.shoot','firearm.rifle','vehicle.helicopter_door'}
        visual_source=root/checks[1]
        files=['status.txt','evidence.json','sounds.json']+[label+'.png' for label in LABELS]
        assert all((folder/name).stat().st_mtime>=max(latest,visual_source.stat().st_mtime) for name in files),'Stale native artifact'
        for part in (1,2):
            review=folder/f'review-{part}.jpg'
            assert review.stat().st_mtime>=max((folder/(label+'.png')).stat().st_mtime for label in LABELS)
        report['platforms'][platform]={
            'sources':{name:sha(root/name) for name in production+checks},'regressions':logs,
            'coverUnitTests':units,'coverUnitTotal':24,'helicopterControlUnitTests':helicopter_units,'helicopterControlUnitTotal':27,
            'native':{key:value for key,value in evidence.items() if key!='frames'},
            'nativeEnemyHealthAtWallRemoval':at_removal['enemyHealth'],'nativeFinalEnemyHealth':frames[-1]['enemyHealth'],
            'nativeLogSha256':sha(visual_log),'nativeSounds':sounds,
            'nativeArtifacts':{name:sha(folder/name) for name in files},
            'reviewedSheets':{f'review-{part}.jpg':sha(folder/f'review-{part}.jpg') for part in (1,2)}}
    report['generatorSha256']=sha(Path(__file__))
    data=json.dumps(report,ensure_ascii=False,indent=2)+'\n'
    for root in ROOTS.values():
        path=root/'art/military2/soldier-cover-validation.json'
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(data,encoding='utf8')
        print(path,sha(path))

if __name__=='__main__':
    main()
