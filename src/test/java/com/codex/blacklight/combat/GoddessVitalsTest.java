package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoddessVitalsTest {
    @Test void damageCrossingFirstThresholdSurvivesReloadAndAbsorption() {
        var life = new GoddessVitals();
        assertEquals(100_000, life.healthAfterHit(105_000, 93_000));
        assertEquals(7_000, life.remainder());
        var loaded = new GoddessVitals();
        loaded.restore(life.removed(), life.waiting(), life.remainder());
        assertEquals(100_000, loaded.healthAfterHit(100_000, 20_000));
        assertEquals(93_000, loaded.absorb(GoddessVitals.RIGHT_ARM, 100_000));
        assertFalse(loaded.waiting());
        assertEquals(50_000, loaded.healthAfterHit(53_000, 50_000));
        assertFalse(loaded.accepts(GoddessVitals.RIGHT_ARM));
        assertTrue(loaded.accepts(GoddessVitals.LEFT_ARM));
    }

    @Test void oneHitCannotSkipAnyOfTheThreeExecutions() {
        var life = new GoddessVitals();
        assertEquals(100_000, life.healthAfterHit(150_000, -1_000_000));
        assertEquals(100_000, life.absorb(GoddessVitals.HEAD, 100_000));
        assertEquals(50_000, life.absorb(GoddessVitals.LEFT_ARM, 100_000));
        assertEquals(1, life.absorb(GoddessVitals.RIGHT_ARM, 50_000));
        assertTrue(life.waiting());
        assertFalse(life.finished());
        assertEquals(0, life.absorb(GoddessVitals.HEAD, 1));
        assertTrue(life.finished());
        assertFalse(life.accepts(GoddessVitals.HEAD));
    }
}
