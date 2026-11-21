"""Measure contiguous torso/sleeve UV patches from a local P1 alex.p3d.rz.

The independent MC and Mercer characters have different proportions, geometry
and seam layouts. Each of their six box faces maps to one connected chart from
the original vest or sleeve. A source island is never interpolated into another.
"""
import argparse
import collections
import math
from pathlib import Path
import struct

import export_devastator_fx as p3d


def vectors(values, width):
    return list(zip(*[iter(values)] * width))


def read_surface(tree, name):
    skin, = [node for node in p3d.walk(tree)
             if node.kind == 0x10001 and p3d.string(node.payload)[0] == name]
    group, = [c for c in skin.children if c.kind == 0x10020]
    _, offset = p3d.string(group.payload, 4)
    primitive, _, count, index_count, _ = struct.unpack_from("<5I", group.payload, offset)
    chunks = {c.kind: c.payload for c in group.children}
    if primitive != 0 or index_count % 3:
        raise ValueError("Expected a triangle list")
    if 0x10014 in chunks:
        attr = p3d.attributes(group.children, count)
        pos, uv = vectors(attr[p3d.POSITION], 3), vectors(attr[p3d.UV0], 2)
        index = [value for value, in struct.iter_unpack("<H", chunks[0x10013][12:])]
    else:
        pos = list(struct.iter_unpack("<3f", chunks[0x10005][4:]))
        uv_data, = [c.payload for c in group.children if c.kind == 0x10007]
        if struct.unpack_from("<II", uv_data) != (count, 0):
            raise ValueError("Expected a single UV0 stream on the vest")
        uv = list(struct.iter_unpack("<2f", uv_data[8:]))
        index = [value for value, in struct.iter_unpack("<I", chunks[0x1000a][4:])]
    if len(index) != index_count or len(uv) != count or len(pos) != count:
        raise ValueError("Source surface counts do not match")
    return pos, uv, vectors(index, 3)


def subtract(a, b):
    return tuple(x-y for x, y in zip(a, b))


def cross(a, b):
    return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])


def describe(mesh):
    pos, uv, tris = mesh
    # Original UV seams are split even where vertex positions are identical.
    keys = [tuple(round(t, 6) for t in (*p, *u)) for p, u in zip(pos, uv)]
    faces = []
    for idx, tri in enumerate(tris):
        xyz = [pos[i] for i in tri]
        c = tuple(sum(axis)/3 for axis in zip(*xyz))
        n = cross(subtract(xyz[1], xyz[0]), subtract(xyz[2], xyz[0]))
        edges = [tuple(sorted((keys[tri[i]], keys[tri[(i+1)%3]]))) for i in range(3)]
        faces.append((idx, c, n, edges))
    return faces


def components(triangles):
    owners = collections.defaultdict(list)
    for i, _, _, edges in triangles:
        for e in edges:
            owners[e].append(i)
    neigh = collections.defaultdict(set)
    for ids in owners.values():
        for i in ids:
            neigh[i].update(ids)
    pending = {i for i, _, _, _ in triangles}
    while pending:
        stack = [min(pending)]
        group = set()
        while stack:
            i = stack.pop()
            if i in group: continue
            group.add(i)
            stack.extend(neigh[i] - group)
        pending.difference_update(group)
        yield group

GRID = 5
AXES = ((2, 1), (2, -1), (0, 1), (0, -1), (1, 1), (1, -1))


def closest_barycentric(q, triangle):
    a, b, c = triangle
    def det(v, w): return v[0]*w[1] - v[1]*w[0]
    ab, ac, aq = subtract(b, a), subtract(c, a), subtract(q, a)
    total = det(ab, ac)
    if abs(total) > 1e-10:
        wb, wc = det(aq, ac)/total, det(ab, aq)/total
        if wb >= 0 and wc >= 0 and wb+wc <= 1:
            return (1-wb-wc, wb, wc), 0.
    best = (None, math.inf)
    for i in range(3):
        j = (i+1)%3
        start, edge = triangle[i], subtract(triangle[j], triangle[i])
        length = sum(v*v for v in edge)
        if length < 1e-12: continue
        f = max(0, min(1, sum(x*y for x,y in zip(subtract(q,start),edge))/length))
        dist = sum((q[k]-(start[k]+f*edge[k]))**2 for k in range(2))
        if dist < best[1]:
            w = [0., 0., 0.]
            w[i], w[j] = 1-f, f
            best = (w, dist)
    return best


def canonical(p, part):
    # P1 faces -Z like Mercer V4, but its anatomical left is +X. The source
    # sleeve extends obliquely in bind pose; MC arms hang straight down.
    x, y, z = p
    if not part:
        return (-x/.218, (y-1.10)/.50, (z-.006)/.147)
    sign = -1 if part == 1 else 1
    centre_x = sign*(.502-.53*(y-1.035))
    centre_z = -.052+.150*(y-1.035)
    return ((centre_x-x)/.066, (y-1.035)/.51, (z-centre_z)/.079)


