package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;

/** Applies gameplay-driven pose constraints once, before any skin, armour or limb layer samples it. */
public final class BlacklightRenderPose {
    private BlacklightRenderPose() { }
    public static boolean mouthStruggle(LivingEntity entity,float partial){
        var action=entity.getData(ModAttachments.PLAYER_ACTION);
        return action.clipId().startsWith("greene_mouth_") && action.active(PlayerMotionController.actionTime(entity,partial));
    }
    public static BlacklightPose sample(LivingEntity entity, float partial) {
        if(mouthStruggle(entity,partial)){
            var action=entity.getData(ModAttachments.PLAYER_ACTION);
            return MotionLibrary.pose(action.clipId(),action.seconds(PlayerMotionController.actionTime(entity,partial)),false);
        }
        if(entity instanceof net.minecraft.world.entity.player.Player&&entity.getVehicle() instanceof com.codex.blacklight.entity.BlackwatchHelicopter craft)return com.codex.blacklight.client.vehicle.HelicopterPose.skeleton(craft,entity,partial);
        if(entity instanceof net.minecraft.world.entity.player.Player&&entity.getVehicle() instanceof com.codex.blacklight.entity.MilitaryVehicle vehicle){
            String seat=vehicle.seatOf(entity.getUUID());
            boolean driver=vehicle.definition().seats().stream().anyMatch(s->s.id().equals(seat)&&s.job()==com.codex.blacklight.military.vehicle.VehicleDefinition.Job.DRIVER);
            return com.codex.blacklight.client.vehicle.VehicleSeatPose.skeleton(driver,net.minecraft.util.Mth.wrapDegrees(entity.getViewYRot(partial)+vehicle.getYRot()),entity.getViewXRot(partial),com.codex.blacklight.client.vehicle.VehicleSeatPose.drop(vehicle.definition()));
        }
        if(entity instanceof net.minecraft.world.entity.player.Player p){var hydra=HydraPlayerPose.sample(p,partial);if(hydra!=null)return hydra;}
        var skeleton = PlayerMotionController.skeleton();
        var pose = PlayerMotionController.pose(entity, partial);
        if(entity instanceof net.minecraft.world.entity.player.Player p){var throwing=BlockThrowPose.sample(p,partial);if(throwing!=null)return throwing;}
        braceLean(entity,pose,partial);
        NativeHeadLook.apply(entity,pose,partial);
        if (!NativeHitPose.apply(entity, skeleton, pose, partial)) ConsumeVictimPose.epic(entity, pose, partial);
        boolean actorOwns=NativeConsumePose.actor(entity, pose, partial);
        if (!actorOwns && ConsumeVisual.carrying(entity.getData(ModAttachments.GRAB_STAGE))) {
            String key = entity.getData(ModAttachments.PLAYER_STATE).getActivePower() == PowerForm.BLADE && (!(entity instanceof net.minecraft.world.entity.player.Player p)||com.codex.blacklight.combat.BlockThrowSystem.held(p)==null) ? "grab_blade_hold" : "grab_hold";
            var held = MotionLibrary.pose(key, (entity.tickCount + partial) / 20F, true);
            pose=CarryPoseLayer.holdCreature(held,pose,!entity.onGround(),key.equals("grab_blade_hold"));
        }
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
        if(wall==null&&!weapon.charging()&&weapon.spec()!=null
                &&(weapon.spec().hammerComboSlam()||entity.onGround()&&com.codex.blacklight.combat.HammerQuakeContact.owns(weapon.spec()))){
            var clock=WeaponMotionClock.sample(weapon,entity.level().getGameTime()+(double)partial);
            if(clock!=null){
                var shapes=entity instanceof net.minecraft.client.player.AbstractClientPlayer player
                        ?com.codex.blacklight.client.renderer.WeaponScaleRenderer.sample(player,partial)
                        :new com.codex.blacklight.combat.WeaponAttackScales.Shape[]{com.codex.blacklight.combat.WeaponAttackScales.sample(clock.key(),clock.seconds(),false),com.codex.blacklight.combat.WeaponAttackScales.sample(clock.key(),clock.seconds(),true)};
                com.codex.blacklight.combat.HammerGroundPose.quake(entity,pose,clock.key(),clock.seconds(),entity.getPosition(partial),BlacklightRenderPose.facing(entity,partial),shapes[0],shapes[1]);
            }
        }
        if(wall==null)WhipAimPose.apply(entity,skeleton,pose);
        // Fit after locomotion/wall constraints and to the receiver's rendered
        // position. Holding no longer skips contact correction just because a
        // paired actor clip was found.
        // Receiver HOLD can arrive one packet before the carrier's stage update.
        // GrabHoldPose checks the held receiver itself, so fit that first frame
        // while the carrier still reports CAPTURE as well.
        if(!actorOwns||ConsumeVisual.carrying(entity.getData(ModAttachments.GRAB_STAGE))||entity.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.CAPTURE)
            GrabHoldPose.apply(entity,skeleton,pose,partial);
        return pose;
    }
    private static void braceLean(LivingEntity entity,BlacklightPose pose,float partial){
        var action=entity.getData(ModAttachments.PLAYER_ACTION);
        String key=action.clipId();
        if(!key.startsWith("spines_")||!key.endsWith("_draw")||!action.active(PlayerMotionController.actionTime(entity,partial)))return;
        var travel=new net.minecraft.world.phys.Vec3(entity.getX()-entity.xo,0,entity.getZ()-entity.zo);
        var local=travel.yRot((float)Math.toRadians(entity.getYRot()));
        float drive=(float)Math.tanh(local.z*7),side=(float)Math.tanh(local.x*7);
        float turn=net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(entity.yHeadRot-entity.yHeadRotO)/35,-1,1);
        pose.orElseEmpty("Torso").rotation().rotateX(-.19F*drive).rotateZ(.14F*side+.09F*turn);
        pose.orElseEmpty("Chest").rotation().rotateY(-.12F*turn);
        pose.orElseEmpty("Head").rotation().rotateX(.09F*drive);
        if(key.startsWith("spines_air_")){
            pose.orElseEmpty("Thigh_L").rotation().rotateX(-.095F*drive).rotateZ(.065F*side);
            pose.orElseEmpty("Thigh_R").rotation().rotateX(.085F*drive).rotateZ(.06F*side);
            pose.orElseEmpty("Leg_R").rotation().rotateX(-.055F*drive);
        }
    }
    public static float facing(LivingEntity entity,float partial) {
        if(mouthStruggle(entity,partial))return entity.getData(ModAttachments.PLAYER_ACTION).yaw();
        if(entity.getVehicle() instanceof com.codex.blacklight.entity.BlackwatchHelicopter craft)return -net.minecraft.util.Mth.rotLerp(partial,craft.yRotO,craft.getYRot());
        if(entity.getVehicle() instanceof com.codex.blacklight.entity.MilitaryVehicle vehicle)return -net.minecraft.util.Mth.rotLerp(partial,vehicle.yRotO,vehicle.getYRot());
        if(entity instanceof net.minecraft.world.entity.player.Player p){var h=com.codex.blacklight.city.HydraTransport.carrier(p);if(h!=null)return h.getYRot();}
        if(entity instanceof net.minecraft.world.entity.player.Player p&&com.codex.blacklight.combat.BlockThrowMotion.active(p))return com.codex.blacklight.combat.BlockThrowMotion.yaw(p,partial);
        return PlayerMotionController.facing(entity,partial);
    }
    public static float rawFacing(LivingEntity entity,float partial) {
        var dodge=entity.getData(ModAttachments.PLAYER_ACTION);
        if(dodge.clipId().startsWith("spines_")&&!dodge.clipId().startsWith("spines_scapula")
                &&dodge.active(PlayerMotionController.actionTime(entity,partial)))
            return net.minecraft.util.Mth.rotLerp(partial,entity.yHeadRotO,entity.yHeadRot);
        if(dodge.clipId().equals("basic_dodge_roll")&&dodge.active(com.codex.blacklight.animation.PlayerMotionController.actionTime(entity,partial)))return dodge.yaw();
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
