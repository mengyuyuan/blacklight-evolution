package com.codex.blacklight.military.aircraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterShotPathTest {
    @Test void unloadedBoundaryAtTheExactEndRetainsTheEntireLoadedPrefix() {
        var start = new Vec3(8, 90, 8);
        var east = HelicopterShotPath.beforeVoxel(start, new Vec3(176, 90, 8), new BlockPos(176, 90, 8));
        assertEquals(175.999, east.x, 1e-8);
        var west = HelicopterShotPath.beforeVoxel(start, new Vec3(-160, 90, 8), new BlockPos(-161, 90, 8));
        assertEquals(-159.999, west.x, 1e-8);
    }

    @Test void diagonalShotStopsAtTheEnteringFaceAndCannotLeaveAnUnloadedStart() {
        var start = new Vec3(8.5, 90, 8.5);
        var end = new Vec3(168.5, 90, 168.5);
        var hit = HelicopterShotPath.beforeVoxel(start, end, new BlockPos(160, 90, 160));
        assertEquals(160 - .001 / Math.sqrt(2), hit.x, 1e-8);
        assertEquals(hit.x, hit.z, 1e-8);
        assertEquals(start, HelicopterShotPath.beforeVoxel(start, end, BlockPos.containing(start)));
    }
}
