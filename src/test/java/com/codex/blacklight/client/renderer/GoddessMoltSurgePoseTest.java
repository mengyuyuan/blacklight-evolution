package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GoddessRig;
import com.codex.blacklight.combat.GreeneMolt;
import com.codex.blacklight.combat.GreeneMoltSurge;
import com.codex.blacklight.util.MoltAnimationData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoddessMoltSurgePoseTest {
    private static float[] frame(int tick) throws Exception {
        var raw = MoltAnimationData.read(GoddessMoltSurgePoseTest.class.getResourceAsStream("/assets/blacklight/greene/molt/scene.bin.gz"));
        var data = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x474d4c32, data.getInt());
        data.getInt();
        int lines = data.getInt(), count = data.getInt();
        data.position(4 * (4 + 36 + 4 * lines + 18));
        for (int mesh = 0; mesh < 2; mesh++) {
            int vertices = data.getInt();
            data.position(data.position() + vertices * 48);
        }
        var timeline = data.asFloatBuffer();
        int stride = timeline.remaining() / count;
        float[] pose = new float[GreeneColonies.ROWS * 64];
        timeline.position(stride * (tick / 2)).get(pose, 0, GreeneColonies.NATIVE * 16);
        GreeneColonies.pose(pose);
        return pose;
    }

    private static Vector3f point(float[] pose, int joint) {
        return new Matrix4f().set(pose, 16 * joint).getTranslation(new Vector3f());
    }

    @Test void palmsPushOnThePropagatingFrontAndEveryColonyStaysAttached() throws Exception {
        var surge = new GoddessMoltSurgePose();
        var gathered = frame(GreeneMoltSurge.GATHER);
        var release = frame(GreeneMoltSurge.RELEASE);
        var original = release.clone();
        surge.release(GreeneMoltSurge.GATHER, gathered);
        surge.release(GreeneMoltSurge.RELEASE, release);
        float palmTravel = point(release,7).distance(point(release,21)) - point(gathered,7).distance(point(gathered,21));
        assertTrue(palmTravel > 3, "palms should spread by several model units as the front leaves; got " + palmTravel);
        assertEquals(point(original,2), point(release,2), "original ascent and pelvis height");
        assertTrue(point(release,0).distance(point(original,0)) > .1F);
        for (int joint = GoddessRig.JOINTS; joint < GreeneColonies.JOINTS; joint++) {
            int carrier = joint < GreeneColonies.NATIVE ? GoddessRig.carrier(joint)
                    : GreeneColonies.GROUPS.get((joint - GreeneColonies.NATIVE) / 12).owner();
            var carried = new Matrix4f().set(release,carrier * 16)
                    .mul(new Matrix4f().set(original,carrier * 16).invert());
            var expected = carried.transformPosition(point(original,joint));
            assertTrue(expected.distance(point(release,joint)) < .002F, "carrier " + carrier + " joint " + joint);
        }
        var arrived = frame(GreeneMoltSurge.ARRIVAL);
        var prior = arrived.clone();
        surge.release(GreeneMoltSurge.ARRIVAL, arrived);
        assertArrayEquals(prior, arrived, 0);
    }

    @Test void lastFrameMeetsEntityClockAndHoverInsteadOfJumpingToIdle() throws Exception {
        var surge = new GoddessMoltSurgePose();
        var pose = frame(GreeneMolt.DURATION);
        var target = frame(GreeneMoltSurge.RELEASE);
        float hover = GoddessRig.hover(375.5F);
        assertEquals(0, GoddessMoltSurgePose.settle(GreeneMoltSurge.RELEASE));
        assertEquals(1, GoddessMoltSurgePose.settle(GreeneMolt.DURATION));
        surge.settle(1, hover, pose, target);
        for (int i = 0; i < GreeneColonies.NATIVE; i++) {
            float[] expected = Arrays.copyOfRange(target,i*16,i*16+16);
            expected[13] += hover;
            assertArrayEquals(expected, Arrays.copyOfRange(pose,i*16,i*16+16), 0);
        }
    }
}
