"""Deterministic cuboid asset authoring; no reference image is mapped onto geometry."""
from pathlib import Path
import math, json, uuid, random, base64, sys
from collections import Counter
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parent
BAT_RIG='--bat-rig' in sys.argv
OUT=ROOT/('model-v5' if BAT_RIG else 'model-v2')
OUT.mkdir(exist_ok=True)
STAGE=sys.argv[1] if len(sys.argv)>1 else 'detail'
RNG=random.Random(161016)
NS=uuid.UUID('8c0056eb-7eb0-486f-af74-a6543b07b654')
uid=lambda n:str(uuid.uuid5(NS,n))
elements=[]; bones=[]; groups={}; counters=Counter()
PALETTE=[(23,23,27),(34,33,38),(43,42,47),(56,54,59),(75,13,22),(104,19,27),(193,23,29),(255,59,42),(24,13,19),(125,25,32),(40,35,38),(88,84,85)]
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

def rod(tag,p,q,width,depth,mat,parent):
 p=np.array(p,dtype=float);q=np.array(q,dtype=float)
 delta=q-p
 # Paths curve through Z by stacked segment centers; every individual piece
 # remains a rigid, exportable rotated cuboid, with no non-cuboid deformations.
 length=float(np.linalg.norm(delta[:2]))
 if length<.01:return
 angle=-math.degrees(math.atan2(delta[0],delta[1]))
 return cube(tag,(p+q)/2,(width,length+.08,depth),angle,mat,parent)

def depth(x,y):return 3.8+2.2*math.sin(abs(x)/32*math.pi)+.025*(y-24)
def xyz(p,side):return (p[0]*side,p[1],depth(p[0],p[1]))

def chain(tag,path,side,parent,startw,endw,step=1.6,plates=True):
 lengths=[math.dist(a,b) for a,b in zip(path,path[1:])];total=sum(lengths);walk=0
 for pi,(a,b) in enumerate(zip(path,path[1:])):
  dist=lengths[pi];n=max(1,math.ceil(dist/step))
  for i in range(n):
   t0=i/n;t1=(i+1)/n;tm=(t0+t1)/2
   p=[a[j]+(b[j]-a[j])*t0 for j in range(2)];q=[a[j]+(b[j]-a[j])*t1 for j in range(2)]
   progress=(walk+tm*dist)/total
   w=endw+(startw-endw)*((1-progress)**.58)
   p3=np.array(xyz(p,side));q3=np.array(xyz(q,side))
   dep=max(.30,w*.82)
   rod(tag+'_core',p3,q3,w,dep,0,parent)
   if STAGE!='blockout':
    # A channel is framed by separate offset plates; it never lies coplanar
    # with an armor face, so emission does not z-fight with the shell.
    ang=-math.degrees(math.atan2(q3[0]-p3[0],q3[1]-p3[1]));vein_axis=np.array([math.cos(math.radians(ang)),math.sin(math.radians(ang)),0])
    for face in (-1,1):
     offset=np.array([0,0,face*(dep/2+.06)])
     p_shift=vein_axis*w*.07*((i%3)-1);q_shift=vein_axis*w*.07*(((i+1)%3)-1)
     rod(tag+'_vein',p3+offset+p_shift,q3+offset+q_shift,max(.07,w*.13),.16,6,parent)
    if plates and w>.36:
     c=(p3+q3)/2;ang=-math.degrees(math.atan2(q3[0]-p3[0],q3[1]-p3[1]));axis=np.array([math.cos(math.radians(ang)),math.sin(math.radians(ang)),0])
     plen=max(.35,dist/n*(1.06 if i%3 else 1.24))
     for face in (-1,1):
      offset=axis*w*.50*(1 if (i+pi)//3%2 else -1)
      pc=c+offset+np.array([0,0,face*(dep/2+.30)])
      plate=cube(tag+'_plate',pc,(w*.75,plen*1.16,.8),ang+(side*8 if i%3==0 else -side*3),RNG.choice([1,1,2,2,3]),parent)
      plate['rotation'][0]=face*(7 if i%2 else -5)
      plate['rotation'][1]=side*face*(9 if i%3 else -8)
      other=c-offset*.66+np.array([0,0,face*(dep/2+.25)])
      cube(tag+'_counter_plate',other,(w*.38,plen*.86,.58),ang-side*4,RNG.choice([1,2,3]),parent)
     if (i+pi)%3==0 and w>.9:
      edge=c+axis*w*.55+np.array([0,0,-dep*.08])
      cube(tag+'_edge',edge,(w*.34,plen*.86,dep+.55),ang,2,parent)
     if (i+pi)%3==0 and w>1:
      for face in (-1,1):
       cface=c+np.array([0,0,face*(dep/2+.73)])
       rod(tag+'_plate_fissure',cface,cface+axis*w*.50+np.array([0,.35,0]),.13,.09,6,parent)
   walk+=0
  walk+=dist

def inside(x,y,poly):
 yes=False
 for a,b in zip(poly,poly[1:]+poly[:1]):
  if (a[1]>y)!=(b[1]>y) and x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0]:yes=not yes
 return yes

