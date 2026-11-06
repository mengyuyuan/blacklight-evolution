package com.codex.blacklight.mixin.client;

import com.codex.blacklight.compat.EpicFightBlacklightBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Align the local Prototype body's facing after Blacklight resolves movement ownership. */
@Mixin(targets = "com.codex.blacklight.client.ClientMovementEvents", remap = false)
public abstract class PrototypeBodyYawMixin {
    @Inject(
            method = "lockWeaponMovement(Lnet/neoforged/neoforge/client/event/MovementInputUpdateEvent;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false)
    private static void blacklight$alignPrototypeBodyWithView(
            @Coerce Object event, CallbackInfo callback) {
        EpicFightBlacklightBridge.alignPrototypeBodyYaw(event);
    }
}
