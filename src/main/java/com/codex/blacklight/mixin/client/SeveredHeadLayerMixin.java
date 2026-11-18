package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.DismemberVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomHeadLayer.class)
public abstract class SeveredHeadLayerMixin {
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",at=@At("HEAD"),cancellable=true)
    private void blacklight$severedHead(PoseStack stack,MultiBufferSource buffers,int light,LivingEntity entity,float swing,float amount,float partial,float age,float yaw,float pitch,CallbackInfo ci){
        if(DismemberVisuals.missingHead(entity))ci.cancel();
    }
}
