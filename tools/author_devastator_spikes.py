"""Author Blacklight's editable, original devastator spike meshes.

Only the public recipe below is an input. No Prototype file, private mesh array,
texture or motion is read. Angular split blades, lobed muscle roots, asymmetric
spurs and raised vein cords are independently constructed geometry. The source
game is visual proportion/structure reference, not a source of topology.

Run from any directory; optional --recipe/--output/--obj-output override defaults.
Generated OBJ files include vertex RGB, UVs and normals and can be edited in a
3D modeller. World coordinates are Y-up, in Minecraft blocks, scale 1.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[1]


def add(a, b): return tuple(x + y for x, y in zip(a, b))
def sub(a, b): return tuple(x - y for x, y in zip(a, b))
def mul(v, s): return tuple(x * s for x in v)
def dot(a, b): return sum(x * y for x, y in zip(a, b))
def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
def unit(v):
    size = math.sqrt(dot(v, v))
    if size < 1e-12: raise ValueError("Degenerate modelling frame")
    return mul(v, 1 / size)


def catmull(points, t):
    span = max(0, min(len(points) - 1.000001, t * (len(points) - 1)))
    i = int(span); u = span - i
    p0, p1, p2, p3 = [points[max(0, min(len(points) - 1, j))] for j in (i - 1, i, i + 1, i + 2)]
    return tuple(.5 * ((2 * b) + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u * u
                      + (-a + 3 * b - 3 * c + d) * u * u * u)
                 for a, b, c, d in zip(p0, p1, p2, p3))


def noise(seed, value):
    return math.sin(seed * 2.317 + value * 1.913) * .58 + math.sin(seed * .371 + value * 4.732) * .42


def colour_mix(a, b, weight):
    return tuple(max(0, min(255, round(x * (1 - weight) + y * weight))) for x, y in zip(a, b))


class Mesh:
    def __init__(self):
        self.positions = []; self.uvs = []; self.colours = []; self.indices = []; self.parts = []

    def vertex(self, position, uv, colour):
        index = len(self.positions)
        self.positions.append(tuple(position)); self.uvs.append(tuple(uv)); self.colours.append(tuple(colour))
        return index

    def triangle(self, a, b, c):
        area = cross(sub(self.positions[b], self.positions[a]), sub(self.positions[c], self.positions[a]))
        if dot(area, area) <= 1e-17: return
        self.indices.extend((a, b, c))

    def loft(self, name, points, section, rings, colour, seed, roughness=0, roll=0, hard=False):
        first_triangle = len(self.indices) // 3
        vertices = []; sides = len(section)
        for i in range(rings):
            t = i / (rings - 1); row = catmull(points, t); center = row[:3]
            width, depth = max(.001, row[3]), max(.001, row[4])
            lo = catmull(points, max(0, t - .002))[:3]; hi = catmull(points, min(1, t + .002))[:3]
            tangent = unit(sub(hi, lo))
            reference = (0, 0, 1) if abs(tangent[2]) < .96 else (1, 0, 0)
            u = unit(cross(tangent, reference)); v = unit(cross(tangent, u))
            angle = roll + noise(seed, t * 5) * roughness * .35
            uu = add(mul(u, math.cos(angle)), mul(v, math.sin(angle)))
            vv = add(mul(v, math.cos(angle)), mul(u, -math.sin(angle)))
            ring = []
            for j, (x, y) in enumerate(section):
                ridge = 1 + roughness * noise(seed + j * 3, i * .73)
                point = add(center, add(mul(uu, x * width * ridge), mul(vv, y * depth * ridge)))
                ring.append(self.vertex(point, (j / sides, t), colour(t, j)))
            vertices.append(ring)
        for a, b in zip(vertices, vertices[1:]):
            for j in range(sides):
                k = (j + 1) % sides
                if hard:
                    # Each angular blade panel has a deliberate sharp bevel ridge.
                    q = [self.vertex(self.positions[idx], self.uvs[idx], self.colours[idx])
                         for idx in (a[j], a[k], b[k], b[j])]
                    self.triangle(q[0], q[1], q[2]); self.triangle(q[0], q[2], q[3])
                else:
                    self.triangle(a[j], a[k], b[k]); self.triangle(a[j], b[k], b[j])
        for end, ring in ((0, vertices[0]), (-1, vertices[-1])):
            center = tuple(sum(self.positions[k][axis] for k in ring) / sides for axis in range(3))
            cap = self.vertex(center, (.5, end + 1), colour(0 if end == 0 else 1, 0))
            for j in range(sides):
                k = (j + 1) % sides
                if end == 0: self.triangle(cap, ring[k], ring[j])
                else: self.triangle(cap, ring[j], ring[k])
        self.parts.append(dict(name=name, firstTriangle=first_triangle, triangleCount=len(self.indices) // 3 - first_triangle))

    def transformed(self, other, offset=(0, 0, 0), yaw=0, tilt=0):
        base = len(self.positions); first = len(self.indices) // 3
        a, b = math.radians(yaw), math.radians(tilt)
        for p, uv, color in zip(other.positions, other.uvs, other.colours):
            x, y, z = p
            x, y = x * math.cos(b) - y * math.sin(b), x * math.sin(b) + y * math.cos(b)
            x, z = x * math.cos(a) - z * math.sin(a), x * math.sin(a) + z * math.cos(a)
            self.vertex(add((x, y, z), offset), uv, color)
        self.indices.extend(base + i for i in other.indices)
        for part in other.parts:
            self.parts.append(dict(part, firstTriangle=part["firstTriangle"] + first))

    def finish(self, ident):
        # Remove construction ring vertices unused by the sharp blade panels.
        used = sorted(set(self.indices)); lookup = {old: new for new, old in enumerate(used)}
        self.positions = [self.positions[i] for i in used]; self.uvs = [self.uvs[i] for i in used]
        self.colours = [self.colours[i] for i in used]; self.indices = [lookup[i] for i in self.indices]
        normal = [(0, 0, 0) for _ in self.positions]
        for start in range(0, len(self.indices), 3):
            ids = self.indices[start:start + 3]; a, b, c = [self.positions[i] for i in ids]
            face = cross(sub(b, a), sub(c, a))
            for i in ids: normal[i] = add(normal[i], face)
        normal = [unit(n) for n in normal]
        low = [min(p[a] for p in self.positions) for a in range(3)]
        high = [max(p[a] for p in self.positions) for a in range(3)]
        data = dict(id=ident, sourceScale=1, coordinateSystem="world_Y_up", authored=True,
                    positions=[round(x, 6) for p in self.positions for x in p],
                    normals=[round(x, 6) for n in normal for x in n],
                    uvs=[round(x, 6) for uv in self.uvs for x in uv],
                    colors=[(r << 16) | (g << 8) | b for r, g, b in self.colours],
                    indices=self.indices, bounds=dict(min=low, max=high), longitudinalAxis=1,
                    parts=self.parts)
        return data


def round_section(sides, lobes=0, offset=0):
    result = []
    for i in range(sides):
        angle = i * 2 * math.pi / sides + offset
        radius = 1 + (.14 * math.cos(angle * lobes) if lobes else 0)
        result.append((math.cos(angle) * radius, math.sin(angle) * radius))
    return result


def spike(height, seed, recipe, satellite=False, lod=False):
    mesh = Mesh(); palette = recipe["palette"]; config = recipe["construction"]; h = height
    density = recipe["lod"]["satellite" if satellite else "single"] if lod else {}
    section = [config["bladeSection"][i] for i in density.get("bladeSectionIndices", range(8))]
    spur_section = [config["bladeSection"][i] for i in density.get("spurSectionIndices", range(8))]
    dark = lambda t, j: colour_mix(palette["root"], palette["muscle"], .18 + .25 * math.sin(j * 1.3 + t * 9) ** 2)
    bone = lambda t, j: colour_mix(palette["bone"], palette["edge"], (.32 + .42 * t) if j in (0, 4) else .06 + .15 * t)
    red = lambda t, j: colour_mix(palette["veinShadow"], palette["vein"], .45 + .45 * math.cos(j * 1.7) ** 2)
    scale = lambda rows: [tuple(x * h for x in row) for row in rows]
    # Broad lobed flesh root. It rises around the fractured blade rather than
    # ending as a circular cone. The negative foot margin can enter the ground.
    root = scale([(0, -.018, 0, .18, .16), (-.01, .035, .01, .19, .17),
                  (.013, .11, -.012, .16, .14), (.018, .205, -.009, .12, .105),
                  (-.012, .31, .014, .095, .081), (.014, .41, 0, .069, .052)])
    mesh.loft("lobed_biomass_root", root, round_section(density.get("rootSides", 10), 5), density.get("rootRings", 9), dark, seed, .1)
    primary = scale(config["primarySpine"])
    # Each variant changes the authored curve asymmetrically, not private data.
    primary = [(x + noise(seed, y / h * 5) * .009 * h, y, z, w, d)
               for x, y, z, w, d in primary]
    blade_rings = density.get("bladeRings", 18 if satellite else config["mainBladeRings"])
    mesh.loft("hooked_primary_bone_blade", primary, section, blade_rings, bone, seed + 9, .075, hard=True)
    split = scale(config["splitSpine"])
    mesh.loft("open_fracture_counter_blade", split, section, density.get("splitRings", 13 if satellite else 19),
              bone, seed + 21, .05, roll=.21, hard=True)
    # Secondary sharp spurs emerge from one side at staggered heights. These are
    # volume with their own blade panels, not marks painted on the shaft.
    for k in range(1 if satellite else 2):
        y = (.29 + k * .18) * h; sign = 1 if k == 0 else -1
        spur = [(0, y, 0, .055 * h, .025 * h), (sign * .115 * h, y + .055 * h, .018 * h, .042 * h, .018 * h),
                (sign * .185 * h, y + .13 * h, .012 * h, .019 * h, .009 * h),
                (sign * .17 * h, y + .21 * h, -.02 * h, .001, .001)]
        mesh.loft("irregular_bone_spur_" + str(k), spur, spur_section, density.get("spurRings", 9), bone, seed + 37 + k, .04, roll=.15, hard=True)
    # Muscle bundles wrap upward on the root and split beneath the bone forks.
    for k in range(density.get("muscleCount", 2 if satellite else 4)):
        theta = k * 2.399 + seed * .019; path = []
        for i in range(6):
            t = i / 5; y = (.012 + .385 * t) * h; angle = theta + .95 * t
            radius = (.145 * (1 - t) + .049 * t) * h
            path.append((math.cos(angle) * radius, y, math.sin(angle) * radius,
                         (.046 * (1 - t) + .011 * t) * h, (.038 * (1 - t) + .008 * t) * h))
        mesh.loft("raised_muscle_bundle_" + str(k), path, round_section(density.get("muscleSides", 7), 0), density.get("muscleRings", 9), dark, seed + 60 + k, .11)
    # Raised red cords follow physical surfaces and terminate beneath the tips.
    for k in range(density.get("veinCount", 1 if satellite else 3)):
        path = []; theta = seed * .023 + k * 2.09
        for i in range(8):
            t = i / 7; at = .025 + t * .67; c = catmull(primary, min(.83, at))
            angle = theta + 1.55 * t
            r = (.0095 * (1 - .48 * t)) * h
            path.append((c[0] + math.cos(angle) * c[3] * .93, c[1],
                         c[2] + math.sin(angle) * c[4] * .97, r, r * .78))
        mesh.loft("raised_red_vein_" + str(k), path, round_section(density.get("veinSides", 5)), density.get("veinRings", 17 if not satellite else 12), red, seed + 91 + k, .05)
    return mesh


def cluster(height, seed, recipe, lod=False):
    result = Mesh(); result.transformed(spike(height, seed, recipe, lod=lod), yaw=seed % 37 - 18)
    dark = lambda t, j: colour_mix(recipe["palette"]["root"], recipe["palette"]["muscle"], .25)
    for index, (dx, dz, ratio, yaw, tilt) in enumerate(recipe["construction"]["clusterSatellites"]):
        # Unequal positions, scales and cant give a spreading graveyard crown.
        offset = (dx * height, .005, dz * height)
        result.transformed(spike(height * ratio, seed + index * 17 + 5, recipe, satellite=True, lod=lod),
                           offset=offset, yaw=yaw + seed % 13, tilt=tilt)
        # Broad living root cables connect the distant roots into one organism.
        path = [(0, .045 * height, 0, .10 * height, .07 * height),
                (offset[0] * .4, .13 * height, offset[2] * .4, .08 * height, .065 * height),
                (offset[0] * .78, .09 * height, offset[2] * .78, .072 * height, .047 * height),
                (offset[0], .03 * height, offset[2], .057 * height, .037 * height)]
        result.loft("spreading_root_bridge_" + str(index), path,
                    round_section(recipe["lod"]["bridgeSides"] if lod else 8, 4),
                    recipe["lod"]["bridgeRings"] if lod else 10, dark, seed + index * 21, .12)
    return result


def write_obj(path, mesh, provenance):
    lines = ["# Original Blacklight authored geometry; no imported game topology or textures.",
             "# Y-up Minecraft block units; vertex RGB extension keeps raised tissue colours editable.",
             "# " + provenance, "o " + mesh["id"]]
    p, n, uv, colours = mesh["positions"], mesh["normals"], mesh["uvs"], mesh["colors"]
    for i in range(len(colours)):
        rgb = [(colours[i] >> shift & 255) / 255 for shift in (16, 8, 0)]
        lines.append("v " + " ".join(f"{v:.6f}" for v in (*p[i * 3:i * 3 + 3], *rgb)))
    for i in range(len(colours)): lines.append("vt " + " ".join(f"{v:.6f}" for v in uv[i * 2:i * 2 + 2]))
    for i in range(len(colours)): lines.append("vn " + " ".join(f"{v:.6f}" for v in n[i * 3:i * 3 + 3]))
    for at in range(0, len(mesh["indices"]), 3):
        for part in mesh["parts"]:
            if part["firstTriangle"] == at // 3: lines.append("g " + part["name"])
        lines.append("f " + " ".join(f"{i + 1}/{i + 1}/{i + 1}" for i in mesh["indices"][at:at + 3]))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def validate(mesh):
    count = len(mesh["colors"])
    assert len(mesh["positions"]) == len(mesh["normals"]) == count * 3
    assert len(mesh["uvs"]) == count * 2 and len(mesh["indices"]) % 3 == 0
    assert max(mesh["indices"]) < count and min(mesh["indices"]) >= 0
    assert all(math.isfinite(v) for name in ("positions", "normals", "uvs") for v in mesh[name])
    assert all(abs(sum(v * v for v in mesh["normals"][i:i + 3]) - 1) < 1e-4 for i in range(0, count * 3, 3))
    assert any("raised_red_vein" in part["name"] for part in mesh["parts"])
    assert any("open_fracture" in part["name"] for part in mesh["parts"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recipe", type=Path, default=REPOSITORY / "art/devastators/spikes.model.json")
    parser.add_argument("--output", type=Path, default=REPOSITORY / "src/main/resources/assets/blacklight/models/devastators/spikes.json")
    parser.add_argument("--obj-output", type=Path, default=REPOSITORY / "art/devastators")
    args = parser.parse_args(); recipe = json.loads(args.recipe.read_text(encoding="utf-8"))
    result = dict(version=1, coordinateSystem="world_Y_up", worldScale=1, authored=True,
                  provenance=recipe["provenance"], meshes={})
    args.output.parent.mkdir(parents=True, exist_ok=True); args.obj_output.mkdir(parents=True, exist_ok=True)
    for ident, config in recipe["variants"].items():
        author = cluster if config["cluster"] else spike
        mesh = author(config["height"], config["seed"], recipe).finish(ident); validate(mesh)
        reduced = author(config["height"], config["seed"], recipe, lod=True).finish(ident + "_lod"); validate(reduced)
        budget = recipe["lod"]["clusterTriangleBudget" if config["cluster"] else "singleTriangleBudget"]
        if len(reduced["indices"]) // 3 > budget:
            raise ValueError("Authored LOD exceeded the per-instance geometry budget")
        reduced["lodOf"] = ident
        # Both levels retain identical authored height and a shared conservative
        # envelope; reductions rebuild closed sections rather than drop triangles.
        bounds = {"min": [min(mesh["bounds"]["min"][a], reduced["bounds"]["min"][a]) for a in range(3)],
                  "max": [max(mesh["bounds"]["max"][a], reduced["bounds"]["max"][a]) for a in range(3)]}
        mesh["bounds"] = reduced["bounds"] = bounds
        for candidate in (mesh, reduced):
            result["meshes"][candidate["id"]] = candidate
            write_obj(args.obj_output / (candidate["id"] + ".obj"), candidate, recipe["provenance"])
            print(candidate["id"], "vertices", len(candidate["colors"]), "triangles", len(candidate["indices"]) // 3,
                  "height", round(candidate["bounds"]["max"][1] - candidate["bounds"]["min"][1], 3))
    args.output.write_text(json.dumps(result, separators=(",", ":"), allow_nan=False), encoding="utf-8")
    print("WROTE", args.output, args.output.stat().st_size, "bytes; editable OBJ", args.obj_output)


if __name__ == "__main__": main()
