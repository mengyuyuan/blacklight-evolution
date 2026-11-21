"""Bind the existing authored cubes to the actual P2 reference joints.

The old exporter retained authored limb lengths and only Character_Root Y
translation. That cannot preserve source wrist positions. This exporter first
fits the authored bind mesh, then derives every local pose from complete source
world transforms. Source mesh/UV/texture data are never copied.
"""
from pathlib import Path
import copy, hashlib, importlib.util, json, math, sys
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('source_motion',ROOT/'tools/export_encounter_source_motion.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
import goliath_source_pose as source_pose
from goliath_neck_geometry import add_neck
from goliath_foot_geometry import add_feet
np=m.np
path,rig_name,reference_name,clips=m.PROFILES['goliath']
clips=dict(clips,CHARGE_RETURN='behemoth_bullrush_to_aggro',
    TAUNT_TELL='behemoth_biped_to_aggro_head_tell',TAUNT='behemoth_com_biped_aggro_to_biped',
    TAUNT_HOWL='behemoth_com_biped_howl',
    arm_fall='behemoth_com_claw_stun_flail',
    back_slam='behemoth_aggro_crawl_bigair_attack',
    face_stun='behemoth_com_crawl_stuned_head',
    finish_idle='behemoth_com_crawl_stuned_idle',
    crippled_walk='behemoth_crawl_dismembered',
    spike_windup='behemoth_com_crawl_front_spike_all',
    back_recover='behemoth_com_prone_stuned_recover',
    crawl_death='behemoth_com_crawl_death')
nodes=m.nodes(path);rig=m.skeleton(nodes,rig_name)
anims={m.decoder.text8(n['payload'],4)[0]:n for n in nodes if n['type']==0x121000}
decoded={name:m.decoder.decode(anims[name]) for name in set(clips.values())|{reference_name}}
rig_evidence=source_pose.rig_metadata(m,nodes,rig_name)
def source_world(clip,frame):return source_pose.world(m,rig,clip,frame)
reference=source_world(decoded[reference_name],0)
geo_path=ROOT/'src/main/resources/assets/blacklight/geo/goliath_mc.geo.json'
bind_path=ROOT/'art/encounters/goliath-authored-bind.geo.json'
if not bind_path.exists():bind_path.write_bytes(geo_path.read_bytes())
original=json.loads(bind_path.read_text());result=copy.deepcopy(original)
old={b['name']:b for b in original['minecraft:geometry'][0]['bones']}
geo={b['name']:b for b in result['minecraft:geometry'][0]['bones']}
mapping=m.mapping_for('goliath',rig,old)
# The authored names describe the artist's geometry, not the source anatomy:
# its right_* arm is the giant plated fist. In the P2 skin that is the L arm
# (Wrist_L and Shield_L_1..3); Wrist_R drives the slender opposing blade.
# Match the entire chain by the weighted skin, not by the final letter.
for side,suffix in [('right','L'),('left','R')]:
    mapping.update({side+'_arm':'Shoulder_'+suffix,side+'_forearm':'Elbow_'+suffix,side+'_hand':'Wrist_'+suffix})
order=[name for name in old if name in mapping]
S=np.diag([-1.,1.,1.]);old_pivots={name:S@np.array(b['pivot']) for name,b in old.items()}
scale=abs(old_pivots['head'][1])/abs(reference['Head'][1,3])
new_pivots={name:S@reference[source][:3,3]*scale for name,source in mapping.items()}

raw_bind=source_world({'tracks':[]},0)
thin_fit={'design':'The authored fingers retain the parent wrist orientation.'}
aims={'torso':('head','Head')}
for side,suffix in [('left','L'),('right','R')]:
    arm_suffix='R' if side=='left' else 'L'
    aims.update({side+'_arm':(side+'_forearm','Elbow_'+arm_suffix),side+'_forearm':(side+'_hand','Wrist_'+arm_suffix),side+'_leg':(side+'_shin','Knee_'+suffix)})
bind={};factors={}
for name,bone in old.items():
    if name in mapping:
        rotation=np.eye(3);factor=1.
        if name in aims:
            child,source_child=aims[name]
            before=old_pivots[child]-old_pivots[name]
            after=new_pivots[child]-new_pivots[name]
            rotation=m.rotation_between(before,after)
            factor=float(np.linalg.norm(after)/np.linalg.norm(before))
        elif bone.get('parent') in factors:
            factor=factors[bone['parent']]
            # Leaf geometry must inherit the parent's bind orientation as well
            # as length scale; otherwise the fingers retain the old rig's axis.
            rotation=bind[bone['parent']][:3,:3]/factor
        if name=='left_hand':
            # Closed, compact claws clear the support floor during the giant fist's slam.
            factor *= .48
        transform=np.eye(4);transform[:3,:3]=rotation*factor
        transform[:3,3]=new_pivots[name]-transform[:3,:3]@old_pivots[name]
        bind[name]=transform;factors[name]=factor
    else:
        assert not bone.get('rotation') and not bone.get('position'),('Unmapped animated/static bone requires explicit transform',name)
        bind[name]=bind[bone['parent']];factors[name]=factors[bone['parent']]
        new_pivots[name]=(bind[name]@np.r_[old_pivots[name],1])[:3]
    geo[name]['pivot']=[round(float(v),6) for v in S@new_pivots[name]]
    for cube in geo[name].get('cubes',[]):
        origin=np.array(cube['origin']);size=np.array(cube['size'])
        pivot=S@np.array(cube.get('pivot',[0,0,0]))
        angles=np.radians(cube.get('rotation',[0,0,0]))*[-1,-1,1]
        q=m.quat_matrix([0,0,0,1])
        x,y,z=angles
        cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
        q=np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])
        center=pivot+q@(S@(origin+size/2)-pivot)
        target=(bind[name]@np.r_[center,1])[:3]
        rotation=bind[name][:3,:3]/factors[name]@q
        target_size=size*factors[name];target_center=S@target
        cube['origin']=[round(float(v),6) for v in target_center-target_size/2]
        cube['size']=[round(float(v),6) for v in target_size]
        cube['pivot']=[round(float(v),6) for v in target_center]
        cube['rotation']=[round(float(v),6) for v in np.degrees(m.euler(rotation))*[-1,-1,1]]
        if 'inflate' in cube:cube['inflate']*=factors[name]
        # Existing UV rectangles, texture and cube count stay.

