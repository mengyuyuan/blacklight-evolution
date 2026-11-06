package com.codex.blacklight.mixin;

import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Forge 1.20.1 has a reservation setter but no public getter. Read only. */
@Mixin(ItemEntity.class)
public interface ItemPickupTargetAccess {
    @Accessor("target") UUID blacklight$pickupTarget();
}
