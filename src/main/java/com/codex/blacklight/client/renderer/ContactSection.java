package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/** A confirmed sharp contact selects an immediate, complete section. This is
 * the original-feel presentation policy, not a claim of finite-volume cutting. */
final class ContactSection {
    record Plane(Vec3 point, Vec3 normal) {}

    static Plane fit(Vec3 contact, Vec3 normal, List<Vec3> skin) {
        if (skin.isEmpty() || normal.lengthSqr() < 1e-12) return null;
        normal = normal.normalize();
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (var p : skin) {
            double d = p.subtract(contact).dot(normal);
            lo = Math.min(lo, d); hi = Math.max(hi, d);
        }
        double width = hi - lo;
        if (width < .04) return null;
        // Preserve interior hit locations. A grazing lethal contact is inset
        // within the contacted tissue so it makes a readable section, not skin dust.
        double inset = width * .30;
        double offset = Math.clamp(0, lo + inset, hi - inset);
        return new Plane(contact.add(normal.scale(offset)), normal);
    }

    /** Exact finite triangle contact. Overlapping bounding boxes alone never
     * authorize a consume cut; both blade and skin edges are tested. */
    static Plane contact(List<Vec3[]> blade, List<Vec3> quads) {
        for (var a : blade) {
            var cross = a[1].subtract(a[0]).cross(a[2].subtract(a[0]));
            if (cross.lengthSqr() < 1e-14) continue;
            for (int f = 0; f + 3 < quads.size(); f += 4) {
                if (!overlaps(a, quads, f)) continue;
                for (int t = 1; t < 3; t++) {
                    Vec3[] b = {quads.get(f), quads.get(f+t), quads.get(f+t+1)};
                    for (int i = 0; i < 3; i++) {
                        var p = intersection(a[i], a[(i+1)%3], b);
                        if (p == null) p = intersection(b[i], b[(i+1)%3], a);
                        if (p != null) return new Plane(p, cross.normalize());
                    }
                }
            }
        }
        return null;
    }

    private static boolean overlaps(Vec3[] a,List<Vec3> b,int offset) {
        for(int axis=0;axis<3;axis++){
            double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
            double otherLo=lo,otherHi=hi;
            for(var p:a){double v=coordinate(p,axis);lo=Math.min(lo,v);hi=Math.max(hi,v);}
            for(int i=0;i<4;i++){double v=coordinate(b.get(offset+i),axis);otherLo=Math.min(otherLo,v);otherHi=Math.max(otherHi,v);}
            if(hi<otherLo-1e-7||otherHi<lo-1e-7)return false;
        }
        return true;
    }
    private static double coordinate(Vec3 p,int axis){return axis==0?p.x:axis==1?p.y:p.z;}

    private static Vec3 intersection(Vec3 from, Vec3 to, Vec3[] triangle) {
        var direction = to.subtract(from);
        var e1 = triangle[1].subtract(triangle[0]);
        var e2 = triangle[2].subtract(triangle[0]);
        var h = direction.cross(e2); double determinant = e1.dot(h);
        if (Math.abs(determinant) < 1e-12) return null;
        var s = from.subtract(triangle[0]); double u = s.dot(h)/determinant;
        if (u < -1e-7 || u > 1+1e-7) return null;
        var q = s.cross(e1); double v = direction.dot(q)/determinant;
        if (v < -1e-7 || u+v > 1+1e-7) return null;
        double t = e2.dot(q)/determinant;
        return t < -1e-7 || t > 1+1e-7 ? null : from.lerp(to, Math.clamp(t,0,1));
    }
}
