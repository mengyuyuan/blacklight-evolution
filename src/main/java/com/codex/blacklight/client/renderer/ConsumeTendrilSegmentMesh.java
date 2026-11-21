package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Deforms the original three-piece Tendril_SegmentShape once per authored FIG span. */
final class ConsumeTendrilSegmentMesh {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("blacklight", "textures/entity/consume_tendril_segment.png");
    // Callers express width as a nominal radius: .107 * original FIG scale in world units.
    private static final double SOURCE_RADIUS = .107;
    private record Mesh(float[] vertices, int[] indices, float[] deformed) {}
    private static final class Loaded {
        static final Mesh MESH = read();
    }

    private ConsumeTendrilSegmentMesh() {}

    private static Mesh read() {
        var resource = ConsumeTendrilSegmentMesh.class.getResourceAsStream("/assets/blacklight/models/consume/tendril_segment.bin");
        if (resource == null) throw new IllegalStateException("Missing Tendril_SegmentShape");
        try (var input = new DataInputStream(new BufferedInputStream(resource))) {
            if (input.readInt() != 0x54534731 || input.readUnsignedShort() != 513 || input.readUnsignedShort() != 1248)
                throw new IOException("Invalid Tendril_SegmentShape header");
            float[] vertices = new float[513 * 8];
            for (int i = 0; i < vertices.length; ++i) {
                vertices[i] = input.readFloat();
                if (!Float.isFinite(vertices[i])) throw new IOException("Invalid tendril vertex");
            }
            int[] indices = new int[1248];
            for (int i = 0; i < indices.length; ++i) {
                indices[i] = input.readUnsignedShort();
                if (indices[i] >= 513) throw new IOException("Invalid tendril face");
            }
            if (input.read() != -1) throw new IOException("Unexpected tendril data");
            return new Mesh(vertices, indices, new float[513 * 6]);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read Tendril_SegmentShape", error);
        }
    }

    static void draw(PoseStack stack, VertexConsumer out, int light, Vec3[] cameraRelativePath,
                     double[] widths, Vec3 baselineNormal, int argb) {
        int count = cameraRelativePath.length;
        if (count < 2 || widths.length != count || (argb >>> 24) == 0) return;
        Spline[] spans = new Spline[count - 1];
        for (int i = 0; i < spans.length; i++) spans[i] = Spline.at(cameraRelativePath, i);
        var mesh = Loaded.MESH;
        for (int i = 0; i + 1 < count; ++i) {
            Vec3 start = cameraRelativePath[i], delta = cameraRelativePath[i + 1].subtract(start);
            if (delta.lengthSqr() < 1e-10 || Math.max(widths[i], widths[i + 1]) < 1e-6) continue;
            deform(mesh, spans[i], i+1 < spans.length ? spans[i+1] : null, baselineNormal,
                    Math.max(0, widths[i]), Math.max(0, widths[i+1]));
            var pose = stack.last();
            for (int face = 0; face < mesh.indices.length; face += 3) {
                int a = mesh.indices[face], b = mesh.indices[face + 1], c = mesh.indices[face + 2];
                vertex(pose, out, mesh, a, light, argb);
                vertex(pose, out, mesh, b, light, argb);
                vertex(pose, out, mesh, c, light, argb);
                vertex(pose, out, mesh, c, light, argb); // QUADS material: the second triangle has zero area.
            }
        }
    }

    // Original char_alex_tendril / lit_PR uses Catmull-Rom over point0..3 and a forward
    // difference in source-Y space to orient the local X/Y/Z binormal/tangent/normal basis.
    private record Spline(Vec3 start, Vec3 linear, Vec3 quadratic, Vec3 cubic) {
        static Spline at(Vec3[] path, int i) {
            Vec3 p1 = path[i], p2 = path[i + 1];
            Vec3 p0 = i > 0 ? path[i - 1] : p1.add(p1.subtract(p2));
            Vec3 p3 = i + 2 < path.length ? path[i + 2] : p2.add(p2.subtract(p1));
            Vec3 linear = p2.subtract(p0).scale(.5);
            Vec3 endTangent = p3.subtract(p1).scale(.5);
            Vec3 delta = p2.subtract(p1);
            return new Spline(p1, linear, delta.scale(3).subtract(linear.scale(2)).subtract(endTangent),
                    linear.add(endTangent).subtract(delta.scale(2)));
        }
    }

