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
    @Test void normalDivesHaveVisiblePressureAndSourceZoomCanFinishBeyondHeldBodyPose() {
        var ordinary=DevastatorEffectTimeline.flight(20,2,1);
        assertTrue(ordinary.strength()>.5,"A normal Minecraft dive must not need 12 blocks/tick to become visible");
        assertTrue(DevastatorEffectTimeline.flightFov(70,ordinary.tangentGain(),1)>77);
        var longFlight=DevastatorEffectTimeline.flight(10000,1000,4);
        assertEquals(2D/3,longFlight.tangentGain(),1e-8);
        assertEquals(98.8149,DevastatorEffectTimeline.flightFov(70,longFlight.tangentGain(),1),.02);
        assertTrue(DevastatorEffectTimeline.flight(100,5,4).tangentGain()
                >DevastatorEffectTimeline.flight(100,5,2.15).tangentGain(),"Body hold time is not the flight clock");
    }
    @Test void flightOpticsPreserveUserSettingsAndStopAtRealLanding() {
        for(double fov:new double[]{30,70,110,150,170}){
            assertEquals(fov,DevastatorEffectTimeline.flightFov(fov,2D/3,0),0);
            assertEquals(fov,DevastatorEffectTimeline.flightFov(fov,0,1),0);
            assertTrue(DevastatorEffectTimeline.flightFov(fov,2D/3,1)>=fov);
            assertTrue(DevastatorEffectTimeline.flightFov(fov,2D/3,1)<=175);
        }
        assertEquals(0,DevastatorEffectTimeline.lens(3,3,0,0,200,12).flight());
        assertFalse(DevastatorEffectTimeline.flight(0,0,2).active());
        assertFalse(DevastatorEffectTimeline.flight(Double.NaN,2,2).active());
        assertTrue(DevastatorEffectTimeline.flight(20,2,.2).shock()>.9);
        assertEquals(0,DevastatorEffectTimeline.flight(20,2,1.2).shock());
    }
    @Test void painDistortionUsesItsEmitterWindowAndFadesAfterEmission() {
        assertFalse(DevastatorEffectTimeline.lens(2,1,.5,0,0,0).active());
        assertTrue(DevastatorEffectTimeline.lens(2,1,2,0,0,0).burst()>.3);
        assertTrue(DevastatorEffectTimeline.lens(2,1,4,0,0,0).burst()>0);
        assertFalse(DevastatorEffectTimeline.lens(2,1,5.01,0,0,0).active());
        assertEquals(5,DevastatorEffectTimeline.visibleEnd(2,0,3));
    }
}
