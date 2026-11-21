"""Editable cuboid items with source-informed running gear and authored hull UVs.

Item-only wheel detail does not alter placed-entity collision, mounts or motion.
Measured P1 wheel instances/profiles are recorded in chassis-running-gear-source.json.
"""
from __future__ import annotations
import io
import math
from PIL import Image, ImageDraw

FACES = ('north', 'east', 'south', 'west', 'up', 'down')
MATERIALS = ('paint','paint_light','paint_dark','steel','steel_light','rubber',
             'brass','brass_light','copper','red','blue','white','black','warning','label','glass')


def display(miniature=False):
    # Vehicle geometry faces +Z. Show its cab/glacis in the inventory; the
    # standard block yaw of 225 degrees presents the rear instead. Minecraft
    # ItemTransform already mirrors the left-hand Y/Z rotations, so both hands
    # need the same authored yaw to retain a corresponding view of the vehicle.
    return {
        'gui': {'rotation':[25,45 if miniature else 225,0], 'translation':[0,0,0], 'scale':[.92]*3},
        'ground': {'rotation':[0,0,0], 'translation':[0,3,0], 'scale':[.55]*3},
        'fixed': {'rotation':[0,180,0], 'translation':[0,0,0], 'scale':[.8]*3},
        'firstperson_righthand': {'rotation':[0,45,0], 'translation':[0,2,0], 'scale':[.65 if miniature else .6]*3},
        'firstperson_lefthand': {'rotation':[0,45 if miniature else 225,0], 'translation':[0,2,0], 'scale':[.65 if miniature else .6]*3},
        'thirdperson_righthand': {'rotation':[0,0,0], 'translation':[0,1,0], 'scale':[.55]*3},
        'thirdperson_lefthand': {'rotation':[0,0,0], 'translation':[0,1,0], 'scale':[.55]*3},
        'head': {'rotation':[0,180,0], 'translation':[0,6,0], 'scale':[.7]*3},
    }


def atlas(item, palette):
    colors = [palette['armor'],palette['armor_light'],palette['armor_dark'],
              '#566269','#C7D0CE','#222A2D','#AD7D3F','#D5AC66','#83552E',
              '#A83E32','#35657D','#D3D7CB','#151B1D','#C2A14E','#D3D7CB','#38606B']
    image=Image.new('RGB',(128,128));pixels=image.load()
    for i,color in enumerate(colors):
        rgb=tuple(bytes.fromhex(color[1:]));ox,oy=i%4*32,i//4*32
        for y in range(32):
            for x in range(32):
                grain=((x*11+y*7+i*5)%5)-2
                highlight=7 if x in (1,2) or y==1 else -6 if x==30 or y==30 else 0
                pixels[ox+x,oy+y]=tuple(max(0,min(255,c+grain+highlight)) for c in rgb)
    warning=image.crop((32,96,64,128));stripes=ImageDraw.Draw(warning)
    for offset in range(-36,36,12):stripes.polygon([(offset,0),(offset+5,0),(offset+37,31),(offset+32,31)],fill='#252B2B')
    image.paste(warning,(32,96));draw=ImageDraw.Draw(image)
    # Keep labels within their own UV island, including two pixels of padding.
    draw.rectangle((64,96,95,127),fill='#CCD1C4');draw.rectangle((66,98,93,125),outline='#545D59')
    label={'tank_he_shell':'HE','tank_ap_shell':'AP','autocannon_round':'AC','military_fuel_can':'FUEL','vehicle_repair_parts':'KIT'}[item]
    draw.text((68,102),label,fill='#293732')
    for yy in (115,119):draw.line((69,yy,90,yy),fill='#56665D')
    result=io.BytesIO();image.save(result,format='PNG');return result.getvalue()


