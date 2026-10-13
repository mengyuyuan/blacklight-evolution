package com.codex.blacklight.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChargePoseClockTest {
    @Test void continuousAcrossTicksAndOutOfOrderRenderPasses() {
        var clock=new ChargePoseClock();float end=0;
        for(int tick=1;tick<=30;tick++) {
            clock.commit(tick,24);
            assertEquals(end,clock.ratio(0,24),1e-6);
            float late=clock.ratio(.9F,24),early=clock.ratio(.1F,24);
            assertTrue(late>=early);
            assertEquals(late,clock.ratio(.9F,24));
            end=clock.ratio(1,24);
        }
        assertEquals(1,end);
    }
    @Test void cancellationAndNextChargeDoNotReuseHeldPose() {
        var clock=new ChargePoseClock();clock.commit(24,24);clock.commit(0,24);
        assertEquals(0,clock.ratio(.9F,24));clock.commit(1,24);
        assertEquals(.5F/24,clock.ratio(.5F,24),1e-6);
    }
}
