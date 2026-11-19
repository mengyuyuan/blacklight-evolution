package com.codex.blacklight.animation;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Slot zero is the original blade. The same socket transform seats and releases each satellite. */
public final class ScapulaVolley {
    private ScapulaVolley() {}

    public static int pairs(boolean aerialThrow, boolean charged) {
        return aerialThrow ? (charged ? 2 : 1) : 0;
    }

    public static float size(int slot) {
        return switch (Math.abs(slot)) { case 1 -> .78F; case 2 -> .64F; default -> 1F; };
    }

    public static Matrix4f socket(Matrix4f hinge, int slot) {
        return socket(hinge,slot,size(slot));
    }
    static Matrix4f socket(Matrix4f hinge,float slot,float size) {
        float tier=Math.abs(slot);
        return new Matrix4f(hinge).translate(-.13F*tier, .052F*slot, .045F*tier)
                .rotateY(.16F*tier).rotateZ(-.12F*slot).scale(size);
    }

    public static Vec3 aim(Vec3 forward, Vec3 fallbackRight, int slot, boolean charged) {
        if(slot==0)return forward;
        Vec3 right=new Vec3(forward.z,0,-forward.x);
        right=right.lengthSqr()<.0001 ? fallbackRight.normalize() : right.normalize();
        Vec3 up=forward.cross(right).normalize();
        double angle=Math.toRadians(slot*(charged?6.0:12.0));
        return forward.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)))
                .add(up.scale((charged?-.024:.028)*Math.abs(slot))).normalize();
    }

    public static float damage(float base,int slot,int pairs) {
        if(pairs==0)return base;
        return base*(slot==0?.68F:Math.abs(slot)==1?.29F:.21F);
    }

    /** Turn existing horizontal travel a little. Repeated light attacks cannot add altitude or stack dash speed. */
    public static Vec3 bankLight(Vec3 old,Vec3 wish){
        if(wish.horizontalDistanceSqr()<1e-6)return old;
        var horizontal=new Vec3(old.x,0,old.z);
        double speed=horizontal.length();
        var turned=horizontal.scale(.75).add(wish.normalize().scale(.25*Math.max(.19,speed)));
        return new Vec3(turned.x,old.y,turned.z);
    }
    /** Take steering in the scoring frame; with no direction the volley kicks the character backwards. */
    public static Vec3 breakAway(Vec3 old,Vec3 aim,Vec3 wish,boolean charged){
        var back=new Vec3(-aim.x,0,-aim.z);
        var direction=wish.horizontalDistanceSqr()<1e-6?back:wish.normalize();
        double force=charged?.52:.34;
        // Preserve the jump arc. A moving actor spends less of their forward momentum on neutral recoil.
        if(wish.horizontalDistanceSqr()<1e-6)force*=1-.70*ease((float)(old.horizontalDistance()/.55));
        return new Vec3(old.x*.92+direction.x*force,old.y,old.z*.92+direction.z*force);
    }

    public static float ease(float t) {
        t=Math.max(0,Math.min(1,t));
        return t*t*(3-2*t);
    }

    /** The second beat follows the first through each channel, from back root to quill. */
    public static float vascularPulse(double ticks,float distance) {
        double phase=(ticks-3.6*distance)/10;
        phase-=Math.floor(phase);
        double first=(phase-.23)/.105, second=(phase-.54)/.115;
        return (float)Math.min(1,Math.exp(-first*first)+.62*Math.exp(-second*second));
    }

    public record Strain(float load,float kick,float beat,float regrow,float tilt,float inner,float outer) {
        public float blade(int slot) { return Math.abs(slot)==2 ? outer : Math.abs(slot)==1 ? inner : 1; }
    }

    /** Body time may stop at full draw. Circulation continues on the independent material clock. */
    public static Strain strain(String clip,float t,double clock) {
        boolean air=clip.startsWith("spines_air_"), draw=clip.endsWith("_draw");
        boolean charged=clip.endsWith("_charged"), release=charged||clip.endsWith("_throw");
        boolean light=clip.startsWith("spines_light_"), burst=clip.endsWith("_burst");
        float at=air&&release?1:charged?5:release?4:clip.endsWith("_lift")?4:burst?(t<7?4:10):5;
        float since=t-at;
        float kick=release||light ? ease(1+since*3)*(1-ease(since/(release?4.2F:2.6F))) : 0;
        float growth=release||light ? ease((since-.75F)/2)*(1-ease((since-3)/4)) : 0;
        float load=draw ? ease(t/10) : release ? (charged?1F:.72F)*(1-ease(since/2)) :
                light ? .5F*ease((t-(burst&&t>=7?7:0))/(burst?2.5F:3.5F))*(1-ease(since/2)) : 0;
        float beat=(float)(.5+.5*Math.sin(clock*.92));
        float tilt=clip.contains("_return")?-.8F:clip.contains("_sweep")?.9F:
                clip.endsWith("_lift")||burst&&t>=7?1F:burst?-.72F:0;
        float inner=0,outer=0;
        if(air && draw){inner=ease(t/3);outer=ease((t-3)/3);}
        if(air && release && since<0){inner=1;outer=charged?1:0;}
        return new Strain(load,kick,beat,growth,tilt,inner,outer);
    }
}
