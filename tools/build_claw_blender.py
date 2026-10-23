"""Editable claw study on the shipped Epic Fight rig. Run with Blender --background.

Usage: blender -b --python tools/build_claw_blender.py -- WORK OUTPUT
The vendor importer/exporter is loaded from WORK/epic-blender-addon without installing
or enabling its UI/updater. Meshes are exported in their actual joint-local space.
"""
import bpy, bmesh, json, math, sys, importlib.util
from pathlib import Path
from mathutils import Vector, Matrix, Euler

WORK, OUT = [Path(p) for p in sys.argv[sys.argv.index('--')+1:][:2]]
OUT.mkdir(parents=True, exist_ok=True)
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'src/main/resources/assets/blacklight'
spec = importlib.util.spec_from_file_location('epic_vendor', WORK/'epic-blender-addon/__init__.py', submodule_search_locations=[str(WORK/'epic-blender-addon')])
vendor = importlib.util.module_from_spec(spec); sys.modules[spec.name] = vendor
# Do not execute the add-on initializer (which registers an update service).
vendor.__path__ = [str(WORK/'epic-blender-addon')]
from epic_vendor import import_mc_json as importer, compat

bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
scene=bpy.context.scene; scene.render.fps=100
data=json.loads((WORK/'epic-biped.json').read_text())
rig=importer.import_armature(bpy.context,data['armature'],'MAT','Blacklight_EpicFight')['armature_obj']
rig.show_in_front=True
body=importer.import_mesh(bpy.context,data['vertices'],data['armature']['joints'],rig,'Minecraft_scale_reference')
# The human arm surface is replaced by the mutation, not drawn through it.
bm=bmesh.new(); bm.from_mesh(body.data)
remove=[v for v in bm.verts if abs(v.co.x)>.26 and .72<v.co.z<1.65]
bmesh.ops.delete(bm,geom=remove,context='VERTS'); bm.to_mesh(body.data); bm.free()

def material(name,color,rough=.55,metal=0):
    m=bpy.data.materials.new(name);m.diffuse_color=(*color,1);m.use_nodes=True
    bs=m.node_tree.nodes.get('Principled BSDF');bs.inputs['Base Color'].default_value=(*color,1)
    bs.inputs['Roughness'].default_value=rough;bs.inputs['Metallic'].default_value=metal
    return m
mats=[material('01 / dark biological core',(.015,.009,.010)),
      material('02 / charcoal keratin',(.012,.015,.017),.48),
      material('03 / raised tendon',(.065,.015,.012),.65),
      material('04 / blade ridge',(.040,.049,.050),.44),
      material('05 / narrow cutting edge',(.13,.15,.15),.4),
      material('06 / recessed palm',(.022,.009,.011),.65)]
body.data.materials.append(material('Neutral scale mannequin',(.19,.23,.26),.9))
claw_objects=[]

# A visible face on +Y prevents treating the untextured mannequin's back as front.
eye_material=material('Face orientation / eyes',(.018,.024,.027))
for side in (-1,1):
    bpy.ops.mesh.primitive_cube_add(size=1,location=(side*.095,.254,1.82))
    eye=bpy.context.object;eye.name='Face eye '+str(side);eye.scale=(.052,.012,.032)
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    # Apply location to rest-space geometry before the armature modifier.
    bpy.ops.object.transform_apply(location=True,rotation=False,scale=False)
    eye.data.materials.append(eye_material)
    eye.vertex_groups.new(name='Head').add(list(range(len(eye.data.vertices))),1,'REPLACE')
    eye.parent=rig;eye.modifiers.new('Follow head','ARMATURE').object=rig


