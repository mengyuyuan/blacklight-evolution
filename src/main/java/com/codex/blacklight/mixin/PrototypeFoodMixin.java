package com.codex.blacklight.mixin;

import com.codex.blacklight.power.PrototypeMetabolism;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PrototypeFoodMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;tick(Lnet/minecraft/world/entity/player/Player;)V"))
    private void blacklight$suspendHunger(FoodData food, Player player, Operation<Void> original) {
        // Keep the saved food state; neither starvation nor food-based healing
        // applies while biomass powers are active.
        if (!PrototypeMetabolism.hungerSuspended(player)) original.call(food, player);
    }

    @Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
    private void blacklight$skipExhaustion(float exhaustion, CallbackInfo callback) {
        if (PrototypeMetabolism.hungerSuspended((Player)(Object)this)) callback.cancel();
    }
}
