package com.codex.blacklight.client.renderer;

import com.codex.blacklight.infection.InfectedAttackStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Authored translucent geometry inspired by P1's directional blood and spark sprites.
 * Source emission windows are seconds; speed, droplet life and dimensions below are
 * Minecraft presentation choices, not a claim to decode P1's particle time units.
 * No source atlas, emissive pass, render-frame state or simulation-side particles. */
public final class ImpactSpray {
    private static final double BLOOD_EMISSION = .16666699945926666;
    private static final double HUNTER_EMISSION = .1333329975605011;
    private static final double FAN_EMISSION = .33333298563957214;
    private ImpactSpray() {}

    public static void flesh(PoseStack pose, VertexConsumer out, Vec3 origin, Vec3 normal,
                             Vec3 camera, InfectedAttackStyle style, double ageTicks,
                             float size, long seed, boolean hunter, int light) {
        if (!Double.isFinite(ageTicks) || ageTicks < 0 || ageTicks > 14) return;
        double age = ageTicks / 20, scale = scale(size);
        var axis = direction(normal, new Vec3(0, 1, 0));
        var u = tangent(axis);
        var v = axis.cross(u);
        boolean heavy = style == InfectedAttackStyle.CRUSH || style == InfectedAttackStyle.BURST;
        int drops = heavy ? 21 : style == InfectedAttackStyle.PIERCE ? 10 : 16;
        for (int i = 0; i < drops; i++) {
            long s = seed + i * 53L;
            double delay = i == 0 ? 0 : unit(s + 1) * BLOOD_EMISSION;
            double t = age - delay, life = .20 + unit(s + 2) * .21;
            if (t < 0 || t >= life) continue;
            double angle = unit(s + 3) * Math.PI * 2;
            var radial = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
            double spread = style == InfectedAttackStyle.PIERCE ? .24 : .52;
            var velocity = axis.scale((1.5 + unit(s + 4) * 2.7) * scale)
                    .add(radial.scale(spread * (.3 + unit(s + 5)) * scale));
            var point = origin.add(radial.scale(.012 * scale))
                    .add(velocity.scale(t)).add(0, -.5 * 7.5 * t * t, 0);
            var travel = velocity.add(0, -7.5 * t, 0);
            double fade = fade(t, life), width = (.014 + unit(s + 6) * .016) * scale;
            double length=width*(1.8+unit(s+7)*2.1);
            int color = i % 3 == 0 ? 0x480910 : i % 3 == 1 ? 0x9a1b22 : 0x701017;
            soft(pose, out, camera, point, travel, length,
                    width, color, 220 * fade, light, s, false);
            // Small wet highlight within selected droplets, lit by the world.
            // This is a surface glint, not a white flash over the receiving body.
            if(i%3==1)soft(pose,out,camera,point.add(u.scale(width*.28)),travel,
                    length*.42,width*.20,0xd1927b,155*fade,light,s,false);
        }
        // Short torn fluid strands establish the instant of contact before the
        // separated drops fan out. Their thin tails disappear before the spray.
        if(style!=InfectedAttackStyle.PIERCE)for(int i=0;i<(heavy?5:3);i++){
            long s=seed+401+i*29L;double t=age-i*.012,life=.13+unit(s)*.07;
            if(t<0||t>=life)continue;
            double a=unit(s+1)*Math.PI*2;
            var travel=axis.scale(2.4).add(u.scale(Math.cos(a)*.8)).add(v.scale(Math.sin(a)*.8));
            var point=origin.add(travel.scale((t+.012)*scale)).add(0,-3*t*t,0);
            double width=(.018+unit(s+2)*.014)*scale, length=(.10+t*.55)*scale;
            soft(pose,out,camera,point,travel,length,width,0x81121a,205*fade(t,life),light,s,false);
        }
        // The source Hunter branch adds Gush003, separate from its ordinary Gush001.
        if (hunter) for (int i = 0; i < 5; i++) {
            long s = seed + 701 + i * 37L;
            double t = age - unit(s) * HUNTER_EMISSION, life = .19 + unit(s + 1) * .10;
            if (t < 0 || t >= life) continue;
            double a = unit(s + 2) * Math.PI * 2;
            var velocity = axis.scale((1.6 + unit(s + 3)) * scale)
                    .add(u.scale(Math.cos(a) * .65 * scale)).add(v.scale(Math.sin(a) * .65 * scale));
            var point = origin.add(velocity.scale(t)).add(0, -3.75 * t * t, 0);
            soft(pose, out, camera, point, velocity.add(0, -7.5 * t, 0), .055 * scale,
                    .012 * scale, 0x6b1017, 170 * fade(t, life), light, s, false);
        }
        // Sparse low-opacity mist; only heavy impacts get the extended fan window.
        int clouds = heavy ? 4 : 1;
        for (int i = 0; i < clouds; i++) {
            long s = seed + 1301 + i * 61L;
            double delay = heavy ? unit(s) * FAN_EMISSION : .025;
            double t = age - delay, life = heavy ? .25 : .19;
            if (t < 0 || t >= life) continue;
            double a = unit(s + 1) * Math.PI * 2;
            var drift = axis.scale(.5 * scale).add(u.scale(Math.cos(a) * .28 * scale))
                    .add(v.scale(Math.sin(a) * .28 * scale));
            var point = origin.add(drift.scale(t)).add(0, -.8 * t * t, 0);
            double radius = (.09 + t * .70) * scale * (heavy ? 1.5 : 1);
            double envelope = Math.sin(Math.PI * t / life);
            soft(pose, out, camera, point, u, radius * 1.4, radius,
                    0x76141d, (heavy ? 45 : 30) * envelope, light, s, true);
        }
    }

