package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.ClientCarryPlacement;
import com.codex.blacklight.client.animation.ConsumeVictimPose;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** One stage-driven effect for vanilla, Blacklight and Gecko receivers, including large targets. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ConsumeAbsorptionFx {
    private ConsumeAbsorptionFx() {}
    public static Vec3 destination(LivingEntity victim,float partial,Vec3 placement) {
        var data=com.codex.blacklight.registry.ModAttachments.getData(victim, ModAttachments.CONSUME_VISUAL);
        var owner=victim.level().getEntity(data.consumerId());if(owner==null)return Vec3.ZERO;
        Vec3 chest=new Vec3(0,owner.getBbHeight()*.63,0);
        if(owner instanceof LivingEntity&&!ConsumeSequence.mountedStage(data.stage())){
            var form=PowerForm.values()[com.codex.blacklight.forge.WingMath.clamp(data.form(),0,PowerForm.values().length-1)];
            var row=ConsumePlacementTrack.contactFrame(ConsumeSequence.key(form,data.variant()),data.motionProgress(victim.level().getGameTime(),partial)).orElse(null);
            if(row!=null){Vec3 f=Vec3.directionFromRotation(0,data.facing()),r=new Vec3(-f.z,0,f.x);chest=r.scale(row[7]*.9375).add(f.scale(row[9]*.9375)).add(0,row[8]*.9375,0);}
        }
        return owner.getPosition(partial).add(chest).subtract(victim.getPosition(partial).add(placement));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        float partial=event.getPartialTick();
        var camera=event.getCamera().getPosition();var stack=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        var type=RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE);
        for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon mob)||mob.distanceToSqr(camera)>96*96)continue;
            var visual=com.codex.blacklight.registry.ModAttachments.getData(mob, ModAttachments.CONSUME_VISUAL);if(!visual.consuming()||mc.level.getEntity(visual.consumerId())==null)continue;
            var phase=ConsumeFracture.phase(mob,partial);if(phase.opening()<=0)continue;
            var placement=ClientCarryPlacement.consumeOffset(mob,partial);
            Vec3 position=mob.getPosition(partial).add(placement).subtract(camera);
            Vec3 to=destination(mob,partial,placement);
            stack.pushPose();stack.translate(position.x,position.y,position.z);
            draw(mob,partial,stack,buffers.getBuffer(type),mc.getEntityRenderDispatcher().getPackedLightCoords(mob,partial),to,phase);
            stack.popPose();
        }
        buffers.endBatch(type);
    }
    private static void draw(LivingEntity mob,float partial,PoseStack stack,VertexConsumer out,int light,Vec3 to,ConsumeFracture.Phase phase) {
        Vec3 center=ConsumeSequence.contact(mob).subtract(mob.getPosition(partial));
        drawPosed(stack,out,light,java.util.List.of(new ConsumeTendrils.Attachment(center,.35)),to,phase,mob.getId());
    }
    /** All inputs share the SAME render frame as the fractured skin, including its
     * skeleton, body projection, network interpolation and final fragment motion. */
    public static void drawPosed(PoseStack stack,VertexConsumer out,int light,java.util.List<ConsumeTendrils.Attachment> roots,Vec3 to,ConsumeFracture.Phase phase,int seed){
        if(roots.isEmpty())return;
        ConsumeTendrils.draw(stack,out,light,roots,to,phase,seed);
        // Brief moving flecks at the actual wound. They cannot remain in a frozen
        // radial halo for the rest of the consume animation.
        float age=(phase.progress()-phase.contact())/.14F;
        if(age<=0||age>=1)return;
        float burst=(float)Math.sin(age*Math.PI)*phase.opening();
        int count=phase.style()==ConsumeFracture.Style.CRUSH?9:5;
        for(int i=0;i<count;i++){
            Vec3 root=roots.get(i%roots.size()).center();double a=i*2.399963+seed*.17;
            Vec3 velocity=new Vec3(Math.cos(a)*.20,.16+(i%3)*.04,Math.sin(a)*.20);
            Vec3 point=root.add(velocity.scale(age)).add(0,-.25*age*age,0);
            Vec3 direction=velocity.normalize();double size=(.016+(i%3)*.006)*burst;
            ConsumedMobVisuals.tube(stack,out,light,point.subtract(direction.scale(size)),point.add(direction.scale(size)),size,i%2==0?0xff66242e:0xff28181d);
        }
    }
    private static float smooth(float t){t=net.minecraft.util.Mth.clamp(t,0,1);return t*t*(3-2*t);}
}
