package com.codex.blacklight.combat;

import net.minecraft.world.phys.Vec3;

/** P1 object-throw timing; MC distances, gravity and damage are explicit adaptations. */
public final class HydraThrow {
    public static final int PICKUP=12,HOLD=13,THROW=14;
    public static final float ATTACH=54,PICKUP_END=119/1.5F,RELEASE=1.26667F*20,THROW_END=65/1.5F;
    public static final int HOLD_TICKS=20;
    public static final double GRAVITY=.045,SPEED=1.375,MAX_RANGE=32;
    private HydraThrow(){}
    public static boolean action(int a){return a>=PICKUP&&a<=THROW;}
    /** Low ballistic arc. The target is sampled once at release, never homed after release. */
    public static Vec3 velocity(Vec3 from,Vec3 target){
        Vec3 d=target.subtract(from);double horizontal=d.horizontalDistance(),time=Math.max(1,horizontal/SPEED);
        for(int i=0;i<12;i++){
            double vy=d.y/time+GRAVITY*(time-1)*.5;
            double vx=Math.sqrt(Math.max(.04,SPEED*SPEED-vy*vy));time=Math.min(60,Math.max(1,horizontal/vx));
        }
        return new Vec3(d.x/time,d.y/time+GRAVITY*(time-1)*.5,d.z/time);
    }
    public static HydraContact.Box box(Vec3 center,double size,float age){
        double y=Math.toRadians(age*16),x=Math.toRadians(age*11);
        Vec3 right=new Vec3(Math.cos(y),0,-Math.sin(y));
        Vec3 up=new Vec3(Math.sin(y)*Math.sin(x),Math.cos(x),Math.cos(y)*Math.sin(x));
        Vec3 forward=right.cross(up);
        return HydraContact.oriented(center,new Vec3(size*.5,size*.5,size*.5),right,up,forward);
    }
}