def membrane(name,poly,holes,side,parent):
 step=.55
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
  z=depth(cx,cy)+.12
  cube(name,(cx*side,cy,z),(w*step,h*step,.22),0,4 if (x+y)%4 else 5,parent)

# Legacy saddle is retained only for the pre-bat source.
if not BAT_RIG:
 # Small organic attachment/root saddle: only upper-back width, no torso.
 for x in (-2.4,-1.2,0,1.2,2.4):
  cube('root_saddle',(x,24,3.8),(1.35,2.4,1.5),-x*7,1)
  if STAGE!='blockout':
   cube('root_ridge',(x,25.2,3.65),(1.1,.8,1.9),-x*7,2)
   cube('root_channel',(x,24,2.99),(.18,1.9,.18),-x*7,6)

paths=[[(16.6,40.7),(20,34.2),(23.4,26.4),(26.4,17),(28.6,8),(30,3)],
       [(16.5,34),(18.7,25.5),(21.6,15.3),(24,5.8),(26,-3.4)],
       [(14.1,29.8),(15,21),(16.4,10.8),(17.4,.8),(18,-9)],
       [(11.4,27.5),(10.2,19.2),(8.9,11.8),(9.6,4.4),(11,-4.4)]]
if BAT_RIG:
 paths[1]=[(16.6,40.7),(15.8,36)]+paths[1]
 paths[2]=[(16.6,40.7),(13.6,35)]+paths[2]
 paths[3]=[(16.6,40.7),(11,35)]+paths[3]
