package com.codex.blacklight.animation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrecisionWingMotionTest {
    @Test void fixedCollarDoesNotFreezeTheLongInnerBladeAtTheSameHorizontalPosition(){
        assertEquals(0,PrecisionWingPose.flightWeight(.32F,1.46F,.2F));
        assertEquals(0,PrecisionWingPose.flightWeight(-.32F,1.46F,.2F));
        assertEquals(1,PrecisionWingPose.flightWeight(.32F,-.5F,.2F));
        assertEquals(1,PrecisionWingPose.flightWeight(-.32F,-.5F,.2F));
        assertEquals(1,PrecisionWingPose.flightWeight(1.5F,1.46F,.2F));
    }
    @Test void flightUsesTheClassicFanArticulationAtEveryPhaseAndSteeringState() {
        float[][] states = {
                {0, 0, 0, 0, 0, 0, 0}, // hover
                {.8F, 1, 0, 0, 0, 0, 0}, // cruise
                {1, 1, 0, .6F, 0, 0, 1}, // powered ascent
                {.7F, 1, -1, 0, -1, 0, 0}, // bank left
                {.7F, 1, 1, 0, 1, 0, 0}, // bank right
                {.4F, -1, 0, -.8F, 0, 1, 0} // braking descent
        };
        for (var state : states) for (int step = 0; step <= 60; step++) {
            var pose = SovereignWingMotion.sample(step / 60., 1, 1,
                    state[0], state[1], state[2], state[3], state[4], state[5], state[6]);
            assertClassicOrientation(pose, new Vector3f(0, 0, 1));
        }
    }

    @Test void pitchedAndBankedBodiesKeepTheSameWorldSpaceStrokePlane() {
        for (float pitch : new float[]{0, -45, -90, 60}) {
            var chest = new Quaternionf().rotationZ(.25F).rotateX((float) Math.toRadians(pitch));
            var axis = SovereignWingMotion.strokeAxis(chest);
            for (double phase : new double[]{0, .2, .45, .7, .95})
                assertClassicOrientation(SovereignWingMotion.sample(phase, 1, 1, .8F, 1, .6F, .3F, .8F, 0, 1), axis);
        }
    }

    @Test void fullPowerStrokeDoesNotFlattenAgainstAnArtificialAngleLimit() {
        var high = SovereignWingMotion.sample(0, 1, 1, 0, 0, 0, 1, 0, 0);
        var low = SovereignWingMotion.sample(.45, 1, 1, 0, 0, 0, 1, 0, 0);
        for (int part = 1; part <= 2; part++) {
            // The accepted precision fan's long blades point down in its bind pose.
            var localSpar = new Vector3f(part == 1 ? 1 : -1, -1, 0);
            var upper = PrecisionWingPose.transform(part, high, new Vector3f(0, 0, 1), 1, 1, 0)
                    .transformDirection(new Vector3f(localSpar));
            var lower = PrecisionWingPose.transform(part, low, new Vector3f(0, 0, 1), 1, 1, 0)
                    .transformDirection(new Vector3f(localSpar));
            assertTrue(upper.y - lower.y > .65F, "The downstroke must move the wing, rather than plateau at 18 degrees");
        }
    }

    @Test void flightLeavesTheBodyMountAndBothHingesStationary() {
        for (int step = 0; step <= 60; step++) {
            var pose = SovereignWingMotion.sample(step / 60., 1, 1, 1, 1, 1, 1, 1, 0, 1);
            var mount = PrecisionWingPose.transform(0, pose, new Vector3f(0, 0, 1), 1, 1, 0);
            assertTrue(mount.equals(new Matrix4f(), 1e-6F));
            for (int part = 1; part <= 2; part++) {
                var pivot = PrecisionWingPose.pivot(part);
                var moved = PrecisionWingPose.transform(part, pose, new Vector3f(0, 0, 1), 1, 1, 0)
                        .transformPosition(new Vector3f(pivot));
                assertEquals(0, pivot.distance(moved), 1e-5F, "The animation may not pull the attachment away from the body");
            }
        }
    }

    private static void assertClassicOrientation(SovereignWingMotion.Pose pose, Vector3f axis) {
        for (int part = 1; part <= 2; part++) {
            String name = part == 1 ? "left_root" : "right_root";
            var classic = SovereignWingMotion.articulation(name, pose, axis, 1, new Quaternionf());
            String side=part==1?"left":"right";
            for(String joint:new String[]{"rib_2","arch_2","finger_2","tip_2"})
                classic.mul(SovereignWingMotion.articulation(side+"_"+joint,pose,axis,1,new Quaternionf()));
            var precision = PrecisionWingPose.transform(part, pose, axis, 1, 1, 0);
            for (var basis : new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)}) {
                var expected = classic.transform(new Vector3f(basis));
                var actual = precision.transformDirection(new Vector3f(basis));
                assertEquals(0, expected.distance(actual), 2e-5F,
                        name + " must follow the same stroke, pitch and sweep as the classic rig");
            }
        }
    }
}
