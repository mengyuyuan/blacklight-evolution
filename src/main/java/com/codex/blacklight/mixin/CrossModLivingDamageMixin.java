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
        var target=(LivingEntity)(Object)this;
        if(source==null||!Float.isFinite(amount))return false;
        var observation=com.codex.blacklight.combat.NativeHitObservation.begin(target,source,amount);
        boolean accepted=CrossModCombat.enhanced(target,source)
                ?original.call(CrossModCombat.offensiveSource(source),CrossModCombat.offensiveAmount(amount))
                :original.call(source,amount);
        return observation==null?accepted:observation.finish(accepted);
    }
    @WrapMethod(method="actuallyHurt")
    private void blacklight$nativeLivingArmour(DamageSource source,float amount,Operation<Void> original){
        var entity=(LivingEntity)(Object)this;
        if(!(entity instanceof net.minecraft.world.entity.Mob mob)||!com.codex.blacklight.infection.NativeTissueVitals.eligible(mob)){
            original.call(source,amount);return;
        }
        // This point receives the accepted cooldown difference. Continue the
        // source/vanilla armour, effect and damage-event chain exactly once.
        var decision=com.codex.blacklight.infection.NativeTissueDefense.incoming(mob,source,amount);
        float health=mob.getHealth(),absorption=mob.getAbsorptionAmount(),maximum=mob.getMaxHealth();
        original.call(source,decision.damage());
        if(mob.getHealth()<health||mob.getAbsorptionAmount()<absorption)
            com.codex.blacklight.infection.NativeTissueDefense.accepted(mob,decision,maximum);
    }
}
