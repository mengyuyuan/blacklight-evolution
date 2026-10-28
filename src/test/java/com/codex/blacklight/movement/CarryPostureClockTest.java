package com.codex.blacklight.movement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CarryPostureClockTest {
    @Test void enteringGlideLowersTheHeldBodyGraduallyAndRenderingCannotAdvanceIt() {
        var clock=new CarryPostureClock();assertEquals(0,clock.sample(10,false,1));
        for(int tick=11;tick<=14;tick++) {
            float end=clock.sample(tick,true,1);
            assertEquals((tick-10)*.25F,end,.00001F);
            assertEquals(end-.25F,clock.sample(tick,true,0),.00001F);
            for(int pass=0;pass<8;pass++)assertEquals(end-.125F,clock.sample(tick,true,.5F),.00001F);
        }
        assertEquals(.75F,clock.sample(15,false,1));
        assertEquals(0,clock.sample(19,false,1));
    }
    @Test void interruptedOrNewLifeDoesNotRetainAnOldGlideGrip() {
        var clock=new CarryPostureClock();clock.sample(1,true,0);clock.sample(6,true,1);
        assertEquals(1,clock.sample(6,true,1));clock.reset();
        assertEquals(0,clock.sample(0,false,1));
    }
}
