package com.codex.blacklight.mixin.client;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prototype-specific sprint food rules and particle suppression. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "hasEnoughFoodToStartSprinting", at = @At("HEAD"), cancellable = true)
    private void blacklight$ignoreSprintHunger(CallbackInfoReturnable<Boolean> callback) {
        // Vanilla uses this for both entering sprint and cancelling an existing sprint.
        if (com.codex.blacklight.power.PrototypeMetabolism.hungerSuspended((LocalPlayer)(Object)this)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "canSpawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void blacklight$hideVanillaSprintTrail(CallbackInfoReturnable<Boolean> callback) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        if (com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE).isPrototypeMode()) {
            callback.setReturnValue(false);
        }
    }
}
