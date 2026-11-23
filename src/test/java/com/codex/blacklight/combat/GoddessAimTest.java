package com.codex.blacklight.combat;

import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoddessAimTest {
    private static final double WALK = .216;
    private static final Vec3 PLAYER = new Vec3(0, .9, 30);
    private static final UUID RUNNER = new UUID(0, 1);

    private GoddessAim walkingEast() {
        var aim = new GoddessAim();
        for (int t = -12; t <= 0; t++) aim.observe(RUNNER, PLAYER.add(t * WALK, 0, 0), t);
        return aim;
    }

    // A world-space flight segment against the player's moving box, with vanilla air drag.
    private boolean crossesPlayer(Vec3 head, Vec3 mark, boolean reverseAtRelease) {
        Vec3 speed = mark.subtract(head).normalize().scale(GoddessVolley.NEEDLE_SPEED);
        Vec3 center = PLAYER.add(4 * WALK, 0, 0);
        Vec3 step = new Vec3(reverseAtRelease ? -WALK : WALK, 0, 0);
        var box = AABB.ofSize(Vec3.ZERO, .6, 1.8, .6).inflate(.3);
        for (int tick = 0; tick < 65; tick++) {
            Vec3 to = head.add(speed), next = center.add(step);
            if (box.clip(head.subtract(center), to.subtract(next)).isPresent()) return true;
            center = next;
            head = to;
            speed = speed.scale(.99);
        }
        return false;
    }

    @Test void leadingTheCrownThreatensContinuousWalkingButLeavesAChangeOfDirection() {
        var aim = walkingEast();
        for (double range : new double[]{20, 38, 64}) {
            Vec3 muzzle = PLAYER.add(0, 29, -range);
            double flight = GoddessAim.flightTicks(muzzle.distanceTo(PLAYER), GoddessVolley.NEEDLE_SPEED);
            Vec3 mark = aim.lead(PLAYER, 4 + flight);
            assertFalse(crossesPlayer(muzzle, PLAYER, false), "old lock on current position");
            assertTrue(crossesPlayer(muzzle, mark, false), "continuous walk at range " + range);
            assertFalse(crossesPlayer(muzzle, mark, true), "committed shot must not follow a reversal");
        }
    }

    @Test void handSlamAndRushCommitToTheWalkingPathAndCapFastFlightLead() {
        var aim = walkingEast();
        for (int ticks : new int[]{16, 21, 34}) {
            Vec3 mark = aim.lead(PLAYER, ticks);
            assertEquals(PLAYER.x + WALK * ticks, mark.x, .01);
            assertEquals(PLAYER.y, mark.y, 0);
        }
        for (int t = 1; t <= 8; t++) aim.observe(RUNNER, PLAYER.add(t * .95, 0, 0), t);
        assertEquals(8, aim.lead(PLAYER, 40).distanceTo(PLAYER), .001);
    }

    @Test void switchingPlayersTeleportingAndReacquiringDoNotRetainLead() {
        var aim = walkingEast();
        Vec3 other = PLAYER.add(40, 5, 0);
        var id = new UUID(0, 2);
        aim.observe(id, other, 1);
        assertEquals(other, aim.lead(other, 24));
        aim.observe(id, other.add(.25, 0, 0), 2);
        assertTrue(aim.lead(other, 20).x > other.x + 1);
        aim.observe(id, PLAYER, 3);
        assertEquals(PLAYER, aim.lead(PLAYER, 24));
        aim.observe(id, PLAYER.add(2, 0, 0), 18);
        assertEquals(PLAYER, aim.lead(PLAYER, 24));
    }
}
