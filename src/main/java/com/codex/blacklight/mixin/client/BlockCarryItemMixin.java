package com.codex.blacklight.mixin.client;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.renderer.entity.layers.ItemInHandLayer.class)
public abstract class BlockCarryItemMixin {
 @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
 private void blacklight$carry(net.minecraft.world.entity.LivingEntity entity,net.minecraft.world.item.ItemStack item,net.minecraft.world.item.ItemDisplayContext context,net.minecraft.world.entity.HumanoidArm arm,com.mojang.blaze3d.vertex.PoseStack stack,net.minecraft.client.renderer.MultiBufferSource buffer,int light,CallbackInfo ci){
  if(entity instanceof net.minecraft.world.entity.player.Player p&&com.codex.blacklight.combat.BlockThrowSystem.held(p)!=null)ci.cancel();
 }
}
