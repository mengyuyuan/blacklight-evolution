"""Deterministic cuboid asset authoring; no reference image is mapped onto geometry."""
from pathlib import Path
import math, json, uuid, random, base64, sys
from collections import Counter
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parent
BAT_RIG=True
OUT=ROOT/'model-v12'
OUT.mkdir(exist_ok=True)
STAGE=sys.argv[1] if len(sys.argv)>1 else 'detail'
RNG=random.Random(161016)
NS=uuid.UUID('8c0056eb-7eb0-486f-af74-a6543b07b654')
uid=lambda n:str(uuid.uuid5(NS,n))
elements=[]; bones=[]; groups={}; counters=Counter()
PALETTE=[(17,17,20),(26,25,29),(36,35,39),(49,47,52),(42,8,14),(65,11,20),(193,23,29),(255,59,42),(24,13,19),(125,25,32),(40,35,38),(88,84,85)]
tex=Image.new('RGBA',(256,256),(25,25,28,255))
em=Image.new('RGBA',(256,256),(0,0,0,255))
tr=random.Random(91016)
for idx,col in enumerate(PALETTE):
 ox=(idx%4)*64; oy=(idx//4)*64
 for y in range(64):
  for x in range(64):
   if idx in (6,7): variation=tr.choice([-5,0,0,3])
   else: variation=tr.choice([-6,-3,-2,0,0,0,2,3,5])
   # Pixel-scale clusters, never smooth photoreal noise.
   variation+=((x//4+y//3+idx)%5-2)*1.3
   c=tuple(max(0,min(255,int(t+variation))) for t in col)
   tex.putpixel((ox+x,oy+y),c+(255,))
   if idx in (6,7): em.putpixel((ox+x,oy+y),c+(255,))
tex.save(OUT/'sovereign_wings.png'); em.save(OUT/'sovereign_wings_emissive.png')

def bone(name,pivot,parent=None):
 b={'name':name,'uuid':uid(name),'pivot':list(pivot),'parent':parent}
 bones.append(b);groups[name]=b
 return name

bone('wing_mount',(0,24,3.5))

def cube(tag,center,size,rz=0,mat=1,parent='wing_mount'):
 if min(size)<=0: raise ValueError(size)
 counters[tag]+=1;n=f'{tag}_{counters[tag]:04d}'
 c=np.array(center);s=np.array(size)
 dimensions={'north':(s[0],s[1]),'south':(s[0],s[1]),'east':(s[2],s[1]),'west':(s[2],s[1]),'up':(s[0],s[2]),'down':(s[0],s[2])}
 faces={}
 for direction,(w,h) in dimensions.items():
  uw=max(.25,float(w*2));uh=max(.25,float(h*2))
  assert uw<=60 and uh<=60,(n,uw,uh)
  u=(mat%4)*64+1+RNG.randrange(max(1,int(61-uw)))
  v=(mat//4)*64+1+RNG.randrange(max(1,int(61-uh)))
  faces[direction]={'uv':[u,v,round(u+uw,5),round(v+uh,5)],'texture':0}
 e={'name':n,'type':'cube','uuid':uid(n),'from':np.round(c-s/2,5).tolist(),'to':np.round(c+s/2,5).tolist(),'origin':np.round(c,5).tolist(),'rotation':[0,0,round(rz,5)],'faces':faces,'box_uv':False,'rescale':False,'inflate':0,'color':mat%8,'visibility':True,'autouv':0,'_parent':parent,'_material':mat}
 if BAT_RIG and '_web_' in tag:e['_skin']=True
 elements.append(e)
 return e

def rod(tag,p,q,width,thickness,mat,parent):
 p=np.array(p,dtype=float);q=np.array(q,dtype=float);v=q-p
 length=float(np.linalg.norm(v))
 if length<.01:return
 # Full 3-D orientation, using the same XYZ convention as the custom renderer.
 rx=math.degrees(math.atan2(v[2],v[1]))
 rz=-math.degrees(math.atan2(v[0],math.hypot(v[1],v[2])))
 e=cube(tag,(p+q)/2,(width,length+.07,thickness),rz,mat,parent)
 e['rotation'][0]=rx
 return e

# The shoulder assembly follows the supplied front/right/back sheet. Keep the
# hinge at the scapular crest; animation must accommodate this anatomical root.
def shoulder_clearance(p,contact=False):
 x,y,z=p;side=1 if x>=0 else -1
 t=max(0,min(1,(y-16)/6));t=t*t*(3-2*t)
 return [x+side*1.2*t*math.exp(-abs(x)/18),y,z]

def depth(x,y):return 3.6+1.65*math.sin(abs(x)/34*math.pi)+.025*(y-24)
def xyz(p,side):return shoulder_clearance((p[0]*side,p[1],depth(p[0],p[1])))

def inside(x,y,poly):
 yes=False
 for a,b in zip(poly,poly[1:]+poly[:1]):
  if (a[1]>y)!=(b[1]>y) and x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0]:yes=not yes
 return yes

def membrane(name,poly,holes,side,parent):
 poly=[tuple(shoulder_clearance((x,y,0))[:2]) for x,y in poly]
 holes=[(*shoulder_clearance((x,y,0))[:2],rx,ry) for x,y,rx,ry in holes]
 step=.35
 loX=math.floor(min(p[0] for p in poly)/step);hiX=math.ceil(max(p[0] for p in poly)/step)
 loY=math.floor(min(p[1] for p in poly)/step);hiY=math.ceil(max(p[1] for p in poly)/step)
 mask=set()
 for iy in range(loY,hiY):
  for ix in range(loX,hiX):
   x=(ix+.5)*step;y=(iy+.5)*step
   if not inside(x,y,poly):continue
   if any(((x-cx)/rx)**2+((y-cy)/ry)**2<1 for cx,cy,rx,ry in holes):continue
   mask.add((ix,iy))
 # Greedy rectangulation gives real square-edged openings without thousands
 # of separate voxel tiles or transparency-sorting dependencies.
 while mask:
  x,y=min(mask,key=lambda p:(p[1],p[0]));w=1
  while (x+w,y) in mask:w+=1
  h=1
  while all((x+i,y+h) in mask for i in range(w)):h+=1
  for iy in range(y,y+h):
   for ix in range(x,x+w):mask.remove((ix,iy))
  cx=(x+w/2)*step;cy=(y+h/2)*step
  z=5.15
  cube(name,(cx*side,cy,z),(w*step,h*step,.22),0,4,parent)


# Interwoven upper branches with four unequal descending talons.
# Landmark chains traced from the LEFT wing of the original 1536x1024 concept.
# Player proxy: 32 units, image head-top 242 / soles 790; centre X600.
# Perspective is not an orthographic blueprint; rear depth remains inferred.
def landmark(points):return [((600-x)/17.125,(790-y)/17.125) for x,y in points]
ribs=[landmark(p) for p in [
 [(505,373),(491,343),(471,324),(428,298),(389,257),(349,222),(330,190),(310,145),(286,96)],
 [(514,382),(487,354),(457,328),(413,289),(375,268),(341,230),(304,205),(269,197)],
 [(502,386),(482,358),(455,334),(415,307),(381,283),(348,251),(318,247)],
 [(520,376),(492,363),(467,347),(418,327),(385,307),(365,285),(341,279)]]]
rays=[landmark(p) for p in [
 [(286,96),(251,114),(220,162),(191,208),(150,248),(126,292),(97,354),(79,419),(57,514),(27,636)],
 [(269,197),(240,240),(214,285),(197,346),(175,409),(157,477),(126,590),(101,751)],
 [(318,247),(303,300),(303,373),(283,455),(268,525),(258,609),(257,718),(275,888)],
 [(341,279),(329,327),(339,406),(350,490),(370,586),(395,699),(424,790)]]]
rib_widths=[[1.1,1.15,1.3,1.5,1.45,1.65,1.8,1.9,2.1],[1.1,1.0,1.2,1.4,1.3,1.7,1.9,2.2],[1.0,1.1,1.3,1.4,1.2,1.9,2.1],[.95,1.0,1.1,1.2,1.4,1.7,1.8]]
ray_widths=[[2.1,1.8,1.7,1.8,2.0,1.9,2.1,1.6,1.0,.06],[2.2,1.8,2.2,2.3,2,1.7,.95,.06],[2.1,1.5,1.6,2.1,1.75,2.8,2.0,.06],[1.8,1.3,1.5,1.65,1.9,1.15,.06]]
skin_paths={};root_paths={}
groups['wing_mount']['pivot']=[0,20,2]

def armored_beam(tag,path,side,parent,wa,wb,widths=None):
 lengths=[math.dist(a,b) for a,b in zip(path,path[1:])];total=sum(lengths);travel=0
 for j,(a,b) in enumerate(zip(path,path[1:])):
  distance=lengths[j];count=max(1,round(distance/(2.3+(j%3)*.37)))
  for i in range(count):
   start=np.array(a)+(np.array(b)-a)*i/count
   stop=np.array(a)+(np.array(b)-a)*(i+1)/count
   progress=(travel+distance*(i+.5)/count)/total
   width=(widths[j]+(widths[j+1]-widths[j])*(i+.5)/count) if widths else wb+(wa-wb)*(1-progress)**.72
   p=np.array(xyz(start,side));q=np.array(xyz(stop,side));center=(p+q)/2
   angle=-math.degrees(math.atan2(q[0]-p[0],q[1]-p[1]));axis=np.array([math.cos(math.radians(angle)),math.sin(math.radians(angle)),0])
   thick=max(.20,width*.60);length=math.dist(start,stop);rh=RNG.uniform(-.10,.10)
   rod(tag+'_core',p,q,width,thick,0,parent)
   if STAGE!='blockout':
    for face in (-1,1):
     # Broken channels fork between overlapping asymmetric armour masses.
     offset=np.array([0,0,face*(thick*.5+.10)])
     u=p+offset+axis*width*.16;v=q+offset-axis*width*.13
     rod(tag+'_vein_tissue',u-np.array([0,0,face*.045]),v-np.array([0,0,face*.045]),max(.12,width*.23),.12,9,parent)
     rod(tag+'_fissure',u,v,max(.055,width*.07),.13,6,parent)
     if (j+i)%2==0:
      rod(tag+'_fissure_fork',(u+v)/2,(u+v)/2+axis*width*.43+np.array([0,.55,0]),.09,.10,7 if j%3==0 else 6,parent)
     for k in (-1,1):
      pc=center+axis*width*(.35*k+rh)+np.array([0,RNG.uniform(-.25,.25),face*(thick*.5+.20)])
      # Trim the neck-facing lip of the innermost shoulder plate.
      if tag.endswith('_arch_4_inner') and j==0 and i==0 and k==-side and face==1:pc+=axis*side*width*.4
      plate=cube(tag+'_armour',pc,(width*(.52 if k==1 else .44),length*(RNG.uniform(.95,1.28) if k==1 else RNG.uniform(.60,1.10)),.32+width*.1),angle+side*k*RNG.uniform(1,10),RNG.choice([0,1,1,2,2,3]),parent)
      plate['rotation'][1]=side*face*k*RNG.uniform(3,13)
     if width>1.3 and (j+i)%3==0:
      pc=center+axis*width*.55+np.array([0,.35,face*.25])
      cube(tag+'_barb',pc,(width*.32,length*1.2,thick*.8),angle-side*17,1,parent)
  travel+=distance

for side,label in ((1,'left'),(-1,'right')):
 root=bone(label+'_root',shoulder_clearance((side*5.8,23.5,3.9)),'wing_mount')
 for i,(upper,lower) in enumerate(zip(ribs,rays)):
  split_upper=max(2,len(upper)//2);split_lower=max(2,len(lower)//2)
  rib=bone(f'{label}_rib_{i+1}',xyz(upper[0],side),root)
  arch=bone(f'{label}_arch_{i+1}',xyz(upper[split_upper],side),rib)
  finger=bone(f'{label}_finger_{i+1}',xyz(lower[0],side),arch)
  tip=bone(f'{label}_tip_{i+1}',xyz(lower[split_lower],side),finger)
  sections=[(rib,upper[:split_upper+1],rib_widths[i][:split_upper+1],f'_arch_{i+1}_inner'),
            (arch,upper[split_upper:],rib_widths[i][split_upper:],f'_arch_{i+1}_outer'),
            (finger,lower[:split_lower+1],ray_widths[i][:split_lower+1],f'_talon_{i+1}_upper'),
            (tip,lower[split_lower:],ray_widths[i][split_lower:],f'_talon_{i+1}_lower')]
  for owner,path,widths,tag in sections:
   skin_paths[owner]=[xyz(p,side) for p in path]
   armored_beam(label+tag,path,side,owner,widths[0],widths[-1],widths)
 # Continuous upper body is rendered by BlacklightPlayerModel with the player's
 # original skin weights. Only a few broad scapular lobes bridge into the ribs.
 # No trailing armpit strands, fragmented scale clusters or floating mantle.
 end=np.array(groups[root]['pivot']);base=np.array([side*3.1,20.9,1.95]);root_paths[root]=[base.tolist(),end.tolist()]
 for k,(c,size,tilt) in enumerate([
  ((3.0,19.7,2.1),(2.2,2.1,1.1),-10),
  ((4.5,20.2,2.8),(2.0,1.2,1.5),-25),
  ((6.4,22.4,3.5),(2.4,2.6,1.7),-35),
  ((6.3,23.1,4.0),(2.1,2.1,1.65),-45)]):
  e=cube(label+'_scapular_lobe',(side*c[0],c[1],c[2]),size,side*tilt,1 if k%2 else 0,'wing_mount')
  e['_root_skin']=root
 # The joint is filled by wide tissue, not a starburst of thin connecting rods.
 for k,path in enumerate(ribs):
  start=[side*(5.6+k*.2),21.0+k*.3,2.6+k*.25]
  e=rod(label+'_scapular_bridge',start,xyz(path[0],side),1.45,1.3,0,'wing_mount');e['_root_skin']=root
 # Two broad red seams embedded in the scapular lobes, ending above the axilla.
 for k,path in enumerate([[(2.3,19.2,2.8),(3.4,20.0,3.2),(5.0,20.5,4.0),(6.0,23.5,4.9)],
                           [(3.2,18.9,2.7),(4.1,19.7,3.4),(5.5,21.5,4.2)]]):
  for a,b in zip(path,path[1:]):
   e=rod(label+'_scapular_seam',[side*a[0],a[1],a[2]],[side*b[0],b[1],b[2]],.12,.09,6,'wing_mount');e['_root_skin']=root
 if STAGE!='blockout':
  for i,path in enumerate(ribs):
   for node,p2 in enumerate(path[1:]):
    if node%2:continue
    x,y=p2;z=depth(x,y);parent=label+f'_rib_{i+1}'
    for face in (-1,1):
     plate=cube(label+'_joint_crest',shoulder_clearance((side*x,y,z+face*.95)),(1.2,1.9,.6),side*(-25+i*9),1 if node%3 else 2,parent)
     plate['rotation'][1]=side*face*(9+i*3)
 if STAGE!='blockout':
  connective=[[(506,373),(473,345),(452,337),(428,309),(404,300),(387,279),(362,270),(345,245),(321,239)],
    [(505,373),(480,366),(465,345),(437,338),(416,318),(395,312),(375,288),(348,284)],
    [(490,343),(460,333),(446,308),(420,298),(402,270),(371,245),(357,220),(337,209),(320,175)],
    [(479,352),(470,331),(443,320),(436,293),(408,284),(391,261),(373,252)]]
  for k,points in enumerate(connective):
   path=landmark(points);owner=label+f'_rib_{k+1}'
   for i,(a,b) in enumerate(zip(path,path[1:])):
    p=np.array(xyz(a,side));q=np.array(xyz(b,side));p[2]-=.65+(i%3)*.13;q[2]-=.65+((i+1)%3)*.13
    rod(label+'_braid_tissue',p,q,.36 if i%3 else .55,.35,8 if k%2 else 0,owner)
    rod(label+'_braid_vessel',p+np.array([0,0,-.20]),q+np.array([0,0,-.20]),.09,.065,6,owner)
 # Separate torn membrane panels leave the upper nested arches open.
 polys=[landmark(p) for p in [
 [(150,248),(240,240),(214,285),(197,346),(175,409),(157,477),(143,511),(138,477),(125,491),(120,448),(100,458),(102,402),(97,354),(126,292)],
 [(214,285),(303,300),(303,373),(283,455),(268,525),(258,609),(250,650),(233,602),(224,631),(225,563),(205,585),(205,529),(176,548),(175,470),(197,346)],
 [(303,300),(329,327),(339,406),(350,490),(370,586),(395,699),(378,667),(369,617),(350,647),(341,596),(319,612),(311,550),(293,556),(283,455),(303,373)]]]
 for i,poly in enumerate(polys):
  holes=[(*landmark([(173,356)])[0],.42,1.2),(*landmark([(242,459)])[0],.5,1.65),(*landmark([(319,462)])[0],.35,1.0)]
  membrane(label+f'_web_{i}',poly,holes,side,label+f'_finger_{i+1}')
  if STAGE!='blockout':
   anchors=[[(154,294),(177,320),(185,370),(164,428)],[(235,337),(248,386),(233,437),(245,498),(228,550)],[(315,354),(322,402),(311,455),(330,500),(322,550)]]
   path=landmark(anchors[i])
   for a,b in zip(path,path[1:]):
    p=np.array(xyz(a,side));q=np.array(xyz(b,side))
    for face in (-1,1):
     off=np.array([0,0,.12+face*.17]);e=rod(label+f'_web_fibre_{i}',p+off,q+off,.16,.10,8,label+f'_finger_{i+1}');e['_skin']=True

 if STAGE!='blockout':
  upper_webs=[[(248,155),(279,165),(309,192),(269,197),(250,220),(243,196),(231,207),(233,180)],
    [(303,220),(335,223),(353,247),(318,247),(303,272),(300,250),(286,258)],
    [(355,230),(391,257),(420,295),(402,289),(394,276),(384,284),(372,258)]]
  for i,p in enumerate(upper_webs):
   membrane(label+f'_web_remnant_{i}',landmark(p),[],side,label+f'_arch_{i+1}')
 if STAGE!='blockout':
  # Sparse broken tendrils are longer than the plate edge chips.
  branches=[landmark(p) for p in [
   [(288,106),(233,107),(205,122),(184,163),(144,188),(123,215),(119,237)],
   [(322,144),(362,163),(377,194),(405,200),(425,231)],
   [(423,284),(462,304),(488,291),(513,262),(504,234),(476,223),(464,195),(440,190),(435,165)],
   [(467,347),(444,352),(414,367),(392,399),(401,438),(388,470),(399,496)],
   [(505,373),(492,357),(463,383),(455,431),(475,469),(462,516),(483,563)]]]
  for k,path in enumerate(branches[:3]):
   if k==2:path=[(x+.4*max(0,1-abs(y-30)/12),y) for x,y in path]
   if k>=3:path=[(x+max(0,23-y)*.65,y) for x,y in path]
   armored_beam(label+f'_tendril_{k}',path,side,label+f'_rib_{min(k,3)+1}' if k<3 else label+'_root',.55,.08)
# Reassign secondary rigid details to their nearest corresponding branch segment.
def path_distance(point,path):
 p=np.array(point);best=1e9
 for a,b in zip(path,path[1:]):
  a=np.array(a);v=np.array(b)-a;t=np.clip(np.dot(p-a,v)/max(1e-9,np.dot(v,v)),0,1)
  best=min(best,float(np.linalg.norm(p-(a+v*t))))
 return best
for e in elements:
 if '_root_skin' in e or '_shoulder_skin' in e or e.get('_skin'):continue
 if '_tendril_' in e['name'] or '_braid_' in e['name']:
  # A connected tissue strand cannot choose a different rigid bone for each
  # plate: neighbouring pieces tear apart during a broad power stroke.
  e['_skin']=True
  continue
 if any(t in e['name'] for t in ('_joint_crest','_braid_','_tendril_')):
  side=e['_parent'].split('_')[0]
  candidates=[n for n in skin_paths if n.startswith(side)]
  e['_parent']=min(candidates,key=lambda n:path_distance(e['origin'],skin_paths[n]))

def outliner(name):
 return {'uuid':groups[name]['uuid'],'isOpen':False,'children':[outliner(b['name']) for b in bones if b['parent']==name]+[e['uuid'] for e in elements if e['_parent']==name]}

bbgroups=[{'name':b['name'],'uuid':b['uuid'],'origin':b['pivot'],'rotation':[0,0,0],'export':True,'isOpen':False,'visibility':True,'color':0} for b in bones]
source='data:image/png;base64,'+base64.b64encode((OUT/'sovereign_wings.png').read_bytes()).decode()
animations=[]  # Static reconstruction only; source folding is not yet authored.

assert len(elements)<=3400, f'Geometry budget exceeded: {len(elements)}'

bb={'meta':{'format_version':'5.0','model_format':'bedrock','box_uv':False},'name':'sovereign_wings','model_identifier':'blacklight.sovereign_wings','visible_box':[6,5,1],'variable_placeholders':'','resolution':{'width':256,'height':256},'elements':[{k:v for k,v in e.items() if not k.startswith('_')} for e in elements],'groups':bbgroups,'outliner':[outliner('wing_mount')],'textures':[{'path':'sovereign_wings.png','name':'sovereign_wings.png','folder':'','namespace':'blacklight','id':'0','uuid':uid('texture'),'source':source,'mode':'bitmap','particle':False,'visible':True,'render_mode':'default','render_sides':'auto','uv_width':256,'uv_height':256}], 'animations':animations}
stem='blockout' if STAGE=='blockout' else 'sovereign_wings'
(OUT/(stem+'.bbmodel')).write_text(json.dumps(bb,separators=(',',':')),encoding='utf8')
(OUT/(stem+'.asset.json')).write_text(json.dumps({'bones':bones,'elements':elements,'palette':PALETTE,'stage':STAGE,'skin_paths':skin_paths,'root_paths':root_paths},separators=(',',':')),encoding='utf8')
counts={'stage':STAGE,'cuboids':len(elements),'quads':len(elements)*6,'triangles':len(elements)*12,'bones':len(bones),'texture_size':[256,256],'texels_per_unit':2,'material_tiles':len(PALETTE),'categories':dict(counters),'runtime_measured':False}
(OUT/(stem+'.metrics.json')).write_text(json.dumps(counts,indent=2),encoding='utf8')
assert len(elements)<=3400,counts
assert len({e['uuid'] for e in elements})==len(elements)
assert len({b['name'] for b in bones})==len(bones)
print(json.dumps({k:v for k,v in counts.items() if k!='categories'}))
