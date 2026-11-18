"""Static local source/skin audit only; no Java, client, or source asset export.

The source's bare linear skinning is not its final game rendering: the actor
also references PoseFixupBehaviour and CharacterSolverBehaviour. Their runtime
configuration is decoded separately by read_goliath_grounding.py; it does not
establish automatic fist IK or justify a wrist-height correction.
"""
from pathlib import Path
import hashlib, importlib.util, itertools, json, math, re
import numpy as np
import goliath_source_pose as source_pose

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('source_motion',ROOT/'tools/export_encounter_source_motion.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
path,rig_name,ref_name,clips=m.PROFILES['goliath'];raw=path.read_bytes();nodes=m.nodes(path);rig=m.skeleton(nodes,rig_name)
anims={m.decoder.text8(n['payload'],4)[0]:n for n in nodes if n['type']==0x121000}
source_pose.rig_metadata(m,nodes,rig_name)
reference=source_pose.world(m,rig,m.decoder.decode(anims[ref_name]),0);raw_bind=source_pose.world(m,rig,{'tracks':[]},0)
clip=m.decoder.decode(anims[clips['HAMMER']]);inverse_bind=np.array([np.linalg.inv(raw_bind[name]) for name,_,_ in rig])
table=json.loads((ROOT/'src/main/resources/assets/blacklight/models/encounter_goliath_source.json').read_text())
geo=json.loads((ROOT/'src/main/resources/assets/blacklight/geo/goliath_mc.geo.json').read_text())['minecraft:geometry'][0]['bones']
owners={b['name']:b for b in geo};names=table['bones'];pivots=np.array(table['pivots'])/16;scale=table['pixelsPerSourceUnit']/16

def translation(point):
    a=np.eye(4);a[:3,3]=point;return a

def rotation(x,y,z):
    cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    a=np.eye(4);a[:3,:3]=[[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]];return a

def stats(points):
    points=np.asarray(points)
    return {'min':points.min(axis=0).tolist(),'max':points.max(axis=0).tolist(),'mean':points.mean(axis=0).tolist()}

def quaternion(angles):
    x,y,z=np.asarray(angles)/2
    cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    return [sx*cy*cz-cx*sy*sz,cx*sy*cz+sx*cy*sz,cx*cy*sz-sx*sy*cz,cx*cy*cz+sx*sy*sz]

def runtime_corners(frame,key='HAMMER'):
    matrices=[];result={}
    frames=table['clips'][key]['frames'];lo=int(frame);hi=min(lo+1,len(frames)-1);weight=frame-lo
    rows=[]
    for a,b in zip(frames[lo],frames[hi]):
        q=m.slerp(quaternion(a[:3]),quaternion(b[:3]),weight)
        rows.append(m.euler(m.quat_matrix(q))+list(np.asarray(a[3:])*(1-weight)+np.asarray(b[3:])*weight))
    for i,name in enumerate(names):
        if name.startswith('source_parent_scale_'):
            rows[i][6:9]=list(1/np.asarray(rows[table['parents'][i]][6:9]))
    for i,p in enumerate(rows):
        scale_matrix=np.diag([*p[6:9],1]) if len(p)>=9 else np.eye(4)
        local=translation(pivots[i]+np.array(p[3:6])/16)@rotation(*p[:3])@scale_matrix@translation(-pivots[i])
        matrices.append(local if table['parents'][i]<0 else matrices[table['parents'][i]]@local)
    for name,bone in owners.items():
        mapped=name
        while mapped not in names:
            assert not owners[mapped].get('rotation') and not owners[mapped].get('position')
            mapped=owners[mapped]['parent']
        for ci,cube in enumerate(bone.get('cubes',[])):
            p=np.array(cube.get('pivot',[0,0,0]))*[-1,1,1]/16
            r=np.radians(cube.get('rotation',[0,0,0]))*[-1,-1,1]
            matrix=rotation(0,math.pi,0)@matrices[names.index(mapped)]@translation(p)@rotation(*r)@translation(-p)
            points=[]
            for corner in itertools.product([0,1],repeat=3):
                point=(np.array(cube['origin'])+np.array(cube['size'])*corner)*[-1,1,1]/16
                points.append((matrix@np.r_[point,1])[:3]+[0,.01,0])
            result[(name,ci)]=points
    return result

skins=[];skin_evidence=[]
for node in nodes:
    if node['type']!=0x10040:continue
    name,_=m.decoder.text8(node['payload'],4)
    if name not in ['behemoth_L_ArmShape000_skin','behemoth_SinueShape000_skin']:continue
    skin,_=m.decoder.m.streams(node)
    group_weight=np.where(np.isin(skin['indices'],[37,38,39,40]),skin['weights'],0).sum(axis=1)
    selected=group_weight>.5
    assert np.max(np.abs(skin['weights'].sum(axis=1)-1))<.00001
    skins.append((name,skin,selected))
    skin_evidence.append({'name':name,'offset':node['offset'],'fistMajorityVertices':int(selected.sum()),'criterion':'sum of positive weights assigned to Wrist_L/Shield_L_2/Shield_L_3/Shield_L_1 > 0.5; full four-influence skinning retained for those vertices'})
comparisons=[]
for frame in [0,42,48,52,88]:
    current=source_pose.world(m,rig,clip,frame);rebase=reference['Motion_Root']@np.linalg.inv(current['Motion_Root'])
    current={name:rebase@matrix for name,matrix in current.items()};skin_matrices=np.array([current[name] for name,_,_ in rig])@inverse_bind
    source_points=[];source_wrist_points=[]
    for name,skin,selected in skins:
        p=np.c_[skin['position'][:,:3],np.ones(len(skin['position']))];out=np.zeros_like(p)
        for k in range(4):out+=np.einsum('nij,nj->ni',skin_matrices[skin['indices'][:,k]],p)*skin['weights'][:,k:k+1]
        # source -> mirror X -> renderer yaw 180. Both sets use the same
        # Minecraft-sized root and +.01 model lift for comparison only.
        world=out[:,:3]*[1,1,-1]*scale+[0,.01,0]
        source_points.extend(world[selected]);wrist=((skin['indices']==37)&(skin['weights']>.5)).any(axis=1);source_wrist_points.extend(world[wrist])
    corners=runtime_corners(frame)
    giant=[point for (bone,_),points in corners.items() if bone=='right_hand' or bone.startswith('right_finger_') for point in points]
    thin=[point for (bone,_),points in corners.items() if bone=='left_hand' or bone.startswith('left_finger_') for point in points]
    cube_minima=[{'bone':bone,'cube':ci,'minY':min(p[1] for p in points)} for (bone,ci),points in corners.items() if bone=='right_hand' or bone.startswith('right_finger_')]
    comparisons.append({'frame':frame,'sourceWristY':float(current['Wrist_L'][1,3]*scale+.01),'sourceFullySkinnedFist':stats(source_points),'sourceWristMajoritySubset':stats(source_wrist_points),'authoredGiantSkin':stats(giant),'authoredThinSkin':stats(thin),'giantCubeMinima':cube_minima})

fixtures=json.loads((ROOT/'src/test/resources/goliath-skin-reference.json').read_text())['fixtures'];maximum=0
for f in fixtures:
    corners=runtime_corners(f['frame'],f['clip'])[(f['bone'],f['cube'])]
    error=max(min(np.linalg.norm(np.array(p)-corner) for corner in corners) for p in f['points']);maximum=max(maximum,error);assert error<.0002
assert any(f['bone']=='right_finger_3' and f['frame']==48 for f in fixtures)

controller_nodes=[]
for offset in [5696716,5696839,5700996,5701191,5701247,5701362,5701525,5701640,5702063]:
    node=next(n for n in nodes if n['offset']==offset);payload=bytes(node['payload'])
    row={'offset':offset,'chunkType':hex(node['type']),'payloadBytes':len(payload),'payloadSha256':hashlib.sha256(payload).hexdigest()}
    if offset==5702063:
        row['references']=[{'text':name,'absoluteOffset':offset+12+payload.index(name.encode())} for name in ['PoseFixupBehaviour','BehemothPoseFixupProperties','CharacterSolverBehaviour','BehemothLimbs','WallRunnerCharacterSolverProperties']]
    else:
        row['payloadHex']=payload.hex();row['readableNames']=[v.decode() for v in re.findall(rb'[A-Za-z_][A-Za-z0-9_:]{3,80}',payload)]
    controller_nodes.append(row)

fig=json.loads(Path('D:/PrototypeReference/p2/creature-fig-structure.json').read_text())
known=['IKSurfaceFixup','EnableLimbIK','CharacterIK','AnchorToGround','ControlRoot','GroundSlide']
fig_ground=[{'offset':r['offset'],'type':r['type'],'owner':r['owner']} for r in fig['records'] if 'behemoth' in r['file'] and r['type'] in known]
impact=next(r for r in comparisons if r['frame']==48);worst=min(impact['giantCubeMinima'],key=lambda r:r['minY'])
worst_corners=np.array(runtime_corners(48)[(worst['bone'],worst['cube'])]);center_y=float(worst_corners[:,1].mean());vertical_radius=float(np.ptp(worst_corners[:,1])/2)
report={'schema':1,'source':str(path),'sourceSha256':table['sourceSha256'],'method':'Full original four-weight linear blend skinning, reference Motion_Root travel/heading rebase, identical MC scale/axes/render lift. Authored geometry uses the exact exported hierarchical pose/cube-pivot transforms; these are static calculations, not observed game rendering.',
        'skinEvidence':skin_evidence,'comparisons':comparisons,'worstImpactCube':worst,
        'attribution':{'wristY':impact['sourceWristY'],'cubeCenterY':center_y,'cubeVerticalHalfSpan':vertical_radius,'centerBelowWrist':impact['sourceWristY']-center_y,'cubeBelowWrist':impact['sourceWristY']-worst['minY'],'sourceFistMinimumY':impact['sourceFullySkinnedFist']['min'][1],'authoredExcessBelowBareSource':impact['sourceFullySkinnedFist']['min'][1]-worst['minY'],'finding':'The deepest cuboid is right_finger_3 cube 0, the last authored knuckle. Mapping and native receiving geometry are correct. Extra extension belongs to authored shape/bind fitting, while bare source skin also crosses the root plane before the original pose/character solver. This does not isolate a safe uniform scale correction.'},
        'controllerEvidence':controller_nodes,'knownFigGroundTracks':fig_ground,
        'controllerBoundary':'See goliath-grounding-runtime-audit.json: JointStuckTrack begin/end message producer and consumer are traced, but no such track/activation is present in local behemoth raw/FIG data. Pure3D local TRS, segment-scale compensation, parent accumulation and inverse-bind palette are recovered. Actual post-fixup pose, external event participation, global scheduler and entity-to-ground transform remain unproven. Installed components are not proof of automatic fist IK.',
        'contactCheck':{'fixtureCount':len(fixtures),'corners':len(fixtures)*8,'maxCornerErrorBlocks':maximum,'mapping':{'right_hand':table['mapping']['right_hand'],'left_hand':table['mapping']['left_hand']},'conclusion':'Both rendered giant hand and unmapped knuckles use right_hand -> Wrist_L. Worst cuboid is included in independent source-world fixture; no receiving-only ground clamp or shrink.'},
        'decision':'Do not change runtime geometry or wrist trajectory from this audit alone. Uniform/ground-height changes would conceal an unresolved source solver and shape-fitting problem. Decode controller semantics or obtain source-frame reference of the post-fixup pose before another support correction.',
        'validation':'Source decoding, numeric transforms and resource inspection only. No Gradle, Java tests, GameTests or client.'}
out=ROOT/'art/encounters/goliath-giant-support-audit.json';out.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'report':str(out),'worstCube':worst,'sourceFistMinimumY':impact['sourceFullySkinnedFist']['min'][1],'excess':report['attribution']['authoredExcessBelowBareSource'],'maxCornerErrorBlocks':maximum,'controllerNodes':len(controller_nodes)}))
