package com.codex.blacklight.mixin;

import com.codex.blacklight.combat.CrossModCombat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemEntity.class)
public abstract class CrossModItemDamageMixin {
    @WrapMethod(method="hurt")
    private boolean blacklight$externalItemProtection(DamageSource source, float amount, Operation<Boolean> original) {
        var item = (ItemEntity)(Object)this;
        if (item.isInvulnerableTo(source)) return original.call(source, amount);
        float reduced = CrossModCombat.defendItem(item, source, amount);
        return reduced > 0 && original.call(source, reduced);
    }
}
