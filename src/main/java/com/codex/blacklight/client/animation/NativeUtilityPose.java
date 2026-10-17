package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.MotionLibrary;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
import com.codex.blacklight.animation.BlacklightPose;

/** Source poses over the current gait. Combat, consumption and wall contact keep priority. */
public final class NativeUtilityPose {
    public record Selection(String key, float time, float weight, boolean plated, boolean transition, boolean moving) { }
    private static boolean moving(Player p) {
        var visual=com.codex.blacklight.movement.LocomotionVisualState.get(p.getId());
        return visual.phase().isGroundMovement()||visual.speed()>.018||p.getDeltaMovement().horizontalDistance()>.018;
    }
    public static Selection select(net.minecraft.world.entity.LivingEntity entity, float partial) {
        if (!(entity instanceof Player p) || !p.onGround()
                || com.codex.blacklight.movement.WallPoseState.get(p) != null
                || com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).spec() != null
                || com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.GRAB_STAGE) != 0
                || com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUME_RECOVERY).active()
                || com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.HIT_REACTION).weight(p.level().getGameTime(), partial) > 0
                || com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.JUMP_CHARGE) > 0) return null;
        var s = com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_STATE);
        float armor = UtilityPowerSystem.visualWeight(p, PowerForm.ARMOR, partial);
        float shield = UtilityPowerSystem.visualWeight(p, PowerForm.SHIELD, partial);
        if (armor <= 0 && shield <= 0) return null;
        double age = (p.level().getGameTime() - s.changedAt() + partial) / 20.0;
        double hit = (p.level().getGameTime() - s.hitAt() + partial) / 20.0;
        boolean plated = armor > shield;
        boolean moving = moving(p);
        String key = plated ? "armor_transform" : "shield_deploy";
        var clip = MotionLibrary.find("utility_"+key).orElseThrow();
        float time = (float)age;
        boolean impact = !plated && hit >= 0 && hit < .367;
        boolean transition = UtilityPowerSystem.active(p, plated ? PowerForm.ARMOR : PowerForm.SHIELD)
                && !impact && age >= 0 && age < clip.duration();
        if (!transition) {
            key = plated ? "armor_idle" : impact ? "shield_hit" : p.isSprinting() ? "shield_sprint" : "shield_run";
            clip = MotionLibrary.find("utility_"+key).orElseThrow();
            time = key.equals("shield_hit") ? (float)hit : !plated && !moving ? 0
                    : (float)(((p.level().getGameTime() + partial) / 20.0) % Math.max(.001, clip.duration()));
        }
        time = net.minecraft.util.Mth.clamp(time, 0, Math.max(0, clip.duration() - .001F));
        float weight = Math.max(armor, shield);
        if (transition) weight *= Math.min(1, (clip.duration() - (float)age) / .15F);
        return new Selection(key, time, weight, plated, transition, moving);
    }
    public static void apply(net.minecraft.world.entity.LivingEntity entity, BlacklightPose pose, float partial) {
        if(entity instanceof Player p) {
            var wing=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WING_FLIGHT);
            double wingAge=p.level().getGameTime()+partial-wing.transformedAt();
            float wingWeight=wing.transformedAt()>0?com.codex.blacklight.animation.WingMorphTimeline.bodyWeight(wing.deployed(),wingAge):0;
            if(wingWeight>0&&p.onGround()&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).spec()==null
                    &&!com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING)&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.GRAB_STAGE)==0
                    &&com.codex.blacklight.mode.PrototypeModeSystem.isActive(p)) {
                com.codex.blacklight.animation.WingTransformationPose.apply(pose,wing.deployed(),wingAge);
                return;
            }
            var morph=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.POWER_MORPH);
            var animation=morph.key().isEmpty()?null:MotionLibrary.find(morph.key()).orElse(null);
            if(animation!=null&&com.codex.blacklight.movement.WallPoseState.get(p)==null&&morph.visible(p,partial)) {
                float t=morph.seconds(p,partial),duration=animation.duration();
                float in=morph.key().equals("morph_thermal_vision")?0:.333333F;
                float out=switch(morph.key()) {
                    case "morph_claws"->.5F;case "morph_blade","morph_hammerfists"->.1F;
                    case "morph_whipfist"->.166667F;default->.333333F;
                };
                float weight=net.minecraft.util.Mth.clamp(Math.min(in==0?1:t/in,(duration-t)/out),0,1);
                var source=MotionLibrary.pose(morph.key(),net.minecraft.util.Mth.clamp(t,0,Math.max(0,duration-.001F)),false);
                var blended=BlacklightPose.interpolatePose(pose,source,weight);
                if(p.onGround()&&moving(p))
                    for(String joint:new String[]{"Torso","Chest","Head","Shoulder_R","Arm_R","Elbow_R","Hand_R","Tool_R","Shoulder_L","Arm_L","Elbow_L","Hand_L","Tool_L"})
                        pose.getJointTransformData().put(joint,blended.orElseEmpty(joint).copy());
                else pose.getJointTransformData().putAll(blended.getJointTransformData());
                return;
            }
        }
        Selection selection = select(entity, partial);
        if (selection == null) return;
        boolean plated=selection.plated(), transition=selection.transition(), moving=selection.moving();
        BlacklightPose source=MotionLibrary.pose("utility_"+selection.key(),selection.time(),false);
        BlacklightPose blended = BlacklightPose.interpolatePose(pose, source, selection.weight());
        String[] joints = plated ? new String[]{"Torso","Chest","Head","Shoulder_R","Arm_R","Elbow_R","Hand_R","Shoulder_L","Arm_L","Elbow_L","Hand_L"}
                : new String[]{"Shoulder_L","Arm_L","Elbow_L","Hand_L","Tool_L"};
        if (plated && transition && !moving) pose.getJointTransformData().putAll(blended.getJointTransformData());
        else if (!plated || transition || !moving)
            for (String joint : joints) pose.getJointTransformData().put(joint, blended.orElseEmpty(joint).copy());
    }
}
