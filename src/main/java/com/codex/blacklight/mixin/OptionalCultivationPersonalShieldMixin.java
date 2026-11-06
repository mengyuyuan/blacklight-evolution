package com.codex.blacklight.mixin;

import com.codex.blacklight.compat.CultivationCompatibility;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The optional mod's personal shield runs before its separate Qi handler.
 * Scope the bridge to configured native melee and formal true damage on NPCs.
 * Player protection, death vetoes and unrelated event listeners still execute. */
@Pseudo
@Mixin(targets="com.xiaoxiang.cultivation.event.PersonalSpiritShieldHandler",remap=false)
public abstract class OptionalCultivationPersonalShieldMixin {
    @Inject(method={"onLivingAttack","onLivingHurt","onLivingDamage"},at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private static void blacklight$nativeTrueDamage(@Coerce Object event,CallbackInfo ci){
        if(CultivationCompatibility.bypassMobShield(event))ci.cancel();
    }
}
