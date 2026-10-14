package com.codex.blacklight.animation;

import org.joml.Matrix4f;

/** Distance-driven feet with the authored upper body kept in the same grip space. */
public final class CarryPoseLayer {
    private CarryPoseLayer() {}

    public static BlacklightPose apply(BlacklightPose action, BlacklightPose stride, float weight) {
        if (weight <= 0) return action.copy();
        var skeleton = BlacklightSkeleton.get();
        var result = BlacklightPose.interpolatePose(action, stride, Math.min(1, weight));
        // Torso is the common parent of the head, shoulders, hands and tools.
        // Rebase it after replacing the root so neither palm drifts off its cargo.
        var torso = skeleton.torso;
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
        var local = new Matrix4f(parent).mul(torso.getLocalTransform()).invert()
                .mul(skeleton.getBoundTransformFor(action, torso));
        result.getJointTransformData().put(torso.getName(), BoneTransform.fromMatrix(local));
        return result;
    }
}
