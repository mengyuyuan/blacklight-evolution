package com.codex.blacklight.client.renderer;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLivingEvent;

/** Victims retain their species model until it contracts into biomass. No replacement mob or item drop. */
@EventBusSubscriber(modid = BlacklightNeoMod.MOD_ID, value = Dist.CLIENT)
public final class ConsumedMobVisuals {
    public static final ResourceLocation WHITE = new ResourceLocation("blacklight", "textures/entity/claw_white.png");
    private static final java.util.Set<LivingEntity> RENDERING = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private ConsumedMobVisuals() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void before(RenderLivingEvent.Pre<?, ?> event) {
        if (!LivingRenderScope.active(event.getPoseStack())) return;
        var mob = event.getEntity();
        if((com.codex.blacklight.client.animation.ImpactRagdoll.active(mob)||AdaptiveRagdoll.active(mob))&&!com.codex.blacklight.client.animation.RagdollDrive.captured(mob))return;
        if (RENDERING.contains(mob)) return;
        // The dispatcher coats vanilla and custom renderers once, after their posed mesh is emitted.
        if(com.codex.blacklight.power.BiomassSystem.claimant(mob)!=null)return;
        if (mob instanceof BlacklightHunter) return;
        var data = com.codex.blacklight.registry.ModAttachments.getData(mob, ModAttachments.CONSUME_VISUAL);
        if (!data.active()) {
            boolean feeding=DevourerFeedingFx.shrinkingVictim(mob,event.getPartialTick());
            var hit=com.codex.blacklight.registry.ModAttachments.getData(mob, ModAttachments.HIT_REACTION);
            if(!feeding&&hit.weight(mob.level().getGameTime(),event.getPartialTick())<=0)return;
            if(!feeding&&mob instanceof net.minecraft.client.player.AbstractClientPlayer&&com.codex.blacklight.client.animation.NativeHitPose.available(mob))return;
            var stack=event.getPoseStack();LivingRenderScope.push(stack);
            if(feeding)DevourerFeedingFx.applyVictim(mob,event.getPartialTick(),stack);
            com.codex.blacklight.client.animation.HitBodyPose.apply(mob,stack,event.getPartialTick());
            return;
        }
        var owner = mob.level().getEntity(data.consumerId());
        if (owner == null) return;
        float partial = event.getPartialTick(), t = data.progress(mob.level().getGameTime(), partial);
        Vec3 placement=com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(mob,partial);
        var stack = event.getPoseStack();
        LivingRenderScope.push(stack);stack.translate(placement.x,placement.y,placement.z);
        if (t >= .995F && !com.codex.blacklight.combat.ConsumeSequence.boss(mob)) { stack.popPose();event.setCanceled(true); return; }
        ConsumeFracture fragments=!PigRagdoll.active(mob)&&!AdaptiveRagdoll.active(mob)&&ConsumeFracture.visible(mob,partial)?new ConsumeFracture(mob,partial,stack,event.getMultiBufferSource(),ConsumeAbsorptionFx.destination(mob,partial,placement)):null;
        if(fragments!=null)fragments.bindModel(event.getRenderer().getModel());
        com.codex.blacklight.client.animation.ConsumeVictimPose.body(mob, stack, partial);
        if (fragments!=null) {
            event.setCanceled(true);
            var buffers=fragments==null?event.getMultiBufferSource():fragments;
            try {
                RENDERING.add(mob);
                renderVanilla(event,mob,buffers);
                if(fragments!=null)fragments.finish();
            } finally { RENDERING.remove(mob);stack.popPose(); }
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void renderVanilla(RenderLivingEvent.Pre<?,?> event,LivingEntity mob,net.minecraft.client.renderer.MultiBufferSource buffers){
        ((net.minecraft.client.renderer.entity.LivingEntityRenderer)event.getRenderer()).render(mob,mob.getYRot(),event.getPartialTick(),event.getPoseStack(),buffers,event.getPackedLight());
    }

    public static void tube(PoseStack stack, VertexConsumer out, int light, Vec3 a, Vec3 b, double radius, int color) {
        Vec3 axis = b.subtract(a).normalize(), u = axis.cross(new Vec3(0,1,0)).normalize();
        if (u.lengthSqr() < .1) u = new Vec3(1,0,0);
        Vec3 v = axis.cross(u).normalize();
        for (int i=0; i<6; i++) {
            Vec3 n=u.scale(Math.cos(i*Math.PI/3)).add(v.scale(Math.sin(i*Math.PI/3)));
            Vec3 m=u.scale(Math.cos((i+1)*Math.PI/3)).add(v.scale(Math.sin((i+1)*Math.PI/3)));
            vertex(stack,out,light,a.add(n.scale(radius)),n,color); vertex(stack,out,light,b.add(n.scale(radius)),n,color);
            vertex(stack,out,light,b.add(m.scale(radius)),m,color); vertex(stack,out,light,a.add(m.scale(radius)),m,color);
        }
    }
    private static void vertex(PoseStack stack, VertexConsumer out, int light, Vec3 p, Vec3 n, int color) {
        vertex(stack,out,light,p,n,color,.5F,.5F);
    }
    public static void vertex(PoseStack stack, VertexConsumer out, int light, Vec3 p, Vec3 n, int color,float u,float v) {
        out.vertex((stack.last()).pose(),(float)p.x,(float)p.y,(float)p.z).color(color).uv(u,v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal((stack.last()).normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
}
