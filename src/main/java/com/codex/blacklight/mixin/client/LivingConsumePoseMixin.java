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
    @Inject(method="getOverlayCoords",at=@At("HEAD"),cancellable=true)
    private static void blacklight$corpseOverlay(LivingEntity entity,float white,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> ci){
        if(entity.isDeadOrDying()&&(com.codex.blacklight.client.animation.ImpactRagdoll.active(entity)||com.codex.blacklight.client.renderer.AdaptiveRagdoll.active(entity)))
            ci.setReturnValue(net.minecraft.client.renderer.texture.OverlayTexture.pack(white,com.codex.blacklight.client.animation.RagdollDrive.hurtFlash(entity)));
    }
    @Inject(method="setupRotations",at=@At("HEAD"),cancellable=true)
    private void blacklight$physicalRoot(LivingEntity entity,PoseStack stack,float bob,float bodyYaw,float partial,float scale,CallbackInfo ci){
        if(com.codex.blacklight.client.animation.ImpactRagdoll.active(entity)||com.codex.blacklight.client.renderer.AdaptiveRagdoll.active(entity)){
            stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-bodyYaw));ci.cancel();
        }
    }
    @WrapMethod(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void blacklight$scope(LivingEntity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffer,int light,Operation<Void> original) {
        com.codex.blacklight.client.renderer.AdaptiveRagdoll.bindModel(entity,model);
        try (var actor = com.codex.blacklight.client.renderer.NativeRenderScope.enter(entity);
             var severed = com.codex.blacklight.client.renderer.DismemberVisuals.scope();
             var scope = LivingRenderScope.begin(stack);
             var tissue = com.codex.blacklight.client.renderer.NativeInfectionVisuals.begin(entity,model,
                     ()->((LivingEntityRenderer)(Object)this).getTextureLocation(entity),stack,partial)) {
            try { original.call(entity,yaw,partial,stack,tissue==null?buffer:com.codex.blacklight.client.renderer.NativeInfectionVisuals.buffers(buffer),light); }
            finally { ConsumeVictimPose.restore(model); }
        }
    }
    @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",shift=At.Shift.AFTER))
    private void blacklight$victim(LivingEntity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffer,int light,CallbackInfo ci) {
        ConsumeVictimPose.vanilla(model,entity,partial);
        com.codex.blacklight.client.renderer.DismemberVisuals.pose(model,entity,partial);
    }
    @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"))
    private void blacklight$restore(LivingEntity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffer,int light,CallbackInfo ci) {
        com.codex.blacklight.client.renderer.InfectedMorphology.flush(entity,buffer);
        ConsumeVictimPose.restore(model);
    }
}
