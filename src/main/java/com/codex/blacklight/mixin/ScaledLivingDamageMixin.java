package com.codex.blacklight.mixin;

import com.codex.blacklight.combat.ForgeDamageEvent;
import com.codex.blacklight.power.CombatScale;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class ScaledLivingDamageMixin {
    @ModifyVariable(method="actuallyHurt",at=@At(value="STORE",ordinal=2),argsOnly=true,ordinal=0)
    private float blacklight$convertBeforeAbsorption(float damage, @Local(argsOnly=true) DamageSource source){
        return CombatScale.finalDamage((LivingEntity)(Object)this,source,damage);
    }
    @Inject(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V",shift=At.Shift.AFTER))
    private void blacklight$afterHealthLoss(DamageSource source,float damage,CallbackInfo callback,@Local(index=3) float healthDamage){
        LivingEntity entity=(LivingEntity)(Object)this;
        if(!entity.level().isClientSide&&healthDamage>0)MinecraftForge.EVENT_BUS.post(new ForgeDamageEvent.Post(entity,source,healthDamage));
    }
}