def largest_patch(mesh, faces, part, side):
    pos, uv, tris = mesh
    axis, sign = AXES[side]
    facing = []
    for i, c, n, e in faces:
        h = c[1]
        if part and (not (1.005 <= h <= 1.575) or ((c[0] < 0) != (part == 1))):
            continue
        if not part and not (1.08 < h < 1.64):
            continue
        if not part and side == 1 and c[0] < 0: continue
        # Exclude the hem, rectangular cuff and hand islands when looking for the sleeve.
        if part and min(uv[j][0] for j in tris[i]) < .604: continue
        normal = n[axis] * sign * (-1 if axis == 0 else 1)
        if normal < .18 * math.sqrt(sum(v*v for v in n)): continue
        if axis == 1 and ((sign > 0 and canonical(c,part)[1] < .75) or
                          (sign < 0 and canonical(c,part)[1] > .25)): continue
        facing.append((i,c,n,e))
    projected_area = {i:abs(n[axis]) for i,_,n,_ in facing}
    group = max(components(facing), key=lambda g: sum(projected_area[i] for i in sorted(g)))
    if len(group) < 2:
        raise ValueError(f"No continuous source patch for part {part} side {side}")
    st_axes = (0,2) if axis == 1 else (2,1) if axis == 0 else (0,1)
    rows = []
    for i in sorted(group):
        pts = [canonical(pos[j],part) for j in tris[i]]
        flat = [(p[st_axes[0]],p[st_axes[1]]) for p in pts]
        depth = sum(p[axis]*sign for p in pts)/3
        rows.append((flat, [uv[j] for j in tris[i]], depth))
    # Caps and side walls use the measured extent of the selected chart.
    # Sampling slightly inside its perimeter avoids collapsed UVs along the edge.
    extents = [sorted(p[a] for tri,_,_ in rows for p in tri) for a in range(2)]
    ranges = [(v[int(.05*(len(v)-1))], v[int(.95*(len(v)-1))]) for v in extents]
    if axis != 1:
        ranges[1] = (max(.01,ranges[1][0]),min(.99,ranges[1][1]))
    def measure(s,t):
        # The two zip halves have independent P1 UVs. Mirror one front panel to
        # preserve physical weave density without crossing an atlas island seam.
        if not part and side == 1: s = 1-abs(2*s-1)
        q = tuple(lo+(hi-lo)*f for (lo,hi),f in zip(ranges,(s,t)))
        best = (math.inf,0)
        found = None
        for tri, tex, depth in rows:
            weights, dist = closest_barycentric(q,tri)
            rank = (dist,-depth)
            if weights is not None and rank < best:
                best = rank
                found = tuple(sum(w * pt[k] for w,pt in zip(weights,tex)) for k in range(2))
        return found
    links = [(i,i+1) for i in range(GRID**2) if i%GRID < GRID-1]
    links += [(i,i+GRID) for i in range(GRID*(GRID-1))]
    # A cylindrical UV cut sometimes ends at the shoulder, leaving two banks
    # topology-connected around the tip. Keep one contiguous side of the cut.
    for lo, hi in ((0.,1.), (0.,.75), (.25,1.), (0.,.5), (.5,1.)):
        for bottom, top in ((0.,1.), (.12,1.), (0.,.88)):
            grid = [measure(lo+(hi-lo)*x/(GRID-1), bottom+(top-bottom)*y/(GRID-1))
                    for y in range(GRID) for x in range(GRID)]
            if max(math.dist(grid[i],grid[j]) for i,j in links) <= .135:
                return grid, len(group)
    raise ValueError(f"No seam-safe interval for part {part} side {side}")


def main():
    arg = argparse.ArgumentParser(description=__doc__)
    arg.add_argument("package", type=Path)
    arg.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] /
                     "src/main/resources/assets/blacklight/models/consume_body_uv.bin")
    args = arg.parse_args()
    tree = p3d.chunks(memoryview(p3d.decompress(args.package)))
    vest = read_surface(tree, "alex_reg_body_AlexVestShape")
    arms = read_surface(tree, "alex_reg_arms_alex_armsShape")
    data = bytearray(b"CUV1" + bytes([GRID]))
    for part, mesh in enumerate((vest, arms, arms)):
        faces = describe(mesh)
        for side in range(len(AXES)):
            values, count = largest_patch(mesh,faces,part,side)
            if any(not (0 <= v <= 1) for uv in values for v in uv):
                raise ValueError("UV outside the original character atlas")
            links = [(i,i+1) for i in range(GRID**2) if i%GRID < GRID-1]
            links += [(i,i+GRID) for i in range(GRID*(GRID-1))]
            jump = max(math.dist(values[i], values[j]) for i,j in links)
            area = [max(v[k] for v in values)-min(v[k] for v in values) for k in (0,1)]
            if jump > .22 or max(area) < .012:
                raise ValueError(f"Unusable UV patch ({part}, {side}): step {jump}, span {area}")
            print(f"part {part} face {side}: {count} connected triangles, max UV step {jump:.3f}, "
                  f"patch size {area[0]:.3f} x {area[1]:.3f}")
            for uv in values:
                data.extend(struct.pack(">HH", *(round(v*65535) for v in uv)))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(data)


if __name__ == "__main__":
    main()
