package com.codex.blacklight.silentqa;

import com.mojang.blaze3d.audio.Listener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Mute this OpenAL context while retaining real source lifecycle/pitch checks. */
@Mixin(Listener.class)
public abstract class MutedListenerMixin {
    @ModifyArg(method="setGain",at=@At(value="INVOKE",target="Lorg/lwjgl/openal/AL10;alListenerf(IF)V"),index=1)
    private float blacklight$muteOutput(float original){return 0F;}
}
