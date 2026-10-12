"""Original editable Devastator sculpture: round tissue, short barbs and raised veins.

Authored cross-sections and topology only. No source-game mesh, UV, texture or data
is read. Reference screenshots inform silhouette, not vertex copying.
Run with Python to export; --preview uses the locally installed Blender headlessly.
"""
import json
import math
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/devastators'
RESOURCE = ROOT / 'src/main/resources/assets/blacklight/models/devastators/tendrils.json'
BLACK = (15, 18, 22)
SHELL = (32, 37, 43)
RIDGE = (62, 70, 76)
BONE = (104, 113, 119)
EDGE = (157, 163, 163)
RED = (104, 13, 26)
DARK_RED = (58, 8, 17)


def add(a, b): return tuple(x+y for x, y in zip(a, b))
def sub(a, b): return tuple(x-y for x, y in zip(a, b))
def mul(a, k): return tuple(x*k for x in a)
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def normal(v):
    size = math.sqrt(sum(x*x for x in v))
    return tuple(x/size for x in v) if size > 1e-12 else (0, 1, 0)
def rgb(c): return c[0] << 16 | c[1] << 8 | c[2]


class Sculpt:
    def __init__(self, name, length):
        self.name = name
        self.length = length
        self.faces = []
        self.features = []

    def face(self, a, b, c, color, normals=None):
        n = cross(sub(b, a), sub(c, a))
        if sum(x*x for x in n) < 1e-15: return
        self.faces.append((a, b, c, normals or (normal(n),)*3, color))

    def loft(self, rings, palette, caps=True, smooth=False, feature=None):
        first = len(self.faces)
        sides = len(rings[0])
        for row in range(len(rings)-1):
            for j in range(sides):
                k = (j+1) % sides
                a, b, c, d = rings[row][j], rings[row+1][j], rings[row+1][k], rings[row][k]
                color = palette[(j + (row // 3)) % len(palette)]
                self.face(a, b, c, color); self.face(a, c, d, color)
        if smooth:
            # Area-weighted volume normals keep a round stalk round under axial
            # stretch. Side faces retain the authored lobes and raised topology.
            sums = {}
            key = lambda point: tuple(round(v, 12) for v in point)
            for a, b, c, _, _ in self.faces[first:]:
                face_normal = cross(sub(b, a), sub(c, a))
                for point in (a, b, c): sums[key(point)] = add(sums.get(key(point), (0, 0, 0)), face_normal)
            for i in range(first, len(self.faces)):
                a, b, c, _, color = self.faces[i]
                self.faces[i] = (a, b, c, tuple(normal(sums[key(p)]) for p in (a, b, c)), color)
        if caps:
            for end, flip in ((rings[0], False), (rings[-1], True)):
                center = tuple(sum(p[i] for p in end)/len(end) for i in range(3))
                for j in range(sides):
                    a, b = end[j], end[(j+1) % sides]
                    self.face(center, b if flip else a, a if flip else b, palette[0])
        if feature:
            self.features.append(dict(name=feature, firstTriangle=first, triangleCount=len(self.faces)-first))

    def mesh(self):
        p, n, uv, colors, indices = [], [], [], [], []
        for a, b, c, normals, color in self.faces:
            for point, norm in zip((a, b, c), normals):
                indices.append(len(colors)); colors.append(rgb(color))
                p.extend(round(v, 6) for v in point)
                n.extend(round(v, 6) for v in norm)
                uv.extend((round((math.atan2(point[2], point[0])/math.tau) % 1, 6),
                           round(point[1]/self.length, 6)))
        bounds = {'min': [min(p[i::3]) for i in range(3)], 'max': [max(p[i::3]) for i in range(3)]}
        assert len(indices) // 3 <= 1100, (self.name, len(indices)//3)
        assert abs(bounds['min'][1]) < .0001 and abs(bounds['max'][1]-self.length) < .0001
        return dict(positions=p, normals=n, uvs=uv, colors=colors, indices=indices,
                    bounds=bounds, sourceScale=1, longitudinalAxis=1, triangleCount=len(indices)//3,
                    features=self.features, authored=True)


def center(t, length=1, bend=1):
    return (.028*math.sin(math.pi*t*1.55)*math.sin(math.pi*t)*bend,
            t*length,
            .021*math.sin(math.pi*t*3)*math.sin(math.pi*t)*bend)


def radius(t, base=.102, pointed=False):
    if pointed:
        return base*(1-t)**.78 + .0007
    # The runtime already applies a full-length taper. A second strong model
    # taper produced long blade-like wedges instead of tensile tissue cords.
    swell = sum(.012*math.exp(-((t-mid)/.055)**2) for mid in (.14, .43, .71))
    return base*(1-.12*t) + .004 + swell*(base/.102)


def section_radius(t, theta, base=.102, pointed=False):
    return radius(t, base, pointed) * (1+.075*math.cos(3*theta+t*2)+.04*math.cos(5*theta-t*5))


def body(sculpt, rings=25, sides=12, base=.102, bend=1, pointed=False):
    rows = []
    for i in range(rings):
        t = i/(rings-1); row = []
        for j in range(sides):
            angle = j*math.tau/sides + .13*math.sin(t*7)
            r = section_radius(t, angle, base, pointed)
            row.append(add(center(t, sculpt.length, bend), (r*math.cos(angle), 0, r*.97*math.sin(angle))))
        rows.append(row)
    sculpt.loft(rows, (BLACK, SHELL, BLACK, SHELL), smooth=True, feature='round_lobed_stalk')


def tissue_ridge(sculpt, start, end, angle, base, width, color, bend=1, sides=4, samples=9, pointed=False):
    """Actual raised three-dimensional tissue; no painted vein texture."""
    rows = []
    for i in range(samples):
        t = start + (end-start)*i/(samples-1)
        theta = angle + .42*math.sin(t*8+angle)
        main_radius = section_radius(t, theta, base, pointed)
        radial = (math.cos(theta), 0, .97*math.sin(theta))
        tangent = (-math.sin(theta), 0, math.cos(theta))
        at = add(center(t, sculpt.length, bend), mul(radial, main_radius+.003))
        thickness = width*(1-.30*t) * (.87+.13*math.sin(i*1.9)**2)
        if pointed: thickness *= (1-t)**.65
        rows.append([add(at, add(mul(radial, math.cos(j*math.tau/sides)*thickness),
                                  mul(tangent, math.sin(j*math.tau/sides)*thickness*.8)))
                     for j in range(sides)])
    sculpt.loft(rows, (color, color, DARK_RED if color==RED else SHELL, color), smooth=not pointed,
                feature='raised_red_vein' if color==RED else 'raised_muscle_cord')


def collar(sculpt, t, base, bend=1):
    """Closed transverse tissue rib, short even when the whole stalk is stretched."""
    rows = []
    for dy, raised in ((-.00115, -.001), (0, .014), (.00115, -.001), (0, -.005), (-.00115, -.001)):
        row = []; at = t + dy
        for j in range(6):
            angle = j*math.tau/6 + .11*math.sin(t*9)
            r = section_radius(at, angle, base) + raised*(base/.102)
            row.append(add(center(at, sculpt.length, bend), (r*math.cos(angle), 0, r*.97*math.sin(angle))))
        rows.append(row)
    # The closed rib's inner bevel is deliberately sharp; smoothing across its
    # concave closing surface would invert some corner normals.
    sculpt.loft(rows, (SHELL, BLACK, SHELL, RIDGE), caps=False, feature='short_transverse_tissue_rib')


def barb(sculpt, t, theta, base, size, bend=1, pointed=False):
    """A short, genuinely transverse thorn; no full-length axial fin/talon."""
    first = len(sculpt.faces)
    radial = (math.cos(theta), 0, .97*math.sin(theta))
    side = (-math.sin(theta), 0, math.cos(theta))
    at = add(center(t, sculpt.length, bend), mul(radial, section_radius(t, theta, base, pointed)*.97))
    thickness = .008*(base/.102)**.5
    # A four-sided root and one short radial tip make a closed solid thorn.
    # Normalized Y span <= .0024 limits even a 164-block strand to < .4 blocks.
    root = [add(at, (0, -.0012*sculpt.length, 0)), add(at, mul(side, thickness)),
            add(at, (0, .0012*sculpt.length, 0)), add(at, mul(side, -thickness))]
    tip = add(at, add(mul(radial, size), (0, -.0005*sculpt.length, 0)))
    base_center = add(at, mul(radial, -.003))
    for j in range(4):
        k = (j+1)%4
        sculpt.face(root[j], root[k], tip, BONE if j in (0, 1) else RIDGE)
        sculpt.face(base_center, root[k], root[j], BLACK)
    sculpt.features.append(dict(name='short_transverse_side_barb', firstTriangle=first,
                                triangleCount=len(sculpt.faces)-first))


def tendril_segment():
    sculpt = Sculpt('tendril_segment', 1)
    body(sculpt)
    for angle, start, end in ((.24, .025, .94), (2.20, .08, .90), (4.55, .025, .85)):
        tissue_ridge(sculpt, start, end, angle, .102, .016, RED)
    tissue_ridge(sculpt, .025, .91, 1.16, .102, .020, SHELL, samples=9)
    for t in (.23, .51, .77): collar(sculpt, t, .102)
    for i, t in enumerate((.13, .28, .42, .58, .73, .88)):
        barb(sculpt, t, (i*2.29+.42) % math.tau, .102, .031+.023*(1-t))
    return sculpt


def tendril_spike():
    sculpt = Sculpt('tendril_spike', .3)
    body(sculpt, rings=12, sides=10, base=.039, bend=.38, pointed=True)
    tissue_ridge(sculpt, .03, .84, 1.05, .039, .007, RED, bend=.38, samples=8, pointed=True)
    for i, t in enumerate((.28, .52, .72)):
        barb(sculpt, t, .4+i*2.16, .039, .042*(1-t)+.014, bend=.38, pointed=True)
    return sculpt


def whipfist_segment():
    sculpt = Sculpt('whipfist_segment', 1)
    body(sculpt, rings=22, sides=10, base=.065, bend=.72)
    for angle in (.6, 3.55):
        tissue_ridge(sculpt, .04, .96, angle, .065, .012, RED, bend=.72, samples=9)
    tissue_ridge(sculpt, .025, .91, 5.05, .065, .016, SHELL, bend=.72, samples=9)
    for t in (.26, .54, .81): collar(sculpt, t, .065, bend=.72)
    for i, t in enumerate((.13, .26, .40, .56, .72, .88)):
        barb(sculpt, t, .8+i*2.6, .065, .029*(1-t)+.018, bend=.72)
    return sculpt


def export_obj(sculpt, mesh):
    path = ART / f'{sculpt.name}.obj'; mtl = ART / f'{sculpt.name}.mtl'
    lines = [f'# Authored tissue sculpture; no original game vertices', f'mtllib {mtl.name}', f'o {sculpt.name}']
    p, normals, uv = mesh['positions'], mesh['normals'], mesh['uvs']
    lines.extend('v %.6f %.6f %.6f' % tuple(p[i:i+3]) for i in range(0,len(p),3))
    lines.extend('vt %.6f %.6f' % tuple(uv[i:i+2]) for i in range(0,len(uv),2))
    lines.extend('vn %.6f %.6f %.6f' % tuple(normals[i:i+3]) for i in range(0,len(normals),3))
    used = None
    for i in range(0,len(mesh['indices']),3):
        color = mesh['colors'][i]
        if color != used: lines.append(f'usemtl rgb_{color:06x}'); used = color
        lines.append('f ' + ' '.join(f'{j+1}/{j+1}/{j+1}' for j in mesh['indices'][i:i+3]))
    path.write_text('\n'.join(lines)+'\n', encoding='utf-8')
    materials = []
    for color in sorted(set(mesh['colors'])):
        channels = ((color>>16)&255, (color>>8)&255, color&255)
        materials += [f'newmtl rgb_{color:06x}', 'Kd '+' '.join(f'{v/255:.6f}' for v in channels), 'Ks 0.25 0.25 0.25', 'Ns 90']
    mtl.write_text('\n'.join(materials)+'\n',encoding='utf-8')


def audit_mesh(name, mesh):
    """Check the exported, rounded coordinates that the runtime actually reads."""
    p, n, uv, colors, indices = (mesh[key] for key in ('positions', 'normals', 'uvs', 'colors', 'indices'))
    vertex_count = len(p)//3
    assert len(p)%3 == 0 and len(n) == len(p) and len(uv) == vertex_count*2
    assert len(colors) == vertex_count and len(indices)%3 == 0
    assert all(math.isfinite(v) for array in (p,n,uv) for v in array)
    assert all(0 <= c <= 0xFFFFFF for c in colors)
    assert all(0 <= index < vertex_count for index in indices)
    deviations = [abs(math.sqrt(sum(v*v for v in n[i:i+3]))-1) for i in range(0,len(n),3)]
    assert max(deviations) < .00001
    for face in range(0,len(indices),3):
        points = [p[index*3:index*3+3] for index in indices[face:face+3]]
        vector = cross(sub(points[1],points[0]), sub(points[2],points[0]))
        assert sum(v*v for v in vector) > 1e-15, (name, 'Degenerate exported triangle', face//3)
        expected = normal(vector)
        supplied = [n[index*3:index*3+3] for index in indices[face:face+3]]
        # Rounded volume normals differ from a flat triangle's normal, but every
        # corner must point into the face's outward hemisphere.
        assert all(sum(a*b for a,b in zip(expected,corner)) > .10 for corner in supplied), (name, 'Winding disagrees with volume normal', face//3)
    actual_bounds = {'min':[min(p[i::3]) for i in range(3)], 'max':[max(p[i::3]) for i in range(3)]}
    assert actual_bounds == mesh['bounds'] and mesh['sourceScale'] == 1 and mesh['longitudinalAxis'] == 1
    assert len(indices)//3 <= 1100
    barb_spans = []
    for feature in mesh['features']:
        if feature['name'] != 'short_transverse_side_barb': continue
        first = feature['firstTriangle']*3; last = first+feature['triangleCount']*3
        y = [p[index*3+1] for index in indices[first:last]]
        barb_spans.append((max(y)-min(y))/(actual_bounds['max'][1]-actual_bounds['min'][1]))
    assert barb_spans and max(barb_spans) <= .00241, (name, 'Side barb would become an axial blade under long stretch')
    return dict(triangles=len(indices)//3, runtimeQuadVertices=len(indices)//3*4,
                bounds=actual_bounds, exportedGeometryValidated=True,
                maximumNormalLengthDeviation=round(max(deviations),8),
                maximumSideBarbNormalizedAxialSpan=max(barb_spans),
                maximumSideBarbAxialSpanAt164Blocks=max(barb_spans)*164,
                roundVolumeNormals=True,
                transverseTissueRibs=sum(feature['name']=='short_transverse_tissue_rib' for feature in mesh['features']),
                tissueColorVertices=sum(color in (rgb(RED),rgb(DARK_RED)) for color in colors))


def stretched_preview(mesh, length=24, width=2, twist=.23):
    """Model QA with the current runtime's curve/taper/normal mapping.

    This does not change the renderer or inflate its wave. A small 0.2-block
    control offset exposes long-distance shape problems hidden by unit previews.
    """
    result = dict(mesh, positions=[], normals=[])
    source_length=mesh['bounds']['max'][1]-mesh['bounds']['min'][1]
    control=(length*.46,.2,-.08);tip=(length,0,0)
    c,s=math.cos(twist),math.sin(twist)
    for i in range(len(mesh['colors'])):
        p=mesh['positions'][i*3:i*3+3];n=mesh['normals'][i*3:i*3+3]
        t=max(0,min(1,(p[1]-mesh['bounds']['min'][1])/source_length));one=1-t
        tangent=normal(add(mul(control,2*one),mul(sub(tip,control),2*t)))
        reference=(0,0,1);projection=sum(a*b for a,b in zip(reference,tangent))
        radial=normal(sub(reference,mul(tangent,projection)));up=cross(radial,tangent)
        centerline=add(mul(control,2*one*t),mul(tip,t*t));scale=width*max(.08,1-t*.86)
        x=(p[0]*c-p[2]*s)*scale;z=(p[0]*s+p[2]*c)*scale
        point=add(centerline,add(mul(radial,x),mul(up,z)))
        nx=n[0]*c-n[2]*s;nz=n[0]*s+n[2]*c
        direction=normal(add(add(mul(radial,nx),mul(up,nz)),mul(tangent,n[1]*source_length/length)))
        result['positions'].extend(point);result['normals'].extend(direction)
    return result


def blender_scene():
    import bpy
    from mathutils import Vector
    bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
    data=json.loads(RESOURCE.read_text(encoding='utf-8'))
    palette={}
    for name, definition in data['meshes'].items():
        for color in set(definition['colors']):
            if color in palette: continue
            mat=bpy.data.materials.new(f'tissue_{color:06x}'); mat.use_nodes=True
            rgb_color=tuple(((color>>shift)&255)/255 for shift in (16,8,0))
            mat.diffuse_color=(*rgb_color,1)
            shader=mat.node_tree.nodes.get('Principled BSDF')
            linear=tuple(v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4 for v in rgb_color)
            shader.inputs['Base Color'].default_value=(*linear,1)
            shader.inputs['Roughness'].default_value=.28 if color==rgb(RED) else .39
            shader.inputs['Metallic'].default_value=.08
            palette[color]=mat
        raw=definition['positions']; verts=[(raw[i],raw[i+2],raw[i+1]) for i in range(0,len(raw),3)]
        indices=definition['indices']; faces=[(indices[i],indices[i+2],indices[i+1]) for i in range(0,len(indices),3)]
        mesh=bpy.data.meshes.new(name); mesh.from_pydata(verts,[],faces); mesh.update()
        # Preserve the same authored round-volume normals used by the runtime.
        # Coordinates and reversed winding map model Y-up into Blender Z-up.
        raw_normals=definition['normals']
        normal_vectors=[(raw_normals[i],raw_normals[i+2],raw_normals[i+1]) for i in range(0,len(raw_normals),3)]
        for polygon in mesh.polygons: polygon.use_smooth=True
        mesh.normals_split_custom_set_from_vertices(normal_vectors)
        obj=bpy.data.objects.new(name,mesh); bpy.context.collection.objects.link(obj)
        obj.location.x={'tendril_segment':-.28,'whipfist_segment':.27,'tendril_spike':.025}[name]
        colors=list(sorted(set(definition['colors'])))
        for color in colors: mesh.materials.append(palette[color])
        for polygon in mesh.polygons: polygon.material_index=colors.index(definition['colors'][polygon.index*3])
    def light(name,loc,power,color,size):
        block=bpy.data.lights.new(name,'AREA');block.energy=power;block.color=color;block.shape='DISK';block.size=size
        obj=bpy.data.objects.new(name,block);bpy.context.collection.objects.link(obj);obj.location=loc
        obj.rotation_euler=(Vector((0,0,.5))-obj.location).to_track_quat('-Z','Y').to_euler()
    light('broad_key',(-1.8,-2.7,3),240,(.85,.93,1),3)
    light('red_tissue_fill',(1.8,-1.2,1.1),80,(1,.75,.7),2)
    light('edge_rim',(.2,2.5,2),280,(.7,.85,1),2)
    scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=48
    scene.world.color=(.025,.03,.04)
    scene.render.resolution_x=1600;scene.render.resolution_y=1100;scene.render.resolution_percentage=100
    scene.view_settings.view_transform='AgX'
    camera_data=bpy.data.cameras.new('camera');camera=bpy.data.objects.new('camera',camera_data);bpy.context.collection.objects.link(camera)
    scene.camera=camera;camera_data.type='ORTHO'
    def render(name,loc,target,scale):
        camera.location=loc;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();camera_data.ortho_scale=scale
        scene.render.filepath=str(ART/name);bpy.ops.render.render(write_still=True)
    if '--stretched-only' not in sys.argv:
        render('tendrils-three-quarter.png',(1.7,-3,1.8),(0,0,.52),1.8)
        render('tendril-close-up.png',(.45,-2.0,1.5),(-.27,0,.42),.69)
        render('tendrils-side.png',(3,-.3,1.15),(0,0,.53),1.8)
    for obj in bpy.context.scene.objects:
        if obj.type=='MESH':obj.hide_render=True
    stretched=stretched_preview(data['meshes']['tendril_segment'])
    p=stretched['positions'];n=stretched['normals'];indices=stretched['indices']
    verts=[(p[i],p[i+2],p[i+1]) for i in range(0,len(p),3)]
    faces=[(indices[i],indices[i+2],indices[i+1]) for i in range(0,len(indices),3)]
    mesh=bpy.data.meshes.new('tendril_stretched_24_block_QA');mesh.from_pydata(verts,[],faces);mesh.update()
    obj=bpy.data.objects.new(mesh.name,mesh);bpy.context.collection.objects.link(obj)
    colours=list(sorted(set(stretched['colors'])))
    for colour in colours:mesh.materials.append(palette[colour])
    for polygon in mesh.polygons:
        polygon.material_index=colours.index(stretched['colors'][polygon.index*3]);polygon.use_smooth=True
    mesh.normals_split_custom_set_from_vertices([(n[i],n[i+2],n[i+1]) for i in range(0,len(n),3)])
    light('stretched_key',(8,-4,5),1100,(.85,.93,1),6)
    light('stretched_rim',(8,3,3),750,(.7,.85,1),5)
    # Show six central blocks, where the former axial talons became long planks.
    render('tendril-stretched-close-up.png',(8,-5,3.4),(8,0,.10),6)
    obj.hide_render=True
    for item in bpy.context.scene.objects:
        if item.type=='MESH' and item!=obj:item.hide_render=False
    bpy.context.preferences.filepaths.save_version=0
    bpy.ops.wm.save_as_mainfile(filepath=str(ART/'tendrils-authored.blend'))


def main():
    ART.mkdir(parents=True,exist_ok=True);RESOURCE.parent.mkdir(parents=True,exist_ok=True)
    sculpts=[tendril_segment(),tendril_spike(),whipfist_segment()]
    result=dict(schema=1,authored=True,notice='Original procedural sculpture; no source-game mesh or textures copied.',
                meshes={s.name:s.mesh() for s in sculpts})
    text=json.dumps(result,separators=(',',':'))+'\n'
    RESOURCE.write_text(text,encoding='utf-8');(ART/'tendrils-editable.json').write_text(text,encoding='utf-8')
    for sculpt in sculpts: export_obj(sculpt,result['meshes'][sculpt.name])
    report={key:audit_mesh(key,m) for key,m in result['meshes'].items()}
    (ART/'tendrils-model-audit.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report))
    if '--preview' in sys.argv or '--stretched-preview' in sys.argv:
        blender=Path('C:/Program Files/Blender Foundation/Blender 5.2/blender.exe')
        args=[str(blender),'--background','--factory-startup','--python',str(Path(__file__).resolve()),'--','--blender-scene']
        if '--stretched-preview' in sys.argv:args.append('--stretched-only')
        subprocess.run(args,check=True)


if __name__=='__main__':
    if '--blender-scene' in sys.argv: blender_scene()
    else: main()
