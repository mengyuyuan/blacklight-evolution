package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.animation.ConsumeVictimPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fur and other copied model layers must follow the captured animal's limbs too. */
@Mixin(RenderLayer.class)
public abstract class ConsumeLayerPoseMixin {
    @Inject(method="coloredCutoutModelCopyLayerRender",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",shift=At.Shift.AFTER))
    private static void blacklight$pose(EntityModel<?> parent,EntityModel<?> model,ResourceLocation texture,PoseStack stack,MultiBufferSource buffer,int light,
            LivingEntity entity,float swing,float amount,float age,float yaw,float pitch,float partial,float red,float green,float blue,CallbackInfo ci) {
        ConsumeVictimPose.vanilla(model,entity,partial);
        if(buffer instanceof com.codex.blacklight.client.renderer.ConsumeFracture fracture)fracture.bindModel(model);
        com.codex.blacklight.client.renderer.InfectedMorphology.copiedSkinLayer(entity,model,texture);
    }
    @Inject(method="coloredCutoutModelCopyLayerRender",at=@At("RETURN"))
    private static void blacklight$restore(EntityModel<?> parent,EntityModel<?> model,ResourceLocation texture,PoseStack stack,MultiBufferSource buffer,int light,
            LivingEntity entity,float swing,float amount,float age,float yaw,float pitch,float partial,float red,float green,float blue,CallbackInfo ci) {
        ConsumeVictimPose.restore(model);
    }
}
