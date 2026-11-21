"""Authored Blackwatch transport mesh and UV atlas. No extracted game meshes or textures."""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageColor

ROOT=Path(__file__).resolve().parents[1]
bones={'body':[0,0,0],'rotor':[0,4.05,0],'tail_rotor':[-.45,2.85,-5.8],
       'door_left':[-1.46,1.15,-.2],'door_right':[1.46,1.15,-.2],
       'cockpit_left':[-1.4,1.4,2.6],'cockpit_right':[1.4,1.4,2.6],
       'gun':[0,1,3.6],'pod_left':[-1.9,1.4,1.3],'pod_right':[1.9,1.4,1.3],
       'rapid_left':[-2.66,1.4,1.3],'rapid_right':[2.66,1.4,1.3]}
parts=[]
colors={'armor':'#283439','edge':'#52616A','dark':'#121A1E','glass':'#46717C',
        'rubber':'#202426','metal':'#9BA9AC','seat':'#374844','white':'#A5B2A8','red':'#BE4237','green':'#439A70','yellow':'#BA9B47'}

def box(name,lo,hi,material='armor',bone='body',rotation=None):
    pivot=bones[bone]
    row={'name':name,'parent':bone,'box':[round((lo[i]-pivot[i])*16,4) for i in range(3)]+[round((hi[i]-lo[i])*16,4) for i in range(3)],'material':material}
    if rotation:row['rotation']=rotation
    parts.append(row)

