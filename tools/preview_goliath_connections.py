"""Static authored-cube preview, using SourceBossMotion/Gecko transformations.

No game or Java is launched. This is a geometric review sheet, not acceptance
of final lighting, texture filtering, pose interpolation, or gameplay.
"""
from pathlib import Path
import argparse, itertools, json, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]

def transform(translation=(0,0,0),rotation=(0,0,0),scale=(1,1,1)):
    x,y,z=rotation;cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    value=np.eye(4)
    value[:3,:3]=np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],
        [sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])@np.diag(scale)
    value[:3,3]=translation;return value

def skin(geo,table,clip,frame):
    # Integer source frames avoid a second implementation of quaternion SLERP.
    names=table['bones'];rows=np.array(table['clips'][clip]['frames'][frame],dtype=float)
    for i,name in enumerate(names):
        if name.startswith('source_parent_scale_'):rows[i,6:9]=1/rows[table['parents'][i],6:9]
    matrices=[]
    for i,row in enumerate(rows):
        pivot=np.array(table['pivots'][i])/16
        local=transform(pivot+row[3:6]/16,row[:3],row[6:9])@transform(-pivot)
        parent=table['parents'][i];matrices.append(local if parent<0 else matrices[parent]@local)
    owners={b['name']:b for b in geo['minecraft:geometry'][0]['bones']};out={}
    for name,bone in owners.items():
        mapped=name
        while mapped not in names:
            assert not owners[mapped].get('rotation') and not owners[mapped].get('position')
            mapped=owners[mapped]['parent']
        for ci,cube in enumerate(bone.get('cubes',[])):
            pivot=np.array(cube.get('pivot',[0,0,0]))*[-1,1,1]/16
            angles=np.radians(cube.get('rotation',[0,0,0]))*[-1,-1,1]
            # Same -X/+Y/+Z positions, -X/-Y/+Z cube Euler signs and RzRyRx
            # as GoliathContact/Gecko, then entity yaw=0 -> model Y rotation pi.
            matrix=transform(rotation=(0,math.pi,0))@matrices[names.index(mapped)]@transform(pivot,angles)@transform(-pivot)
            points=[]
            for corner in itertools.product([0,1],repeat=3):
                p=(np.array(cube['origin'])+np.array(cube['size'])*corner)*[-1,1,1]/16
                points.append((matrix@np.r_[p,1])[:3]+[0,.01,0])
            out[(name,ci)]=(np.array(points),cube)
    return out

def separation(a,b):
    ea=[a[4]-a[0],a[2]-a[0],a[1]-a[0]];eb=[b[4]-b[0],b[2]-b[0],b[1]-b[0]]
    axes=[np.cross(e[i],e[(i+1)%3]) for e in [ea,eb] for i in range(3)]+[np.cross(x,y) for x in ea for y in eb]
    gaps=[]
    for axis in axes:
        length=np.linalg.norm(axis)
        if length<1e-12:continue
        axis=axis/length;pa=a@axis;pb=b@axis
        gaps.append(max(pb.min()-pa.max(),pa.min()-pb.max()))
    return float(max(gaps))

def connections(cubes):
    pairs=[('torso_lower','torso_waist'),('torso_waist','torso_middle'),('torso_middle','torso'),('torso_lower','left_leg'),('torso_lower','right_leg')]
    out={}
    for a,b in pairs:
        aa=[p for (n,_),(p,_) in cubes.items() if n==a];bb=[p for (n,_),(p,_) in cubes.items() if n==b]
        if not aa:aa=[p for (n,_),(p,_) in cubes.items() if n=='torso']
        if not bb:bb=[p for (n,_),(p,_) in cubes.items() if n=='torso']
        out[a+' -> '+b]=min(separation(x,y) for x in aa for y in bb)
    return out

