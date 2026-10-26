package com.codex.blacklight.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CarryInterpolationTest {
    @Test void delayedReceiverPacketCanRecoverInsteadOfPermanentlyTrailingTheRunner() {
        for(double speed:new double[]{.25,.6,.8,1.0}) {
            double owner=0,receiver=-6*speed;
            for(int tick=0;tick<30;tick++) {
                owner+=speed;
                double gap=owner-receiver;
                assertTrue(gap<=CarryInterpolation.correctionLimit(speed,speed),"tracking packet can be seven sprint ticks behind");
                receiver=owner;
                assertEquals(owner,receiver);
                if(tick%6==5)receiver=owner-6*speed;
            }
        }
    }
    @Test void teleportOrInvalidOwnerSampleNeverRebasesTheReceiver() {
        assertEquals(0,CarryInterpolation.correctionLimit(.8,32));
        assertEquals(0,CarryInterpolation.correctionLimit(Double.NaN,.5));
        assertEquals(0,CarryInterpolation.correctionLimit(.5,Double.POSITIVE_INFINITY));
        assertEquals(0,CarryInterpolation.correctionLimit(-.5,.5));
        assertTrue(CarryInterpolation.correctionLimit(100,1)<=12);
    }
}
