package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.NativePlayerRenderScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A second body's root transform must not be added to Blacklight's authored pose.
 * Animation layers keep ticking and become visible again on the normal draw. */
@Pseudo
@Mixin(targets="dev.kosmx.playerAnim.core.impl.AnimationProcessor",remap=false)
public abstract class OptionalPlayerAnimatorRenderMixin {
    @Inject(method="isActive()Z",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void blacklight$ownBodyTransform(CallbackInfoReturnable<Boolean> callback) {
        if(NativePlayerRenderScope.active())callback.setReturnValue(false);
    }
}
