package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DevastatorEffectTimelineTest {
    @Test void retractSurvivesDamageControllerEnd() {
        assertEquals(10D/3,DevastatorEffectTimeline.visibleEnd(1,.4,3),1e-8);
        assertTrue(DevastatorEffectTimeline.lens(1,1,3.1,.4,0,0).retract()>0);
        assertFalse(DevastatorEffectTimeline.lens(1,1,10D/3+.01,.4,0,0).active());
    }
    @Test void shortAndLongStrandsReturnAtTheirActualVelocity() {
        double reverse=DevastatorEffectTimeline.TENDRIL_RETRACT_START;
        assertEquals(1,DevastatorEffectTimeline.strand(reverse,0,reverse,10,30,350),1e-8);
        assertEquals(0,DevastatorEffectTimeline.strand(reverse+.1,0,reverse,10,30,350),1e-8);
        assertTrue(DevastatorEffectTimeline.strand(reverse+.1,0,reverse,50,30,350)>0);
        assertEquals(0,DevastatorEffectTimeline.strand(reverse+1,0,reverse,50,30,350),1e-8);
    }
    @Test void interruptedGrowthNeverReturnsFromAnUnformedFullLength() {
        assertEquals(.2,DevastatorEffectTimeline.strand(.2,0,.2,30,30,30),1e-8);
        assertEquals(.1,DevastatorEffectTimeline.strand(.3,0,.2,30,30,30),1e-8);
    }
    @Test void groundSpikeDescendsInPointTwoSecondsWithoutRestart() {
        assertEquals(1,DevastatorEffectTimeline.spike(2,0,.5,2),1e-8);
        assertEquals(.5,DevastatorEffectTimeline.spike(2.1,0,.5,2),1e-8);
        assertEquals(0,DevastatorEffectTimeline.spike(2.2,0,.5,2),1e-8);
    }
    @Test void flightLensRespondsToHeightAndSpeedSeparately() {
        assertTrue(DevastatorEffectTimeline.flightStrength(100,2)>DevastatorEffectTimeline.flightStrength(10,2));
        assertTrue(DevastatorEffectTimeline.flightStrength(100,20)>DevastatorEffectTimeline.flightStrength(100,2));
        assertTrue(DevastatorEffectTimeline.flightStrength(10000,1000)<=1);
        assertEquals(0,DevastatorEffectTimeline.flightStrength(Double.NaN,3));
    }
    @Test void painDistortionUsesItsEmitterWindowAndFadesAfterEmission() {
        assertFalse(DevastatorEffectTimeline.lens(2,1,.5,0,0,0).active());
        assertTrue(DevastatorEffectTimeline.lens(2,1,2,0,0,0).burst()>.3);
        assertTrue(DevastatorEffectTimeline.lens(2,1,4,0,0,0).burst()>0);
        assertFalse(DevastatorEffectTimeline.lens(2,1,5.01,0,0,0).active());
        assertEquals(5,DevastatorEffectTimeline.visibleEnd(2,0,3));
    }
}
