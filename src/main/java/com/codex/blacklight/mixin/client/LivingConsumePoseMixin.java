package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.animation.ConsumeVictimPose;
import com.codex.blacklight.client.renderer.LivingRenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply only after vanilla has set the species pose; restore shared model parts after rendering. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingConsumePoseMixin {
    @Shadow protected EntityModel<?> model;
    @WrapMethod(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void blacklight$scope(LivingEntity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffer,int light,Operation<Void> original) {
        try (var scope = LivingRenderScope.begin(stack)) {
            try { original.call(entity,yaw,partial,stack,buffer,light); }
            finally { ConsumeVictimPose.restore(model); }
        }
    }
    @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",shift=At.Shift.AFTER))
    private void blacklight$victim(LivingEntity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffer,int light,CallbackInfo ci) {
        ConsumeVictimPose.vanilla(model,entity,partial);
    }
    @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"))
    private void blacklight$restore(LivingEntity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffer,int light,CallbackInfo ci) {
        com.codex.blacklight.client.renderer.InfectedMorphology.flush(entity,buffer);
        ConsumeVictimPose.restore(model);
    }
}
