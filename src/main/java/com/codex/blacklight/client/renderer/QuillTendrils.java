package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.QuillOrgan;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The exposed loop and the tract beneath the skin meet at the same puncture. Coordinates are relative to the host's feet. */
final class QuillTendrils {
    static final int COUNT = 7;
    static final double HOOK = .24, CONTACT = .40, CORE = .59, EXIT = .79;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    record Strand(Vec3 root, Vec3 arch, Vec3 hook, Vec3 puncture, Vec3 buried, Vec3 exit, Vec3 tip) {
        Vec3 point(double u, double tension) {
            Vec3 elbow = arch.lerp(root.lerp(puncture, .38), .70 * tension);
            Vec3 barb = hook.lerp(root.lerp(puncture, .78), .72 * tension);
            if (u <= HOOK) {
                double t = u / HOOK;
                return root.lerp(elbow, t).lerp(elbow.lerp(barb, t), t);
            }
            if (u <= CONTACT) return barb.lerp(puncture, (u - HOOK) / (CONTACT - HOOK));
            if(u<=CORE)return puncture.lerp(buried,(u-CONTACT)/(CORE-CONTACT));
            if(u<=EXIT)return buried.lerp(exit,(u-CORE)/(EXIT-CORE));
            return exit.lerp(tip,(u-EXIT)/(1-EXIT));
        }
    }

    final Strand[] strands = new Strand[COUNT];
    final double girth;
    final Vec3 wound;
    private static final java.util.Map<QuillOrgan,QuillTendrils> FITTED=new java.util.WeakHashMap<>();

    static QuillTendrils at(QuillOrgan organ,net.minecraft.world.entity.LivingEntity host,Vec3 wound){
        var fitted=FITTED.get(organ);
        return fitted!=null?fitted:new QuillTendrils(wound,host.getBbWidth(),host.getBbHeight(),host.yBodyRot,organ.offset());
    }

    /** Fit the exits once against the posed skin before the physical body breaks apart. */
    static void fit(QuillOrgan organ,net.minecraft.world.entity.LivingEntity host,Vec3 origin,
                    java.util.List<AdaptiveRagdoll.Vertex> skin,float partial){
        if(organ==null || FITTED.containsKey(organ))return;
        var surface=QuillSkinContact.lookup(host,organ);
        var wound=(surface==null?organ.getPosition(partial):surface.point()).subtract(origin);
        var fan=new QuillTendrils(wound,host.getBbWidth(),host.getBbHeight(),host.yBodyRot,organ.offset());
        for(int i=0;i<COUNT;i++){
            var s=fan.strands[i];
            Vec3 dir=s.exit.subtract(s.buried).normalize();
            Vec3 from=s.buried.add(origin);
            double depth=0;
            for(int j=0;j+3<skin.size();j+=4){
                var a=skin.get(j).p;var b=skin.get(j+1).p;var c=skin.get(j+2).p;var d=skin.get(j+3).p;
                double t=Math.min(QuillWounds.ray(from,dir,a,b,c),QuillWounds.ray(from,dir,a,c,d));
                if(Double.isFinite(t))depth=Math.max(depth,t);
            }
            if(depth>.01){
                Vec3 exit=s.buried.add(dir.scale(depth));
                fan.strands[i]=new Strand(s.root,s.arch,s.hook,s.puncture,s.buried,exit,exit.add(s.tip.subtract(s.exit)));
            }
        }
        FITTED.put(organ,fan);
    }

    QuillTendrils(Vec3 wound, double width, double height, double yaw, double seed) {
        this.wound=wound;
        width = Math.max(.10, width);
        height = Math.max(.10, height);
        Vec3 center = new Vec3(0, height * .53, 0);
        double rx = width * .43, ry = height * .38;
        Vec3 direction = wound.subtract(center).multiply(1 / rx, 1 / ry, 1 / rx).normalize();
        if (direction.lengthSqr() < .1) direction = Vec3.directionFromRotation(0, (float) yaw);
        Vec3 axis = new Vec3(direction.x / rx, direction.y / ry, direction.z / rx).normalize();
        Vec3 across = (Math.abs(direction.y) > .9 ? new Vec3(1, 0, 0) : UP).cross(direction).normalize();
        Vec3 along = direction.cross(across);
        double length = Mth.clamp(Math.sqrt(width * height) * .62, .11, 1.05);
        girth = Mth.clamp(length * .120, .012, .095);
        for (int i = 0; i < COUNT; i++) {
            double angle = seed + i * 2 * Math.PI / COUNT;
            Vec3 fan = across.scale(Math.cos(angle)).add(along.scale(Math.sin(angle)));
            Vec3 target = direction.scale(.78).add(fan.scale(.91)).normalize();
            Vec3 puncture = center.add(target.multiply(rx, ry, rx));
            Vec3 buried = center.add(target.scale(.33).add(fan.scale(.10)).multiply(rx, ry, rx));
            Vec3 root = wound.add(axis.scale(.09 * length)).add(fan.scale(.085 * length));
            Vec3 arch = wound.add(axis.scale(.68 * length)).add(fan.scale(.44 * length));
            Vec3 hook = puncture.add(axis.scale(.39 * length)).add(fan.scale(.38 * length));
            Vec3 far=direction.scale(-1).add(fan.scale(.52+.12*(i%3))).normalize();
            Vec3 exit=center.add(far.multiply(width*.55,height*.53,width*.55));
            Vec3 tip=exit.add(exit.subtract(buried).normalize().scale(length*(.62+.16*(i%3))));
            strands[i] = new Strand(root, arch, hook, puncture, buried, exit, tip);
        }
    }

    static double emergence(double age, int slot) {
        double t = age - .11 * (slot % 3);
        if(t<1.8)return HOOK*ease(t/1.8);
        if(t<3)return HOOK+(CONTACT-HOOK)*ease((t-1.8)/1.2);
        if(t<5.3)return CONTACT+(1-CONTACT)*ease((t-3)/2.3);
        return 1-ease((t-8.2)/3.2);
    }

    static double ease(double t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
