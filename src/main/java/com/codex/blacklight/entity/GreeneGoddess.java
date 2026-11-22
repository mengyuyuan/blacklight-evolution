package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Command-summoned second form for viewing the animated assembly at its native scale. */
public final class GreeneGoddess extends Entity {
    public GreeneGoddess(EntityType<? extends GreeneGoddess> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    // The streamer extends to the side of the origin and rotates with the entity.
    @Override public AABB getBoundingBoxForCulling() {
        return new AABB(getX()-15, getY()-1, getZ()-15, getX()+15, getY()+26, getZ()+15);
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256.0 * 256.0; }
}
