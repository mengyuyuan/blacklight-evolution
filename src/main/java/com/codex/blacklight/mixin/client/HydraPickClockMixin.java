package com.codex.blacklight.mixin.client;

import com.codex.blacklight.combat.HydraPicking;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class HydraPickClockMixin {
    @Inject(method="pick(F)V",at=@At("HEAD")) private void blacklight$hydraPickClock(float partial,CallbackInfo ci){HydraPicking.begin(partial);}
    @Inject(method="pick(F)V",at=@At("RETURN")) private void blacklight$hydraPickDone(float partial,CallbackInfo ci){HydraPicking.end();}
}
