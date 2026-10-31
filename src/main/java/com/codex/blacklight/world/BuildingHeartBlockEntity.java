package com.codex.blacklight.world;

import com.codex.blacklight.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Animation uses the synchronized core age and level clock, with no per-frame server packets. */
public final class BuildingHeartBlockEntity extends BlockEntity {
    public BuildingHeartBlockEntity(BlockPos pos,BlockState state) { super(ModBlockEntities.BUILDING_HEART.get(),pos,state); }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() { return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(3); }
}