def mesh(name,verts,faces,mi,bone):
    me=bpy.data.meshes.new(name);me.from_pydata(verts,[],faces);me.update()
    ob=bpy.data.objects.new(name,me);scene.collection.objects.link(ob)
    for m in mats:me.materials.append(m)
    for p in me.polygons:p.material_index=mi if isinstance(mi,int) else mi[p.index]
    bm=bmesh.new();bm.from_mesh(me)
    bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(me);bm.free()
    # Vertices stay in joint local coordinates. The modifier evaluates the same
    # rest-to-pose transform used by the runtime layer.
    rest=rig.data.bones[bone].matrix_local.copy()
    for v in me.vertices:
        # Correct the hand's anatomical chirality before the front-axis conversion:
        # right-palm-forward requires the thumb on its lateral (+X) side.
        if bone.startswith('Hand_'):v.co.x=-v.co.x
        v.co=rest@v.co
        v.co.y=-v.co.y
    bm=bmesh.new();bm.from_mesh(me)
    bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(me);bm.free()
    vg=ob.vertex_groups.new(name=bone);vg.add(list(range(len(verts))),1,'REPLACE')
    ob.parent=rig;ob.modifiers.new('Epic Fight joint','ARMATURE').object=rig
    ob['runtime_joint']=bone;claw_objects.append(ob)
    return ob

def tube(name,pts,radii,bone,mi=0,sides=10):
    # Parallel cross-sections along local Y; radii contain X/Z semi-axes.
    verts=[]
    for (x,y,z),(rx,rz) in zip(pts,radii):
        for k in range(sides):
            a=2*math.pi*k/sides
            verts.append((x+rx*math.cos(a),y,z+rz*math.sin(a)))
    faces=[]
    for r in range(len(pts)-1):
        for k in range(sides):
            a=r*sides+k;b=r*sides+(k+1)%sides
            faces.append((a,b,b+sides,a+sides))
    faces.extend([tuple(reversed(range(sides))),tuple((len(pts)-1)*sides+k for k in range(sides))])
    return mesh(name,verts,faces,mi,bone)

def blade(name,pts,widths,bone):
    verts=[]
    for p,w in zip(pts,widths):
        x,y,z=p
        # Lenticular section: a broad dorsal spine and a thin cutting edge.
        verts.extend([(x-w,y,z),(x-w*.72,y,z-w*.22),(x,y,z-w*.40),
                      (x+w*.72,y,z-w*.22),(x+w,y,z),(x,y,z+w*.63)])
    faces=[];colors=[]
    for r in range(len(pts)-1):
        for k in range(6):
            faces.append((r*6+k,r*6+(k+1)%6,(r+1)*6+(k+1)%6,(r+1)*6+k))
            colors.append([4,1,1,4,3,1][k])
    faces.extend([(5,4,3,2,1,0),tuple((len(pts)-1)*6+k for k in range(6))]);colors.extend([1,4])
    return mesh(name,verts,faces,colors,bone)

