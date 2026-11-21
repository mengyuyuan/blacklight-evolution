package com.codex.blacklight.client.renderer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Vertex;

/** Cuts a shallow polygonal bite from the struck skin, retaining its texture on the loose pieces. */
final class ImpactChip {
    record Result(List<Vertex> skin, List<List<Vertex>> chips, boolean cut) {}
    private record Plane(Vec3 normal, double limit) {
        double gap(Vec3 point) { return normal.dot(point) - limit; }
    }

    static Result carve(List<Vertex> mesh, Vec3 site, Vec3 inward, double radius, int pieces, RenderType lining) {
        return cut(mesh,site,inward,radius,pieces,lining,null);
    }
    /** Slender five-sided gouge. The longest dimension follows the observed tangential stroke. */
    static Result groove(List<Vertex> mesh,Vec3 site,Vec3 inward,Vec3 stroke,double radius,RenderType lining){
        return cut(mesh,site,inward,radius,0,lining,stroke);
    }
    private static Result cut(List<Vertex> mesh,Vec3 site,Vec3 inward,double radius,int pieces,RenderType lining,Vec3 stroke){
        Vec3 u=stroke==null?Vec3.ZERO:stroke.subtract(inward.scale(stroke.dot(inward))).normalize();
        if(u.lengthSqr()<.5)u=inward.cross(Math.abs(inward.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize();
        Vec3 v=inward.cross(u);
        List<Plane> planes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double a = 2 * Math.PI * i / 5;
            Vec3 n = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)*(stroke==null?1:3.0)));
            planes.add(new Plane(n, n.dot(site) + radius * (i == 2 ? .8 : 1)));
        }
        planes.add(new Plane(inward, inward.dot(site) + .8 * radius));
        planes.add(new Plane(inward.scale(-1), -inward.dot(site) + .55 * radius));

        List<Vertex> skin = new ArrayList<>(mesh.size());
        List<List<Vertex>> patch = new ArrayList<>();
        Vec3 recess = inward.scale((stroke==null?.46:.27) * radius);
        for (int i = 0; i + 3 < mesh.size(); i += 4) {
            List<Vertex> face = mesh.subList(i, i + 4);
            List<Vertex> inside = face;
            List<Vertex> outside = new ArrayList<>();
            for (Plane plane : planes) {
                if (inside.isEmpty()) break;
                List<Vertex> other = new ArrayList<>();
                inside = split(inside, plane, other);
                tessellate(outside, other);
            }
            if (!hasArea(inside)) {
                skin.addAll(face);
                continue;
            }
            skin.addAll(outside);
            patch.add(inside);
            cap(skin, inside, recess, lining, false, planes);
        }
        if (patch.isEmpty()) return new Result(mesh, List.of(), false);
        if (pieces == 0) return new Result(skin, List.of(), true);
        // Partition the actual contact patch. On a model edge its surviving width can be
        // much smaller than the fist. Splitting that width keeps later blows readable.
        double[] x = range(patch, u), y = range(patch, v);
        boolean wider = x[1] - x[0] >= y[1] - y[0];
        Vec3 across = wider ? u : v;
        double[] span = wider ? x : y;
        List<List<Vertex>> chips = new ArrayList<>(pieces);
        for (int j = 0; j < pieces; j++) {
            double lo = span[0] + (span[1] - span[0]) * j / pieces;
            double hi = span[0] + (span[1] - span[0]) * (j + 1) / pieces;
            Plane left = new Plane(across.scale(-1), -lo), right = new Plane(across, hi);
            List<Vertex> chip = new ArrayList<>();
            for (List<Vertex> face : patch) {
                List<Vertex> strip = split(split(face, left, null), right, null);
                if (!hasArea(strip)) continue;
                tessellate(chip, strip);
                cap(chip, strip, recess, lining, true, planes);
            }
            if (!chip.isEmpty()) chips.add(chip);
        }
        return new Result(skin, chips, true);
    }

    private static double[] range(List<List<Vertex>> patch, Vec3 axis) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (var face : patch) for (var v : face) {
            double coordinate = v.p.dot(axis);
            min = Math.min(min, coordinate);
            max = Math.max(max, coordinate);
        }
        return new double[]{min, max};
    }

    private static List<Vertex> split(List<Vertex> face, Plane plane, List<Vertex> outside) {
        if (face.isEmpty()) return face;
        List<Vertex> inside = new ArrayList<>();
        Vertex last = face.get(face.size() - 1);
        double before = plane.gap(last.p);
        for (Vertex cur : face) {
            double after = plane.gap(cur.p);
            if ((before > 0) != (after > 0)) {
                Vertex edge = last.mix(cur, before / (before - after));
                inside.add(edge);
                if (outside != null) outside.add(edge);
            }
            if (after <= 0) inside.add(cur);
            else if (outside != null) outside.add(cur);
            last = cur;
            before = after;
        }
        return inside;
    }

    private static boolean hasArea(List<Vertex> face) {
        for (int i = 1; i + 1 < face.size(); i++) {
            Vec3 a = face.get(i).p.subtract(face.get(0).p);
            Vec3 b = face.get(i + 1).p.subtract(face.get(0).p);
            if (a.cross(b).lengthSqr() > 1e-15) return true;
        }
        return false;
    }

    private static void tessellate(List<Vertex> into, List<Vertex> polygon) {
        for (int i = 1; i + 1 < polygon.size(); i++) {
            Vertex a = polygon.get(0), b = polygon.get(i), c = polygon.get(i + 1);
            if (b.p.subtract(a.p).cross(c.p.subtract(a.p)).lengthSqr() <= 1e-15) continue;
            into.add(a); into.add(b); into.add(c); into.add(c);
        }
    }

    private static void cap(List<Vertex> into, List<Vertex> face, Vec3 recess, RenderType lining,
                            boolean loose, List<Plane> planes) {
        var backing = new ArrayList<Vertex>();
        for (int i = 0; i < face.size(); i++) {
            Vertex a = face.get(i), b = face.get((i + 1) % face.size());
            Vec3 at = a.p.add(recess), end = b.p.add(recess);
            backing.add(tissue(a, at, loose ? a.n.scale(-1) : a.n, lining, loose ? 0xff923847 : 0xff782b35));
            if (a.p.distanceToSqr(b.p) < 1e-14) continue;
            // Only the outline of the recess needs a wall. A detached chip also exposes its sides.
            if (!loose && planes.stream().noneMatch(p -> Math.abs(p.gap(a.p)) < 1e-6 && Math.abs(p.gap(b.p)) < 1e-6)) continue;
            Vec3 normal = b.p.subtract(a.p).cross(recess).normalize();
            if (normal.lengthSqr() < .5) continue;
            for (Vec3 corner : List.of(a.p, b.p, end, at))
                into.add(tissue(a, corner, normal, lining, 0xff9b4250));
        }
        if (loose) java.util.Collections.reverse(backing);
        tessellate(into, backing);
    }

    private static Vertex tissue(Vertex source, Vec3 at, Vec3 normal, RenderType lining, int color) {
        return new Vertex(source.key, lining, at, normal, .5F, .5F, color, source.overlay, source.light);
    }
}