box('belly',[-1.25,.7,-2.7],[1.25,1.15,2.9])
box('cabin_floor',[-1.45,1.05,-2.4],[1.45,1.25,2.6],'edge')
box('cabin_roof',[-1.48,2.65,-2.55],[1.48,2.9,2.7])
box('rear_bulkhead',[-1.43,1.2,-2.65],[1.43,2.65,-2.35])
box('nose_lower',[-1.25,1.15,2.6],[1.25,1.65,3.65])
box('nose_tip',[-.95,1.45,3.65],[.95,1.75,3.8],'dark')
box('instrument_panel',[-1.12,1.8,2.48],[1.12,2.05,2.82],'dark')
for side in (-1,1):
    x0,x1=(.05,1.3) if side>0 else (-1.3,-.05)
    box(f'windshield_{side}',[x0,1.85,2.8],[x1,2.61,2.88],'glass',rotation={'axis':'x','degrees':-22.5,'origin':[0,2.3,2.8]})
    box(f'nose_shoulder_{side}',[x0,1.65,2.6],[x1,1.9,3.35])
    box(f'windshield_pillar_{side}',[side*1.3-.06,1.7,2.6],[side*1.3+.06,2.7,2.92],'edge')
    cockpit='cockpit_left' if side<0 else 'cockpit_right'
    box(f'cockpit_side_glass_{side}',[side*1.4-.04,1.9,1.25],[side*1.4+.04,2.62,2.6],'glass',cockpit)
    box(f'cockpit_door_lower_{side}',[side*1.4-.07,1.2,1.15],[side*1.4+.07,1.9,2.6],bone=cockpit)
    box(f'cockpit_door_handle_{side}',[side*1.5-.04,1.75,1.4],[side*1.5+.04,1.85,1.72],'metal',cockpit)
    box(f'cabin_rear_side_{side}',[side*1.42-.07,1.2,-2.4],[side*1.42+.07,2.65,-1.85])
    box(f'door_front_pillar_{side}',[side*1.43-.07,1.2,.95],[side*1.43+.07,2.7,1.15],'edge')
    box(f'door_track_{side}',[side*1.5-.05,2.64,-2.45],[side*1.5+.05,2.78,1.12],'metal')
    bone='door_left' if side<0 else 'door_right'
    box(f'sliding_door_lower_{side}',[side*1.46-.06,1.2,-1.82],[side*1.46+.06,1.95,.94],bone=bone)
    box(f'sliding_door_window_{side}',[side*1.46-.04,1.95,-1.65],[side*1.46+.04,2.52,.7],'glass',bone)
    for z in (-1.82,.7):box(f'door_frame_{side}_{z}',[side*1.46-.07,1.95,z],[side*1.46+.07,2.65,z+.16],'edge',bone)
    box(f'sliding_door_handle_{side}',[side*1.56-.04,1.73,.55],[side*1.56+.04,1.85,.82],'white',bone)
    box(f'engine_nacelle_{side}',[side*.72-.42,2.9,-1.25],[side*.72+.42,3.45,1.55])
    box(f'engine_intake_{side}',[side*.72-.36,3.0,1.55],[side*.72+.36,3.35,1.75],'dark')
    for j in range(4):box(f'intake_grille_{side}_{j}',[side*.72-.29+j*.17,3.02,1.75],[side*.72-.25+j*.17,3.33,1.79],'edge')
    box(f'engine_exhaust_{side}',[side*.72-.32,3.0,-1.65],[side*.72+.32,3.33,-1.22],'metal')
    box(f'exhaust_bore_{side}',[side*.72-.25,3.05,-1.7],[side*.72+.25,3.28,-1.64],'dark')
    box(f'landing_strut_{side}',[side*1.15-.1,.35,-1.9],[side*1.15+.1,1.2,-1.67],'metal')
    box(f'landing_axle_{side}',[min(side*1.15,side*1.58),.38,-1.92],[max(side*1.15,side*1.58),.55,-1.66],'metal')
    for j,(yy,zz,hh,dd) in enumerate(((0,-2.0,.85,.48),(.15,-2.18,.55,.84))):
        box(f'main_tire_{side}_{j}',[side*1.48-.18,yy,zz],[side*1.48+.18,yy+hh,zz+dd],'rubber')
    box(f'main_wheel_hub_{side}',[side*1.48-.2,.27,-1.95],[side*1.48+.2,.57,-1.67],'metal')
    # Tie the visible step into the belly at both ends.
    for j,z in enumerate((-1.53,.73)):
        box(f'step_bracket_{side}_{j}',[min(side*1.17,side*1.6),.79,z],
            [max(side*1.17,side*1.6),.88,z+.09],'dark')
    for j,z in enumerate((1.32,2.32)):
        box(f'canopy_hinge_{side}_{j}',[side*1.45-.055,1.82,z],[side*1.45+.055,1.99,z+.14],'edge',cockpit)
    box(f'wheel_fairing_{side}',[side*1.1-.20,.90,-2.08],[side*1.1+.20,1.22,-1.54])
    box(f'nacelle_shoulder_{side}',[side*.72-.47,2.86,-1.32],[side*.72+.47,3.10,1.37],'edge')
    box(f'cabin_step_{side}',[side*1.6-.08,.75,-1.7],[side*1.6+.08,.9,.9],'metal')
    for j,z in enumerate((-.55,-1.55)):
        box(f'troop_seat_{side}_{j}',[side*.8-.4,1.15,z-.3],[side*.8+.4,1.35,z+.3],'seat')
        box(f'troop_seat_back_{side}_{j}',[side*1.08-.15,1.35,z-.3],[side*1.08+.15,2,z+.3],'seat')
    box(f'pilot_seat_{side}',[side*.65-.4,1.15,1.65],[side*.65+.4,1.35,2.3],'seat')
    box(f'pilot_back_{side}',[side*.65-.4,1.35,1.6],[side*.65+.4,2.25,1.8],'seat')
    box(f'nav_lamp_{side}',[side*1.55-.06,2.5,-2.3],[side*1.55+.06,2.65,-2.08],'red' if side<0 else 'green')
    box(f'unit_stripe_{side}',[side*1.5-.025,1.4,-2.27],[side*1.5+.025,2.3,-2.12],'white')
box('windshield_center',[-.065,1.8,2.77],[.065,2.67,2.95],'edge',rotation={'axis':'x','degrees':-22.5,'origin':[0,2.3,2.8]})
box('cockpit_brow',[-1.4,2.57,2.55],[1.4,2.73,2.83],'edge')
box('main_transmission',[-.48,3.05,-.3],[.48,3.7,.5],'dark')
box('rotor_mast',[-.12,3.55,-.12],[.12,4.05,.12],'metal')
box('main_hub',[-.45,3.95,-.45],[.45,4.2,.45],'edge','rotor')
for i in range(4):
    angle=i*90
    box(f'rotor_blade_{i}',[.35,4.01,-.17],[5.55,4.09,.22],'dark','rotor',{'axis':'y','degrees':angle})
    box(f'rotor_tip_{i}',[5.55,4.01,-.17],[6.05,4.09,.22],'white','rotor',{'axis':'y','degrees':angle})
    box(f'rotor_pitch_link_{i}',[.15,3.9,-.08],[.85,4.01,.08],'metal','rotor',{'axis':'y','degrees':angle})