class Mesh:
    def __init__(self):self.elements=[]
    def box(self,name,lo,hi,material,rotation=None):
        index=MATERIALS.index(material);u,v=index%4*4,index//4*4
        element={'name':name,'from':[round(n,5) for n in lo],'to':[round(n,5) for n in hi],
                 'faces':{face:{'uv':[u+.25,v+.25,u+3.75,v+3.75],'texture':'#skin'} for face in FACES}}
        if rotation:element['rotation']=rotation
        self.elements.append(element)
    def section(self,name,x,z,y,height,radius,material,slices=8):
        # A continuous strip profile gives the cylinder a rounded pixel silhouette.
        # Adjacent strips share boundaries, never duplicate coplanar exterior faces.
        for i in range(slices):
            a=-radius+2*radius*i/slices;b=-radius+2*radius*(i+1)/slices
            depth=math.sqrt(max(0,radius*radius-((a+b)/2)**2))
            self.box(f'{name}_{i}',[x+a,y,z-depth],[x+b,y+height,z+depth],material)
    def taper(self,name,x,z,y,height,radius,tip,material,bands=6):
        for i in range(bands):
            r=radius+(tip-radius)*(i+.5)/bands
            self.section(f'{name}_{i}',x,z,y+height*i/bands,height/bands,r,material,6)


def fuel_can():
    m=Mesh()
    m.box('tank_center',[4.3,1.6,5.8],[11.7,11.7,10.2],'paint')
    m.box('tank_shoulders',[4,2.1,6.1],[12,11.2,9.9],'paint')
    m.box('rolled_base',[4,1.2,5.7],[12,1.8,10.3],'paint_dark')
    m.box('top_seam',[4.3,11.6,5.9],[11.7,12,10.1],'paint_light')
    for side,z in [('front',5.65),('back',10.21)]:
        for angle in (-45,45):
            m.box(f'{side}_pressed_rib_{angle}',[7.76,3.8,z],[8.24,9.5,z+.14],'paint_light',
                  {'origin':[8,6.65,z+.07],'axis':'z','angle':angle,'rescale':False})
        m.box(side+'_badge',[6.6,6,z-.02],[9.4,8,z+.18],'label')
    for i,z in enumerate((6.5,8,9.5)):
        for j,x in enumerate((5.1,9.7)):m.box(f'handle_post_{i}_{j}',[x,11.8,z-.23],[x+.65,13.8,z+.23],'paint_dark')
        m.box(f'handle_grip_{i}',[5.1,13.65,z-.28],[10.35,14.4,z+.28],'paint')
        m.box(f'handle_wear_{i}',[6.1,14.4,z-.18],[9.4,14.48,z+.18],'steel')
    m.section('filler_neck',11,8,12,.45,.85,'steel')
    m.section('filler_cap',11,8,12.45,.45,1,'paint_dark')
    m.box('cap_lock',[10.1,12.92,7.82],[11.9,13.14,8.18],'steel_light')
    m.box('cap_hinge',[11.8,12.1,7.7],[12.35,12.65,8.3],'steel')
    for x in (4.15,11.6):
        m.box(f'front_weld_{x}',[x,2.3,5.72],[x+.25,10.8,5.86],'paint_dark')
    return m


