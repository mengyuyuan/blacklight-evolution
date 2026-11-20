package com.codex.blacklight.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThrownBodyWallTest {
    private final AABB body = new AABB(.4, 1, .2, 1, 2.8, .8);
    private final AABB slab = new AABB(1, 1, 0, 2, 1.5, 1);
    private final BlockPos block = new BlockPos(1, 1, 0);

    @Test void measuresCompressionAgainstTheActualSlabFace() {
        var hit = ThrownBodyWall.contact(body, slab, block, new Vec3(.6, -.1, 1.3));
        assertNotNull(hit);
        assertEquals(new Vec3(-1, 0, 0), hit.normal());
        assertEquals(new Vec3(1, 1.25, .5), hit.point());
        assertEquals(.6, hit.speed(), .00001);
    }
    @Test void slidingAwayPastAndAboveTheWallCannotDealAnImpact() {
        assertNull(ThrownBodyWall.contact(body, slab, block, new Vec3(.08, 0, 1.3)));
        assertNull(ThrownBodyWall.contact(body, slab, block, new Vec3(-.9, 0, 0)));
        assertNull(ThrownBodyWall.contact(body.move(0, .5, 0), slab, block, new Vec3(.9, 0, 0)));
        assertNull(ThrownBodyWall.contact(body.move(0, 0, 1), slab, block, new Vec3(.9, 0, 0)));
    }
}