for i in range(4):
    radius=.62-i*.11;z=-2.55-i*.8
    box(f'tail_boom_{i}',[-radius,1.65+i*.18,z-.8],[radius,2.65+i*.08,z])
box('tail_root_shoulder',[-.91,1.68,-2.98],[.91,2.67,-2.46])
box('tail_root_crown',[-.66,2.64,-2.89],[.66,2.79,-2.48],'edge')
box('fin_root',[-.25,2.43,-5.98],[.25,2.83,-5.31])
box('mast_flange',[-.29,3.65,-.24],[.29,3.76,.24],'edge')
box('tail_fin',[-.13,2.1,-6.25],[.13,3.72,-5.65])
box('tailplane',[-1.4,2.1,-5.7],[1.4,2.23,-5.05],'edge')
box('tail_rotor_hub',[-.6,2.71,-5.94],[-.25,2.99,-5.66],'metal','tail_rotor')
for i in range(4):
    box(f'tail_rotor_blade_{i}',[-.48,2.88,-5.9],[-.4,3.85,-5.72],'dark','tail_rotor',{'axis':'x','degrees':i*90})
box('front_gear_strut',[-.09,.35,2.25],[.09,1.15,2.46],'metal')
box('front_tire',[-.18,0,2.05],[.18,.7,2.6],'rubber')
box('front_hub',[-.2,.22,2.2],[.2,.47,2.45],'metal')
box('searchlight_body',[-.32,1.03,3.3],[.32,1.42,3.66],'dark')
box('searchlight_lens',[-.24,1.08,3.66],[.24,1.36,3.7],'white')
box('roof_beacon',[-.1,2.9,-2.3],[.1,3.1,-2.1],'red')
box('nose_sensor',[-.2,1.75,3.45],[.2,2.0,3.72],'dark')

box('chin_gun_mount',[-.28,.85,3.3],[.28,1.15,3.73],'edge')
box('gun_receiver',[-.19,.81,3.55],[.19,1.19,4.0],'dark','gun')
for i in range(6):
    import math
    angle=i*math.pi/3;x=math.cos(angle)*.105;y=1+math.sin(angle)*.105
    box(f'gun_barrel_{i}',[x-.035,y-.035,3.9],[x+.035,y+.035,4.8],'metal','gun')
box('gun_forward_collar',[-.16,.84,4.5],[.16,1.16,4.61],'dark','gun')
# Source TOD attaches inner Hellfire racks and outboard rapid pods separately.
# Preserve the authored transport hull; retain the original D1/D2 lateral ratio.
# Detail remains authored geometry, not extracted original mesh/texture data.
hellfire_slots=[(.162815,.134876,.010615),(.162933,-.134876,-.010615),(-.162933,-.134876,-.010615),(-.162933,.134876,.010615)]
for side in (-1,1):
    x=side*1.9;bone='pod_left' if side<0 else 'pod_right'
    box(f'wing_spar_{side}',[min(side*1.35,side*2.86),1.68,.6],[max(side*1.35,side*2.86),1.79,1.8],'edge')
    box(f'inner_pylon_{side}',[x-.09,1.46,1.12],[x+.09,1.74,1.48],'dark',bone)
    for dx in (-.095,.095):
        box(f'rack_rail_{side}_{dx}',[x+dx-.028,1.40,.67],[x+dx+.028,1.47,1.93],'metal',bone)
    for z in (.88,1.72):box(f'rack_crossbeam_{side}_{z}',[x-.2,1.30,z-.035],[x+.2,1.50,z+.035],'edge',bone)
    for slot,(dx,dy,dz) in enumerate(hellfire_slots):
        xx=x+dx;yy=1.4+dy;zz=1.3+dz;missile=f'hellfire_{side}_{slot}';bones[missile]=[xx,yy,zz]
        # Stepped pixel hull, cap and fins fit the rack's free space.
        box(missile+'_body',[xx-.055,yy-.055,zz-.70],[xx+.055,yy+.055,zz+.48],'armor',missile)
        for i,(begin,end,radius) in enumerate(((.48,.58,.048),(.58,.66,.036),(.66,.70,.02))):
            box(missile+f'_nose_{i}',[xx-radius,yy-radius,zz+begin],[xx+radius,yy+radius,zz+end],'dark',missile)
        box(missile+'_band',[xx-.057,yy-.057,zz+.25],[xx+.057,yy+.057,zz+.29],'yellow',missile)
        box(missile+'_nozzle',[xx-.044,yy-.044,zz-.706],[xx+.044,yy+.044,zz-.681],'dark',missile)
        box(missile+'_tail_horizontal',[xx-.12,yy-.012,zz-.63],[xx+.12,yy+.012,zz-.40],'edge',missile)
        box(missile+'_tail_vertical',[xx-.012,yy-.15,zz-.63],[xx+.012,yy+.15,zz-.40],'edge',missile)
        for z in (-.3,.13):box(missile+f'_clip_{z}',[xx-.062,yy+.052,zz+z-.03],[xx+.062,yy+.10,zz+z+.03],'metal',missile)
    px=side*2.66;pod='rapid_left' if side<0 else 'rapid_right'
    box(f'rapid_pylon_{side}',[px-.08,1.52,1.1],[px+.08,1.75,1.5],'dark',pod)
    box(f'rapid_core_{side}',[px-.17,1.23,.552],[px+.17,1.57,2.048],'armor',pod)
    for k in range(8):
        box(f'rapid_shell_{side}_{k}',[px-.078,1.575,.552],[px+.078,1.602,2.048],'edge',pod,{'axis':'z','degrees':k*45,'origin':[0,0,0]})
    box(f'rapid_front_recess_{side}',[px-.155,1.245,2.049],[px+.155,1.555,2.06],'dark',pod)
    # Authored honeycomb face; original pod identity/bounds are verified, this
    # cuboid port tessellation is not claimed to reproduce the original topology.
    n=0
    for row in range(-2,3):
        count=5-abs(row)
        for col in range(count):
            tx=px+(col-(count-1)/2)*.06;ty=1.4+row*.052
            box(f'rapid_port_lip_{side}_{n}',[tx-.026,ty-.026,2.057],[tx+.026,ty+.026,2.079],'metal',pod)
            box(f'rapid_port_dark_{side}_{n}',[tx-.018,ty-.018,2.079],[tx+.018,ty+.018,2.082],'dark',pod);n+=1

