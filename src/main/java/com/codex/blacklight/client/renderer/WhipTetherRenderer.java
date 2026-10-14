package com.codex.blacklight.client.renderer;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.animation.ConsumeVictimPose;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import java.util.*;

/** Draw after both entities: use the animated arm's actual rendered transform, with no one-frame attachment lag. */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class WhipTetherRenderer {
    private static final Map<Integer,Vec3> HANDS=new HashMap<>();
    private static final Map<Integer,Vec3> GRIPS=new HashMap<>();
    private static Vec3 camera=Vec3.ZERO;
    private WhipTetherRenderer() {}
    public static void capture(AbstractClientPlayer player,PoseStack handStack) {
        // The hook's authored local tip. This stack is the very same one used to draw the right forearm.
        // LevelRenderer puts view rotation in RenderSystem's global matrix. Entity stacks are already camera-relative world space.
        HANDS.put(player.getId(),BlacklightBoneRendering.worldPoint(handStack,new Vector3f(0,.30F,.05F)));
    }
    public static void capture(AbstractClientPlayer player,PoseStack stack,Vector3f anchor) {
        HANDS.put(player.getId(),BlacklightBoneRendering.worldPoint(stack,anchor));
    }
    public static void captureGrip(AbstractClientPlayer player,PoseStack stack,Vector3f anchor) {
        GRIPS.put(player.getId(),BlacklightBoneRendering.worldPoint(stack,anchor));
    }
    public static Vec3 capturedHand(int entityId) { return HANDS.get(entityId); }
    public static boolean extended(AbstractClientPlayer player){
        if(com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE).getActivePower()!=PowerForm.WHIPFIST)return false;
        var visual=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.WEAPON_VISUAL);var move=visual.spec();
        return move!=null&&move.form==PowerForm.WHIPFIST&&!visual.traveling()||com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.GRAB_STAGE)!=0;
    }
    /** Actual rendered claw palm, also used by the opt-in contact acceptance harness. */
    public static void captureGrip(AbstractClientPlayer player,PoseStack stack) {
        GRIPS.put(player.getId(),BlacklightBoneRendering.worldPoint(stack,new Vector3f(.00267F,.32362F,.07666F)));
    }
    public static void captureBlade(AbstractClientPlayer player,PoseStack stack) {
        GRIPS.put(player.getId(),BlacklightBoneRendering.worldPoint(stack,new Vector3f(-.0636396F,.68F,-.0636396F)));
    }
    public static Vec3 capturedGrip(int entityId) { return GRIPS.get(entityId); }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY) {
            HANDS.clear();GRIPS.clear();camera=event.getCamera().getPosition();return;
        }
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        float partial=event.getPartialTick();
        var buffers=mc.renderBuffers().bufferSource();var type=RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE);
        var out=buffers.getBuffer(type);var stack=event.getPoseStack();
        for(var entity:mc.level.entitiesForRendering()) {
            if(!(entity instanceof LivingEntity mob))continue;
            var data=com.codex.blacklight.registry.ModAttachments.getData(mob, ModAttachments.CONSUME_VISUAL);
            if(!data.active()||data.form()!=PowerForm.WHIPFIST.ordinal())continue;
            var owner=mc.level.getEntity(data.consumerId());if(owner==null)continue;
            float t=ConsumedMobVisuals.tetherProgress(mob,partial);if(t<.075F||t>.9F)continue;
            Vec3 delta=mob.getPosition(partial).subtract(owner.getPosition(partial));
            // First person / culled owner has no rendered body joint. Keep a local wrist fallback in that case.
            Vec3 right=new Vec3(-delta.z,0,delta.x).normalize().scale(.38);
            Vec3 hand=HANDS.getOrDefault(owner.getId(),owner.getPosition(partial).add(right).add(0,1.05,0));
            Vec3 placement=com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(mob,partial);
            double wrapRadius=mob.getBbWidth()*.58*(1-net.minecraft.util.Mth.clamp((t-.85)/.05,0,1));
            Vec3 body=mob.getPosition(partial).add(placement).add(ConsumeVictimPose.effectPoint(mob,new Vec3(wrapRadius,mob.getBbHeight()*.22,0),partial));
            float reach=net.minecraft.util.Mth.clamp((t-.075F)/.11F,0,1);
            Vec3 end=hand.lerp(body,reach);
            Vec3 side=new Vec3(-delta.z,0,delta.x).normalize();
            double sweep=Math.sin(Math.PI*reach)*.8;
            Vec3 control=hand.lerp(end,.45).add(side.scale(sweep)).add(0,.28*(1-reach)-.12,0);
            stack.pushPose();stack.translate(hand.x-camera.x,hand.y-camera.y,hand.z-camera.z);
            int light=mc.getEntityRenderDispatcher().getPackedLightCoords(mob,partial);
            var points=new ArrayList<Vec3>();
            for(int i=0;i<=32;i++)points.add(bezier(hand,control,end,i/32.0).subtract(hand));
            // The cutting tip stays outside the wrapped body, never a spear through its center.
            WhipArmSurface.render(stack,out,light,points,.115,false);
            stack.popPose();
        }
        buffers.endBatch(type);
    }
    private static Vec3 bezier(Vec3 a,Vec3 b,Vec3 c,double t) {
        return a.scale((1-t)*(1-t)).add(b.scale(2*(1-t)*t)).add(c.scale(t*t));
    }
}
