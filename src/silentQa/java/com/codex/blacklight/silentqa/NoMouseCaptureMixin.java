package com.codex.blacklight.silentqa;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class NoMouseCaptureMixin {
    @Inject(method="grabMouse",at=@At("HEAD"),cancellable=true)
    private void blacklight$neverCaptureMouse(CallbackInfo ci){ci.cancel();}
}