def render(cubes,size,title,texture,focus=False):
    w,h=size;image=Image.new('RGB',size,(230,233,237));draw=ImageDraw.Draw(image)
    font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',20)
    draw.text((14,10),title,fill=(25,30,35),font=font)
    yaw=math.radians(25);view=transform(rotation=(math.radians(9),yaw,0))[:3,:3]
    allpoints=np.concatenate([p@view.T for p,_ in cubes.values()]);lo=allpoints.min(axis=0);hi=allpoints.max(axis=0)
    # One shared viewport for every panel, keeping before/after scale comparable.
    zoom=52 if focus else 35;cx=w/2-70 if focus else w/2;cy=h-85
    def project(point):return (cx+point[0]*zoom,cy-point[1]*zoom)
    faces=[('west',[0,1,3,2]),('east',[4,6,7,5]),('down',[0,4,5,1]),('up',[2,3,7,6]),('north',[0,2,6,4]),('south',[1,5,7,3])]
    polygons=[]
    for (name,_),(points,cube) in cubes.items():
        for face,indices in faces:
            world=points[indices];v=world@view.T
            uv=cube.get('uv',{}).get(face)
            if uv:
                u,t=uv['uv'];sx,sy=uv['uv_size'];crop=texture.crop((u,t,u+max(1,sx),t+max(1,sy))).convert('RGB')
                color=np.array(crop.resize((1,1)).getpixel((0,0)),dtype=float)
            else:color=np.array([110,70,65],dtype=float)
            normal=np.cross(world[1]-world[0],world[2]-world[0]);normal/=max(1e-12,np.linalg.norm(normal))
            shade=.6+.4*abs(np.dot(normal,np.array([.3,.8,.5])/np.linalg.norm([.3,.8,.5])))
            color=tuple(int(max(0,min(255,x*shade))) for x in color)
            polygons.append((v[:,2].mean(),[project(p) for p in v],color))
    for _,points,color in sorted(polygons,key=lambda row:row[0]):draw.polygon(points,fill=color,outline=(45,39,38))
    draw.line((0,cy,w,cy),fill=(155,162,168),width=1)
    note='Torso + legs only; arms hidden for geometric review' if focus else 'Geometric preview / yaw 0 / same scale'
    draw.text((14,h-45),note,fill=(70,75,80),font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',15))
    return image

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--before',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    current=ROOT/'src/main/resources/assets/blacklight'
    old_geo=json.loads((args.before/'goliath_mc.geo.json').read_text());old_table=json.loads((args.before/'encounter_goliath_source.json').read_text())
    geo=json.loads((current/'geo/goliath_mc.geo.json').read_text());table=json.loads((current/'models/encounter_goliath_source.json').read_text())
    texture=Image.open(current/'textures/entity/goliath_mc.png');cases=[('idle',0),('walk',20),('HAMMER',48),('CHARGE',100),('ERUPTION',0)]
    sheet=Image.new('RGB',(640*len(cases),700*2),(230,233,237));focus=sheet.copy();rows=[]
    for column,(clip,frame) in enumerate(cases):
        for row,(label,g,t) in enumerate([('BEFORE',old_geo,old_table),('AFTER',geo,table)]):
            cubes=skin(g,t,clip,frame);sheet.paste(render(cubes,(640,700),f'{label}: {clip} source frame {frame}',texture),(column*640,row*700))
            body={key:value for key,value in cubes.items() if key[0].startswith('torso') or key[0] in ['left_leg','right_leg','left_shin','right_shin','head','jaw']}
            focus.paste(render(body,(640,700),f'{label}: {clip} source frame {frame}',texture,True),(column*640,row*700))
            rows.append(dict(version=label,clip=clip,frame=frame,separatingAxisGapBlocks=connections(cubes)))
    args.output.parent.mkdir(parents=True,exist_ok=True);sheet.save(args.output)
    focus.save(args.output.with_name(args.output.stem+'-waist.png'))
    args.output.with_suffix('.json').write_text(json.dumps({'method':'Gecko/SourceBossMotion integer-frame TRS; negative separation means cuboid overlap; flat face-mean atlas colors, not game lighting','samples':rows},indent=2)+'\n')
    print(json.dumps(rows))

if __name__=='__main__':main()
