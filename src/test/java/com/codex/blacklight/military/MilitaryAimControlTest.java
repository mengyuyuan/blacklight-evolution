package com.codex.blacklight.military;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MilitaryAimControlTest {
    @Test void originalToleranceIsDotErrorRatherThanRadians() {
        double angle = Math.acos(1 - MilitaryAimControl.DOT_ERROR);
        assertTrue(MilitaryAimControl.aligned(0, -Math.sin(angle - 1e-7), Math.cos(angle - 1e-7)));
        assertFalse(MilitaryAimControl.aligned(0, -Math.sin(angle + 1e-7), Math.cos(angle + 1e-7)));
        assertTrue(MilitaryAimControl.aligned(0, -Math.sin(.4), Math.cos(.4)),
                "a 0.1-radian interpretation would reject this source-valid heading");
        assertFalse(MilitaryAimControl.aligned(0, 1, 0));
        assertFalse(MilitaryAimControl.aligned(0, 0, -1));
    }

    @Test void rearTargetRequiresSeveralBoundedTurnsAndCannotSnapAtWrap() {
        float yaw = 0;
        for (int tick = 0; tick < 5; tick++) {
            yaw = MilitaryAimControl.turn(yaw, 180);
            assertFalse(MilitaryAimControl.aligned(yaw, 0, -10));
        }
        yaw = MilitaryAimControl.turn(yaw, 180);
        assertTrue(MilitaryAimControl.aligned(yaw, 0, -10));
        assertEquals(-179, MilitaryAimControl.turn(179, -179));
        assertEquals(-180, MilitaryAimControl.interpolate(179, -179, .5F));
    }

    @Test void horizontalGateIgnoresScaleAndRejectsUndefinedHeading() {
        assertEquals(-90, MilitaryAimControl.bearing(10, 0));
        assertTrue(MilitaryAimControl.aligned(-90, 1000, 0));
        assertFalse(MilitaryAimControl.aligned(0, 0, 0));
        assertFalse(MilitaryAimControl.aligned(Float.NaN, 0, 1));
        assertFalse(MilitaryAimControl.aligned(0, Double.POSITIVE_INFINITY, 1));
        assertTrue(Float.isNaN(MilitaryAimControl.bearing(0, 0)));
        assertTrue(Float.isNaN(MilitaryAimControl.interpolate(0, Float.NaN, .5F)));
    }
}
