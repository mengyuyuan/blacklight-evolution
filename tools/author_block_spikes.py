"""Bake the existing authored claw cuboids into the Devastator GPU mesh format.

Both attacks share geo/ground_spike.geo.json and claw_biomass.png. No original
game meshes, textures or private reference assets are read. The result retains
individual rotated boxes, their hard face normals and the Minecraft box UV net.
"""
from __future__ import annotations
import json
import math
from pathlib import Path
from author_devastator_spikes import Mesh, write_obj


def rotate(point, pivot, angles):
    x,y,z=(point[i]-pivot[i] for i in range(3))
    for axis, degrees in enumerate(angles):
        c,s=math.cos(math.radians(degrees)),math.sin(math.radians(degrees))
        if axis==0: y,z=y*c-z*s,y*s+z*c
        elif axis==1: x,z=x*c+z*s,-x*s+z*c
        else: x,y=x*c-y*s,x*s+y*c
    return (x+pivot[0],y+pivot[1],z+pivot[2])


def build(geometry, config, ident):
    mesh=Mesh(); bones={b['name']:b for b in geometry['bones']}
    texture=geometry['description']; tw,th=texture['texture_width'],texture['texture_height']
    for bone in geometry['bones']:
        if not config['cluster'] and bone['name'].startswith('spire_') and bone['name']!='spire_0': continue
        for ci,cube in enumerate(bone.get('cubes',[])):
            x,y,z=cube['origin']; sx,sy,sz=cube['size']; u,v=cube['uv']
            # Outward winding in authored Y-up coordinates, six separate faces.
            faces=[
                ([(x,y,z),(x,y+sy,z),(x+sx,y+sy,z),(x+sx,y,z)],(u+sz,v+sz,sx,sy)),
                ([(x+sx,y,z+sz),(x+sx,y+sy,z+sz),(x,y+sy,z+sz),(x,y,z+sz)],(u+sz+sx+sz,v+sz,sx,sy)),
                ([(x,y,z+sz),(x,y+sy,z+sz),(x,y+sy,z),(x,y,z)],(u,v+sz,sz,sy)),
                ([(x+sx,y,z),(x+sx,y+sy,z),(x+sx,y+sy,z+sz),(x+sx,y,z+sz)],(u+sz+sx,v+sz,sz,sy)),
                ([(x,y+sy,z),(x,y+sy,z+sz),(x+sx,y+sy,z+sz),(x+sx,y+sy,z)],(u+sz,v,sx,sz)),
                ([(x,y,z+sz),(x,y,z),(x+sx,y,z),(x+sx,y,z+sz)],(u+sz+sx,v,sx,sz)),
            ]
            first=len(mesh.indices)//3
            for points,rect in faces:
                transformed=[]
                for point in points:
                    point=rotate(point,cube.get('pivot',bone.get('pivot',[0,0,0])),cube.get('rotation',[0,0,0]))
                    current=bone
                    while current:
                        point=rotate(point,current.get('pivot',[0,0,0]),current.get('rotation',[0,0,0]))
                        current=bones.get(current.get('parent'))
                    transformed.append(tuple(a/16 for a in point))
                a,b,w,h=rect
                uv=[(a/tw,(b+h)/th),(a/tw,b/th),((a+w)/tw,b/th),((a+w)/tw,(b+h)/th)]
                ids=[mesh.vertex(point,tex,(255,255,255)) for point,tex in zip(transformed,uv)]
                mesh.triangle(*ids[:3]);mesh.triangle(ids[0],ids[2],ids[3])
            mesh.parts.append(dict(name=f"{bone['name']}_cube_{ci}",firstTriangle=first,triangleCount=12))
    # Geometry style and proportions stay identical. Only variant dimensions
    # change; the largest graveyard spire is deliberately below the old towers.
    top=max(p[1] for p in mesh.positions)
    scale=config['height']/top
    mesh.positions=[tuple(a*scale for a in p) for p in mesh.positions]
    result=mesh.finish(ident)
    result['textureName']='blacklight:textures/entity/claw_biomass.png'
    result['sharedGeometry']='assets/blacklight/geo/ground_spike.geo.json'
    return result


def generate(args,recipe):
    root=Path(__file__).resolve().parents[1]
    geometry=json.loads((root/recipe['sourceGeometry']).read_text(encoding='utf-8'))['minecraft:geometry'][0]
    result=dict(version=1,coordinateSystem='world_Y_up',worldScale=1,authored=True,provenance=recipe['provenance'],meshes={})
    args.output.parent.mkdir(parents=True,exist_ok=True);args.obj_output.mkdir(parents=True,exist_ok=True)
    for ident,config in recipe['variants'].items():
        full=build(geometry,config,ident)
        # Cuboids already have only 12 triangles each; keep the same silhouette
        # at all distances instead of replacing it with a different object.
        lod=dict(full,id=ident+'_lod',lodOf=ident)
        for candidate in (full,lod):
            assert all(math.isfinite(v) for key in ('positions','normals','uvs') for v in candidate[key])
            assert max(candidate['indices']) < len(candidate['positions'])//3
            result['meshes'][candidate['id']]=candidate
            write_obj(args.obj_output/(candidate['id']+'.obj'),candidate,recipe['provenance'])
            print(candidate['id'],'triangles',len(candidate['indices'])//3,'above-ground height',round(candidate['bounds']['max'][1],3))
    args.output.write_text(json.dumps(result,separators=(',',':'),allow_nan=False),encoding='utf-8')
    print('WROTE',args.output,args.output.stat().st_size,'bytes')