def shell(item):
    m=Mesh()
    if item=='autocannon_round':
        for i,x in enumerate((4.3,8,11.7)):
            y=1.6+(i%2)*.6
            m.section(f'case_{i}',x,8,y,7.3,1.35,'brass')
            m.section(f'rim_{i}',x,8,y-.25,.3,1.52,'copper')
            m.section(f'crimp_{i}',x,8,y+7.1,.4,1.4,'steel')
            m.taper(f'projectile_{i}',x,8,y+7.5,3.2,1.25,.17,'paint')
            m.section(f'primer_{i}',x,8,y-.32,.08,.4,'steel',4)
            m.box(f'belt_front_{i}',[x-1.6,y+2.5,6.52],[x+1.6,y+3.3,6.76],'steel')
            m.box(f'belt_rear_{i}',[x-1.6,y+2.5,9.24],[x+1.6,y+3.3,9.48],'steel')
        for i,x in enumerate((6.1,9.8)):m.box(f'link_{i}',[x-.4,4.8,6.5],[x+.4,6.2,9.5],'steel_light')
    else:
        m.section('cartridge',8,8,1.6,6.2,2.15,'brass')
        m.section('extraction_rim',8,8,1.15,.32,2.4,'brass_light')
        m.section('rim_groove',8,8,1.47,.23,1.96,'copper')
        m.section('primer',8,8,1.05,.1,.58,'steel',6)
        m.section('neck',8,8,7.8,.45,1.9,'copper')
        if item=='tank_he_shell':
            m.section('driving_band',8,8,8.25,.5,2.08,'copper')
            m.section('projectile_body',8,8,8.75,1.6,1.9,'red')
            m.taper('ogive',8,8,10.35,3.3,1.9,.35,'red')
            m.section('fuze',8,8,13.65,.5,.35,'steel_light',6)
            m.section('identification_band',8,8,10.35,.22,1.91,'white')
        else:
            m.section('sabot_base',8,8,8.25,.7,2.05,'blue')
            m.section('sabot_collar',8,8,8.95,1.25,1.6,'steel')
            m.section('penetrator',8,8,10.2,2.1,.55,'steel_light',6)
            m.taper('penetrator_tip',8,8,12.3,2.5,.55,.07,'steel_light')
            for i in range(4):
                if i<2:m.box(f'fin_{i}',[7.86,10.2,8+(i*2-1)*.75],[8.14,11.7,8+(i*2-1)*.75+.35],'steel')
                else:m.box(f'fin_{i}',[8+((i-2)*2-1)*.75,10.2,7.86],[8+((i-2)*2-1)*.75+.35,11.7,8.14],'steel')
        m.box('case_identification',[7,4.5,5.84],[9,6.3,5.9],'label')
    return m


def repair_kit():
    m=Mesh()
    m.box('case_bottom',[2.2,2,4],[13.8,2.7,12],'paint_dark')
    for name,lo,hi in [('front',[2.2,2.7,4],[13.8,5.8,4.65]),('back',[2.2,2.7,11.35],[13.8,5.8,12]),
                       ('left',[2.2,2.7,4.65],[2.85,5.8,11.35]),('right',[13.15,2.7,4.65],[13.8,5.8,11.35])]:m.box(name,lo,hi,'paint')
    m.box('tool_tray',[2.85,2.7,4.65],[13.15,3.2,11.35],'rubber')
    m.box('raised_lid',[2.2,5.8,11.35],[13.8,11.6,12],'paint',{'origin':[8,5.8,12],'axis':'x','angle':-22.5,'rescale':False})
    m.box('lid_lining',[2.9,6.5,11.18],[13.1,10.9,11.36],'paint_dark',{'origin':[8,5.8,12],'axis':'x','angle':-22.5,'rescale':False})
    for i,x in enumerate((3.3,11.8)):
        m.box(f'latch_{i}',[x,4,3.72],[x+.9,5.8,4.18],'brass')
        m.box(f'hinge_{i}',[x,5.35,11.8],[x+1,6.2,12.3],'steel')
    for i,(x,z) in enumerate(((2.1,3.9),(13.1,3.9),(2.1,11.1),(13.1,11.1))):m.box(f'corner_{i}',[x,2,z],[x+.8,3.4,z+1],'steel')
    for i,x in enumerate((5.7,9.6)):m.box(f'handle_mount_{i}',[x,3.2,3.4],[x+.7,4.6,4.1],'steel')
    m.box('carry_grip',[5.7,3.35,2.65],[10.3,4.1,3.25],'rubber')
    m.box('socket_rail',[3.3,3.2,9.1],[12.6,3.55,10.3],'steel')
    for i,x in enumerate((4,5.7,7.4,9.1,10.8)):
        m.section(f'socket_{i}',x,9.7,3.55,.9,.55,'steel_light',6)
        m.box(f'socket_hole_{i}',[x-.2,4.45,9.5],[x+.2,4.48,9.9],'black')
    m.box('wrench_shaft',[4.1,3.4,6.2],[10.6,3.8,7.05],'steel_light')
    m.box('wrench_jaw_bridge',[10.3,3.4,5.9],[11.1,3.8,7.35],'steel')
    for i,z in enumerate((5.9,6.95)):m.box(f'wrench_open_jaw_{i}',[11.1,3.4,z],[12.4,3.8,z+.4],'steel_light')
    for i,z in enumerate((6.05,6.8)):m.box(f'wrench_ring_{i}',[3.2,3.4,z],[4.5,3.8,z+.3],'steel_light')
    m.box('wrench_ring_end',[3.2,3.4,6.35],[3.55,3.8,6.8],'steel_light')
    m.box('driver_handle',[3.5,3.3,7.5],[6.3,3.9,8.3],'red')
    m.box('driver_shaft',[6.3,3.5,7.75],[11.9,3.7,8.05],'steel_light')
    m.box('front_label',[6.6,4.3,3.95],[9.4,5.5,3.99],'label')
    return m


