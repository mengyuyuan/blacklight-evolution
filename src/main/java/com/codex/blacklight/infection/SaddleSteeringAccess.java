package com.codex.blacklight.infection;

import net.minecraft.world.entity.ItemBasedSteering;

/** These vanilla saddles are synchronized flags, rather than inventory slots. */
public interface SaddleSteeringAccess {
    ItemBasedSteering blacklight$steering();
}
