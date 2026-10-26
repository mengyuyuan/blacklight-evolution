"""Own Java-item geometry and pixel atlas, informed by the locally verified P1 cinematic prop.
No original-game model or texture bytes are copied into the runtime asset.
Run from any directory; outputs are relative to this repository.
"""
from __future__ import annotations
import base64
import hashlib
import io
import json
import math
from pathlib import Path
import random
import uuid
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'art/virus-vial'
ASSETS = ROOT / 'src/main/resources/assets/blacklight'
SIZE, DENSITY = 128, 4
atlas = Image.new('RGBA', (SIZE, SIZE))
patches, elements, measurements = {}, [], []
cursor_x, cursor_y, row_h = 1, 1, 0


def patch(kind: str, w: float, h: float) -> list[float]:
    global cursor_x, cursor_y, row_h
    px, py = max(1, round(w * DENSITY)), max(1, round(h * DENSITY))
    key = kind, px, py
    if key not in patches:
        if cursor_x + px + 1 > SIZE:
            cursor_x, cursor_y, row_h = 1, cursor_y + row_h + 2, 0
        if cursor_y + py + 1 > SIZE:
            raise ValueError('Vial atlas budget exceeded')
        tile = Image.new('RGBA', (px, py))
        rng = random.Random(int.from_bytes(hashlib.sha256(repr(key).encode()).digest()[:8]))
        for y in range(py):
            for x in range(px):
                v = rng.choice([-3, -1, 0, 0, 1, 2])
                edge = x in (0, px - 1)
                if kind == 'glass':
                    color = (125, 146, 138, 255) if (x == 0 and y % 17 < 5) or (x == px-1 and y % 23 < 2) else (0, 0, 0, 0)
                elif kind == 'fluid':
                    flow = 12 if (x + y // 9) % max(2, px) == 0 else 0
                    color = (90 + flow + v, 13 + v, 24 + v, 255)
                elif kind == 'fluid_end':
                    color = (117 + v, 19 + v, 33 + v, 255)
                elif kind == 'rail':
                    n = (40 if x == 0 else -23 if x == px - 1 else 0)
                    color = (108+n+v, 113+n+v, 111+n+v, 255)
                else:
                    n = -12 if y in (0, py-1) else 35 if y == 1 else 0
                    base = 77 if kind == 'cap_end' else 57
                    color = (base+n+v, base+4+n+v, base+2+n+v, 255)
                if kind in ('cap_end', 'fluid_end'):
                    u, t = abs((x+.5)/px*2-1), abs((y+.5)/py*2-1)
                    if u+t > 1+math.tan(math.pi/8):
                        color = (0,0,0,0)
                tile.putpixel((x,y), color)
        if kind == 'cap_end':
            d = ImageDraw.Draw(tile)
            d.rectangle((px//2-2,py//2-2,px//2+2,py//2+2),fill=(39,42,42,255))
            d.line((px//2-1,py//2,px//2+1,py//2),fill=(142,32,38,255))
        atlas.paste(tile, (cursor_x,cursor_y))
        patches[key] = [cursor_x,cursor_y,cursor_x+px,cursor_y+py]
        cursor_x += px+2
        row_h = max(row_h, py)
    rectangle = patches[key]
    measurements.append({'material':kind,'size_units':[w,h],'size_pixels':[px,py],
                         'density':[round(px/w,4),round(py/h,4)]})
    return [v / SIZE * 16 for v in rectangle]


def cube(name, low, high, material, faces, turn=0):
    dims = [high[i]-low[i] for i in range(3)]
    mapping={}
    for f in faces:
        w,h = (dims[0],dims[2]) if f in ('up','down') else (dims[2],dims[1]) if f in ('east','west') else (dims[0],dims[1])
        mapping[f]={'uv':patch(material,w,h),'texture':'#vial'}
    e={'name':name,'from':low,'to':high,'faces':mapping}
    if turn:
        e['rotation']={'origin':[8,8,8],'axis':'y','angle':turn}
    elements.append(e)


def ring(name, radius, y0, y1, thickness=0, material='metal'):
    half=radius*math.tan(math.pi/8)
    for angle in (0,45):
        # Each half is made from cardinal panels; Java accepts the 45-degree rotation.
        for side in ('north','east','south','west'):
            a,b=[8-half,y0,8-radius],[8+half,y1,8-radius+thickness]
            if side=='south': a,b=[8-half,y0,8+radius-thickness],[8+half,y1,8+radius]
            if side=='east': a,b=[8+radius-thickness,y0,8-half],[8+radius,y1,8+half]
            if side=='west': a,b=[8-radius,y0,8-half],[8-radius+thickness,y1,8+half]
            faces=('north','south') if side in ('north','south') else ('east','west')
            cube(f'{name}_{side}_{angle}',a,b,material,faces,angle)


def cap(name, radius, y, material):
    cube(name,[8-radius,y,8-radius],[8+radius,y,8+radius],material,('up','down'))


def main():
    SOURCE.mkdir(parents=True,exist_ok=True)
    ring('lower_metal_collar',2.3,1,2.4,.24)
    cap('lower_end_face',2.3,1,'cap_end')
    cap('lower_inside_face',2.3,2.4,'cap_end')
    ring('upper_metal_collar',2.3,13.6,15,.24)
    cap('upper_inside_face',2.3,13.6,'cap_end')
    cap('upper_end_face',2.3,15,'cap_end')
    for sx in (-1,1):
        for sz in (-1,1):
            x,z=8+sx*1.46,8+sz*1.46
            # Rail ends are buried in the collars, so no internal end quads.
            cube(f'protective_rail_{sx}_{sz}',[x-.20,2.25,z-.20],[x+.20,13.75,z+.20],
                 'rail',('north','east','south','west'))
    ring('glass_tube',1.7,2.3,13.7,material='glass')
    ring('virus_contents',1.45,2.4,10.75,material='fluid')
    cap('contents_bottom',1.45,2.4,'fluid_end')
    cap('contents_meniscus',1.45,10.75,'fluid_end')
    display={
        'thirdperson_righthand':{'rotation':[80,0,5],'translation':[0,1.3,0.5],'scale':[.55]*3},
        'thirdperson_lefthand':{'rotation':[80,0,-5],'translation':[0,1.3,0.5],'scale':[.55]*3},
        'firstperson_righthand':{'rotation':[0,-14,8],'translation':[1,1.2,0],'scale':[.65]*3},
        'firstperson_lefthand':{'rotation':[0,14,-8],'translation':[1,1.2,0],'scale':[.65]*3},
        'gui':{'rotation':[18,32,-18],'translation':[0,0,0],'scale':[1]*3},
        'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.45]*3},
        'fixed':{'rotation':[0,180,0],'scale':[.8]*3},
    }
    model={'credit':'Blacklight virus vial: original pixel geometry and atlas',
           'ambientocclusion':True,'gui_light':'side','render_type':'minecraft:cutout',
           'textures':{'vial':'blacklight:item/blacklight_vial','particle':'blacklight:item/blacklight_vial'},
           'elements':elements,'display':display}
    target=ASSETS/'models/item/blacklight_sample.json'
    target.write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
    texture=ASSETS/'textures/item/blacklight_vial.png'
    atlas.save(texture,optimize=True)
    blob=io.BytesIO();atlas.save(blob,format='PNG',optimize=True)
    editable=[]
    for e in elements:
        b={k:v for k,v in e.items() if k not in ('rotation','faces')}
        b.update({'type':'cube','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'blacklight:vial/'+e['name'])),
                  'box_uv':False,'rescale':False,'origin':[8,8,8],
                  'faces':{f:{'uv':[v*SIZE/16 for v in e['faces'][f]['uv']],'texture':0}
                           if f in e['faces'] else {'uv':[0,0,0,0],'texture':None}
                           for f in ('north','east','south','west','up','down')}})
        if 'rotation' in e: b['rotation']=[0,e['rotation']['angle'],0]
        editable.append(b)
    bb={'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},
        'name':'Blacklight Virus Vial','model_identifier':'blacklight_sample','visible_box':[1,1,0],
        'resolution':{'width':SIZE,'height':SIZE},'elements':editable,'outliner':[e['uuid'] for e in editable],
        'textures':[{'path':str(texture),'name':'blacklight_vial.png','folder':'item','namespace':'blacklight',
                     'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'blacklight:vial/atlas')),
                     'id':'0','particle':True,'render_mode':'default','source':'data:image/png;base64,'+base64.b64encode(blob.getvalue()).decode()}],
        'display':display}
    (SOURCE/'blacklight_vial.bbmodel').write_text(json.dumps(bb,indent=2)+'\n',encoding='utf-8')
    atlas.save(SOURCE/'blacklight_vial.png',optimize=True)
    faces=sum(len(e['faces']) for e in elements)
    report={'elements':len(elements),'quads':faces,'triangles':faces*2,'atlas':[SIZE,SIZE],
            'png_bytes':texture.stat().st_size,'effective_uv_density':measurements,
            'source_movie_references':['NIS_4-5_Gentek4_48fr.bik at 0.7s','NIS_7_3_FMV.bik at 3.2s'],
            'reference_limits':'Cinematic tube/cage silhouette verified; unreadable labels and exact fill level are not claimed to be original.'}
    assert len(elements)<=44 and faces*2<=320
    (SOURCE/'build-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ('elements','quads','triangles','atlas','png_bytes')}))


if __name__=='__main__': main()
