package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.NativePlayerRenderScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional: preserve Blacklight's body and attached forms during its own draw only. */
@Pseudo
@Mixin(targets={"yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch",
        "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch"},remap=false)
public abstract class OptionalEpicFightPlayerRenderMixin {
    @Inject(method="overrideRender()Z",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void blacklight$keepNativeForms(CallbackInfoReturnable<Boolean> callback) {
        if(NativePlayerRenderScope.ownsPatch(this))callback.setReturnValue(false);
    }
}
