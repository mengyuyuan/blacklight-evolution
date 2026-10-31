"""Retarget the authorised BB rig/cloth into live native-model resources.

The military.2 generator/atlases remain the immutable editable base. This overlay
uses the 16 body cubes and animation channels, never the 5,349-cube weapon mesh.
"""
from pathlib import Path
import argparse,base64,hashlib,io,json,math
import numpy as np
from PIL import Image

SOURCE_SHA='4d1b2213f160aa7a5ece31131c77102d654474277b46a6f93c8624cba71b4696'

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def matrix(angles):
    x,y,z=np.radians(angles);cx,sx,cy,sy,cz,sz=np.cos(x),np.sin(x),np.cos(y),np.sin(y),np.cos(z),np.sin(z)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
def faces(u,v,w,h,d):
    return {'up':(u+d,v,w,d),'down':(u+d+w,v,w,d),'west':(u,v+d,d,h),
            'north':(u+d,v+d,w,h),'east':(u+d+w,v+d,d,h),'south':(u+2*d+w,v+d,w,h)}

def weapon_parts(length,kind):
    def box(name,xyz,size,material):return {'name':name,'box':[*xyz,*size],'source_material':material}
    if kind in ['rocket_launcher','missile_launcher','thermobaric_launcher','infection_launcher']:
        width={'rocket_launcher':2.3,'missile_launcher':2.7,'thermobaric_launcher':3.,'infection_launcher':2.5}[kind]
        return [box('tube',[-width/2,-width-.45,3-length],[width,width,length],7),
                box('rear_ring',[-width/2-.15,-width-.6,2.4],[width+.3,width+.3,.6],8),
                box('front_ring',[-width/2-.15,-width-.6,3-length],[width+.3,width+.3,.6],8),
                box('trigger_grip',[-.5,-.7,-.2],[1,2.3,1],6),box('support_grip',[-.55,-.75,-9.65],[1.1,.75,1.1],6),
                box('optic',[width/2-.1,-width-.2,-4],[.8,.9,2.1],5)]
    result=[box('stock',[-.65,-2.35,.8],[1.3,1.7,2.2],6),box('receiver',[-.7,-2.2,-4.8],[1.4,1.6,5.6],8),
            box('trigger_grip',[-.52,-.6,-.1],[1.04,2.1,.9],6),box('handguard',[-.75,-1.9,-10],[1.5,1.9,5.2],7),
            box('barrel',[-.28,-1.52,3-length],[.56,.56,length-13],8),box('muzzle',[-.43,-1.67,3-length],[.86,.86,.7],8),
            box('sight',[-.28,-2.85,-4],[.56,.65,2.3],5),box('magazine',[-.55,-.7,-3.2],[1.1,3,1.65],6)]
    if kind=='military_machine_gun':
        result[-1]=box('ammo_box',[-1.25,-.7,-3.2],[2.5,2.7,2.5],7)
        result.extend([box('left_bipod',[-.9,-.5,-11.4],[.3,2.8,.4],8),box('right_bipod',[.6,-.5,-11.4],[.3,2.8,.4],8)])
    if kind=='grenade_launcher':
        result[-1]=box('drum',[-1.25,-.7,-3.2],[2.5,2.8,2.5],8)
        result[4]=box('wide_bore',[-.65,-1.95,3-length],[1.3,1.3,length-13],8)
    return result

