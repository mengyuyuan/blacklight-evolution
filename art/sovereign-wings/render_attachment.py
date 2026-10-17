"""Close-up attachment review before animation retargeting; no image-texture shortcuts."""
from pathlib import Path
ROOT=Path(__file__).resolve().parent
base=(ROOT/'render_wings.py').read_text(encoding='utf8')
exec(compile(base[:base.index('# Actual rigid-node fold track.')],str(ROOT/'render_wings.py'),'exec'))
exec(compile(base[base.index('def light('):base.index("if '--root-review'")],str(ROOT/'render_wings.py'),'exec'))
ref=(ROOT/'render_reference_wings.py').read_text(encoding='utf8')
exec(compile(ref[ref.index('bodymat='):ref.index('# Geometry-only')],str(ROOT/'render_reference_wings.py'),'exec'))
# Preview the actual replacement skin surface. The lower-body proxy stays gray.
skinmat=bpy.data.materials.new('Sovereign_body_replacement');skinmat.use_nodes=True
nodes=skinmat.node_tree.nodes;links=skinmat.node_tree.links
skintex=nodes.new('ShaderNodeTexImage');skintex.image=bpy.data.images.load(str(ROOT.parents[1]/'src/main/resources/assets/blacklight/textures/sovereign_body.png'));skintex.interpolation='Closest'
links.new(skintex.outputs['Color'],nodes.get('Principled BSDF').inputs['Base Color'])
nodes.get('Principled BSDF').inputs['Roughness'].default_value=.8
for ob in proxy:
 if ob.name in ('QA_torso','QA_left_arm','QA_right_arm'):
  ob.dimensions.z=6;ob.location.z=15
def replacement(name,x,w,u,v):
 # Vanilla 64px skin UVs; the same upper six rows as BlacklightPlayerModel.
 x0=x;x1=x+w;y0=0;y1=6;z0=-2;z1=2
 quads=[([(x0,y0,z1),(x0,y0,z0),(x0,y1,z0),(x0,y1,z1)],(u,v+4,u+4,v+10)),
        ([(x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0)],(u+4,v+4,u+4+w,v+10)),
        ([(x1,y0,z0),(x1,y0,z1),(x1,y1,z1),(x1,y1,z0)],(u+4+w,v+4,u+8+w,v+10)),
        ([(x1,y0,z1),(x0,y0,z1),(x0,y1,z1),(x1,y1,z1)],(u+8+w,v+4,u+8+2*w,v+10)),
        ([(x0,y0,z1),(x1,y0,z1),(x1,y0,z0),(x0,y0,z0)],(u+4,v,u+4+w,v+4))]
 verts=[];faces=[];uvs=[];mats=[]
 for points,(u0,v0,u1,v1) in quads:
  columns=w if name.endswith('chest') else 1
  for col in range(columns):
   a=col/columns;b=(col+1)/columns
   ps=[Vector(points[0]).lerp(Vector(points[1]),a),Vector(points[0]).lerp(Vector(points[1]),b),
       Vector(points[3]).lerp(Vector(points[2]),b),Vector(points[3]).lerp(Vector(points[2]),a)]
   n=len(verts);verts.extend([cv((-p[0],24-p[1],p[2])) for p in ps]);faces.append(tuple(range(n,n+4)))
   uvs.extend([((u0+(u1-u0)*a)/64,1-v0/64),((u0+(u1-u0)*b)/64,1-v0/64),((u0+(u1-u0)*b)/64,1-v1/64),((u0+(u1-u0)*a)/64,1-v1/64)])
   mats.append(1 if name.endswith('chest') and abs(sum(p.x for p in ps)/4)<1.25 else 0)
 mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],faces);mesh.materials.append(skinmat);mesh.materials.append(bodymat)
 ob=bpy.data.objects.new(name,mesh);scene.collection.objects.link(ob)
 for polygon,material in zip(mesh.polygons,mats):polygon.material_index=material
 uv=mesh.uv_layers.new()
 for i,p in enumerate(uvs):uv.data[i].uv=p
replacement('QA_replaced_chest',-4,8,16,16)
replacement('QA_replaced_right_upper_arm',-8,4,40,16)
replacement('QA_replaced_left_upper_arm',4,4,32,48)
scene.cycles.samples=12
view('attachment_back',(0,-70,23),(0,-3,23),25,1000,900)
view('attachment_front',(0,70,23),(0,0,23),25,1000,900)
view('attachment_side',(75,0,23),(0,0,23),25,1000,900)
view('attachment_full',(0,-95,18),(0,-3,18),72,1400,1150)