for side,sign in [('R',1),('L',-1)]:
    arm='Arm_'+side;hand='Hand_'+side
    tube(side+' / shoulder to elbow',[(0,-.045,0),(0,.025,0),(sign*.017,.10,0),(0,.22,0),(0,.325,0)],
         [(.095,.10),(.158,.148),(.15,.14),(.122,.115),(.105,.10)],arm)
    tube(side+' / forearm core',[(0,-.055,0),(0,.025,0),(sign*.025,.10,-.008),(sign*.015,.185,0),(0,.275,0)],
         [(.102,.10),(.14,.13),(.19,.145),(.165,.125),(.114,.088)],hand)
    tube(side+' / palm',[(0,.245,0),(0,.305,-.005),(sign*.01,.37,-.007),(0,.41,-.012)],
         [(.105,.08),(.182,.089),(.22,.078),(.18,.053)],hand,5)
    # Three distinct fingers, their middle digit longest; a separately opposed thumb.
    for i,(x,length,fan) in enumerate([(-.135,.36,-.10),(0,.46,0),(.135,.405,.115)]):
        x*=sign;fan*=sign
        tube(f'{side} / knuckle {i+1}',[(x,.355,-.008),(x,.399,-.012),(x+fan*.25,.447,-.025)],
             [(.07,.064),(.06,.061),(.043,.044)],hand,1,8)
        y=.42
        blade(f'{side} / talon {i+1}',[(x,y,-.015),(x+fan*.40,y+length*.25,-.035),
             (x+fan*.78,y+length*.61,-.057),(x+fan,y+length*.87,-.13),
             (x+fan*.90,y+length,-.22)], [.048,.052,.036,.022,.0012],hand)
        # Longitudinal metacarpal ridge, no repeated horizontal banding.
        blade(f'{side} / palm ridge {i+1}',[(x*.43,.24,.064),(x*.72,.31,.095),(x,.39,.055)], [.016,.025,.008],hand)
    x=-sign*.15
    tube(side+' / thumb root',[(x,.29,-.018),(x-sign*.08,.35,-.025),(x-sign*.115,.39,-.05)],
         [(.065,.067),(.060,.055),(.042,.040)],hand,0,8)
    blade(side+' / opposed thumb',[(x-sign*.09,.36,-.035),(x-sign*.17,.43,-.06),
          (x-sign*.185,.54,-.14),(x-sign*.12,.595,-.225)], [.045,.043,.024,.001],hand)
    # Asymmetric, lengthwise keratin plates over the forearm.
    for j,x in enumerate([-.105,0,.108]):
        blade(f'{side} / dorsal plate {j}',[(sign*x*.35,-.025,.075),(sign*x,.055,.15),
              (sign*x,.13,.166),(sign*x*.70,.235,.102),(sign*x*.45,.30,.069)],
              [.005,.045,.043,.025,.002],hand)
    # Small bundles follow the anatomy; they do not encircle the arm.
    for j,x in enumerate([-.10,-.045,.045,.105]):
        tube(f'{side} / tendon {j}',[(sign*x*.8,.012,-.094),(sign*x*1.35,.09,-.115),
             (sign*x*1.15,.18,-.10),(sign*x*.68,.27,-.077)],[(.007,.007),(.011,.008),(.009,.007),(.004,.004)],hand,2,5)

def export_meshes():
    groups={};parts={}
    for ob in claw_objects:
        bone=ob['runtime_joint'];local=rig.data.bones[bone].matrix_local.inverted()
        me=ob.data;me.calc_loop_triangles()
        vertices=[list(local@v.co) for v in me.vertices]
        entry=groups.setdefault(bone,[])
        parts[ob.name]={'joint':bone,'start':len(entry),'count':len(me.loop_triangles)}
        for tri in me.loop_triangles:
            pts=[vertices[i] for i in tri.vertices]
            # Export sRGB material colors, not linear values, for Minecraft vertices.
            col=mats[tri.material_index].diffuse_color[:3]
            srgb=[round(255*(12.92*c if c<=.0031308 else 1.055*c**(1/2.4)-.055)) for c in col]
            entry.append({'p':[[round(c,6) for c in p] for p in pts], 'c':srgb})
    dst=RES/'models/claw_blender.json';dst.parent.mkdir(parents=True,exist_ok=True)
    dst.write_text(json.dumps({'version':1,'source':'claw-study.blend','joints':groups,'parts':parts},separators=(',',':')))
    # Single utility texel. All anatomical color regions come from authored surfaces.
    white=bpy.data.images.new('Runtime white',width=1,height=1);white.pixels=[1,1,1,1]
    white.filepath_raw=str(RES/'textures/entity/claw_white.png');white.file_format='PNG';white.save()
    print('MESH_EXPORT',sum(map(len,groups.values())),'triangles',flush=True)
export_meshes()

# IK is evaluated and baked by Blender, preserving the actual limb lengths.
controls={}
def target(name,loc):
    ob=bpy.data.objects.new(name,None);scene.collection.objects.link(ob);ob.location=loc
    ob.empty_display_type='SPHERE';ob.empty_display_size=.045;controls[name]=ob;return ob
for side,sign in [('R',1),('L',-1)]:
    wrist=target('CTRL / wrist '+side,(sign*.48,-.11,.985))
    pole=target('CTRL / elbow '+side,(sign*1.2,.38,1.1))
    ik=rig.pose.bones['Hand_'+side].constraints.new('IK');ik.target=wrist;ik.chain_count=2
    # Explicit hand length: importer can select the zero-length helper branch.
    ik.use_stretch=False
    foot=target('CTRL / foot '+side,(sign*.16,-.10 if side=='R' else .10,.015))
    knee=target('CTRL / knee '+side,(sign*.17,1.5,.45))
    ik=rig.pose.bones['Leg_'+side].constraints.new('IK');ik.target=foot;ik.chain_count=2;ik.use_stretch=False
    ik.pole_target=knee;ik.pole_angle=math.pi/2

