"""Retain authored cuboids/UVs while binding them to P1's actual skin joints.

Original mesh vertices are read only to classify anatomical owners. They are not
exported. Native local ROT/TRAN/SCL helpers retain nonuniform scale and inherited
shear; static inverse-bind leaves keep the authored bind geometry unchanged.
"""
from pathlib import Path
import copy, hashlib, importlib.util, json, math, re, struct, sys
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('greene_source_motion',ROOT/'tools/export_encounter_source_motion.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
np=m.np
ASSETS=ROOT/'src/main/resources/assets/blacklight'
source,rig_name,reference_name,clips=m.PROFILES['elizabeth_greene']
# A corpse owns a captured segment independently of the live MotherCore. The
# same source clip contains all three Slug chains; callers select one segment.
# Keep the shared profile unchanged so living action selection cannot pick it.
clips=dict(clips)|{'segment_death':'mother_segment_1_die'}
nodes=m.nodes(source);rig=m.skeleton(nodes,rig_name)
anims={m.decoder.text8(n['payload'],4)[0]:n for n in nodes if n['type']==0x121000}
decoded={name:m.decoder.decode(anims[name]) for name in set(clips.values())}
bind=m.world(rig,{'tracks':[]},0)
skel=next(n for n in nodes if n['type']==0x23000 and m.decoder.text8(n['payload'])[0]==rig_name)
compensate={}
for node in skel['children']:
    if node['type']!=0x23001:continue
    name,at=m.decoder.text8(node['payload']);extra=node['payload'][at+68:]
    if len(extra)!=54:raise ValueError('Unexpected P1 joint auxiliary layout')
    if struct.unpack_from('<12f',extra)!=(0,0,0,1,1,1,0,0,0,0,0,0):
        raise ValueError('Unhandled P1 joint auxiliary transform '+name)
    matrix_type,flag=struct.unpack_from('<HI',extra,48)
    compensate[name]=matrix_type==1 and flag!=0
scaled={t['bone'] for clip in decoded.values() for t in clip['tracks'] if t['tag']=='SCL'}
geo_path=ASSETS/'geo/elizabeth_greene_mc.geo.json'
motion_path=ASSETS/'models/encounter_elizabeth_greene_source.json'
saved_geo=ROOT/'art/encounters/greene-authored-bind.geo.json'
saved_weak=ROOT/'art/encounters/greene-legacy-weak-motion.json'
if not saved_geo.exists():saved_geo.write_bytes(geo_path.read_bytes())
if not saved_weak.exists():
    old_motion=json.loads(motion_path.read_text())
    keep=['root','weak_0','weak_1','weak_2']
    weak={'bones':keep,'boundary':'Retained independent Minecraft weakpoint-ring placement and old pose until its full source controller is ported.',
          'clips':{key:{'frames':[[row[old_motion['bones'].index(b)] for b in keep] for row in clip['frames']]} for key,clip in old_motion['clips'].items()}}
    saved_weak.write_text(json.dumps(weak,separators=(',',':')),encoding='utf8')
original=json.loads(saved_geo.read_text());old={b['name']:b for b in original['minecraft:geometry'][0]['bones']}
legacy=json.loads(saved_weak.read_text())
# The accepted silhouette has its horns on negative geometry-X and embedded
# host on positive X; original Horn/Body_Root have the opposite signs. Gecko
# mirrors geometry X, so native source axes already equal runtime model axes.
scale=old['head']['pivot'][1]/bind['Body_Root'][1,3]
S=np.diag([-1.,1.,1.])
K=np.diag([scale,scale,scale,1.])
Kinv=np.linalg.inv(K)

def skin(name):
    node=next(n for n in nodes if n['type']==0x10001 and m.decoder.text8(n['payload'])[0]==name)
    group=next(n for n in node['children'] if n['type']==0x10020)
    palette_data=next(n['payload'] for n in group['children'] if n['type']==0x1000d)
    count=struct.unpack_from('<I',palette_data)[0]
    palette=np.frombuffer(palette_data,dtype='<u4',count=count,offset=4)
    desc=next(n['payload'] for n in group['children'] if n['type']==0x10014 and struct.unpack_from('<I',n['payload'],4)[0]==2)
    desc_size=struct.unpack_from('<I',desc,12)[0]
    fields={r[0]:r for r in [struct.unpack_from('<IIIBI',desc,16+i*17) for i in range(desc_size//17)]}
    pos_field=fields[0x2c929929];weight_field=fields[0x49570cfb];index_field=fields[0x73d5cba7]
    payload=next(n['payload'] for n in group['children'] if n['type']==0x10012 and struct.unpack_from('<I',n['payload'],4)[0]==2)
    size=struct.unpack_from('<I',payload,8)[0];stride=pos_field[3];vertices=size//stride
    if size%stride or weight_field[3]!=stride or index_field[3]!=stride:raise ValueError('Inconsistent original skin stride')
    positions=np.ndarray((vertices,3),dtype='<f4',buffer=payload,offset=12+pos_field[2],strides=(stride,4)).copy()
    weights=np.ndarray((vertices,3),dtype='<f4',buffer=payload,offset=12+weight_field[2],strides=(stride,4)).copy()
    weights=np.c_[weights,1-weights.sum(1)]
    ids=np.ndarray((vertices,4),dtype='u1',buffer=payload,offset=12+index_field[2],strides=(stride,1)).copy()
    if ids.max()>=len(palette) or weights.min()<-1e-5 or weights.max()>1.00001:raise ValueError('Invalid original skin weights')
    combined=np.stack([np.where(ids==i,weights,0).sum(1) for i in range(len(palette))],axis=1)
    names=[rig[i][0] for i in palette]
    return positions,combined,names,{'name':name,'nodeOffset':node['offset'],'vertices':vertices,'palette':names,
        'weightSums':{name:float(combined[:,i].sum()) for i,name in enumerate(names)},
        'layout':'Interleaved descriptor position float3, weights float3 plus fourth=1-sum, palette indices byte4; no palette remapping by joint name guesses.'}

skins={key:skin('mother_core_'+name) for key,name in [('inner','InnerCoreShape'),('outer','OuterCoreShape'),('spine','SpineShape')]}
source_leaf={'Spine_Base':'neck_base','Spine_1':'neck_mid','Spine_2':'neck_top','Spine_3':'neck_upper','Spine_4':'neck_crown',
             'Head':'torso','Head_End':'crown_tip','Mouth':'mouth','Horn':'left_arm','Body_Root':'head','Body':'host_body','Chest_L':'host_left','Chest_R':'host_right',
             **{'Heart_'+str(i):'lobe_'+str(i) for i in range(1,8)}}
leaf_source={leaf:src for src,leaf in source_leaf.items()}
# Preserve this named authored group; its hanging lobe belongs to the giant
# head, not the original human Chest_R. Its geometry does not move in bind pose.
leaf_source['right_arm']='Head'
leafs={}
for name,src in leaf_source.items():
    pivot=old[name]['pivot'] if name in old else (S@bind[src][:3,3]*scale).tolist()
    leafs[name]={'name':name,'parent':'native_'+src,'pivot':pivot,'cubes':[]}

def cube_matrix(cube):
    pivot=S@np.asarray(cube.get('pivot',[0,0,0]))
    rot=np.radians(cube.get('rotation',[0,0,0]))*[-1,-1,1]
    q=rotation(rot);mat=np.eye(4);mat[:3,:3]=q;mat[:3,3]=pivot-q@pivot
    return mat

def rotation(v):
    x,y,z=v;cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    return np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])

assignments=[]
for owner,bone in old.items():
    if owner.startswith('weak_'):continue
    for index,cube in enumerate(bone.get('cubes',[])):
        group='inner' if owner=='head' else 'spine' if owner.startswith('neck_') or owner=='root' else 'outer'
        center=(cube_matrix(cube)@np.r_[S@(np.asarray(cube['origin'])+np.asarray(cube['size'])/2),1])[:3]/scale
        positions,weights,names,_=skins[group]
        distance=np.sum((positions-center)**2,axis=1);near=np.argsort(distance,kind='stable')[:8]
        average=np.average(weights[near],axis=0,weights=1/np.maximum(distance[near],.0025))
        source_joint=names[int(np.argmax(average))]
        # The horns are distinct in both meshes. Do not let their coarse
        # authored bend nearest a scalp vertex accidentally inherit Chest_L.
        if owner=='left_arm':source_joint='Horn'
        leaf='right_arm' if owner=='right_arm' else source_leaf[source_joint]
        leafs[leaf]['cubes'].append(copy.deepcopy(cube))
        assignments.append({'oldBone':owner,'oldCube':index,'newBone':leaf,'sourceJoint':leaf_source[leaf],
                            'sourceSkin':group,'nearestWeight':float(max(average)),'nearestVertices':near.tolist()})

# The three health children share MotherCore's complete pose (mission FIG
# 66452/P4 instructions 098..108), rather than orbiting at authored ring offsets.
# Their skins name separate skeleton resources, but all 56 joints, parents and
# local binds are identical. Native Slug curves therefore animate these leaves
# in exactly the same source-bind coordinate system as the core.
segment_records=[]
for index in range(3):
    number=index+1;prefix='Slug_0'+str(number);skeleton_name='mother_segment_'+str(number)+'_skeleton'
    segment_rig=m.skeleton(nodes,skeleton_name)
    if len(segment_rig)!=len(rig) or any(a[0:2]!=b[0:2] or not np.array_equal(a[2],b[2]) for a,b in zip(segment_rig,rig)):
        raise ValueError('Segment skeleton does not share the exact core bind: '+skeleton_name)
    segment_skel=next(n for n in nodes if n['type']==0x23000 and m.decoder.text8(n['payload'])[0]==skeleton_name)
    if [n['payload'] for n in segment_skel['children'] if n['type']==0x23001]!=[n['payload'] for n in skel['children'] if n['type']==0x23001]:
        raise ValueError('Segment joint auxiliary records differ from core: '+skeleton_name)
    name='mother_segment_'+str(number)+'_'+prefix+'Shape'
    positions,weights,names,metadata=skin(name)
    node=next(n for n in nodes if n['offset']==metadata['nodeOffset'])
    group=next(n for n in node['children'] if n['type']==0x10020)
    indices=next(n['payload'] for n in group['children'] if n['type']==0x10013)
    triangles=np.frombuffer(indices,dtype='<u2',offset=12).reshape(-1,3)
    if triangles.max()>=len(positions):raise ValueError('Segment triangle outside vertex buffer')
    dominant=weights.argmax(1);regions=[]
    for joint_index,src in enumerate(names):
        if not src.startswith(prefix+'_'):raise ValueError('Unexpected segment palette joint '+src)
        # Include each triangle incident to a dominantly weighted vertex. The
        # resulting measured boxes overlap at the original skin seams instead
        # of opening arbitrary gaps between sampled vertex rings. This is a
        # rigid cuboid adaptation, not original weighted mesh rendering.
        incident=triangles[np.any(dominant[triangles]==joint_index,axis=1)]
        selected=np.unique(incident)
        if not len(selected):raise ValueError('Empty source segment region '+src)
        support=positions[selected];lo=support.min(0);hi=support.max(0)
        geometry_points=support@S*scale;origin=geometry_points.min(0);size=geometry_points.max(0)-origin
        if np.any(size<=0):raise ValueError('Degenerate source segment region '+src)
        leaf='weak_'+str(index)+'_'+src
        # Retain only this project's authored texture pixels. Original meshes
        # supply measured anatomy and bone ownership, never UVs or texture data.
        old_uv=old['weak_'+str(index)]['cubes'][7 if '_Jaw_' in src else 2 if src.endswith('_Head') else 1]['uv']
        cube={'origin':origin.tolist(),'size':size.tolist(),'uv':copy.deepcopy(old_uv)}
        leaf_source[leaf]=src
        leafs[leaf]={'name':leaf,'parent':'native_'+src,'pivot':(S@bind[src][:3,3]*scale).tolist(),'cubes':[cube]}
        regions.append({'leaf':leaf,'sourceJoint':src,'dominantVertices':int(np.sum(dominant==joint_index)),
                        'supportVertices':len(selected),'supportTriangles':len(incident),
                        'sourceBindBounds':[lo.tolist(),hi.tolist()],
                        'sourceBindJoint':bind[src][:3,3].tolist(),'authoredUvCube':7 if '_Jaw_' in src else 2 if src.endswith('_Head') else 1})
    segment_records.append({'segment':index,'template':'Mother_Segment_'+str(number),'grabSlot':'Segment'+str(number),
                            'skin':metadata,'skeleton':skeleton_name,'skeletonOffset':segment_skel['offset'],
                            'sameCoreSkeletonJoints':len(rig),'maxBindMatrixDifference':0.,
                            'sourceBindBounds':[positions.min(0).tolist(),positions.max(0).tolist()], 'regions':regions})

required={src for src in leaf_source.values()}
for src in list(required):
    i=next(i for i,r in enumerate(rig) if r[0]==src)
    while i>0:required.add(rig[i][0]);i=rig[i][1]
    required.add(rig[0][0])
native=[(i,r) for i,r in enumerate(rig) if r[0] in required]
compensated={name:rig[parent][0] for i,(name,parent,_) in native if i and compensate[name] and rig[parent][0] in scaled}
bones=[copy.deepcopy(old['root'])];bones[0]['cubes']=[]
for i,(name,parent,_) in native:
    b={'name':'native_'+name,'pivot':[0,0,0]}
    if name in compensated:
        bones.append({'name':'native_'+name+'_offset','parent':'native_'+rig[parent][0],'pivot':[0,0,0]})
        bones.append({'name':'native_'+name+'_parent_scale','parent':'native_'+name+'_offset','pivot':[0,0,0]})
        b['parent']='native_'+name+'_parent_scale'
    elif i:b['parent']='native_'+rig[parent][0]
    bones.append(b)
bones.extend(leafs.values())
geometry=copy.deepcopy(original);geometry['minecraft:geometry'][0]['bones']=bones
order=[b['name'] for b in bones];pivots={b['name']:S@np.asarray(b['pivot']) for b in bones}
parents=[order.index(b['parent']) if b.get('parent') in order else -1 for b in bones]
table={'schema':1,'source':str(source),'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),
       'skeleton':rig_name,'reference':'original skeleton bind; native local channels, not an idle-frame delta',
       'bones':order,'parents':parents,'pivots':[pivots[n].tolist() for n in order],
       'mapping':{'native_'+name:name for _,(name,_,_) in native}|leaf_source,
       'pixelsPerSourceUnit':scale,'rootMotion':'Motion_Root controller travel is held at source bind; all remaining original ROT/TRAN/SCL hierarchy is preserved. All three damage segments share the core native Slug pose and bind coordinates, with no ring offsets.',
       'clips':{},'sockets':{}}

def row_for(mat,pivot):
    sizes=np.linalg.norm(mat[:3,:3],axis=0);r=mat[:3,:3]/sizes
    if np.max(np.abs(r.T@r-np.eye(3)))>2e-5:raise ValueError('Cannot flatten shear into a local TRS')
    values=m.euler(r)+list(mat[:3,3]-pivot+r@np.diag(sizes)@pivot)+list(sizes)
    return [round(float(v),7) for v in values]

static={name:row_for(K@np.linalg.inv(bind[src])@Kinv,pivots[name]) for name,src in leaf_source.items()}

def local_matrices(clip,frame):
    tracks={}
    for t in clip['tracks']:tracks.setdefault(t['bone'],{})[t['tag']]=t
    local=[];world=[];scales=[]
    for i,(name,parent,base) in enumerate(rig):
        mat=base.copy();channels=tracks.get(name,{}) if i else {}
        if 'ROT' in channels:mat[:3,:3]=m.quat_matrix(m.track_at(channels['ROT'],frame))
        if 'TRAN' in channels:mat[:3,3]=m.track_at(channels['TRAN'],frame)
        size=np.asarray(m.track_at(channels['SCL'],frame)) if 'SCL' in channels else np.ones(3)
        mat[:3,:3]=mat[:3,:3]@np.diag(size);scales.append(size)
        local.append(mat);effective=mat.copy()
        if i and compensate[name]:effective[:3,:3]=np.diag(1/scales[parent])@effective[:3,:3]
        world.append(effective if i==0 else world[parent]@effective)
    return local,{r[0]:world[i] for i,r in enumerate(rig)},scales

def reconstructed(row):
    result=[]
    for i,(name,v) in enumerate(zip(order,row)):
        r=rotation(v[:3])@np.diag(v[6:9] if len(v)>=9 else [1,1,1]);p=pivots[name]
        mat=np.eye(4);mat[:3,:3]=r;mat[:3,3]=p+v[3:6]-r@p
        result.append(mat if parents[i]<0 else result[parents[i]]@mat)
    return result

checks=[];fixtures=[];spine_fixtures=[];segment_fixtures=[];segment_death_fixtures=[]
for key,original_clip in clips.items():
    clip=decoded[original_clip];frames=[];error=0.
    for frame in range(int(clip['frames'])):
        local,world,scales=local_matrices(clip,frame)
        by_name={}
        for i,(name,parent,_) in native:
            mat=K@local[i]@Kinv
            if name in compensated:
                offset=np.eye(4);offset[:3,3]=mat[:3,3];mat=mat.copy();mat[:3,3]=0
                by_name['native_'+name+'_offset']=row_for(offset,np.zeros(3))
                inverse=np.eye(4);inverse[:3,:3]=np.diag(1/scales[parent])
                by_name['native_'+name+'_parent_scale']=row_for(inverse,np.zeros(3))
            by_name['native_'+name]=row_for(mat,np.zeros(3))
        by_name.update(static)
        root_key=key if key in legacy['clips'] else 'idle'
        root_frame=frame if key in legacy['clips'] else 0
        by_name['root']=legacy['clips'][root_key]['frames'][root_frame][legacy['bones'].index('root')]
        row=[by_name[name] for name in order];frames.append(row)
        generated=reconstructed(row)
        for leaf,src in leaf_source.items():
            desired=K@world[src]@np.linalg.inv(bind[src])@Kinv
            error=max(error,float(np.max(np.abs(generated[order.index(leaf)]-desired))))
        if key in ['idle','VOLLEY','exposed'] and frame in [0,32,72,90,150]:
            for src in ['Spine_Base','Spine_1','Spine_2','Spine_3','Spine_4','Head']:
                spine_fixtures.append({'clip':key,'frame':frame,'bone':'native_'+src,'pointModelBlocks':(world[src][:3,3]*scale/16).tolist()})
            for leaf in ['head','left_arm','mouth','torso']+[r['leaf'] for segment in segment_records for r in segment['regions']]:
                if not leafs[leaf]['cubes']:continue
                cube=leafs[leaf]['cubes'][0];point=S@(np.asarray(cube['origin'])+np.asarray(cube['size'])/2)
                desired=K@world[leaf_source[leaf]]@np.linalg.inv(bind[leaf_source[leaf]])@Kinv
                posed=(desired@cube_matrix(cube)@np.r_[point,1])[:3]/16
                fixture={'clip':key,'frame':frame,'bone':leaf,'cube':0,'centerModelBlocks':posed.tolist()}
                if leaf.startswith('weak_'):
                    segment=next(s for s in segment_records if leaf.startswith('weak_'+str(s['segment'])+'_'))
                    fixture['region']='weak_'+str(segment['segment'])
                    fixture['regionCube']=next(i for i,r in enumerate(segment['regions']) if r['leaf']==leaf)
                    segment_fixtures.append(fixture)
                else:fixtures.append(fixture)
        if key=='segment_death' and frame in [0,20,32,66,80,95]:
            for segment in segment_records:
                for region_index,region in enumerate(segment['regions']):
                    leaf=region['leaf'];cube=leafs[leaf]['cubes'][0]
                    point=S@(np.asarray(cube['origin'])+np.asarray(cube['size'])/2)
                    desired=K@world[leaf_source[leaf]]@np.linalg.inv(bind[leaf_source[leaf]])@Kinv
                    posed=(desired@cube_matrix(cube)@np.r_[point,1])[:3]/16
                    segment_death_fixtures.append({'clip':key,'frame':frame,'bone':leaf,'region':'weak_'+str(segment['segment']),
                        'regionCube':region_index,'centerModelBlocks':posed.tolist()})
    table['clips'][key]={'source':original_clip,'fps':clip['fps'],'seconds':(clip['frames']-1)/clip['fps'],'frames':frames}
    checks.append({'clip':key,'frames':len(frames),'maxAffineCoefficientError':error})
    if error>5e-4:raise ValueError('Native helper export loses source affine transform')

# Original emitter fields, not copied from a previous retarget's axes/offsets.
source_path=Path('D:/PrototypeReference/p1/input/comparison/mother_tod.p3d.rz')
objects=m.nodes(source_path);obj=None
for node in objects:
    if node['type']!=0x7f00000:continue
    _,at=m.decoder.text8(node['payload']);name,_=m.decoder.text8(node['payload'],at)
    if name=='MotherCore':obj=node;break
meta=next(n for n in obj['children'] if n['type']==0x7f00001);raw=bytes(meta['payload'][4:])
for match in re.finditer(rb'\x09VomitGun[1-9]|\x0aVomitGun10',raw):
    length=raw[match.start()];name=raw[match.start()+1:match.start()+1+length].decode();at=raw.find(b'\x05Mouth',match.end(),match.end()+70)
    if at<0:continue
    offset=struct.unpack_from('>3f',raw,at+6)
    if any(struct.unpack_from('>3f',raw,at+18)):raise ValueError('Unhandled emitter rotation '+name)
    table['sockets'][name]={'bone':'native_Mouth','sourceJoint':'Mouth','sourceOffset':offset,'offset':(np.asarray(offset)*scale).tolist(),
                          'sourceFile':'comparison/mother_tod.p3d.rz','sourceOffsetBytes':meta['offset']+16+at+6}
if len(table['sockets'])!=10:raise ValueError('Incomplete original emitters')
table['socketProvenance']={'source':str(source_path),'sha256':hashlib.sha256(source_path.read_bytes()).hexdigest(),
                          'state':'Original native Mouth hierarchy, independent of the embedded human Body_Root; same local ROT/TRAN/SCL as the posed outer-skin leaves.',
                          'registration':'prototypeenginef.dll 0x102c84c0','objectOffset':obj['offset']}
geo_path.write_text(json.dumps(geometry,indent=2)+'\n',encoding='utf8')
motion_path.write_text(json.dumps(table,separators=(',',':')),encoding='utf8')
report={'schema':1,'state':'native hierarchy and authored skin ownership corrected; compilation/client validation deferred',
        'source':str(source),'sourceSha256':table['sourceSha256'],'authoredBindSha256':hashlib.sha256(saved_geo.read_bytes()).hexdigest(),
        'scalePixelsPerSourceUnit':scale,'scaleBasis':'Authored embedded host pivot Y / original Body_Root bind Y; visual proportions remain authored.',
        'axes':'Original source X equals runtime model X. Geometry JSON X is opposite, matching source Horn vs authored lateral horns and source Body_Root vs authored host.',
        'nativeHelpers':len(native),'bones':len(bones),'sourceSkins':{k:v[3] for k,v in skins.items()},'assignments':assignments,
        'parentScaleCompensation':compensated,
        'bindPolicy':'Core authored cube coordinates/rotations/sizes/UVs remain byte-value identical. The legacy independent weakpoint ring is replaced by source segment region boxes with existing authored UVs. Native bind multiplied by the static inverse-bind leaf is identity.',
        'classification':'Authored cube center in scaled source bind space; inverse-distance mean of eight nearest source skin vertices chooses a rigid joint. Horns explicitly retain Horn. This rigid cuboid mapping is an MC adaptation of blended native skin, not a copied mesh.',
        'checks':checks,'socketBindModelPixels':(bind['Mouth'][:3,3]*scale).tolist(),
        'segmentSource':'greene-segment-shared-pose-source.json',
        'limits':['No Java compilation, unit/GameTests or game/client run.', 'Segment cuboids are rigid incident-triangle bounds, not blended original skin; box corners can enclose empty space and need visual/playable acceptance.', 'Source affine agreement is numerical export integrity, not proof of visual acceptance.']}
(ROOT/'art/encounters/greene-native-bind-correction.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
segment_report={'schema':1,'state':'Source shared-pose segment binding replaces authored ring; no compilation or gameplay/visual validation performed.',
                'source':str(source),'sourceSha256':table['sourceSha256'],'scalePixelsPerSourceUnit':scale,
                'sourceCoordinateSystem':'All three segment skin buffers are in the same bind world as mother_core; their named skeletons contain byte-identical 56 joint records. No grab-slot offset is added.',
                'script':{'figOffset':66452,'function':'P4 MotherSpawnSegments','attachInstructions':'098..104','sharePoseInstructions':'105..108','physicsEnableInstructions':'109..112','exclusionGroupInstructions':'113..116',
                          'sequence':'Spawn Mother_Segment_i at core position/orientation; attach to Segment+i with empty child joint, mode 0; go_SharePose(core, child); phys_EnablePhysics(child,true); phys_SetExclusionGroup(child,coreGroup). RestoreSegments/P5 repeats attach and SharePose.'},
                'replacement':{'removedLegacyRingCubes':sum(len(old['weak_'+str(i)]['cubes']) for i in range(3)),'sourceBoundSegmentCubes':sum(len(s['regions']) for s in segment_records),'unchangedAuthoredCoreCubes':len(assignments)},
                'geometryPolicy':'For each actual source palette joint, the cuboid bounds all vertices of triangles incident to at least one vertex whose maximum skin weight is that joint. The three jaws remain separate. Only measured boxes and native pose channels are exported; original triangles/UVs/textures are not copied.',
                'axes':'geometry = diag(-1,1,1) * sourceBind * existingPixelsPerSourceUnit; runtime model mirrors geometry X back. Core cuboids and all existing texture pixels remain unchanged.',
                'segments':segment_records,'checks':checks,
                'limits':['No Java, Gradle, unit/GameTests, game/client or visual acceptance run.', 'Rigid bounding cuboids can overfill curved native surfaces and do not implement weighted vertex blending.', 'Segment detach/dead-body presentation remains separate work; this change corrects attached live segment geometry and pose.']}
(ROOT/'art/encounters/greene-segment-shared-pose-source.json').write_text(json.dumps(segment_report,indent=2)+'\n',encoding='utf8')
socket_fixtures=[]
for key,frame in [('idle',0),('VOLLEY',32),('VOLLEY',72),('exposed',150)]:
    _,world,_=local_matrices(decoded[clips[key]],frame)
    for slot in [2,10]:
        socket=table['sockets']['VomitGun'+str(slot)]
        point=(K@world['Mouth']@np.r_[socket['sourceOffset'],1])[:3]/16
        socket_fixtures.append({'clip':key,'frame':frame,'slot':slot,'pointModelBlocks':point.tolist()})
(ROOT/'src/test/resources/greene-skin-reference.json').write_text(json.dumps({'sourceSha256':table['sourceSha256'],'method':'Direct original native local matrices and authored core / measured segment bind cube centers; independent of exported pose rows.','fixtures':fixtures,'segments':segment_fixtures,'segmentsDeath':segment_death_fixtures,'sockets':socket_fixtures,'spine':spine_fixtures},indent=2)+'\n',encoding='utf8')
print(json.dumps({'bones':len(bones),'cuboids':len(assignments)+sum(len(s['regions']) for s in segment_records),
                  'sourceFrames':sum(c['frames'] for c in checks),'maxAffineError':max(c['maxAffineCoefficientError'] for c in checks),
                  'mappedCuboids':{n:len(b['cubes']) for n,b in leafs.items()}}))
