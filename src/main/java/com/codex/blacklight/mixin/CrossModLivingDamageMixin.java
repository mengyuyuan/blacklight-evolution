package com.codex.blacklight.mixin;

import com.codex.blacklight.combat.CrossModCombat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class CrossModLivingDamageMixin {
    @WrapMethod(method="hurt")
    private boolean blacklight$nativeExternalAttack(DamageSource source, float amount, Operation<Boolean> original) {
        if (CrossModCombat.enhanced((LivingEntity)(Object)this, source))
            return original.call(CrossModCombat.offensiveSource(source), CrossModCombat.offensiveAmount(amount));
        return original.call(source, amount);
    }
}
