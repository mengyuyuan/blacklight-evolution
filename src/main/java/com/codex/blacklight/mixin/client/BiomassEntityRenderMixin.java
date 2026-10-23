package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.BiomassCoatBuffers;
import com.codex.blacklight.power.BiomassState;
import com.codex.blacklight.power.BiomassSystem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Includes custom/Gecko renderers which never fire RenderLivingEvent. */
@Mixin(EntityRenderDispatcher.class)
public abstract class BiomassEntityRenderMixin {
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void blacklight$coat(EntityRenderer<?> renderer,Entity entity,float yaw,float partial,
                                PoseStack stack,MultiBufferSource buffers,int light,Operation<Void> original){
        var owner=entity instanceof LivingEntity?BiomassSystem.claimant(entity):null;
        if(owner==null){original.call(renderer,entity,yaw,partial,stack,buffers,light);return;}
        float progress=BiomassSystem.state(owner).progress(entity.level().getGameTime()+partial,com.codex.blacklight.power.ConsumeProgression.absorbTicks(owner));
        var coat=new BiomassCoatBuffers((LivingEntity)entity,stack,buffers,
                Math.min(1,progress*1.45F),com.codex.blacklight.client.renderer.BiomassSurfaceBounds.ease((progress-.65F)/.35F),entity.tickCount+partial);
        original.call(renderer,entity,yaw,partial,stack,coat,light);
        coat.finish();
    }
}
