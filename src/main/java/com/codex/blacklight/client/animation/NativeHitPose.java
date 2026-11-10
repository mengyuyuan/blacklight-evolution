package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.HitReaction;
import com.codex.blacklight.animation.MotionLibrary;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.animation.BlacklightPose;

public final class NativeHitPose {
    private NativeHitPose(){}
    private static String key(int motion) { return new String[]{"reaction_body","reaction_left","reaction_right","reaction_backhand","reaction_up","reaction_fall","reaction_down","reaction_rise"}[Math.clamp(motion==8?3:motion,0,7)]; }
    public static boolean available(net.minecraft.world.entity.LivingEntity entity){
        return entity!=null && MotionLibrary.find(key(entity.getData(ModAttachments.HIT_REACTION).motion())).isPresent();
    }
    /** The flinch weight this client actually draws for the entity: 0 when a newer own action,
     * consume or committed weapon move outranks it. Shared by the renderer and multiplayer QA. */
    public static float visibleWeight(net.minecraft.world.entity.LivingEntity entity,float partial){
        var hit=entity.getData(ModAttachments.HIT_REACTION);
        if(entity.getData(ModAttachments.CONSUME_VISUAL).active()||hit.weight(entity.level().getGameTime(),partial)<=0||!available(entity))return 0;
        // A player's own grab or consume outranks any flinch; the server rejects it too.
        if(entity instanceof net.minecraft.world.entity.player.Player&&(entity.getData(ModAttachments.CONSUMING)
                &&entity.getData(ModAttachments.GRAB_STAGE)!=com.codex.blacklight.combat.ConsumeVisual.NONE))return 0;
        // A player's own committed attack outranks an incoming flinch.
        if(entity instanceof net.minecraft.world.entity.player.Player&&entity.getData(ModAttachments.WEAPON_VISUAL).spec()!=null)return 0;
        // Any player action started at or after the hit (combo swing, dodge, landing) supersedes the flinch.
        if(entity instanceof net.minecraft.world.entity.player.Player){var action=entity.getData(ModAttachments.PLAYER_ACTION);
            if(action.started()>=hit.started()&&action.active(com.codex.blacklight.animation.PlayerMotionController.actionTime(entity,partial)))return 0;}
        return hit.weight(entity.level().getGameTime(),partial);
    }
    public static boolean apply(net.minecraft.world.entity.LivingEntity entity,com.codex.blacklight.animation.BlacklightSkeleton armature,BlacklightPose pose,float partial){
        if(visibleWeight(entity,partial)<=0)return false;
        var hit=entity.getData(ModAttachments.HIT_REACTION);
        var clip=MotionLibrary.find(key(hit.motion())).orElseThrow();
        float age=entity.level().getGameTime()-hit.started()+partial;
        float progress=hit.kind()==HitReaction.GETUP?hit.progress(entity.level().getGameTime(),partial):
                hit.kind()==HitReaction.DOWN?.45F:Math.clamp(age/(hit.kind()==HitReaction.LAUNCH||hit.kind()==HitReaction.SLAM?12:Math.min(12,hit.duration())),0,1);
        var sample=MotionLibrary.pose(key(hit.motion()),progress*Math.max(0,clip.duration()-.001F),false);
        float weight=hit.weight(entity.level().getGameTime(),partial);
        var result=BlacklightPose.interpolatePose(pose,sample,weight);
        pose.getJointTransformData().putAll(result.getJointTransformData());return true;
    }
}
