"""Bake authored helicopter cuboids and authored ammunition items, without Java/game capture."""
from pathlib import Path
import itertools,json,math
from vehicle_item_models import Mesh,display
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/blacklight'

def rotate(v,axis,degrees):
    a=math.radians(degrees);c,s=math.cos(a),math.sin(a);x,y,z=v
    return {'x':(x,c*y-s*z,s*y+c*z),'y':(c*x+s*z,y,-s*x+c*z),'z':(c*x-s*y,s*x+c*y,z)}[axis]

def helicopter():
    data=json.loads((ASSETS/'military/helicopter-model.json').read_text(encoding='utf-8'));rows=[];bounds=[]
    normals={'north':(0,0,-1),'south':(0,0,1),'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0)}
    for p in data['parts']:
        b=p['box'];pivot=[n*16 for n in data['bones'][p['parent']]];r=p.get('rotation',{});axis=r.get('axis','y');degrees=r.get('degrees',0)
        # Quarter turns are baked into the cuboid. The remaining legal item
        # rotation preserves the original angled cockpit, tail rotor and fins.
        quarter=math.floor((degrees+45)/90)*90;remain=degrees-quarter
        origin=[n*16 for n in r.get('origin',[0,0,0])]
        corners=list(itertools.product(*[(b[i],b[i]+b[i+3]) for i in range(3)]))
        def transformed(point,angle):return [x+o+t for x,o,t in zip(rotate([v-o for v,o in zip(point,origin)],axis,angle),origin,pivot)]
        posed=[transformed(v,quarter) for v in corners];bounds += [transformed(v,degrees) for v in corners]
        lo=[min(v[i] for v in posed) for i in range(3)];hi=[max(v[i] for v in posed) for i in range(3)]
        u,v=p['uv'];w,h,d=b[3:]
        uv={'north':[u+d,v+d,u+d+w,v+d+h],'east':[u,v+d,u+d,v+d+h],
            'south':[u+d+w+d,v+d,u+d+w+d+w,v+d+h],'west':[u+d+w,v+d,u+d+w+d,v+d+h],
            'up':[u+d,v,u+d+w,v+d],'down':[u+d+w,v,u+d+w+w,v+d]}
        faces={}
        for old,n in normals.items():
            dest=tuple(round(t) for t in rotate(n,axis,quarter));face=next(f for f,m in normals.items() if m==dest)
            faces[face]={'texture':'#skin','uv':[round(n*16/(data['textureWidth'] if i%2==0 else data['textureHeight']),7) for i,n in enumerate(uv[old])]}
        rows.append((p['name'],lo,hi,[a+b for a,b in zip(origin,pivot)],axis,remain,faces))
    low=[min(v[i] for v in bounds) for i in range(3)];high=[max(v[i] for v in bounds) for i in range(3)];scale=14.5/max(b-a for a,b in zip(low,high));center=[(a+b)/2 for a,b in zip(low,high)]
    def point(p):return [round(8+(v-c)*scale,6) for v,c in zip(p,center)]
    elements=[]
    for name,lo,hi,origin,axis,angle,faces in rows:
        e={'name':name,'from':point(lo),'to':point(hi),'faces':faces}
        if angle:e['rotation']={'origin':point(origin),'axis':axis,'angle':angle,'rescale':False}
        elements.append(e)
    return {'parent':'minecraft:block/block','gui_light':'side','ambientocclusion':False,'textures':{'skin':'blacklight:entity/blackwatch_helicopter','particle':'#skin'},'display':display(True),'elements':elements}

def ammunition(kind):
    m=Mesh()
    if kind=='helicopter_cannon_round':
        m.section('case',8,8,1.5,7.4,1.7,'brass');m.section('rim',8,8,1.15,.35,1.95,'brass_light')
        m.section('primer',8,8,1.05,.1,.45,'steel');m.section('neck',8,8,8.9,.4,1.4,'copper')
        m.section('projectile',8,8,9.3,1.3,1.32,'paint');m.taper('nose',8,8,10.6,3.6,1.32,.12,'paint')
        m.section('band',8,8,10.2,.35,1.34,'white')
    else:
        heavy=kind=='helicopter_hellfire_missile';r=1.3 if heavy else .9
        m.section('motor',8,8,1.5,9,r,'paint_dark' if heavy else 'steel')
        m.section('warhead',8,8,10.5,1.2,r,'paint');m.taper('nose',8,8,11.7,2.7,r,.08,'paint')
        m.section('identification',8,8,9.8,.45,r+.03,'blue' if heavy else 'warning')
        m.section('nozzle',8,8,1.1,.4,r*.9,'steel_light');m.section('exhaust',8,8,1,.12,r*.55,'black')
        for y,height,width in ([(2,2.8,1.55),(7.7,1.8,1.05)] if heavy else [(1.6,2.3,1.3)]):
            for side in (-1,1):
                x=8+side*r;m.box(f'fin_x_{y}_{side}',[min(x,x+side*width),y,7.86],[max(x,x+side*width),y+height,8.14],'steel')
                z=8+side*r;m.box(f'fin_z_{y}_{side}',[7.86,y,min(z,z+side*width)],[8.14,y+height,max(z,z+side*width)],'steel')
    return {'parent':'minecraft:block/block','gui_light':'side','ambientocclusion':False,'textures':{'skin':'blacklight:item/tank_he_shell','particle':'#skin'},'display':display(),'elements':m.elements}

def main():
    # Entity renderers load this PNG directly, but baked inventory models use
    # the blocks atlas. Its default directory sources omit textures/entity.
    atlas_path=ROOT/'src/main/resources/assets/minecraft/atlases/blocks.json'
    atlas=json.loads(atlas_path.read_text(encoding='utf-8')) if atlas_path.exists() else {'sources':[]}
    source={'type':'minecraft:single','resource':'blacklight:entity/blackwatch_helicopter'}
    if source not in atlas.setdefault('sources',[]):
        atlas['sources'].append(source)
        atlas_path.parent.mkdir(parents=True,exist_ok=True)
        atlas_path.write_text(json.dumps(atlas,indent=2)+'\n',encoding='utf-8')
    for name in ['blackwatch_helicopter_chassis','helicopter_cannon_round','helicopter_hellfire_missile','helicopter_70mm_rocket']:
        model=helicopter() if name.endswith('chassis') else ammunition(name)
        (ASSETS/f'models/item/{name}.json').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
        print(name,len(model['elements']))
if __name__=='__main__':main()
