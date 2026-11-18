package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** An anatomical X hinge in the captured bones' local frame, not world axes.
 * Part rotations are deltas from capture; that capture can already be bent. */
final class RagdollHinge {
    private final Quaternionf parentBind,childInverse;
    private final float minimum,maximum;
    RagdollHinge(Quaternionf parentBind,Quaternionf childBind,float minimum,float maximum){
        this.parentBind=new Quaternionf(parentBind).normalize();
        this.childInverse=new Quaternionf(childBind).normalize().conjugate();
        this.minimum=minimum;this.maximum=maximum;
    }
    private Quaternionf parentWorld(Quaternionf delta){return new Quaternionf(delta).mul(parentBind).normalize();}
    private static double wrapped(double angle){return Math.atan2(Math.sin(angle),Math.cos(angle));}
    private float angle(Quaternionf relative){
        if(relative.x*relative.x+relative.w*relative.w<1e-12)return 0;
        return (float)wrapped(2*Math.atan2(relative.x,relative.w));
    }
    Quaternionf constrain(Quaternionf parentDelta,Quaternionf childDelta){
        var parent=parentWorld(parentDelta);
        var relative=new Quaternionf(parent).conjugate().mul(childDelta).mul(new Quaternionf(childInverse).conjugate()).normalize();
        float angle=angle(relative);
        if(angle<minimum||angle>maximum){
            // Angles wrap at pi. Choose the nearer endpoint of the permitted arc.
            angle=Math.abs(wrapped(angle-minimum))<Math.abs(wrapped(angle-maximum))?minimum:maximum;
        }
        return parent.rotateX(angle).mul(childInverse).normalize();
    }
    Vector3f constrainSpin(Quaternionf parentDelta,Quaternionf childDelta,Vector3f parentSpin,Vector3f childSpin){
        var parent=parentWorld(parentDelta);
        var relative=new Quaternionf(parent).conjugate().mul(childDelta).mul(new Quaternionf(childInverse).conjugate()).normalize();
        var axis=parent.transform(new Vector3f(1,0,0));
        float speed=new Vector3f(childSpin).sub(parentSpin).dot(axis),angle=angle(relative);
        if(angle<=minimum+1e-4F&&speed<0||angle>=maximum-1e-4F&&speed>0)speed=0;
        return new Vector3f(parentSpin).fma(speed,axis);
    }
    static Vec3 pivotShift(Vec3 localAnchor,Quaternionf before,Quaternionf after,float scale){
        var anchor=localAnchor.scale(scale).toVector3f();
        return new Vec3(before.transform(new Vector3f(anchor)).sub(after.transform(new Vector3f(anchor))));
    }
}
