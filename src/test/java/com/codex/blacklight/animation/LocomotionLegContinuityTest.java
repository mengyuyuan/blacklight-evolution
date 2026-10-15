package com.codex.blacklight.animation;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocomotionLegContinuityTest {
    private MatrixMotionClip load(String name) throws Exception {
        return MatrixMotionClip.parse(JsonParser.parseString(Files.readString(
                Path.of("art/motion-profile", name + ".json"))).getAsJsonObject());
    }

    @Test void bundledIdleAndWalkNeverFlipOneLegAtStraightKnees() throws Exception {
        // A 177-degree baked pole flip previously occurred in whip idle and walk.
        check("basic_idle_whip", 5);
        check("basic_walk", 15);
        check("basic_sprint", 40);
        check("basic_jump_running", 40);
    }

    private void check(String name, double maximumDegrees) throws Exception {
        var clip = load(name);
        for (String joint : new String[]{"Thigh_L", "Thigh_R", "Leg_L", "Leg_R"}) {
            var previous = clip.sample(joint, 0, false).getNormalizedRotation(new Quaternionf()).normalize();
            // Exercise the runtime interpolator at 240 Hz, not only stored keys.
            int steps = Math.round(clip.duration() * 240);
            for (int i = 1; i <= steps; i++) {
                var rotation = clip.sample(joint, i / 240F, false)
                        .getNormalizedRotation(new Quaternionf()).normalize();
                double degrees = Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(previous.dot(rotation)))));
                assertTrue(degrees < maximumDegrees / 4 + .1,
                        name + "/" + joint + " at " + i / 240F + ": " + degrees);
                previous = rotation;
            }
            if (!name.equals("basic_jump_running")) {
                var start = clip.sample(joint, 0, false).getNormalizedRotation(new Quaternionf()).normalize();
                var end = clip.sample(joint, clip.duration(), false).getNormalizedRotation(new Quaternionf()).normalize();
                double seam = Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(start.dot(end)))));
                // Authored cyclic clips have a small wrap interval, blended by the controller.
                assertTrue(seam < 3.5, name + "/" + joint + " excessive loop seam: " + seam);
            }
        }
    }
}