# Fix terminal segment lengths to the actual chain lengths (the importer guesses
# terminal lengths, so the hand and shin must be set explicitly for IK).
bpy.context.view_layer.objects.active=rig;rig.select_set(True);bpy.ops.object.mode_set(mode='EDIT')
for side in ['R','L']:
    rig.data.edit_bones['Hand_'+side].length=.272858
    rig.data.edit_bones['Leg_'+side].length=.37472
bpy.ops.object.mode_set(mode='OBJECT')

def set_pose(frame,twist=0,lean=5,height=-.025,R=(.50,-.07,.835),L=(-.50,-.07,.835),
             footR=(.16,-.10,.015),footL=(-.16,.10,.015),root_pitch=0):
    for p in rig.pose.bones:
        p.rotation_mode='XYZ';p.rotation_euler=(0,0,0);p.location=(0,0,0);p.scale=(1,1,1)
    def rot(name,xyz):rig.pose.bones[name].rotation_euler=[math.radians(v) for v in xyz]
    rot('Root',(root_pitch,twist*.22,0));rig.pose.bones['Root'].location.y=height
    rot('Torso',(lean*.6,twist*.50,0));rot('Chest',(lean*.4,twist*.28,0));rot('Head',(-lean*.75,-twist*.7,0))
    # The shipped skin's front-face UV lies on +Y. The original motion sketches
    # used -Y; reflect that authoring convention once before IK and keying.
    def front(v):return (v[0],-v[1],v[2])
    controls['CTRL / wrist R'].location=front(R);controls['CTRL / wrist L'].location=front(L)
    controls['CTRL / foot R'].location=front(footR);controls['CTRL / foot L'].location=front(footL)
    # A bent starting configuration avoids the straight-chain IK singularity.
    for side in ['R','L']:
        rot('Thigh_'+side,(-23,0,0));rot('Leg_'+side,(46,0,0))
    for p in rig.pose.bones:
        rest=p.bone.matrix_local.to_3x3()
        reflection=rest.inverted()@Matrix.Diagonal((1,-1,1))@rest
        p.rotation_euler=(reflection@p.matrix_basis.to_3x3()@reflection).to_euler('XYZ')
        p.location=reflection@p.location
        p.keyframe_insert('rotation_euler',frame=frame,group=p.name)
        p.keyframe_insert('location',frame=frame,group=p.name)
    for ob in controls.values():ob.keyframe_insert('location',frame=frame)

def clear_animation():
    rig.animation_data_clear()
    for ob in controls.values():ob.animation_data_clear()

def stash_and_export(name,duration,keys):
    clear_animation()
    for t,args in keys:set_pose(round(t*100),**args)
    action=rig.animation_data.action;action.name=name;action.use_fake_user=True
    for ob in controls.values():
        if ob.animation_data:
            ob.animation_data.action.name=name+' / '+ob.name;ob.animation_data.action.use_fake_user=True
    # Smooth anticipation and recovery; bake every frame to preserve IK and easing.
    for ob in [rig,*controls.values()]:
        if ob.animation_data:
            for fc in compat.get_fcurves_from_action(ob.animation_data.action):
                for k in fc.keyframe_points:k.interpolation='BEZIER';k.handle_left_type='AUTO_CLAMPED';k.handle_right_type='AUTO_CLAMPED'
    exported=compat.export_animation(rig,data['armature']['joints'],'ATTR',bake=True)
    result=json.loads(json.dumps({'format':'ATTRIBUTES','animation':exported},cls=compat.NoIndentEncoder))
    for tr in result['animation']:
        for f in tr['transform']:
            q=f['rot'];norm=math.sqrt(sum(v*v for v in q))
            # Loader conjugation and OpenMatrix4f.fromQuaternion's transposed
            # convention cancel: retain Blender WXYZ without another conjugation.
            f['rot']=[v/norm for v in q]
    dest=RES/'native_animations/biped'/f'{name}.json';dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(clip_text(result))
    (OUT/'exports').mkdir(exist_ok=True);(OUT/'exports'/f'{name.split("/")[-1]}.json').write_text(clip_text(result))
    return result