# The authored torso pivot is at its WAIST (Y=56), not its chest. Mapping
# that pivot and all 19 cubes to Spine_3 moved the lowest abdomen up to the
# chest and disconnected it from both hips. The original TorsoShape001 skin
# actually weights Pelvis / Spine_1 / Spine_2 / Spine_3. Reuse the existing
# flesh sections and plates, splitting the middle flesh cube at its centre
# for the two waist joints; do not add a gap-covering volume.
# Keep every non-torso bind fit exactly as before (including head and arms).
torso_original=copy.deepcopy(old['torso'])
torso_cross_scale=factors['torso']
body_sections={
    'torso':dict(source='Spine_3',pivot=[0,92,4],cubes=[0,3,4,6,7,10,11,12,14,15,18]),
    'torso_middle':dict(source='Spine_2',pivot=[0,73.2,4],cubes=[1,5,8,13,16],half='upper'),
    'torso_waist':dict(source='Spine_1',pivot=[0,56,4],cubes=[1,9,17],half='lower'),
    'torso_lower':dict(source='Pelvis',pivot=[0,56,4],cubes=[2]),
}
assert sorted(i for section in body_sections.values() for i in section['cubes'])==sorted([*range(19),1])
body_evidence=[]
for name,section in body_sections.items():
    bone=dict(name=name,parent='root',pivot=section['pivot'],
              cubes=[copy.deepcopy(torso_original['cubes'][i]) for i in section['cubes']])
    if 'half' in section:
        cube=bone['cubes'][0];assert section['cubes'][0]==1 and not cube.get('rotation')
        height=cube['size'][1];cube['size'][1]=height/2
        if section['half']=='upper':cube['origin'][1]+=height/2
        for face in ['north','south','east','west']:
            uv=cube['uv'][face];v=uv['uv_size'][1];uv['uv_size'][1]=v/2
            if section['half']=='lower':uv['uv'][1]+=v/2
    mapping[name]=section['source'];old_pivots[name]=S@np.asarray(bone['pivot'])
    new_pivots[name]=S@reference[section['source']][:3,3]*scale
    # Match adjacent source spine spacing. Using the old head/waist vector to
    # size the whole chest was the same wrong anatomical-anchor assumption.
    lower=name in ['torso_lower','torso_waist']
    low,high=('Pelvis','Spine_2') if lower else ('Spine_2','Spine_3')
    authored_length=17.2 if lower else 18.8
    raw_axis=S@(raw_bind[high][:3,3]-raw_bind[low][:3,3])*scale
    raw_rotation=m.rotation_between(np.array([0.,1.,0.]),raw_axis)
    # Fit in the common raw skin-bind coordinates before posing the mesh.
    # The idle reference already twists the pelvis/chest. A minimal alignment
    # to its vertical axis alone loses that twist and misplaces hip sockets.
    source=section['source']
    reference_delta=reference[source][:3,:3]@np.linalg.inv(raw_bind[source][:3,:3])
    rotation=S@reference_delta@S@raw_rotation
    longitudinal_scale=float(np.linalg.norm(raw_axis)/authored_length)
    factor=torso_cross_scale
    if name=='torso_lower':
        authored_hip_span=np.linalg.norm(old_pivots['left_leg']-old_pivots['right_leg'])
        factor=float(np.linalg.norm(raw_bind['Hip_L'][:3,3]-raw_bind['Hip_R'][:3,3])*scale/authored_hip_span)
    # The old chest width/depth already fit the surrounding unchanged arms and
    # head. Fit only the authored longitudinal spacing/length to the spine,
    # rather than widening every plate and burying the head in a swollen chest.
    # Cubes remain rigid cuboids (not a claim of native multiweight skinning).
    stretch=longitudinal_scale/factor
    for cube in bone['cubes']:
        anchor=bone['pivot'][1]
        cube['origin'][1]=anchor+(cube['origin'][1]-anchor)*stretch
        cube['size'][1]*=stretch
        if 'pivot' in cube:cube['pivot'][1]=anchor+(cube['pivot'][1]-anchor)*stretch
    old[name]=bone
    transform=np.eye(4);transform[:3,:3]=rotation*factor
    transform[:3,3]=new_pivots[name]-transform[:3,:3]@old_pivots[name]
    bind[name]=transform;factors[name]=factor
    geo[name]=copy.deepcopy(bone);geo[name]['pivot']=[round(float(v),6) for v in S@new_pivots[name]]
    for cube in geo[name]['cubes']:
        size=np.array(cube['size']);pivot=S@np.array(cube.get('pivot',[0,0,0]))
        x,y,z=np.radians(cube.get('rotation',[0,0,0]))*[-1,-1,1]
        cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
        q=np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])
        center=pivot+q@(S@(np.array(cube['origin'])+size/2)-pivot)
        target_center=S@(transform@np.r_[center,1])[:3];target_size=size*factor
        cube['origin']=[round(float(v),6) for v in target_center-target_size/2]
        cube['size']=[round(float(v),6) for v in target_size]
        cube['pivot']=[round(float(v),6) for v in target_center]
        cube['rotation']=[round(float(v),6) for v in np.degrees(m.euler(rotation@q))*[-1,-1,1]]
        if 'inflate' in cube:cube['inflate']*=factor
    body_evidence.append(dict(bone=name,source=section['source'],originalCubeIndices=section['cubes'],
        authoredPivot=section['pivot'],sourceReferencePivotPixels=(S@new_pivots[name]).tolist(),
        sourceSpan=[low,high],authoredSpanPixels=authored_length,crossSectionScale=factor,
        originalMiddleCubeHalf=section.get('half'),
        orientation='Raw-bind spine axis followed by source reference * inverse raw joint basis; preserves source pelvis/chest twist',
        crossSectionBasis='Source hip span / authored hip span' if name=='torso_lower' else 'Unchanged authored chest cross-section scale',
        longitudinalScale=longitudinal_scale,authoredLongitudinalFit=stretch))
