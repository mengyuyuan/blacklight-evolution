package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GoddessSceneClockTest {
    @Test void ongoingSceneIgnoresClockCorrectionsAndAllowsOutOfOrderRenderPasses() {
        var clock = new GoddessSceneClock();
        clock.synchronize(3, 1, 900, 968, 100);
        assertEquals(71.06F, clock.age(103, .06F), .0001F);
        clock.synchronize(3, 1, 900, 969, 103);
        assertEquals(71.94F, clock.age(103, .94F), .0001F);
        assertEquals(71F, clock.age(103, 0), .0001F);
        assertEquals(72.34F, clock.age(104, .34F), .0001F);
    }

    @Test void vacuoleReachesCaptureAndBurstOnTheirPacketsEvenWhenClientTicksFallBehind() {
        var clock = new GoddessSceneClock();
        clock.synchronize(4, 1, 1000, 1000, 200);
        clock.catchUpTo(40, 233);
        assertEquals(40.5F, clock.age(233, .5F));
        clock.catchUpTo(64, 247);
        assertEquals(64F, clock.age(247, 0));
        clock.catchUpTo(40, 248);
        assertEquals(65.25F, clock.age(248, .25F));
        clock.synchronize(5, 0, 1074, 1074, 257);
        assertEquals(0F, clock.age(257, 0));
    }

    @Test void newActionOrStepStartsAtItsSynchronizedProgress() {
        var clock = new GoddessSceneClock();
        clock.synchronize(3, 1, 800, 900, 600);
        assertEquals(100.5F, clock.age(600, .5F));
        clock.synchronize(4, 1, 915, 915, 615);
        assertEquals(.5F, clock.age(615, .5F));
        clock.synchronize(4, 2, 920, 924, 624);
        assertEquals(4.5F, clock.age(624, .5F));
    }
}
