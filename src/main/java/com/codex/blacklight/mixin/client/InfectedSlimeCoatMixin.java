package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.InfectedMorphology;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The translucent native shell must reveal the authored wounds in its inner body. */
@Mixin(SlimeOuterLayer.class)
public abstract class InfectedSlimeCoatMixin {
    @Shadow @Final private EntityModel<?> model;
    @Inject(method="render",at=@At("HEAD"))
    private void blacklight$coat(PoseStack stack,MultiBufferSource buffers,int light,LivingEntity entity,
            float swing,float amount,float partial,float age,float yaw,float pitch,CallbackInfo ci){
        InfectedMorphology.copiedSkinLayer(entity,model,
                ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png"));
    }
}
