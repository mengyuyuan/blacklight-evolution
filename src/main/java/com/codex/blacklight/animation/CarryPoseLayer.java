package com.codex.blacklight.animation;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Distance-driven feet with the authored upper body kept in the same grip space. */
public final class CarryPoseLayer {
    private CarryPoseLayer() {}

    public static BlacklightPose apply(BlacklightPose action, BlacklightPose stride, float weight) {
        if (weight <= 0) return action.copy();
        var skeleton = BlacklightSkeleton.get();
        var result = BlacklightPose.interpolatePose(action, stride, Math.min(1, weight));
        // Keep the torso anchored to the moving pelvis. Preserving the action's
        // absolute torso position here used to detach it from the walking legs.
        var torso = skeleton.torso;
        var anchor = skeleton.getBoundTransformFor(result, torso).getTranslation(new Vector3f());
        for (var bone : skeleton.bones()) {
            var ancestor = bone;
            while (ancestor != null && ancestor != torso) ancestor = ancestor.parent();
            if (ancestor != null) {
                var transform = action.get(bone.getName());
                if (transform == null) result.getJointTransformData().remove(bone.getName());
                else result.getJointTransformData().put(bone.getName(), transform.copy());
            }
        }
        var parent = skeleton.getBoundTransformFor(result, torso.parent());
        var attached = skeleton.getBoundTransformFor(action, torso).setTranslation(anchor);
        var local = new Matrix4f(parent).mul(torso.getLocalTransform()).invert().mul(attached);
        result.getJointTransformData().put(torso.getName(), BoneTransform.fromMatrix(local));
        return result;
    }
}
