package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.AdaptiveRagdoll;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.*;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Captures the real animated Gecko bone without changing its controller or shared model. */
@Mixin(value=GeoEntityRenderer.class,remap=false)
public abstract class GeoRagdollMixin {
    @WrapOperation(method="renderRecursively(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V",
            at=@At(value="INVOKE",target="Lsoftware/bernie/geckolib/renderer/GeoEntityRenderer;renderCubesOfBone(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void blacklight$bone(GeoEntityRenderer<?> renderer,PoseStack stack,GeoBone bone,VertexConsumer out,int light,int overlay,int color,Operation<Void> original){
        stack.pushPose();
        try{RenderUtil.translateToPivotPoint(stack,bone);AdaptiveRagdoll.bone(bone,bone.getParent(),stack.last().pose(),bone.getName());}
        finally{stack.popPose();}
        try{original.call(renderer,stack,bone,out,light,overlay,color);}
        finally{AdaptiveRagdoll.clearPart();}
    }
}
