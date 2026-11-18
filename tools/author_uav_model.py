"""Authored voxel geometry/surface from measured P1 UAV references; no source mesh/texture is embedded."""
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
# Original uav001Blade has one joint at Y=0.5846875906 and spans X +/-0.5295076.
rotor_y=.5846875905990601*16;bone('rotor_0','torso',(0,rotor_y,0))
box('rotor_0',[0,rotor_y,0],[16.94424,.28653,1.5],'dark')
for side in [-1,1]:
 box('rotor_0',[side*6.25,rotor_y,side*.5],[4.1,.30,1.9],'panel',[0,side*7,0])
box('rotor_0',[0,rotor_y+.3,0],[1.55,.6,1.55],'steel')
# P1 MeshShape has three disconnected six-point triangular struts. These are
# the two endpoint-face centroids, in source units, not guessed convergence.
struts=[([-.499000,.597924,-.362583],[-.041923,1.573165,-.033779]),
        ([.499356,.597924,-.362583],[.042279,1.573165,-.033779]),
        ([0,.603426,.617719],[0,1.555918,.067797])]
def source_point(p):return [-p[0]*16,p[1]*16,p[2]*16]
for a,b in struts:
 start,end=source_point(a),source_point(b)
 # The retained authored shroud's inner lip is lower than the source upper
 # shoulder. Extend the SAME measured strut line into that lip, so measured
 # top attachments do not leave floating feet above our existing body mesh.
 t=(8.25-start[1])/(end[1]-start[1]);start=[x+t*(y-x) for x,y in zip(start,end)]
 beam('torso',start,end,.46,'steel')
 # The sockets overlap the shroud and sensor housing along the same strut.
 for lo,hi,width in [(0,.10,.88),(.91,1,.73)]:
  p=[x+(y-x)*lo for x,y in zip(start,end)];q=[x+(y-x)*hi for x,y in zip(start,end)]
  beam('torso',p,q,width,'panel')
# The 86-point connected head is a SOLID flattened oval, not a ring around a
# rectangular housing. Fit continuous authored cuboid bands to measured bounds.
head_bounds=[[-.3339794,1.49146235,-.20075928],[.33397949,1.72888458,-.03665394]]
head_center=source_point([(a+b)/2 for a,b in zip(*head_bounds)])
head_radius=[(b-a)*8 for a,b in zip(*head_bounds)]
for iy in range(9):
 yn=-1+(iy+.5)*2/9
 for iz in range(5):
  zn=-1+(iz+.5)*2/5;remaining=1-yn*yn-zn*zn
  if remaining<=0:continue
  width=2*head_radius[0]*math.sqrt(remaining)
  box('head',[head_center[0],head_center[1]+yn*head_radius[1],head_center[2]+zn*head_radius[2]],
      [width,2*head_radius[1]/9,2*head_radius[2]/5],'sensor')
# Separate 33-point rear barrel: oval cross-section, long axis Z. Its measured
# front intersects the cap; strut tips enter its underside rather than floating.
barrel_bounds=[[-.15757427,1.54857337,-.16645387],[.15757425,1.66432357,.16645387]]
barrel_center=source_point([(a+b)/2 for a,b in zip(*barrel_bounds)])
barrel_radius=[(b-a)*8 for a,b in zip(*barrel_bounds)]
for iy in range(7):
 yn=-1+(iy+.5)*2/7;width=2*barrel_radius[0]*math.sqrt(1-yn*yn)
 box('head',[barrel_center[0],barrel_center[1]+yn*barrel_radius[1],barrel_center[2]],
     [width,2*barrel_radius[1]/7,2*barrel_radius[2]],'panel')
 # A narrow authored termination band keeps the rear cylindrical silhouette.
 box('head',[barrel_center[0],barrel_center[1]+yn*barrel_radius[1],barrel_radius[2]-.12],
     [width,2*barrel_radius[1]/7,.24],'edge')
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
report={'origin':'Authored voxel mesh and procedural surface measured against private P1 original UAV geometry; original assets are not embedded','sourcePackageSha256':'deb7ddceb62f5ef91c6230d02170d992c3b93af6fa837524872d04d039ee27a5','sourceMeshBounds':[[-.9459512,-.00281513,-.96015084],[.94595075,1.7288846,.94595104]],'sourceRotorJoint':[0,.5846875905990601,0],'sourceLampOffset':[-.155,.428,-.964],'bedrockLamp':lamp,'rotors':1,'cubes':sum(len(b['cubes']) for b in bones),'stateEffects':{'idle':'lightGlowGreen','seeing':'lightGlowYellow','alert':'lightGlowRed'},'limits':['Voxel surface approximates curved source geometry','Detection-duration and lease values remain Minecraft adaptations, not decoded original timing']}
report['headCorrection']={'sourceProof':'D:/PrototypeReference/p1/private/uav/head-measurements.json','method':'1002-vertex P1 main mesh, welded triangle-connected components: solid cap86 points, rear barrel33 points, three struts6 points each. No native mesh or texture copied.','sourceCapBounds':head_bounds,'sourceBarrelBounds':barrel_bounds,'sourceStrutEndpointCentroids':struts,'mapping':'Bedrock [-X,Y,Z]*16. New solid voxel ellipse and rear elliptical cylinder replace hollow segmented trim and square backing blocks. Existing torso shell, single rotor and lamp placement remain unchanged.','adaptation':'Cap uses9x5 ellipsoid bands; barrel7 bands; strut square width.46px approximates original triangular cross-section. Strut upper endpoints and direction use measured source, while each lower end is extended along that same line to authored Y8.25 to intersect the retained inner shroud lip rather than float above it. Rear termination band and crosshatch are authored material cues. Exact native cap curvature/asymmetric barrel contour are approximations.','status':'Static source/asset generation only; no Java/client or game verification.'}
report['runtimeRotationCorrection']={'cause':'The prior beam angles used mathematical authoring Euler directly as Gecko JSON. Mirrored vertices plus Gecko Euler(-X,-Y,+Z) reversed the horizontal lean of non-horizontal beams; the previous preview omitted that conversion and falsely showed attached struts.','encoding':'Authoring Rz(z)*Ry(y)*Rx(x) becomes JSON[-x,y,-z]; Gecko baked Euler[-JSON.x,-JSON.y,JSON.z] then correctly transforms the mirrored-X vertices.','scope':'Three upper support rods and underside skid frames. Head remains fixed, its measured bounds unchanged; shell, rotor origin, light location and textures retain their authored values. Wreck export uses baked Gecko angles so its geometry stays aligned with the live model.','evidence':'art/military2/uav-gecko-rotation-review-20261117.json','preview':'preview_uav_head.py now reconstructs the actual Gecko cube transform, shown back in authoring axes for comparison.','validation':'Static endpoints/geometry only; no game or JVM executed.'}
report['files']={str(p.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in [*files,ASSETS/'textures/entity/recon_uav_mc.png',ASSETS/'textures/entity/recon_uav_mc_glowmask.png',ASSETS/'military/recon-uav-wreck.json',ASSETS/'textures/entity/recon_uav_wreck.png']}
(ROOT/'art/military2/uav-model-source.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8');print('Authored',report['cubes'],'cubes, one rotor, source-located three-state lamp')
