package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.PigRagdoll;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.SaddleLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The vanilla saddle uses a separate, still-standing pig model. Do not leave that ghost behind. */
@Mixin(SaddleLayer.class)
public abstract class RagdollSaddleMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void blacklight$physicalSaddle(PoseStack stack,MultiBufferSource buffers,int light,Entity entity,
            float swing,float amount,float partial,float age,float yaw,float pitch,CallbackInfo ci){
        if(entity instanceof Pig pig&&PigRagdoll.active(pig))ci.cancel();
    }
}