def clip_text(result):
    lines=['{','  "format": "ATTRIBUTES",','  "animation": [']
    for index,tr in enumerate(result['animation']):
        lines.extend(['    {','      "name": '+json.dumps(tr['name'])+',',
                      '      "time": '+json.dumps(tr['time'])+',','      "transform": ['])
        for i,f in enumerate(tr['transform']):
            f={key:[round(v,7) for v in vals] for key,vals in f.items()}
            lines.append('        '+json.dumps(f)+(',' if i+1<len(tr['transform']) else ''))
        lines.extend(['      ]','    }'+(',' if index+1<len(result['animation']) else '')])
    return '\n'.join(lines+['  ]','}',''])

idle={}
stash_and_export('living/claw_idle',1.6,[(0,idle),(.8,dict(lean=6,R=(.50,-.08,.843),L=(-.50,-.08,.843))),(1.6,idle)])
clips=[('combat/claw_auto1',.7,[(0,idle),(.13,dict(twist=-28,lean=6,R=(.83,.16,1.48),L=(-.43,-.15,1.08))),
       (.20,dict(twist=23,lean=16,height=-.045,R=(.18,-.67,1.20),L=(-.58,.08,1.05))),
       (.32,dict(twist=36,lean=18,height=-.04,R=(-.32,-.46,.95),L=(-.65,.13,1.08))),
       (.50,dict(twist=10,lean=12,R=(.30,-.25,.94))),(.7,idle)]),
       ('combat/claw_auto2',.8,[(0,idle),(.17,dict(twist=30,lean=7,L=(-.83,.12,1.38),R=(.43,-.18,1.13))),
       (.25,dict(twist=-26,lean=17,height=-.045,L=(-.12,-.67,1.17),R=(.59,.12,1.06))),
       (.40,dict(twist=-38,lean=16,height=-.04,L=(.34,-.43,1.04),R=(.65,.10,1.03))),
       (.60,dict(twist=-10,L=(-.34,-.24,.98))),(.8,idle)]),
       ('combat/claw_auto3',1.1,[(0,idle),(.25,dict(twist=-15,lean=-7,height=-.025,R=(.52,-.14,1.97),L=(-.64,.12,1.17))),
       (.35,dict(twist=20,lean=28,height=-.095,R=(.18,-.63,1.03),L=(-.72,.05,1.10))),
       (.50,dict(twist=32,lean=31,height=-.105,R=(-.20,-.36,.66),L=(-.67,.17,1.02))),
       (.80,dict(twist=8,lean=16,height=-.04,R=(.38,-.25,.88))), (1.1,idle)])]
for name,duration,keys in clips:stash_and_export(name,duration,keys)

# Locomotion cycles are authored in place; entity translation remains game physics.
# The stance foot moves backwards relative to the pelvis while the swing foot lifts.
for name,duration,stride,lift,lean in [('claw_walk',.9,.20,.12,9),('claw_sprint',.54,.31,.23,24),('wall_run',.48,.19,.25,15)]:
    keys=[]
    for i in range(9):
        phase=i/8;args={'lean':lean,'height':-.05 if name=='claw_walk' else -.08,'twist':math.sin(phase*math.tau)*6}
        for side,sign,offset in [('R',1,0),('L',-1,.5)]:
            p=(phase+offset)%1
            if p<.5:y=-stride+4*stride*p;z=.015
            else:y=stride-4*stride*(p-.5);z=.015+lift*math.sin((p-.5)*math.tau)
            args['foot'+side]=(sign*.16,y,z)
            if name=='wall_run':
                args['foot'+side]=(sign*.16,-.23,.12+lift*(1+math.sin(p*math.tau)))
                args[side]=(sign*.40,-.37,1.40+.25*math.sin(p*math.tau))
            elif name=='claw_sprint':args[side]=(sign*.54,.06+.13*math.sin(p*math.tau),.94+.04*math.cos(p*math.tau))
            else:args[side]=(sign*.50,-.075-.09*math.sin(p*math.tau),.84)
        keys.append((round(phase*duration,3),args))
    stash_and_export('living/'+name,duration,keys)