order=[name for name in old if name in mapping]

def runtime_matrix(source):
    value=np.eye(4);value[:3,:3]=S@source[:3,:3]@S
    value[:3,3]=S@source[:3,3]*scale
    return value

def pose_matrix(values,pivot):
    x,y,z=values[:3];cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    r=np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])@np.diag(values[6:9])
    value=np.eye(4);value[:3,:3]=r;value[:3,3]=pivot+values[3:6]-r@pivot
    return value

def pose_row(rotation,translation,scales=(1,1,1)):
    return [round(float(v),8) for v in m.euler(rotation)+list(translation)+list(scales)]

# Preserve source local TRS and the real ancestor chain. A nonuniform scale
# under rotated descendants creates shear: decomposing that WORLD delta into
# one TRS would discard it. Two CONSTANT inverse-bind factors attach the
# authored mesh to the source chain without framewise SVD or axis switching.
rig_index={name:i for i,(name,_,_) in enumerate(rig)};required=set()
# CollisionHitAction walks the inclusive source joint-index interval, not only
# skinned ancestors. Bullrush Elbow_L..Wrist_L includes unweighted Chest_L_End.
# Keep that empty native helper so damage and the displayed pose use one rig.
charge_joints=[rig[i][0] for i in range(rig_index['Elbow_L'],rig_index['Wrist_L']+1)]
assert charge_joints==['Elbow_L','Chest_L_End','Forarm_L','Wrist_L']
taunt_joints=[rig[i][0] for i in range(rig_index['Forarm_L'],rig_index['Middle_Base_L']+1)]
assert len(taunt_joints)==9
# These unweighted finger joints are source Hit origins, not new skin drivers.
for name in set(mapping.values())|set(charge_joints)|set(taunt_joints):
    index=rig_index[name]
    while True:
        required.add(index)
        if index==0:break
        index=rig[index][1]
