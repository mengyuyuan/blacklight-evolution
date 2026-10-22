package com.codex.blacklight.mixin;

import com.codex.blacklight.combat.ConsumeControl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ConsumeEffectMixin {
    @Inject(method="canBeAffected",at=@At("HEAD"),cancellable=true)
    private void blacklight$rejectControl(MobEffectInstance effect,CallbackInfoReturnable<Boolean> cir){
        if(ConsumeControl.controlEffect(effect)&&ConsumeControl.protectedEntity((LivingEntity)(Object)this))cir.setReturnValue(false);
    }
}
