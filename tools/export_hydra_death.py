"""Export the proven P1 Hydra death clip shared by both native death branches.

Static source conversion, not a Java/game test. Other Hydra motions and geometry
are inputs only. The same world-rotation delta mapping as existing hyd_exit is
retained; authored proportions are an explicit adaptation, not original skin.
"""
from export_encounter_source_motion import *

source=P1/'Hydra.p3d'
all_nodes=nodes(source)
rig=skeleton(all_nodes,'hydra_skeleton')
def source_clip(name):
    node=next(n for n in all_nodes if n['type']==0x121000 and decoder.text8(n['payload'],4)[0]==name)
    return node,decoder.decode(node)

reference_node,reference_clip=source_clip('hyd_act_idle')
death_node,death_clip=source_clip('hyd_death')
reference=world(rig,reference_clip,0)
assert death_clip['fps']==30 and death_clip['frames']==164
assert not any(t['tag']=='SCL' for t in death_clip['tracks'])
table=json.loads((ASSETS/'models/hydra_motion.json').read_text())
mapping={'neck_0':'full_rig_body1','neck_1':'full_rig_body2','neck_2':'full_rig_body3',
         'neck_3':'full_rig_body4','neck_4':'full_rig_body5','neck_5':'Neck_2','neck_6':'Neck_5',
         'head':'Head','jaw':'Gum','pincer_left':'Lip_L','pincer_right':'Lip_R',
         'claw_left':'Claw_L','claw_right':'Claw_R'}
scale=table['pivots'][table['bones'].index('head')][1]/(reference['Head'][1,3]-reference['Character_Root'][1,3])
frames=[];last={};root_points=[];fixtures=[]
for frame in range(99):  # FIG6748 ends at98, not the asset's later frame163.
    current=world(rig,death_clip,frame);rotations={};row=[]
    for index,name in enumerate(table['bones']):
        rotation=np.eye(3)
        if name in mapping:
            joint=mapping[name]
            rotation=current[joint][:3,:3]@reference[joint][:3,:3].T
        parent=table['parents'][index]
        local=rotations[table['bones'][parent]].T@rotation if parent>=0 else rotation
        rotations[name]=rotation
        angles=np.asarray(euler(local))
        if name in last:
            alternate=np.array([angles[0]+math.pi,math.pi-angles[1],angles[2]+math.pi])
            candidates=[v+2*math.pi*np.round((last[name]-v)/(2*math.pi)) for v in (angles,alternate)]
            angles=min(candidates,key=lambda v:np.linalg.norm(v-last[name]))
        last[name]=angles
        y=(current['Character_Root'][1,3]-reference['Character_Root'][1,3])*scale if name=='base' else 0
        row.append([round(float(v),7) for v in angles]+[0,round(float(y),7),0])
    root_points.append(current['Character_Root'][:3,3].tolist());frames.append(row)
    if frame in [0,27,54,98]:
        # Independent original matrices, before exported Euler rounding or Java
        # pose sampling. Use target pivots only to preserve the authored rig.
        transforms={}
        for index,name in enumerate(table['bones']):
            parent=table['parents'][index];parent_name=table['bones'][parent] if parent>=0 else None
            local_rotation=rotations[parent_name].T@rotations[name] if parent_name else rotations[name]
            pivot=np.asarray(table['pivots'][index]);local=np.eye(4);local[:3,:3]=local_rotation
            local[:3,3]=pivot-local_rotation@pivot
            if name=='base':local[1,3]+=(current['Character_Root'][1,3]-reference['Character_Root'][1,3])*scale
            transforms[name]=(transforms[parent_name] if parent_name else np.eye(4))@local
            if name in ['neck_6','head','claw_left','claw_right']:
                fixtures.append({'frame':frame,'bone':name,'modelPoint':(transforms[name]@np.r_[pivot,1])[:3].tolist()})

output={'schema':1,'source':'hyd_death','sourceOffset':death_node['offset'],'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),
        'fps':30,'sourceFrames':164,'firstFrame':0,'lastFrame':98,'bones':table['bones'],'frames':frames}
(ASSETS/'models/hydra_death_motion.json').write_text(json.dumps(output,separators=(',',':'))+'\n')
report={'schema':1,'source':str(source),'sourceSha256':output['sourceSha256'],'sourceOffset':death_node['offset'],
        'sourceFrames':164,'exportedFrames':99,'fps':30,'mapping':mapping,'reference':'hyd_act_idle',
        'sourceUnitBlocks':scale,'rootSampledPositions':{str(i):root_points[i] for i in [0,27,54,98]},
        'method':'Original animated world rotation times inverse idle world rotation, expressed relative to each mapped target parent. Character_Root Y displacement uses the existing Hydra head-height scale. Original horizontal root locomotion remains excluded, matching existing rooted Hydra adaptation.',
        'existingRetract':'hyd_exit already present in hydra_motion.json; death uses native InitFrame27 and holds frame54.',
        'limits':['Static asset conversion only; no Java, tests or game run.','Existing rigid authored rig proportions and rotation mapping are retained, not original weighted skin.','No synthetic death keyframes, extra root depth, source audio or blood FX substitution.']}
(ROOT/'art/hydra/death-motion-provenance.json').write_text(json.dumps(report,indent=2)+'\n')
(ROOT/'src/test/resources/hydra-death-reference.json').write_text(json.dumps({'sourceSha256':output['sourceSha256'],
    'method':'Original native world matrices and target pivots; independent of exported Euler arrays and Java pose interpolation.','fixtures':fixtures},indent=2)+'\n')
print(json.dumps({'sourceFrames':164,'exportedFrames':len(frames),'bones':len(table['bones']),'sourceUnitBlocks':scale}))
