package com.codex.blacklight.client.renderer;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Passive velocity correction after positional constraints. A projection may
 * remove motion into a rejected direction, but never turns a terrain lift or
 * joint repair into a launch velocity. This is an MC solver adapter. */
final class RagdollConstraintMotion {
    static final int SLEEP_STEPS=24;
    private static final double STEP=.5,QUIET_SPEED_SQUARED=.002,CORRECTION_EPSILON=1e-5;
    record Link(int parent,int child){}

    static Vec3 linear(Vec3 velocity,Vec3 correction){
        double length=correction.lengthSqr();
        if(length<=CORRECTION_EPSILON*CORRECTION_EPSILON)return velocity;
        double toward=velocity.dot(correction);
        return toward<0?velocity.subtract(correction.scale(toward/length)):velocity;
    }
    static Vector3f angular(Vector3f localSpin,Quaternionf predicted,Quaternionf corrected){
        var delta=new Quaternionf(corrected).mul(new Quaternionf(predicted).conjugate()).normalize();
        // q and -q describe the same correction; choose its shortest direction.
        if(delta.w<0)delta.set(-delta.x,-delta.y,-delta.z,-delta.w);
        var world=new Vec3(predicted.transform(new Vector3f(localSpin)));
        var remaining=linear(world,new Vec3(delta.x,delta.y,delta.z));
        return new Quaternionf(corrected).conjugate().transform(remaining.toVector3f());
    }
    static int still(int previous,boolean supported,Vec3 displacement,Quaternionf before,Quaternionf after,
                     Vec3 velocity,Vector3f spin){
        var delta=new Quaternionf(after).mul(new Quaternionf(before).conjugate()).normalize();
        double angle=2*Math.atan2(Math.sqrt((double)delta.x*delta.x+(double)delta.y*delta.y+(double)delta.z*delta.z),Math.abs(delta.w));
        // The previous speed thresholds are retained, including their half-tick
        // displacement equivalents. Check the completed pose, not just a limb's
        // own floor collision; a head supported by its torso can also settle.
        boolean quiet=velocity.lengthSqr()<QUIET_SPEED_SQUARED&&spin.lengthSquared()<QUIET_SPEED_SQUARED
                &&displacement.lengthSqr()<QUIET_SPEED_SQUARED*STEP*STEP&&angle*angle<QUIET_SPEED_SQUARED*STEP*STEP;
        return supported&&quiet?Math.min(SLEEP_STEPS,previous+1):0;
    }
    static boolean[] supported(boolean[] contacts,List<Link> links){
        boolean[] supported=contacts.clone();boolean changed;
        do{
            changed=false;
            for(var link:links)if(supported[link.parent]!=supported[link.child]){
                supported[link.parent]=supported[link.child]=true;changed=true;
            }
        }while(changed);
        return supported;
    }
    private RagdollConstraintMotion(){}
}
