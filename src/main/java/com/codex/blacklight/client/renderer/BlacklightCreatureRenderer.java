package com.codex.blacklight.client.renderer;
import com.codex.blacklight.client.model.BlacklightCreatureModel;
import com.codex.blacklight.client.animation.ConsumeVictimPose;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import java.util.function.Function;
public final class BlacklightCreatureRenderer<T extends LivingEntity & GeoEntity> extends AnatomicalGeoRenderer<T> {
    public BlacklightCreatureRenderer(EntityRendererProvider.Context context,Function<T,String> asset){super(context,new BlacklightCreatureModel<>(asset));shadowRadius=.65F;}
    @Override public void render(T entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffer,int light){
        var visual=com.codex.blacklight.registry.ModAttachments.getData(entity, ModAttachments.CONSUME_VISUAL);float progress=visual.progress(entity.level().getGameTime(),partial);
        var placement=com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(entity,partial);
        stack.pushPose();if(visual.active())stack.translate(placement.x,placement.y,placement.z);
        ConsumeFracture fragments=ConsumeFracture.visible(entity,partial)?new ConsumeFracture(entity,partial,stack,buffer,ConsumeAbsorptionFx.destination(entity,partial,placement)):null;
        if(visual.active())ConsumeVictimPose.body(entity,stack,partial);
        else if(!(entity instanceof com.codex.blacklight.entity.BlacklightHive)&&!com.codex.blacklight.client.animation.NativeReceiverPose.available(entity,partial))com.codex.blacklight.client.animation.HitBodyPose.apply(entity,stack,partial);
        try{if(progress<.995F)super.render(entity,yaw,partial,stack,fragments==null?buffer:fragments,light);if(fragments!=null)fragments.finish();}finally{stack.popPose();}
        var owner=entity.level().getEntity(visual.consumerId());
        if(owner!=null&&visual.active()){
            var delta=owner.getPosition(partial).subtract(entity.getPosition(partial));
            var out=buffer.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            ConsumedMobVisuals.whip(entity,stack,out,light,delta,ConsumedMobVisuals.tetherProgress(entity,partial));
        }
    }
}
