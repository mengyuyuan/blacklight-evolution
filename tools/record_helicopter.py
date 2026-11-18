"""Verify and record the final two-loader helicopter captures and current source hashes."""
import argparse
from collections import Counter
import hashlib
import json
import re
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
FORGE=Path('D:/blacklight-evolution-forge-1.20.1')
QA=Path('D:/CodexCache/helicopter-qa')

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--reviewed',action='store_true');args=parser.parse_args()
    if not args.reviewed:parser.error('Inspect native combat, player and navigation contact sheets for both loaders first')
    source_files=['src/main/java/com/codex/blacklight/'+p for p in (
        'entity/BlackwatchHelicopter.java','entity/MilitarySoldier.java','military/aircraft/HelicopterFlight.java',
        'military/aircraft/HelicopterSupport.java','military/aircraft/HelicopterArmament.java','military/aircraft/HelicopterCombat.java','military/MilitaryTactics.java','military/vehicle/VehicleDamageBridge.java',
        'military/vehicle/VehicleSounds.java','registry/ModEntities.java','client/model/HelicopterModel.java',
        'client/model/BlackwatchModel.java','client/renderer/HelicopterRenderer.java','client/vehicle/HelicopterAudio.java',
        'client/ClientClawEvents.java','firearms/FirearmsCombat.java','firearms/VehicleOrdnanceProjectile.java','firearms/GunplayEffects.java')]
    source_files += ['src/helicopterTest/java/com/codex/blacklight/gametest/HelicopterGameTest.java',
                     'src/helicopterTest/java/com/codex/blacklight/gametest/HelicopterCombatGameTest.java',
                     'src/test/java/com/codex/blacklight/military/aircraft/HelicopterFlightTest.java',
                     'src/test/java/com/codex/blacklight/military/aircraft/HelicopterArmamentTest.java',
                     'src/helicopterVisual/java/com/codex/blacklight/client/vehicle/HelicopterVisual.java',
                     'tools/helicopter-test.init.gradle','tools/helicopter-visual.init.gradle']
    source_files += ['src/main/java/com/codex/blacklight/'+p for p in (
        'military/aircraft/HelicopterPilot.java','military/aircraft/HelicopterHijack.java','military/vehicle/VehicleSeats.java','military/vehicle/VehicleControlAuthority.java','military/vehicle/VehicleHijackSystem.java',
        'network/HelicopterControlPayload.java','network/VehicleSeatPayload.java','network/ModNetwork.java','mode/PrototypeSurvivalLock.java',
        'client/VehicleControls.java','client/VehicleHud.java','client/vehicle/HelicopterControls.java','client/vehicle/HelicopterView.java','client/vehicle/HelicopterPose.java','client/vehicle/VehicleCamera.java',
        'client/animation/BlacklightRenderPose.java','client/model/MercerPlayerMesh.java','mixin/client/VehiclePlayerPoseMixin.java')]
    source_files += ['src/helicopterTest/java/com/codex/blacklight/gametest/HelicopterPilotGameTest.java','src/helicopterVisual/java/com/codex/blacklight/client/vehicle/HelicopterPilotVisual.java',
                     'src/test/java/com/codex/blacklight/military/aircraft/HelicopterAuthorityTest.java','src/test/java/com/codex/blacklight/military/vehicle/VehicleControlAuthorityTest.java']
    source_files += ['src/main/java/com/codex/blacklight/military/aircraft/HelicopterRoute.java','src/main/java/com/codex/blacklight/military/aircraft/HelicopterNavigation.java',
                     'src/test/java/com/codex/blacklight/military/aircraft/HelicopterRouteTest.java','src/helicopterTest/java/com/codex/blacklight/gametest/HelicopterNavigationGameTest.java',
                     'src/helicopterVisual/java/com/codex/blacklight/client/vehicle/HelicopterNavigationVisual.java']
    assets=['src/main/resources/assets/blacklight/'+p for p in ('military/helicopter-model.json','textures/entity/blackwatch_helicopter.png','sounds.json')]
    assets += [f'src/main/resources/assets/blacklight/sounds/vehicles/helicopter_{name}.ogg' for name in ('rotor','turbine','door','alarm')]
    report={'schemaVersion':4,'date':'2026-11-16','scope':'Mechanical armed transport: officer dispatch, real ground takeoff and landing delivery, pursuit/strafe, finite guns/rockets, squad target handoff, damage/crash, authored geometry and positional sounds, real player piloting and interruptible same-aircraft prototype capture; bounded incremental 3D routing, dynamic replan and diagonal landing exits',
            'overallGoalComplete':False,
            'authoring':{name:sha(ROOT/name) for name in ('tools/build_helicopter.py','tools/build-helicopter-audio.py','tools/record_helicopter.py')},
            'remaining':['Broader moving/aerial target and multiplayer encounter coverage and third-party skin renderers',
                         'Long-range/global navigation and fully obstructed return recovery','Rappelling','Real base/helipad departure scheduling',
                         'Source-game frame-by-frame comparison and final subjective sound evaluation'],
            'nativeVisualLimit':'The combat capture holds delivered troops AI-disabled to isolate aircraft weapon effects; real dismount and target handoff are separately verified by server scenarios.',
            'platforms':{}}
    audit=Path('D:/PrototypeReference/p1/blackwatch-fig-audit.json')
    record=next(r for r in json.loads(audit.read_text('utf8'))['records'] if r.get('offset')==484460)
    assert record['type']=='SetBrakeDistance' and record['fields']['BrakeDistance']==20
    report['privateReference']={'auditSha256':sha(audit),'source':record['source'],'offset':484460,'BrakeDistance':20,
                                'limit':'Only this braking parameter informs the adaptation; not the original game flight algorithm.'}
    strafe=next(r for r in json.loads(audit.read_text('utf8'))['records'] if r.get('offset')==491524)
    assert strafe['type']=='HelicopterStrafeMode'
    report['privateReference']['strafeModeOffset']=491524
    report['privateReference']['strafeLimit']='The mode name is corroborated; orbit, engagement distances, weapon stores and damage are project gameplay, not recovered original algorithms.'
    for platform,root,build_name in (('neo',ROOT,'neo-build'),('forge',FORGE,'forge-render-build')):
        evidence=QA/(platform+'-combat-visual');build=Path('D:/CodexCache/creature-combat-qa')/build_name
        log=Path(f'D:/CodexCache/creature-combat-{platform}-helicopterVerify.log')
        text=log.read_text('utf8',errors='replace');assert 'BUILD SUCCESSFUL' in text and 'All 32 required tests passed' in text
        military_log=Path(f'D:/CodexCache/creature-combat-{platform}-militaryServer.log')
        military_text=military_log.read_text('utf8',errors='replace')
        military_match=re.search(r'All (\d+) required tests passed',military_text)
        assert 'BUILD SUCCESSFUL' in military_text and military_match
        military_count=int(military_match.group(1));assert military_count=={'neo':18,'forge':19}[platform]
        assert military_log.stat().st_mtime>=log.stat().st_mtime,'Military regression must follow this verification'
        visual_log=Path(f'D:/CodexCache/creature-combat-{platform}-helicopterCombatVisual.log')
        assert 'BUILD SUCCESSFUL' in visual_log.read_text('utf8',errors='replace')
        assert (evidence/'status.txt').read_text('utf8').startswith('PASS:')
        unit_paths=[build/f'test-results/test/TEST-com.codex.blacklight.military.aircraft.{name}.xml' for name in ('HelicopterFlightTest','HelicopterArmamentTest','HelicopterAuthorityTest','HelicopterRouteTest')]
        for unit_path,count in zip(unit_paths,(4,2,2,8)):
            unit=ET.parse(unit_path).getroot();assert unit.get('tests')==str(count) and unit.get('failures')=='0' and unit.get('errors')=='0'
        control_xml=build/'test-results/test/TEST-com.codex.blacklight.military.vehicle.VehicleControlAuthorityTest.xml'
        control=ET.parse(control_xml).getroot();assert control.get('tests')=='11' and control.get('failures')=='0' and control.get('errors')=='0'
        pilot_evidence=QA/(platform+'-pilot-visual');pilot_log=Path(f'D:/CodexCache/creature-combat-{platform}-helicopterPilotVisual.log')
        assert 'BUILD SUCCESSFUL' in pilot_log.read_text('utf8',errors='replace') and (pilot_evidence/'status.txt').read_text('utf8').startswith('PASS:')
        pilot_frames=json.loads((pilot_evidence/'frames.json').read_text('utf8'));pilot_sounds=json.loads((pilot_evidence/'sounds.json').read_text('utf8'))
        assert max(row['y'] for row in pilot_frames)>86 and abs(pilot_frames[-1]['y']-81)<.02
        assert max(abs(row['yaw']) for row in pilot_frames)>20 and pilot_frames[-1]['rounds']<240 and pilot_frames[-1]['rockets']<8
        assert set(pilot_sounds)>={'vehicle.helicopter_door','vehicle.helicopter_rotor','vehicle.helicopter_turbine','vehicle.coaxial','vehicle.rocket'}
        pilot_captures=['LATCH','PULL','MERCER_PULL','EJECT','ENTRY','SEATED','MERCER_SEATED','ASCENT','TURN','THIRD_PERSON','COCKPIT_FIRE','DESCENT','LANDED','COCKPIT_LANDED']
        pilot_record={'clientTicks':len(pilot_frames),'nativeEvidence':str(pilot_evidence),'maximumHeight':max(row['y'] for row in pilot_frames),'maximumYaw':max(abs(row['yaw']) for row in pilot_frames),
                      'finalRounds':pilot_frames[-1]['rounds'],'finalRockets':pilot_frames[-1]['rockets'],'sounds':pilot_sounds,'nativeLogSha256':sha(pilot_log),
                      'framesSha256':sha(pilot_evidence/'frames.json'),'screenshotsSha256':{n:sha(pilot_evidence/(n+'.png')) for n in pilot_captures},
                      'reviewedSheetsSha256':{f'review-{i}.png':sha(pilot_evidence/f'review-{i}.png') for i in (1,2,3,4)},
                      'scope':'Actual local player sends production interaction and keyboard packets; same damaged aircraft, real extracted crew, native skin and Mercer poses, camera/HUD, finite weapons and landing.',
                      'limit':'Ground capture holds NPCs AI-disabled to inspect original identities and clear exits. The separate server scenario enables actual NPC movement for airborne egress. Multiplayer and arbitrary third-party renderers are not covered.'}
        navigation_folder=QA/(platform+'-navigation-visual');navigation_log=Path(f'D:/CodexCache/creature-combat-{platform}-helicopterNavigationVisual.log')
        assert 'BUILD SUCCESSFUL' in navigation_log.read_text('utf8',errors='replace') and (navigation_folder/'status.txt').read_text().startswith('PASS:')
        navigation=json.loads((navigation_folder/'evidence.json').read_text());mission=navigation['missions'];route_frames=navigation['frames']
        assert len(mission)==2 and all(row['delivered']==4 and len(set(row['originalCrew']))==6 and row['maxWorldNodes']<=24 for row in mission)
        assert mission[0]['oppositeExcursion']>19 and mission[0]['searches']>=2 and mission[0]['invalidations']>0 and mission[1]['maxHeight']>100.9
        assert all(row['worldNodes']<=24 for row in route_frames)
        assert {row['navigation'] for row in route_frames}>={'SEARCHING','FOLLOWING'}
        navigation_sounds=json.loads((navigation_folder/'sounds.json').read_text());assert set(navigation_sounds)>={'vehicle.helicopter_rotor','vehicle.helicopter_turbine','vehicle.helicopter_door'}
        route_captures=['TOWER_SEARCH','TOWER_REPLAN','TOWER_DETOUR','TOWER_DESCEND','TOWER_UNLOAD','TOWER_RETURN','LOW_SEARCH','LOW_CROSSING','LOW_DESCEND','LOW_UNLOAD','LOW_RETURN']
        navigation_record={'missions':mission,'observedServerTicks':len(route_frames),'nativeEvidence':str(navigation_folder),'nativeLogSha256':sha(navigation_log),'evidenceSha256':sha(navigation_folder/'evidence.json'),
                           'sounds':navigation_sounds,'screenshotsSha256':{n:sha(navigation_folder/(n+'.png')) for n in route_captures},
                           'reviewedSheetsSha256':{f'review-{i}.png':sha(navigation_folder/f'review-{i}.png') for i in (1,2,3)},
                           'budget':{'nodesPerCraftTick':6,'nodesPerLevelTick':24,'maxExpandedNodes':1536,'latticeStepBlocks':4,'maxStartGoalDistance':160,'horizontalMargin':32,'verticalMargin':24},
                           'scope':'Actual AI flight around a tall wall, insertion of another solid wall ahead, opposite-side replan, diagonal landing, original crew delivery and return; second mission climbs above a long low wall. Loaded terrain only, conservative full hull/rotor corridors; per-tick motion still checks dynamic bodies.',
                           'limit':'Bounded local routing authored for this project, not a recovered source-game flight algorithm. Does not generate terrain, provide a global route, or guarantee escape when every return corridor is blocked. Troops are AI-disabled to isolate landing clearance.'}
        latest_production=max((root/p).stat().st_mtime for p in source_files if p.startswith('src/main/'))
        for current_log in (log,military_log,visual_log,pilot_log,navigation_log):
            assert current_log.stat().st_mtime>=latest_production,('Validation predates current production source',platform,str(current_log))
        assert log.stat().st_mtime>=max((root/p).stat().st_mtime for p in source_files if '/helicopterTest/' in p or p.startswith('src/test/'))
        frames=json.loads((evidence/'frames.json').read_text('utf8'));phases=Counter(row['phase'] for row in frames)
        assert set(phases)-{'IDLE'}=={'SPIN_UP','CLIMB','APPROACH','DESCEND','UNLOAD','DEPART','PURSUIT','STRAFE','RETURN','CRASH'}
        # Spawn can reach the client before the initial mission/passenger data.
        # Permit only a short stationary prefix, never a return to IDLE in flight.
        initial_sync=next(i for i,row in enumerate(frames) if row['phase']!='IDLE')
        assert initial_sync<=10 and all(row['phase']!='IDLE' for row in frames[initial_sync:])
        assert all(row['rotor']==0 and row['passengers']==0 and row['x']==-20 and row['y']==81 and row['z']==0 for row in frames[:initial_sync])
        grounded=[row for row in frames if row['phase']=='SPIN_UP'];climb=[row for row in frames if row['phase']=='CLIMB']
        assert max(row['y'] for row in grounded)-min(row['y'] for row in grounded)<.03
        assert max(row['y'] for row in climb)>min(row['y'] for row in grounded)+10
        assert all(row['passengers']==6 for row in frames if row['phase'] in ('SPIN_UP','CLIMB','APPROACH','DESCEND'))
        assert all(row['passengers']==2 for row in frames if row['phase']=='RETURN')
        sounds=json.loads((evidence/'sounds.json').read_text('utf8'));assert set(sounds)=={f'vehicle.helicopter_{n}' for n in ('rotor','turbine','door','alarm')}|{'vehicle.coaxial','vehicle.rocket'}
        mount_error=float((evidence/'mount-error.txt').read_text());assert mount_error<.0001
        assert max(row['airShots'] for row in frames)>=3
        for asset in assets:
            # Both native clients loaded the actual processed resources from these isolated Gradle outputs.
            assert sha(root/asset)==sha(build/'resources/main'/Path(asset).relative_to('src/main/resources')), ('Stale compiled asset',platform,asset)
        captures=['SPIN_UP','CLIMB','APPROACH','DESCEND','UNLOAD','UNLOAD_REVERSE','DEPART','PURSUIT','STRAFE','STRAFE_FIRE','RETURN','CRASH']
        preview=root/'art/military-vehicles/previews';preview.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(evidence/'UNLOAD_REVERSE.png',preview/'helicopter-delivery.png')
        shutil.copyfile(evidence/'STRAFE_FIRE.png',preview/'helicopter-strafe.png')
        shutil.copyfile(pilot_evidence/'MERCER_PULL.png',preview/'helicopter-hijack.png')
        shutil.copyfile(pilot_evidence/'THIRD_PERSON.png',preview/'helicopter-pilot.png')
        shutil.copyfile(navigation_folder/'TOWER_DETOUR.png',preview/'helicopter-navigation.png')
        report['platforms'][platform]={'sourceSha256':{p:sha(root/p) for p in source_files},
            'assetSha256':{p:sha(root/p) for p in assets},'unitTests':16,'serverScenarios':32,'sharedControlRegressionTests':11,'sharedControlXmlSha256':sha(control_xml),'playerControl':pilot_record,'navigation':navigation_record,
            'unitXmlSha256':{p.name:sha(p) for p in unit_paths},'verificationLogSha256':sha(log),'nativeLogSha256':sha(visual_log),
            'militaryRegressionScenarios':military_count,'militaryRegressionLogSha256':sha(military_log),
            'nativeEvidence':str(evidence),'clientTicks':len(frames),'phaseTicks':dict(phases),'sounds':sounds,
            'initialEntitySyncTicks':initial_sync,
            'maximumRenderedMountError':mount_error,'shotsObserved':max(row['airShots'] for row in frames),
            'groundedSpoolUpY':[min(row['y'] for row in grounded),max(row['y'] for row in grounded)],
            'framesSha256':sha(evidence/'frames.json'),'soundsSha256':sha(evidence/'sounds.json'),
            'screenshotsSha256':{name:sha(evidence/(name+'.png')) for name in captures},
            'reviewedSheetsSha256':{f'review-{i}.png':sha(evidence/f'review-{i}.png') for i in (1,2,3)},
            'review':'Native takeoff, landing/dismount, pursuit, strafe firing, return and crash inspected; no missing textures or protruding seated heads observed. Actual model matrices checked against chin gun and all eight rocket tube shot positions.'}
    for root in (ROOT,FORGE):
        p=root/'art/military-vehicles/helicopter-validation.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf8')
    print(json.dumps({'unitTestsPerLoader':16,'sharedControlRegressionPerLoader':11,'serverScenariosPerLoader':32,'militaryRegressionScenarios':{p:r['militaryRegressionScenarios'] for p,r in report['platforms'].items()},'nativeTicks':{p:r['clientTicks'] for p,r in report['platforms'].items()},'navigationTicks':{p:r['navigation']['observedServerTicks'] for p,r in report['platforms'].items()},'soundCuesPerLoader':6,'overallGoalComplete':False}))

if __name__=='__main__':main()