source_names=[name for i,(name,_,_) in enumerate(rig) if i in required]
source_bones=['source_'+name for name in source_names]
pose_names=[];pose_pivots=[];pose_parents=[];helpers=[]
for source in source_names:
    index=rig_index[source];parent=-1
    if index>0:
        parent=pose_names.index('source_'+rig[rig[index][1]][0])
        # Original segment-scale compensation is a PRE-rotation inverse scale
        # with an uncompensated translation. It needs its own TRS helper.
        bridge='source_parent_scale_'+source
        helpers.append(dict(name=bridge,parent=pose_names[parent],pivot=[0,0,0]))
        pose_names.append(bridge);pose_parents.append(parent);pose_pivots.append(np.zeros(3))
        parent=len(pose_names)-1
    bone='source_'+source
    helpers.append(dict(name=bone,pivot=[0,0,0],**({} if parent<0 else {'parent':pose_names[parent]})))
    pose_names.append(bone);pose_parents.append(parent);pose_pivots.append(np.zeros(3))
constant_rows={};reference_runtime={name:runtime_matrix(value) for name,value in reference.items()}
for bone in order:
    inverse=np.linalg.inv(reference_runtime[mapping[bone]])
    u,singular,vt=np.linalg.svd(inverse[:3,:3])
    if np.linalg.det(u)<0:u[:,-1]*=-1;vt[-1,:]*=-1
    assert np.linalg.det(u)>0 and np.linalg.det(vt)>0
    assert np.max(np.abs(u@np.diag(singular)@vt-inverse[:3,:3]))<1e-10
    helper='source_bind_'+bone
    pose_names.extend([helper,bone]);pose_pivots.extend([np.zeros(3),new_pivots[bone]])
    pose_parents.extend([pose_names.index('source_'+mapping[bone]),len(pose_names)-2])
    helpers.append(dict(name=helper,parent='source_'+mapping[bone],pivot=[0,0,0]))
    geo[bone]['parent']=helper
    constant_rows[helper]=pose_row(u,inverse[:3,3],singular)
    constant_rows[bone]=pose_row(vt,vt@new_pivots[bone]-new_pivots[bone])
