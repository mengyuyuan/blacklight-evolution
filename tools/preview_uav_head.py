"""Static Gecko cube transform comparison. No Java/game or original P1 texture use."""
from pathlib import Path
import argparse,json,math
import numpy as np
from PIL import Image,ImageDraw
from preview_blackwatch_reference import triangle,font
ROOT=Path(__file__).resolve().parents[1]

def rotation(degrees):
 x,y,z=map(math.radians,degrees);cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
 return np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])

def render(geometry,texture,yaw=180,pitch=7,width=500,height=520,detail=False):
 camera=rotation([pitch,yaw,0]);canvas=np.full((height,width,4),[228,235,229,255],dtype=np.uint8);depth=np.full((height,width),-np.inf)
 scale=28 if detail else 13;focus=np.array([0,24.8,0] if detail else [0,14,0]);texture=np.asarray(Image.open(texture).convert('RGBA'))
 for bone in geometry['minecraft:geometry'][0]['bones']:
  if bone['name'].startswith('lamp_') and bone['name']!='lamp_red':continue
  for cube in bone.get('cubes',[]):
   x0,y0,z0=cube['origin'];x1,y1,z1=np.array(cube['origin'])+np.array(cube['size'])
   faces={'north':([0,0,-1],[(x0,y1,z0),(x1,y1,z0),(x1,y0,z0),(x0,y0,z0)]),'south':([0,0,1],[(x1,y1,z1),(x0,y1,z1),(x0,y0,z1),(x1,y0,z1)]),
    'west':([-1,0,0],[(x0,y1,z1),(x0,y1,z0),(x0,y0,z0),(x0,y0,z1)]),'east':([1,0,0],[(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(x1,y0,z0)]),
    'up':([0,1,0],[(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)]),'down':([0,-1,0],[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)])}
   # Gecko mirrors vertices/pivot in X and bakes angles(-X,-Y,+Z).
   # Display that actual runtime result back in authoring coordinates: the
   # equivalent transform is Rz(-Z)*Ry(Y)*Rx(-X), not the raw JSON Euler.
   angles=cube.get('rotation',[0,0,0]);r=rotation([-angles[0],angles[1],-angles[2]]);pivot=np.array(cube.get('pivot',[0,0,0]))
   for face,(normal,vertices) in faces.items():
    normal=np.array(normal)@r.T
    if normal@camera[2]<=0:continue
    uv=cube['uv'][face];u,v=uv['uv'];w,h=uv['uv_size'];tex=np.array([[u,v],[u+w,v],[u+w,v+h],[u,v+h]])/256
    points=((np.array(vertices)-pivot)@r.T+pivot-focus)@camera.T;points[:,0]=points[:,0]*scale+width/2;points[:,1]=-points[:,1]*scale+height/2+10
    shade=.66+.34*max(0,float(normal@np.array([-.4,.7,-.59])))
    for indices in [(0,1,2),(0,2,3)]:triangle(canvas,depth,points[list(indices)],tex[list(indices)],texture,shade)
 return Image.fromarray(canvas)

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--before',type=Path,required=True);args=parser.parse_args()
 root=ROOT/'src/main/resources/assets/blacklight';before=json.loads((args.before/'recon_uav_mc.geo.json').read_text(encoding='utf-8'));after=json.loads((root/'geo/recon_uav_mc.geo.json').read_text(encoding='utf-8'))
 sheet=Image.new('RGB',(1500,1100),'#e4ebe5')
 for row,(geo,tex,label) in enumerate([(before,args.before/'recon_uav_mc.png','修改前'),(after,root/'textures/entity/recon_uav_mc.png','修改后')]):
  for col,(yaw,name) in enumerate([(180,'正面'),(90,'侧面'),(140,'斜视')]):
   image=render(geo,tex,yaw=yaw);ImageDraw.Draw(image).text((20,12),label+' · '+name,font=font(25),fill='#20382c');sheet.paste(image,(500*col,40+530*row))
 ImageDraw.Draw(sheet).text((20,8),'无人机 · 按 Gecko 实际旋转转换的 geo JSON 对照（非游戏截图）',font=font(23),fill='#20382c')
 out=ROOT/'art/military2/previews';out.mkdir(parents=True,exist_ok=True);sheet.save(out/'uav-head-before-after.png')
 detail=Image.new('RGB',(1200,410),'#e4ebe5')
 for col,(geo,tex,label) in enumerate([(before,args.before/'recon_uav_mc.png','修改前'),(after,root/'textures/entity/recon_uav_mc.png','修改后')]):
  image=render(geo,tex,yaw=140,width=600,height=410,detail=True);ImageDraw.Draw(image).text((20,12),label+' · 顶部连接',font=font(25),fill='#20382c');detail.paste(image,(600*col,0))
 detail.save(out/'uav-head-detail-before-after.png');print(out)
if __name__=='__main__':main()
