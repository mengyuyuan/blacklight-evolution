package com.codex.blacklight.compat;

import java.util.OptionalDouble;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;

/** One adapter per mechanism/framework, not per creature. Read-only reserve
 * observation must never consume energy or create a foreign capability. */
public interface DamageMechanismAdapter {
    ResourceLocation id();
    boolean supports(LivingEntity entity);
    default OptionalDouble reserve(LivingEntity entity){return OptionalDouble.empty();}
    default boolean bypassNpcShield(LivingEntity entity,DamageSource source){return false;}
}
