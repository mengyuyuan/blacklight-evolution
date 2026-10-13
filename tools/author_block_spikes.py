"""Bake the existing authored claw cuboids into the Devastator GPU mesh format.

Both attacks share geo/ground_spike.geo.json and claw_biomass.png. No original
game meshes, textures or private reference assets are read. Height, spire cross
section and eruption footprint are independent recipe dimensions. The result
retains rotated cuboids, hard face normals and the original Minecraft box UV net.
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


def assemble(geometry, config, core_gain=1.0, footprint_gain=1.0, vertical_scale=1.0):
    mesh=Mesh(); bones={b['name']:b for b in geometry['bones']}
    eruption_points=[]
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
                    # Widen around this spire's own anchor, without spreading the
                    # cluster anchors or stretching its vertical construction.
                    # Scaling the cuboid before rotation keeps all six faces flat.
                    gain=footprint_gain if bone['name']=='eruption' else core_gain
                    pivot=bone.get('pivot',[0,0,0])
                    widen=lambda p: (pivot[0]+(p[0]-pivot[0])*gain,
                                      p[1],pivot[2]+(p[2]-pivot[2])*gain)
                    point=rotate(widen(point),widen(cube.get('pivot',pivot)),cube.get('rotation',[0,0,0]))
                    current=bone
                    while current:
                        point=rotate(point,current.get('pivot',[0,0,0]),current.get('rotation',[0,0,0]))
                        current=bones.get(current.get('parent'))
                    point=tuple(a/16*vertical_scale for a in point)
                    transformed.append(point)
                    if bone['name']=='eruption': eruption_points.append(point)
                a,b,w,h=rect
                uv=[(a/tw,(b+h)/th),(a/tw,b/th),((a+w)/tw,b/th),((a+w)/tw,(b+h)/th)]
                ids=[mesh.vertex(point,tex,(255,255,255)) for point,tex in zip(transformed,uv)]
                mesh.triangle(*ids[:3]);mesh.triangle(ids[0],ids[2],ids[3])
            mesh.parts.append(dict(name=f"{bone['name']}_cube_{ci}",firstTriangle=first,triangleCount=12))
    return mesh,eruption_points


def build(geometry, config, ident):
    reference,eruption=assemble(geometry,config)
    top=max(p[1] for p in reference.positions)
    root_span=max(max(p[a] for p in eruption)-min(p[a] for p in eruption) for a in (0,2))
    main=next(b for b in geometry['bones'] if b['name']=='spire_0')
    source_core_width=main['cubes'][0]['size'][0]/16
    scale=config['height']/top
    # Widened rotated boxes slightly affect the top AABB. Solve the vertical
    # scale while keeping the requested local cross section/footprint in blocks.
    # This is offline authoring; no runtime growth/retraction clock is changed.
    for _ in range(16):
        core_gain=config.get('coreWidth',source_core_width*scale)/(source_core_width*scale)
        footprint_gain=config.get('rootFootprint',root_span*scale)/(root_span*scale)
        mesh,eruption=assemble(geometry,config,core_gain,footprint_gain,scale)
        actual_top=max(p[1] for p in mesh.positions)
        if abs(actual_top-config['height'])<1e-10: break
        scale*=config['height']/actual_top
    else:
        raise ValueError(f'{ident}: independent dimension solve did not converge')
    result=mesh.finish(ident)
    result['textureName']='blacklight:textures/entity/claw_biomass.png'
    result['sharedGeometry']='assets/blacklight/geo/ground_spike.geo.json'
    result['authoredProportions']=dict(height=config['height'],coreWidth=config.get('coreWidth'),
                                      rootFootprint=config.get('rootFootprint'),standingPlayerHeight=1.8,
                                      widthMode='independent_local_cross_section_and_eruption_footprint')
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
            assert len(candidate['indices'])//3==(576 if config['cluster'] else 144)
            assert abs(candidate['bounds']['max'][1]-config['height'])<1e-8
            result['meshes'][candidate['id']]=candidate
            write_obj(args.obj_output/(candidate['id']+'.obj'),candidate,recipe['provenance'])
            print(candidate['id'],'triangles',len(candidate['indices'])//3,
                  'height',round(candidate['bounds']['max'][1],3),
                  'main core width',config.get('coreWidth'),'root footprint',config.get('rootFootprint'))
    args.output.write_text(json.dumps(result,separators=(',',':'),allow_nan=False),encoding='utf-8')
    print('WROTE',args.output,args.output.stat().st_size,'bytes')
