package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GoliathInjuryPoseTest {
    @Test void ankleTranslationKeepsTheVisibleCalvesConnectedToTheFeet() {
        for (var opening : new GoliathWindow[]{GoliathWindow.KNEE, GoliathWindow.LEG_FALL,
                GoliathWindow.ARM_FALL, GoliathWindow.FACE}) {
            int mask = opening == GoliathWindow.KNEE ? 0 : opening == GoliathWindow.LEG_FALL ? 1 : 3;
            for (int tick = 0; tick < opening.ticks; tick++) {
                var surfaces = GoliathContact.regions(window(opening, tick), net.minecraft.world.phys.Vec3.ZERO, 0, mask);
                for (String side : mask == 0 ? new String[]{"left", "right"} : new String[]{"left"}) {
                    var calf = surfaces.stream().filter(r -> r.bone().equals(side + "_calf"))
                            .flatMap(r -> r.boxes().stream()).findFirst().orElseThrow();
                    var foot = surfaces.stream().filter(r -> r.bone().equals(side + "_foot"))
                            .flatMap(r -> r.boxes().stream()).findFirst().orElseThrow();
                    org.junit.jupiter.api.Assertions.assertTrue(calf.touches(foot), opening + " tick " + tick + " " + side);
                }
            }
        }
    }

    // Positions independently sampled from P2 behemoth.p3d world joint matrices
    // with Motion_Root travel removed. Coordinates are rig blocks before display scaling.
    private static void joint(float[][] pose, String name, double x, double y, double z) {
        var point = GoliathMotion.table().point(pose, name);
        assertEquals(x, point.x, .0002, name + " X");
        assertEquals(y, point.y, .0002, name + " Y");
        assertEquals(z, point.z, .0002, name + " Z");
    }

    private static float[][] window(GoliathWindow window, float age) {
        return GoliathInjuryPose.window(window, age, window.ticks, 0);
    }

    @Test void limbBreaksUseTheSourceFlinchBeforeChangingGait() {
        joint(window(GoliathWindow.LEG_FALL, 29), "head", .335282, 6.351804, .102132);
        joint(window(GoliathWindow.ARM_FALL, 40), "head", .252876, 4.650617, -3.423839);
    }

    @Test void plantedBladeOccupiesTheContactPointAcrossTheTransition() {
        joint(GoliathInjuryPose.attack(false, 39, 0), "left_hand", -.337321, 1.461788, -10.451473);
        joint(window(GoliathWindow.ARM, 16), "left_hand", -.333842, 1.655358, -10.451597);
    }

    @Test void airborneBodyDropsThenLeavesTheFaceAtGroundLevel() {
        joint(GoliathInjuryPose.attack(true, 25, 0), "head", -1.640296, 14.491696, -.631818);
        joint(window(GoliathWindow.FACE, 7), "head", 2.201231, 2.741724, -2.287469);
        joint(window(GoliathWindow.FINISH, 20), "head", .502352, 1.322869, -3.777543);
    }

    @Test void rightSoleMeetsTheGroundForTheWholeKneeOpening() {
        // P2's weighted R_LegShape001 plantar surface at source frames 25, 54,
        // and 95 measures -0.057, -0.058, and -0.063 blocks after display scaling.
        for (int sourceFrame = 25; sourceFrame <= 213; sourceFrame++) {
            var pose = GoliathMotion.sample("exposed", sourceFrame * 20F / 30F, false);
            var regions = GoliathContact.regions(pose, net.minecraft.world.phys.Vec3.ZERO, 0, 0, GoliathPresentation.SCALE);
            double sole = regions.stream().filter(r -> r.bone().equals("right_foot") || r.bone().equals("right_toes"))
                    .flatMap(r -> r.boxes().stream()).mapToDouble(b -> b.bounds.minY).min().orElseThrow();
            assertEquals(-.058, sole, .085, "plantar surface at source frame " + sourceFrame);
        }
        assertEquals(GoliathLockPart.RIGHT_LEG, GoliathLockPart.forSkin("right_foot"));
        assertEquals(GoliathLockPart.RIGHT_LEG, GoliathLockPart.forSkin("right_toes"));
    }

}
