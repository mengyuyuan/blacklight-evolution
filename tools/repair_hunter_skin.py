"""Reweight the existing welded hunter without remeshing it (Blender Python)."""
from pathlib import Path
import json,math,sys
from mathutils import Vector,Euler
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'tools'))
from hunter_skin_weights import weight_vertex
A=R/'src/main/resources/assets/blacklight'
mesh_path=A/'models/hunter_connected.json';mesh=json.loads(mesh_path.read_text())
data=json.loads((R/'art/hunter/block-model-source.json').read_text())
geo=json.loads((A/'geo/blacklight_hunter_authored.geo.json').read_text())['minecraft:geometry'][0]['bones']
parents={b['name']:b.get('parent') for b in geo}
keep=['Pelvis','Tapered waist','Broad ribcage','Raised shoulder back','Neck bridge','Low bald head','Lower abdominal block','Open lower jaw','Defined pectoral','Collar muscle','Scapula','Cheek plane','Jaw hinge']
tokens=[' shoulder',' upper arm',' elbow',' long forearm',' wrist',' broad palm',' finger ',' thumb',' thigh',' forward knee',' rearward shin',' hock',' grounded foot']
volumes=[]
for box in data['boxes']:
 name=box['name']
 if name not in keep and not any(t in name for t in tokens):continue
 if any(t in name for t in ['cap','tendon','claw']):continue
 volumes.append((box['center'],[v*.62 for v in box['size']],box['bone'],Euler([math.radians(v) for v in box['rotation']]).to_matrix().transposed()))
changed=0
for v in mesh['vertices']:
 # Rigid added claws/teeth preserve their exact original bone assignment.
 if v['w'][0]==v['w'][1]:continue
 v['w']=weight_vertex([q*16 for q in v['p']],volumes,parents);changed+=1
mesh['skinning']='dual_quaternion'
mesh['authoring']='Continuous anatomical surface; adjacent-joint weights, rigid masses and dual-quaternion deformation.'
mesh_path.write_text(json.dumps(mesh,separators=(',',':')),encoding='utf8')
print('REWEIGHTED',changed,'vertices; geometry/atlas unchanged')
