package com.codex.blacklight.combat;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoliathPresentationTest {
    @Test void visibleHurtRegionsKeepTheirScaleAndFootAnchor() {
        var pose = GoliathMotion.sample("exposed", 242 * 20F / 30F, false);
        var origin = new Vec3(11, 8, -6);
        var anchor = origin.add(0, .01, 0);
        float yaw = 37;
        var nativeBoxes = GoliathContact.regions(pose, origin, yaw, 0);
        var liveBoxes = GoliathContact.regions(pose, origin, yaw, 0, GoliathPresentation.SCALE);
        for (int i = 0; i < nativeBoxes.size(); i++) {
            var raw = nativeBoxes.get(i).boxes();
            var live = liveBoxes.get(i).boxes();
            assertEquals(raw.size(), live.size());
            for (int box = 0; box < raw.size(); box++) {
                for (var corner : raw.get(box).points) {
                    var expected = scaled(corner, anchor);
                    assertTrue(live.get(box).points.stream().anyMatch(p -> p.distanceTo(expected) < .0005));
                }
            }
        }
    }

    @Test void liveChargeAndFrontSlamScaleTheContactVolumeAroundTheActor() {
        var origin = new Vec3(11, 8, -6);
        var anchor = origin.add(0, .01, 0);
        var snapshot = new GoliathCharge.Snapshot(3, 0);
        var rawCharge = GoliathCharge.spheres(snapshot, 1, origin, 37).get(0);
        var liveCharge = GoliathCharge.spheres(snapshot, 1, origin, 37, GoliathPresentation.SCALE).get(0);
        assertNear(scaled(rawCharge.center(), anchor), liveCharge.center());
        assertEquals(rawCharge.radius() * GoliathPresentation.SCALE, liveCharge.radius(), .00001);

        var rawSlam = GoliathSlam.spheres(38, origin, 37).get(0);
        var liveSlam = GoliathSlam.spheres(38, origin, 37, GoliathPresentation.SCALE).get(0);
        assertNear(scaled(rawSlam.origin(), origin), liveSlam.origin());
        assertEquals(rawSlam.radius() * GoliathPresentation.SCALE, liveSlam.radius(), .00001);
    }

    @Test void wallReactionEntersFromTheActualChargeSampleAndLeavesAtIdle() {
        var table = GoliathMotion.table();
        int duration = GoliathRecovery.WALL_TICKS;
        float idleAge = 123;
        float[][] idle = GoliathMotion.sample("idle", idleAge, true);
        var unblendedFall = GoliathMotion.sample("exposed", 218 * 20F / 30F, false);
        var unblendedTail = GoliathMotion.sample("rise", 80 * 20F / 30F, false);
        var floor = GoliathMotion.sample("exposed", 242 * 20F / 30F, false);
        var riseBoundary = GoliathMotion.recovery(GoliathRecovery.riseStart(duration),duration,0,idleAge);
        assertEquals(0,maxJointShift(table,floor,riseBoundary),.0005,"rise starts from the actual grounded pose");
        assertTrue(maxJointShift(table, unblendedTail, idle) > .001, "last source frame is not identical to live idle");
        for (int phase : List.of(3, 4)) {
            for (float age : new float[]{0F, 4F, 7F}) {
                float[][] charge = GoliathCharge.pose(new GoliathCharge.Snapshot(phase, 0), age);
                long impact = GoliathRecovery.packImpactSample(phase, age);
                assertTrue(maxJointShift(table, charge, unblendedFall) > .2, "raw wall transition would jump");
                var atImpact = GoliathMotion.recovery(0, duration, impact, idleAge);
                var afterRise = GoliathMotion.recovery(duration, duration, impact, idleAge);
                assertEquals(0, maxJointShift(table, charge, atImpact), .0005);
                assertEquals(0, maxJointShift(table, idle, afterRise), .0005);
                assertEquals(impact, GoliathRecovery.sanitizeImpactSample(impact));
            }
        }
    }

    private static Vec3 scaled(Vec3 point, Vec3 origin) {
        return origin.add(point.subtract(origin).scale(GoliathPresentation.SCALE));
    }

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) < .0005, () -> "actual " + actual + ", expected " + expected);
    }

    private static double maxJointShift(SourceBossMotion table, float[][] a, float[][] b) {
        var first = table.transforms(a);
        var second = table.transforms(b);
        double max = 0;
        for (int i = 0; i < first.length; i++) {
            var position = first[i].transformPosition(new org.joml.Vector3f());
            max = Math.max(max, position.distance(second[i].transformPosition(new org.joml.Vector3f())));
        }
        return max;
    }
}