charge=dict(lean=34,height=-.30,R=(.48,.15,.66),L=(-.48,.15,.66),footR=(.18,-.08,.015),footL=(-.18,.08,.015))
stash_and_export('living/prototype_jump_charge',2.05,[(0,idle),(.32,dict(lean=20,height=-.15,R=(.48,.08,.75),L=(-.48,.08,.75))),(.70,charge),(1.35,charge),(2.05,charge)])
rise=dict(lean=12,R=(.52,.11,1.05),L=(-.52,.11,1.05),footR=(.16,.14,.23),footL=(-.16,.22,.35))
fall=dict(lean=10,R=(.66,-.06,1.14),L=(-.66,-.06,1.14),footR=(.17,-.08,.15),footL=(-.17,.13,.23))
stash_and_export('living/prototype_jump_rise',.2,[(0,dict(lean=18,R=(.5,.08,.95),L=(-.5,.08,.95))),(.09,rise),(.2,rise)])
stash_and_export('living/prototype_jump_fall',.2,[(0,rise),(.12,fall),(.2,fall)])
glide=dict(root_pitch=30,lean=23,height=.04,R=(.94,-.12,1.00),L=(-.94,-.12,1.00),footR=(.19,.49,.39),footL=(-.19,.49,.39))
stash_and_export('living/prototype_glide',.7,[(0,glide),(.35,glide|{'R':(.93,-.10,1.035),'L':(-.93,-.10,1.035)}),(.7,glide)])
dash=dict(root_pitch=35,lean=18,height=.06,R=(.53,.27,1.01),L=(-.53,.27,1.01),footR=(.16,.55,.46),footL=(-.16,.40,.57))
stash_and_export('action/air_dash',.6,[(0,rise),(.09,dash),(.30,dash),(.46,glide),(.6,fall)])

for name,duration,contact in [('claw_sprint_attack',.8,.1),('claw_dashing_slice',1.,.15)]:
    prep=dict(lean=27,twist=-22,height=-.06,R=(.77,.12,1.23),L=(-.52,.13,.99))
    hit=dict(lean=31,twist=28,height=-.08,R=(.14,-.61,1.06),L=(-.71,.16,1.05))
    through=dict(lean=26,twist=38,height=-.06,R=(-.26,-.35,.83),L=(-.63,.16,1.03))
    stash_and_export('combat/'+name,duration,[(0,prep),(contact*.50,prep),(contact,hit),(contact+.16,through),(duration*.75,dict(lean=15,R=(.4,-.2,.88))),(duration,idle)])

plant=dict(lean=42,height=-.32,R=(.34,-.34,.47),L=(-.71,.03,.92),footR=(.19,-.14,.015),footL=(-.19,.12,.015))
stash_and_export('combat/claw_groundspike',2.2,[(0,idle),(.24,dict(lean=-4,R=(.55,-.09,1.9),L=(-.62,.05,1.09))),
    (.48,plant),(.55,plant),(1.6,plant),(1.9,dict(lean=18,height=-.12,R=(.5,-.18,.72))),(2.2,idle)])
for name,duration,contact,height,lean in [('landing_light',.28,.07,-.10,15),('landing_heavy',.44,.12,-.23,29),('landing_devastating',.62,.18,-.32,40)]:
    compressed=dict(lean=lean,height=height,R=(.44,-.20,.83+height),L=(-.57,.04,.89+height))
    stash_and_export('living/'+name,duration,[(0,fall),(contact,compressed),(contact+.04,compressed),(duration,idle)])

