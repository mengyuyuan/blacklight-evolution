package com.codex.blacklight.client.animation;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BiomassSurfaceWaveTest {
    @Test void tinyNegativePhasesWrapWithoutReadingPastTheTable() {
        for (float phase : new float[]{-Float.MIN_VALUE, -Float.MIN_NORMAL, -0x1.0p-26F, -0x1.0p-25F}) {
            assertEquals(0, BiomassSurface.wave(phase), 0.000001F, "phase=" + phase);
        }
    }

    @Test void neighboringSamplesStayContinuousAcrossPeriodBoundaries() {
        for (float boundary : new float[]{-8192, -256, -1, -0.0F, 0, 1, 256, 8192}) {
            for (float phase : new float[]{Math.nextDown(boundary), boundary, Math.nextUp(boundary)}) {
                double fraction = (double)phase - Math.floor((double)phase);
                double expected = Math.sin(fraction * Math.PI * 2);
                assertEquals(expected, BiomassSurface.wave(phase), 0.0001, "phase=" + phase);
            }
        }
        for (float phase : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            assertEquals(0, BiomassSurface.wave(phase));
        }
    }

    @Test void finiteFloatInputsAlwaysProduceFiniteBoundedWaves() {
        var random = new Random(28);
        for (int sample = 0; sample < 250_000; sample++) {
            float phase = Float.intBitsToFloat(random.nextInt());
            if (!Float.isFinite(phase)) continue;
            float value = BiomassSurface.wave(phase);
            if (!Float.isFinite(value) || value < -1 || value > 1) {
                fail("Invalid liquid wave: phase=" + phase + ", value=" + value);
            }
        }
    }

    @Test void liquidWeaponDeformationHandlesTheReportedPhaseBoundary() {
        var vertex = new BiomassSurface.Vertex(.14F, .32F, -.045F, 0, 1, 0, .147F, -0x1.0p-26F, 1);
        var frame = new BiomassSurface.Frame(.1F, .5F, 1, false);
        float[] result = new float[8];
        result[0] = result[7] = 123;
        BiomassSurface.deform(vertex, frame, 0, vertex.x(), vertex.y(), vertex.z(), result, 1);
        assertEquals(123, result[0]);
        assertEquals(123, result[7]);
        for (int i = 1; i <= 6; i++) assertTrue(Float.isFinite(result[i]));
        assertTrue(result[4] > 0, "The regression must exercise liquid deformation, not the hardened early return");
        assertEquals(vertex.x(), result[1], .05F);
        assertEquals(vertex.y(), result[2], .05F);
        assertEquals(vertex.z(), result[3], .05F);
    }
}
