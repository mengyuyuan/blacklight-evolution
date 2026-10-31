package com.codex.blacklight.mixin;

import com.codex.blacklight.integration.twilight.TwilightCombat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="twilightforest.entity.boss.Hydra", remap=false)
public abstract class OptionalTwilightHydraMixin {
    @Shadow(remap=false) private int ticksSinceDamaged;

    @WrapMethod(method={"hurt", "m_6469_"}, require=0)
    private boolean blacklight$damageHydra(DamageSource source, float amount, Operation<Boolean> original) {
        var self = (LivingEntity)(Object)this;
        if (!TwilightCombat.applies(self, source, amount)) return original.call(source, amount);
        var hit = TwilightCombat.prepare(self, source, amount);
        float vitality = self.getHealth() + self.getAbsorptionAmount();
        boolean accepted = original.call(hit.source(), hit.amount());
        accepted &= self.getHealth() + self.getAbsorptionAmount() < vitality;
        if (accepted) ticksSinceDamaged = 0;
        return accepted;
    }

    @Inject(method="attackEntityFromPart", at=@At("HEAD"), cancellable=true, require=0, remap=false)
    private void blacklight$damageHydraPart(@Coerce Object part, DamageSource source, float amount,
                                          CallbackInfoReturnable<Boolean> result) {
        var self = (LivingEntity)(Object)this;
        if (TwilightCombat.applies(self, source, amount)) result.setReturnValue(self.hurt(source, amount));
    }
}
