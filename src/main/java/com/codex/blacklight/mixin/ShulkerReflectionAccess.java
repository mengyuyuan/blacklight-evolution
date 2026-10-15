package com.codex.blacklight.mixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ShulkerBullet.class)
public interface ShulkerReflectionAccess {
    @Accessor("finalTarget") void blacklight$clearTarget(Entity target);
    @Accessor("targetId") void blacklight$clearTargetId(java.util.UUID target);
}
