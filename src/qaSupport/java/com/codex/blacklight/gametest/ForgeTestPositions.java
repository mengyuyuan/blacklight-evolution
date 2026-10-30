package com.codex.blacklight.gametest;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Exact placement adapter for the Forge 1.20.1 test runtime. */
public final class ForgeTestPositions {
    private ForgeTestPositions() {}
    public static void moveTo(Entity entity, Vec3 position, float yaw, float pitch) {
        entity.moveTo(position.x,position.y,position.z,yaw,pitch);
    }
}
