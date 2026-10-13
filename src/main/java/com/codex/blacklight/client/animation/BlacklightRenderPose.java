package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;

/** Applies gameplay-driven pose constraints once, before any skin, armour or limb layer samples it. */
public final class BlacklightRenderPose {
    private BlacklightRenderPose() { }
    public static BlacklightPose sample(LivingEntity entity, float partial) {
        var skeleton = PlayerMotionController.skeleton();
        var pose = PlayerMotionController.pose(entity, partial);
        NativeHeadLook.apply(entity,pose,partial);
        if (!NativeHitPose.apply(entity, skeleton, pose, partial)) ConsumeVictimPose.epic(entity, pose, partial);
        if (entity.getData(ModAttachments.GRAB_STAGE) == ConsumeVisual.HOLD) {
            String key = entity.getData(ModAttachments.PLAYER_STATE).getActivePower() == PowerForm.BLADE && (!(entity instanceof net.minecraft.world.entity.player.Player p)||com.codex.blacklight.combat.BlockThrowSystem.held(p)==null) ? "grab_blade_hold" : "grab_hold";
            var held = MotionLibrary.pose(key, (entity.tickCount + partial) / 20F, true);
            for (String joint : new String[]{"Torso", "Chest", "Head", "Shoulder_R", "Arm_R", "Elbow_R", "Hand_R", "Tool_R", "Shoulder_L", "Arm_L", "Elbow_L", "Hand_L", "Tool_L"})
                pose.getJointTransformData().put(joint, held.orElseEmpty(joint).copy());
        }
        if (!NativeConsumePose.actor(entity, pose, partial)) GrabHoldPose.apply(entity, skeleton, pose, partial);
        ConsumeGripPose.apply(entity, skeleton, pose, partial);
        var wall=com.codex.blacklight.movement.WallPoseState.get(entity);
        if(wall!=null) {
            var velocity=entity.getDeltaMovement();
            if(wall.mode()==com.codex.blacklight.movement.WallTraversal.Mode.RUN) {
                double right=velocity.x*wall.normalZ()-velocity.z*wall.normalX();
                double up=WallSurfacePose.normalizeRunVertical(right,velocity.y,entity.onGround(),wall.retiring());
                // A paused editor frame has an authored direction, rather than the
                // player's residual gravity. Live wall travel still uses authority velocity.
                String preview=PlayerMotionController.previewKey(entity);
                if(preview!=null)switch(preview) {
                    case "basic_wall_run_up" -> {right=0;up=1;}
                    case "basic_wall_run_right" -> {right=1;up=0;}
                    case "basic_wall_run_left" -> {right=-1;up=0;}
                    case "basic_wall_run_diag_right" -> {right=1;up=1;}
                    case "basic_wall_run_diag_left" -> {right=-1;up=1;}
                    default -> { }
                }
                WallSurfacePose.orientRun(pose,skeleton,right,up,entity.getBbWidth()*.5F);
            }
            if(wall.mode()!=com.codex.blacklight.movement.WallTraversal.Mode.TOP
                    &&wall.mode()!=com.codex.blacklight.movement.WallTraversal.Mode.PARKOUR)
                WallSurfacePose.clearSkin(pose,skeleton,entity.getBbWidth()*.5F);
            WallSurfacePose.faceWall(pose,skeleton,new org.joml.Matrix4f()
                    .rotationY((float)Math.toRadians(180-facing(entity,partial))).scale(.9375F),wall.normalX(),wall.normalZ());
        }
        WallContactPose.apply(entity, skeleton, pose, partial);
        var weapon=entity.getData(ModAttachments.WEAPON_VISUAL);
        if(wall==null&&entity.onGround()&&weapon.spec()==com.codex.blacklight.combat.WeaponMove.HAMMER_RAM
                &&!weapon.charging())HammerGroundContact.apply(pose);
        return pose;
    }
    public static float facing(LivingEntity entity,float partial) {
        return PlayerMotionController.facing(entity,partial);
    }
    public static float rawFacing(LivingEntity entity,float partial) {
        float body=net.minecraft.util.Mth.rotLerp(partial,entity.yBodyRotO,entity.yBodyRot);
        var weapon=entity.getData(ModAttachments.WEAPON_VISUAL);
        float facing=NativeConsumePose.facing(entity,weapon.spec()==null?entity.getYRot():weapon.yaw(),partial);
        if(entity instanceof net.minecraft.client.player.AbstractClientPlayer player) {
            Float movement=com.codex.blacklight.client.PrototypeAnimationController.movementFacing(player,partial);
            var morph=player.getData(ModAttachments.POWER_MORPH);
            if(morph.visible(player,partial))movement=morph.facing();
            if(player.getData(ModAttachments.GRAB_STAGE)==0&&weapon.spec()==null&&movement!=null)return movement;
            if(player.getData(ModAttachments.GRAB_STAGE)==0&&weapon.spec()==null&&!morph.visible(player,partial))return body;
        }
        return facing;
    }
}