for side,label in ((1,'left'),(-1,'right')):
 root=bone(label+'_root',(side*2.8,24,3.8),'wing_mount')
 elbow=bone(label+'_elbow',xyz((8.1,29.1),side) if BAT_RIG else (side*10.5,30,4.6),root)
 wrist=bone(label+'_wrist',xyz((16.6,40.7),side),elbow) if BAT_RIG else elbow
 if BAT_RIG:
  chain(label+'_shoulder',[(2.6,24.3),(5,25.6),(8.1,29.1)],side,root,3.2,3.0,2.15)
  chain(label+'_forearm',[(8.1,29.1),(11.2,34.9),(15.8,41),(17,41.3)],side,elbow,3.0,3.3,2.15)
 else:chain(label+'_shoulder',[(2.6,24.3),(5,25.6),(8.1,29.1),(11.2,34.9),(15.8,41),(17,41.3)],side,root,3.2,3.3,2.15)
 # A secondary layered support makes the root feel like tissue attached to
 # each wing rather than a straight strap connecting two unrelated props.
 if not BAT_RIG:
  chain(label+'_underbrace',[(3,23),(7.5,26.5),(12.5,31),(16.8,35.2)],side,root,2.15,1.8,2.1)
  chain(label+'_middlebrace',[(3.1,24),(6.9,25.5),(10.5,28.4),(14.1,29.8)],side,root,1.8,2.5,2.1)
  chain(label+'_innerbrace',[(3.5,23.6),(6.7,24.6),(9.3,26.5),(11.4,27.5)],side,root,1.65,2.2,2.1)
 for i,path in enumerate(paths):
  f=bone(f'{label}_finger_{i+1}',xyz(path[0],side),wrist)
  chain(f'{label}_finger{i+1}',path,side,f,[3.4,3.25,3.3,2.7][i],.10,2.05)
 if STAGE!='blockout':
  membrane(label+'_web_outer',[(16.6,39),(20,33),(23.1,26),(26,17),(28.2,9),(26.1,11),(24.8,9.2),(23.5,15),(22.1,16.5),(20,25),(17,33)],[(20.7,30.3,1.05,2.0),(24.5,20.6,.7,1.8),(25.6,16.2,.48,.75)],side,elbow)
  membrane(label+'_web_middle',[(16.6,33.5),(18.8,25.5),(21.7,15),(24,5.8),(25.3,-.4),(22.8,3),(21.2,7),(19.8,4.8),(17.5,-1.5),(16.7,9),(15,21),(14.1,29.8)],[(17.9,24.1,.8,2.1),(19.8,15.9,.65,1.4),(20.1,10,.6,1.1)],side,elbow)
  membrane(label+'_web_inner',[(14.1,29.8),(15,21),(16.4,11),(17.4,1),(17.8,-5.3),(15.4,.4),(14.1,4.5),(12.7,1.6),(10.6,-1.9),(9.7,4.8),(9,12),(10.2,19.2),(11.4,27.5)],[(12.4,22,1.0,2.1),(12.8,13.6,1.0,2.8),(14.6,7,.55,1.05)],side,elbow)
  # Thin rectangular branched tendrils, deliberately elbowed rather than tubes.
  tendrils=[[(15.5,40),(12.2,39),(10.1,37),(8.5,36),(7.6,33.8),(4.6,32.5)],
   [(12,34),(9.8,35.7),(8.8,38),(7,38.7)],
   [(7.9,28.7),(6.3,32),(6.7,35.5),(5.8,37.4)],
   [(4.3,24.9),(5.2,21.3),(4.3,19.6),(5,17.9)],
   [(6.8,26.2),(8,23.1),(6.8,21),(7.3,19.5)],
   [(9.1,29),(11,25),(12.7,23),(12.2,20.7)],
   [(17.5,39),(20.8,38),(22.5,35.5),(25.6,34),(27.1,32.2)]]
  for i,path in enumerate(tendrils):chain(f'{label}_tendril{i}',path,side,elbow if BAT_RIG and path[0][0]>8.1 else root,.65,.14,2.2,False)
  # Shoulder shingles are broad stepped plates, not spikes glued to a flat web.
  for i in range(6):
   x=4.4+i*.9;y=25.2+i*1.12;z=depth(x,y)
   for f in (-1,1):
    cube(label+'_scapular_plate',(x*side,y,z+f*.95),(1.8,2.8,.85),side*(-32+i*2),2 if i%2 else 1,elbow if BAT_RIG and x>8.1 else root)
  # Compact layered shoulder nodes, with overlapping plates and root tendons.
  for node,(x,y,w) in enumerate([(3.8,24.2,2.7),(6.1,26,2.9),(9,29.4,3.1),(12,34.2,3.2),(16.6,40.5,3.0)]):
   owner=(wrist if x>=16 else elbow if x>=8.1 else root) if BAT_RIG else root
   z=depth(x,y)
   for face in (-1,1):
    for k in (-1,0,1):
     plate=cube(label+'_joint_carapace',(side*(x+k*.72),y+.4-abs(k)*.65,z+face*(1.45+(.28 if k==0 else 0))),(w*.54,2.2+abs(k)*.55,.85),side*(-26+k*13),2 if k==0 else 1,owner)
     plate['rotation'][1]=side*k*12
    for k in (-1,1):
     start=np.array((side*(x+k*.4),y+.7,z+face*2.25))
     end=start+np.array((side*k*.7,-1.2,0))
     rod(label+'_joint_crack',start,end,.16,.1,6,owner)
  # Angular branches across the membranes, kept below the armor face depth.
  veinpaths=[[(16.6,31),(18,28),(18.2,26),(19.3,24.4)],[(18.2,26),(17.1,24.8),(16.6,22.5)],[(14.2,25),(13.2,23),(13.6,20.5),(12.5,18.5)],[(13.6,20.5),(15,19),(15.5,17)],[(22.2,22),(23.2,20),(23.6,17.4)]]
  for vi,path in enumerate(veinpaths):
   for a,b in zip(path,path[1:]):
    p=np.array(xyz(a,side));q=np.array(xyz(b,side))
    for face in (-1,1):
     off=np.array([0,0,.12+face*.18])
     rod(label+'_web_vessel',p+off,q+off,.14,.08,6,elbow)

