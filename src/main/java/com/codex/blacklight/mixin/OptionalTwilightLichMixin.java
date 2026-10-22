package com.codex.blacklight.mixin;

import com.codex.blacklight.integration.twilight.TwilightCombat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets="twilightforest.entity.boss.Lich", remap=false)
public abstract class OptionalTwilightLichMixin {
    @Shadow(remap=false) public abstract int getShieldStrength();
    @Shadow(remap=false) public abstract void setShieldStrength(int value);

    @WrapMethod(method={"hurt", "m_6469_"}, require=0)
    private boolean blacklight$breakLichShield(DamageSource source, float amount, Operation<Boolean> original) {
        var self = (LivingEntity)(Object)this;
        if (!TwilightCombat.applies(self, source, amount)) return original.call(source, amount);
        var hit = TwilightCombat.prepare(self, source, amount);
        boolean accepted = original.call(hit.source(), hit.amount());
        // Damage rejected by an event must not grant a successful shield break.
        if (accepted && getShieldStrength() > 0) {
            setShieldStrength(0);
            self.playSound(SoundEvents.SHIELD_BREAK, 1.1F, .75F);
        }
        return accepted;
    }
}
