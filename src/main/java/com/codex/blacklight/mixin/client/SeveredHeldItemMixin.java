package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.DismemberVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class SeveredHeldItemMixin {
    @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
    private void blacklight$severedHand(LivingEntity entity,ItemStack item,ItemDisplayContext display,HumanoidArm arm,PoseStack stack,MultiBufferSource buffers,int light,CallbackInfo ci){
        if(DismemberVisuals.missingArm(entity,arm))ci.cancel();
    }
}