result['minecraft:geometry'][0]['bones']=helpers+list(geo.values())
table={'schema':1,'source':str(path),'sourceSha256':hashlib.sha256(path.read_bytes()).hexdigest(),
       'skeleton':rig_name,'reference':reference_name,'bones':pose_names,'mapping':mapping,
       'pivots':[p.tolist() for p in pose_pivots],'parents':pose_parents,'pixelsPerSourceUnit':scale,
       'sourceHierarchy':dict(zip(source_bones,source_names)),
       'collisionOnlySourceJoints':[name for name in charge_joints if name not in mapping.values()],
       'scaleHandling':'Source local ROT/TRAN/SCL and actual ancestors; pre-rotation reciprocal-parent-scale helpers implement native segment-scale compensation without scaling the child translation. Two constant inverse-reference-bind factors retain affine shear without per-frame decomposition.',
       'rootMotion':'Full source joint transforms; source Motion_Root travel/heading removed with referenceMotionRoot * inverse(sampledMotionRoot). All articulated translations are retained relative to the rebound authored bind mesh.',
       'clips':{}}
errors=[];wrist_samples=[];scale_evidence=[]
for key,name in clips.items():
    clip=decoded[name];frames=[];max_error=0;max_affine_error=0
    for track in clip['tracks']:
        if track['tag']=='SCL':
            values=np.asarray(track['values'])
            scale_evidence.append(dict(clip=key,source=name,animationChunkOffset=anims[name]['offset'],joint=track['bone'],min=values.min(axis=0).tolist(),max=values.max(axis=0).tolist(),drivesAuthoredSkin=track['bone'] in source_names))
    for frame in range(int(clip['frames'])):
        current=source_world(clip,frame)
        motion=reference['Motion_Root']@np.linalg.inv(current['Motion_Root'])
        current={name:motion@mat for name,mat in current.items()}
        runtime={name:runtime_matrix(value) for name,value in current.items()}
        rows=dict(constant_rows)
        sampled_locals=source_pose.locals(m,rig,clip,frame)
        for source in source_names:
            index=rig_index[source];parent=rig[index][1]
            local=runtime[source] if index==0 else runtime_matrix(sampled_locals[index])
            scales=np.linalg.norm(local[:3,:3],axis=0);rotation=local[:3,:3]/scales
            assert np.max(np.abs(rotation.T@rotation-np.eye(3)))<1e-5,(key,frame,source,'non-TRS source local')
            position=local[:3,3]
            if index>0:
                compensation=1/np.linalg.norm(sampled_locals[parent][:3,:3],axis=0) if source!='Middle_Base_L' else np.ones(3)
                rows['source_parent_scale_'+source]=pose_row(np.eye(3),position,compensation)
                position=np.zeros(3)
            rows['source_'+source]=pose_row(rotation,position,scales)
        actual={};row=[]
        for i,bone in enumerate(pose_names):
            values=rows[bone];row.append(values)
            local=pose_matrix(values,pose_pivots[i]);parent=pose_parents[i]
            actual[bone]=(np.eye(4) if parent<0 else actual[pose_names[parent]])@local
        for bone in order:
            source=mapping[bone]
            target=runtime[source][:3,3]
            desired=runtime[source]@np.linalg.inv(reference_runtime[source])
            affine_error=float(np.max(np.abs(actual[bone]-desired)))
            max_affine_error=max(max_affine_error,affine_error)
            assert affine_error<.0002,(key,frame,bone,'affine',affine_error)
            point=(actual[bone]@np.r_[new_pivots[bone],1])[:3]
            error=float(np.linalg.norm(point-target)/16);max_error=max(max_error,error)
            assert error<.0001,(key,frame,bone,error)
            if key=='HAMMER' and frame in [0,42,48,49,52,88] and bone.endswith('_hand'):
                wrist_samples.append(dict(frame=frame,bone=bone,sourceBlocks=(target/16).tolist(),runtimeBlocks=(point/16).tolist(),error=error))
        frames.append(row)
    table['clips'][key]={'source':name,'fps':clip['fps'],'frames':frames,'seconds':(clip['frames']-1)/clip['fps']}
    errors.append(dict(clip=key,frames=len(frames),maxJointErrorBlocks=max_error,maxAffineElementErrorPixels=max_affine_error))
