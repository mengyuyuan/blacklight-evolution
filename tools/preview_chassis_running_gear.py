"""Static item-JSON raster preview only: no Java, game, GL, tests or source pixels.

Optional --before points at six previously saved item JSONs for comparison.
"""
from pathlib import Path
import argparse,json,math
import numpy as np
from PIL import Image,ImageDraw
from preview_blackwatch_reference import triangle,font

ROOT=Path(__file__).resolve().parents[1]
ITEMS=[f'{f}_{k}_chassis' for k in ('truck','apc','tank') for f in ('military','blackwatch')]

def render(model,width=640,height=410,yaw=45,pitch=25,scale=30,focus=None):
    a,b=map(math.radians,(yaw,pitch));cy,sy,cx,sx=math.cos(a),math.sin(a),math.cos(b),math.sin(b)
    camera=np.array([[cy,0,sy],[sx*sy,cx,-sx*cy],[-cx*sy,sx,cx*cy]])
    canvas=np.full((height,width,4),[231,236,230,255],dtype=np.uint8);depth=np.full((height,width),-np.inf)
    path=ROOT/'src/main/resources/assets/blacklight/textures'/Path(model['textures']['skin'].split(':')[1]+'.png')
    texture=np.asarray(Image.open(path).convert('RGBA'));light=np.array([-.5,1,.6]);light/=np.linalg.norm(light)
    focus=np.array(focus if focus is not None else [8,8,8])
    for element in model['elements']:
        x0,y0,z0=element['from'];x1,y1,z1=element['to']
        faces={
            'north':([0,0,-1],[(x0,y1,z0),(x1,y1,z0),(x1,y0,z0),(x0,y0,z0)]),
            'south':([0,0,1],[(x1,y1,z1),(x0,y1,z1),(x0,y0,z1),(x1,y0,z1)]),
            'west':([-1,0,0],[(x0,y1,z1),(x0,y1,z0),(x0,y0,z0),(x0,y0,z1)]),
            'east':([1,0,0],[(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(x1,y0,z0)]),
            'up':([0,1,0],[(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)]),
            'down':([0,-1,0],[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)])}
        for face,(normal,corners) in faces.items():
            normal=np.array(normal)
            if normal@camera[2]<=0:continue
            uv=np.array(element['faces'][face]['uv'])/16
            tex=np.array([[uv[0],uv[1]],[uv[2],uv[1]],[uv[2],uv[3]],[uv[0],uv[3]]])
            points=(np.array(corners)-focus)@camera.T;points[:,0]=points[:,0]*scale+width/2;points[:,1]=-points[:,1]*scale+height/2+15
            shade=.64+.36*max(0,float(normal@light))
            for indices in [(0,1,2),(0,2,3)]:triangle(canvas,depth,points[list(indices)],tex[list(indices)],texture,shade)
    return Image.fromarray(canvas)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--before',type=Path);parser.add_argument('--output',type=Path,default=ROOT/'art/military-vehicles/previews/chassis-running-gear');args=parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=True);sheet=Image.new('RGB',(1280,3*450+90),'#e7ece6');d=ImageDraw.Draw(sheet)
    d.text((26,16),'六种车架 · 真实物品 JSON 静态预览',font=font(28),fill='#21392d');d.text((26,53),'当前作者化贴图与方块几何；不是游戏截图。',font=font(17),fill='#52645b')
    models={}
    for i,item in enumerate(ITEMS):
        model=json.loads((ROOT/f'src/main/resources/assets/blacklight/models/item/{item}.json').read_text());models[item]=model
        image=render(model);draw=ImageDraw.Draw(image);kind=item.split('_')[1]
        name=('军方' if item.startswith('military') else '黑色守望')+' · '+{'truck':'卡车','apc':'装甲车','tank':'坦克'}[kind]
        draw.text((24,14),name,font=font(25),fill='#21392d');draw.text((24,378),f'{len(model["elements"])} 个方块部件 · 原有车体 / 专属轮组',font=font(16),fill='#52645b')
        image.save(args.output/(item+'.png'));sheet.paste(image,(i%2*640,90+i//2*450))
    sheet.save(args.output/'six-chassis.png')
    if args.before:
        board=Image.new('RGB',(1280,3*410+80),'#e7ece6');draw=ImageDraw.Draw(board);draw.text((24,15),'轮组几何对照：修改前 / 修改后',font=font(28),fill='#21392d')
        for row,kind in enumerate(('truck','apc','tank')):
            item=f'military_{kind}_chassis';after=models[item];before=json.loads((args.before/(item+'.json')).read_text())
            for col,model in enumerate((before,after)):
                # Side diagnostic exposes both elevated end wheels; the truck's
                # rear three-quarter close-up exposes the actual double-tire gap.
                if kind=='truck':
                    selected=[e for e in after['elements'] if e['name'].startswith('left_wheel_') and ('_2' in e['name'] or '_3' in e['name'])]
                    lo=np.min([e['from'] for e in selected],axis=0);hi=np.max([e['to'] for e in selected],axis=0);focus=(lo+hi)/2
                    image=render(model,yaw=130,pitch=8,scale=84,focus=focus)
                else:
                    selected=[e for e in after['elements'] if e['name'].startswith('track_left')]
                    lo=np.min([e['from'] for e in selected],axis=0);hi=np.max([e['to'] for e in selected],axis=0);focus=(lo+hi)/2
                    image=render(model,yaw=90,pitch=4,scale=41,focus=focus)
                d=ImageDraw.Draw(image);d.text((22,14),{'truck':'卡车后两轴','apc':'装甲车左履带','tank':'坦克左履带'}[kind]+(' · 修改前' if col==0 else ' · 修改后'),font=font(23),fill='#21392d')
                board.paste(image,(col*640,80+row*410))
        board.save(args.output/'running-gear-before-after.png')
    print('Wrote static item JSON previews to',args.output)

if __name__=='__main__':main()
