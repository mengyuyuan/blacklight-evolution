package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.HitReaction;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.math.Axis;

/** Non-human rigs retain their own proportions; the body's support point stays above the floor. */
public final class HitBodyPose {
    private HitBodyPose(){}
    public static void apply(LivingEntity e,PoseStack stack,float partial){
        if(com.codex.blacklight.client.renderer.AdaptiveRagdoll.active(e))return;
        var hit=e.getData(ModAttachments.HIT_REACTION);float weight=hit.weight(e.level().getGameTime(),partial);if(weight<=0)return;
        float progress=hit.progress(e.level().getGameTime(),partial),prone;
        if(hit.kind()==HitReaction.DOWN)prone=1;
        else if(hit.kind()==HitReaction.GETUP)prone=1-progress*progress*(3-2*progress);
        else if(hit.kind()==HitReaction.DEATH)prone=Math.clamp(progress*3,0,1);
        else prone=(hit.kind()==HitReaction.LAUNCH||hit.kind()==HitReaction.SLAM||hit.motion()==com.codex.blacklight.combat.ImpactProfile.FALL?Math.clamp(progress*3,0,.8F):0);
        float height=e.getBbHeight(),width=e.getBbWidth();
        float radians=(float)Math.toRadians(hit.yaw());
        // A quadruped tips sideways; a biped falls on its back.
        boolean broad=e instanceof net.minecraft.world.entity.animal.Animal||width>height*.65;
        float angle=prone*(float)Math.PI*.5F;
        float center=height*.5F;
        float support=(float)(Math.abs(Math.cos(angle))*height*.5+Math.abs(Math.sin(angle))*width*.5);
        stack.translate(0,support,0);stack.mulPose(Axis.YP.rotation(-radians));
        if(broad)stack.mulPose(Axis.ZP.rotation(angle));else stack.mulPose(Axis.XP.rotation(angle));
        if(prone<.01F)stack.mulPose(Axis.XP.rotation(-.14F*weight));
        stack.mulPose(Axis.YP.rotation(radians));stack.translate(0,-center,0);
    }
}
