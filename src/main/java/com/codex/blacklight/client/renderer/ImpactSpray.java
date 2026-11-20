package com.codex.blacklight.client.renderer;

import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.combat.FleshStrike;
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
        flesh(pose,out,new SprayEmitterPath(origin,normal,false),camera,style,FleshStrike.GENERIC,
                false,ageTicks,size,seed,hunter,light);
    }

    // Dimensions are tuned in blocks. The lash is a narrow streak along its sweep,
    // the hammer spreads sideways and an ordinary punch stays close to contact.
    private record Shape(int drops,int strands,double spread,double speed,double width,double stretch,double window) {}
    private static final Shape FIST=new Shape(10,2,.88,1.12,1.22,1.12,.36);
    private static final Shape FIST_FINISH=new Shape(14,3,1.25,1.43,1.38,1.50,.52);
    private static final Shape RUPTURE=new Shape(19,4,1.45,1.55,1.48,1.20,.63);
    private static final Shape LASH=new Shape(15,3,.26,1.45,.86,2.05,.72);
    private static final Shape HAMMER=new Shape(20,4,2.65,.90,1.58,.94,.75);
    private static final Shape MUSCLE=new Shape(16,3,.72,1.68,1.28,1.36,.85);
    private static final Shape CUT=new Shape(17,3,.39,1.32,1.08,1.62,.85);
    private static final Shape RAKE=new Shape(11,3,.35,1.13,.87,1.81,.62);
    private static final Shape PUNCTURE=new Shape(10,0,.24,1,.9,1.25,1);
    private static final Shape CRUSH=new Shape(21,5,.52,1,1,1,1);
    private static Shape shape(FleshStrike strike,InfectedAttackStyle style) {
        if(style==InfectedAttackStyle.PIERCE)return PUNCTURE;
        return switch(strike){
            case EDGE -> CUT;
            case CLAW -> RAKE;
            case QUILL -> PUNCTURE;
            case FIST -> FIST;
            case LASH -> LASH;
            case HAMMER -> HAMMER;
            case MUSCLE -> MUSCLE;
            default -> style==InfectedAttackStyle.CRUSH||style==InfectedAttackStyle.BURST?CRUSH:CUT;
        };
    }

    static void flesh(PoseStack pose,VertexConsumer out,SprayEmitterPath path,Vec3 camera,
                      InfectedAttackStyle style,FleshStrike strike,boolean charged,double ageTicks,
                      float size,long seed,boolean hunter,int light){
        if (!Double.isFinite(ageTicks) || ageTicks < 0 || ageTicks > 14) return;
        double age = ageTicks / 20, scale = scale(size)*(charged?1.16:1);
        boolean rupture=strike==FleshStrike.MUSCLE && style==InfectedAttackStyle.BURST;
        var shape=rupture ? RUPTURE : strike==FleshStrike.FIST && (size>=.78F || charged) ? FIST_FINISH : shape(strike,style);
        int drops=strike==FleshStrike.QUILL && size<.4F ? 4 : shape.drops+(charged?4:0);
        for (int i = 0; i < drops; i++) {
            long s = seed + i * 53L;
            double delay = i == 0 ? 0 : unit(s + 1) * BLOOD_EMISSION * shape.window;
            // Put a small leading group on the contact frame. The rest follows
            // the moving wound across its emission window.
            if(strike!=FleshStrike.GENERIC&&i<3)delay=0;
            double t = age - delay, life = .20 + unit(s + 2) * .21;
            if (t < 0 || t >= life) continue;
            var frame=path.at(delay);
            var axis=frame.axis();var u=frame.tangent();var v=frame.bitangent();
            double angle = unit(s + 3) * Math.PI * 2;
            var radial = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
            double spread = shape.spread;
            var velocity = axis.scale((1.5 + unit(s + 4) * 2.7) * scale * shape.speed)
                    .add(radial.scale(spread * (.3 + unit(s + 5)) * scale));
            if(strike==FleshStrike.HAMMER && style==InfectedAttackStyle.BURST)
                velocity=radial.scale((1.8+2.6*unit(s+4))*scale).add(axis.scale(.25+.48*unit(s+5)));
            var point = frame.point().add(radial.scale(.012 * scale))
                    .add(velocity.scale(t)).add(0, -.5 * 7.5 * t * t, 0);
            var travel = velocity.add(0, -7.5 * t, 0);
            double fade = fade(t, life), width = (.014 + unit(s + 6) * .016) * scale * shape.width;
            double length=width*(1.8+unit(s+7)*2.1)*shape.stretch;
            int color = i % 3 == 0 ? 0x480910 : i % 3 == 1 ? 0xb32830 : 0x861520;
            soft(pose, out, camera, point, travel, length,
                    width, color, 220 * fade, light, s, false);
            // Small wet highlight within selected droplets, lit by the world.
            // This is a surface glint, not a white flash over the receiving body.
            if(i%3==1)soft(pose,out,camera,point.add(u.scale(width*.28)),travel,
                    length*.42,width*.20,0xd1927b,155*fade,light,s,false);
        }
        // Short torn fluid strands establish the instant of contact before the
        // separated drops fan out. Their thin tails disappear before the spray.
        for(int i=0;i<shape.strands;i++){
            long s=seed+401+i*29L;double t=age-i*.012,life=.13+unit(s)*.07;
            if(t<0||t>=life)continue;
            var frame=path.at(i*.012);
            var axis=frame.axis();var u=frame.tangent();var v=frame.bitangent();
            double a=unit(s+1)*Math.PI*2;
            var travel=axis.scale(2.4*shape.speed).add(u.scale(Math.cos(a)*shape.spread)).add(v.scale(Math.sin(a)*shape.spread));
            var point=frame.point().add(travel.scale((t+.012)*scale)).add(0,-3*t*t,0);
            double width=(.018+unit(s+2)*.014)*scale*shape.width, length=(.10+t*.55)*scale*shape.stretch;
            soft(pose,out,camera,point,travel,length,width,0x81121a,205*fade(t,life),light,s,false);
        }
        // The source Hunter branch adds Gush003, separate from its ordinary Gush001.
        boolean hunterCut=hunter&&style==InfectedAttackStyle.TEAR;
        if (hunterCut||hunter&&strike==FleshStrike.GENERIC) for (int i = 0; i < 5; i++) {
            long s = seed + 701 + i * 37L;
            double delay=unit(s)*HUNTER_EMISSION;
            double t = age - delay, life = .19 + unit(s + 1) * .10;
            if (t < 0 || t >= life) continue;
            var frame=path.at(delay);
            var axis=frame.axis();var u=frame.tangent();var v=frame.bitangent();
            double a = unit(s + 2) * Math.PI * 2;
            var velocity = axis.scale((1.6 + unit(s + 3)) * scale)
                    .add(u.scale(Math.cos(a) * .65 * scale)).add(v.scale(Math.sin(a) * .65 * scale));
            var point = frame.point().add(velocity.scale(t)).add(0, -3.75 * t * t, 0);
            soft(pose, out, camera, point, velocity.add(0, -7.5 * t, 0), .055 * scale,
                    .012 * scale, 0x6b1017, 170 * fade(t, life), light, s, false);
        }
        // Full-charge slashes against a Hunter add the longer fan to the player's hit.
        boolean fan=hunterCut&&charged || strike==FleshStrike.GENERIC&&(style==InfectedAttackStyle.CRUSH||style==InfectedAttackStyle.BURST);
        int clouds = rupture ? 5 : fan ? 4 : 1;
        for (int i = 0; i < clouds; i++) {
            long s = seed + 1301 + i * 61L;
            double delay = rupture ? i*.012 : fan ? unit(s) * FAN_EMISSION : .025;
            double t = age - delay, life = rupture ? .29 : fan ? .25 : .19;
            if (t < 0 || t >= life) continue;
            var frame=path.at(delay);
            var axis=frame.axis();var u=frame.tangent();var v=frame.bitangent();
            double a = unit(s + 1) * Math.PI * 2;
            var drift = axis.scale(.5 * scale).add(u.scale(Math.cos(a) * .28 * scale))
                    .add(v.scale(Math.sin(a) * .28 * scale));
            if(rupture)drift=axis.scale(.9+i*.37).add(u.scale(Math.cos(a)*.65)).add(v.scale(Math.sin(a)*.65));
            var point = frame.point().add(drift.scale(t)).add(0, -.8 * t * t, 0);
            double radius = (.09 + t * .70) * scale * (rupture ? 1.75 : fan ? 1.5 : 1);
            double envelope = Math.sin(Math.PI * t / life);
            soft(pose, out, camera, point, u, radius * 1.4, radius,
                    0x76141d, (rupture ? 110 : fan ? 45 : 30) * envelope, light, s, true);
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
        int alpha = (int) Math.min(255, opacity);
        int innerAlpha = (int) (alpha * (cloud ? .55 : .98));
        double core = cloud ? .42 : .82;
        int sectors = 8;
        for (int i = 0; i < sectors; i++) {
            double a = i * Math.PI * 2 / sectors, b = (i + 1) * Math.PI * 2 / sectors;
            var ra = rim(across, side, a, length, width, seed, i, cloud);
            var rb = rim(across, side, b, length, width, seed, (i + 1) % sectors, cloud);
            var ia = center.add(ra.scale(core));
            var ib = center.add(rb.scale(core));
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
        out.vertex(pose.last().pose(), (float) p.x, (float) p.y, (float) p.z)
                .color((alpha << 24) | rgb).uv(.5F, .5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.last().normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
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
