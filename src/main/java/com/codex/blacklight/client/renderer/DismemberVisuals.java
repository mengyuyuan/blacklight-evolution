package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.HeavyBlow;
import com.codex.blacklight.combat.HitReaction;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Corpse side of HeavyBlow.dismember: the struck part is hidden on the dying body and a copy
 * of it is thrown along the blow. Shared models are restored after every render, even when a
 * later handler cancelled the render and no Post event arrives.
 */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class DismemberVisuals {
    private static final java.util.List<ModelPart> HIDDEN=new java.util.ArrayList<>();
    private static final java.util.Map<LivingEntity,Long> STARTED=new java.util.WeakHashMap<>();
    private record Thrown(LivingEntity mob,ModelPart part,net.minecraft.client.model.geom.PartPose pose,net.minecraft.resources.ResourceLocation texture,int light,float age){}
    private static final java.util.List<Thrown> QUEUE=new java.util.ArrayList<>(),PENDING=new java.util.ArrayList<>();
    private DismemberVisuals(){}

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void before(RenderLivingEvent.Pre<?,?> event){
        restore();
        if(event.isCanceled())return;
        var mob=event.getEntity();
        int mask=ModAttachments.peekData(mob,ModAttachments.DISMEMBER,0);
        if(mask==0||!(event.getRenderer().getModel() instanceof HumanoidModel<?> model))return;
        long now=mob.level().getGameTime();
        long started=STARTED.computeIfAbsent(mob,m->now);
        var parts=parts(model,mask);
        float age=now-started+event.getPartialTick();
        for(var part:parts){
            if(!part.visible)continue;
            thrown(event,mob,part,age,mask);
            part.visible=false;HIDDEN.add(part);
        }
    }
    @SubscribeEvent public static void after(RenderLivingEvent.Post<?,?> event){
        // The shared model now holds this body's posed joints; the torn part leaves from there.
        for(var q:PENDING)if(q.mob()==event.getEntity())QUEUE.add(new Thrown(q.mob(),q.part(),q.part().storePose(),q.texture(),q.light(),q.age()));
        PENDING.clear();restore();
    }
    private static void restore(){for(var part:HIDDEN)part.visible=true;HIDDEN.clear();PENDING.clear();}

    private static java.util.List<ModelPart> parts(HumanoidModel<?> m,int mask){
        var out=new java.util.ArrayList<ModelPart>();
        if((mask&HeavyBlow.DISMEMBER_HEAD)!=0){out.add(m.head);out.add(m.hat);}
        if((mask&HeavyBlow.DISMEMBER_RIGHT_ARM)!=0){out.add(m.rightArm);if(m instanceof PlayerModel<?> p)out.add(p.rightSleeve);}
        if((mask&HeavyBlow.DISMEMBER_LEFT_ARM)!=0){out.add(m.leftArm);if(m instanceof PlayerModel<?> p)out.add(p.leftSleeve);}
        return out;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private static void thrown(RenderLivingEvent.Pre<?,?> event,LivingEntity mob,ModelPart part,float age,int mask){
        var texture=((LivingEntityRenderer)event.getRenderer()).getTextureLocation(mob);
        if(QUEUE.size()+PENDING.size()<64)PENDING.add(new Thrown(mob,part,null,texture,event.getPackedLight(),age));
    }

    /** The torn part follows a short ballistic arc along the blow, tumbling until it lands. */
    @SubscribeEvent public static void world(net.neoforged.neoforge.client.event.RenderLevelStageEvent event){
        if(event.getStage()!=net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES||QUEUE.isEmpty())return;
        var mc=net.minecraft.client.Minecraft.getInstance();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera=event.getCamera().getPosition();var stack=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        for(var q:QUEUE){
            var mob=q.mob();var part=q.part();
            var hit=ModAttachments.peekData(mob,ModAttachments.HIT_REACTION,HitReaction.EMPTY);
            float yaw=hit.started()==0?mob.getYRot():hit.yaw();
            double rad=Math.toRadians(yaw),dx=-Math.sin(rad),dz=Math.cos(rad);
            float pivot=(24-q.pose().y)/16F;
            // Rise and fall (v=.32, g=.045 blocks/tick) until the part reaches the floor.
            double floor=-(1.5-pivot)+.12,disc=.32*.32-4*.045*floor;
            float land=(float)((.32+Math.sqrt(Math.max(0,disc)))/(2*.045));
            float t=Math.min(q.age(),land);
            double up=.32*t-.045*t*t,out=.24*t;
            var at=mob.getPosition(partial).subtract(camera);
            stack.pushPose();
            stack.translate(at.x+dx*out,at.y+up,at.z+dz*out);
            stack.mulPose(Axis.YP.rotationDegrees(180-mob.yBodyRot));
            stack.scale(-1,-1,1);
            stack.translate(0,-1.501F,0);
            var saved=part.storePose();boolean was=part.visible;
            part.loadPose(q.pose());
            // Tumble around the part's own joint.
            stack.translate(part.x/16F,part.y/16F,part.z/16F);
            stack.mulPose(Axis.XP.rotationDegrees(t*31));stack.mulPose(Axis.ZP.rotationDegrees(t*17));
            stack.translate(-part.x/16F,-part.y/16F,-part.z/16F);
            part.visible=true;
            part.render(stack,buffers.getBuffer(RenderType.entityCutoutNoCull(q.texture())),q.light(),OverlayTexture.NO_OVERLAY);
            part.visible=was;part.loadPose(saved);
            stack.popPose();
        }
        QUEUE.clear();
        buffers.endBatch();
    }
}
