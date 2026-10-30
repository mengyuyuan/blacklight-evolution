package com.codex.blacklight.mixin;

import net.minecraft.world.entity.ItemBasedSteering;

/** These vanilla saddles are synchronized flags, rather than inventory slots. */
public interface SaddleSteeringAccess {
    ItemBasedSteering blacklight$steering();
}