def _running_gear(kind, pivots, boxes):
    """Authored stepped wheels fitted inside the existing item hull/track envelope.

    Wheel profiles, tire thickness and hub proportions use the block chassis envelope.
    Each new box reuses a complete existing material UV island (uvSize), so its
    changed dimensions cannot read neighboring islands in the authored atlas.
    """
    source={p['name']:(p,lo,hi) for p,lo,hi in boxes}
    result=[entry for entry in boxes if '_wheel_' not in entry[0]['name']]

    def box(name, lo, hi, template):
        p=dict(template);p['name']=name;p['uvSize']=template['box'][3:]
        result.append((p,list(lo),list(hi)))

    def disc(name, x0, x1, y, z, radius, template, strips=7):
        # Nonoverlapping horizontal bands retain a Minecraft stair-step outline.
        for i in range(strips):
            low=-radius+2*radius*i/strips;high=-radius+2*radius*(i+1)/strips
            depth=math.sqrt(max(0,radius*radius-((low+high)/2)**2))
            box(f'{name}_{i}',[min(x0,x1),y+low,z-depth],[max(x0,x1),y+high,z+depth],template)

    def hub(name, outward, lip, sign, y, z, radius, metal, rubber, ratios):
        # The original militarytiresrivets shader is shared by all three source
        # wheel families. Here authored hub/rubber islands provide the material.
        stages=(('rim',ratios[0],.38,metal,5),('recess',ratios[1],.56,rubber,5))
        if len(ratios)>2:stages+=(('hub',ratios[2],1,metal,3),)
        for label,r,depth,template,strips in stages:
            disc(name+'_'+label,outward-sign*.025,outward+sign*lip*depth,y,z,radius*r,template,strips)

    if kind=='truck':
        for side,sign in [('left',-1),('right',1)]:
            for axle in range(1,4):
                name=f'{side}_wheel_{axle}';tire,lo,hi=source[name+'_tire_vertical'];metal,hlo,hhi=source[name+'_hub']
                _,y,z=pivots[name];radius=(hi[1]-lo[1])/2
                outward=hi[0] if sign>0 else lo[0];hub_edge=hhi[0] if sign>0 else hlo[0]
                width=radius*.5;gap=radius*.125
                for tire_index in range(1 if axle==1 else 2):
                    outer=outward-sign*tire_index*(width+gap)
                    disc(name+f'_tire_{tire_index}',outer-sign*width,outer,y,z,radius,tire)
                hub(name,outward,abs(hub_edge-outward),sign,y,z,radius,metal,tire,(.56,.34,.18))
        return result

    count=6 if kind=='apc' else 7
    pitch=1.0
    native=[('front',.78,-(count+1)*pitch/2,.38)]
    native.extend((f'road_{i+1}',.5,(i-(count-1)/2)*pitch,.48) for i in range(count))
    native.append(('rear' if kind=='apc' else 'sprocket',.76,(count+1)*pitch/2,.42))
    source_low=min(-z-r for _,y,z,r in native);source_high=max(-z+r for _,y,z,r in native)
    source_floor=min(y-r for _,y,z,r in native)
    for side,sign in [('left',-1),('right',1)]:
        prefix=f'track_{side}';tire,lo,hi=source[prefix+'_wheel_1_tire_vertical'];metal,hlo,hhi=source[prefix+'_wheel_1_hub']
        track=[entry for entry in boxes if entry[0]['name'].startswith(prefix+'_') and '_wheel_' not in entry[0]['name']]
        low_z=min(a[2] for _,a,b in track)+.6;high_z=max(b[2] for _,a,b in track)-.6
        floor=min(a[1] for _,a,b in track)+.8
        scale=(high_z-low_z)/(source_high-source_low)
        outward=hi[0] if sign>0 else lo[0];hub_edge=hhi[0] if sign>0 else hlo[0]
        for label,ny,nz,nr in native:
            name=prefix+'_'+label;y=floor+(ny-source_floor)*scale;z=low_z+(-nz-source_low)*scale;radius=nr*scale
            if label=='sprocket':
                disc(name+'_core',lo[0],hi[0],y,z,radius*.76,metal)
                for i in range(13):
                    angle=math.pi/2+i*math.tau/13;cy=y+math.sin(angle)*radius*.83;cz=z+math.cos(angle)*radius*.83;half=radius*.12
                    box(name+f'_tooth_{i}',[lo[0],cy-half,cz-half],[hi[0],cy+half,cz+half],metal)
                hub(name,outward,abs(hub_edge-outward),sign,y,z,radius,metal,tire,(.5,.37))
            else:
                disc(name+'_tire',lo[0],hi[0],y,z,radius,tire)
                hub(name,outward,abs(hub_edge-outward),sign,y,z,radius,metal,tire,(.70,.47,.22))
    return result