    /** Small warm streaks for an explicitly metallic or blocked contact. */
    public static void sparks(PoseStack pose, VertexConsumer out, Vec3 origin, Vec3 normal,
                              Vec3 camera, double ageTicks, float size, long seed, int light) {
        if (!Double.isFinite(ageTicks) || ageTicks < 0 || ageTicks > 7) return;
        double age = ageTicks / 20, scale = Math.min(1.2, scale(size));
        // Hot metallic sparks emit light; flesh above always uses scene light.
        light=net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        var axis = direction(normal, new Vec3(0, 1, 0));
        var u = tangent(axis);
        var v = axis.cross(u);
        for (int i = 0; i < 11; i++) {
            long s = seed + i * 43L;
            double t = age - (i == 0 ? 0 : unit(s) * .035), life = .09 + unit(s + 1) * .22;
            if (t < 0 || t >= life) continue;
            double a = unit(s + 2) * Math.PI * 2, spread = .35 + unit(s + 3) * .8;
            var velocity = axis.scale((1.6 + unit(s + 4) * 3.2) * scale)
                    .add(u.scale(Math.cos(a) * spread * scale)).add(v.scale(Math.sin(a) * spread * scale));
            var point = origin.add(velocity.scale(t)).add(0, -4 * t * t, 0);
            int color = i % 3 == 0 ? 0xe9ac52 : i % 3 == 1 ? 0xbf6021 : 0xe58c37;
            soft(pose, out, camera, point, velocity.add(0, -8 * t, 0),
                    (.034 + unit(s + 5) * .075) * scale, (.004 + unit(s + 6) * .0045) * scale,
                    color, 220 * fade(t, life), light, s, false);
        }
    }

    /** A tapered soft silhouette: inner coverage falls to zero at its outer edge.
     * Vertex alpha supplies the feathering; the shared white texture carries no art. */
    private static void soft(PoseStack pose, VertexConsumer out, Vec3 camera, Vec3 center,
                             Vec3 travel, double length, double width, int rgb, double opacity,
                             int light, long seed, boolean cloud) {
        if (opacity < 1 || width <= 0 || length <= 0) return;
        var facing = direction(camera.subtract(center), new Vec3(0, 0, 1));
        var across = travel.subtract(facing.scale(travel.dot(facing)));
        across = direction(across, tangent(facing));
        var side = facing.cross(across);
        int alpha = (int) Math.min(255, opacity), innerAlpha = (int) (alpha * .55);
        int sectors = 8;
        for (int i = 0; i < sectors; i++) {
            double a = i * Math.PI * 2 / sectors, b = (i + 1) * Math.PI * 2 / sectors;
            var ra = rim(across, side, a, length, width, seed, i, cloud);
            var rb = rim(across, side, b, length, width, seed, (i + 1) % sectors, cloud);
            var ia = center.add(ra.scale(.42));
            var ib = center.add(rb.scale(.42));
            vertex(pose, out, center, facing, rgb, alpha, light);
            vertex(pose, out, ia, facing, rgb, innerAlpha, light);
            vertex(pose, out, ib, facing, rgb, innerAlpha, light);
            vertex(pose, out, center, facing, rgb, alpha, light);
            vertex(pose, out, ia, facing, rgb, innerAlpha, light);
            vertex(pose, out, center.add(ra), facing, rgb, 0, light);
            vertex(pose, out, center.add(rb), facing, rgb, 0, light);
            vertex(pose, out, ib, facing, rgb, innerAlpha, light);
        }
    }

    private static Vec3 rim(Vec3 along, Vec3 side, double angle, double length, double width,
                            long seed, int index, boolean cloud) {
        double x = Math.cos(angle), y = Math.sin(angle);
        double irregular = cloud ? .78 + unit(seed + index * 17L) * .36 : 1;
        // Uneven tail/head length avoids a rectangular streak or perfect circular blob.
        return along.scale(x * length * (x < 0 ? 1.25 : .7) * irregular)
                .add(side.scale(y * width * irregular));
    }

    private static void vertex(PoseStack pose, VertexConsumer out, Vec3 p, Vec3 normal,
                               int rgb, int alpha, int light) {
        out.addVertex(pose.last(), (float) p.x, (float) p.y, (float) p.z)
                .setColor((alpha << 24) | rgb).setUv(.5F, .5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static Vec3 direction(Vec3 v, Vec3 fallback) {
        return Double.isFinite(v.lengthSqr()) && v.lengthSqr() > 1.0e-8 ? v.normalize() : fallback;
    }
    private static Vec3 tangent(Vec3 axis) {
        return direction(axis.cross(Math.abs(axis.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)), new Vec3(1, 0, 0));
    }
    // Incoming size is weapon strength, not a world-space radius. Previously a
    // normal .5-strength hit halved already centimetre-sized droplets.
    private static double scale(float size) { return Float.isFinite(size) ? .45+Math.max(0,Math.min(2.4,size))*.8 : .45; }
    private static double fade(double age, double life) {
        double t = Math.max(0, Math.min(1, (age / life - .25) / .75));
        return 1 - t * t * (3 - 2 * t);
    }
    private static double unit(long seed) {
        seed = (seed ^ (seed >>> 30)) * 0xbf58476d1ce4e5b9L;
        seed = (seed ^ (seed >>> 27)) * 0x94d049bb133111ebL;
        return ((seed ^ (seed >>> 31)) >>> 11) * 0x1.0p-53;
    }
}
