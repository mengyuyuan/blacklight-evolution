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
        if(!(entity instanceof Player player)
                ||com.codex.blacklight.movement.WallPoseState.get(player)!=null
                ||com.codex.blacklight.registry.ModAttachments.getData(player,ModAttachments.WEAPON_VISUAL).spec()!=null
                ||com.codex.blacklight.registry.ModAttachments.getData(player,ModAttachments.GRAB_STAGE)!=0
                ||com.codex.blacklight.registry.ModAttachments.getData(player,ModAttachments.CONSUME_RECOVERY).active()
                ||com.codex.blacklight.registry.ModAttachments.getData(player,ModAttachments.JUMP_CHARGE)>0
                ||com.codex.blacklight.registry.ModAttachments.getData(player,ModAttachments.POWER_MORPH).visible(player,partial))return;
        var action=com.codex.blacklight.registry.ModAttachments.getData(player,ModAttachments.PLAYER_ACTION);
        if(action.active(com.codex.blacklight.animation.PlayerMotionController.actionTime(player,partial))&&!action.clipId().startsWith("basic_"))return;
        float contact=com.codex.blacklight.animation.PlayerMotionController.groundLook(player,partial);
        if(contact<=0)return;
        float headYaw=Mth.rotLerp(partial,player.yHeadRotO,player.yHeadRot);
        float bodyYaw=BlacklightRenderPose.facing(player,partial);
        float relative=Mth.wrapDegrees(headYaw-bodyYaw);
        // At exactly the rear the anatomical turn is ambiguous. Return to centre
        // continuously from either side, avoiding a 150-degree snap across +/-180.
        float rear=Mth.clamp((180-Math.abs(relative))/65F,0,1);
        float yaw=Mth.clamp(relative,-75,75)*rear*rear*(3-2*rear);
        float pitch=Mth.clamp(Mth.lerp(partial,player.xRotO,player.getXRot()),-70,70);
        // Native Minecraft's model Y points downward; Blacklight's Y points up.
        pose.orElseEmpty("Head").rotation().rotateY(-(float)Math.toRadians(yaw*contact)).rotateX(-(float)Math.toRadians(pitch*contact));
    }
}
