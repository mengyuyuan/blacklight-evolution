package com.codex.blacklight.mixin;

import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Optional Potentia bridge; retain eligibility, stamina, damage events and death. */
@Pseudo
@Mixin(targets="com.potentia.entity.EntityTitan",remap=false)
public abstract class PotentiaDiveMixin {
    // Explicit development/production names: this optional class is not on the compile classpath.
    @Redirect(method={"hurt","m_6469_"},at=@At(value="INVOKE",target="Ljava/lang/Math;min(FF)F"),require=0,expect=1)
    private float blacklight$heightScaledImpact(float amount,float cap,DamageSource source,float incoming) {
        return Float.isFinite(amount) && amount > 0 && com.codex.blacklight.combat.CrossModCombat.bypassTitanLimit(source)
                ? amount : Math.min(amount,cap);
    }
}
