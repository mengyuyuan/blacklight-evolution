package com.codex.blacklight.mixin;

import com.codex.blacklight.power.CriticalMassSystem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Respect all Forge damage cancellations before committing overhealth. */
@Mixin(value=ForgeHooks.class,remap=false)
public abstract class ForgeCriticalMassDamageMixin {
    @Inject(method="onLivingDamage",at=@At("RETURN"),cancellable=true)
    private static void blacklight$absorbFinal(LivingEntity entity,DamageSource source,float damage,
                                              CallbackInfoReturnable<Float> callback){
        float remaining = CriticalMassSystem.absorbFinalDamage(entity,source,callback.getReturnValue());
        callback.setReturnValue(com.codex.blacklight.power.AdaptivePassiveSystem.finalDamage(entity,source,remaining));
    }
}