def export(repo,output,source):
    if digest(source)!=SOURCE_SHA:raise ValueError('Unexpected BB revision; preserve the old input and review a new mapping')
    raw=json.loads(source.read_text(encoding='utf8'));elements={e['uuid']:e for e in raw['elements']};bones={};owner={}
    def walk(nodes,parent=None):
        for n in nodes:
            if isinstance(n,dict):
                bones[n['name']]={'name':n['name'],'uuid':n['uuid'],'parent':parent,'origin':n['origin'],'rotation':n.get('rotation',[0,0,0])}
                walk(n.get('children',[]),n['name'])
            else:owner[n]=parent
    walk(raw['outliner'])
    body=[e for e in raw['elements'] if owner[e['uuid']]!='Weapon - Left Grip'];weapon=[e for e in raw['elements'] if owner[e['uuid']]=='Weapon - Left Grip']
    if len(body)!=16 or len(weapon)!=5349 or len(bones)!=12:raise ValueError('Rig topology changed')
    def world(name,p):
        while name:
            b=bones[name];o=np.array(b['origin']);p=o+matrix(b['rotation'])@(p-o);name=b['parent']
        return p
    def native(p):return (np.array(p)*[1,-1,1]+[0,24,0]).tolist()
    grips={side:native(world(side+' Arm Forearm',np.array([-6,12,0] if side=='Left' else [6,12,0]))) for side in ['Left','Right']}
    animations={}
    for a in raw['animations']:
        result={'length':a['length'],'loop':a.get('loop'),'bones':{}}
        for animator in a.get('animators',{}).values():
            channels={}
            for k in animator.get('keyframes',[]):
                p=k['data_points'][0];channels.setdefault(k['channel'],[]).append({'time':k['time'],'value':[float(p[axis]) for axis in ['x','y','z']],'interpolation':k.get('interpolation','linear')})
            result['bones'][animator['name']]=channels
        animations[a['name']]=result
    source_textures=[Image.open(io.BytesIO(base64.b64decode(t['source'].split(',',1)[1]))).convert('RGBA') for t in raw['textures']]
    skin=source_textures[0]
    by_name={e['name']:e for e in body};regions=[];base_hashes={};result_hashes={}
    arm_uv={'right_upper':[2,210],'right_lower':[20,210],'left_upper':[38,210],'left_lower':[56,210]}
    reference_length=max(e['to'][2] for e in weapon)-min(e['from'][2] for e in weapon)
    lengths={'military_rifle':reference_length,'military_carbine':reference_length*.9,'military_machine_gun':reference_length*1.165,
             'grenade_launcher':reference_length*.95,'rocket_launcher':18.5,'missile_launcher':19.8,'thermobaric_launcher':18.8,'infection_launcher':16.}
    light_weapons={name:weapon_parts(length,name) for name,length in lengths.items()}
    used=np.zeros((256,256),dtype=bool);used[:201,:]=True
    for u,v in arm_uv.values():used[v:v+10,u:u+16]=True
    # Deterministic packed UVs in previously unused atlas space. The old gear UVs are untouched.
    ordered=sorted([p for ps in light_weapons.values() for p in ps],key=lambda p:-(p['box'][4]+p['box'][5]))
    for part in ordered:
        _,_,_,w,h,d=part['box'];rw,rh=math.ceil(2*(w+d))+2,math.ceil(h+d)+2;placed=False
        for y in range(201,257-rh):
            for x in range(0,257-rw):
                if not used[y:y+rh,x:x+rw].any():
                    part['uv']=[x+1,y+1];used[y:y+rh,x:x+rw]=True;placed=True;break
            if placed:break
        if not placed:raise ValueError('Calibrated gun UVs no longer fit the retained 512 atlas')
    material_regions=[]
    for faction in ['blackwatch','military']:
        baseline=repo/'art/military2'/f'{faction}_soldier_layered.png';base_hashes[faction]=digest(baseline)
        atlas=Image.open(baseline).convert('RGBA')
        def transfer(source_cube,target_faces):
            for face,dest in target_faces.items():
                uv=by_name[source_cube]['faces'][face]['uv'];sx,sy,ex,ey=map(lambda f:int(round(f)),uv)
                left,top=min(sx,ex),min(sy,ey);sw,sh=abs(ex-sx),abs(ey-sy)
                if sw==0 or sh==0:raise ValueError('Empty authored UV region')
                patch=skin.crop((left,top,left+sw,top+sh));flips=[]
                if ex<sx:patch=patch.transpose(Image.Transpose.FLIP_LEFT_RIGHT);flips.append('x')
                if ey<sy:patch=patch.transpose(Image.Transpose.FLIP_TOP_BOTTOM);flips.append('y')
                u,v,w,h=dest;tx,ty,tw,th=round(u*2),round(v*2),round(w*2),round(h*2)
                patch=patch.resize((tw,th),Image.Resampling.NEAREST)
                mode='copy' if not flips else 'copy_flip_'+'_'.join(flips)
                if faction=='military':
                    pixels=np.asarray(patch).astype(float);lum=(pixels[:,:,0]*.3+pixels[:,:,1]*.59+pixels[:,:,2]*.11)/128
                    factor=np.clip(.65+lum*.55,.65,1.35);pixels[:,:,:3]=np.clip(factor[:,:,None]*np.array([68,76,49]),0,255);patch=Image.fromarray(pixels.astype('uint8'),'RGBA');mode='reference_luminance'
                atlas.paste(patch,(tx,ty))
                regions.append({'faction':faction,'source_texture':0,'source_cube':source_cube,'face':face,'source_rect':[left,top,sw,sh],
                    'target_rect':[tx,ty,tw,th],'mode':mode,'atlas':f'assets/blacklight/textures/entity/{faction}_soldier_layered.png'})
        # Keep all original head/face UVs intact. Cloth is the reference body's base garment.
        transfer('Body',faces(36,2,8,12,4))
        for target,source_side in [('right','Left'),('left','Right')]:
            for half,source_suffix in [('upper','Upper Skin'),('lower','Forearm Skin')]:
                u,v=arm_uv[target+'_'+half];transfer(source_side+' Arm '+source_suffix,faces(u,v,4,6,4))
        for gun,parts in light_weapons.items():
            for part in parts:
                u,v=part['uv'];_,_,_,w,h,d=part['box'];material=source_textures[part['source_material']]
                for face,(fx,fy,fw,fh) in faces(u,v,w,h,d).items():
                    x,y=math.floor(fx*2),math.floor(fy*2);pw=max(1,math.ceil((fx+fw)*2)-x);ph=max(1,math.ceil((fy+fh)*2)-y)
                    atlas.paste(material.resize((pw,ph),Image.Resampling.BILINEAR),(x,y))
                material_regions.append({'faction':faction,'weapon':gun,'part':part['name'],'source_texture':part['source_material'],'atlas_uv':part['uv']})
        dest=output/'src/main/resources/assets/blacklight/textures/entity'/f'{faction}_soldier_layered.png';dest.parent.mkdir(parents=True,exist_ok=True);atlas.save(dest);result_hashes[faction]=digest(dest)
    main=np.array(grips['Left']);support=np.array(grips['Right']);back=(main-support)/np.linalg.norm(main-support)
    right=np.cross([0,1,0],back);right/=np.linalg.norm(right);down=np.cross(back,right);basis=np.column_stack((right,down,back))
    mount_angles=[math.atan2(basis[2,1],basis[2,2]),math.asin(-basis[2,0]),math.atan2(basis[1,0],basis[0,0])]
    # The first-person items remain light JSON geometry; scale the real long axis to the same measured family lengths.
    first_person=[]
    for name,length in lengths.items():
        path=repo/'src/main/resources/assets/blacklight/models/item'/f'{name}.json';model=json.loads(path.read_text(encoding='utf8'))
        span=max(e['to'][0] for e in model['elements'])-min(e['from'][0] for e in model['elements']);scale=length/span
        for hand in ['firstperson_righthand','firstperson_lefthand']:model['display'][hand]['scale']=[round(scale,7)]*3
        dest=output/path.relative_to(repo);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(json.dumps(model,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        def grip_point(support):
            names=['forward grip','foregrip','handguard'] if support else ['pistol grip']
            element=next(e for n in names for e in model['elements'] if e['name']==n)
            point=(np.array(element['from'])+np.array(element['to']))*.5
            if support and element['name']=='handguard':point[1]=element['from'][1]-.15
            if 'rotation' in element:
                r=element['rotation'];angles=[0.,0.,0.];angles['xyz'.index(r['axis'])]=r['angle'];origin=np.array(r['origin']);point=origin+matrix(angles)@(point-origin)
            display=model['display']['firstperson_righthand'];rx,ry,rz=np.radians(display['rotation'])
            cx,sx=np.cos(rx),np.sin(rx);cy,sy=np.cos(ry),np.sin(ry);cz,sz=np.cos(rz),np.sin(rz)
            rotation=np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])
            camera=np.array([.43,-.485,-.72])+np.array(display['translation'])/16
            return (camera+rotation@((point-8)*scale/16)).tolist()
        first_person.append({'weapon':name,'source_long_axis':span,'reference_family_length':length,'firstperson_scale':round(scale,7),
                             'main_grip':grip_point(False),'support_grip':grip_point(True),'grip_provenance':'actual lightweight item JSON, item rotationXYZ/display scale, native arm transform'})
    palette_path=repo/'src/main/resources/assets/blacklight/textures/item/firearms_palette.png';palette=Image.open(palette_path).convert('RGBA')
    for index,(texture,box) in enumerate([(8,(0,0,4,4)),(7,(4,0,8,4)),(6,(8,0,12,4))]):palette.paste(source_textures[texture].resize((4,4),Image.Resampling.BILINEAR),box[:2])
    dest=output/palette_path.relative_to(repo);dest.parent.mkdir(parents=True,exist_ok=True);palette.save(dest)
    profile={'schema':1,'source_sha256':SOURCE_SHA,'source_file':source.name,'author':'1145homo',
        'counts':{'elements':len(elements),'body_cubes':len(body),'weapon_cubes':len(weapon),'textures':len(raw['textures']),'animations':len(animations)},
        'coordinates':{'source':'Blockbench Y-up, model units','native':'Humanoid Y-down, model units','point_rule':'(x,24-y,z)','rotation_rule':'(-x,y,-z), ZYX','render_units_per_model_unit':1/16},
        'bones':list(bones.values()),'body_cubes':body,'animations':animations,'cloth_regions':regions,'weapons':light_weapons,'weapon_material_regions':material_regions,'firstperson_models':first_person,
        'native':{'shoulders':{'right':[-6,0,0],'left':[6,0,0]},'upper_arm_length':6,'forearm_length':6,'carry_origin':[0,4,-5],
            'main_grip':grips['Left'],'support_grip':grips['Right'],'arm_uv':arm_uv,'relative_aim_limit_radians':.10,'reach_limit':11.97,
            'source_weapon_pivot':[-2,5,-5],'source_weapon_yaw_degrees':-33.4,'reference_weapon_length':reference_length,
            'weapon_mount_position':(main-np.array([0,4,-5])).tolist(),'weapon_mount_rotation':mount_angles,'magazine_grip':(main+basis@np.array([0,1.2,-2.5])).tolist()},
        'reload':{'source':'blacklight_authored','duration_is_server_owned':True,'phases':['release_support','magazine_work','return_support'],'release_curve':'sin(pi*progress)^2'},
        'provenance':{'base_atlas_sha256':base_hashes,'retargeted_atlas_sha256':result_hashes,'source_skin_sha256':hashlib.sha256(skin.tobytes()).hexdigest(),
            'excluded_reference_geometry':'Weapon - Left Grip: 5349 cubes, replaced by calibrated native light geometry','face_policy':'original 64x32 pixels at atlas (4,4) untouched',
            'retarget_constraints':'Fixed torso shoulder sockets; two-segment arm IK to rigid weapon grips; torso bears large aim angles and carry translation fits both reach spheres. Source keyframes are preserved unchanged.'}}
    target=output/'src/main/resources/assets/blacklight/military/reference-rig.json';target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(profile,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf8')
    print(json.dumps({'profile':str(target),'body_cubes':len(body),'excluded_weapon_cubes':len(weapon),'animations':len(animations),'cloth_regions':len(regions)},ensure_ascii=False))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[1]);parser.add_argument('--output-root',type=Path);parser.add_argument('--source',type=Path)
    args=parser.parse_args();repo=args.repo.resolve();export(repo,(args.output_root or repo).resolve(),(args.source or repo/'docs/制作资料/参考模型/blackwatch_two_hand_sneak.bbmodel').resolve())