add_neck(result,table)
add_feet(result,table,m,rig,decoded,nodes)
geo_path.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
(ROOT/'src/main/resources/assets/blacklight/models/encounter_goliath_source.json').write_text(json.dumps(table,separators=(',',':')),encoding='utf-8')
report={'schema':1,'status':'source_bind_and_pose_corrected_not_native_validated',
    'rootCause':'Old retargeting retained authored pivot positions/limb lengths, omitted articulated translations and retained only Character_Root vertical displacement. Correct direction alone cannot preserve endpoints on a differently proportioned hierarchy.',
    'solution':'Original limb bind fits are retained. The formerly chest-only torso is rebound through Pelvis/Spine_1/Spine_2/Spine_3, retaining its authored cross-sections except the pelvis fitted to the actual hip span; one middle cube is partitioned with continuous side UVs and longitudinal lengths follow source spine spacing. True source ancestor TRS including SCL and original reciprocal-parent-scale compensation drives the mesh through two constant inverse-reference-bind factors. GoliathMotion recomputes compensation after interpolation/blending. Authored giant right arm maps to source L arm and slender left arm to source R according to actual skin weights, not name suffixes. No endpoint height clamp or invented keyframes.',
    'scaleCorrection':{'cause':'Shared Python source reader ignored SCL. Goliath now uses its own verified TRS reader; shared encounter readers are untouched.','sourceHierarchyBones':len(source_bones),'tracks':scale_evidence,'rigAuxiliaryFields':rig_evidence,'runtimeEvidence':'goliath-grounding-runtime-audit.json: original Pure3D local/world/skin palette pipeline','boundary':'Animated SCL affects source R slender arm; source L giant-fist support gap is not explained or fixed by this change.'},
    'source':str(path),'sourceSha256':table['sourceSha256'],'authoredBindInput':str(bind_path),'bindFactors':factors,'thinHandBindFit':thin_fit,'torsoBindCorrection':body_evidence,'checks':errors,'wristSamples':wrist_samples,
    'conventions':{'geometryUnits':'pixels; 16 pixels per Minecraft block','sourceScalePixelsPerUnit':scale,'sourceToRuntimeAxes':'mirror X using diag(-1,1,1); source -Z is forward','runtimeRotation':'Rz * Ry * Rx','worldYaw':'rotateY(180 - entityYaw) after source pose; render origin Y offset 0.01','unmappedChildren':'Weak plates/fingers retain authored cuboids and static zero bone rotation/translation; only their mapped ancestor moves. Their pivot translations cancel around zero rotation. P2 left finger chains carry no positive skin weights and are not blindly used as deformation joints.','hitScaling':'FIG spheres were already converted with the same pixelsPerSourceUnit/16 by build_goliath_slam.py; contact performs no second geometry scale.'},
    'boundary':['Numerical source-joint agreement is not rendered geometry/player acceptance','Only front slam has source FIG timing/contact; other move/phase clocks remain MC adapters','Original source meshes/textures are not imported','Broad contact/terrain/receive-volume acceptance remains pending','Slender-hand side and bind-axis correction clears its front-slam impact fingers, without modifying source joints. Giant-hand support/volume and external source controller semantics remain unresolved; unused source finger chains are not new deformation joints. No endpoint clamp or invented pose is applied.']}
