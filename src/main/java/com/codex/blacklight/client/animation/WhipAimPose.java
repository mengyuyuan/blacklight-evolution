package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.combat.WhipCurveContact;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The whip lash is tilted about the shoulder by {@link WhipCurveContact#aimPitch}; the arm that
 * throws it must tilt the same way, or an upward/downward strike leaves the lash bending off a
 * level arm. The right hand's authored position is rotated about the shoulder by the same angle
 * and reached with the shared IK, so elbow and wrist keep the authored bend.
 */
public final class WhipAimPose {
    private WhipAimPose() {}
    /** Hook tip in the right tool frame, as captured by WhipTetherRenderer. */
    private static final Vector3f TIP = new Vector3f(0, .30F, .05F);
    public static void apply(LivingEntity entity, BlacklightSkeleton skeleton, BlacklightPose pose) {
        if (entity.getData(ModAttachments.GRAB_STAGE) != ConsumeVisual.NONE) return;
        var move = entity.getData(ModAttachments.WEAPON_VISUAL).spec();
        if (move == null || move.form != PowerForm.WHIPFIST) return;
        float pitch = WhipCurveContact.aimPitch(entity);
        if (pitch == 0) return;
        var shoulder = skeleton.searchJointByName("Arm_R");
        var wrist = skeleton.searchJointByName("Tool_R");
        if (shoulder == null || wrist == null) return;
        Vector3f pivot = skeleton.getBoundTransformFor(pose, shoulder).getTranslation(new Vector3f());
        Vector3f tip = skeleton.getBoundTransformFor(pose, wrist).transformPosition(new Vector3f(TIP));
        // Model space faces -Z with +Y up; Minecraft pitch is positive downwards.
        Vector3f target = new Quaternionf().rotationX((float)Math.toRadians(-pitch))
                .transform(tip.sub(pivot, new Vector3f())).add(pivot);
        BlacklightBoneIk.reach(skeleton, pose, "R", target, new Vector3f(TIP), 12);
    }
}
