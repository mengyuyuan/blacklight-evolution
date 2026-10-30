package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.vehicle.PrototypeVehicleTargeting;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class PrototypeVehiclePickMixin {
    @Inject(method="pick(F)V",at=@At("TAIL"))
    private void blacklight$vehicleFromDisplayedCamera(float partial,CallbackInfo ci){
        PrototypeVehicleTargeting.update(partial);
    }
}
