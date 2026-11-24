package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.SpiderAnatomy;
import com.codex.blacklight.util.MoltAnimationData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiderMoltPoseTest {
    private static final int JOINTS = 39;
    private static final float EPSILON = .002F;

    private static int parent(int i) {
        var list = SpiderAnatomy.MOLT_JOINTS;
        String name = SpiderAnatomy.PARENTS.get(list.get(i));
        return name == null ? -1 : list.indexOf(name);
    }

    private static Vector3f pivot(int i) {
        return SpiderAnatomy.PIVOTS.get(SpiderAnatomy.MOLT_JOINTS.get(i)).toVector3f();
    }

    private static int[] lastPose() {
        var poses = new Matrix4f[JOINTS];
        var last = new int[JOINTS * 16];
        var values = new float[16];
        for (int i = 0; i < JOINTS; i++) {
            int parent = parent(i);
            var m = parent < 0 ? new Matrix4f().translate(.4F, .15F, -.2F).rotateZ(.23F)
                    : new Matrix4f(poses[parent]);
            var p = pivot(i);
            m.translate(p).rotateX(.15F * (float)Math.sin(i)).translate(-p.x, -p.y, -p.z);
            poses[i] = m;
            m.get(values);
            for (int j = 0; j < 16; j++) last[16 * i + j] = Float.floatToIntBits(values[j]);
        }
        return last;
    }

    @Test void carriesLastPoseAndKeepsAllJointsAttachedOnTheAuthoredMolt() throws Exception {
        var raw = MoltAnimationData.read(getClass().getResourceAsStream("/assets/blacklight/greene/molt/scene.bin.gz"));
        var scene = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x474d4c32, scene.getInt());
        int people = scene.getInt(), lines = scene.getInt(), frames = scene.getInt();
        scene.position(4 * (4 + 36 + 4 * lines + 18));
        for (int mesh = 0; mesh < 2; mesh++) {
            int vertices = scene.getInt();
            scene.position(scene.position() + vertices * 48);
        }
        var timeline = scene.asFloatBuffer();
        int stride = timeline.remaining() / frames;
        int[] last = lastPose();
        var pose = new SpiderMoltPose();
        float[] bones = new float[JOINTS * 16], authored = new float[bones.length];
        for (int sample = 0; sample <= 80; sample++) {
            float t = sample / 10F;
            timeline.position(sample * stride).get(authored);
            System.arraycopy(authored, 0, bones, 0, bones.length);
            pose.sample(t, 0xff, last, bones);
            if (sample == 0)
                for (int i = 0; i < bones.length; i++) assertEquals(Float.intBitsToFloat(last[i]), bones[i], EPSILON);
            if (sample == 8) {
                // At peak roar the damaged shell still leans to its original side.
                for (int i = 0; i < 16; i++) assertEquals(Float.intBitsToFloat(last[i]), bones[i], EPSILON);
            }
            if (sample == 80) assertArrayEquals(authored, bones, EPSILON);
            for (int i = 1; i < JOINTS; i++) {
                var p = pivot(i);
                var child = new Matrix4f().set(bones, i * 16).transformPosition(new Vector3f(p));
                var connected = new Matrix4f().set(bones, parent(i) * 16).transformPosition(new Vector3f(p));
                assertTrue(child.distance(connected) < .04F, "joint " + i + " at " + t + "s");
            }
            float[] path = new float[48];
            Arrays.fill(path, 1F);
            pose.alignTendrils(path);
            var expected = new Matrix4f().set(bones, 16).mul(new Matrix4f().set(authored,16).invert())
                .transformPosition(new Vector3f(1));
            assertEquals(expected.x, path[0], EPSILON);
            assertEquals(expected.y, path[1], EPSILON);
            assertEquals(expected.z, path[2], EPSILON);
            assertArrayEquals(new float[]{1,1,1}, Arrays.copyOfRange(path, 45, 48));
        }
    }

    @Test void roarOpensTheSpiderEvenInAnOldSaveWithoutCapturedMatrices() {
        var pose = new SpiderMoltPose();
        float[] rest = new float[JOINTS * 16];
        for (int i = 0; i < JOINTS; i++) new Matrix4f().get(rest, i * 16);
        var windUp = rest.clone();
        var roar = rest.clone();
        pose.sample(.43F, 0, new int[0], windUp);
        pose.sample(.80F, 0, new int[0], roar);
        var chin = pivot(31);
        var before = new Matrix4f().set(windUp, 30 * 16).transformPosition(new Vector3f(chin));
        var after = new Matrix4f().set(roar, 30 * 16).transformPosition(new Vector3f(chin));
        assertTrue(before.distance(after) > 2F);
        assertArrayEquals(Arrays.copyOfRange(rest, 13 * 16, 29 * 16),
                Arrays.copyOfRange(roar, 13 * 16, 29 * 16), EPSILON);
    }
}
