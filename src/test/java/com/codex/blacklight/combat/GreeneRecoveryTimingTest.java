package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreeneRecoveryTimingTest {
    @Test void sourceTimerIncludesTheCollapseAndUsesSignedRandomRange(){
        // Independent native field/RNG fixture: 26.66670036 +/- 1 seconds.
        assertEquals(514,GreeneRecoveryTiming.recoveryStartTicks(0));
        assertEquals(534,GreeneRecoveryTiming.recoveryStartTicks(1<<22));
        assertEquals(554,GreeneRecoveryTiming.recoveryStartTicks((1<<23)-1));
        assertEquals(389,GreeneRecoveryTiming.downTicks(0));
        assertEquals(409,GreeneRecoveryTiming.downTicks(1<<22));
        assertEquals(429,GreeneRecoveryTiming.downTicks((1<<23)-1));
    }
    @Test void recoveryStartsOnTheFirstServerTickAtOrAfterTheNativeDeadline(){
        // 0x40100000 -> 2.25; 0x40700000 -> 3.75.
        for(int bits:new int[]{0x100000,0x700000}){
            double sourceSeconds=bits==0x100000?25.91670036315918:27.41670036315918;
            int ticks=GreeneRecoveryTiming.recoveryStartTicks(bits);
            assertTrue((ticks-1)/20D<sourceSeconds&&ticks/20D>=sourceSeconds);
            int hold=GreeneRecoveryTiming.downTicks(bits);
            assertEquals(GreeneMotion.DOWN,GreeneMotion.stage(ticks-1,hold));
            assertEquals(GreeneMotion.RISING,GreeneMotion.stage(ticks,hold));
        }
    }
    @Test void invalidSamplesAreRejectedInsteadOfSilentlyWrapping(){
        assertThrows(IllegalArgumentException.class,()->GreeneRecoveryTiming.recoveryStartTicks(-1));
        assertThrows(IllegalArgumentException.class,()->GreeneRecoveryTiming.recoveryStartTicks(1<<23));
    }
}
