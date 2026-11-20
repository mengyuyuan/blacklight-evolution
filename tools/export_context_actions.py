"""Export contextual actions from the local P1 archive onto the native player rig.

No source files are modified. Uses the same fixed-length bind-delta retarget as
export_dodge_motion.py. The contact sheet / in-game review remains necessary:
FIG timings and Minecraft damage/radius adaptations are recorded separately.
"""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys
import numpy as np

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'art/motion-profile'


def quaternion(r):
    # Unit xyzw quaternion; robust at 180 degrees.
    k = np.array([[r[0,0]-r[1,1]-r[2,2], r[0,1]+r[1,0], r[0,2]+r[2,0], r[2,1]-r[1,2]],
                  [r[0,1]+r[1,0], r[1,1]-r[0,0]-r[2,2], r[1,2]+r[2,1], r[0,2]-r[2,0]],
                  [r[0,2]+r[2,0], r[1,2]+r[2,1], r[2,2]-r[0,0]-r[1,1], r[1,0]-r[0,1]],
                  [r[2,1]-r[1,2], r[0,2]-r[2,0], r[1,0]-r[0,1], np.trace(r)]]) / 3
    _, v = np.linalg.eigh(k)
    q = v[:, -1]
    return q if q[3] >= 0 else -q


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(',', ':'), allow_nan=False), encoding='utf8')


def fit_beatdown_stance(pose, bind, source_height, time, turn_between):
    """Retain the P1 pelvis clearance over a prone receiver on the shorter MC legs."""
    amount = float(np.clip((time - .72) / .35, 0, 1))
    amount *= amount * (3 - 2 * amount)
    if not amount:
        return
    original = {name: matrix.copy() for name, matrix in pose.items()}
    lift = amount * np.clip(source_height - original["Root"][1, 3], 0, .29)
    for matrix in pose.values():
        matrix[1, 3] += lift

    def aim(name, origin, old_axis, new_axis):
        pose[name][:3, :3] = turn_between(old_axis, new_axis) @ original[name][:3, :3]
        pose[name][:3, 3] = origin

    def two_bones(a, b, end, start, hint, goal):
        first, second = b-a, end-b
        la, lb = np.linalg.norm(first), np.linalg.norm(second)
        line = goal-start
        direction = line / max(np.linalg.norm(line), 1e-8)
        reach = np.clip(np.linalg.norm(line), abs(la-lb)+1e-6, la+lb-1e-6)
        end_at = start + direction*reach
        axial = (la*la-lb*lb+reach*reach)/(2*reach)
        perpendicular = hint-start - direction*np.dot(hint-start,direction)
        if np.linalg.norm(perpendicular) < 1e-6:
            perpendicular = first - direction*np.dot(first,direction)
        mid = start + direction*axial + perpendicular/max(np.linalg.norm(perpendicular),1e-8)*np.sqrt(max(0,la*la-axial*axial))
        return mid, end_at

    for side, sign in (("L", -1), ("R", 1)):
        thigh, shin, knee = ("Thigh_"+side,"Leg_"+side,"Knee_"+side)
        hip, old_knee = (original[n][:3,3] for n in (thigh,shin))
        ankle_bind = 2*bind[shin][:3,3] - bind[thigh][:3,3]
        ankle = (original[shin] @ np.linalg.inv(bind[shin]) @ [*ankle_bind,1])[:3]
        at = pose[thigh][:3,3].copy()
        goal = ankle + [sign*.13*amount, 0, 0]
        hint = old_knee + [sign*.15*amount, .10*amount, 0]
        mid, end = two_bones(hip,old_knee,ankle,at,hint,goal)
        aim(thigh,at,old_knee-hip,mid-at)
        aim(shin,mid,ankle-old_knee,end-mid)
        pose[knee][:3,3] = mid + original[knee][:3,3]-old_knee
        pose[knee][:3,:3] = pose[thigh][:3,:3] @ np.linalg.inv(original[thigh][:3,:3]) @ original[knee][:3,:3]

    # Keep the native punch endpoints while the hips rise. The torso and arms
    # have their original segment lengths, including through the contact frame.
    spine = original["Torso"][:3,3]
    chest = original["Chest"][:3,3]
    base = pose["Torso"][:3,3].copy()
    desired = chest-base
    # Near the vertical the unaltered direction avoids a neck/spine flip.
    if np.linalg.norm(desired) > .06:
        bend = turn_between(chest-spine,desired)
        pose["Torso"][:3,:3] = bend @ original["Torso"][:3,:3]
    new_chest = base + pose["Torso"][:3,:3] @ np.linalg.solve(original["Torso"][:3,:3],chest-spine)
    shift = new_chest-chest
    for name in ("Chest","Head","Shoulder_L","Shoulder_R"):
        pose[name][:3,3] = original[name][:3,3] + shift
    for side in ("L","R"):
        upper, lower, hand, helper = ("Arm_"+side,"Hand_"+side,"Tool_"+side,"Elbow_"+side)
        shoulder, elbow, wrist = (original[n][:3,3] for n in (upper,lower,hand))
        at = shoulder+shift
        mid, endpoint = two_bones(shoulder,elbow,wrist,at,elbow+.5*shift,wrist)
        aim(upper,at,elbow-shoulder,mid-at)
        aim(lower,mid,wrist-elbow,endpoint-mid)
        pose[hand][:3,3] = endpoint
        pose[helper][:3,3] = mid + original[helper][:3,3]-elbow
        pose[helper][:3,:3] = pose[upper][:3,:3] @ np.linalg.inv(original[upper][:3,:3]) @ original[helper][:3,:3]


