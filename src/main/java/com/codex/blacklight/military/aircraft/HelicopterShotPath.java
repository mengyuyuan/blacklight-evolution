package com.codex.blacklight.military.aircraft;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/** Trace only the voxels crossed by the weapon. Missing terrain ends a shot without loading it. */
final class HelicopterShotPath {
    private HelicopterShotPath() {}

    private static BlockPos firstUnavailable(ServerLevel level, Vec3 from, Vec3 to) {
        return BlockGetter.traverseBlocks(from, to, level, (world, pos) ->
                world.isOutsideBuildHeight(pos) || world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                        ? pos.immutable() : null, world -> null);
    }

    static boolean loaded(ServerLevel level, Vec3 from, Vec3 to) {
        return firstUnavailable(level, from, to) == null;
    }

    static Vec3 loadedEnd(ServerLevel level, Vec3 from, Vec3 to) {
        BlockPos stop = firstUnavailable(level, from, to);
        if (stop == null) return to;
        return beforeVoxel(from, to, stop);
    }

    static Vec3 beforeVoxel(Vec3 from, Vec3 to, BlockPos stop) {
        Vec3 delta = to.subtract(from);
        double fraction = Math.max(entryFraction(from.x, delta.x, stop.getX()),
                Math.max(entryFraction(from.y, delta.y, stop.getY()), entryFraction(from.z, delta.z, stop.getZ())));
        double length = delta.length();
        if (length == 0 || fraction <= 0) return from;
        return from.add(delta.scale(Math.max(0, Math.min(1, fraction) - .001 / length)));
    }

    private static double entryFraction(double start, double delta, int cell) {
        if (delta == 0) return Double.NEGATIVE_INFINITY;
        return ((delta > 0 ? cell : cell + 1) - start) / delta;
    }
}
