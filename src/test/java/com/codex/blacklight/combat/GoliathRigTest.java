package com.codex.blacklight.combat;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoliathRigTest {
    @Test void neckMeshOverlapsItsJointSocketsAcrossLocomotionCombatAndDeath() {
        var table = GoliathMotion.table();
        for (String clip : List.of("idle", "walk", "CHARGE", "HAMMER", "SLAM", "ERUPTION", "exposed", "rise", "death")) {
            for (float fraction : new float[]{0, .175F, .375F, .5F, .725F, .9F, 1}) {
                var pose = GoliathMotion.sample(clip, table.clip(clip).seconds() * 20 * fraction, false);
                var body = GoliathContact.regions(pose, Vec3.ZERO, 0, 0, GoliathPresentation.SCALE);
                Vec3 spine = joint(pose, "Spine_3");
                Vec3 lower = joint(pose, "Neck");
                Vec3 upper = joint(pose, "Neck_2");
                Vec3 head = joint(pose, "Head");

                assertTrue(contains(body, "neck_base", lower), clip + " chest-to-neck joint");
                assertTrue(contains(body, "neck_middle", lower), clip + " lower neck root");
                assertTrue(contains(body, "neck_middle", upper), clip + " lower neck end");
                assertTrue(contains(body, "neck_top", upper), clip + " upper neck root");
                assertTrue(contains(body, "neck_top", head) && contains(body, "head", head), clip + " skull connection");

                boolean connectsToTorso = false;
                for (int part = 1; part < 16; part++) {
                    var point = spine.lerp(lower, part / 16D);
                    connectsToTorso |= contains(body, "torso", point) && contains(body, "neck_base", point);
                }
                assertTrue(connectsToTorso, clip + " neck base intersects the chest");
                assertEquals(.681 * GoliathPresentation.SCALE, head.distanceTo(upper), .003, clip + " source head attachment");
            }
        }
    }

    @Test void ordinaryExposureDoesNotPlayTheCounterattackArc() {
        var table = GoliathMotion.table();
        for (int duration : new int[]{70, 140}) {
            float maxHead = 0;
            for (float age = 0; age <= duration; age += .25F) {
                var pose = GoliathMotion.recovery(age, duration, 0, 40 + age);
                maxHead = Math.max(maxHead, table.point(pose, "source_Head").y);
            }
            assertTrue(maxHead < 9.5F, "standing hit stays near the idle/locomotion envelope: " + maxHead);
        }
    }

    @Test void groundSpikeBlendsThroughTheSourceReleaseWithoutTheUnusedAerialSection() {
        var table = GoliathMotion.table();
        float maxHead = 0;
        for (float age = 0; age <= EncounterMove.ERUPTION.duration; age += .25F) {
            var pose = GoliathMotion.attack(EncounterMove.ERUPTION, age);
            maxHead = Math.max(maxHead, table.point(pose, "source_Head").y);
        }
        assertTrue(maxHead < 10F, "ground spike head envelope: " + maxHead);
        var pose = GoliathMotion.attack(EncounterMove.ERUPTION, EncounterMove.ERUPTION.contact);
        var release = GoliathMotion.sample("ERUPTION", 104 * 20F / 30F, false);
        assertTrue(joint(pose, "Head").distanceTo(joint(release, "Head")) < .001);
    }

    @Test void gaitUsesDisplacementAndEasesOutOnStopOrActionEntry() {
        var gait = new GoliathGait();
        gait.tick(0, true);
        assertEquals(0, gait.weight(1), .0001);
        assertEquals(0, gait.phase(1, 60), .0001);

        gait.tick(.16, true);
        assertEquals(.09, gait.weight(.5F), .0001);
        assertTrue(gait.phase(1, 60) > 0);
        for (int i = 0; i < 6; i++) gait.tick(.16, true);
        assertEquals(1, gait.weight(1), .0001);

        float phase = gait.phase(1, 60);
        gait.tick(.6, false);
        assertEquals(.82, gait.weight(1), .0001);
        assertEquals(phase, gait.phase(1, 60), .0001);
        gait.tick(16, true);
        assertEquals(0, gait.weight(1), .0001);
        assertEquals(0, gait.phase(1, 60), .0001);
    }

    private static Vec3 joint(float[][] pose, String name) {
        var p = GoliathMotion.table().point(pose, "source_" + name);
        float scale = GoliathPresentation.SCALE;
        return new Vec3(-p.x * scale, p.y * scale + .01, -p.z * scale);
    }

    private static boolean contains(List<HydraContact.Region> regions, String name, Vec3 point) {
        return regions.stream().filter(r -> r.bone().equals(name)).flatMap(r -> r.boxes().stream())
            .anyMatch(box -> box.nearest(point).distanceToSqr(point) < 1e-6);
    }
}
