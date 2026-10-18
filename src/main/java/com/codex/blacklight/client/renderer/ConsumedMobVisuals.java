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
    private static final java.util.Set<LivingEntity> SCALED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static final java.util.Set<LivingEntity> RENDERING = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private ConsumedMobVisuals() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void before(RenderLivingEvent.Pre<?, ?> event) {
        var mob = event.getEntity();
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
            var stack=event.getPoseStack();stack.pushPose();
            if(feeding)DevourerFeedingFx.applyVictim(mob,event.getPartialTick(),stack);
            com.codex.blacklight.client.animation.HitBodyPose.apply(mob,stack,event.getPartialTick());
            SCALED.add(mob);
            return;
        }
        var owner = mob.level().getEntity(data.consumerId());
        if (owner == null) return;
        float partial = event.getPartialTick(), t = data.progress(mob.level().getGameTime(), partial);
        Vec3 placement=com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(mob,partial);
        Vec3 delta = owner.getPosition(partial).subtract(mob.getPosition(partial).add(placement));
        var stack = event.getPoseStack();
        stack.pushPose();stack.translate(placement.x,placement.y,placement.z);
        var out = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(WHITE));
        whip(mob, stack, out, event.getPackedLight(), delta, tetherProgress(mob,partial));
        if (t >= .995F && !com.codex.blacklight.combat.ConsumeSequence.boss(mob)) { stack.popPose();event.setCanceled(true); return; }
        ConsumeFracture fragments=ConsumeFracture.visible(mob,partial)?new ConsumeFracture(mob,partial,stack,event.getMultiBufferSource(),ConsumeAbsorptionFx.destination(mob,partial,placement)):null;
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
        } else SCALED.add(mob);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void renderVanilla(RenderLivingEvent.Pre<?,?> event,LivingEntity mob,net.minecraft.client.renderer.MultiBufferSource buffers){
        ((net.minecraft.client.renderer.entity.LivingEntityRenderer)event.getRenderer()).render(mob,mob.getYRot(),event.getPartialTick(),event.getPoseStack(),buffers,event.getPackedLight());
    }
    @SubscribeEvent
    public static void after(RenderLivingEvent.Post<?, ?> event) {
        if (SCALED.remove(event.getEntity())) event.getPoseStack().popPose();
    }

    public static float tetherProgress(LivingEntity mob,float partial) {
        var data=com.codex.blacklight.registry.ModAttachments.getData(mob, ModAttachments.CONSUME_VISUAL);
        if(data.stage()==com.codex.blacklight.combat.ConsumeVisual.WHIP_PULL)
            return Math.min(.44F,(mob.level().getGameTime()-data.started()+partial)/60F);
        if(data.stage()==com.codex.blacklight.combat.ConsumeVisual.CAPTURE)
            return (mob.level().getGameTime()-data.started()+partial)/60F;
        if(data.stage()==com.codex.blacklight.combat.ConsumeVisual.HOLD)return .44F;
        if(data.consuming())return .44F+data.progress(mob.level().getGameTime(),partial)*.5F;
        return 1;
    }
    /** A thick, continuous cable and tightening coil distinguish whip capture from generic tendrils. */
    public static void whip(LivingEntity mob, PoseStack stack, VertexConsumer out, int light, Vec3 delta, float t) {
        if (com.codex.blacklight.registry.ModAttachments.getData(mob, ModAttachments.CONSUME_VISUAL).form() != PowerForm.WHIPFIST.ordinal() || t > .9F) return;
        if (t < .185F) return;
        stack.pushPose();
        com.codex.blacklight.client.ClientPoseMatrices.multiply(stack,com.codex.blacklight.client.animation.ConsumeVictimPose.effectTransform(mob,net.minecraft.client.Minecraft.getInstance().getFrameTime()));
        double radius = mob.getBbWidth() * .58 * (1-net.minecraft.util.Mth.clamp((t-.85)/.05, 0, 1));
        for (int i = 0; i < 48; i++) {
            double a = i / 48.0, b = (i+1) / 48.0;
            Vec3 p = new Vec3(Math.cos(a*Math.PI*5)*radius, mob.getBbHeight()*(.22+.6*a), Math.sin(a*Math.PI*5)*radius);
            Vec3 q = new Vec3(Math.cos(b*Math.PI*5)*radius, mob.getBbHeight()*(.22+.6*b), Math.sin(b*Math.PI*5)*radius);
            tube(stack, out, light, p, q, .045, i%4==0 ? 0xff833b32 : 0xff35292a);
        }
        stack.popPose();
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
        out.vertex((stack.last()).pose(),(float)p.x,(float)p.y,(float)p.z).color(color).uv(.5F,.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal((stack.last()).normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
}