def export(archive, decoder_directory, power_package=None):
    sys.path.insert(0, str(decoder_directory))
    # Reuse the audited archive/channel decoder, without executing its CLI.
    spec = importlib.util.spec_from_file_location('context_source', ROOT/'tools/export_encounter_source_motion.py')
    source = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(source)
    decoder_spec=importlib.util.spec_from_file_location('context_decoder',decoder_directory/'decode-animation.py')
    decoder=importlib.util.module_from_spec(decoder_spec);decoder_spec.loader.exec_module(decoder)
    source.decoder=decoder
    clip_archive = power_package or archive
    nodes = source.nodes(clip_archive)
    rig_name = next(source.decoder.text8(n['payload'])[0] for n in nodes if n['type']==0x23000) if power_package else 'alex_reg_arms_skeleton'
    rig = source.skeleton(nodes, rig_name)
    original = {source.decoder.text8(n['payload'], 4)[0]: n for n in nodes if n['type'] == 0x121000}
    actor_bind = source.world(rig, {'tracks': []}, 0)
    receiver_rig = source.skeleton(source.nodes(archive.with_name('startup.p3d.rz')), 'ped_m_sold_aa_master_skeleton_skeleton')
    receiver_bind = source.world(receiver_rig, {'tracks': []}, 0)
    axis = np.array([[1,0,0,0],[0,0,1,0],[0,-1,0,0],[0,0,0,1]], dtype=float)
    mirror = np.diag([-1., 1., 1.])
    rest, parents, target_bind = {}, {}, {}

    def load(node, parent=None):
        n = node['name']; m = np.array(node['transform']).reshape(4, 4)
        if parent is None: m = axis @ m
        rest[n] = m; parents[n] = parent
        target_bind[n] = target_bind[parent] @ m if parent else m
        for child in node['children']: load(child, n)
    load(json.loads((ROOT/'src/main/resources/assets/blacklight/animation/player_bind.json').read_text())['hierarchy'][0])
    mapping = {'Root':'Pelvis','Torso':'Spine_1','Chest':'Spine_3','Head':'Head'}
    for side in ('L','R'):
        mapping.update({f'Thigh_{side}':f'Hip_{side}', f'Leg_{side}':f'Knee_{side}', f'Knee_{side}':f'Knee_{side}',
                        f'Shoulder_{side}':f'Clavicle_{side}', f'Arm_{side}':f'Shoulder_{side}',
                        f'Hand_{side}':f'Elbow_{side}', f'Elbow_{side}':f'Elbow_{side}', f'Tool_{side}':f'Wrist_{side}'})
    scale = target_bind['Root'][1,3] / actor_bind['Pelvis'][1,3]
    # The native rig's Knee/Elbow tracks are helpers at the bend, not endpoints.
    # Align actual segment directions; a bind-rotation delta alone leaves the
    # A-pose target pointing its arms/legs away from the source T-pose skeleton.
    aims={'Torso':('Chest','Spine_3'),'Chest':('Head','Head')}
    for side in ('L','R'):
        aims.update({f'Shoulder_{side}':(f'Arm_{side}',f'Shoulder_{side}'),
                     f'Arm_{side}':(f'Hand_{side}',f'Elbow_{side}'),
                     f'Hand_{side}':(f'Tool_{side}',f'Wrist_{side}'),
                     f'Thigh_{side}':(f'Leg_{side}',f'Knee_{side}')})
    def foot(world,side):
        leg='Leg_'+side;thigh='Thigh_'+side
        ankle=2*target_bind[leg][:3,3]-target_bind[thigh][:3,3]
        return (world[leg]@np.linalg.inv(target_bind[leg])@np.array([*ankle,1]))[:3]
    source_hash = hashlib.sha256(clip_archive.read_bytes()).hexdigest()
    written = []

    def clip(name, original_name, end_frame=None, seconds=None, paired_receiver=False):
        skeleton, bind = (receiver_rig, receiver_bind) if paired_receiver else (rig, actor_bind)
        ratio = target_bind['Root'][1,3] / bind['Pelvis'][1,3]
        decoded = source.decoder.decode(original[original_name])
        end = min(int(decoded['frames'])-1, end_frame if end_frame is not None else int(decoded['frames'])-1)
        duration = seconds if seconds is not None else end/decoded['fps']
        times = np.linspace(0, duration, round(duration*60)+1).tolist()
        frames = {n:[] for n in rest}; worlds = []; travel = []
        entry_pose = source.world(skeleton, decoded, 0)
        initial = entry_pose['Motion_Root'][:3,3]
        toes = ("Ball_L", "Ball_R")
        entry_floor = min(entry_pose[n][1,3] for n in toes) if name=="consume_normal_beatdown_actor" else 0
        for t in times:
            current_source = source.world(skeleton, decoded, min(end, t*decoded['fps']))
            current = {}
            for n in rest:
                src = mapping[n]; parent = parents[n]
                rotation = mirror @ current_source[src][:3,:3] @ np.linalg.inv(bind[src][:3,:3]) @ mirror @ target_bind[n][:3,:3]
                if n in aims:
                    child,source_child=aims[n]
                    rest_direction=target_bind[child][:3,3]-target_bind[n][:3,3]
                    desired=mirror@(current_source[source_child][:3,3]-current_source[src][:3,3])
                    delta=rotation@np.linalg.inv(target_bind[n][:3,:3])
                    rotation=source.rotation_between(delta@rest_direction,desired)@rotation
                elif n.startswith('Leg_'):
                    side=n[-1];rest_direction=target_bind[n][:3,3]-target_bind['Thigh_'+side][:3,3]
                    desired=mirror@(current_source['Ankle_'+side][:3,3]-current_source[src][:3,3])
                    delta=rotation@np.linalg.inv(target_bind[n][:3,:3])
                    rotation=source.rotation_between(delta@rest_direction,desired)@rotation
                position = (current[parent]@rest[n])[:3,3] if parent else target_bind[n][:3,3].copy()
                if not parent:
                    relative = current_source['Pelvis'][:3,3]-current_source['Motion_Root'][:3,3]
                    baseline = bind['Pelvis'][:3,3]-bind['Motion_Root'][:3,3]
                    position += mirror @ (relative-baseline)*ratio
                matrix = np.eye(4); matrix[:3,:3]=rotation; matrix[:3,3]=position
                current[n]=matrix
            if name=='ground_stomp' or (name.endswith('_actor') and not any(k in name for k in ('carry_air','air_throw'))) or name.endswith('_slam'):
                floor=min(foot(current,'L')[1],foot(current,'R')[1])
                for matrix in current.values():matrix[1,3]-=floor
            if name == "consume_normal_beatdown_actor":
                fit_beatdown_stance(current,target_bind,current_source["Pelvis"][1,3],t,source.rotation_between)
                # The throw contains a short authored hop. Flooring both feet on
                # every sample suppressed that lift in both halves of the pair.
                airborne = max(0,min(current_source[n][1,3] for n in toes)-entry_floor)
                lift = airborne*float(np.clip((1-t)/.12,0,1))
                for matrix in current.values():
                    matrix[1,3] += lift
            for n,matrix in current.items():
                parent=parents[n]
                local=np.linalg.inv(current[parent])@matrix if parent else np.linalg.inv(axis)@matrix
                frames[n].append(local.round(8).reshape(-1).tolist())
            worlds.append(current)
            delta=mirror@(current_source['Motion_Root'][:3,3]-initial)*ratio
            delta[2]*=-1  # Placement uses forward; native skin uses render Z.
            travel.append(delta.round(8).tolist())
        # Mandatory movement channel. Scene placement owns paired travel; stomp
        # remains planted, so none of these clips independently move the actor.
        tracks=[{'name':n,'time':times,'transform':frames[n]} for n in rest]
        tracks.append({'name':'Coord','time':times,'transform':[np.eye(4).reshape(-1).tolist() for _ in times]})
        result={'format':'MATRIX','animation':tracks,
                'blacklight':{'durationTicks':math.ceil((duration+.05)*20-1e-5),'transitionSeconds':.05},
                'source':{'animation':original_name,'archiveSha256':source_hash,'sourceFrames':[0,end],
                          'fps':decoded['fps'],'speed':1,'adaptation':'Fixed native bone lengths; Motion_Root travel separated; short receiver holds final frame.'}}
        if paired_receiver:
            result['source']['skeleton'] = 'ped_m_sold_aa_master_skeleton_skeleton'
        save(OUT/(name+'.json'),result); written.append(name+'.json')
        if name == "consume_normal_beatdown_actor":
            result["source"]["stance"] = 'P1 pelvis height during grounded punches, with fixed-length MC legs and arms fitted to the floor and wrist targets.'
        return result, worlds, travel

    if power_package is None:
        stomp, sw, _ = clip('ground_stomp', 'alex_ground_stomp')
        # No FIG owner for this clip was recovered. Derive impact from the minimum
        # raised-foot height after its apex; this is explicitly an adaptation.
        right_heights = np.array([foot(w,'R')[1] for w in sw])
        left_heights = np.array([foot(w,'L')[1] for w in sw])
        foot_heights = left_heights if np.ptp(left_heights)>np.ptp(right_heights) else right_heights
        apex=int(np.argmax(foot_heights[:len(foot_heights)*2//3])); impact=apex+int(np.argmin(foot_heights[apex:len(foot_heights)*4//5]))
        tick=max(2, math.ceil((impact/60+.05)*20-1e-5))
        stomp['blacklight'].update(contactStartTick=tick,contactEndTick=tick+2)
        stomp['source']['contactTiming']='Adapted: lowest foot sample after lift; no recovered FIG hit track.'
        save(OUT/'ground_stomp.json',stomp)
    placement=json.loads((OUT/'consume-placement.data').read_text())
    event_path=OUT/'consume-receiver-events.data'; events=json.loads(event_path.read_text())
    if power_package is not None:
        # Receiver FIG owns effect and absorption; impact is sampled from the
        # wrist-to-head/chest approach of the paired puppets at the FIG speed.
        by_package={
            'alex_hammerfist':('hammer_smush','alex_hammer_grap_headsmush',158,158,158/30,
                                ('HEAD_IMPACT',40/39,[40/39],2.0,2.5)),
            'alex_musclemass':('muscle_heart','alex_grap_muscle_heart_to_head',50,50,50/30,
                                ('CHEST_ABSORB',.5,[.5,20/30],1.33333,1.67)),
        }
        key=power_package.name.split('.')[0]
        specifications=[by_package[key]]
    else:
        specifications=[
            ('carry_sprint','alex_loco_grap_sprint_n',17,17,17/30,None),
            ('carry_air','alex_grap_air_float',54,54,54/30,None),
            ('air_throw','alex_grap_hmn_jump_throw',35,35,35/30,None),
            ('leg_slam','alex_grap_hmn_leg_slam2',73,73,73/30,None),
            ('normal_head_stomp','alex_grap_head_stomp',117,84,117/30,('HEAD_IMPACT',.9,[.9],2.8,3.5)),
            ('normal_beatdown','alex_grap_beatdown',196,196,196/30,('HEAD_IMPACT',50/30,[50/30,60/30,70/30,82/30,99/30],88/30,4.3)),
            ('normal_knee_head','alex_grap_knee_head',47,41,47/30,('HEAD_IMPACT',.6,[.6],.9,1.4)),
        ]
        # Original full-length paired stealth clips. Effect/settlement times below are
        # Minecraft choreography, not decoded FIG damage or completion markers.
        for key, base, end in [
            ('stealth_neck','alex_stealth_consumes_break_neck',114),
            ('stealth_bearhug','alex_stealth_consumes_bearhug_002',120),
            ('stealth_nelson','alex_stealth_consumes_full_nelson_002',103),
            ('stealth_guillotine','alex_stealth_consumes_guillotine_002',104),
            ('stealth_jugular','alex_stealth_consumes_rip_jugular_002',96),
        ]:
            duration=end/30
            specifications.append((key,base,end,end,duration,
                ('CHEST_ABSORB' if key=='stealth_bearhug' else 'HEAD_IMPACT',
                 duration*.35,[duration*.35],duration*.65,duration-.15)))
        for hand in ('one','two'):
            name='block_throw_'+hand+'_slam'
            slam, worlds, _=clip(name,'alex_weap_2hnd_slam')
            heights=np.array([(w['Tool_R'][1,3]+w['Tool_L'][1,3])*.5 for w in worlds])
            apex=int(np.argmax(heights[:len(heights)*2//3]))
            low=apex+int(np.argmin(heights[apex:len(heights)*4//5]))
            slam['source']['releaseTick']=max(2,math.ceil(low/60*20-1e-5))
            slam['source']['releaseTiming']='Minecraft adaptation: lowest palm center after lift; no FIG release event recovered. Actual collision owns damage.'
            save(OUT/(name+'.json'),slam)
    model_axes=np.diag([1.,-1.,1.])
    part_names=['Head','Chest','Arm_R','Arm_L','Thigh_R','Thigh_L','Root','Hand_R','Hand_L','Leg_R','Leg_L']
    for key, base, ae, ve, duration, scene in specifications:
        actor_clip, actor, actor_travel=clip('consume_'+key+'_actor',base+'_gv',ae,duration)
        if key=='air_throw':
            # Receiver Motion_Root advances through frame 18, then holds. This is
            # a Minecraft release cue from the decoded curve, not a FIG marker.
            actor_clip['source'].update(releaseFrame=18,releaseTick=13,
                releaseTiming='Adapted from receiver root becoming stationary at frame 18; 0.05s transition included; no recovered FIG release event.')
            save(OUT/('consume_'+key+'_actor.json'),actor_clip)
        _, victim, victim_travel=clip('consume_'+key+'_victim',base+'_rcv',ve,duration,paired_receiver=scene is not None)
        contacts=[]; projections=[]
        for aw, vw in zip(actor,victim):
            def point(w,n):
                p=w[n][:3,3].copy();p[2]*=-1;return p
            hand=point(aw,'Tool_R'); head=point(vw,'Head')*.9375
            floor=min(foot(vw,'L')[1],foot(vw,'R')[1],*(point(vw,n)[1] for n in ['Head','Tool_R','Tool_L']))*.9375-.08
            row=[*hand,*head,floor,*point(aw,'Chest'),*(point(vw,'Chest')*.9375),*([0.]*33)]
            contacts.append(np.round(row,8).tolist())
            rotation=vw['Root'][:3,:3]@np.linalg.inv(target_bind['Root'][:3,:3])
            translation=(vw['Root'][:3,3]-target_bind['Root'][:3,3])*.9375
            # Skin rotates around the standard humanoid pelvis at .9 blocks.
            frame=[*translation,*quaternion(rotation)]
            for n in part_names:
                relative=np.linalg.inv(rotation)@vw[n][:3,:3]@np.linalg.inv(target_bind[n][:3,:3])
                frame.extend(quaternion(model_axes@relative@model_axes))
            projections.append(np.round(frame,8).tolist())
        placement['contacts'][key]=contacts;placement['projections'][key]=projections
        placement['motion'][key]=victim_travel;placement['actorMotion'][key]=actor_travel
        if scene:
            style,deform,hits,effect,finish=scene
            events['profiles'][key]={'style':style,'deformSeconds':deform,'hitSeconds':hits,'effectSeconds':effect,
                                    'finishSeconds':finish,'settleSeconds':.16,'source':base+'_rcv',
                                    'sourceFrames':[0,ve],'timingNote':('Full source clip; hit/effect/finish are Minecraft adaptation cues, not recovered FIG markers.' if key.startswith('stealth_') else 'Head stomp hit .9s recovered from FIG; other effect/finish/hit times are Minecraft adaptation cues.')}
    save(OUT/'consume-placement.data',placement);save(event_path,events)
    written += ['consume-placement.data','consume-receiver-events.data']
    manifest_path=OUT.parent/'motion-profile-manifest.json'; manifest=json.loads(manifest_path.read_text())
    for name in written:
        raw=(OUT/name).read_bytes();manifest['files'][name]={'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
    manifest['files']=dict(sorted(manifest['files'].items()))
    manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    if power_package is None:
        from bake_consume_contact_cues import bake
        bake()
    print(json.dumps({'files':written,'scale':scale}))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--archive',type=Path,default=Path('D:/PrototypeReference/p1/input/alex.p3d.rz'))
    parser.add_argument('--decoder-directory',type=Path,default=Path('D:/PrototypeReference/p2/tools'))
    parser.add_argument('--power-package',type=Path,help='Export the supplemental receiver pair from this original weapon package.')
    args=parser.parse_args();export(args.archive,args.decoder_directory,args.power_package)
