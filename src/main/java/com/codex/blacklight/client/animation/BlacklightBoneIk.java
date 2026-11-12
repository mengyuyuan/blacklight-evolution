package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.BlacklightSkeleton;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Original constrained CCD solver. Joint lengths and the shared bind rig remain immutable. */
public final class BlacklightBoneIk {
    private BlacklightBoneIk() { }
    /** Exact two-segment contact. Preserve the authored elbow side and both bone lengths. */
    public static void reachTwoBone(BlacklightSkeleton skeleton,BlacklightPose pose,String side,Vector3f target,Vector3f palmLocal){
        var upper=skeleton.searchJointByName("Arm_"+side);var lower=skeleton.searchJointByName("Hand_"+side);var tip=skeleton.searchJointByName("Tool_"+side);
        if(upper==null||lower==null||tip==null)return;
        Vector3f shoulder=skeleton.getBoundTransformFor(pose,upper).getTranslation(new Vector3f());
        Vector3f elbow=skeleton.getBoundTransformFor(pose,lower).getTranslation(new Vector3f());
        Vector3f end=skeleton.getBoundTransformFor(pose,tip).transformPosition(new Vector3f(palmLocal));
        float a=shoulder.distance(elbow),b=elbow.distance(end),distance=shoulder.distance(target);
        if(!Float.isFinite(a+b+distance)||a<1e-5F||b<1e-5F||distance<1e-5F)return;
        Vector3f forward=target.sub(shoulder,new Vector3f()).normalize();
        float reach=Math.clamp(distance,Math.abs(a-b)+1e-5F,a+b-1e-5F);
        float along=(a*a-b*b+reach*reach)/(2*reach),height=(float)Math.sqrt(Math.max(0,a*a-along*along));
        Vector3f bend=elbow.sub(shoulder,new Vector3f());bend.fma(-bend.dot(forward),forward);
        if(bend.lengthSquared()<1e-8F){bend.set(Math.abs(forward.y)<.9F?new Vector3f(0,1,0):new Vector3f(1,0,0));bend.fma(-bend.dot(forward),forward);}
        bend.normalize();
        Vector3f desiredElbow=new Vector3f(shoulder).fma(along,forward).fma(height,bend);
        turn(skeleton,pose,upper,new Quaternionf().rotationTo(elbow.sub(shoulder,new Vector3f()).normalize(),desiredElbow.sub(shoulder,new Vector3f()).normalize()));
        elbow=skeleton.getBoundTransformFor(pose,lower).getTranslation(new Vector3f());
        end=skeleton.getBoundTransformFor(pose,tip).transformPosition(new Vector3f(palmLocal));
        Vector3f clamped=new Vector3f(shoulder).fma(reach,forward);
        turn(skeleton,pose,lower,new Quaternionf().rotationTo(end.sub(elbow,new Vector3f()).normalize(),clamped.sub(elbow,new Vector3f()).normalize()));
    }
    private static void turn(BlacklightSkeleton skeleton,BlacklightPose pose,BlacklightSkeleton.Bone bone,Quaternionf rotation){
        var local=pose.orElseEmpty(bone.getName()).rotation();
        var world=skeleton.getBoundTransformFor(pose,bone).getUnnormalizedRotation(new Quaternionf()).normalize();
        var basis=new Quaternionf(world).mul(new Quaternionf(local).invert());
        local.premul(new Quaternionf(basis).invert().mul(rotation).mul(basis)).normalize();
    }
    public static void reach(BlacklightSkeleton skeleton, BlacklightPose pose, String side,
                             Vector3f target, Vector3f palmLocal, int iterations) {
        var wrist = skeleton.searchJointByName("Tool_" + side);
        if (wrist == null) return;
        for (int i = 0; i < iterations; i++) {
            for (String name : new String[]{"Hand_" + side, "Arm_" + side}) {
                var bone = skeleton.searchJointByName(name);
                if (bone == null) continue;
                Matrix4f world = skeleton.getBoundTransformFor(pose, bone);
                Vector3f center = world.getTranslation(new Vector3f());
                Vector3f palm = skeleton.getBoundTransformFor(pose, wrist).transformPosition(new Vector3f(palmLocal));
                Vector3f current = palm.sub(center, new Vector3f());
                Vector3f desired = target.sub(center, new Vector3f());
                if (current.lengthSquared() < 1e-8F || desired.lengthSquared() < 1e-8F) continue;
                Quaternionf turn = new Quaternionf().rotationTo(current.normalize(), desired.normalize());
                // Damp large corrections to avoid elbow jitter when the target crosses the shoulder.
                turn.set(new Quaternionf().slerp(turn, .7F));
                Quaternionf worldRotation = world.getUnnormalizedRotation(new Quaternionf()).normalize();
                Quaternionf poseRotation = pose.orElseEmpty(name).rotation();
                Quaternionf basis = new Quaternionf(worldRotation).mul(new Quaternionf(poseRotation).invert());
                Quaternionf localTurn = new Quaternionf(basis).invert().mul(turn).mul(basis);
                poseRotation.premul(localTurn).normalize();
            }
            Vector3f palm = skeleton.getBoundTransformFor(pose, wrist).transformPosition(new Vector3f(palmLocal));
            if (palm.distanceSquared(target) < .000025F) break;
        }
    }
}