# Unique UV net for each cuboid, packed into a 1024 atlas.
image=Image.new('RGB',(1024,1024),'#142028');draw=ImageDraw.Draw(image);x=y=2;row_h=0
for part in parts:
    w,h,d=[max(1,int(__import__('math').ceil(v))) for v in part['box'][3:]]
    uw,uh=2*w+2*d,h+d
    if x+uw+2>1024:x=2;y+=row_h+3;row_h=0
    if y+uh+2>1024:raise ValueError('Helicopter atlas overflow')
    part['uv']=[x,y];color=colors[part['material']]
    draw.rectangle((x,y,x+uw,y+uh),fill=color)
    base=ImageColor.getrgb(color)
    def tone(value):return tuple(max(0,min(255,c+value)) for c in base)
    # Paint each ModelPart face separately. Inset seams follow panel edges,
    # and the glass reflection stays inside the glazing at oblique angles.
    for fx,fy,fw,fh in ((d,0,w,d),(d+w,0,w,d),(0,d,d,h),(d,d,w,h),(d+w,d,d,h),(2*d+w,d,w,h)):
        if fw<4 or fh<4:continue
        left,top=x+fx,y+fy
        right,bottom=left+fw-1,top+fh-1
        material=part['material']
        if material=='glass':
            for row in range(fh):
                draw.line((left,top+row,right,top+row),fill=tone(round(14-23*row/max(1,fh-1))))
            for col in range(fw):
                row=fh//4+(fw-col)//4
                if row<fh-1:draw.point((left+col,top+row),fill=tone(19))
        elif material in ('armor','edge','metal') and fw>=9 and fh>=6:
            draw.line((left,top,right,top),fill=tone(9))
            draw.line((left,bottom,right,bottom),fill=tone(-8))
            if fw>=17 and fh>=12:
                draw.line((left+2,top+2,left+2,bottom-2),fill=tone(-7))
                for corner in (left+3,right-3):
                    for row in (top+3,bottom-3):draw.point((corner,row),fill=tone(18))
    x+=uw+3;row_h=max(row_h,uh)
model={'schemaVersion':1,'textureWidth':1024,'textureHeight':1024,'bones':bones,'parts':parts}
for root in (ROOT,):
    asset=root/'src/main/resources/assets/blacklight';(asset/'military').mkdir(parents=True,exist_ok=True)
    (asset/'military/helicopter-model.json').write_text(json.dumps(model,indent=2)+'\n','utf8')
    image.save(asset/'textures/entity/blackwatch_helicopter.png')
