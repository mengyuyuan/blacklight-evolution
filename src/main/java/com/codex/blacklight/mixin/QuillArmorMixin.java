package com.codex.blacklight.mixin;

import com.codex.blacklight.entity.BackspineQuill;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Local to the projectile contact. Armor items and the target's attributes remain intact. */
@Mixin(LivingEntity.class)
public abstract class QuillArmorMixin {
    @ModifyExpressionValue(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getArmorValue()I"))
    private int blacklight$wedgedArmor(int armor, DamageSource source, float damage) {
        return source.getDirectEntity() instanceof BackspineQuill quill && quill.reducesArmorOf((LivingEntity)(Object)this)
                ? Math.round(armor * .75F) : armor;
    }
}
