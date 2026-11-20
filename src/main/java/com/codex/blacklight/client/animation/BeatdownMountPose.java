package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll;
import org.joml.Quaternionf;
import com.codex.blacklight.combat.ConsumeVisual;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Place the giver over the prone receiver while retaining the authored upper-body motion. */
final class BeatdownMountPose {
    private BeatdownMountPose() {}

    static void apply(LivingEntity actor, LivingEntity victim, ConsumeVisual visual,
                      BlacklightSkeleton rig, BlacklightPose pose, float partial) {
        if (visual.landingDelay() < 0) return;
        float seconds = visual.elapsed(actor.level().getGameTime(), partial) / 20F;
        // Frame 115 consumes the original receiver. Keep the pelvis planted
        // until then and blend into the authored standing motion afterwards.
        float absorbed = com.codex.blacklight.combat.ConsumeReceiverScene.absorptionSeconds(
                com.codex.blacklight.power.PowerForm.NONE, visual.variant());
        float weight = RagdollDrive.smooth((seconds - .95F) / .34F)
                * (1 - RagdollDrive.smooth((seconds - absorbed) / .4F));
        if (weight <= 0) return;
        var support = AdaptiveRagdoll.beatdownSupport(victim, partial);
        if (support == null) return;

        var origin = actor.getPosition(partial);
        var forward = Vec3.directionFromRotation(0,visual.facing());
        float scale = .9375F * actor.getScale();
        var fitted = pose.copy();
        var original = rig.getPoseMatrices(pose);
        var seat = support.surface().add(0, .025 * scale, 0);
        var delta = seatPelvis(rig, fitted, local(seat,origin,forward,scale));
        var chestShift = new Vector3f(delta).mul(.52F);
        var headShift = new Vector3f(delta).mul(.62F);
        BlacklightBoneIk.aimJoint(rig, fitted, rig.torso, rig.chest, original[rig.chest.getId()].getTranslation(new Vector3f()).add(chestShift));
        BlacklightBoneIk.aimJoint(rig, fitted, rig.chest, rig.head, original[rig.head.getId()].getTranslation(new Vector3f()).add(headShift));

        Vec3 heading = support.towardHead();
        Vec3 side = new Vec3(heading.z,0,-heading.x);
        for (String limb : new String[]{"L","R"}) {
            int sign = limb.equals("L") ? 1 : -1;
            var thigh = rig.searchJointByName("Thigh_" + limb);
            var shin = rig.searchJointByName("Leg_" + limb);
            // Fit the knee outside the flank first. Solving from the ankle alone
            // folds the knee inwards on a wide coat, even if the foot is outside it.
            var outward = local(side.scale(sign), Vec3.ZERO, forward, 1);
            var toward = local(heading, Vec3.ZERO, forward, 1);
            var hip = rig.getBoundTransformFor(fitted, thigh).getTranslation(new Vector3f());
            var oldKnee = rig.getBoundTransformFor(fitted, shin).getTranslation(new Vector3f());
            float length = hip.distance(oldKnee);
            var center = local(seat,origin,forward,scale);
            var knee = kneePosition(hip,center,outward,toward,length,(float)(support.halfWidth()/scale));
            BlacklightBoneIk.aimJoint(rig,fitted,thigh,shin,knee);

            var ankle = seat.add(side.scale(sign*(support.halfWidth()+.1*scale))).subtract(heading.scale(.25*scale));
            var floor = actor.level().clip(new ClipContext(ankle.add(0,.25,0), ankle.add(0,-1.5,0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
            double ground = floor.getType() == HitResult.Type.BLOCK ? floor.getLocation().y : support.bottom();
            var shinFrame = rig.getBoundTransformFor(fitted,shin);
            var pivot = shinFrame.getTranslation(new Vector3f());
            var legAxis = shinFrame.transformDirection(new Vector3f(0,.375F,0));
            float drop = Math.max(0,Math.min(legAxis.length(),pivot.y-(float)((ground-origin.y)/scale)-.095F));
            var soleAxis = new Vector3f(toward).mul(-(float)Math.sqrt(Math.max(0,legAxis.lengthSquared()-drop*drop)));
            soleAxis.y = -drop;
            BlacklightBoneIk.turn(rig,fitted,shin,new Quaternionf().rotationTo(legAxis.normalize(),soleAxis.normalize()));
            var wrist = rig.searchJointByName("Tool_" + limb);
            BlacklightBoneIk.reachTwoBone(rig, fitted, limb, original[wrist.getId()].getTranslation(new Vector3f()), new Vector3f());
        }
        pose.getJointTransformData().putAll(BlacklightPose.interpolatePose(pose, fitted, weight).getJointTransformData());
    }

    static Vector3f seatPelvis(BlacklightSkeleton rig, BlacklightPose pose, Vector3f seat) {
        var pelvis = rig.getBoundTransformFor(pose,rig.rootJoint).getTranslation(new Vector3f());
        var delta = new Vector3f(seat).sub(pelvis);
        var bindOffset = rig.rootJoint.getLocalTransform().invert().transformDirection(new Vector3f(delta));
        pose.orElseEmpty(rig.rootJoint.getName()).translation().add(bindOffset);
        return delta;
    }

    static Vector3f kneePosition(Vector3f hip, Vector3f seat, Vector3f outward, Vector3f forward,
                                 float thighLength, float halfWidth) {
        float hipSide = new Vector3f(hip).sub(seat).dot(outward);
        float spread = Math.max(0,Math.min(thighLength*.96F,halfWidth+.12F-hipSide));
        float remaining = Math.max(0,thighLength*thighLength-spread*spread);
        float front = Math.min(.12F,.52F*(float)Math.sqrt(remaining));
        var knee = new Vector3f(hip).fma(spread,outward).fma(front,forward);
        knee.y -= (float)Math.sqrt(Math.max(0,remaining-front*front));
        return knee;
    }

    private static Vector3f local(Vec3 point, Vec3 origin, Vec3 forward, float scale) {
        Vec3 offset = point.subtract(origin);
        return new Vector3f((float)((offset.z*forward.x-offset.x*forward.z)/scale),
                (float)(offset.y/scale),(float)(-offset.dot(forward)/scale));
    }
}
