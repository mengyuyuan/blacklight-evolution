package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** A finite recovery interval, expressed as the fraction of the remaining error.
 * Repeated fixed springs never reach the animation before the body is retired. */
final class RagdollRecovery {
    static final double DURATION=16;
    static double progress(double time,double deadline){
        return progress(time,deadline,DURATION);
    }
    static double progress(double time,double deadline,double duration){
        double t=Math.max(0,Math.min(1,(time-deadline+duration)/Math.max(1,duration)));
        return t*t*(3-2*t);
    }
    static float step(double from,double to,double deadline){
        return step(from,to,deadline,DURATION);
    }
    static float step(double from,double to,double deadline,double duration){
        double previous=progress(from,deadline,duration),next=progress(to,deadline,duration);
        if(next>=1)return 1;
        return (float)Math.max(0,(next-previous)/(1-previous));
    }
    /** A completed control packet can precede the client's corrected clock by a tick.
     * Honor that release near the deadline; a genuine early interruption still blends out. */
    static long releaseDeadline(long now,long expected,boolean cleared){
        return Math.min(expected,now+(cleared&&expected-now<=2?0:(long)DURATION));
    }
    static Vec3 localAnchor(Vec3 pivot,Vec3 center,Quaternionf rotation,float scale){
        return new Vec3(new Quaternionf(rotation).conjugate().transform(pivot.subtract(center).scale(1/scale).toVector3f()));
    }
    static double releaseWeight(double age){
        double t=Math.max(0,Math.min(1,age/8));return 1-t*t*(3-2*t);
    }
    private RagdollRecovery(){}
}