# Place the hinge on the upper back instead of at neck height. Leave room for
# the captured head leaning backwards relative to the chest during forward flight.
skin_paths={f'{label}_finger_{i+1}':[xyz(p,side) for p in path] for side,label in ((1,'left'),(-1,'right')) for i,path in enumerate(paths)} if BAT_RIG else {}
if BAT_RIG:
 def attachment(point,owner):
  side=1 if owner.startswith('left') else -1 if owner.startswith('right') else 0
  return [point[0]+side*6.0,point[1]-2.8,point[2]+1.6]
 for b in bones:b['pivot']=attachment(b['pivot'],b['name'])
 groups['wing_mount']['pivot']=[0,20,2]
 for e in elements:
  owner=e['_parent']
  if '_shoulder_plate_' in e['name'] and abs(e['origin'][0])<5:
   for key in ('from','to','origin'):e[key][0]+=.6*(1 if owner.startswith('left') else -1)
  for key in ('from','to','origin'):e[key]=attachment(e[key],owner)
 for owner,path in skin_paths.items():skin_paths[owner]=[attachment(p,owner) for p in path]

# Flesh grows from two separate shoulder-blade patches. Vertices near the skin
# stay with the chest; the outer flesh follows the root articulation.
root_paths={}
if BAT_RIG:
 def root_segment(tag,p,q,width,thickness,mat,root):
  p=np.array(p,dtype=float);q=np.array(q,dtype=float);v=q-p
  length=float(np.linalg.norm(v));rx=math.degrees(math.atan2(v[2],v[1]))
  rz=-math.degrees(math.atan2(v[0],math.hypot(v[1],v[2])))
  e=cube(tag,(p+q)/2,(width,length+.16,thickness),rz,mat,'wing_mount')
  e['rotation'][0]=rx;e['_root_skin']=root
  return e
 for side,label in ((1,'left'),(-1,'right')):
  root=label+'_root';end=np.array(groups[root]['pivot'])
  base=np.array([side*2.8,17.0,1.95]);root_paths[root]=[base.tolist(),end.tolist()]
  # Unequal stepped strips taper into the skin instead of a rectangular plate.
  for j,(x,y,h,w) in enumerate([(1.3,17.9,2.8,.65),(2.0,18.3,3.0,.9),(2.8,18.6,2.7,.95),(3.5,19.0,1.9,.65)]):
   patch=cube(label+'_skin_flesh',(side*x,y-1.4,2.0),(w,h,.30),side*(j-1)*7,8 if j%2 else 4)
   patch['_root_skin']=root
  routes=[[(side*1.0,18.0,1.95),(side*4.4,19.3,2.5),end],
          [(side*2.2,19.7,1.95),(side*4.7,20.0,3.0),end],
          [(side*3.4,18.3,1.95),(side*4.3,19.7,3.5),end],
          [(side*3.8,19.6,1.95),(side*5.3,20.4,3.6),end]]
  routes=[[np.array(p)-np.array([0,1.4,0]) for p in route[:-1]]+[route[-1]] for route in routes]
  for i,route in enumerate(routes):
   for j,(start,stop) in enumerate(zip(route,route[1:])):
    root_segment(label+'_root_flesh',start,stop,.62+j*.55,.55+j*.30,8 if i%2 else 4,root)
    if STAGE!='blockout':
     lift=np.array([0,0,.28+j*.18]);root_segment(label+'_root_capillary',np.array(start)+lift,np.array(stop)+lift,.10,.09,6,root)
  # Overlapping angular tissue ribs grow thicker toward the spar. These are
  # skinned with the same root bridge, not separate hard mounting hardware.
  if STAGE!='blockout':
   for i in range(5):
    t=(i+.5)/5;center=base*(1-t)+end*t+np.array([0,0,.18])
    e=cube(label+'_root_rib',center,(.8+t*.9,.65+t*.35,.24+t*.35),-side*(25+i*7),0 if i%2 else 1)
    e['_root_skin']=root

