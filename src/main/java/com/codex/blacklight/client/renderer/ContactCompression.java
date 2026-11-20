package com.codex.blacklight.client.renderer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Vertex;

/** A local press into the captured skin. Positions and normals use the same radial field. */
final class ContactCompression {
    final Vec3 center, direction;
    final double radius, depth, started;
    double strength;

    ContactCompression(Vec3 center,Vec3 direction,double radius,double started) {
        this.center=center;
        this.direction=direction.normalize();
        this.radius=radius;
        this.depth=radius*.39;
        this.started=started;
        strength=weight(0);
    }

    static double weight(double age) {
        // Visible contact on the first sample, one tick of compression, then recoil.
        if(age<1)return .68+.32*Math.max(0,age);
        double t=Math.max(0,Math.min(1,(age-1)/5));
        return 1-.76*t*t*(3-2*t);
    }

    Vec3 point(Vec3 p) {
        double w=1-p.distanceToSqr(center)/(radius*radius);
        return w<=0?p:p.add(direction.scale(depth*strength*w*w));
    }

    Vec3 normal(Vec3 p,Vec3 normal) {
        Vec3 delta=p.subtract(center);
        double w=1-delta.lengthSqr()/(radius*radius);
        if(w<=0)return normal;
        Vec3 gradient=delta.scale(-4*w*depth*strength/(radius*radius));
        return normal.scale(1+gradient.dot(direction)).subtract(gradient.scale(normal.dot(direction))).normalize();
    }

    int shade(double displacement, int color) {
        if (displacement <= 0) return color;
        // Self-shadow inside the hollow is visible on flat-lit entity textures too.
        double factor = 1 - .43 * Math.min(1, displacement / depth);
        int red = (int)Math.round(((color >>> 16) & 255) * factor);
        int green = (int)Math.round(((color >>> 8) & 255) * factor);
        int blue = (int)Math.round((color & 255) * factor);
        return (color & 0xff000000) | red << 16 | green << 8 | blue;
    }

    List<Vertex> surface(List<Vertex> source) {
        int faces = source.size() / 4;
        int[] columns = new int[faces], rows = new int[faces];
        int[] extra = new int[9];
        for (int i = 0; i < faces; i++) {
            int at = 4 * i;
            if (AdaptiveRagdoll.bounds(source.subList(at, at + 4)).distanceToSqr(center) >= radius * radius) continue;
            var a = source.get(at); var b = source.get(at + 1);
            var c = source.get(at + 2); var d = source.get(at + 3);
            columns[i] = divisions(Math.max(a.p.distanceTo(b.p), d.p.distanceTo(c.p)));
            rows[i] = divisions(Math.max(a.p.distanceTo(d.p), b.p.distanceTo(c.p)));
            for (int limit = 2; limit <= 8; limit++) {
                extra[limit] += 4 * (Math.min(limit, columns[i]) * Math.min(limit, rows[i]) - 1);
            }
        }
        // All coincident texture layers must get the same grid. Letting early
        // layers use the budget first leaves a flat outer face over the dent.
        int limit = 8;
        while (limit > 1 && extra[limit] > 1536) limit--;
        var out = new ArrayList<Vertex>(source.size() + extra[limit]);
        for (int i = 0; i < faces; i++) {
            int at = 4 * i;
            int nx = Math.min(limit, columns[i]), ny = Math.min(limit, rows[i]);
            if (nx * ny <= 1) {
                out.addAll(source.subList(at, at + 4));
                continue;
            }
            var a = source.get(at); var b = source.get(at + 1);
            var c = source.get(at + 2); var d = source.get(at + 3);
            var grid = new Vertex[ny + 1][nx + 1];
            for (int y = 0; y <= ny; y++) {
                double t = (double)y / ny;
                var left = a.mix(d, t); var right = b.mix(c, t);
                for (int x = 0; x <= nx; x++) grid[y][x] = left.mix(right, (double)x / nx);
            }
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    out.add(grid[y][x]); out.add(grid[y][x + 1]);
                    out.add(grid[y + 1][x + 1]); out.add(grid[y + 1][x]);
                }
            }
        }
        return out;
    }

    private int divisions(double extent) {
        return Math.max(1,Math.min(8,(int)Math.ceil(extent/(radius*.35))));
    }
}
