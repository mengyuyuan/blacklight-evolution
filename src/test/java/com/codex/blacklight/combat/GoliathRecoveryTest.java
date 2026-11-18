package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoliathRecoveryTest {
    private static float frame(float age, int duration) {
        return GoliathRecovery.poseProgress(age, duration) * 290;
    }

    @Test void wallReactionReachesFallLandingHoldAndRiseBeforeDeadline() {
        int duration = GoliathRecovery.WALL_TICKS;
        assertEquals(218F, frame(0, duration), .001F);
        assertEquals(230F, frame(7, duration), .001F);
        assertEquals(242F, frame(15, duration), .001F);
        assertEquals(242F, frame(32, duration), .001F);
        assertEquals(242F, frame(44, duration), .001F);
        assertEquals(16F, GoliathRecovery.riseFrame(32, duration), .001F);
        assertEquals(48F, GoliathRecovery.riseFrame(44, duration), .001F);
        assertEquals(80F, GoliathRecovery.riseFrame(duration, duration), .001F);
    }

    @Test void legacyAndNonWallExposuresUseTheirOwnWholeInterval() {
        assertEquals(218F, frame(0, 40), .001F);
        assertEquals(242F, frame(23, 40), .001F);
        assertEquals(23, GoliathRecovery.riseStart(40));
        assertEquals(80F, GoliathRecovery.riseFrame(40, 40), .001F);
        assertEquals(52.5F, frame(35, 70), .001F);
        assertEquals(192F, frame(140, 140), .001F);
        assertEquals(192F, frame(150, 140), .001F);
    }

    @Test void saveRestoresOnlyLiveGameTimeIntervals() {
        assertEquals(156L, GoliathRecovery.restoredEnd(128, 100, 156));
        assertEquals(140L, GoliathRecovery.restoredEnd(120, 100, 140));
        assertEquals(0L, GoliathRecovery.restoredEnd(156, 100, 156));
        assertEquals(0L, GoliathRecovery.restoredEnd(99, 100, 156));
        assertEquals(0L, GoliathRecovery.restoredEnd(120, -1, 156));
        assertEquals(0L, GoliathRecovery.restoredEnd(120, 100, Long.MAX_VALUE));
    }
}