def outliner(name):
 return {'uuid':groups[name]['uuid'],'isOpen':False,'children':[outliner(b['name']) for b in bones if b['parent']==name]+[e['uuid'] for e in elements if e['_parent']==name]}

bbgroups=[{'name':b['name'],'uuid':b['uuid'],'origin':b['pivot'],'rotation':[0,0,0],'export':True,'isOpen':False,'visibility':True,'color':0} for b in bones]
source='data:image/png;base64,'+base64.b64encode((OUT/'sovereign_wings.png').read_bytes()).decode()
animations=[]
for clip,reverse in [('fold',False),('unfold',True)]:
 animators={}
 for label,sign in [('left',-1),('right',1)]:
  b=groups[label+'_root'];keyframes=[]
  for t,v in [(0,0),(.35,20),(.85,58),(1.2,70)]:
   if reverse:t=1.2-t
   keyframes.append({'channel':'rotation','data_points':[{'x':'0','y':str(v*sign),'z':'0'}],'uuid':uid(f'{clip}{label}{t}'),'time':t,'interpolation':'linear'})
  animators[b['uuid']]={'name':b['name'],'type':'bone','keyframes':sorted(keyframes,key=lambda k:k['time'])}
 animations.append({'uuid':uid(clip),'name':'animation.sovereign_wings.'+clip,'loop':'once','override':False,'length':1.2,'snapping':20,'animators':animators})

bb={'meta':{'format_version':'5.0','model_format':'bedrock','box_uv':False},'name':'sovereign_wings','model_identifier':'blacklight.sovereign_wings','visible_box':[6,5,1],'variable_placeholders':'','resolution':{'width':256,'height':256},'elements':[{k:v for k,v in e.items() if not k.startswith('_')} for e in elements],'groups':bbgroups,'outliner':[outliner('wing_mount')],'textures':[{'path':'sovereign_wings.png','name':'sovereign_wings.png','folder':'','namespace':'blacklight','id':'0','uuid':uid('texture'),'source':source,'mode':'bitmap','particle':False,'visible':True,'render_mode':'default','render_sides':'auto','uv_width':256,'uv_height':256}], 'animations':animations}
stem='blockout' if STAGE=='blockout' else 'sovereign_wings'
(OUT/(stem+'.bbmodel')).write_text(json.dumps(bb,separators=(',',':')),encoding='utf8')
(OUT/(stem+'.asset.json')).write_text(json.dumps({'bones':bones,'elements':elements,'palette':PALETTE,'stage':STAGE,'skin_paths':skin_paths,'root_paths':root_paths},separators=(',',':')),encoding='utf8')
counts={'stage':STAGE,'cuboids':len(elements),'quads':len(elements)*6,'triangles':len(elements)*12,'bones':len(bones),'texture_size':[256,256],'texels_per_unit':2,'material_tiles':len(PALETTE),'categories':dict(counters),'runtime_measured':False}
(OUT/(stem+'.metrics.json')).write_text(json.dumps(counts,indent=2),encoding='utf8')
assert len(elements)<=3000,counts
assert len({e['uuid'] for e in elements})==len(elements)
assert len({b['name'] for b in bones})==len(bones)
print(json.dumps({k:v for k,v in counts.items() if k!='categories'}))