def vehicle(item, geometry):
    faction,kind,_=item.split('_');chassis=geometry['chassis'][kind]
    pivots={b['name']:[v*16 for v in b['pivotBlocks']] for b in chassis['bones']}
    boxes=[]
    for p in chassis['parts']:
        lo=[p['box'][i]+pivots[p['parent']][i] for i in range(3)]
        boxes.append((p,lo,[lo[i]+p['box'][i+3] for i in range(3)]))
    boxes=_running_gear(kind,pivots,boxes)
    low=[min(b[1][i] for b in boxes) for i in range(3)];high=[max(b[2][i] for b in boxes) for i in range(3)]
    scale=14.5/max(b-a for a,b in zip(low,high));center=[(a+b)/2 for a,b in zip(low,high)]
    elements=[]
    for p,lo,hi in boxes:
        u,v=p['uv'];w,h,d=p.get('uvSize',p['box'][3:])
        uv={'north':[u+d,v+d,u+d+w,v+d+h],'east':[u,v+d,u+d,v+d+h],
            'south':[u+d+w+d,v+d,u+d+w+d+w,v+d+h],'west':[u+d+w,v+d,u+d+w+d,v+d+h],
            'up':[u+d,v,u+d+w,v+d],'down':[u+d+w,v,u+d+w+w,v+d]}
        elements.append({'name':p['name'],'from':[round(8+(n-c)*scale,6) for n,c in zip(lo,center)],
                         'to':[round(8+(n-c)*scale,6) for n,c in zip(hi,center)],
                         'faces':{face:{'texture':'#skin','uv':[round(n*16/(geometry['textureWidth'] if i%2==0 else geometry['textureHeight']),7) for i,n in enumerate(coords)]} for face,coords in uv.items()}})
    return {'parent':'minecraft:block/block','gui_light':'side','ambientocclusion':False,
            'textures':{'skin':f'blacklight:entity/military_vehicles_{faction}','particle':'blacklight:item/'+item},
            'display':display(True),'elements':elements}


def model(item,geometry):
    if item.endswith('_chassis'):return vehicle(item,geometry)
    mesh=fuel_can() if item=='military_fuel_can' else repair_kit() if item=='vehicle_repair_parts' else shell(item)
    return {'parent':'minecraft:block/block','gui_light':'side','ambientocclusion':False,
            'textures':{'skin':'blacklight:item/'+item,'particle':'#skin'},'display':display(),'elements':mesh.elements}