stash_and_export('action/transform_claws',1.2,[(0,idle),(.22,dict(lean=8,R=(.49,-.18,1.06),L=(-.48,-.12,1.0))),
    (.45,dict(lean=5,R=(.61,-.25,1.12),L=(-.57,-.17,1.08))),(.70,dict(lean=7,R=(.61,-.13,.90),L=(-.59,-.13,.93))), (1.2,idle)])
stash_and_export('action/consume',2.3,[(0,idle),(.25,dict(lean=15,R=(.18,-.64,1.29),L=(-.42,-.36,1.1))),
    (.58,dict(lean=12,R=(.16,-.39,1.15),L=(-.25,-.37,1.2))),(.95,dict(lean=-6,height=-.06,R=(.19,-.26,1.27),L=(-.24,-.29,1.24))),
    (1.45,dict(lean=18,height=-.12,R=(.35,-.24,.95),L=(-.35,-.23,.97))),(1.8,dict(lean=11,R=(.57,-.13,.96),L=(-.58,-.13,.96))),(2.3,idle)])

# Readable preview stage; lighting is deliberately broad so silhouette is visible.
ground=material('Preview floor',(.065,.082,.09),1)
bpy.ops.mesh.primitive_plane_add(size=200);floor=bpy.context.object;floor.name='Preview floor';floor.data.materials.append(ground)
world=scene.world;world.use_nodes=True;world.node_tree.nodes['Background'].inputs[0].default_value=(.22,.25,.28,1);world.node_tree.nodes['Background'].inputs[1].default_value=.45
def area(name,loc,power,size):
    d=bpy.data.lights.new(name,'AREA');d.energy=power;d.shape='DISK';d.size=size
    ob=bpy.data.objects.new(name,d);scene.collection.objects.link(ob);ob.location=loc
    ob.rotation_euler=(Vector((0,0,1))-ob.location).to_track_quat('-Z','Y').to_euler()
area('Key', (3,4,5),650,5);area('Rim',(-3,-2,3),800,4);area('Fill',(-3,-2,2),250,4)
camdata=bpy.data.cameras.new('Review camera');cam=bpy.data.objects.new('Review camera',camdata);scene.collection.objects.link(cam);scene.camera=cam
def camera(loc,look=(0,0,.94),scale=2.5):
    cam.location=loc;cam.rotation_euler=(Vector(look)-cam.location).to_track_quat('-Z','Y').to_euler();camdata.type='ORTHO';camdata.ortho_scale=scale
camera((3,5,2.4))
scene.render.engine='CYCLES';scene.cycles.samples=12;scene.cycles.use_denoising=True
scene.render.resolution_x=900;scene.render.resolution_y=900;scene.render.resolution_percentage=100
scene.view_settings.view_transform='Standard';scene.render.image_settings.file_format='PNG'

# Store each action with its control actions. Switching clips must switch controls,
# not just the armature: this is documented in the packed text and render helper.
notes=bpy.data.texts.new('README / controls and export')
notes.write('Blacklight claw study. Original geometry, inspired by Prototype 1.\nActual shipped Epic Fight biped skeleton.\n20 Actions: living locomotion and landings, combat claws, action morph/consume/dash.\nEach action has matching CTRL actions. Switch them together with tools/render_claw_blender.py.\nIK constraints are baked using the Epic Fight exporter; runtime mesh uses joint-local coordinates.\nNeutral Minecraft mesh is an Epic Fight dependency asset for scale reference only.\n')
def select_action(name):
    rig.animation_data.action=bpy.data.actions[name]
    for ob in controls.values():ob.animation_data.action=bpy.data.actions[name+' / '+ob.name]
select_action('living/claw_idle');scene.frame_start=0;scene.frame_end=160;scene.frame_set(0)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'claw-study.blend'))
for name,loc in [('front',(0,5,1.15)),('side',(5,0,1.15)),('three-quarter',(3,5,2.4))]:
    camera(loc);scene.render.filepath=str(OUT/f'model-{name}.png');bpy.ops.render.render(write_still=True)
print('BUILD_COMPLETE',flush=True)
