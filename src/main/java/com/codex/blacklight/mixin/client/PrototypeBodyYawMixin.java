package com.codex.blacklight.mixin.client;

import com.codex.blacklight.city.HydraTransport;
import com.codex.blacklight.client.camera.CameraCompatibility;
import com.codex.blacklight.client.camera.PrototypeChaseCamera;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Align the local Prototype body's facing after Blacklight resolves movement ownership. */
@Mixin(targets = "com.codex.blacklight.client.ClientMovementEvents", remap = false)
public abstract class PrototypeBodyYawMixin {
    @Inject(
            method = "lockWeaponMovement(Lnet/minecraftforge/client/event/MovementInputUpdateEvent;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false)
    private static void blacklight$alignPrototypeBodyWithView(MovementInputUpdateEvent event, CallbackInfo callback) {
        // Direct field access, not reflection: Forge production jars run on SRG member names.
        var player = event.getEntity();
        if (Minecraft.getInstance().player != player || HydraTransport.active(player)) return;
        var state = ModAttachments.peekData(player, ModAttachments.PLAYER_STATE);
        if (!state.isInfected() || !state.isPrototypeMode()) return;
        float yaw = player.getYRot();
        if (PrototypeChaseCamera.ownsView() || CameraCompatibility.externalOwnsView()) {
            float control = PrototypeChaseCamera.controlYaw();
            if (Float.isFinite(control)) yaw = control;
        }
        if (!Float.isFinite(yaw)) return;
        player.setYBodyRot(yaw);
        player.yBodyRotO = yaw;
    }
}