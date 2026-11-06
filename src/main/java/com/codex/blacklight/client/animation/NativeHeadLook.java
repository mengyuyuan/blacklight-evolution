package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Idle and walking keep the authored gait while the independent head follows the player's look. */
public final class NativeHeadLook {
    private NativeHeadLook() { }
    public static void apply(LivingEntity entity,BlacklightPose pose,float partial) {
        if(!(entity instanceof Player player)||!player.onGround()
                ||com.codex.blacklight.movement.WallPoseState.get(player)!=null
                ||player.getData(ModAttachments.WEAPON_VISUAL).spec()!=null
                ||player.getData(ModAttachments.GRAB_STAGE)!=0
                ||player.getData(ModAttachments.CONSUME_RECOVERY).active()
                ||player.getData(ModAttachments.JUMP_CHARGE)>0
                ||player.getData(ModAttachments.POWER_MORPH).visible(player,partial)
                ||player.getData(ModAttachments.HIT_REACTION).weight(player.level().getGameTime(),partial)>0)return;
        var action=player.getData(ModAttachments.PLAYER_ACTION);
        if(action.active(com.codex.blacklight.animation.PlayerMotionController.actionTime(player,partial))&&!action.clipId().startsWith("basic_"))return;
        float headYaw=Mth.rotLerp(partial,player.yHeadRotO,player.yHeadRot);
        float bodyYaw=BlacklightRenderPose.facing(player,partial);
        float yaw=Mth.clamp(Mth.wrapDegrees(headYaw-bodyYaw),-75,75);
        float pitch=Mth.clamp(Mth.lerp(partial,player.xRotO,player.getXRot()),-70,70);
        // Native Minecraft's model Y points downward; Blacklight's Y points up.
        pose.orElseEmpty("Head").rotation().rotateY(-(float)Math.toRadians(yaw)).rotateX(-(float)Math.toRadians(pitch));
    }
}
