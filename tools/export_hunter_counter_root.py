"""Export native Motion_Root translations, without changing Hunter skin poses.
Static source parsing only; never invokes Java, native game binaries or tests.
"""
from pathlib import Path
import hashlib
import json
import math
import numpy as np
import export_encounter_source_motion as m

ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path('D:/PrototypeReference/p1/private/comparison/Brawler.p3d')
FIG=Path('D:/PrototypeReference/p1/input/comparison/Brawler_fig.p3d.rz')

def export():
    nodes=m.nodes(SOURCE);rig=m.skeleton(nodes,'brawler_master_skeleton_skeleton')
    clips={m.decoder.text8(n['payload'],4)[0]:n for n in nodes if n['type']==0x121000}
    reference=m.world(rig,m.decoder.decode(clips['brawler_act_calm']),0)
    # Exactly the existing Hunter pelvis/sole calibration, pixels -> blocks.
    scale=18/reference['Pelvis'][1,3]/16
    source_records=json.loads(Path('D:/PrototypeReference/p1/creature-fig-audit.json').read_text())['records']
    by={r['offset']:r for r in source_records if r['source']=='comparison/Brawler_fig.p3d.rz'}
    output={'schema':1,'fps':30,'sourceUnitBlocks':scale,'clips':{}}
    sources=[];fixtures=[]
    for key,name,first,last,record in [('counter','brawler_pnch_claw_counter',0,42,53820),('release','brawler_claw_barrage',3,76,54684)]:
        clip=m.decoder.decode(clips[name]);fields=by[record]['fields']
        root_tracks=[t for t in clip['tracks'] if t['bone']=='Motion_Root']
        if rig[0][0]!='Motion_Root' or any(t['tag']!='TRAN' for t in root_tracks):raise ValueError('Root ownership changed')
        if not (clip['fps']==30 and fields['HasRootTranslation'] and fields['HasRootRotation'] and fields['InitFrame']==first and fields['EndFrame']==last and fields['TimeEnd']==-1):raise ValueError('Source schedule changed')
        rows=[]
        for frame in range(first,last+1):
            matrix=m.world(rig,clip,frame)['Motion_Root']
            if not np.allclose(matrix[:3,:3],np.eye(3),atol=1e-7):raise ValueError('Native root rotation needs a separate port')
            rows.append(matrix[:3,3].tolist())
        if not np.isfinite(rows).all() or max(r[1] for r in rows)-min(r[1] for r in rows)>1e-7:raise ValueError('Expected horizontal root')
        output['clips'][key]={'firstFrame':first,'lastFrame':last,'speed':fields['Speed'],'positions':rows}
        sources.append({'key':key,'clip':name,'animationOffset':clips[name]['offset'],'fig':by[record],
                        'rootTracks':[{'tag':t['tag'],'keys':len(t['frames'])} for t in root_tracks],
                        'childScaleTracksNotPartOfRoot':[{'bone':t['bone'],'min':np.asarray([m.track_at(t,f) for f in range(first,last+1)]).min(axis=0).tolist(),'max':np.asarray([m.track_at(t,f) for f in range(first,last+1)]).max(axis=0).tolist()} for t in clip['tracks'] if t['tag']=='SCL'],
                        'firstRoot':rows[0],'lastRoot':rows[-1],'worldYawZeroDisplacement':[(rows[-1][0]-rows[0][0])*scale,0,-(rows[-1][2]-rows[0][2])*scale]})
        for frame in [first,first+6,(first+last)//2,last]:
            point=m.world(rig,clip,frame)['Motion_Root'][:3,3]
            delta=point-np.asarray(rows[0]);fixtures.append({'clip':key,'sourceFrame':frame,'sourcePoint':point.tolist(),'yawZeroOffset':[float(delta[0]*scale),0,float(-delta[2]*scale)]})
    pose=json.loads((ROOT/'src/main/resources/assets/blacklight/models/hunter_combat_motion.json').read_text())
    for name in ['combat_counter','combat_barrage']:
        if any(abs(row[3])+abs(row[5])>1e-8 for frame in pose['clips'][name] for row in frame):raise ValueError('Pose already has horizontal translation; avoid double root motion')
    resource=ROOT/'src/main/resources/assets/blacklight/models/hunter_counter_root.json'
    resource.write_text(json.dumps(output,separators=(',',':'))+'\n')
    fixture={'sourceSha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),'method':'Independent original Motion_Root world matrices minus each native phase first frame, existing Hunter pelvis scale, source +X -> MC yaw0 +X, source -Z -> MC yaw0 +Z.','fixtures':fixtures}
    (ROOT/'src/test/resources/hunter-counter-root-reference.json').write_text(json.dumps(fixture,indent=2)+'\n')
    provenance={'schema':1,'state':'Static decode/export only; no Java, tests, native execution/emulation, game or media playback.',
        'sourceSha256':fixture['sourceSha256'],'figSha256':hashlib.sha256(FIG.read_bytes()).hexdigest(),'sourceUnitBlocks':scale,
        'scaleBasis':{'authoredPelvisPixels':18,'referencePelvisY':float(reference['Pelvis'][1,3]),'pixelsPerBlock':16},
        'sources':sources,'autoTarget':[by[54304],by[56812]],'sequence':by[54244],
        'noDoubleMotion':'Existing combat_counter and combat_barrage pose rows have zero horizontal translations on all bones. Their vertical sole correction already subtracts Motion_Root height; do not add source Y or change those poses.',
        'axis':'Existing retarget reflects source X to model -X; renderer rotates model by180-yaw. Combined root mapping is(dx,0,-dz).yRot(-yaw). All sampled native Motion_Root rotations are identity.',
        'timing':'Counter sourceFrame=min42,age*1.5*.9; release starts at legacy composed age8/sourceFrame3, then min76,3+(age-8)*1.5. Subtract within each clip; absolute root coordinates are unrelated authoring offsets. No interpolation between counter endZ-6.516 and release startZ+.267.',
        'implementation':{
            'scope':'HunterCounterMotion supplies COUNTER and only COUNTER_RELEASE-marked BARRAGE. Standalone barrage and other actions retain their existing movement. No synchronized fields added; server actual entity position uses normal entity replication.',
            'collision':'Each observed phase delta goes through Entity.move SELF using actual block/entity collision shapes and existing step-up behavior. Entire requested swept AABB must stay in loaded chunks and world border; the requested root must remain in mob restriction. Invalid boundaries cancel the chain. After horizontal-only move, formerly-grounded actor retains onGround only if its accepted bbox shifted .01 downward intersects real collision support.',
            'blocked':'No desired world endpoint is retained. Animation age advances even when collision rejects part/all travel, and the next difference starts at the actual accepted entity position. Removing cover cannot repay previous blocked distance.',
            'continuity':'Same age is ignored. A gap over1tick or displacement over8blocks cancels rather than replaying an unobserved movement/hit corridor. This is an explicit MC safety policy, not a decoded source teleport limit.',
            'lifecycle':'Counter release rebases its root at the accepted counter endpoint. cancel/claim/NoAI/stun/load clear transient continuation; same-object NBT load also cancels. attackEpoch invalidates movement/damage callback reentry before stale tick writes or another hit window. Active attacks are not saved/resumed.',
            'reach':'AI opening/release previews include the same future source offsets; they only allow selection. Actual damage still requires the posed wrist sweep at accepted actor positions, existing LOS and accepted hurt.',
            'pendingTests':'HunterCounterTest5 methods including8 independent source-root fixtures and no-double-horizontal-pose check; HunterCounterGameTest8 methods including real two-stage movement/duplicate tick, full-height wall/no movement repayment, same-object load.'},
        'limits':['No Java/Gradle/unit/GameTest/game/native execution or emulation/media playback performed.',
            'Full native root-motion blend/controller/physics execution is not reproduced: these are original translation deltas applied to the existing MC locomotion and collision adapter. Existing pose joins are retained; no inferred root velocity blending is added.',
            'Existing authored rigid skeleton, sole-height correction and action timing remain. Source large hit impulse, complete reaction-counter AI and original skinned body are still separate gaps.',
            'Existing counter pose exporter ignores child Spine_1/Spine_2/Neck SCL tracks (ranges recorded above). Motion_Root is independent bone0 with only translation, so root travel is unaffected; full counter skin scaling is not claimed complete.',
            'Actual client interpolation, uneven ground, stairs, crowd collision and combat around cover require later permitted player acceptance.']}
    (ROOT/'art/hunter/counter-root-source-20261117.json').write_text(json.dumps(provenance,indent=2)+'\n')
    print(json.dumps({'scale':scale,'frames':[len(c['positions']) for c in output['clips'].values()],'displacements':[s['worldYawZeroDisplacement'] for s in sources]}))

if __name__=='__main__':export()
