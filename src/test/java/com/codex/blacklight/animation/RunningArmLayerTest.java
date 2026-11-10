package com.codex.blacklight.animation;

import static org.junit.jupiter.api.Assertions.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class RunningArmLayerTest {
    private static final String[] GUARDS = {"basic_idle_claw", "basic_idle_blade", "basic_idle_hammer", "basic_idle_whip"};
    /** Right/left hand position in chest space: [min z, max z, mean y] per hand. */
    private static float[] hands(String key, String guard) {
        var sk = BlacklightSkeleton.get();
        float d = MotionLibrary.duration(key);
        float[] r = {9, -9, 0, 9, -9, 0};
        for (int i = 0; i < 48; i++) {
            var pose = MotionLibrary.pose(key, d * i / 48F, true);
            if (guard != null) pose = RunningArmLayer.apply(pose, key, guard);
            var m = sk.getPoseMatrices(pose);
            var chest = new Matrix4f(m[sk.chest.getId()]).invert();
            int k = 0;
            for (var hand : new BlacklightSkeleton.Bone[]{sk.handR, sk.handL}) {
                var p = chest.transformPosition(m[hand.getId()].getTranslation(new Vector3f()));
                r[k] = Math.min(r[k], p.z); r[k + 1] = Math.max(r[k + 1], p.z); r[k + 2] += p.y / 48; k += 3;
            }
        }
        return r;
    }

    @Test void walkingAndBareHandsKeepTheAuthoredStride() {
        for (String key : new String[]{"basic_walk", "basic_run", "basic_sprint", "basic_sprint_brake"}) {
            var pose = MotionLibrary.pose(key, .2F, true);
            assertSame(pose, RunningArmLayer.apply(pose, key, "basic_idle"), key);
        }
        for (String guard : GUARDS) {
            var walk = MotionLibrary.pose("basic_walk", .3F, true);
            assertSame(walk, RunningArmLayer.apply(walk, "basic_walk", guard), guard);
        }
    }

    @Test void armedRunningKeepsMostOfTheSwingAndDoesNotLiftTheArms() {
        for (String key : new String[]{"basic_run", "basic_sprint", "basic_sprint_fast", "basic_sprint_fastest"}) {
            var authored = hands(key, null);
            for (String guard : GUARDS) {
                var armed = hands(key, guard);
                for (int k = 0; k < 6; k += 3) {
                    float before = authored[k + 1] - authored[k], after = armed[k + 1] - armed[k];
                    assertTrue(after >= before * .6F, key + "/" + guard + " swing " + after + " of " + before);
                    assertTrue(after <= before * .9F, key + "/" + guard + " still damps weapon flail " + after + " of " + before);
                    System.out.printf(java.util.Locale.ROOT, "ARM %s %s hand%d swing %.2f->%.2f height %.2f->%.2f%n", key, guard, k / 3, before, after, authored[k + 2], armed[k + 2]);
                    assertTrue(armed[k + 2] <= authored[k + 2] + .03F, key + "/" + guard + " arms are not lifted into a guard");
                    assertTrue(armed[k + 2] >= authored[k + 2] - .08F, key + "/" + guard + " arms do not droop");
                }
            }
        }
    }
}