(ROOT/'art/encounters/goliath-bind-correction.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')

# Independent receiving-skin fixtures: source world matrices act directly on
# original authored corners through the bind fit, bypassing both the exported
# pose rows and the rewritten geometry. These catch either Java path drifting.
fixtures=[];body_fixtures=[]
eruption_track=next(t for t in decoded[clips['ERUPTION']]['tracks'] if t['tag']=='SCL' and t['bone']=='Shoulder_R')
eruption_scales=[m.track_at(eruption_track,frame)[0] for frame in range(int(decoded[clips['ERUPTION']]['frames']))]
fixture_frames=[('HAMMER',frame) for frame in [0,42,48,48.5,52,88]]+[('ERUPTION',int(np.argmin(eruption_scales))),('ERUPTION',int(np.argmax(eruption_scales)))]
legacy_fixture_frames=set(fixture_frames)
fixture_frames+=[('idle',0),('walk',20),('CHARGE',100)]
for key,frame in fixture_frames:
    current=source_world(decoded[clips[key]],frame)
    motion=reference['Motion_Root']@np.linalg.inv(current['Motion_Root'])
    owners=['torso','torso_middle','torso_waist','torso_lower','left_leg','right_leg']
    if (key,frame) in legacy_fixture_frames:owners+=['left_hand','left_finger_2','right_hand','right_finger_3','weak_0','weak_1']
    for owner in owners:
        mapped=owner
        while mapped not in mapping:mapped=old[mapped]['parent']
        source=mapping[mapped];world=motion@current[source]
        r=S@world[:3,:3]@np.linalg.inv(reference[source][:3,:3])@S
        d=np.eye(4);d[:3,:3]=r;d[:3,3]=S@world[:3,3]*scale-r@new_pivots[mapped]
        cube=old[owner]['cubes'][0];pivot=S@np.array(cube.get('pivot',[0,0,0]));angles=np.radians(cube.get('rotation',[0,0,0]))*[-1,-1,1]
        x,y,z=angles;cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
        q=np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])
        points=[]
        for x in [0,1]:
            for y in [0,1]:
                for z in [0,1]:
                    point=S@(np.array(cube['origin'])+np.array(cube['size'])*[x,y,z])
                    point=pivot+q@(point-pivot)
                    point=(d@bind[owner]@np.r_[point,1])[:3]/16
                    point=point*[-1,1,-1]+[0,.01,0]
                    points.append(point.tolist())
        target=body_fixtures if owner in ['torso','torso_middle','torso_waist','torso_lower','left_leg','right_leg'] else fixtures
        target.append(dict(clip=key,frame=frame,bone=owner,cube=0,points=points))
fixture_path=ROOT/'src/test/resources/goliath-skin-reference.json';fixture_path.parent.mkdir(parents=True,exist_ok=True)
fixture_path.write_text(json.dumps(dict(sourceSha256=table['sourceSha256'],method='Original source world transforms and original authored cube corners, independent of exported Java pose/geometry',fixtures=fixtures),indent=2)+'\n',encoding='utf-8')
(ROOT/'src/test/resources/goliath-torso-reference.json').write_text(json.dumps(dict(sourceSha256=table['sourceSha256'],method='Direct source skeleton matrices applied to independently fitted authored torso/leg corners, without exported pose rows or geo coordinates. Torso keeps original cross-sections and UVs, with the recorded longitudinal fit.',fixtures=body_fixtures),indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(clips=len(errors),frames=sum(e['frames'] for e in errors),maxJointErrorBlocks=max(e['maxJointErrorBlocks'] for e in errors),wristAtImpact=[x for x in wrist_samples if x['frame']==48])))
