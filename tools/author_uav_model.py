"""Project-designed drone chassis, struts, sensor and procedural pixel atlas."""
from pathlib import Path
import json,math,random,hashlib
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/blacklight'
colors={'shell':(91,96,93),'edge':(116,121,117),'dark':(31,36,35),'steel':(139,143,137),'rubber':(20,25,24),'grille':(49,55,52),'panel':(77,82,77),'green':(35,240,74),'yellow':(255,202,23),'red':(255,34,24),'sensor':(30,40,43)}
texture=Image.new('RGBA',(256,256),(0,0,0,0));glow=Image.new('RGBA',(256,256),(0,0,0,0));tiles={};rng=random.Random(310161)
for i,(name,rgb) in enumerate(colors.items()):
 x=(i%8)*32;y=(i//8)*32;tiles[name]=(x,y)
 for dx in range(32):
  for dy in range(32):
   delta=rng.randrange(-5,6) if name not in ('red','yellow','green') else 0
   color=tuple(max(0,min(255,v+delta)) for v in rgb)+(255,);texture.putpixel((x+dx,y+dy),color)
   if name in ('red','yellow','green'):glow.putpixel((x+dx,y+dy),color)
 if name=='grille':
  draw=ImageDraw.Draw(texture)
  for k in range(2,31,5):draw.line((x+2,y+k,x+29,y+k),fill=(17,23,21,255),width=2)
 if name=='sensor':
  # Authored crosshatch, not the original UAV diffuse pixels.
  for dx in range(32):
   for dy in range(32):
    if (dx+dy)%8==0 or (dx-dy)%8==0:texture.putpixel((x+dx,y+dy),(42,53,56,255))
bones=[];by={}
def bone(name,parent='root',pivot=(0,0,0)):
 b={'name':name,'pivot':list(pivot),'cubes':[]}
 if parent:b['parent']=parent
 bones.append(b);by[name]=b;return b
def box(owner,center,size,material,rotation=None):
 x,y=tiles[material];cube={'origin':[round(c-s*.5,6) for c,s in zip(center,size)],'size':list(size),'uv':{face:{'uv':[x+1,y+1],'uv_size':[30,30]} for face in ['north','south','east','west','up','down']}}
 # Input angles describe the measured positive-Y authoring space. Gecko bakes
 # JSON into mirrored-X vertices with Euler(-X,-Y,+Z), then applies Rz*Ry*Rx.
 # Encoding (-X,+Y,-Z) makes that runtime transform equal the intended mirrored
 # authoring transform. Raw mathematical X/Z previously inverted the struts.
 if rotation:cube.update(pivot=list(center),rotation=[-rotation[0],rotation[1],-rotation[2]])
 by[owner]['cubes'].append(cube)
def beam(owner,a,b,width,material):
 d=[b[i]-a[i] for i in range(3)];length=math.sqrt(sum(v*v for v in d))
 box(owner,[(a[i]+b[i])*.5 for i in range(3)],[width,length,width],material,[math.degrees(math.asin(d[2]/length)),0,-math.degrees(math.atan2(d[0],d[1]))])
bone('root',None);bone('torso');bone('head','torso')
# The source outer shell is 1.8919 wide. Three narrow bands form the rounded annular shroud.
for radius,y,height,depth,material in [(14.45,3.7,1.1,1.35,'edge'),(14.05,5.85,3.4,2.1,'shell'),(13.25,8.05,1.1,2.8,'edge'),(10.85,7,3,1.0,'dark')]:
 for i in range(32):
  a=i*math.tau/32;box('torso',[math.sin(a)*radius,y,math.cos(a)*radius],[radius*math.tau/32+.06,height,depth],material,[0,math.degrees(a),0])
# Recessed vents, access seams, fasteners and the internal duct/drive supports.
for i in range(12):
 a=i*math.tau/12;box('torso',[math.sin(a)*15.10,5.75,math.cos(a)*15.10],[1.75,1.25,.18],'grille',[0,math.degrees(a),0])
 box('torso',[math.sin(a)*14.2,8.64,math.cos(a)*14.2],[.24,.18,.24],'steel')
for a in [0,math.tau/3,math.tau*2/3]:
 beam('torso',[0,7.8,0],[math.sin(a)*10.5,7.8,math.cos(a)*10.5],.65,'steel')
box('torso',[0,8.35,0],[2.1,1.9,2.1],'dark')
# Integral motor, three spokes and a protected camera pod, all in model pixels.
rotor_y=9.25
bone('rotor_0','torso',(0,rotor_y,0))
box('rotor_0',[0,rotor_y,0],[17,.5,1.8],'dark')
for side in (-1,1):
 box('rotor_0',[side*6.5,rotor_y,side*.45],[3.8,.45,2.1],'panel',[0,side*8,0])
box('rotor_0',[0,rotor_y+.35,0],[1.8,.7,1.8],'steel')
for i in range(3):
 angle=i*math.tau/3
 start=[math.sin(angle)*10.15,8.15,math.cos(angle)*10.15]
 end=[math.sin(angle)*1.3,23.75,math.cos(angle)*1.3]
 beam('torso',start,end,.65,'steel')
 for lo,hi in [(0,.12),(.89,1)]:
  beam('torso',[x+(y-x)*lo for x,y in zip(start,end)],[x+(y-x)*hi for x,y in zip(start,end)],.98,'panel')
box('head',[0,24.4,-.25],[7.5,3.1,4.75],'panel')
box('head',[0,24.4,-2.8],[6.2,2.35,1.05],'rubber')
box('head',[0,24.5,-3.4],[4.7,1.52,.48],'sensor')
for side in (-1,1):
 box('head',[side*3.85,24.4,-.05],[.65,2.2,3.1],'edge')
box('head',[0,24.6,2.25],[4.9,1.85,1.16],'grille')
# Curved underside guard/skid frames, approximated with connected short segments.
for a in [0,math.tau/3,2*math.tau/3]:
 for side in [-1,1]:
  points=[]
  for r,y in [(13.5,3.8),(14.1,2.1),(13.4,.45),(11.7,.12),(10.3,1.4),(9.2,3.8)]:
   points.append([math.sin(a)*r+math.cos(a)*side*1.0,y,math.cos(a)*r-math.sin(a)*side*1.0])
  for p,q in zip(points,points[1:]):beam('torso',p,q,.33,'steel')
# FIG Effect offsets are source XYZ (-.155,.428,-.964), converted to Bedrock's mirrored X.
lamp=[.155*16,.428*16,-.964*16]
box('torso',[lamp[0],lamp[1],lamp[2]+.30],[2.4,2.3,.55],'panel')
# Open bezel: a full rubber cuboid here used to occlude the source-located glass.
for side in [-1,1]:
 box('torso',[lamp[0]+side*.6,lamp[1],lamp[2]+.04],[.2,1.4,.32],'rubber')
 box('torso',[lamp[0],lamp[1]+side*.6,lamp[2]+.04],[1.0,.2,.32],'rubber')
for state in ['green','yellow','red']:
 bone('lamp_'+state,'torso',lamp);box('lamp_'+state,lamp,[.92,.92,.22],state)
geometry={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.blacklight.recon_uav','texture_width':256,'texture_height':256,'visible_bounds_width':4,'visible_bounds_height':4,'visible_bounds_offset':[0,.9,0]},'bones':bones}]}
files={ASSETS/'geo/recon_uav_mc.geo.json':json.dumps(geometry,indent=2)+'\n',ASSETS/'animations/recon_uav.animation.json':json.dumps({'format_version':'1.8.0','animations':{'idle':{'loop':True,'animation_length':1,'bones':{}}}},indent=2)+'\n'}
for path,content in files.items():path.write_text(content,encoding='utf8')
texture.save(ASSETS/'textures/entity/recon_uav_mc.png');glow.save(ASSETS/'textures/entity/recon_uav_mc_glowmask.png')
# Native positive-Y cosmetic wreck, authored from exactly the same cubes.
wreck_tex=Image.new('RGBA',(512,128),(0,0,0,0));wreck_parts=[]
for i,(name,rgb) in enumerate(colors.items()):
 ImageDraw.Draw(wreck_tex).rectangle(((i%8)*64,(i//8)*64,(i%8)*64+63,(i//8)*64+63),fill=rgb+(255,))
for b in bones:
 if b['name'].startswith('lamp_'):continue
 parent='rotor' if b['name']=='rotor_0' else 'sensor' if b['name']=='head' else 'body'
 pivot=[0,rotor_y,0] if parent=='rotor' else [0,26.1,0] if parent=='sensor' else [0,0,0]
 for c in b['cubes']:
  o=c['origin'];sz=c['size'];p=c.get('pivot',[(o[i]+sz[i]*.5) for i in range(3)])
  uv=c['uv']['north']['uv'];idx=int(uv[1]//32)*8+int(uv[0]//32)
  wreck_parts.append({'parent':parent,'box':[-o[0]-sz[0]+p[0],o[1]-p[1],o[2]-p[2],*sz],
   'pivot':[-p[0],p[1]-pivot[1],p[2]],'rotation':[-c.get('rotation',[0,0,0])[0],-c.get('rotation',[0,0,0])[1],c.get('rotation',[0,0,0])[2]],'uv':[(idx%8)*64+1,(idx//8)*64+1]})
wreck={'bones':{'body':[0,0,0],'rotor':[0,rotor_y,0],'sensor':[0,26.1,0]},'parts':wreck_parts}
(ASSETS/'military/recon-uav-wreck.json').write_text(json.dumps(wreck,indent=2)+'\n',encoding='utf8')
wreck_tex.save(ASSETS/'textures/entity/recon_uav_wreck.png')
print('Built live drone and wreck:',len(wreck_parts),'body pieces')
