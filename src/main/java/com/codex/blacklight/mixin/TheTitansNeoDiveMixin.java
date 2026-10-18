package com.codex.blacklight.mixin;

import com.codex.blacklight.combat.CrossModCombat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Optional TheTitansNeo 1.0.8 bridge: retain its own damage and death pipeline. */
@Pseudo
@Mixin(targets="net.byAqua3.thetitansneo.entity.titan.EntityTitan",remap=false)
public abstract class TheTitansNeoDiveMixin {
    // TheTitansNeo calls its own baseHurt instead of LivingEntity.hurt.
    @WrapMethod(method="hurt", require=0)
    private boolean blacklight$nativeTitanAttack(DamageSource source,float amount,Operation<Boolean> original) {
        if(CrossModCombat.enhanced((LivingEntity)(Object)this,source))
            return original.call(CrossModCombat.offensiveSource(source),CrossModCombat.offensiveAmount(amount));
        return original.call(source,amount);
    }

    // Both the comparison and assignment use 1000. No HP writes, repeated hits,
    // immunity cancellation or changes to other weapons are necessary.
    @ModifyConstant(method="hurt",constant=@Constant(floatValue=1000F),require=0,expect=2)
    private float blacklight$uncapDive(float limit,DamageSource source,float amount) {
        return Float.isFinite(amount) && amount > 0 && com.codex.blacklight.combat.CrossModCombat.bypassTitanLimit(source)
                ? Float.MAX_VALUE : limit;
    }
}
