package com.codex.blacklight.mixin;

import com.codex.blacklight.compat.CultivationCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skip only NPC/beast Qi and flat defense for configured native melee / true damage. The handler's
 * initial invulnerability checks, player defenses and all other listeners still run. */
@Pseudo
@Mixin(targets="com.xiaoxiang.cultivation.event.QiShieldHandler", remap=false)
public abstract class OptionalCultivationShieldMixin {
    @Inject(method={"handleNpcAttack", "handleNpcHurt", "handleBeastAttack", "handleBeastHurt"},
            at=@At("HEAD"), cancellable=true, require=0, remap=false)
    private static void blacklight$nativeTrueDamage(@Coerce Object event, @Coerce Object creature, CallbackInfo ci) {
        if (CultivationCompatibility.bypassMobShield(event)) ci.cancel();
    }
}
