package com.codex.blacklight.military.aircraft;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Shared rendered mount/shot geometry, including the fuselage's bank. Angles are degrees. */
public final class HelicopterArmament {
    public static final Vec3 GUN=new Vec3(0,1,3.6);
    public record Aim(float yaw,float pitch){}
    public static Vec3 pod(int side){return new Vec3(side*1.9,1.4,1.3);}
    public static Vec3 tube(int index){return new Vec3(index%2==0?-.14:.14,index<2?-.14:.14,.95);}
    public static Vec3 mountOffset(Vec3 offset,float yaw,float pitch){
        double p=Math.toRadians(pitch),y=offset.y*Math.cos(p)-offset.z*Math.sin(p),z=offset.y*Math.sin(p)+offset.z*Math.cos(p),a=Math.toRadians(yaw);
        return new Vec3(offset.x*Math.cos(a)+z*Math.sin(a),y,z*Math.cos(a)-offset.x*Math.sin(a));
    }
    public static Vec3 direction(float yaw,float pitch){
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        return new Vec3(Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p));
    }
    public static Vec3 rotate(Vec3 local,float yaw,float bank){
        double b=Math.toRadians(bank),c=Math.cos(b),s=Math.sin(b);
        double x=local.x*c-local.y*s,y=local.x*s+local.y*c,a=Math.toRadians(yaw);
        return new Vec3(x*Math.cos(a)+local.z*Math.sin(a),y,local.z*Math.cos(a)-x*Math.sin(a));
    }
    public static Aim aim(Vec3 direction,float yaw,float bank){
        double a=Math.toRadians(yaw);
        Vec3 local=new Vec3(direction.x*Math.cos(a)-direction.z*Math.sin(a),direction.y,direction.z*Math.cos(a)+direction.x*Math.sin(a));
        double b=Math.toRadians(bank),c=Math.cos(b),s=Math.sin(b);
        local=new Vec3(local.x*c+local.y*s,-local.x*s+local.y*c,local.z);
        return new Aim((float)Math.toDegrees(Math.atan2(local.x,local.z)),(float)-Math.toDegrees(Math.atan2(local.y,local.horizontalDistance())));
    }
    public static Aim limit(Aim aim){return new Aim(Mth.clamp(aim.yaw,-55,55),Mth.clamp(aim.pitch,-5,65));}
    public static float approach(float current,float target){return current+Mth.clamp(Mth.wrapDegrees(target-current),-2.5F,2.5F);}
    private HelicopterArmament(){}
}