    private static void deform(Mesh mesh, Spline span, Spline next, Vec3 backupNormal, double startWidth, double endWidth) {
        final double epsilon = .0001;
        Vec3 a = span.linear, b = span.quadratic, c = span.cubic;
        float[] source = mesh.vertices, shape = mesh.deformed;
        for (int p = 0, q = 0; p < source.length; p += 8, q += 6) {
            double t = source[p + 1];
            double cx = span.start.x + t*(a.x+t*(b.x+t*c.x));
            double cy = span.start.y + t*(a.y+t*(b.y+t*c.y));
            double cz = span.start.z + t*(a.z+t*(b.z+t*c.z));
            // (P(t+epsilon)-P(t))/epsilon, expanded to avoid losing precision. On a shared
            // ring use the outgoing span's forward sample on both sides of the join.
            double u = t;
            Spline tangent = span;
            if (t == 1 && next != null) { u = 0; tangent = next; }
            double k2 = 2*u + epsilon, k3 = 3*u*u + 3*u*epsilon + epsilon*epsilon;
            double tx = tangent.linear.x + k2*tangent.quadratic.x + k3*tangent.cubic.x;
            double ty = tangent.linear.y + k2*tangent.quadratic.y + k3*tangent.cubic.y;
            double tz = tangent.linear.z + k2*tangent.quadratic.z + k3*tangent.cubic.z;
            double tm = Math.sqrt(tx*tx+ty*ty+tz*tz);
            if (tm < 1e-10) { tx = 0; ty = 1; tz = 0; tm = 1; }
            tx/=tm; ty/=tm; tz/=tm;

            // Material upVector is (0,1,0). Only a tangent parallel to it needs an MC fallback.
            double bx = -tz, by = 0, bz = tx;
            if (bx*bx+bz*bz < 1e-10) {
                bx = ty*backupNormal.z-tz*backupNormal.y;
                by = tz*backupNormal.x-tx*backupNormal.z;
                bz = tx*backupNormal.y-ty*backupNormal.x;
                if (bx*bx+by*by+bz*bz < 1e-10) { bx = 0; by = tz; bz = -ty; }
            }
            double bm = Math.sqrt(bx*bx+by*by+bz*bz);
            bx/=bm; by/=bm; bz/=bm;
            double nx = by*tz-bz*ty, ny = bz*tx-bx*tz, nz = bx*ty-by*tx;
            double width = (startWidth+t*(endWidth-startWidth))/SOURCE_RADIUS;
            double x = source[p]*width, z = source[p+2]*width;
            shape[q]   = (float)(cx + bx*x + nx*z);
            shape[q+1] = (float)(cy + by*x + ny*z);
            shape[q+2] = (float)(cz + bz*x + nz*z);

            double sx = source[p+3], sy = source[p+4], sz = source[p+5];
            double ox = bx*sx+tx*sy+nx*sz, oy = by*sx+ty*sy+ny*sz, oz = bz*sx+tz*sy+nz*sz;
            double nm = Math.sqrt(ox*ox+oy*oy+oz*oz);
            shape[q+3] = (float)(ox/nm);
            shape[q+4] = (float)(oy/nm);
            shape[q+5] = (float)(oz/nm);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Mesh mesh, int index, int light, int color) {
        int q = 6 * index, uv = 8 * index + 6;
        float[] v = mesh.deformed;
        out.addVertex(pose, v[q], v[q + 1], v[q + 2]).setColor(color).setUv(mesh.vertices[uv], mesh.vertices[uv + 1])
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, v[q + 3], v[q + 4], v[q + 5]);
    }
}
