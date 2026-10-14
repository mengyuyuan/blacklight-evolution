package com.codex.blacklight.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** True only after the normal death callback has accepted death, not merely at zero HP. */
@Mixin(LivingEntity.class)
public interface LivingDeathAccess {
    @Accessor("dead") boolean blacklight$deathAccepted();
}
